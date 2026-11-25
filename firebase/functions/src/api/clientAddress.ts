import type { Request } from "express";

/**
 * Google ranges that Firebase Hosting uses to reach the API (seen in staging: 64.233.172.44, 66.102.6.33,
 * 192.178.11.1) and other front ends of Google, from https://www.gstatic.com/ipranges/goog.json without the ranges of
 * Google Cloud (cloud.json) and of the public DNS (8.8.x.x). No customer of Google Cloud gets an address in them.
 */
const GOOGLE_PROXY_RANGES: Array<[string, number]> = [
  ["64.233.160.0", 19],
  ["66.102.0.0", 20],
  ["66.249.64.0", 19],
  ["72.14.192.0", 18],
  ["74.125.0.0", 16],
  ["108.170.192.0", 18],
  ["108.177.0.0", 17],
  ["130.211.0.0", 22],
  ["142.250.0.0", 15],
  ["172.217.0.0", 16],
  ["172.253.0.0", 16],
  ["173.194.0.0", 16],
  ["192.178.0.0", 15],
  ["209.85.128.0", 17],
  ["216.58.192.0", 19],
  ["216.239.32.0", 19],
  ["35.191.0.0", 16],
];

function ipv4ToNumber(address: string): number | null {
  const parts = address.split(".");
  if (parts.length !== 4) return null;
  let value = 0;
  for (const part of parts) {
    if (!/^\d{1,3}$/.test(part)) return null;
    const byte = Number(part);
    if (byte > 255) return null;
    value = value * 256 + byte;
  }
  return value;
}

const RANGES = GOOGLE_PROXY_RANGES.map(([base, bits]) => {
  const size = 2 ** (32 - bits);
  const start = Math.floor((ipv4ToNumber(base) ?? 0) / size) * size;
  return { start, end: start + size };
});

export function isGoogleProxy(address: string): boolean {
  const value = ipv4ToNumber(address);
  return value != null && RANGES.some((range) => value >= range.start && value < range.end);
}

/**
 * The address of the client, for the rate limits. Never the first entry of X-Forwarded-For: the client writes that one.
 * - Direct calls (Android, desktop, scripts): Google adds the address of the caller at the end of the header.
 * - Calls through Firebase Hosting (the web apps): Hosting replaces the header with the address of the client, and
 *   Google then adds the address of Hosting, one of its own proxies. The client is the entry before it.
 * Checked on every deploy of staging (.github/workflows/staging.yml, "Check the client address").
 */
export function clientAddress(req: Request): string {
  const chain = String(req.header("x-forwarded-for") ?? "")
    .split(",")
    .map((entry) => entry.trim())
    .filter((entry) => entry.length > 0);
  if (chain.length === 0) return req.socket?.remoteAddress ?? "unknown";
  const last = chain[chain.length - 1];
  if (chain.length >= 2 && isGoogleProxy(last)) return chain[chain.length - 2];
  return last;
}
