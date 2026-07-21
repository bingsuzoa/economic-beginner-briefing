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

    public static PipelineOptions ofToday() {
        return new PipelineOptions(KstDateTimeUtil.getCurrentDate(), null, "MANUAL");
    }
}
