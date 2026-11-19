#!/usr/bin/env node
// The players of a game day scenario open their invitation as a person does: the email arrives in the mailbox of the
// tests, the link of the email opens the page of Firebase, and the person chooses a password there. This script does
// what that page does, with the oobCode of the link. See docs/scenarios.md.
//
//   node scripts/rehearsal/invitations.mjs --plan=out/scenario/plan.json [--slots=1-22] [--minutes=30] \
//     --report=out/invitations.json
//
// Environment: REHEARSAL_PASSWORD (the password every player chooses), REHEARSAL_IMAP_USER, REHEARSAL_IMAP_PASSWORD.
import fs from "node:fs";
import { Api, Metrics, parseArgs, parseRange, Session, sleep, STAGING_API } from "./api.mjs";
import { Mailbox, oobCode, resetLink } from "./mail.mjs";

/** Chooses the password with the code of the email, as the page of the link does. */
async function choosePassword(link, password) {
  const url = new URL(link);
  const apiKey = url.searchParams.get("apiKey");
  if (!apiKey) throw new Error("The link has no apiKey");
  const reply = await fetch(`https://identitytoolkit.googleapis.com/v1/accounts:resetPassword?key=${apiKey}`, {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({ oobCode: oobCode(link), newPassword: password }),
  });
  if (!reply.ok) throw new Error(`The page refused the password: ${reply.status} ${(await reply.text()).slice(0, 200)}`);
}

/**
 * A player of an earlier game day comes back with the account and the password of then. When the password does not
 * work (that game day stopped before its players chose one), the player asks for a new one with "Forgot password", as
 * a person does, and opens that email like an invitation. Returns the players who still need an email.
 */
async function signInTheReturning({ api, plan, slots, password, results, pause = sleep }) {
  const waiting = [];
  for (const slot of plan.slots.filter((each) => each.returning && slots.includes(each.slot))) {
    if (await new Session(api, slot.email, password).signIn()) {
      results.push({ slot: slot.slot, outcome: "SIGNED_IN" });
    } else {
      const reply = await api.call("POST", "/auth/passwordReset", { body: { email: slot.email }, label: "POST passwordReset" });
      if (reply.status === 200) waiting.push(slot);
      else results.push({ slot: slot.slot, outcome: "FAILED", error: `Forgot password answered ${reply.status}` });
    }
    // The sign-in of the API takes 30 a minute from one address.
    await pause(2_500);
  }
  return waiting;
}

export async function openInvitations({
  plan, slots, password, minutes = 30, since = new Date(Date.now() - 3 * 3600_000), api = null, pause = sleep, openMailbox = null,
}) {
  const results = [];
  // The search of the mailbox goes by day: a returning player skips the emails of the earlier game day.
  const askedAt = Date.now() - 60_000;
  const returning = api == null ? [] : await signInTheReturning({ api, plan, slots, password, results, pause });
  const mailbox = openMailbox == null ? await new Mailbox().open() : await openMailbox();
  const pending = new Map(plan.slots
    .filter((slot) => slots.includes(slot.slot) && (!slot.returning || returning.includes(slot)))
    .map((slot) => [slot.email, slot]));
  const deadline = Date.now() + minutes * 60_000;
  try {
    while (pending.size > 0 && Date.now() < deadline) {
      for (const [email, slot] of [...pending]) {
        const numbers = await mailbox.search(email, since);
        for (const number of numbers.reverse()) {
          const message = await mailbox.read(number);
          if (slot.returning && Date.parse(message.date) < askedAt) continue;
          const link = resetLink(message);
          if (link == null) continue;
          try {
            await choosePassword(link, password);
            results.push({ slot: slot.slot, outcome: "PASSWORD_SET", subject: message.subject, receivedAt: message.date });
          } catch (error) {
            results.push({ slot: slot.slot, outcome: "FAILED", error: String(error.message ?? error) });
          }
          pending.delete(email);
          break;
        }
      }
      if (pending.size > 0) await pause(20_000);
    }
  } finally {
    mailbox.close();
  }
  for (const slot of pending.values()) results.push({ slot: slot.slot, outcome: "NO_EMAIL" });
  return results.sort((a, b) => a.slot - b.slot);
}

async function main() {
  const args = parseArgs(process.argv.slice(2));
  const plan = JSON.parse(fs.readFileSync(String(args.plan ?? "out/scenario/plan.json"), "utf8"));
  const password = process.env.REHEARSAL_PASSWORD ?? "";
  if (!password) throw new Error("Set REHEARSAL_PASSWORD.");
  const slots = args.slots ? parseRange(String(args.slots)) : plan.slots.map((slot) => slot.slot);
  const api = new Api(String(args.api ?? STAGING_API), new Metrics());
  const results = await openInvitations({ plan, slots, password, minutes: Number(args.minutes ?? 30), api });
  const count = (outcome) => results.filter((result) => result.outcome === outcome).length;
  console.log(`Invitations: ${count("PASSWORD_SET")} opened and a password chosen, ${count("SIGNED_IN")} players of earlier game days signed in, `
    + `${count("NO_EMAIL")} without email, ${count("FAILED")} failed.`);
  const fine = new Set(["PASSWORD_SET", "SIGNED_IN"]);
  results.filter((result) => !fine.has(result.outcome)).forEach((result) => console.log(`  slot ${result.slot}: ${result.outcome} ${result.error ?? ""}`));
  if (args.report) fs.writeFileSync(String(args.report), `${JSON.stringify(results, null, 2)}\n`);
  if (results.some((result) => !fine.has(result.outcome))) process.exitCode = 1;
}

if (import.meta.url === `file://${process.argv[1]}`) {
  main().catch((error) => {
    console.error(error.message);
    process.exit(1);
  });
}
