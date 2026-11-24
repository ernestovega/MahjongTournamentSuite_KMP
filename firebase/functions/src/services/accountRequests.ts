import { FieldValue, Timestamp } from "firebase-admin/firestore";

import { auth, db } from "../firebase";
import { badRequest, conflict, notFound } from "../api/httpError";
import { sendEmail } from "./mailService";
import { listEnabledSuperAdmins } from "./usersService";
import { bumpGlobalDataVersion } from "./dataVersionsService";

/**
 * A club admin cannot disable or delete an account for everyone, because the account may work for other
 * organizations or play anywhere. It removes what the account has in its own organizations and asks the super admins
 * for the rest.
 * The super admins get an email and a list of the open requests in the Accounts screen, where they approve or
 * reject each one.
 */
export type AccountRequestKind = "DISABLE" | "DELETE";
export type AccountRequestStatus = "PENDING" | "APPROVED" | "REJECTED";

export type AccountRequest = {
  id: string;
  kind: AccountRequestKind;
  status: AccountRequestStatus;
  targetUid: string;
  targetEmail: string;
  reason: string;
  requestedByUid: string;
  requestedByEmail: string;
  createdAtMs: number;
  resolvedByUid: string | null;
  resolvedAtMs: number | null;
};

const COLLECTION = "accountRequests";
export const MAX_REASON_LENGTH = 1000;

export function parseAccountRequestKind(value: unknown): AccountRequestKind | null {
  return value === "DISABLE" || value === "DELETE" ? value : null;
}

function toRequest(id: string, data: FirebaseFirestore.DocumentData): AccountRequest {
  const millis = (value: unknown) => (value instanceof Timestamp ? value.toMillis() : null);
  return {
    id,
    kind: data.kind === "DELETE" ? "DELETE" : "DISABLE",
    status: data.status === "APPROVED" || data.status === "REJECTED" ? data.status : "PENDING",
    targetUid: String(data.targetUid ?? ""),
    targetEmail: String(data.targetEmail ?? ""),
    reason: String(data.reason ?? ""),
    requestedByUid: String(data.requestedByUid ?? ""),
    requestedByEmail: String(data.requestedByEmail ?? ""),
    createdAtMs: millis(data.createdAt) ?? 0,
    resolvedByUid: data.resolvedByUid == null ? null : String(data.resolvedByUid),
    resolvedAtMs: millis(data.resolvedAt),
  };
}

function buildRequestEmail(to: string, request: Omit<AccountRequest, "id" | "createdAtMs" | "resolvedByUid" | "resolvedAtMs">) {
  const action = request.kind === "DELETE" ? "delete" : "disable";
  const subject = `MTS: request to ${action} the account ${request.targetEmail}`;
  const lines = [
    `${request.requestedByEmail} asks to ${action} the account ${request.targetEmail}.`,
    "",
    `Reason: ${request.reason || "(none)"}`,
    "",
    "Open Accounts > Requests in the management app to approve or reject it.",
  ];
  const escape = (value: string) => value.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
  return { to, subject, text: lines.join("\n"), html: lines.map((line) => `<p>${escape(line)}</p>`).join("") };
}

/** Saves a request and tells every enabled super admin by email. A failed email does not undo the request. */
export async function createAccountRequest(params: {
  kind: AccountRequestKind;
  targetUid: string;
  reason: string;
  requestedByUid: string;
}): Promise<AccountRequest> {
  const reason = params.reason.trim();
  if (reason.length > MAX_REASON_LENGTH) throw badRequest(`The reason has at most ${MAX_REASON_LENGTH} characters`);
  const [target, requester] = await Promise.all([auth.getUser(params.targetUid), auth.getUser(params.requestedByUid)]);
  // One open request of each kind per account is enough. A single-field filter needs no composite index.
  const existing = await db.collection(COLLECTION).where("targetUid", "==", params.targetUid).get();
  const open = existing.docs.find((document) => document.get("status") === "PENDING" && document.get("kind") === params.kind);
  if (open) return toRequest(open.id, open.data());
  const data = {
    kind: params.kind,
    status: "PENDING" as const,
    targetUid: target.uid,
    targetEmail: target.email ?? "",
    reason,
    requestedByUid: requester.uid,
    requestedByEmail: requester.email ?? "",
  };
  const ref = await db.collection(COLLECTION).add({ ...data, createdAt: FieldValue.serverTimestamp() });
  await bumpGlobalDataVersion("users");
  const superAdmins = await listEnabledSuperAdmins();
  await Promise.allSettled(superAdmins.filter((admin) => admin.email !== "").map((admin) => sendEmail(buildRequestEmail(admin.email, data))));
  return toRequest(ref.id, { ...data, createdAt: Timestamp.now() });
}

export async function listPendingAccountRequests(): Promise<AccountRequest[]> {
  const snapshot = await db.collection(COLLECTION).where("status", "==", "PENDING").get();
  return snapshot.docs.map((document) => toRequest(document.id, document.data()))
    .sort((left, right) => right.createdAtMs - left.createdAtMs);
}

/** Marks a pending request approved or rejected. The caller runs the disable or the delete of an approved one. */
export async function resolveAccountRequest(id: string, approve: boolean, resolvedByUid: string): Promise<AccountRequest> {
  const ref = db.doc(`${COLLECTION}/${id}`);
  const snapshot = await ref.get();
  if (!snapshot.exists) throw notFound("Request not found");
  const request = toRequest(id, snapshot.data() ?? {});
  if (request.status !== "PENDING") throw conflict("The request is already resolved", { code: "REQUEST_RESOLVED" });
  await ref.update({ status: approve ? "APPROVED" : "REJECTED", resolvedByUid, resolvedAt: FieldValue.serverTimestamp() });
  await bumpGlobalDataVersion("users");
  return { ...request, status: approve ? "APPROVED" : "REJECTED", resolvedByUid, resolvedAtMs: Date.now() };
}
