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
}
