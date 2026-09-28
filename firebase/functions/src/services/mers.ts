const ISO_DATE = /^(\d{4})-(\d{2})-(\d{2})$/;

export function inclusiveDayCount(startDate: string, endDate: string): number {
  const start = ISO_DATE.exec(startDate);
  const end = ISO_DATE.exec(endDate);
  if (!start || !end) return 0;
  const startUtc = Date.UTC(Number(start[1]), Number(start[2]) - 1, Number(start[3]));
  const endUtc = Date.UTC(Number(end[1]), Number(end[2]) - 1, Number(end[3]));
  return Math.max(0, Math.round((endUtc - startUtc) / 86_400_000) + 1);
}

export function calculateMers(params: {
  startDate: string;
  endDate: string;
  participantCount: number;
  representedCountries: Iterable<string>;
}): number {
  const days = inclusiveDayCount(params.startDate, params.endDate);
  const countries = new Set([...params.representedCountries].map((value) => value.trim().toUpperCase()).filter(Boolean)).size;
  const countryBonus = countries >= 10 ? 1 : countries >= 6 ? 0.5 : 0;
  const playerBonus = params.participantCount > 80 ? 1 : params.participantCount > 40 ? 0.5 : 0;
  return days + countryBonus + playerBonus;
}
