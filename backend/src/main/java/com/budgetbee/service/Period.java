package com.budgetbee.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;

/** Turns words like "today", "week", "month" into a [from, to) time range. */
public final class Period {

    public record Range(String key, String label, LocalDateTime from, LocalDateTime to) {
        public LocalDate startDate() { return from.toLocalDate(); }
        public LocalDate endDate() { return to.toLocalDate().minusDays(1); }
    }

    private Period() {}

    public static Range resolve(String key) {
        return resolve(key, LocalDate.now());
    }

    public static Range resolve(String key, LocalDate today) {
        if (key == null) key = "month";
        switch (key) {
            case "today":
                return new Range("today", "today", today.atStartOfDay(), today.plusDays(1).atStartOfDay());
            case "yesterday":
                return new Range("yesterday", "yesterday", today.minusDays(1).atStartOfDay(), today.atStartOfDay());
            case "week": {
                LocalDate mon = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                return new Range("week", "this week", mon.atStartOfDay(), mon.plusDays(7).atStartOfDay());
            }
            case "last_week": {
                LocalDate mon = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusDays(7);
                return new Range("last_week", "last week", mon.atStartOfDay(), mon.plusDays(7).atStartOfDay());
            }
            case "last_month": {
                LocalDate first = today.withDayOfMonth(1).minusMonths(1);
                return new Range("last_month", "last month", first.atStartOfDay(), first.plusMonths(1).atStartOfDay());
            }
            case "year": {
                LocalDate first = today.withDayOfYear(1);
                return new Range("year", "this year", first.atStartOfDay(), first.plusYears(1).atStartOfDay());
            }
            case "all":
                return new Range("all", "all time", LocalDate.of(2000, 1, 1).atStartOfDay(), LocalDate.of(2100, 1, 1).atStartOfDay());
            case "month":
            default: {
                LocalDate first = today.withDayOfMonth(1);
                return new Range("month", "this month", first.atStartOfDay(), first.plusMonths(1).atStartOfDay());
            }
        }
    }

    /** Finds a period word inside free text. Returns null when none is mentioned. */
    public static String detect(String text) {
        String t = text.toLowerCase();
        if (t.contains("yesterday")) return "yesterday";
        if (t.contains("today")) return "today";
        if (t.contains("last month") || t.contains("previous month")) return "last_month";
        if (t.contains("last week") || t.contains("previous week")) return "last_week";
        if (t.contains("this week") || t.matches(".*\\bweek(ly)?\\b.*")) return "week";
        if (t.contains("this year") || t.matches(".*\\byear(ly)?\\b.*")) return "year";
        if (t.contains("this month") || t.matches(".*\\bmonth(ly)?\\b.*")) return "month";
        if (t.contains("all time") || t.contains("overall") || t.contains("ever")) return "all";
        return null;
    }
}
