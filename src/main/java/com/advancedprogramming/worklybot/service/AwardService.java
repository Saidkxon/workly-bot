package com.advancedprogramming.worklybot.service;

import com.advancedprogramming.worklybot.config.PenaltyProperties;
import com.advancedprogramming.worklybot.entity.Employee;
import com.advancedprogramming.worklybot.repository.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Computes the monthly awards: the hardest worker (most minutes worked), the most
 * punctual employee (highest on-time rate, among those with enough worked days), the
 * most-late employee (managers/admins only), and — beyond the single #1 in each of the
 * first two categories — a runners-up list of the next 10 for each, for a fuller monthly
 * shout-out.
 *
 * <p>Punctuality is rate-based ({@link MonthlySalaryBreakdown#punctualityScore()}) — the
 * same 0–100 score shown in the mini-app — so the bot and the app agree. To stop a tiny
 * flawless sample (e.g. 2 perfect days) from beating a near-perfect full month, only
 * employees who worked at least {@code penalty.min-punctuality-days} days qualify for the
 * #1 spot and rank ahead of non-qualifying employees in the runners-up list. The "most
 * worked" award has the same kind of floor ({@code penalty.min-worked-award-days}), so a
 * handful of unusually long shifts can't out-rank someone who worked most of the month. If
 * nobody clears the relevant bar yet (e.g. early in the month), it falls back to everyone
 * who worked, so an award is still produced.
 */
@Service
@RequiredArgsConstructor
public class AwardService {

    private final EmployeeRepository employeeRepository;
    private final SalaryService salaryService;
    private final PenaltyProperties penaltyProperties;

    public MonthlyAwards computeAwards(YearMonth month) {
        List<Employee> employees = employeeRepository.findAllByActiveTrueOrderByFullNameAsc();

        List<RankedEmployee> worked = new ArrayList<>();

        Award mostLate = null;
        long mostLateWorked = -1; // worked minutes of the current most-late leader, for tie-breaks

        int minPunctualityDays = penaltyProperties.getMinPunctualityDays();
        int minWorkedDays = penaltyProperties.getMinWorkedAwardDays();

        for (Employee employee : employees) {
            MonthlySalaryBreakdown breakdown = salaryService.computeBreakdown(employee, month);
            int workedDays = breakdown.workedDays();
            if (workedDays == 0) {
                continue; // only consider employees who actually worked this month
            }

            long workedMinutes = breakdown.totalWorkedMinutes();
            long lateMinutes = breakdown.totalLateMinutes();

            worked.add(new RankedEmployee(
                    employee.getFullName(), employee.getDepartment(),
                    workedMinutes, workedDays, breakdown.lateDays(), lateMinutes,
                    breakdown.punctualityScore(), breakdown.onTimeDays(),
                    workedDays >= minPunctualityDays, workedDays >= minWorkedDays
            ));

            // Latest: most total late minutes; ties break toward fewer worked minutes
            // (later relative to how much they were present).
            if (lateMinutes > 0 && (mostLate == null || lateMinutes > mostLate.value()
                    || (lateMinutes == mostLate.value() && workedMinutes < mostLateWorked))) {
                mostLate = new Award(employee.getFullName(), employee.getDepartment(), lateMinutes);
                mostLateWorked = workedMinutes;
            }
        }

        if (worked.isEmpty()) {
            return null;
        }

        List<RankedEmployee> byWorked = worked.stream()
                .sorted(Comparator
                        .comparing(RankedEmployee::workedQualifies).reversed()
                        .thenComparing(Comparator.comparingLong(RankedEmployee::workedMinutes).reversed()))
                .toList();

        List<RankedEmployee> byPunctuality = worked.stream()
                .sorted(Comparator
                        .comparing(RankedEmployee::punctualityQualifies).reversed()
                        .thenComparing(Comparator.comparingInt(RankedEmployee::punctualityScore).reversed())
                        .thenComparing(Comparator.comparingInt(RankedEmployee::onTimeDays).reversed())
                        .thenComparingLong(RankedEmployee::lateMinutes))
                .toList();

        Award hardestWorker = toWorkedAward(byWorked.get(0));
        Award mostPunctual = toPunctualAward(byPunctuality.get(0));

        List<AwardListEntry> topWorked = byWorked.stream().skip(1).limit(10).map(this::toListEntry).toList();
        List<AwardListEntry> topPunctual = byPunctuality.stream().skip(1).limit(10).map(this::toListEntry).toList();

        return new MonthlyAwards(month, hardestWorker, mostPunctual, mostLate, topWorked, topPunctual);
    }

    private Award toWorkedAward(RankedEmployee r) {
        return new Award(r.fullName(), r.department(), r.workedMinutes());
    }

    private Award toPunctualAward(RankedEmployee r) {
        return new Award(r.fullName(), r.department(), r.punctualityScore());
    }

    private AwardListEntry toListEntry(RankedEmployee r) {
        return new AwardListEntry(r.fullName(), r.department(), r.workedMinutes(), r.workedDays(), r.lateDays(), r.lateMinutes());
    }

    private record RankedEmployee(String fullName, String department, long workedMinutes, int workedDays,
                                  int lateDays, long lateMinutes, int punctualityScore, int onTimeDays,
                                  boolean punctualityQualifies, boolean workedQualifies) {
    }

    public record Award(String fullName, String department, long value) {
    }

    public record AwardListEntry(String fullName, String department, long workedMinutes, int workedDays,
                                 int lateDays, long lateMinutes) {
    }

    public record MonthlyAwards(YearMonth month, Award hardestWorker, Award mostPunctual, Award mostLate,
                                List<AwardListEntry> topWorked, List<AwardListEntry> topPunctual) {
    }
}