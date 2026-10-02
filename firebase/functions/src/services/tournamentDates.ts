export function isValidIsoDate(value: string): boolean {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value)) return false;
  const [year, month, day] = value.split("-").map(Number);
  const date = new Date(Date.UTC(year, month - 1, day));
  return date.getUTCFullYear() === year && date.getUTCMonth() === month - 1 && date.getUTCDate() === day;
}

export function isValidIsoDateRange(startDate: string, endDate: string): boolean {
  return isValidIsoDate(startDate) && isValidIsoDate(endDate) && startDate <= endDate;
}

export function inclusiveDayCount(startDate: string, endDate: string): number {
  if (!isValidIsoDate(startDate) || !isValidIsoDate(endDate)) return 0;
  const start = Date.UTC(
    Number(startDate.slice(0, 4)),
    Number(startDate.slice(5, 7)) - 1,
    Number(startDate.slice(8, 10)),
  );
  const end = Date.UTC(
    Number(endDate.slice(0, 4)),
    Number(endDate.slice(5, 7)) - 1,
    Number(endDate.slice(8, 10)),
  );
  return Math.max(0, Math.round((end - start) / 86_400_000) + 1);
}
