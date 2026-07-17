import { ISODateSchema } from "../domain/article.js";

const KST_OFFSET_MS = 9 * 60 * 60 * 1000;

/**
 * Returns the current time in KST as a Date object.
 */
function nowInKST(): Date {
  const now = new Date();
  return new Date(now.getTime() + KST_OFFSET_MS + now.getTimezoneOffset() * 60 * 1000);
}

/**
 * Returns yesterday's date string (YYYY-MM-DD) in Asia/Seoul timezone.
 */
export function getYesterdayDateInKST(): string {
  const kst = nowInKST();
  kst.setDate(kst.getDate() - 1);
  return formatDateToISO(kst);
}

/**
 * Returns the start of a target date in KST as an ISO datetime string.
 * e.g., "2026-07-16" -> "2026-07-16T00:00:00+09:00"
 */
export function getTargetDateStartKST(targetDate: string): string {
  validateISODate(targetDate);
  return `${targetDate}T00:00:00+09:00`;
}

/**
 * Returns the end of a target date in KST as an ISO datetime string.
 * e.g., "2026-07-16" -> "2026-07-16T23:59:59+09:00"
 */
export function getTargetDateEndKST(targetDate: string): string {
  validateISODate(targetDate);
  return `${targetDate}T23:59:59+09:00`;
}

/**
 * Validates that a string is a valid ISO date (YYYY-MM-DD) and represents a real calendar date.
 */
export function validateISODate(value: string): void {
  const result = ISODateSchema.safeParse(value);
  if (!result.success) {
    throw new Error(`Invalid ISO date: ${value}`);
  }

  const [year, month, day] = value.split("-").map(Number) as [number, number, number];
  const date = new Date(year, month - 1, day);
  if (
    date.getFullYear() !== year ||
    date.getMonth() !== month - 1 ||
    date.getDate() !== day
  ) {
    throw new Error(`Invalid calendar date: ${value}`);
  }
}

/**
 * Formats a Date to ISO date string (YYYY-MM-DD).
 */
function formatDateToISO(date: Date): string {
  const y = date.getFullYear();
  const m = String(date.getMonth() + 1).padStart(2, "0");
  const d = String(date.getDate()).padStart(2, "0");
  return `${y}-${m}-${d}`;
}

/**
 * Returns the current ISO datetime string in KST with offset.
 */
export function nowISOStringKST(): string {
  const kst = nowInKST();
  const y = kst.getFullYear();
  const mo = String(kst.getMonth() + 1).padStart(2, "0");
  const d = String(kst.getDate()).padStart(2, "0");
  const h = String(kst.getHours()).padStart(2, "0");
  const mi = String(kst.getMinutes()).padStart(2, "0");
  const s = String(kst.getSeconds()).padStart(2, "0");
  return `${y}-${mo}-${d}T${h}:${mi}:${s}+09:00`;
}
