package com.advancedprogramming.worklybot.service;

import java.time.YearMonth;
import java.util.List;

/**
 * Full monthly salary result for one employee. Feeds the per-employee Excel file
 * and the employee's in-app salary card.
 */
public record MonthlySalaryBreakdown(
        Long telegramUserId,
        String fullName,
        String departmentName,
        String shiftName,
        YearMonth month,
        long baseSalary,
        long totalDeduction,
        long netSalary,
        int lateDays,
        int penalizedDays,
        long totalLateMinutes,
        long totalWorkedMinutes,
        List<SalaryDayRow> days
) {
    /** Days the employee actually has an attendance record for this month. */
    public int workedDays() {
        return days == null ? 0 : days.size();
    }

    /** Worked days on which the employee was not late. */
    public int onTimeDays() {
        return Math.max(0, workedDays() - lateDays);
    }

    /**
     * Rate-based punctuality, 0–100 (share of worked days that were on time). This is the
     * single definition of "punctuality" used by both the mini-app score and the monthly
     * award, so the two never diverge.
     */
    public int punctualityScore() {
        int worked = workedDays();
        return worked == 0 ? 0 : Math.round((onTimeDays() * 100f) / worked);
    }
}