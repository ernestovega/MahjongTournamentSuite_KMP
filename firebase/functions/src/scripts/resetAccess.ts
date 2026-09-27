import { mkdir, writeFile } from "node:fs/promises";
import { resolve } from "node:path";

import { applicationDefault, initializeApp } from "firebase-admin/app";
import { getAuth, type UserRecord } from "firebase-admin/auth";
import {
  FieldValue,
  getFirestore,
  type DocumentReference,
  type QueryDocumentSnapshot,
} from "firebase-admin/firestore";

const APPLY_FLAG = "--apply";
const BACKUP_DIRECTORY = ".firebase-reset-backups";
const DELETE_BATCH_SIZE = 400;

type Options = {
  projectId: string;
  adminEmail: string;
  adminName: string;
  confirmProject: string | null;
  shouldApply: boolean;
};

type BackupUser = {
  uid: string;
  email: string | null;
  displayName: string | null;
  disabled: boolean;
  emailVerified: boolean;
  customClaims: Record<string, unknown>;
  providers: string[];
};

function argumentValue(name: string): string | null {
  const inlinePrefix = `--${name}=`;
  const inline = process.argv.find((argument) => argument.startsWith(inlinePrefix));
  if (inline) return inline.slice(inlinePrefix.length).trim();

  const index = process.argv.indexOf(`--${name}`);
  const next = index >= 0 ? process.argv[index + 1] : undefined;
  return next && !next.startsWith("--") ? next.trim() : null;
}

function requiredArgument(name: string): string {
  const value = argumentValue(name);
  if (!value) throw new Error(`Missing required argument --${name}`);
  return value;
}

function parseOptions(): Options {
  const projectId = requiredArgument("project");
  const adminEmail = requiredArgument("admin-email").toLowerCase();
  if (!adminEmail.includes("@")) throw new Error("--admin-email must be a valid email address");

  return {
    projectId,
    adminEmail,
    adminName: argumentValue("admin-name") ?? "",
    confirmProject: argumentValue("confirm-project"),
    shouldApply: process.argv.includes(APPLY_FLAG),
  };
}

async function listAllUsers(auth: ReturnType<typeof getAuth>): Promise<UserRecord[]> {
  const users: UserRecord[] = [];
  let pageToken: string | undefined;
  do {
    const page = await auth.listUsers(1_000, pageToken);
    users.push(...page.users);
    pageToken = page.pageToken;
  } while (pageToken);
  return users;
}

function backupUser(user: UserRecord): BackupUser {
  return {
    uid: user.uid,
    email: user.email ?? null,
    displayName: user.displayName ?? null,
    disabled: user.disabled,
    emailVerified: user.emailVerified,
    customClaims: user.customClaims ?? {},
    providers: user.providerData.map((provider) => provider.providerId),
  };
}

function backupDocument(document: QueryDocumentSnapshot): { path: string; data: FirebaseFirestore.DocumentData } {
  return { path: document.ref.path, data: document.data() };
}

function tournamentMemberships(documents: QueryDocumentSnapshot[]): QueryDocumentSnapshot[] {
  return documents.filter((document) => {
    const segments = document.ref.path.split("/");
    return segments.length === 4 && segments[0] === "tournaments" && segments[2] === "members";
  });
}

async function createBackup(params: {
  options: Options;
  users: UserRecord[];
  profiles: QueryDocumentSnapshot[];
  memberships: QueryDocumentSnapshot[];
}): Promise<string> {
  const directory = resolve(process.cwd(), BACKUP_DIRECTORY);
  await mkdir(directory, { recursive: true });
  const timestamp = new Date().toISOString().replace(/[:.]/g, "-");
  const path = resolve(directory, `access-reset-${params.options.projectId}-${timestamp}.json`);
  const payload = {
    projectId: params.options.projectId,
    createdAt: new Date().toISOString(),
    selectedAdminEmail: params.options.adminEmail,
    authUsers: params.users.map(backupUser),
    userProfiles: params.profiles.map(backupDocument),
    tournamentMemberships: params.memberships.map(backupDocument),
  };
  await writeFile(path, `${JSON.stringify(payload, null, 2)}\n`, { encoding: "utf8", flag: "wx", mode: 0o600 });
  return path;
}

