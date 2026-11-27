import assert from "node:assert/strict";
import { after, before, describe, it } from "node:test";

import { Timestamp } from "firebase-admin/firestore";

import { auth, db } from "../firebase";
import { setMailTransportForTests } from "./mailService";
import {
  accountOfEma,
  adminAddRegistration,
  adminRegistrations,
  adminRemoveRegistration,
  adminUpdateRegistration,
  applyPlayerCountToRegistrations,
  addOwnTeamMember,
  cancelOwnRegistration,
  confirmRegistration,
  createOwnTeam,
  emailHint,
  freeSlug,
  importRegistrations,
  loadTournament,
  ownRegistration,
  publicRegistrations,
  recentChanges,
  register,
  registrationsOfEma,
  registryPerson,
  removeExpiredPending,
  requestWithoutEmaNumber,
  saveFoodRestrictions,
  tournamentOfSlug,
  validateRegistrationId,
} from "./registrationsService";

const codeOf = (error: unknown) => (error as { details?: { code?: string } }).details?.code;
const refusedWith = (code: string) => (error: unknown) => {
  assert.equal(codeOf(error), code, String(error));
  return true;
};

/**
 * The registrations read from documents with missing or wrong fields: each field falls back to its default, so a broken
 * document never breaks the lists. The documents are written by hand, as an old version or a manual edit could leave them.
 */
