#!/usr/bin/env node
// The mailbox of the game day scenarios, read by IMAP: each player of a scenario has an address <local>+<tag>@<domain>
// of this mailbox, and the bots and the emulators open the link of their invitation as a person would.
//
//   node scripts/rehearsal/mail.mjs --check                      the mailbox answers, and how many emails it has
//   node scripts/rehearsal/mail.mjs --to=<address> [--since-minutes=60]   the newest email to the address, and its links
//
// Environment: REHEARSAL_IMAP_USER, REHEARSAL_IMAP_PASSWORD, REHEARSAL_IMAP_HOST (cp7061.webempresa.eu by default).
import tls from "node:tls";

const DEFAULT_HOST = "cp7061.webempresa.eu";

/** A small IMAP client: one command at a time, enough to search and read emails. */
export class Mailbox {
  constructor({ host = process.env.REHEARSAL_IMAP_HOST || DEFAULT_HOST, port = 993, user, password } = {}) {
    this.host = host;
    this.port = port;
    this.user = user ?? process.env.REHEARSAL_IMAP_USER ?? "";
    this.password = password ?? process.env.REHEARSAL_IMAP_PASSWORD ?? "";
    this.buffer = "";
    this.tag = 0;
    this.waiting = null;
  }

  async open() {
    if (!this.user || !this.password) throw new Error("Set REHEARSAL_IMAP_USER and REHEARSAL_IMAP_PASSWORD.");
    this.socket = tls.connect({ host: this.host, port: this.port, servername: this.host });
    this.socket.setEncoding("utf8");
    this.socket.on("data", (chunk) => {
      this.buffer += chunk;
      this.waiting?.();
    });
    this.socket.on("error", (error) => this.waiting?.(error));
    await this.until((text) => /^\* OK/m.test(text));
    this.buffer = "";
    await this.command(`LOGIN ${quote(this.user)} ${quote(this.password)}`);
    await this.select("INBOX");
    return this;
  }

  /** Waits until the received text passes the test, for 30 seconds at most. */
  until(test) {
    return new Promise((resolve, reject) => {
      const timer = setTimeout(() => {
        this.waiting = null;
        reject(new Error("The mailbox did not answer in 30 seconds"));
      }, 30_000);
      const check = (error) => {
        if (error) {
          clearTimeout(timer);
          this.waiting = null;
          reject(error);
        } else if (test(this.buffer)) {
          clearTimeout(timer);
          this.waiting = null;
          resolve(this.buffer);
        }
      };
      this.waiting = check;
      check();
    });
  }

  /** Sends one command and returns the lines of its answer. A NO or BAD answer is an error. */
  async command(text) {
    this.tag += 1;
    const tag = `A${this.tag}`;
    this.buffer = "";
    this.socket.write(`${tag} ${text}\r\n`);
    const answer = await this.until((received) => new RegExp(`^${tag} (OK|NO|BAD)`, "m").test(received));
    this.buffer = "";
    const status = answer.match(new RegExp(`^${tag} (OK|NO|BAD)(.*)$`, "m"));
    if (status[1] !== "OK") throw new Error(`IMAP ${text.startsWith("LOGIN") ? "LOGIN" : text}: ${status[1]}${status[2]}`);
    return answer;
  }

