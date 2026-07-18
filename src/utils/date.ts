import { ISODateSchema } from "../domain/article.js";
import { KST_OFFSET_HOURS } from "../config/constants.js";

const KST_OFFSET_MS = KST_OFFSET_HOURS * 60 * 60 * 1000;

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
  return formatDateTimeKST(kst);
}

/**
 * Represents an hourly time window for news collection.
 */
export interface HourlyTimeRange {
  /** Start of the window (inclusive), e.g. "2026-07-18T12:00:00+09:00" */
  startTime: string;
  /** End of the window (inclusive), e.g. "2026-07-18T12:59:59+09:00" */
  endTime: string;
  /** The date portion (YYYY-MM-DD) of the window start in KST */
  targetDate: string;
  /** The hour (0-23) of the window start in KST */
  hour: number;
}

/**
 * Calculates the hourly time range for the previous hour based on a reference time.
 *
 * If executed at 13:00 KST, returns range 12:00:00 ~ 12:59:59 KST.
 * The reference time is always floored to the current hour, then the previous hour window is used.
 *
 * @param referenceTime - The execution time (defaults to current time). Used for testability.
 */
export function getHourlyTimeRange(referenceTime?: Date): HourlyTimeRange {
  const kst = referenceTime ? toKST(referenceTime) : nowInKST();

  // Floor to the current hour
  kst.setMinutes(0, 0, 0);

  // The window is the PREVIOUS hour
  const windowEnd = new Date(kst.getTime() - 1000); // current hour :00:00 - 1 second = previous :59:59
  const windowStart = new Date(kst.getTime() - 60 * 60 * 1000); // one hour before

  const targetDate = formatDateToISO(windowStart);
  const hour = windowStart.getHours();

  return {
    startTime: formatDateTimeKST(windowStart),
    endTime: formatDateTimeKST(windowEnd),
    targetDate,
    hour,
  };
}

/**
 * Formats an hourly briefing title for Notion.
 * e.g., "2026-07-18 12시 경제 브리핑" (12:00~13:00 window)
 */
export function formatHourlyBriefingTitle(targetDate: string, hour: number): string {
  const paddedHour = String(hour).padStart(2, "0");
  return `${targetDate} ${paddedHour}시 경제 브리핑`;
}

/**
 * Converts any Date to KST Date object.
 */
function toKST(date: Date): Date {
  return new Date(date.getTime() + KST_OFFSET_MS + date.getTimezoneOffset() * 60 * 1000);
}

function formatDateTimeKST(date: Date): string {
  const y = date.getFullYear();
  const mo = String(date.getMonth() + 1).padStart(2, "0");
  const d = String(date.getDate()).padStart(2, "0");
  const h = String(date.getHours()).padStart(2, "0");
  const mi = String(date.getMinutes()).padStart(2, "0");
  const s = String(date.getSeconds()).padStart(2, "0");
  return `${y}-${mo}-${d}T${h}:${mi}:${s}+09:00`;
}
