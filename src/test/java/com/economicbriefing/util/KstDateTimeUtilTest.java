package com.economicbriefing.util;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class KstDateTimeUtilTest {

    @Test
    void nowShouldReturnKstTime() {
        OffsetDateTime now = KstDateTimeUtil.now();
        assertNotNull(now);
        assertEquals(ZoneOffset.ofHours(9), now.getOffset());
    }

    @Test
    void getTargetDateStartShouldReturnMidnight() {
        LocalDate date = LocalDate.of(2026, 7, 20);
        OffsetDateTime start = KstDateTimeUtil.getTargetDateStart(date);

        assertEquals(0, start.getHour());
        assertEquals(0, start.getMinute());
        assertEquals(0, start.getSecond());
        assertEquals(ZoneOffset.ofHours(9), start.getOffset());
    }

    @Test
    void getTargetDateEndShouldReturn235959() {
        LocalDate date = LocalDate.of(2026, 7, 20);
        OffsetDateTime end = KstDateTimeUtil.getTargetDateEnd(date);

        assertEquals(23, end.getHour());
        assertEquals(59, end.getMinute());
        assertEquals(59, end.getSecond());
        assertEquals(ZoneOffset.ofHours(9), end.getOffset());
    }

    @Test
    void getHourlyTimeRangeShouldReturnPreviousHour() {
        // Given: reference time is 13:30 KST
        OffsetDateTime reference = OffsetDateTime.of(
            2026, 7, 20, 13, 30, 0, 0, ZoneOffset.ofHours(9)
        );

        KstDateTimeUtil.TimeRange range = KstDateTimeUtil.getHourlyTimeRange(reference);

        // Window should be 12:00:00 ~ 12:59:59
        assertEquals(12, range.start().getHour());
        assertEquals(0, range.start().getMinute());
        assertEquals(0, range.start().getSecond());

        assertEquals(12, range.end().getHour());
        assertEquals(59, range.end().getMinute());
        assertEquals(59, range.end().getSecond());
    }

    @Test
    void getHourlyTimeRangeAtExactHourShouldReturnPreviousHour() {
        // Given: reference time is exactly 13:00 KST
        OffsetDateTime reference = OffsetDateTime.of(
            2026, 7, 20, 13, 0, 0, 0, ZoneOffset.ofHours(9)
        );

        KstDateTimeUtil.TimeRange range = KstDateTimeUtil.getHourlyTimeRange(reference);

        assertEquals(12, range.start().getHour());
        assertEquals(12, range.end().getHour());
    }

    @Test
    void getHourlyTimeRangeAtMidnightShouldReturnPreviousDay() {
        // Given: reference time is 00:30 KST on July 20
        OffsetDateTime reference = OffsetDateTime.of(
            2026, 7, 20, 0, 30, 0, 0, ZoneOffset.ofHours(9)
        );

        KstDateTimeUtil.TimeRange range = KstDateTimeUtil.getHourlyTimeRange(reference);

        assertEquals(23, range.start().getHour());
        assertEquals(19, range.start().getDayOfMonth());
    }

    @Test
    void timeRangeShouldProvideTargetDateAndHour() {
        OffsetDateTime reference = OffsetDateTime.of(
            2026, 7, 20, 13, 30, 0, 0, ZoneOffset.ofHours(9)
        );

        KstDateTimeUtil.TimeRange range = KstDateTimeUtil.getHourlyTimeRange(reference);

        assertEquals(LocalDate.of(2026, 7, 20), range.targetDate());
        assertEquals(12, range.hour());
    }

    @Test
    void formatHourlyBriefingTitleShouldFormatCorrectly() {
        String title = KstDateTimeUtil.formatHourlyBriefingTitle(
            LocalDate.of(2026, 7, 18), 12
        );
        assertEquals("2026-07-18 12\uc2dc \uacbd\uc81c \ube0c\ub9ac\ud551", title);
    }

    @Test
    void formatHourlyBriefingTitleShouldPadSingleDigitHour() {
        String title = KstDateTimeUtil.formatHourlyBriefingTitle(
            LocalDate.of(2026, 7, 18), 9
        );
        assertEquals("2026-07-18 09\uc2dc \uacbd\uc81c \ube0c\ub9ac\ud551", title);
    }

    @Test
    void getFullDayRangeShouldCoverEntireDay() {
        LocalDate date = LocalDate.of(2026, 7, 20);
        KstDateTimeUtil.TimeRange range = KstDateTimeUtil.getFullDayRange(date);

        assertEquals(0, range.start().getHour());
        assertEquals(23, range.end().getHour());
        assertEquals(59, range.end().getMinute());
        assertEquals(59, range.end().getSecond());
        assertEquals(date, range.start().toLocalDate());
        assertEquals(date, range.end().toLocalDate());
    }
}
