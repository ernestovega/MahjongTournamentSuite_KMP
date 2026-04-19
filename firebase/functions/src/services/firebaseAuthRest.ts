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

export async function signUpWithEmailPassword(email: string, password: string): Promise<AuthTokens> {
  const url = `https://identitytoolkit.googleapis.com/v1/accounts:signUp?key=${apiKey()}`;
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

export async function signInWithEmailPassword(email: string, password: string): Promise<AuthTokens> {
  const url = `https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=${apiKey()}`;
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

export async function refreshIdToken(refreshToken: string): Promise<{ idToken: string; refreshToken: string }> {
  const url = `https://securetoken.googleapis.com/v1/token?key=${apiKey()}`;
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
