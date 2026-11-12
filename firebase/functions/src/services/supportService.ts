import { badRequest, HttpError } from "../api/httpError";
import { sendEmail } from "./mailService";

/** A message that someone writes to the support of the apps from the "Need help?" screen. */
export type SupportMessage = {
  name: string;
  email: string;
  message: string;
  /** Which app sent it, for example "player" or "admin". */
  app: string;
  appVersion: string;
  platform: string;
};

export const SUPPORT_LIMITS = { name: 100, email: 254, message: 5000, meta: 60 } as const;

const EMAIL_PATTERN = /^[^\s@<>()[\]\\,;:"]+@[^\s@<>()[\]\\,;:"]+\.[^\s@<>()[\]\\,;:"]+$/;

function field(body: Record<string, unknown>, name: string): string {
  const value = body[name];
  return typeof value === "string" ? value.trim() : "";
}

/** Strips line breaks and control characters, so a value cannot add mail headers or break the subject. */
function oneLine(value: string, max: number): string {
  // eslint-disable-next-line no-control-regex
  return value.replace(/[\u0000-\u001f\u007f]+/g, " ").trim().slice(0, max);
}

/**
 * Reads and checks the body of a support request. Returns null for a bot that filled the hidden field:
 * the caller answers as if it sent the message, so the bot does not learn about the trap.
 */
export function readSupportMessage(body: unknown): SupportMessage | null {
  const data = (body && typeof body === "object" ? body : {}) as Record<string, unknown>;
  if (field(data, "website")) return null;

  const name = oneLine(field(data, "name"), SUPPORT_LIMITS.name);
  const email = field(data, "email");
  const message = field(data, "message").replace(/\r\n/g, "\n");

  if (!email || email.length > SUPPORT_LIMITS.email || !EMAIL_PATTERN.test(email)) throw badRequest("INVALID_EMAIL");
  if (!message) throw badRequest("Missing message");
  if (message.length > SUPPORT_LIMITS.message) throw badRequest("Message too long");

  return {
    name,
    email,
    message,
    app: oneLine(field(data, "app"), SUPPORT_LIMITS.meta) || "unknown",
    appVersion: oneLine(field(data, "appVersion"), SUPPORT_LIMITS.meta) || "unknown",
    platform: oneLine(field(data, "platform"), SUPPORT_LIMITS.meta) || "unknown",
  };
}

function escapeHtml(value: string): string {
  return value.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;");
}

export function buildSupportEmail(message: SupportMessage, to: string) {
  const from = message.name ? `${message.name} <${message.email}>` : message.email;
  const preview = oneLine(message.message, 60);
  const details = `App: ${message.app} ${message.appVersion} (${message.platform})`;
  return {
    to,
    replyTo: message.email,
    subject: `[MTS support] ${message.name || message.email}: ${preview}`,
    text: `From: ${from}\n${details}\n\n${message.message}`,
    html: `<p><strong>From:</strong> ${escapeHtml(from)}<br>${escapeHtml(details)}</p>`
      + `<p style="white-space:pre-wrap">${escapeHtml(message.message)}</p>`,
  };
}

let recipientOverride: string | null = null;

/** Replaces the support address, for tests. Pass null to read the secret again. */
export function setSupportRecipientForTests(recipient: string | null): void {
  recipientOverride = recipient;
}

/** Sends the message to the support mailbox. The address is a secret of the server and never reaches the apps. */
export async function sendSupportMessage(message: SupportMessage): Promise<void> {
  // A bound secret reaches the code as an environment variable of the same name.
  const to = recipientOverride ?? process.env.MTS_SUPPORT_EMAIL?.trim();
  if (!to) {
    console.error("The secret MTS_SUPPORT_EMAIL is not bound to the api function. Support messages cannot be sent.");
    throw new HttpError(503, "support_unavailable", "Support is not available right now. Try again later.");
  }
  await sendEmail(buildSupportEmail(message, to));
}
