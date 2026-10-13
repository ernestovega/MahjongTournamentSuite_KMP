"use strict";
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
exports.setMailTransportForTests = setMailTransportForTests;
exports.sendEmail = sendEmail;
const nodemailer_1 = __importDefault(require("nodemailer"));
const config_1 = require("../config");
const httpError_1 = require("../api/httpError");
const DEFAULT_FROM = "Mahjong Madrid <noreply@mahjongmadrid.com>";
let smtp = null;
let override = null;
/** Replaces the SMTP transport, for tests. Pass null to use SMTP again. */
function setMailTransportForTests(transport) {
    override = transport;
}
async function sendWithSmtp(email) {
    // The connection is made on the first email. The SMTP address is a secret.
    smtp ?? (smtp = nodemailer_1.default.createTransport(config_1.MAIL_SMTP_URI.value()));
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
async function sendEmail(email) {
    try {
        await (override ?? sendWithSmtp)(email);
    }
    catch (error) {
        console.error("Sending an email failed", error);
        throw new httpError_1.HttpError(502, "email_failed", "The email could not be sent. Try again.");
    }
}
//# sourceMappingURL=mailService.js.map