/**
 * Structure check for the draw of a new tournament. The admin app computes the draw.
 * The server checks only the rules that every valid draw must follow.
 * In a team tournament, two players of one team cannot sit at the same table.
 * Two players cannot sit together at a table in more than one round, unless the tournament has more rounds than the
 * draw finds without repeats: then the app draws the rounds in blocks (docs/draw.md, "More rounds than fit without
 * repeats") and two players meet at most [meetingLimit] times.
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
  const limit = meetingLimit(players.length, numRounds, isTeams);
  const pairs = new Map<string, number>();
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
        const times = (pairs.get(pair) ?? 0) + 1;
        if (times > limit) {
          return limit === 1
            ? `Players ${sorted[i]} and ${sorted[j]} play together in more than one round.`
            : `Players ${sorted[i]} and ${sorted[j]} play together in more than ${limit} rounds.`;
        }
        pairs.set(pair, times);
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

/*
 * The rounds without repeats, as the app counts them (domain RepeatFreeRounds.kt, docs/draw.md). Keep both in step:
 * tournamentDraw.test.ts checks the same values as RepeatFreeRoundsTest.
 */
const KNOWN_INDIVIDUAL: Record<number, number> = { 4: 1, 8: 1, 12: 1, 16: 5, 20: 5 };
const PERFECT_KEPT = new Set([16, 28, 40, 64, 256]);
const SURE_INDIVIDUAL: Record<number, number> = { 4: 1, 8: 1, 12: 1, 20: 5, 24: 5, 32: 7, 36: 8, 44: 10, 48: 11 };
const SURE_TEAMS: Record<number, number> = { 4: 0, 8: 0, 12: 0, 20: 4, 24: 4, 32: 6, 36: 7, 44: 9, 48: 10 };

/** The ceiling of rounds without repeats: (N - 1) / 3, or (N - 4) / 3 with teams. */
export function repeatFreeCeiling(players: number, isTeams: boolean): number {
  if (players < 4 || players % 4 !== 0) return 0;
  return Math.max(0, Math.floor((players - 1) / 3) - (isTeams ? 1 : 0));
}

/** The rounds without repeats that the app counts on (RepeatFreeRounds.max). */
export function repeatFreeMax(players: number, isTeams: boolean): number {
  if (players < 4 || players % 4 !== 0) return 0;
  const individual = KNOWN_INDIVIDUAL[players] ?? repeatFreeCeiling(players, false);
  return Math.max(0, individual - (isTeams ? 1 : 0));
}

/** The rounds without repeats that the draw finds for sure (RepeatFreeRounds.sure): the size of a block. */
export function repeatFreeSure(players: number, isTeams: boolean): number {
  if (players < 4 || players % 4 !== 0) return 0;
  const max = repeatFreeMax(players, isTeams);
  if (PERFECT_KEPT.has(players)) return max;
  const measured = (isTeams ? SURE_TEAMS : SURE_INDIVIDUAL)[players];
  const sure = measured ?? Math.floor((repeatFreeCeiling(players, isTeams) * 7) / 10);
  return Math.min(Math.max(sure, Math.min(1, max)), max);
}

/**
 * The most times two players may sit together in a valid draw of [rounds] rounds. Once while the rounds fit in one
 * block of [repeatFreeSure] rounds. With more, the app draws in blocks: after k blocks nobody met anyone more than k
 * times, and a block that does not finish in time allows one more meeting from it on (DrawRoundsInBlocksUseCase), so at
 * most two for each block begun.
 */
export function meetingLimit(players: number, rounds: number, isTeams: boolean): number {
  const perBlock = repeatFreeSure(players, isTeams);
  if (perBlock < 1 || rounds <= perBlock) return 1;
  return 2 * Math.ceil(rounds / perBlock);
}
