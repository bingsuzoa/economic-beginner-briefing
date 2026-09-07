package com.economicbriefing.pipeline;

import java.time.LocalDate;

import com.economicbriefing.util.KstDateTimeUtil;

public record PipelineOptions(
    LocalDate targetDate,
    KstDateTimeUtil.TimeRange timeRange,
    String triggerType
) {
    public static PipelineOptions hourly() {
        KstDateTimeUtil.TimeRange timeRange = KstDateTimeUtil.getHourlyTimeRange();
        return new PipelineOptions(timeRange.targetDate(), timeRange, "SCHEDULER");
    }

    public static PipelineOptions hourly(KstDateTimeUtil.TimeRange timeRange) {
        return new PipelineOptions(timeRange.targetDate(), timeRange, "SCHEDULER");
    }

    public static PipelineOptions manual(LocalDate targetDate) {
        return new PipelineOptions(targetDate, null, "MANUAL");
    }

    /** Replays one exact KST RSS hour without changing the scheduler's normal clock window. */
    public static PipelineOptions manualHourly(LocalDate targetDate, int hour) {
        if (hour < 0 || hour > 23) throw new IllegalArgumentException("hour must be between 0 and 23");
        var start = targetDate.atTime(hour, 0).atZone(KstDateTimeUtil.KST).toOffsetDateTime();
        return new PipelineOptions(targetDate,
                new KstDateTimeUtil.TimeRange(start, start.plusHours(1).minusSeconds(1)), "MANUAL");
    }

    public static PipelineOptions ofToday() {
        return new PipelineOptions(KstDateTimeUtil.getCurrentDate(), null, "MANUAL");
    }
}
