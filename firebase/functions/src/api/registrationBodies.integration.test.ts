import assert from "node:assert/strict";
import { randomUUID } from "node:crypto";
import { after, before, describe, it } from "node:test";

import { createAccount, requireAuthEmulator, startServer, type TestAccount, type TestServer } from "../testing/authFixtures";
import { seedOrganization } from "../testing/firestoreFixtures";
import { fullRoundSchedules } from "../testing/schedule";
import { createTournament } from "../services/tournamentsService";
import { validDrawTables } from "../testing/draw";

/** The routes of the players and the registrations of a tournament answer a request without a body with a refusal, not an error. */
describe("registration routes without a body", () => {
  let server: TestServer;
  let admin: TestAccount;
  let registrationsId = "";
  let drawnId = "";

  before(async () => {
    requireAuthEmulator();
    server = await startServer();
    admin = await createAccount({ role: "SUPER_ADMIN" });
    await seedOrganization({ id: "org-bodies" });
    const created = await server.request("POST", "/tournaments", {
      token: admin.token,
      body: {
        name: "Bodies", shortName: "BOD", organizerIds: ["org-bodies"], eventStartDate: "2027-03-20", eventEndDate: "2027-03-20",
        hostCountry: "ES", hostCity: "Madrid", venueName: "Club", venueAddress: "Calle 1", contactEmail: "org@example.test",
        isTeams: false, numRounds: 1, roundSchedules: fullRoundSchedules(1, "2027-03-20"), timeZone: "Europe/Madrid",
        registration: { maxPlayers: null, closesOn: null },
      },
    });
    assert.equal(created.status, 200, JSON.stringify(created.body));
    registrationsId = (created.body as { id: string }).id;
    drawnId = (await createTournament({
      name: "Drawn", shortName: "DRW", eventStartDate: "2027-03-20", eventEndDate: "2027-03-20", hostCountry: "ES", hostCity: "Madrid",
      isTeams: false, numPlayers: 16, numRounds: 1, numTries: 0,
      players: Array.from({ length: 16 }, (_, index) => ({ id: index + 1, team: 0 })),
      tables: validDrawTables(16, 1), roundSchedules: fullRoundSchedules(1, "2027-03-20") as never, createdByUid: "creator",
    })).id;
  });
  after(async () => {
    await server.close();
  });

  it("refuses each request without a body", async () => {
    const calls: Array<[string, string]> = [
      ["POST", `/tournaments/${drawnId}/player-import/preview`],
      ["POST", `/tournaments/${drawnId}/player-import/apply`],
      ["POST", `/tournaments/${registrationsId}/registrations`],
      ["POST", `/tournaments/${registrationsId}/registrations/import`],
      ["PUT", `/tournaments/${registrationsId}/registrations/07720001`],
      ["PUT", `/tournaments/${registrationsId}/registration-settings`],
      ["POST", `/tournaments/${registrationsId}/draw`],
      ["POST", `/tournaments/${drawnId}/player-count`],
    ];
    const answers: string[] = [];
    for (const [method, path] of calls) {
      const response = await server.request(method, path, { token: admin.token, noBody: true });
      answers.push(`${method} ${path.split("/").slice(3).join("/")} ${response.status} ${JSON.stringify(response.body)}`);
    }
    assert.deepEqual(answers.filter((answer) => / 5\d\d /.test(answer)), [], answers.join("\n"));
  });

  it("saves a pending email that is missing as none and reads a file without a name", async () => {
    const emails = await server.request("PUT", `/tournaments/${drawnId}/player-emails`, { token: admin.token, body: { players: [{ playerId: 1 }] } });
    assert.equal(emails.status, 200, JSON.stringify(emails.body));
    const csv = Buffer.from(`slot,ema\n1,0772${randomUUID().replace(/\D/g, "").slice(0, 4).padEnd(4, "0")}\n`).toString("base64");
    const preview = await server.request("POST", `/tournaments/${drawnId}/player-import/preview`, { token: admin.token, body: { dataBase64: csv } });
    assert.notEqual(preview.status, 500, JSON.stringify(preview.body));
  });
});
