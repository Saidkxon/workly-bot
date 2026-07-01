package com.advancedprogramming.worklybot.service;

import com.advancedprogramming.worklybot.entity.Employee;
import com.advancedprogramming.worklybot.repository.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.YearMonth;
import java.util.List;

/**
 * Computes the monthly awards: the hardest worker (most minutes worked), the most
 * punctual employee (most on-time days among those who worked), and the most-late
 * employee (managers/admins only).
 */
@Service
@RequiredArgsConstructor
public class AwardService {

    private final EmployeeRepository employeeRepository;
    private final SalaryService salaryService;

    public MonthlyAwards computeAwards(YearMonth month) {
        List<Employee> employees = employeeRepository.findAllByActiveTrueOrderByFullNameAsc();

        Award hardestWorker = null;
        Award mostPunctual = null;
        int bestOnTimeDays = -1;          // most-punctual leader's on-time days
        long bestPunctualLate = Long.MAX_VALUE; // ...their total late minutes (tie-break)
        int bestPunctualWorkedDays = -1;  // ...their worked days (final tie-break)
        Award mostLate = null;
        long mostLateWorked = -1;     // worked minutes of the current most-late leader, for tie-breaks

        for (Employee employee : employees) {
            MonthlySalaryBreakdown breakdown = salaryService.computeBreakdown(employee, month);
            int workedDays = breakdown.days().size();
            if (workedDays == 0) {
                continue; // only consider employees who actually worked this month
            }

            long worked = breakdown.totalWorkedMinutes();
            long late = breakdown.totalLateMinutes();
            int onTimeDays = workedDays - breakdown.lateDays();

            if (hardestWorker == null || worked > hardestWorker.value()) {
                hardestWorker = new Award(employee.getFullName(), employee.getDepartment(), worked);
            }
            // Most punctual = the most on-time days over the month. Ranking by on-time
            // days (rather than by fewest late minutes) rewards a full, consistent month,
            // so a flawless 2-day record can no longer beat a near-perfect 30-day one.
            // Ties break by fewer total late minutes than by more worked days.
            if (mostPunctual == null
                    || onTimeDays > bestOnTimeDays
                    || (onTimeDays == bestOnTimeDays && late < bestPunctualLate)
                    || (onTimeDays == bestOnTimeDays && late == bestPunctualLate && workedDays > bestPunctualWorkedDays)) {
                mostPunctual = new Award(employee.getFullName(), employee.getDepartment(), onTimeDays);
                bestOnTimeDays = onTimeDays;
                bestPunctualLate = late;
                bestPunctualWorkedDays = workedDays;
            }
            if (late > 0 && (mostLate == null || late > mostLate.value()
                    || (late == mostLate.value() && worked < mostLateWorked))) {
                mostLate = new Award(employee.getFullName(), employee.getDepartment(), late);
                mostLateWorked = worked;
            }
        }

        if (hardestWorker == null) {
            return null;
        }
        return new MonthlyAwards(month, hardestWorker, mostPunctual, mostLate);
    }

    public record Award(String fullName, String department, long value) {
    }

    public record MonthlyAwards(YearMonth month, Award hardestWorker, Award mostPunctual, Award mostLate) {
    }
}