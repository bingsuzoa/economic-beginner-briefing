import { describe, it, expect } from "vitest";
import {
  getYesterdayDateInKST,
  getTargetDateStartKST,
  getTargetDateEndKST,
  validateISODate,
  getHourlyTimeRange,
  formatHourlyBriefingTitle,
} from "../../src/utils/date.js";

describe("date utilities", () => {
  describe("getYesterdayDateInKST", () => {
    it("should return a valid ISO date string", () => {
      const yesterday = getYesterdayDateInKST();
      expect(yesterday).toMatch(/^\d{4}-\d{2}-\d{2}$/);
    });

    it("should return a date before today", () => {
      const yesterday = getYesterdayDateInKST();
      const yesterdayDate = new Date(yesterday + "T00:00:00+09:00");
      const now = new Date();
      expect(yesterdayDate.getTime()).toBeLessThan(now.getTime());
    });
  });

  describe("getTargetDateStartKST", () => {
    it("should return start of day in KST", () => {
      const start = getTargetDateStartKST("2026-07-16");
      expect(start).toBe("2026-07-16T00:00:00+09:00");
    });

    it("should throw for invalid date", () => {
      expect(() => getTargetDateStartKST("invalid")).toThrow();
    });
  });

  describe("getTargetDateEndKST", () => {
    it("should return end of day in KST", () => {
      const end = getTargetDateEndKST("2026-07-16");
      expect(end).toBe("2026-07-16T23:59:59+09:00");
    });
  });

  describe("validateISODate", () => {
    it("should accept valid dates", () => {
      expect(() => validateISODate("2026-07-16")).not.toThrow();
      expect(() => validateISODate("2026-01-01")).not.toThrow();
      expect(() => validateISODate("2026-12-31")).not.toThrow();
    });

    it("should reject invalid format", () => {
      expect(() => validateISODate("2026/07/16")).toThrow();
      expect(() => validateISODate("07-16-2026")).toThrow();
      expect(() => validateISODate("not-a-date")).toThrow();
    });

    it("should reject invalid calendar date", () => {
      expect(() => validateISODate("2026-02-30")).toThrow();
      expect(() => validateISODate("2026-13-01")).toThrow();
    });
  });

  describe("getHourlyTimeRange", () => {
    it("should return previous hour window when executed at 13:00 KST", () => {
      // 13:00 KST = 04:00 UTC
      const referenceTime = new Date("2026-07-18T04:00:00Z");
      const range = getHourlyTimeRange(referenceTime);

      expect(range.startTime).toBe("2026-07-18T12:00:00+09:00");
      expect(range.endTime).toBe("2026-07-18T12:59:59+09:00");
      expect(range.targetDate).toBe("2026-07-18");
      expect(range.hour).toBe(12);
    });

    it("should return previous hour window when executed at 14:30 KST", () => {
      // 14:30 KST = 05:30 UTC
      const referenceTime = new Date("2026-07-18T05:30:00Z");
      const range = getHourlyTimeRange(referenceTime);

      // Floors to 14:00, then previous hour = 13:00~13:59
      expect(range.startTime).toBe("2026-07-18T13:00:00+09:00");
      expect(range.endTime).toBe("2026-07-18T13:59:59+09:00");
      expect(range.targetDate).toBe("2026-07-18");
      expect(range.hour).toBe(13);
    });

    it("should handle midnight boundary (00:00 KST execution)", () => {
      // 00:00 KST = 15:00 UTC (previous day)
      const referenceTime = new Date("2026-07-17T15:00:00Z");
      const range = getHourlyTimeRange(referenceTime);

      // Previous hour is 23:00~23:59 of July 17
      expect(range.startTime).toBe("2026-07-17T23:00:00+09:00");
      expect(range.endTime).toBe("2026-07-17T23:59:59+09:00");
      expect(range.targetDate).toBe("2026-07-17");
      expect(range.hour).toBe(23);
    });

    it("should handle 01:00 KST execution (window crosses nothing special)", () => {
      // 01:00 KST = 16:00 UTC (previous day)
      const referenceTime = new Date("2026-07-17T16:00:00Z");
      const range = getHourlyTimeRange(referenceTime);

      expect(range.startTime).toBe("2026-07-18T00:00:00+09:00");
      expect(range.endTime).toBe("2026-07-18T00:59:59+09:00");
      expect(range.targetDate).toBe("2026-07-18");
      expect(range.hour).toBe(0);
    });

    it("should return a valid time range without reference time", () => {
      const range = getHourlyTimeRange();

      expect(range.startTime).toMatch(/^\d{4}-\d{2}-\d{2}T\d{2}:00:00\+09:00$/);
      expect(range.endTime).toMatch(/^\d{4}-\d{2}-\d{2}T\d{2}:59:59\+09:00$/);
      expect(range.targetDate).toMatch(/^\d{4}-\d{2}-\d{2}$/);
      expect(range.hour).toBeGreaterThanOrEqual(0);
      expect(range.hour).toBeLessThanOrEqual(23);
    });

    it("should produce exactly 1 hour window (3599 seconds)", () => {
      const referenceTime = new Date("2026-07-18T04:00:00Z");
      const range = getHourlyTimeRange(referenceTime);

      const start = new Date(range.startTime).getTime();
      const end = new Date(range.endTime).getTime();
      expect(end - start).toBe(3599 * 1000); // 59 minutes 59 seconds
    });
  });

  describe("formatHourlyBriefingTitle", () => {
    it("should format title with date and hour", () => {
      expect(formatHourlyBriefingTitle("2026-07-18", 12)).toBe(
        "2026-07-18 12시 경제 브리핑",
      );
    });

    it("should pad single digit hours", () => {
      expect(formatHourlyBriefingTitle("2026-07-18", 9)).toBe(
        "2026-07-18 09시 경제 브리핑",
      );
    });

    it("should handle midnight", () => {
      expect(formatHourlyBriefingTitle("2026-07-18", 0)).toBe(
        "2026-07-18 00시 경제 브리핑",
      );
    });
  });
});
