package com.advancedprogramming.worklybot.service;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * One day of salary-relevant attendance, used for the per-day Excel/mini-app detail.
 *
 * @param warning   true if this is the month's first late day (warned, not charged)
 * @param deduction so'm deducted for this day (0 when on time or when only warned)
 */
public record SalaryDayRow(
        LocalDate date,
        LocalTime arrival,
        LocalTime leave,
        long workedMinutes,
        long lateMinutes,
        long deduction,
        boolean warning
) {
}
