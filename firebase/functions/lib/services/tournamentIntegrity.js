"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.checkTournamentIntegrity = checkTournamentIntegrity;
/**
 * Integrity checks for one tournament. The check is pure: it reads the data that the caller gives
 * and returns a list of findings. `tournamentIntegrityService.ts` loads the data.
 *
 * Severity:
 * - error: the data is wrong or a feature fails (for example ID cards or statistics).
 * - warning: the data is odd and a person should look at it.
 * - info: a fact that can be normal.
 */
const tournamentDates_1 = require("./tournamentDates");
const handScoring_1 = require("./handScoring");
const tableManagerService_1 = require("./tableManagerService");
const text = (value) => String(value ?? "").trim();
const isInteger = (value) => /^-?\d+$/.test(text(value));
const TIME_PATTERN = /^(?:[01]\d|2[0-3]):[0-5]\d$/;
const MAX_FINDINGS_PER_CODE = 25;
class Collector {
    constructor() {
        this.findings = [];
        this.perCode = new Map();
        this.hidden = new Map();
    }
    add(severity, code, message, where = null) {
        const seen = this.perCode.get(code) ?? 0;
        this.perCode.set(code, seen + 1);
        if (seen >= MAX_FINDINGS_PER_CODE) {
            const current = this.hidden.get(code) ?? { severity, count: 0 };
            current.count += 1;
            this.hidden.set(code, current);
            return;
        }
        this.findings.push({ severity, code, message, where });
    }
    /** Adds one line for each code that had more findings than the limit. */
    finish() {
        this.hidden.forEach((value, code) => {
            this.findings.push({
                severity: value.severity,
                code,
                message: `${value.count} more findings of this kind are not listed.`,
                where: null,
            });
        });
        const order = { error: 0, warning: 1, info: 2 };
        return this.findings.sort((a, b) => order[a.severity] - order[b.severity] || a.code.localeCompare(b.code));
    }
}
function checkTournamentIntegrity(tournamentId, input, now = new Date()) {
    const c = new Collector();
    const { tournament } = input;
    const numPlayers = Number(tournament.numPlayers);
    const numRounds = Number(tournament.numRounds);
    checkTournamentInfo(c, input, numPlayers, numRounds);
    const slotIds = checkPlayers(c, input, numPlayers);
    checkTeams(c, input, slotIds);
    checkRounds(c, input, numRounds);
    checkTables(c, input, numPlayers, numRounds, slotIds);
    checkTournamentCounters(c, input);
    const findings = c.finish();
    return {
        tournamentId,
        checkedAt: now.toISOString(),
        counts: {
            errors: findings.filter((f) => f.severity === "error").length,
            warnings: findings.filter((f) => f.severity === "warning").length,
            infos: findings.filter((f) => f.severity === "info").length,
        },
        findings,
    };
}
// -- Tournament info -------------------------------------------------------------------------
function checkTournamentInfo(c, input, numPlayers, numRounds) {
    const t = input.tournament;
    if (text(t.name) === "")
        c.add("error", "info.name", "The tournament has no name.");
    if (text(t.shortName) === "")
        c.add("warning", "info.shortName", "The tournament has no short name.");
    if (!Number.isInteger(numPlayers) || numPlayers <= 0 || numPlayers % 4 !== 0) {
        c.add("error", "info.numPlayers", `The number of players (${text(t.numPlayers)}) must be a positive multiple of 4.`);
    }
    if (!Number.isInteger(numRounds) || numRounds <= 0) {
        c.add("error", "info.numRounds", `The number of rounds (${text(t.numRounds)}) must be a positive number.`);
    }
    const start = text(t.eventStartDate);
    const end = text(t.eventEndDate);
    if (!(0, tournamentDates_1.isValidIsoDateRange)(start, end)) {
        c.add("error", "info.dates", `The event dates are not valid (${start || "empty"} to ${end || "empty"}).`);
    }
    if (text(t.hostCity) === "")
        c.add("warning", "info.hostCity", "The host city is empty.");
    if (!/^[A-Z]{2}$/.test(text(t.hostCountry))) {
        c.add("warning", "info.hostCountry", `The host country "${text(t.hostCountry)}" is not a two letter code.`);
    }
    if (text(t.venueName) === "")
        c.add("warning", "info.venueName", "The venue name is empty.");
    if (text(t.venueAddress) === "")
        c.add("warning", "info.venueAddress", "The venue address is empty.");
    const map = text(t.venueMapUrl);
    if (map !== "") {
        try {
            const url = new URL(map);
            if (url.protocol !== "https:")
                c.add("warning", "info.venueMapUrl", "The map link does not use https.");
        }
        catch (_error) {
            c.add("warning", "info.venueMapUrl", "The map link is not a valid link.");
        }
    }
    if (typeof t.countBestHands !== "boolean")
        c.add("info", "info.countBestHands", "The best hands switch has no value.");
    if (typeof t.countChickenHands !== "boolean")
        c.add("info", "info.countChickenHands", "The chicken hands switch has no value.");
    const schedules = Array.isArray(t.roundSchedules) ? t.roundSchedules : [];
    if (!Array.isArray(t.roundSchedules) || (Number.isInteger(numRounds) && schedules.length !== numRounds)) {
        c.add("warning", "schedule.rows", `The schedule has ${schedules.length} rows for ${numRounds} rounds.`);
    }
    let previous = "";
    schedules.forEach((row) => {
        const where = `round ${text(row.roundId)}`;
        const date = text(row.date);
        const time = text(row.startTime);
        if (date === "" && time === "") {
            c.add("info", "schedule.empty", "The round has no date and no start time.", where);
            return;
        }
        if (date !== "" && !(0, tournamentDates_1.isValidIsoDate)(date))
            c.add("error", "schedule.date", `The date "${date}" is not valid.`, where);
        if (time !== "" && !TIME_PATTERN.test(time))
            c.add("error", "schedule.time", `The time "${time}" is not valid.`, where);
        if (date !== "" && (0, tournamentDates_1.isValidIsoDate)(start) && (0, tournamentDates_1.isValidIsoDate)(end) && (date < start || date > end)) {
            c.add("warning", "schedule.range", `The date ${date} is outside the event dates.`, where);
        }
        if (date !== "" && time !== "") {
            const stamp = `${date}T${time}`;
            if (previous !== "" && stamp < previous)
                c.add("warning", "schedule.order", "The round starts before the previous round.", where);
            previous = stamp;
        }
    });
    const agenda = Array.isArray(t.agendaItems) ? t.agendaItems : [];
    agenda.forEach((item, index) => {
        const date = text(item.date);
        const where = `agenda item ${index + 1}`;
        if (text(item.title) === "")
            c.add("warning", "agenda.title", "The agenda item has no title.", where);
        if (date !== "" && !(0, tournamentDates_1.isValidIsoDate)(date))
            c.add("error", "agenda.date", `The date "${date}" is not valid.`, where);
        if (date !== "" && (0, tournamentDates_1.isValidIsoDate)(start) && (0, tournamentDates_1.isValidIsoDate)(end) && (date < start || date > end)) {
            c.add("warning", "agenda.range", `The date ${date} is outside the event dates.`, where);
        }
        const time = text(item.startTime);
        if (time !== "" && !TIME_PATTERN.test(time))
            c.add("error", "agenda.time", `The time "${time}" is not valid.`, where);
    });
}
// -- Players ---------------------------------------------------------------------------------
/** Returns the set of slot ids. */
function checkPlayers(c, input, numPlayers) {
    const slots = input.players;
    const ids = new Set();
    if (Number.isInteger(numPlayers) && slots.length !== numPlayers) {
        c.add("error", "players.count", `The tournament has ${slots.length} player slots but numPlayers is ${numPlayers}.`);
    }
    const emaOwners = new Map();
    slots.forEach(({ id, data }) => {
        const where = `player ${id}`;
        const numeric = Number(data.id ?? id);
        if (Number(id) !== numeric)
            c.add("error", "players.id", `The slot document id ${id} differs from its id field ${text(data.id)}.`, where);
        if (ids.has(numeric))
            c.add("error", "players.duplicateId", "Two slots have the same id.", where);
        ids.add(numeric);
        if (Number.isInteger(numPlayers) && (numeric < 1 || numeric > numPlayers)) {
            c.add("warning", "players.idRange", `The slot id ${numeric} is outside 1 to ${numPlayers}.`, where);
        }
        const ema = text(data.assignedEmaId);
        const hasNonMember = data.nonMember != null;
        if (ema === "" && !hasNonMember) {
            c.add("error", "players.unassigned", "The slot has no assigned player. ID cards and statistics need one.", where);
        }
        if (ema !== "" && hasNonMember) {
            c.add("warning", "players.both", "The slot has an EMA player and a non-member player at the same time.", where);
        }
        if (text(data.name) === "")
            c.add("warning", "players.name", "The slot has no name.", where);
        if (ema !== "") {
            if (!/^\d{8}$/.test(ema))
                c.add("warning", "players.emaFormat", `The EMA number "${ema}" does not have 8 digits.`, where);
            emaOwners.set(ema, [...(emaOwners.get(ema) ?? []), id]);
            const registry = input.registry.get(ema);
            if (registry == null) {
                c.add("error", "players.notInRegistry", `The EMA number ${ema} is not in the player registry.`, where);
            }
            else {
                const assignedCountry = text(data.assignedCountry).toUpperCase();
                if (registry.country !== "" && assignedCountry !== "" && assignedCountry !== registry.country) {
                    c.add("info", "players.country", `The slot country ${assignedCountry} differs from the registry country ${registry.country}.`, where);
                }
            }
        }
        if (hasNonMember) {
            const nonMember = data.nonMember;
            if (text(nonMember.firstName ?? nonMember.name) === "" && text(data.name) === "") {
                c.add("warning", "players.nonMemberName", "The non-member player has no name.", where);
            }
        }
        const team = Number(data.team);
        if (input.tournament.isTeams === true && (!Number.isInteger(team) || team < 1)) {
            c.add("error", "players.team", "The tournament has teams, but the slot has no valid team.", where);
        }
    });
    emaOwners.forEach((owners, ema) => {
        if (owners.length > 1) {
            c.add("error", "players.duplicateEma", `The EMA number ${ema} is assigned to slots ${owners.join(", ")}.`);
        }
    });
    return ids;
}
// -- Teams -----------------------------------------------------------------------------------
function checkTeams(c, input, slotIds) {
    const isTeams = input.tournament.isTeams === true;
    if (!isTeams) {
        if (input.teams.length > 0)
            c.add("info", "teams.unused", `The tournament has no teams mode, but ${input.teams.length} team documents exist.`);
        return;
    }
    const members = new Map();
    input.players.forEach(({ data }) => {
        const team = Number(data.team);
        members.set(team, [...(members.get(team) ?? []), Number(data.id)]);
    });
    const documents = new Map(input.teams.map(({ id, data }) => [Number(data.id ?? id), data]));
    members.forEach((slotsOfTeam, team) => {
        const where = `team ${team}`;
        const document = documents.get(team);
        if (document == null) {
            c.add("error", "teams.missing", `The players use team ${team}, but the team document does not exist.`, where);
            return;
        }
        if (text(document.name) === "")
            c.add("warning", "teams.name", "The team has no name.", where);
        const listed = (document.playerIds ?? []).map(Number).sort((a, b) => a - b);
        const actual = [...slotsOfTeam].sort((a, b) => a - b);
        if (listed.join(",") !== actual.join(",")) {
            c.add("warning", "teams.playerIds", `The team lists players ${listed.join(", ") || "none"} but the slots say ${actual.join(", ")}.`, where);
        }
        listed.forEach((id) => {
            if (!slotIds.has(id))
                c.add("error", "teams.unknownPlayer", `The team lists player ${id}, who has no slot.`, where);
        });
    });
    documents.forEach((_document, team) => {
        if (!members.has(team))
            c.add("warning", "teams.empty", `The team ${team} has no players.`, `team ${team}`);
    });
    const sizes = new Set([...members.values()].map((list) => list.length));
    if (sizes.size > 1)
        c.add("warning", "teams.sizes", `The teams have different sizes (${[...sizes].sort().join(", ")}).`);
}
// -- Rounds ----------------------------------------------------------------------------------
function checkRounds(c, input, numRounds) {
    if (!Number.isInteger(numRounds))
        return;
    const ids = input.rounds.map(({ id, data }) => Number(data.roundId ?? id));
    if (ids.length !== numRounds)
        c.add("error", "rounds.count", `The tournament has ${ids.length} round documents but numRounds is ${numRounds}.`);
    for (let round = 1; round <= numRounds; round += 1) {
        if (!ids.includes(round))
            c.add("error", "rounds.missing", `The round document ${round} is missing.`, `round ${round}`);
    }
    if (new Set(ids).size !== ids.length)
        c.add("error", "rounds.duplicate", "Two round documents have the same round id.");
}
// -- Tables ----------------------------------------------------------------------------------
function penaltyParts(value) {
    const parts = text(value).split(";").map((part) => part.trim()).filter((part) => part !== "");
    if (parts.some((part) => !/^-?\d+$/.test(part)))
        return null;
    return parts.map(Number);
}
function normalizedPoints(value) {
    return text(value).replace(",", ".");
}
function checkTables(c, input, numPlayers, numRounds, slotIds) {
    const tablesPerRound = Number.isInteger(numPlayers) ? numPlayers / 4 : NaN;
    if (Number.isInteger(numRounds) && Number.isInteger(tablesPerRound) && input.tables.length !== numRounds * tablesPerRound) {
        c.add("error", "tables.count", `The tournament has ${input.tables.length} tables but ${numRounds} rounds with ${tablesPerRound} tables need ${numRounds * tablesPerRound}.`);
    }
    const appearances = new Map();
    const meetings = new Map();
    const keys = new Set();
    input.tables.forEach(({ id, data, hands }) => {
        const roundId = Number(data.roundId);
        const tableId = Number(data.tableId);
        const where = `table ${id}`;
        if (id !== `${roundId}_${tableId}`)
            c.add("error", "tables.id", `The document id ${id} differs from round ${roundId} and table ${tableId}.`, where);
        if (keys.has(id))
            c.add("error", "tables.duplicate", "Two documents describe the same table.", where);
        keys.add(id);
        if (Number.isInteger(numRounds) && (roundId < 1 || roundId > numRounds))
            c.add("error", "tables.round", `The round ${roundId} does not exist.`, where);
        if (Number.isInteger(tablesPerRound) && (tableId < 1 || tableId > tablesPerRound)) {
            c.add("warning", "tables.tableId", `The table number ${tableId} is outside 1 to ${tablesPerRound}.`, where);
        }
        const version = Number(data.version ?? 0);
        if (!Number.isInteger(version) || version < 0)
            c.add("warning", "tables.version", `The version "${text(data.version)}" is not a number.`, where);
        // Assignment: the four players of the table.
        const assigned = (data.playerIds ?? []).map(Number);
        if (assigned.length !== 4 || new Set(assigned).size !== 4) {
            c.add("error", "tables.playerIds", `The table has ${assigned.length} assigned players. It needs four different ones.`, where);
        }
        else {
            assigned.forEach((player) => {
                if (!slotIds.has(player))
                    c.add("error", "tables.unknownPlayer", `The table lists player ${player}, who has no slot.`, where);
                const key = `${roundId}:${player}`;
                appearances.set(key, [...(appearances.get(key) ?? []), tableId]);
            });
            for (let a = 0; a < 4; a += 1) {
                for (let b = a + 1; b < 4; b += 1) {
                    const pair = [assigned[a], assigned[b]].sort((x, y) => x - y).join("-");
                    meetings.set(pair, (meetings.get(pair) ?? 0) + 1);
                }
            }
        }
        checkTableScores(c, data, hands, assigned, where);
    });
    // Every player must sit once in every round.
    if (Number.isInteger(numRounds)) {
        for (let round = 1; round <= numRounds; round += 1) {
            slotIds.forEach((player) => {
                const seen = appearances.get(`${round}:${player}`) ?? [];
                if (seen.length === 0 && input.tables.length > 0) {
                    c.add("warning", "tables.playerMissing", `Player ${player} has no table in round ${round}.`, `round ${round}`);
                }
                else if (seen.length > 1) {
                    c.add("error", "tables.playerTwice", `Player ${player} is at tables ${seen.join(", ")} in round ${round}.`, `round ${round}`);
                }
            });
        }
    }
    const repeated = [...meetings.entries()].filter(([, count]) => count > 1);
    if (repeated.length > 0) {
        const worst = Math.max(...repeated.map(([, count]) => count));
        c.add("info", "tables.repeatOpponents", `${repeated.length} pairs of players meet more than once (up to ${worst} times).`);
    }
}
function checkTableScores(c, table, hands, assigned, where) {
    const seats = (0, handScoring_1.seatIdsOf)(table);
    const isCompleted = Boolean(table.isCompleted);
    const useTotalsOnly = Boolean(table.useTotalsOnly);
    if (seats != null) {
        const seatNumbers = seats.map(Number).sort((a, b) => a - b);
        if (assigned.length === 4 && seatNumbers.join(",") !== [...assigned].sort((a, b) => a - b).join(",")) {
            c.add("error", "tables.seatsDiffer", `The seats (${seats.join(", ")}) are not the assigned players (${assigned.join(", ")}).`, where);
        }
    }
    else if (isCompleted) {
        c.add("error", "tables.completedWithoutSeats", "The table is completed but it has no four different seats.", where);
    }
    // Hands: documents.
    const handIds = hands.map(({ id }) => Number(id));
    if (hands.length !== tableManagerService_1.HANDS_PER_TABLE) {
        c.add("warning", "hands.count", `The table has ${hands.length} hand documents. It needs ${tableManagerService_1.HANDS_PER_TABLE}.`, where);
    }
    hands.forEach(({ id, data }) => {
        if (Number(data.handId) !== Number(id))
            c.add("error", "hands.id", `The hand document ${id} has handId ${text(data.handId)}.`, `${where} hand ${id}`);
    });
    if (new Set(handIds).size !== handIds.length)
        c.add("error", "hands.duplicate", "Two hand documents have the same number.", where);
    const ordered = hands.map(({ data }) => data).sort((a, b) => Number(a.handId) - Number(b.handId));
    const handData = ordered.filter((hand) => !isBlankHand(hand));
    const handPlaces = (hand) => `${where} hand ${text(hand.handId)}`;
    ordered.forEach((hand) => {
        if (isBlankHand(hand)) {
            if (hand.isDone === true)
                c.add("warning", "hands.doneEmpty", "An empty hand is marked as done.", handPlaces(hand));
            return;
        }
        if ((0, handScoring_1.isResultSelectionInvalid)(hand)) {
            c.add("error", "hands.invalid", `The hand result is not valid (winner "${text(hand.playerWinnerId)}", discarder "${text(hand.playerLooserId)}", score "${text(hand.handScore)}").`, handPlaces(hand));
            return;
        }
        const winner = text(hand.playerWinnerId);
        const loser = text(hand.playerLooserId) === "-" ? "" : text(hand.playerLooserId);
        if (seats != null) {
            if (winner !== "" && !seats.includes(winner))
                c.add("error", "hands.winnerSeat", `The winner ${winner} does not sit at the table.`, handPlaces(hand));
            if (loser !== "" && !seats.includes(loser))
                c.add("error", "hands.loserSeat", `The discarder ${loser} does not sit at the table.`, handPlaces(hand));
        }
        if (winner !== "" && winner === loser)
            c.add("error", "hands.sameWinnerLoser", "The winner and the discarder are the same player.", handPlaces(hand));
        const score = Number(text(hand.handScore));
        if (winner !== "" && score < handScoring_1.MIN_HAND_SCORE)
            c.add("error", "hands.score", `The score ${score} is below ${handScoring_1.MIN_HAND_SCORE}.`, handPlaces(hand));
        if (Boolean(hand.isChickenHand) && score > handScoring_1.MAX_CHICKEN_HAND_SCORE) {
            c.add("error", "hands.chickenScore", `A chicken hand cannot score ${score}.`, handPlaces(hand));
        }
        if (winner !== "" && score > 200)
            c.add("warning", "hands.highScore", `The score ${score} is very high. Check for a typing error.`, handPlaces(hand));
        ["playerEastPenalty", "playerSouthPenalty", "playerWestPenalty", "playerNorthPenalty"].forEach((field) => {
            const parts = penaltyParts(hand[field]);
            if (parts == null)
                c.add("warning", "hands.penaltyText", `The penalty "${text(hand[field])}" is not a list of numbers.`, handPlaces(hand));
            else if (parts.some((value) => value > 0))
                c.add("info", "hands.positivePenalty", "A penalty field has a positive value.", handPlaces(hand));
        });
    });
    // Hands that count must be done, in order.
    const lastDone = Math.max(0, ...ordered.filter((hand) => hand.isDone === true).map((hand) => Number(hand.handId)));
    if (!isCompleted) {
        const gap = handData.find((hand) => hand.isDone !== true && Number(hand.handId) < lastDone);
        if (gap != null)
            c.add("warning", "hands.gap", "A hand with data is not done, but a later hand is done.", handPlaces(gap));
    }
    // Totals.
    const stored = [table.playerEastScore, table.playerSouthScore, table.playerWestScore, table.playerNorthScore].map(text);
    const storedValid = stored.every((value) => /^-?\d+$/.test(value));
    const manual = [table.manualPlayerEastScore, table.manualPlayerSouthScore, table.manualPlayerWestScore, table.manualPlayerNorthScore].map(text);
    const manualValid = manual.every((value) => /^-?\d+$/.test(value));
    if (isCompleted && !storedValid && !(useTotalsOnly && manualValid)) {
        c.add("error", "tables.completedWithoutTotals", "The table is completed but it has no four valid scores.", where);
    }
    if (!useTotalsOnly && seats != null) {
        const counted = (0, handScoring_1.handScoreTotals)({ ...table, isCompleted }, ordered);
        if (counted != null && storedValid && counted.some((value, seat) => value !== Number(stored[seat]))) {
            c.add("error", "tables.totalsMismatch", `The saved scores (${stored.join(", ")}) differ from the hands (${counted.join(", ")}).`, where);
        }
        if (counted == null && handData.length > 0 && isCompleted) {
            c.add("warning", "tables.handsNotCounted", "The table has hands, but none of them counts.", where);
        }
        if (isCompleted && counted != null) {
            const done = ordered.filter((hand) => (0, handScoring_1.handSeatScores)(hand, seats) != null).length;
            if (done < tableManagerService_1.HANDS_PER_TABLE)
                c.add("info", "tables.fewHands", `The completed table has ${done} counted hands. A full game has ${tableManagerService_1.HANDS_PER_TABLE}.`, where);
        }
    }
    if (useTotalsOnly && handData.length > 0) {
        c.add("info", "tables.handsIgnored", `The table uses manual totals, so its ${handData.length} hands with data are ignored.`, where);
    }
    if (storedValid) {
        const values = stored.map(Number);
        const sum = values.reduce((a, b) => a + b, 0);
        const penalties = ordered.reduce((total, hand) => total + ["playerEastPenalty", "playerSouthPenalty", "playerWestPenalty", "playerNorthPenalty"]
            .reduce((inner, field) => inner + (penaltyParts(hand[field]) ?? []).reduce((a, b) => a + b, 0), 0), 0);
        if (sum !== 0 && (useTotalsOnly || sum !== penalties)) {
            c.add("warning", "tables.sumNotZero", `The four scores add up to ${sum}, not to zero.`, where);
        }
        if (values.some((value) => Math.abs(value) > 2000)) {
            c.add("warning", "tables.hugeScore", "A score is larger than 2000. Check for a typing error.", where);
        }
        // Points come only from the scores.
        const expected = (0, handScoring_1.tablePointsOf)({ ...table, useTotalsOnly: false, playerEastScore: stored[0], playerSouthScore: stored[1], playerWestScore: stored[2], playerNorthScore: stored[3] });
        const storedPoints = [table.playerEastPoints, table.playerSouthPoints, table.playerWestPoints, table.playerNorthPoints].map(normalizedPoints);
        const expectedPoints = [expected.playerEastPoints, expected.playerSouthPoints, expected.playerWestPoints, expected.playerNorthPoints].map(normalizedPoints);
        if (storedPoints.some((value, index) => Number(value) !== Number(expectedPoints[index]))) {
            c.add("warning", "tables.points", `The table points (${storedPoints.join(", ")}) do not match the scores (${expectedPoints.join(", ")}).`, where);
        }
    }
    // Derived summary fields.
    const summary = (0, tableManagerService_1.calculateTableSummary)(table, ordered);
    if (text(table.completionStatus) !== summary.completionStatus) {
        c.add("warning", "tables.completionStatus", `The saved status is "${text(table.completionStatus)}" but the data says "${summary.completionStatus}".`, where);
    }
    if (Boolean(table.hasProgress) !== summary.hasProgress) {
        c.add("warning", "tables.hasProgress", "The saved progress flag does not match the data.", where);
    }
    if (Number(table.chickenHandCount ?? 0) !== summary.chickenHandCount) {
        c.add("warning", "tables.chickenCount", `The saved chicken hand count is ${text(table.chickenHandCount)} but the hands have ${summary.chickenHandCount}.`, where);
    }
    const savedBest = (table.bestHandScores ?? []).map(Number).join(",");
    if (savedBest !== summary.bestHandScores.join(",") && !useTotalsOnly) {
        c.add("warning", "tables.bestHands", `The saved best hands (${savedBest || "none"}) do not match the hands (${summary.bestHandScores.join(",") || "none"}).`, where);
    }
}
function isBlankHand(hand) {
    return text(hand.playerWinnerId) === "" && text(hand.playerLooserId).replace("-", "") === "" && text(hand.handScore) === ""
        && !hand.isChickenHand && ["playerEastPenalty", "playerSouthPenalty", "playerWestPenalty", "playerNorthPenalty"].every((f) => text(hand[f]) === "");
}
// -- Tournament counters ---------------------------------------------------------------------
function checkTournamentCounters(c, input) {
    const t = input.tournament;
    const completedTables = input.tables.filter(({ data }) => Boolean(data.isCompleted)).length;
    const allCompleted = input.tables.length > 0 && completedTables === input.tables.length;
    if (Boolean(t.isCompleted) !== allCompleted) {
        c.add("error", "counters.isCompleted", `The tournament is marked ${t.isCompleted ? "completed" : "not completed"} but ${completedTables} of ${input.tables.length} tables are completed.`);
    }
    const withProgress = input.tables.filter(({ data, hands }) => (0, tableManagerService_1.calculateTableSummary)(data, hands.map((hand) => hand.data)).hasProgress).length;
    if (Number(t.tablesWithProgress ?? 0) !== withProgress) {
        c.add("warning", "counters.tablesWithProgress", `The counter says ${text(t.tablesWithProgress)} started tables but ${withProgress} have data.`);
    }
    const versions = ((t.dataVersions ?? {}).tableVersions ?? {});
    let stale = 0;
    input.tables.forEach(({ id, data }) => {
        if (Number(versions[id] ?? 0) !== Number(data.version ?? 0))
            stale += 1;
    });
    if (stale > 0) {
        c.add("warning", "counters.tableVersions", `${stale} tables have a version that differs from the tournament table versions. Polling clients may miss changes.`);
    }
}
//# sourceMappingURL=tournamentIntegrity.js.map