async function deleteDocuments(
  db: ReturnType<typeof getFirestore>,
  references: DocumentReference[],
): Promise<void> {
  for (let offset = 0; offset < references.length; offset += DELETE_BATCH_SIZE) {
    const batch = db.batch();
    for (const reference of references.slice(offset, offset + DELETE_BATCH_SIZE)) batch.delete(reference);
    await batch.commit();
  }
}

async function main(): Promise<void> {
  const options = parseOptions();
  if (options.shouldApply && options.confirmProject !== options.projectId) {
    throw new Error(`Apply mode requires --confirm-project=${options.projectId}`);
  }

  const app = initializeApp(
    { credential: applicationDefault(), projectId: options.projectId },
    `reset-access-${Date.now()}`,
  );
  const auth = getAuth(app);
  const db = getFirestore(app);
  const [users, profilesSnapshot, membershipsSnapshot] = await Promise.all([
    listAllUsers(auth),
    db.collection("users").get(),
    db.collectionGroup("members").get(),
  ]);
  const membershipDocuments = tournamentMemberships(membershipsSnapshot.docs);
  const selectedAdmin = users.find((user) => user.email?.toLowerCase() === options.adminEmail) ?? null;

  console.log(`Project: ${options.projectId}`);
  console.log(`Authentication accounts: ${users.length}`);
  console.log(`User profiles: ${profilesSnapshot.size}`);
  console.log(`Tournament assignments: ${membershipDocuments.length}`);
  console.log(selectedAdmin
    ? `Admin account to keep: ${selectedAdmin.uid} (${options.adminEmail})`
    : `Admin account to create: ${options.adminEmail}`);
  console.log(`Authentication accounts to delete: ${users.length - (selectedAdmin ? 1 : 0)}`);

  if (!options.shouldApply) {
    console.log("Dry run only. No remote data changed.");
    console.log(
      `Run again with ${APPLY_FLAG} --confirm-project=${options.projectId} to apply the reset.`,
    );
    return;
  }

  const backupPath = await createBackup({
    options,
    users,
    profiles: profilesSnapshot.docs,
    memberships: membershipDocuments,
  });
  console.log(`Local inventory backup: ${backupPath}`);

  const admin = selectedAdmin ?? await auth.createUser({
    email: options.adminEmail,
    displayName: options.adminName || undefined,
    disabled: false,
  });

  await deleteDocuments(db, [
    ...membershipDocuments.map((document) => document.ref),
    ...profilesSnapshot.docs.map((document) => document.ref),
  ]);

  for (const user of users) {
    if (user.uid !== admin.uid) await auth.deleteUser(user.uid);
  }

  await auth.updateUser(admin.uid, {
    disabled: false,
    displayName: options.adminName || admin.displayName || undefined,
  });
  await auth.setCustomUserClaims(admin.uid, { admin: true });
  await auth.revokeRefreshTokens(admin.uid);
  await db.doc(`users/${admin.uid}`).set({
    uid: admin.uid,
    email: options.adminEmail,
    alias: options.adminName,
    contactEmail: options.adminEmail,
    disabled: false,
    createdAt: FieldValue.serverTimestamp(),
    updatedAt: FieldValue.serverTimestamp(),
  });

  const [verifiedUsers, verifiedProfiles, verifiedMembershipsSnapshot] = await Promise.all([
    listAllUsers(auth),
    db.collection("users").get(),
    db.collectionGroup("members").get(),
  ]);
  const verifiedMemberships = tournamentMemberships(verifiedMembershipsSnapshot.docs);
  const verifiedAdmin = verifiedUsers.find((user) => user.uid === admin.uid);
  if (
    verifiedUsers.length !== 1
    || !verifiedAdmin
    || verifiedAdmin.disabled
    || verifiedAdmin.customClaims?.admin !== true
    || verifiedProfiles.size !== 1
    || verifiedMemberships.length !== 0
  ) {
    throw new Error("Reset verification failed. Review the backup and remote project state.");
  }

  console.log(`Reset complete. ADMIN uid: ${admin.uid}`);
  console.log("Use the password reset screen before the first sign-in when the account has no password.");
}

main().catch((error: unknown) => {
  console.error("Access reset failed.", error);
  process.exitCode = 1;
});
