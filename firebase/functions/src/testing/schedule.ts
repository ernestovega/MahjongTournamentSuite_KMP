/** A date and a start time for every round, as a new tournament must have. The rounds start one hour apart. */
export function fullRoundSchedules(numRounds: number, date = "2026-10-01") {
  return Array.from({ length: numRounds }, (_, index) => ({
    roundId: index + 1,
    date,
    startTime: `${String(8 + (index % 16)).padStart(2, "0")}:00`,
  }));
}
