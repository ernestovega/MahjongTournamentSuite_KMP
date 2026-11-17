// A valid draw for a scenario tournament, as the server checks it (services/tournamentDraw.ts): four players per
// table, every player once per round, no two players of one team at a table, and no pair that sits together twice.

/** players: [{ id, team }] with team 0 in a tournament without teams. Returns [{ roundId, tableId, playerIds }]. */
export function generateDraw(players, rounds, { attempts = 200, random = Math.random } = {}) {
  if (players.length % 4 !== 0) throw new Error("The players must be a multiple of 4");
  const met = new Map(players.map((player) => [player.id, new Set()]));
  const teamOf = new Map(players.map((player) => [player.id, player.team]));
  const fits = (table, id) => table.every((other) => !met.get(id).has(other) && (teamOf.get(id) === 0 || teamOf.get(id) !== teamOf.get(other)));
  const draw = [];
  for (let roundId = 1; roundId <= rounds; roundId += 1) {
    let tables = null;
    for (let attempt = 0; attempt < attempts && tables == null; attempt += 1) tables = tryRound(players, fits, random);
    if (tables == null) throw new Error(`No valid draw for round ${roundId}`);
    tables.forEach((table, index) => {
      for (const id of table) for (const other of table) if (other !== id) met.get(id).add(other);
      draw.push({ roundId, tableId: index + 1, playerIds: [...table].sort((a, b) => a - b) });
    });
  }
  return draw;
}

/** One round: the players with fewest options first, each to a random table where it fits. Null when it gets stuck. */
function tryRound(players, fits, random) {
  const ids = players.map((player) => player.id).sort(() => random() - 0.5);
  const tables = Array.from({ length: players.length / 4 }, () => []);
  for (const id of ids) {
    const open = tables.filter((table) => table.length < 4 && fits(table, id));
    if (open.length === 0) return null;
    // Fill the fullest tables first, so the last players are not left with no table at all.
    const most = Math.max(...open.map((table) => table.length));
    const best = open.filter((table) => table.length === most);
    best[Math.floor(random() * best.length)].push(id);
  }
  return tables;
}

/** Teams of four: players 1-4 are team 1, 5-8 team 2, and so on. Without teams, every player has team 0. */
export function scenarioPlayers(count, isTeams) {
  return Array.from({ length: count }, (_, index) => ({ id: index + 1, team: isTeams ? Math.floor(index / 4) + 1 : 0 }));
}
