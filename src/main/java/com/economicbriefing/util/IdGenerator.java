package com.economicbriefing.util;

import java.time.LocalDate;

public final class IdGenerator {

    private IdGenerator() {}

    public static String executionId() {
        return "exec-" + System.currentTimeMillis();
    }

    public static String briefingId(LocalDate targetDate) {
        return "briefing-" + targetDate;
    }

    public static String briefingId(LocalDate targetDate, int hour) {
        return "briefing-" + targetDate + "T" + String.format("%02d", hour) + ":00";
    }

    public static String articleId(String sourceName, String guid) {
        return Math.abs(sourceName.hashCode()) + "-" + Math.abs(guid.hashCode());
    }
}
