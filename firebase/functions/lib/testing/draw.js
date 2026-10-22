"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.validDrawTables = validDrawTables;
/** Builds a valid draw for tests: no repeated pair, and each player plays once per round. */
function validDrawTables(numPlayers, numRounds) {
    const perRound = numPlayers / 4;
    const met = new Set();
    const tables = [];
    let seed = 7;
    const next = () => (seed = (seed * 1103515245 + 12345) % 2147483648);
    for (let round = 1; round <= numRounds; round++) {
        for (let attempt = 0;; attempt++) {
            if (attempt > 10000)
                throw new Error("No valid draw found for the test.");
            const pool = Array.from({ length: numPlayers }, (_, index) => index + 1);
            for (let i = pool.length - 1; i > 0; i--) {
                const j = next() % (i + 1);
                [pool[i], pool[j]] = [pool[j], pool[i]];
            }
            const groups = [];
            let ok = true;
            for (const id of pool) {
                const group = groups.find((g) => g.length < 4 && g.every((other) => !met.has(`${Math.min(id, other)}-${Math.max(id, other)}`)));
                if (group)
                    group.push(id);
                else if (groups.length < perRound)
                    groups.push([id]);
                else {
                    ok = false;
                    break;
                }
            }
            if (!ok)
                continue;
            groups.forEach((group, index) => {
                tables.push({ roundId: round, tableId: index + 1, playerIds: group });
                group.forEach((a) => group.forEach((b) => { if (a < b)
                    met.add(`${a}-${b}`); }));
            });
            break;
        }
    }
    return tables;
}
//# sourceMappingURL=draw.js.map