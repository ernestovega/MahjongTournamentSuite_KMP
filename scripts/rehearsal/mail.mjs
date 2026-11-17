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
    await this.command("SELECT INBOX");
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

  async count() {
    const answer = await this.command("STATUS INBOX (MESSAGES)");
    return Number(answer.match(/MESSAGES (\d+)/)?.[1] ?? 0);
  }

  /** The numbers of the emails to an address, oldest first. */
  async search(to, sinceDate) {
    const since = sinceDate ? ` SINCE ${imapDate(sinceDate)}` : "";
    const answer = await this.command(`SEARCH TO ${quote(to)}${since}`);
    return (answer.match(/^\* SEARCH(.*)$/m)?.[1] ?? "").trim().split(/\s+/).filter(Boolean).map(Number);
  }

  /** The subject, the date and the text of an email, with its quoted-printable soft breaks joined. */
  async read(number) {
    const answer = await this.command(`FETCH ${number} (BODY.PEEK[HEADER.FIELDS (SUBJECT DATE TO)] BODY.PEEK[TEXT])`);
    const subject = answer.match(/^Subject: (.*)$/mi)?.[1]?.trim() ?? "";
    const date = answer.match(/^Date: (.*)$/mi)?.[1]?.trim() ?? "";
    const text = decodeQuotedPrintable(answer);
    return { number, subject, date, text, links: links(text) };
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
      console.log(`The mailbox answers. It has ${await mailbox.count()} emails.`);
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
