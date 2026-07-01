package com.advancedprogramming.worklybot.service;

import com.advancedprogramming.worklybot.config.PenaltyProperties;
import com.advancedprogramming.worklybot.entity.Employee;
import com.advancedprogramming.worklybot.repository.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.YearMonth;
import java.util.List;

/**
 * Computes the monthly awards: the hardest worker (most minutes worked), the most
 * punctual employee (highest on-time rate, among those with enough worked days), and
 * the most-late employee (managers/admins only).
 *
 * <p>Punctuality is rate-based ({@link MonthlySalaryBreakdown#punctualityScore()}) — the
 * same 0–100 score shown in the mini-app — so the bot and the app agree. To stop a tiny
 * flawless sample (e.g. 2 perfect days) from beating a near-perfect full month, only
 * employees who worked at least {@code penalty.min-punctuality-days} days qualify. If
 * nobody clears that bar yet (e.g. early in the month), it falls back to everyone who
 * worked, so an award is still produced.
 */
@Service
@RequiredArgsConstructor
public class AwardService {

    private final EmployeeRepository employeeRepository;
    private final SalaryService salaryService;
    private final PenaltyProperties penaltyProperties;

    public MonthlyAwards computeAwards(YearMonth month) {
        List<Employee> employees = employeeRepository.findAllByActiveTrueOrderByFullNameAsc();

        Award hardestWorker = null;
        Award mostLate = null;
        long mostLateWorked = -1; // worked minutes of the current most-late leader, for tie-breaks

        Punct qualifiedPunctual = null; // best among employees meeting the min-days threshold
        Punct fallbackPunctual = null;  // best among everyone who worked (used only if none qualify)

        int minDays = penaltyProperties.getMinPunctualityDays();

        for (Employee employee : employees) {
            MonthlySalaryBreakdown breakdown = salaryService.computeBreakdown(employee, month);
            int workedDays = breakdown.workedDays();
            if (workedDays == 0) {
                continue; // only consider employees who actually worked this month
            }

            long worked = breakdown.totalWorkedMinutes();
            long late = breakdown.totalLateMinutes();

            if (hardestWorker == null || worked > hardestWorker.value()) {
                hardestWorker = new Award(employee.getFullName(), employee.getDepartment(), worked);
            }

            Punct candidate = new Punct(employee, breakdown.punctualityScore(), breakdown.onTimeDays(), late);
            fallbackPunctual = better(fallbackPunctual, candidate);
            if (workedDays >= minDays) {
                qualifiedPunctual = better(qualifiedPunctual, candidate);
            }

            // Most late: most total late minutes; ties break toward fewer worked minutes
            // (more late relative to how much they were present).
            if (late > 0 && (mostLate == null || late > mostLate.value()
                    || (late == mostLate.value() && worked < mostLateWorked))) {
                mostLate = new Award(employee.getFullName(), employee.getDepartment(), late);
                mostLateWorked = worked;
            }
        }

        if (hardestWorker == null) {
            return null;
        }
        Punct chosen = qualifiedPunctual != null ? qualifiedPunctual : fallbackPunctual;
        Award mostPunctual = chosen == null ? null
                : new Award(chosen.employee().getFullName(), chosen.employee().getDepartment(), chosen.score());
        return new MonthlyAwards(month, hardestWorker, mostPunctual, mostLate);
    }

    /** Higher on-time rate wins; ties break by more on-time days, then fewer late minutes. */
    private Punct better(Punct current, Punct candidate) {
        if (current == null) {
            return candidate;
        }
        if (candidate.score() != current.score()) {
            return candidate.score() > current.score() ? candidate : current;
        }
        if (candidate.onTimeDays() != current.onTimeDays()) {
            return candidate.onTimeDays() > current.onTimeDays() ? candidate : current;
        }
        return candidate.late() < current.late() ? candidate : current;
    }

    private record Punct(Employee employee, int score, int onTimeDays, long late) {
    }

    public record Award(String fullName, String department, long value) {
    }

    public record MonthlyAwards(YearMonth month, Award hardestWorker, Award mostPunctual, Award mostLate) {
    }
}