  /** The folders of the mailbox. The filter of the server can put an email in the spam folder, and an address with
   * +<tag> can go to a folder of that name, so the searches look in all of them. */
  async folders() {
    const answer = await this.command('LIST "" "*"');
    return answer.split(/\r?\n/).map((line) => line.match(/^\* LIST \(([^)]*)\) (?:"[^"]*"|NIL) (.+)$/))
      .filter((match) => match && !/\\Noselect/i.test(match[1]))
      .map((match) => match[2].trim().replace(/^"(.*)"$/, "$1"));
  }

  async select(folder) {
    if (this.selected === folder) return;
    await this.command(`SELECT ${quote(folder)}`);
    this.selected = folder;
  }

  async count() {
    let total = 0;
    for (const folder of await this.folders()) {
      const answer = await this.command(`STATUS ${quote(folder)} (MESSAGES)`);
      total += Number(answer.match(/MESSAGES (\d+)/)?.[1] ?? 0);
    }
    return total;
  }

  /** How many emails each folder has, and the subject and date of the newest ones, without their addresses. */
  async summary(newest = 5) {
    const rows = [];
    for (const folder of await this.folders()) {
      this.selected = null;
      await this.select(folder);
      const all = (await this.command("SEARCH ALL")).match(/^\* SEARCH(.*)$/m)?.[1]?.trim().split(/\s+/).filter(Boolean) ?? [];
      const latest = [];
      for (const number of all.slice(-newest).reverse()) {
        const email = await this.read({ folder, number: Number(number) });
        latest.push(`${email.date} | ${email.subject}`);
      }
      rows.push({ folder, emails: all.length, latest });
    }
    return rows;
  }

  /** The emails to an address in every folder, oldest first, as { folder, number }. */
  async search(to, sinceDate) {
    const since = sinceDate ? ` SINCE ${imapDate(sinceDate)}` : "";
    const found = [];
    for (const folder of await this.folders()) {
      // A new SELECT each time: the server shows the emails that arrived after the last one.
      this.selected = null;
      await this.select(folder);
      const answer = await this.command(`SEARCH TO ${quote(to)}${since}`);
      (answer.match(/^\* SEARCH(.*)$/m)?.[1] ?? "").trim().split(/\s+/).filter(Boolean)
        .forEach((number) => found.push({ folder, number: Number(number) }));
    }
    return found;
  }

  /** The subject, the date and the text of an email, with its quoted-printable soft breaks joined. */
  async read(ref) {
    const { folder, number } = typeof ref === "number" ? { folder: this.selected ?? "INBOX", number: ref } : ref;
    await this.select(folder);
    const answer = await this.command(`FETCH ${number} (BODY.PEEK[HEADER.FIELDS (SUBJECT DATE TO)] BODY.PEEK[TEXT])`);
    const subject = answer.match(/^Subject: (.*)$/mi)?.[1]?.trim() ?? "";
    const date = answer.match(/^Date: (.*)$/mi)?.[1]?.trim() ?? "";
    const text = decodeQuotedPrintable(answer);
    return { folder, number, subject, date, text, links: links(text) };
  }

  close() {
    this.socket?.write(`A${this.tag + 1} LOGOUT\r\n`);
    this.socket?.end();
  }
}

/** Waits for the newest email to an address, and returns it. Null after [timeoutMs]. */
export async function waitForEmail(mailbox, to, { sinceDate = new Date(Date.now() - 3600_000), timeoutMs = 600_000, matches = () => true } = {}) {
  const deadline = Date.now() + timeoutMs;
  for (;;) {
    const numbers = await mailbox.search(to, sinceDate);
    for (const number of numbers.reverse()) {
      const email = await mailbox.read(number);
      if (matches(email)) return email;
    }
    if (Date.now() + 15_000 > deadline) return null;
    await new Promise((resolve) => setTimeout(resolve, 15_000));
  }
}

/** The link of a Firebase invitation (password reset): it carries mode=resetPassword and the oobCode. */
export function resetLink(email) {
  return email.links.find((link) => link.includes("mode=resetPassword") && link.includes("oobCode=")) ?? null;
}

export function oobCode(link) {
  return new URL(link).searchParams.get("oobCode");
}

function quote(text) {
  return `"${String(text).replace(/\\/g, "\\\\").replace(/"/g, '\\"')}"`;
}

function imapDate(date) {
  const months = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"];
  return `${date.getUTCDate()}-${months[date.getUTCMonth()]}-${date.getUTCFullYear()}`;
}

function decodeQuotedPrintable(text) {
  return text.replace(/=\r?\n/g, "").replace(/=([0-9A-F]{2})/g, (_, hex) => String.fromCharCode(parseInt(hex, 16)));
}

function links(text) {
  return [...new Set((text.match(/https?:\/\/[^\s"'<>)\]]+/g) ?? []).map((link) => link.replace(/&amp;/g, "&")))];
}

async function main() {
  const args = Object.fromEntries(process.argv.slice(2).map((arg) => {
    const [key, ...rest] = arg.replace(/^--/, "").split("=");
    return [key, rest.length ? rest.join("=") : true];
  }));
  const mailbox = await new Mailbox().open();
  try {
    if (args.check) {
      const rows = await mailbox.summary();
      console.log(`The mailbox answers. It has ${rows.reduce((sum, row) => sum + row.emails, 0)} emails.`);
      rows.forEach((row) => {
        console.log(`  ${row.folder}: ${row.emails}`);
        row.latest.forEach((line) => console.log(`    ${line}`));
      });
    } else if (args.to) {
      const minutes = Number(args["since-minutes"] ?? 60);
      const email = await waitForEmail(mailbox, String(args.to), { sinceDate: new Date(Date.now() - minutes * 60_000), timeoutMs: 1 });
      if (email == null) console.log("No email to that address.");
      else console.log(JSON.stringify({ subject: email.subject, date: email.date, links: email.links }, null, 2));
    }
  } finally {
    mailbox.close();
  }
}

if (import.meta.url === `file://${process.argv[1]}`) {
  main().catch((error) => {
    console.error(error.message);
    process.exit(1);
  });
}