describe("registrations with malformed documents", () => {
  const tournamentId = `malformed-${Date.now()}`;
  const ref = db.doc(`tournaments/${tournamentId}`);
  const registrations = ref.collection("registrations");

  before(async () => {
    setMailTransportForTests(async () => {});
    // No name, no contact email, no host country: only what makes it a tournament with open registrations.
    await ref.set({ isTeams: true, drawPending: true, registration: { status: "OPEN" } });
  });
  after(() => setMailTransportForTests(null));

  it("lists registrations and teams without their fields", async () => {
    await registrations.doc("07660001").set({ status: "CONFIRMED", language: "xx", createdAt: Timestamp.fromMillis(86_400_000) });
    await registrations.doc("07660002").set({ status: "WAITING", order: 0 });
    await registrations.doc("07660003").set({ status: "CONFIRMED", order: 5, email: "same@example.test", teamId: "team-a" });
    await registrations.doc("07660004").set({ status: "CONFIRMED", order: 5, email: "same@example.test" });
    await registrations.doc("07660005").set({ status: "PENDING" });
    await registrations.doc("row-12").set({ status: "CONFIRMED", order: 9, importFood: "Vegan", problems: ["INVALID_EMA"], fileEmaId: "12x" });
    await ref.collection("registrationTeams").doc("team-a").set({});
    await ref.collection("registrationTeams").doc("team-b").set({ name: "B" });
    await db.doc("personPrivate/07660001").set({});
    await db.doc("emaIdIndex/07660002").set({});
    await db.doc("emaPlayerRegistry/07660003").set({ firstName: "Ana", photoUrl: "https://example.test/ana.png" });

    const admin = await adminRegistrations(tournamentId);
    const byId = new Map(admin.registrations.map((registration) => [registration.emaId, registration]));
    assert.equal(byId.has("07660005"), false, "a pending registration without an end is gone");
    const first = byId.get("07660001");
    assert.equal(first?.name, "");
    assert.equal(first?.country, "");
    assert.equal(first?.email, "");
    assert.equal(first?.source, "SELF");
    assert.equal(first?.foodRestrictions, "");
    assert.equal(first?.registeredAt, new Date(86_400_000).toISOString());
    assert.equal(byId.get("07660002")?.registeredAt, new Date(0).toISOString());
    assert.equal(byId.get("07660002")?.hasAccount, true);
    assert.equal(byId.get("07660003")?.sharedEmail, true);
    assert.equal(byId.get("07660003")?.photoUrl, "https://example.test/ana.png");
    assert.deepEqual([byId.get("07660003")?.position, byId.get("07660004")?.position], [2, 3]);
    assert.equal(byId.get("row-12")?.foodRestrictions, "Vegan");
    assert.deepEqual(byId.get("row-12")?.problems, ["INVALID_EMA"]);
    assert.deepEqual(admin.teams.map((team) => [team.name, team.captainEmaId, team.members]), [["", "", ["07660003"]], ["B", "", []]]);

    const shown = await publicRegistrations(tournamentId);
    assert.equal(shown.registrations.find((registration) => registration.emaId === "07660002")?.status, "WAITING");
  });

  it("finds no account behind an index without a user", async () => {
    await db.doc("emaIdIndex/07660010").set({});
    await db.doc("emaIdIndex/07660011").set({ uid: "nobody-has-this-uid" });
    assert.equal(await accountOfEma("07660010"), null);
    assert.equal(await accountOfEma("07660011"), null);
    assert.equal(await accountOfEma("07660012"), null);
  });

  it("refuses a disabled EMA player, an id that is not one, and food without consent", async () => {
    await db.doc("emaPlayerRegistry/07660020").set({ firstName: "Old", lastName: "Player", disabled: true });
    await assert.rejects(registryPerson("07660020"), refusedWith("EMA_NOT_FOUND"));
    assert.throws(() => validateRegistrationId(undefined));
    assert.equal(validateRegistrationId(" row-7 "), "row-7");
    await assert.rejects(saveFoodRestrictions("07660020", "Nuts", false), refusedWith("CONSENT_REQUIRED"));
    assert.equal(emailHint("no-domain"), "");
    await assert.rejects(requestWithoutEmaNumber({ tournamentId, name: undefined, email: "a@example.test", country: "", message: "" } as never),
      refusedWith("INVALID_NAME"));
  });

  it("refuses an old link and confirms one without food", async () => {
    await registrations.doc("07660030").set({ status: "PENDING", manageToken: "old-link" });
    // A pending registration past its end is out of the lists: it has no position.
    const old = await ownRegistration({ tournamentId, uid: null, token: "old-link" });
    assert.equal(old.position, 0);
    assert.equal(old.foodRestrictions, "");
    await assert.rejects(confirmRegistration({ tournamentId, token: "old-link" }), refusedWith("LINK_EXPIRED"));

    await db.doc("emaPlayerRegistry/07660031").set({ firstName: "New", lastName: "Player", country: "ES" });
    await registrations.doc("07660031").set({ status: "PENDING", manageToken: "fresh-link", pendingUntil: Date.now() + 60_000 });
    const pending = await ownRegistration({ tournamentId, uid: null, token: "fresh-link" });
    assert.equal(pending.status, "PENDING");
    assert.equal(pending.position, 1);
    const confirmed = await confirmRegistration({ tournamentId, token: "fresh-link" });
    assert.equal(confirmed.status, "CONFIRMED");
  });

  it("adds a person over its pending registration without a token, with food and a team", async () => {
    await db.doc("emaPlayerRegistry/07660040").set({ firstName: "Team", lastName: "Mate", country: "ES" });
    await registrations.doc("07660040").set({ status: "PENDING", pendingUntil: Date.now() + 60_000 });
    const added = await adminAddRegistration({
      tournamentId, emaId: "07660040", email: "", foodRestrictions: "Gluten free", teamName: "Dragons", language: "en",
    });
    assert.equal(added.email, "");
    assert.equal(added.foodRestrictions, "Gluten free");
    assert.notEqual(added.teamId, null);
    const stored = await registrations.doc("07660040").get();
    assert.notEqual(stored.get("manageToken"), "");

    // The same team again changes nothing.
    const again = await adminUpdateRegistration({ tournamentId, emaId: "07660040", teamName: "dragons" });
    assert.equal(again.teamId, added.teamId);
  });

  it("registers an account without an email, and adds a person without one", async () => {
    const user = await auth.createUser({});
    await db.doc("emaPlayerRegistry/07660050").set({ firstName: "No", lastName: "Mail", country: "ES" });
    await db.doc(`users/${user.uid}`).set({ emaId: "07660050" });
    await db.doc("emaIdIndex/07660050").set({ uid: user.uid });
    assert.deepEqual(await accountOfEma("07660050"), { uid: user.uid, email: null });
    const result = await register({
      tournamentId, uid: user.uid, emaId: undefined, email: undefined, foodRestrictions: "", consent: false, language: "en",
    });
    assert.equal(result.status, "CONFIRMED");
    assert.equal(result.emailHint, "");

    await db.doc("emaPlayerRegistry/07660051").set({ firstName: "Also", lastName: "Without", country: "ES" });
    const added = await adminAddRegistration({ tournamentId, emaId: "07660051", email: undefined, language: "en" });
    assert.equal(added.email, "");
  });

  it("puts a confirmed link on the waiting list when the places are full", async () => {
    const fullId = `${tournamentId}-full`;
    const fullRef = db.doc(`tournaments/${fullId}`);
    await fullRef.set({ isTeams: false, drawPending: true, registration: { status: "OPEN", maxPlayers: 1 } });
    await fullRef.collection("registrations").doc("07660060").set({ status: "CONFIRMED", order: 1 });
    await db.doc("emaPlayerRegistry/07660061").set({ firstName: "Late", lastName: "Comer", country: "ES" });
    await fullRef.collection("registrations").doc("07660061").set({
      status: "PENDING", manageToken: "late-link", pendingUntil: Date.now() + 60_000, email: "late@example.test",
    });
    const confirmed = await confirmRegistration({ tournamentId: fullId, token: "late-link" });
    assert.equal(confirmed.status, "WAITING");
  });

  it("cancels a pending registration and one whose team is gone", async () => {
    await registrations.doc("07660070").set({ status: "PENDING", manageToken: "cancel-pending", pendingUntil: Date.now() + 60_000 });
    await cancelOwnRegistration({ tournamentId, uid: null, token: "cancel-pending" });
    assert.equal((await registrations.doc("07660070").get()).exists, false);

    await registrations.doc("07660071").set({ status: "CONFIRMED", order: 1, manageToken: "cancel-lost-team", teamId: "gone" });
    await cancelOwnRegistration({ tournamentId, uid: null, token: "cancel-lost-team" });
    assert.equal((await registrations.doc("07660071").get()).exists, false);
  });

  it("makes and fills a team next to teams without a name", async () => {
    await registrations.doc("07660080").set({ status: "CONFIRMED", order: 1, manageToken: "captain-link" });
    const made = await createOwnTeam({ tournamentId, uid: null, token: "captain-link", name: "Tigers" });
    assert.notEqual(made.teamId, null);

    await ref.collection("registrationTeams").doc("nameless").set({ captainEmaId: "07660081" });
    await registrations.doc("07660081").set({ status: "CONFIRMED", order: 2, manageToken: "nameless-captain", teamId: "nameless" });
    await registrations.doc("07660082").set({ status: "CONFIRMED", order: 3, teamId: "nameless" });
    const view = await addOwnTeamMember({ tournamentId, uid: null, token: "nameless-captain", emaId: "07660082" });
    assert.equal(view.teamId, "nameless");
  });

  it("reads links, pending ends and the log without their fields", async () => {
    await assert.rejects(freeSlug([]), refusedWith("SLUG_TAKEN"));
    await db.doc("registrationSlugs/malformed-slug").set({});
    await assert.rejects(tournamentOfSlug("Malformed-Slug"), /Registration link not found/);

    const tournament = await loadTournament(tournamentId);
    await registrations.doc("07660090").set({ status: "PENDING" });
    assert.ok(await removeExpiredPending(tournament, Date.now()) >= 1);
    assert.equal((await registrations.doc("07660090").get()).exists, false);

    await ref.collection("registrationLog").add({ kind: "registered", emaId: "07660091", at: Timestamp.now() });
    const changes = await recentChanges(tournament, Date.now() - 60_000);
    assert.ok(changes.registered.includes(" (07660091)"), JSON.stringify(changes));

    assert.deepEqual(await registrationsOfEma(""), []);
    assert.ok((await registrationsOfEma("07660001")).some((item) => item.tournamentId === tournamentId));
  });
});

