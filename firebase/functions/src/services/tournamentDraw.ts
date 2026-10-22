/**
 * Structure check for the draw of a new tournament. The admin app computes the draw.
 * The server checks only the rules that every valid draw must follow.
 * In a team tournament, two players of one team cannot sit at the same table.
 * Two players cannot sit together at a table in more than one round.
 */
export type DrawPlayer = { id: number; team: number };
export type DrawTable = { roundId: number; tableId: number; playerIds: number[] };

/** Returns a message for the first problem found, or null when the draw is valid. */
export function drawProblem(
  players: DrawPlayer[],
  tables: DrawTable[],
  numRounds: number,
  numTablesPerRound: number,
  isTeams = false,
): string | null {
  const playerIds = new Set(players.map((player) => player.id));
  const teamOf = new Map(players.map((player) => [player.id, player.team]));
  if (playerIds.size !== players.length) return "Two players have the same id.";
  const tableKeys = new Set<string>();
  const pairs = new Set<string>();
  const seenInRound = new Map<number, Set<number>>();
  for (const table of tables) {
    const where = `round ${table.roundId} table ${table.tableId}`;
    if (!Number.isInteger(table.roundId) || table.roundId < 1 || table.roundId > numRounds) {
      return `The round of ${where} is out of range.`;
    }
    if (!Number.isInteger(table.tableId) || table.tableId < 1 || table.tableId > numTablesPerRound) {
      return `The table of ${where} is out of range.`;
    }
    const key = `${table.roundId}_${table.tableId}`;
    if (tableKeys.has(key)) return `The ${where} appears twice.`;
    tableKeys.add(key);
    if (table.playerIds.length !== 4 || new Set(table.playerIds).size !== 4) {
      return `The ${where} needs four different players.`;
    }
    if (isTeams) {
      const teams = table.playerIds.map((id) => teamOf.get(id) ?? 0).filter((team) => team > 0);
      if (new Set(teams).size !== teams.length) return `The ${where} has two players of the same team.`;
    }
    const sorted = [...table.playerIds].sort((a, b) => a - b);
    for (let i = 0; i < sorted.length; i++) {
      for (let j = i + 1; j < sorted.length; j++) {
        const pair = `${sorted[i]}-${sorted[j]}`;
        if (pairs.has(pair)) return `Players ${sorted[i]} and ${sorted[j]} play together in more than one round.`;
        pairs.add(pair);
      }
    }
    const seen = seenInRound.get(table.roundId) ?? new Set<number>();
    for (const id of table.playerIds) {
      if (!playerIds.has(id)) return `The ${where} has an unknown player ${id}.`;
      if (seen.has(id)) return `Player ${id} plays two tables in round ${table.roundId}.`;
      seen.add(id);
    }
    seenInRound.set(table.roundId, seen);
  }
  return null;
}
