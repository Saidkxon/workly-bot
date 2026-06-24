package com.advancedprogramming.worklybot.entity.enums;

import java.time.LocalTime;

/**
 * Work shifts. Lateness, reminders and the early-leave rules are all derived from
 * the shift's start and end time (no more single office-wide work time).
 */
public enum Shift {

    MORNING(LocalTime.of(8, 30), LocalTime.of(18, 0)),
    EVENING(LocalTime.of(14, 0), LocalTime.of(21, 0));

    private final LocalTime startTime;
    private final LocalTime endTime;

    Shift(LocalTime startTime, LocalTime endTime) {
        this.startTime = startTime;
        this.endTime = endTime;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public LocalTime getEndTime() {
        return endTime;
    }

    /** e.g. "08:30 - 18:00" — used as the button label during registration. */
    public String getDisplayName() {
        return startTime + " - " + endTime;
    }

    /** Resolve a shift from its "HH:mm - HH:mm" label (whitespace-insensitive). */
    public static Shift fromDisplayName(String value) {
        if (value == null) {
            return null;
        }

        String normalized = value.replace(" ", "");
        for (Shift shift : values()) {
            if (shift.getDisplayName().replace(" ", "").equals(normalized)) {
                return shift;
            }
        }
        return null;
    }

    /** Null-safe default so legacy rows without a shift behave like the morning shift. */
    public static Shift orDefault(Shift shift) {
        return shift == null ? MORNING : shift;
    }
}
