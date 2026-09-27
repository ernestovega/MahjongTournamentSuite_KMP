"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const promises_1 = require("node:fs/promises");
const node_path_1 = require("node:path");
const app_1 = require("firebase-admin/app");
const auth_1 = require("firebase-admin/auth");
const firestore_1 = require("firebase-admin/firestore");
const APPLY_FLAG = "--apply";
const BACKUP_DIRECTORY = ".firebase-reset-backups";
const DELETE_BATCH_SIZE = 400;
function argumentValue(name) {
    const inlinePrefix = `--${name}=`;
    const inline = process.argv.find((argument) => argument.startsWith(inlinePrefix));
    if (inline)
        return inline.slice(inlinePrefix.length).trim();
    const index = process.argv.indexOf(`--${name}`);
    const next = index >= 0 ? process.argv[index + 1] : undefined;
    return next && !next.startsWith("--") ? next.trim() : null;
}
function requiredArgument(name) {
    const value = argumentValue(name);
    if (!value)
        throw new Error(`Missing required argument --${name}`);
    return value;
}
function parseOptions() {
    const projectId = requiredArgument("project");
    const adminEmail = requiredArgument("admin-email").toLowerCase();
    if (!adminEmail.includes("@"))
        throw new Error("--admin-email must be a valid email address");
    return {
        projectId,
        adminEmail,
        adminName: argumentValue("admin-name") ?? "",
        confirmProject: argumentValue("confirm-project"),
        shouldApply: process.argv.includes(APPLY_FLAG),
    };
}
async function listAllUsers(auth) {
    const users = [];
    let pageToken;
    do {
        const page = await auth.listUsers(1000, pageToken);
        users.push(...page.users);
        pageToken = page.pageToken;
    } while (pageToken);
    return users;
}
function backupUser(user) {
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
function backupDocument(document) {
    return { path: document.ref.path, data: document.data() };
}
function tournamentMemberships(documents) {
    return documents.filter((document) => {
        const segments = document.ref.path.split("/");
        return segments.length === 4 && segments[0] === "tournaments" && segments[2] === "members";
    });
}
async function createBackup(params) {
    const directory = (0, node_path_1.resolve)(process.cwd(), BACKUP_DIRECTORY);
    await (0, promises_1.mkdir)(directory, { recursive: true });
    const timestamp = new Date().toISOString().replace(/[:.]/g, "-");
    const path = (0, node_path_1.resolve)(directory, `access-reset-${params.options.projectId}-${timestamp}.json`);
    const payload = {
        projectId: params.options.projectId,
        createdAt: new Date().toISOString(),
        selectedAdminEmail: params.options.adminEmail,
        authUsers: params.users.map(backupUser),
        userProfiles: params.profiles.map(backupDocument),
        tournamentMemberships: params.memberships.map(backupDocument),
    };
    await (0, promises_1.writeFile)(path, `${JSON.stringify(payload, null, 2)}\n`, { encoding: "utf8", flag: "wx", mode: 0o600 });
    return path;
}
async function deleteDocuments(db, references) {
    for (let offset = 0; offset < references.length; offset += DELETE_BATCH_SIZE) {
        const batch = db.batch();
        for (const reference of references.slice(offset, offset + DELETE_BATCH_SIZE))
            batch.delete(reference);
        await batch.commit();
    }
}
async function main() {
    const options = parseOptions();
    if (options.shouldApply && options.confirmProject !== options.projectId) {
        throw new Error(`Apply mode requires --confirm-project=${options.projectId}`);
    }
    const app = (0, app_1.initializeApp)({ credential: (0, app_1.applicationDefault)(), projectId: options.projectId }, `reset-access-${Date.now()}`);
    const auth = (0, auth_1.getAuth)(app);
    const db = (0, firestore_1.getFirestore)(app);
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
        console.log(`Run again with ${APPLY_FLAG} --confirm-project=${options.projectId} to apply the reset.`);
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
        if (user.uid !== admin.uid)
            await auth.deleteUser(user.uid);
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
        createdAt: firestore_1.FieldValue.serverTimestamp(),
        updatedAt: firestore_1.FieldValue.serverTimestamp(),
    });
    const [verifiedUsers, verifiedProfiles, verifiedMembershipsSnapshot] = await Promise.all([
        listAllUsers(auth),
        db.collection("users").get(),
        db.collectionGroup("members").get(),
    ]);
    const verifiedMemberships = tournamentMemberships(verifiedMembershipsSnapshot.docs);
    const verifiedAdmin = verifiedUsers.find((user) => user.uid === admin.uid);
    if (verifiedUsers.length !== 1
        || !verifiedAdmin
        || verifiedAdmin.disabled
        || verifiedAdmin.customClaims?.admin !== true
        || verifiedProfiles.size !== 1
        || verifiedMemberships.length !== 0) {
        throw new Error("Reset verification failed. Review the backup and remote project state.");
    }
    console.log(`Reset complete. ADMIN uid: ${admin.uid}`);
    console.log("Use the password reset screen before the first sign-in when the account has no password.");
}
main().catch((error) => {
    console.error("Access reset failed.", error);
    process.exitCode = 1;
});
//# sourceMappingURL=resetAccess.js.map