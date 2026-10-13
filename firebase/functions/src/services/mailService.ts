import nodemailer, { type Transporter } from "nodemailer";

import { MAIL_SMTP_URI } from "../config";
import { HttpError } from "../api/httpError";

export type OutgoingEmail = { to: string; subject: string; text: string; html: string };

/** Sends one email. Tests replace it. */
export type MailTransport = (email: OutgoingEmail) => Promise<void>;

const DEFAULT_FROM = "Mahjong Madrid <noreply@mahjongmadrid.com>";

let smtp: Transporter | null = null;
let override: MailTransport | null = null;

/** Replaces the SMTP transport, for tests. Pass null to use SMTP again. */
export function setMailTransportForTests(transport: MailTransport | null): void {
  override = transport;
}

async function sendWithSmtp(email: OutgoingEmail): Promise<void> {
  // The connection is made on the first email. The SMTP address is a secret.
  smtp ??= nodemailer.createTransport(MAIL_SMTP_URI.value());
  await smtp.sendMail({
    from: process.env.MAIL_FROM?.trim() || DEFAULT_FROM,
    to: email.to,
    subject: email.subject,
    text: email.text,
    html: email.html,
  });
}

/**
 * Sends an email through the SMTP mailbox of the project.
 * A failure becomes a 502 error. The caller can offer to send the email again.
 */
export async function sendEmail(email: OutgoingEmail): Promise<void> {
  try {
    await (override ?? sendWithSmtp)(email);
  } catch (error) {
    console.error("Sending an email failed", error);
    throw new HttpError(502, "email_failed", "The email could not be sent. Try again.");
  }
}
