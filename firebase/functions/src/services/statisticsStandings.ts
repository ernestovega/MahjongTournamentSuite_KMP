type Data = Record<string, unknown>;

const round6 = (value: number): number => Math.round(value * 1e6) / 1e6;

export type PersonStanding = {
  tournamentId: string;
  /** Place of the player in the tournament and the number of players with a result. */
  rank: number;
  of: number;
  /** The points and the score of the player in the tournament. */
  points: number;
  score: number;
  /** Place of the team of the player, and the number of teams. Null when the team is not known. */
  teamRank: number | null;
  teamOf: number | null;
  /** Place of the player by chicken hands. Null when the player won no chicken hand. */
  chickenRank: number | null;
  /** Place of the best hand of the player among the best hands of all players. Null when the player won no hand. */
  bestHandRank: number | null;
};

type SlotRow = { slot: number; points: number; score: number; chicken: number; best: number };

const sumOf = (value: unknown): number =>
  Object.values((value ?? {}) as Record<string, number>).reduce((sum, count) => sum + (Number(count) || 0), 0);
const maxOf = (value: unknown): number =>
  Object.values((value ?? {}) as Record<string, number>).reduce((best, hand) => Math.max(best, Number(hand) || 0), 0);

/** The places of one slot in a tournament, from the stored document of the tournament. */
export function standingOf(tournamentId: string, slotId: number, data: Data): PersonStanding | null {
  const totals = (data.totals ?? {}) as Record<string, { points?: number; score?: number }>;
  const chicken = (data.chicken ?? {}) as Record<string, unknown>;
  const best = (data.best ?? {}) as Record<string, unknown>;
  const teams = (data.teams ?? {}) as Record<string, number>;
  const rows: SlotRow[] = Object.entries(totals).map(([slot, value]) => ({
    slot: Number(slot),
    points: round6(Number(value.points ?? 0)),
    score: Number(value.score ?? 0),
    chicken: sumOf(chicken[slot]),
    best: maxOf(best[slot]),
  }));
  const mine = rows.find((row) => row.slot === slotId);
  if (mine == null) return null;
  const beats = (a: { points: number; score: number }, b: { points: number; score: number }) =>
    a.points > b.points || (a.points === b.points && a.score > b.score);

  const teamIds = [...new Set(rows.map((row) => teams[String(row.slot)]).filter((team) => team != null))];
  const teamRows = teamIds.map((team) => {
    const members = rows.filter((row) => teams[String(row.slot)] === team);
    return { team, points: round6(members.reduce((sum, row) => sum + row.points, 0)), score: members.reduce((sum, row) => sum + row.score, 0) };
  });
  const myTeam = teams[String(slotId)];
  const myTeamRow = teamRows.find((row) => row.team === myTeam);

  return {
    tournamentId,
    rank: rows.filter((row) => beats(row, mine)).length + 1,
    of: rows.length,
    points: mine.points,
    score: mine.score,
    teamRank: myTeamRow == null ? null : teamRows.filter((row) => beats(row, myTeamRow)).length + 1,
    teamOf: myTeamRow == null ? null : teamRows.length,
    chickenRank: mine.chicken === 0
      ? null
      : rows.filter((row) => row.chicken > mine.chicken || (row.chicken === mine.chicken && beats(row, mine))).length + 1,
    bestHandRank: mine.best === 0
      ? null
      : Object.values(best).flatMap((tables) => Object.values((tables ?? {}) as Record<string, number>))
        .filter((hand) => Number(hand) > mine.best).length + 1,
  };
}

