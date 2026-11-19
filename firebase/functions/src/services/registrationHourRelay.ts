/**
 * Cloud Scheduler does not exist in europe-southwest1, so the hourly job of the registrations starts in Belgium and
 * asks a function in Madrid to do the work, because the SMTP server of the mailbox accepts connections only from some
 * countries. The call carries a Google ID token of the service account of the functions. The function in Madrid checks
 * that token itself: the audience must be its own address and the account its own service account.
 */
import type { Request, Response } from "express";

export const REGISTRATIONS_HOUR_REGION = "europe-southwest1";
export const REGISTRATIONS_HOUR_FUNCTION = "registrationsHourRun";
const METADATA_EMAIL = "http://metadata.google.internal/computeMetadata/v1/instance/service-accounts/default/email";

/** The address of the function in Madrid in this project. */
export function registrationsHourUrl(projectId = process.env.GCLOUD_PROJECT ?? ""): string {
  return `https://${REGISTRATIONS_HOUR_REGION}-${projectId}.cloudfunctions.net/${REGISTRATIONS_HOUR_FUNCTION}`;
}

/** Calls the function in Madrid with an ID token of this service account. */
export async function callRegistrationsHour(): Promise<unknown> {
  const { GoogleAuth } = require("google-auth-library") as typeof import("google-auth-library");
  const url = registrationsHourUrl();
  const client = await new GoogleAuth().getIdTokenClient(url);
  const response = await client.request({ url, method: "POST", timeout: 530_000 });
  return response.data;
}

export interface CallerCheck {
  verify: (idToken: string, audience: string) => Promise<{ email?: string; email_verified?: boolean } | undefined>;
  ownEmail: () => Promise<string>;
}

const googleCheck: CallerCheck = {
  async verify(idToken, audience) {
    const { OAuth2Client } = require("google-auth-library") as typeof import("google-auth-library");
    const ticket = await new OAuth2Client().verifyIdToken({ idToken, audience });
    return ticket.getPayload();
  },
  async ownEmail() {
    const response = await fetch(METADATA_EMAIL, { headers: { "Metadata-Flavor": "Google" } });
    if (!response.ok) throw new Error(`metadata ${response.status}`);
    return (await response.text()).trim();
  },
};

/** True when the request comes from the service account of the functions of this project. */
export async function isOwnServiceAccount(
  authorization: string | undefined,
  audience: string,
  check: CallerCheck = googleCheck,
): Promise<boolean> {
  const match = /^Bearer (.+)$/.exec(authorization ?? "");
  if (!match) return false;
  try {
    const payload = await check.verify(match[1], audience);
    if (!payload?.email || payload.email_verified !== true) return false;
    return payload.email === (await check.ownEmail());
  } catch {
    return false;
  }
}

/** The handler of the function in Madrid. */
export function registrationsHourHandler(run: () => Promise<unknown>, check: CallerCheck = googleCheck) {
  return async (req: Request, res: Response): Promise<void> => {
    if (req.method !== "POST") {
      res.status(405).end();
      return;
    }
    if (!(await isOwnServiceAccount(req.header("authorization"), registrationsHourUrl(), check))) {
      res.status(403).end();
      return;
    }
    const results = await run();
    res.status(200).json({ results });
  };
}
