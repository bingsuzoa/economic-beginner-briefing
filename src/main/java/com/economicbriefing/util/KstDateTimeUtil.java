package com.economicbriefing.util;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

public final class KstDateTimeUtil {

    public static final ZoneId KST = ZoneId.of("Asia/Seoul");
    public static final ZoneOffset KST_OFFSET = ZoneOffset.ofHours(9);

    private KstDateTimeUtil() {}

    public static OffsetDateTime now() {
        return OffsetDateTime.now(KST);
    }

    public static LocalDate getCurrentDate() {
        return LocalDate.now(KST);
    }

    public static LocalDate getYesterdayDate() {
        return LocalDate.now(KST).minusDays(1);
    }

    public static OffsetDateTime getTargetDateStart(LocalDate date) {
        return date.atStartOfDay(KST).toOffsetDateTime();
    }

    public static OffsetDateTime getTargetDateEnd(LocalDate date) {
        return date.atStartOfDay(KST).toOffsetDateTime()
                .plusDays(1).minusSeconds(1);
    }

    public static TimeRange getHourlyTimeRange(OffsetDateTime referenceTime) {
        OffsetDateTime ref = referenceTime.atZoneSameInstant(KST).toOffsetDateTime();
        OffsetDateTime currentHour = ref.truncatedTo(ChronoUnit.HOURS);
        OffsetDateTime windowStart = currentHour.minusHours(1);
        OffsetDateTime windowEnd = currentHour.minusSeconds(1);
        return new TimeRange(windowStart, windowEnd);
    }

    public static TimeRange getHourlyTimeRange() {
        return getHourlyTimeRange(now());
    }

    public static TimeRange getFullDayRange(LocalDate date) {
        OffsetDateTime start = date.atStartOfDay(KST).toOffsetDateTime();
        OffsetDateTime end = start.plusDays(1).minusSeconds(1);
        return new TimeRange(start, end);
    }

    public static String formatHourlyBriefingTitle(LocalDate targetDate, int hour) {
        String paddedHour = String.format("%02d", hour);
        return targetDate + " " + paddedHour + "\uc2dc \uacbd\uc81c \ube0c\ub9ac\ud551";
    }

    public record TimeRange(OffsetDateTime start, OffsetDateTime end) {
        public LocalDate targetDate() {
            return start.toLocalDate();
        }

        public int hour() {
            return start.getHour();
        }
    }
}