/** An import of a file whose rows miss times, names or numbers, and the admin changes of registrations that are gone. */
describe("registrations of an import with missing fields", () => {
  const tournamentId = `malformed-import-${Date.now()}`;
  const ref = db.doc(`tournaments/${tournamentId}`);
  const registrations = ref.collection("registrations");

  before(async () => {
    setMailTransportForTests(async () => {});
    await ref.set({ isTeams: true, drawPending: true, registration: { status: "OPEN" } });
  });
  after(() => setMailTransportForTests(null));

  it("names and orders the rows without a time or a name, and leaves a fifth person out of a full team", async () => {
    const csv = [
      "Timestamp,Email,First name,Last name,EMA number,Team",
      ",,,,ABC,",
      "15/09/2026 18:00:00,nameless@example.test,,,XYZ,",
      ",,Ana,One,07670001,Full",
      "15/09/2026 18:01:00,,Bea,Two,07670002,Full",
      ",,Cris,Three,07670003,Full",
      "15/09/2026 18:02:00,,Dani,Four,07670004,Full",
      ",,Eva,Five,07670005,Full",
    ].join("\n");
    const result = await importRegistrations({ tournamentId, csv, apply: true, language: "en" });
    assert.equal(result.imported, 7);

    const byId = new Map((await registrations.get()).docs.map((doc) => [doc.id, doc.data()]));
    assert.equal(byId.get("row-2")?.name, "row-2");
    assert.equal(byId.get("row-3")?.name, "nameless@example.test");
    assert.equal(byId.get("07670001")?.name, "Ana One");
    const inTeam = ["07670001", "07670002", "07670003", "07670004", "07670005"].filter((id) => byId.get(id)?.teamId != null);
    assert.equal(inTeam.length, 4);
  });

  it("takes a person out of a team with an empty team name, and removes a pending registration without a log", async () => {
    const view = await adminUpdateRegistration({ tournamentId, emaId: "07670001", teamName: null });
    assert.equal(view.teamId ?? null, null);

    await registrations.doc("07670010").set({ status: "PENDING", order: 1 });
    await adminRemoveRegistration({ tournamentId, emaId: "07670010" });
    assert.equal((await registrations.doc("07670010").get()).exists, false);
    const log = await ref.collection("registrationLog").get();
    assert.equal(log.docs.some((doc) => doc.get("emaId") === "07670010"), false);
  });

  it("finds no registration link of a tournament that is gone or private", async () => {
    await db.doc("registrationSlugs/malformed-gone").set({ tournamentId: "malformed-no-such-tournament" });
    await assert.rejects(tournamentOfSlug("malformed-gone"), /Registration link not found/);
    await db.doc(`tournaments/${tournamentId}-private`).set({ isPublic: false });
    await db.doc("registrationSlugs/malformed-private").set({ tournamentId: `${tournamentId}-private` });
    await assert.rejects(tournamentOfSlug("malformed-private"), /Registration link not found/);
  });

  it("skips people of a player count change who have no registration", async () => {
    await applyPlayerCountToRegistrations({ tournamentId, removedEmaIds: ["07670098"], promotedEmaIds: ["07670099"], numPlayers: 8 });
    assert.equal((await registrations.doc("07670099").get()).exists, false);
  });
});
