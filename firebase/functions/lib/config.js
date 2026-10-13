"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.MAIL_SMTP_URI = exports.BOOTSTRAP_KEY = exports.ID_TOOLKIT_API_KEY = void 0;
const params_1 = require("firebase-functions/params");
// Secret names must not start with reserved prefixes (e.g. FIREBASE_).
exports.ID_TOOLKIT_API_KEY = (0, params_1.defineSecret)("MTS_ID_TOOLKIT_API_KEY");
exports.BOOTSTRAP_KEY = (0, params_1.defineSecret)("MTS_BOOTSTRAP_KEY");
// The SMTP address of the mailbox that sends the invitations, for example smtps://user:password@host:465.
exports.MAIL_SMTP_URI = (0, params_1.defineSecret)("MTS_SMTP_URI");
//# sourceMappingURL=config.js.map