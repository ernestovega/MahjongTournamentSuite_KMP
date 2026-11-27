import assert from "node:assert/strict";
import { mock, test } from "node:test";

import { MAIL_SMTP_URI } from "../config";
import * as lazyModules from "./lazyModules";
import { sendEmail } from "./mailService";

test("sends through one SMTP connection, from the configured sender or the default one", async () => {
  const sent: Array<{ from?: string; to?: string }> = [];
  let connections = 0;
  const transport = mock.method(lazyModules, "nodemailer", () => ({
    createTransport: () => {
      connections++;
      return { sendMail: async (mail: { from?: string; to?: string }) => { sent.push(mail); } };
    },
  }) as never);
  const secret = mock.method(MAIL_SMTP_URI, "value", () => "smtp://example.test");
  const before = process.env.MAIL_FROM;
  try {
    delete process.env.MAIL_FROM;
    await sendEmail({ to: "ana@example.test", subject: "Hi", text: "Hi", html: "<p>Hi</p>" });
    process.env.MAIL_FROM = " Club <club@example.test> ";
    await sendEmail({ to: "bea@example.test", subject: "Hi", text: "Hi", html: "<p>Hi</p>" });
  } finally {
    if (before === undefined) delete process.env.MAIL_FROM; else process.env.MAIL_FROM = before;
    transport.mock.restore();
    secret.mock.restore();
  }
  assert.equal(connections, 1);
  assert.deepEqual(sent.map((mail) => mail.from), ["Mahjong Madrid <noreply@mahjongmadrid.com>", "Club <club@example.test>"]);
});
