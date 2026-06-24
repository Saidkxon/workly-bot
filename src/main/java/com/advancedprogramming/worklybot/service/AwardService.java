package com.advancedprogramming.worklybot.service;

import com.advancedprogramming.worklybot.entity.Employee;
import com.advancedprogramming.worklybot.repository.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.YearMonth;
import java.util.List;

/**
 * Computes the monthly awards: the hardest worker (most minutes worked), the most
 * punctual employee (fewest late minutes among those who worked), and the most-late
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
        Award mostLate = null;

        for (Employee employee : employees) {
            MonthlySalaryBreakdown breakdown = salaryService.computeBreakdown(employee, month);
            int workedDays = breakdown.days().size();
            if (workedDays == 0) {
                continue; // only consider employees who actually worked this month
            }

            long worked = breakdown.totalWorkedMinutes();
            long late = breakdown.totalLateMinutes();

            if (hardestWorker == null || worked > hardestWorker.value()) {
                hardestWorker = new Award(employee.getFullName(), employee.getDepartment(), worked);
            }
            if (mostPunctual == null || late < mostPunctual.value()
                    || (late == mostPunctual.value() && worked > mostPunctual.value())) {
                mostPunctual = new Award(employee.getFullName(), employee.getDepartment(), late);
            }
            if (late > 0 && (mostLate == null || late > mostLate.value())) {
                mostLate = new Award(employee.getFullName(), employee.getDepartment(), late);
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
