type AuthTokens = {
  idToken: string;
  refreshToken: string;
  uid: string;
};

type FirebaseAuthError = {
  error?: {
    message?: string;
  };
};

import { ID_TOOLKIT_API_KEY } from "../config";

/** The Auth emulator serves the same paths over http. Only local test runs set this variable. */
function googleApi(host: "identitytoolkit" | "securetoken"): string {
  const emulator = process.env.FIREBASE_AUTH_EMULATOR_HOST;
  return emulator ? `http://${emulator}/${host}.googleapis.com` : `https://${host}.googleapis.com`;
}

function apiKey(): string {
  const key = ID_TOOLKIT_API_KEY.value();
  if (!key) {
    throw new Error("Missing secret MTS_ID_TOOLKIT_API_KEY");
  }
  return key;
}

async function postJson<T>(url: string, body: unknown): Promise<T> {
  const res = await fetch(url, {
    method: "POST",
    headers: {
      "content-type": "application/json",
    },
    body: JSON.stringify(body),
  });

  const json = (await res.json()) as T | FirebaseAuthError;

  if (!res.ok) {
    const message = (json as FirebaseAuthError).error?.message ?? `HTTP ${res.status}`;
    throw new Error(message);
  }

  return json as T;
}

export async function signInWithEmailPassword(email: string, password: string): Promise<AuthTokens> {
  const url = `${googleApi("identitytoolkit")}/v1/accounts:signInWithPassword?key=${apiKey()}`;
  const json = await postJson<{ idToken: string; refreshToken: string; localId: string }>(url, {
    email,
    password,
    returnSecureToken: true,
  });

  return {
    idToken: json.idToken,
    refreshToken: json.refreshToken,
    uid: json.localId,
  };
}

/** Sends the password reset email. Tests replace it. */
export type PasswordResetSender = (email: string) => Promise<void>;

let resetOverride: PasswordResetSender | null = null;

/** Replaces the call to the Google password API, for tests. Pass null to use the API again. */
export function setPasswordResetSenderForTests(sender: PasswordResetSender | null): void {
  resetOverride = sender;
}

export async function sendPasswordResetEmail(email: string): Promise<void> {
  if (resetOverride != null) return resetOverride(email);
  const url = `${googleApi("identitytoolkit")}/v1/accounts:sendOobCode?key=${apiKey()}`;
  await postJson(url, {
    requestType: "PASSWORD_RESET",
    email,
  });
}

export async function refreshIdToken(refreshToken: string): Promise<{ idToken: string; refreshToken: string }> {
  const url = `${googleApi("securetoken")}/v1/token?key=${apiKey()}`;
  const body = new URLSearchParams({
    grant_type: "refresh_token",
    refresh_token: refreshToken,
  });

  const res = await fetch(url, {
    method: "POST",
    headers: {
      "content-type": "application/x-www-form-urlencoded",
    },
    body,
  });

  const json = (await res.json()) as {
    id_token?: string;
    refresh_token?: string;
    error?: unknown;
  };

  if (!res.ok || !json.id_token || !json.refresh_token) {
    throw new Error("Failed to refresh token");
  }

  return {
    idToken: json.id_token,
    refreshToken: json.refresh_token,
  };
}
