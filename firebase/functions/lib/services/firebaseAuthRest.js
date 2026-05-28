"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.signUpWithEmailPassword = signUpWithEmailPassword;
exports.signInWithEmailPassword = signInWithEmailPassword;
exports.refreshIdToken = refreshIdToken;
const config_1 = require("../config");
function apiKey() {
    const key = config_1.ID_TOOLKIT_API_KEY.value();
    if (!key) {
        throw new Error("Missing secret MTS_ID_TOOLKIT_API_KEY");
    }
    return key;
}
async function postJson(url, body) {
    const res = await fetch(url, {
        method: "POST",
        headers: {
            "content-type": "application/json",
        },
        body: JSON.stringify(body),
    });
    const json = (await res.json());
    if (!res.ok) {
        const message = json.error?.message ?? `HTTP ${res.status}`;
        throw new Error(message);
    }
    return json;
}
async function signUpWithEmailPassword(email, password) {
    const url = `https://identitytoolkit.googleapis.com/v1/accounts:signUp?key=${apiKey()}`;
    const json = await postJson(url, {
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
async function signInWithEmailPassword(email, password) {
    const url = `https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=${apiKey()}`;
    const json = await postJson(url, {
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
async function refreshIdToken(refreshToken) {
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
    const json = (await res.json());
    if (!res.ok || !json.id_token || !json.refresh_token) {
        throw new Error("Failed to refresh token");
    }
    return {
        idToken: json.id_token,
        refreshToken: json.refresh_token,
    };
}
//# sourceMappingURL=firebaseAuthRest.js.map