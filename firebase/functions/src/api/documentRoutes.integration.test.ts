import assert from "node:assert/strict";
import { after, before, describe, it } from "node:test";

import { db } from "../firebase";
import { createTournament } from "../services/tournamentsService";
import { createAccount, requireAuthEmulator, seatEmaId, startServer, type TestAccount, type TestServer } from "../testing/authFixtures";
import { validDrawTables } from "../testing/draw";
import { fullRoundSchedules } from "../testing/schedule";

/** The printed documents of a tournament through their routes: the sheet layout of the ID cards, the QR cards and the EMA report. */
describe("document routes", () => {
  let server: TestServer;
  let admin: TestAccount;
  let tournamentId = "";

  before(async () => {
    requireAuthEmulator();
    server = await startServer();
    admin = await createAccount({ role: "SUPER_ADMIN" });
    const tournament = await createTournament({
      name: "Documents open", shortName: "DOC", eventStartDate: "2026-10-01", eventEndDate: "2026-10-02",
      hostCountry: "ES", hostCity: "Madrid", isTeams: false, numPlayers: 16, numRounds: 1, numTries: 0,
      players: Array.from({ length: 16 }, (_, index) => ({ id: index + 1, team: 0, name: `Player ${index + 1}`, country: "ES" })),
      tables: validDrawTables(16, 1),
      roundSchedules: fullRoundSchedules(1) as Array<{ roundId: number; date: string | null; startTime: string | null }>,
      createdByUid: "creator",
    });
    tournamentId = tournament.id;
    for (let playerId = 1; playerId <= 16; playerId += 1) {
      const emaId = `0777${String(playerId).padStart(4, "0")}`;
      await db.doc(`emaPlayerRegistry/${emaId}`).set({ firstName: "Doc", lastName: `Player ${playerId}`, country: "ES" });
      await seatEmaId(tournamentId, playerId, emaId);
    }
  });
  after(async () => {
    await server.close();
  });

  const get = (path: string) => server.request("GET", `/tournaments/${tournamentId}${path}`, { token: admin.token });
  const message = (body: unknown) => JSON.stringify(body);

  it("makes the ID cards on sheets with every option, and refuses the options out of range", async () => {
    const sheet = await get("/id-cards?layout=sheet&cardsPerSheet=8&scalePercent=90&landscape=true&doubleSided=false&cutMarks=false"
      + "&backs=false&backSameAsFront=true&substituteSheets=1&staffSheets=1&staffName=Ana&staffName=Luis");
    assert.equal(sheet.status, 200, message(sheet.body));
    assert.equal((await get("/id-cards?layout=sheet&staffName=Eva")).status, 200);
    assert.equal((await get("/id-cards")).status, 200);

    const refused = async (query: string, pattern: RegExp) => {
      const response = await get(`/id-cards?layout=sheet&${query}`);
      assert.equal(response.status, 400, query);
      assert.match(message(response.body), pattern, query);
    };
    await refused("cardsPerSheet=0", /cards per sheet/);
    await refused("cardsPerSheet=2.5", /cards per sheet/);
    await refused("scalePercent=10", /card size/);
    await refused("scalePercent=abc", /card size/);
    await refused("substituteSheets=-1", /substitute sheets/);
    await refused("staffSheets=x", /staff sheets/);
    await refused(`staffName=${"a".repeat(200)}`, /staff name/);
    await refused("scalePercent=400&landscape=false", /does not fit/);
  });

  it("makes the QR cards of the tables", async () => {
    const response = await get("/table-qr-codes?perSheet=2");
    assert.equal(response.status, 200, message(response.body));
  });

  it("makes the score sheets, blank or for every table of every round", async () => {
    const blank = await get("/score-sheets");
    assert.equal(blank.status, 200, message(blank.body));
    const tables = await get("/score-sheets?mode=tables&ema=false");
    assert.equal(tables.status, 200, message(tables.body));
  });

  it("makes the EMA report and refuses rows that are not rankings", async () => {
    const row = (playerId: number, place: number) => ({ playerId, place, tablePoints: 4, score: 10 });
    const post = (rows: unknown) => server.request("POST", `/tournaments/${tournamentId}/ema-report`, { token: admin.token, body: { rows } });

    const report = await post(Array.from({ length: 16 }, (_, index) => row(index + 1, index + 1)));
    assert.equal(report.status, 200, message(report.body));
    assert.equal((await post([null])).status, 400);
    assert.equal((await post([row(0, 1)])).status, 400);
    assert.equal((await post([{ ...row(1, 1), score: "x" }])).status, 400);
    assert.equal((await post([{ ...row(1, 1), place: 0 }])).status, 400);
  });

  it("makes a proof of the ID card with a logo of its own, a logo link and a schedule", async () => {
    const png = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==";
    const proof = (body: unknown) => server.request("POST", "/tournaments/id-card-proof", { token: admin.token, body });
    const base = { shortName: "OPEN", year: "2026", primaryColor: "#112233" };

    const withLogo = await proof({ ...base, associationLogoContentType: "image/png", associationLogoDataBase64: png });
    assert.equal(withLogo.status, 200, message(withLogo.body));
    const withLink = await proof({
      ...base, associationLogoUrl: "https://example.test/logo.png",
      roundSchedules: [{ roundId: 1, date: "2026-10-01", startTime: "10:00" }, { roundId: 2, date: "later" }, null],
    });
    assert.equal(withLink.status, 200, message(withLink.body));
  });

  it("refuses a proof of the ID card with wrong fields or a logo too large", async () => {
    const proof = (body: unknown) => server.request("POST", "/tournaments/id-card-proof", { token: admin.token, body });
    assert.equal((await proof({ shortName: "x".repeat(40), year: "2026", primaryColor: "#112233" })).status, 400);
    assert.equal((await proof({ shortName: "OPEN", year: "2026", primaryColor: "red" })).status, 400);
    assert.equal((await proof({ shortName: "OPEN", year: "2026", primaryColor: "#112233", associationLogoDataBase64: "A".repeat(3_000_001) })).status, 400);
  });
});
