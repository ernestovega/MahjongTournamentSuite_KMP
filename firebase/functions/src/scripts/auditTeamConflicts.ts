import { db, firebaseProjectId } from "../firebase";

/**
 * Lists the tables where two players of the same team sit together. It only reads.
 * A tournament without teams mode is checked too, because its slots still carry a team number.
 * Usage: npm run audit:team-conflicts
 */
async function main(): Promise<void> {
  const tournaments = await db.collection("tournaments").get();
  console.log(`Checking ${tournaments.size} tournaments in ${firebaseProjectId}.`);
  for (const tournament of tournaments.docs) {
    // eslint-disable-next-line no-await-in-loop
    const [players, tables] = await Promise.all([
      tournament.ref.collection("players").get(), tournament.ref.collection("tables").get(),
    ]);
    const teamOf = new Map(players.docs.map((doc) => [Number(doc.get("id") ?? doc.id), Number(doc.get("team") ?? 0)]));
    const conflicts: string[] = [];
    tables.docs.forEach((doc) => {
      const ids = ((doc.get("playerIds") ?? []) as unknown[]).map(Number);
      const seen = new Map<number, number[]>();
      ids.forEach((id) => {
        const team = teamOf.get(id) ?? 0;
        if (team > 0) seen.set(team, [...(seen.get(team) ?? []), id]);
      });
      seen.forEach((members, team) => {
        if (members.length > 1) conflicts.push(`${doc.id}: team ${team} players ${members.join(",")}`);
      });
    });
    console.log(`${conflicts.length > 0 ? "CONFLICT" : "ok      "} ${tournament.id} isTeams=${tournament.get("isTeams")} tables=${tables.size} conflicts=${conflicts.length}`);
    conflicts.slice(0, 3).forEach((line) => console.log(`    ${line}`));
  }
}

main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
