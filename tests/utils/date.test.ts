import { describe, it, expect } from "vitest";
import {
  getYesterdayDateInKST,
  getTargetDateStartKST,
  getTargetDateEndKST,
  validateISODate,
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
});
