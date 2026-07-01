package com.advancedprogramming.worklybot.service;

import com.advancedprogramming.worklybot.config.PenaltyProperties;
import com.advancedprogramming.worklybot.entity.Attendance;
import com.advancedprogramming.worklybot.entity.DepartmentSalary;
import com.advancedprogramming.worklybot.entity.Employee;
import com.advancedprogramming.worklybot.entity.enums.Department;
import com.advancedprogramming.worklybot.entity.enums.Shift;
import com.advancedprogramming.worklybot.repository.AttendanceRepository;
import com.advancedprogramming.worklybot.repository.DepartmentSalaryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

/**
 * Salary + penalty engine. Computes lateness from the employee's {@link Shift} and the
 * configured grace, so it does not depend on AttendanceService. Penalties are
 * correction-aware automatically — an approved correction rewrites the stored arrival
 * time, so the recomputed late minutes (and the deduction) simply drop.
 *
 * Rule: the first late day of a calendar month is a warning only (no deduction); each
 * later late day is charged {@code amountPerLateMinute} per late minute.
 */
@Service
@RequiredArgsConstructor
public class SalaryService {

    private final DepartmentSalaryRepository departmentSalaryRepository;
    private final AttendanceRepository attendanceRepository;
    private final PenaltyProperties penaltyProperties;
    private final WorkCalendarService workCalendarService;
    private final Clock appClock;

    // ---- Editable base-salary configuration ------------------------------------

    public long getMonthlyBase(Department department) {
        if (department == null) {
            return penaltyProperties.getDefaultBaseSalary();
        }
        return departmentSalaryRepository.findById(department)
                .map(DepartmentSalary::getMonthlyAmount)
                .orElseGet(() -> defaultFor(department));
    }

    public long getMonthlyBase(Employee employee) {
        if (Shift.orDefault(employee.getShift()) == Shift.LONG_DAY) {
            return 4_000_000L;
        }
        return getMonthlyBase(Department.fromDisplayName(employee.getDepartment()));
    }

    @Transactional
    public long updateMonthlyBase(Department department, long newAmount) {
        DepartmentSalary salary = departmentSalaryRepository.findById(department)
                .orElseGet(() -> DepartmentSalary.builder().department(department).build());
        salary.setMonthlyAmount(Math.max(0, newAmount));
        departmentSalaryRepository.save(salary);
        return salary.getMonthlyAmount();
    }

    @Transactional
    public void seedDefaults() {
        for (Department department : Department.values()) {
            if (departmentSalaryRepository.findById(department).isEmpty()) {
                departmentSalaryRepository.save(DepartmentSalary.builder()
                        .department(department)
                        .monthlyAmount(defaultFor(department))
                        .build());
            }
        }
    }

    public String departmentSalariesText() {
        StringBuilder sb = new StringBuilder("Bo'limlar bo'yicha fiksa maoshlar:\n\n");
        for (Department department : Department.values()) {
            sb.append(department.getDisplayName()).append(": ")
                    .append(formatSum(getMonthlyBase(department))).append("\n")
                    .append("O'zgartirish: /setsalary_").append(department.name()).append("_<summa>\n")
                    .append("----------------------\n");
        }
        sb.append("\nMasalan: /setsalary_QABUL_BOLIMI_4500000");
        return sb.toString();
    }

    private long defaultFor(Department department) {
        return department == Department.QABUL_BOLIMI
                ? penaltyProperties.getDefaultQabulSalary()
                : penaltyProperties.getDefaultBaseSalary();
    }

    // ---- Monthly breakdown -----------------------------------------------------

    public MonthlySalaryBreakdown computeBreakdown(Employee employee, YearMonth month) {
        long base = getMonthlyBase(employee);
        Shift shift = Shift.orDefault(employee.getShift());

        List<Attendance> attendances = attendanceRepository
                .findAllByEmployeeAndWorkDateBetweenOrderByWorkDateAsc(
                        employee, month.atDay(1), month.atEndOfMonth());

        List<SalaryDayRow> days = new ArrayList<>();
        long totalDeduction = 0;
        long totalLateMinutes = 0;
        long totalWorkedMinutes = 0;
        int lateDays = 0;
        int penalizedDays = 0;
        boolean firstLateUsed = false;

        for (Attendance attendance : attendances) {
            long worked = workedMinutes(attendance);
            long late = lateMinutes(attendance, shift);
            totalWorkedMinutes += worked;

            long deduction = 0;
            boolean warning = false;

            if (late > 0) {
                lateDays++;
                totalLateMinutes += late;
                if (!firstLateUsed) {
                    firstLateUsed = true;
                    warning = true;
                } else {
                    deduction = late * penaltyProperties.getAmountPerLateMinute();
                    totalDeduction += deduction;
                    penalizedDays++;
                }
            }

            days.add(new SalaryDayRow(
                    attendance.getWorkDate(),
                    toLocalTime(attendance.getArrivalTime()),
                    toLocalTime(attendance.getLeaveTime()),
                    worked,
                    late,
                    deduction,
                    warning
            ));
        }

        long net = Math.max(0, base - totalDeduction);

        return new MonthlySalaryBreakdown(
                employee.getTelegramUserId(),
                employee.getFullName(),
                employee.getDepartment(),
                shift.getDisplayName(),
                month,
                base,
                totalDeduction,
                net,
                lateDays,
                penalizedDays,
                totalLateMinutes,
                totalWorkedMinutes,
                days
        );
    }

    /**
     * Message shown right after an arrival check-in: nothing if on time, a warning if it is
     * the first late day of the month, otherwise the deduction that will apply.
     */
    public String arrivalLatenessNote(Employee employee) {
        LocalDate today = LocalDate.now(appClock);
        YearMonth month = YearMonth.from(today);
        Shift shift = Shift.orDefault(employee.getShift());

        Attendance todayAttendance = attendanceRepository.findByEmployeeAndWorkDate(employee, today).orElse(null);
        if (todayAttendance == null) {
            return "";
        }
        long lateToday = lateMinutes(todayAttendance, shift);
        if (lateToday <= 0) {
            return "";
        }

        long priorLateDays = attendanceRepository
                .findAllByEmployeeAndWorkDateBetweenOrderByWorkDateAsc(employee, month.atDay(1), today.minusDays(1))
                .stream()
                .filter(a -> lateMinutes(a, shift) > 0)
                .count();

        if (priorLateDays == 0) {
            return "\n\n⚠️ Bugun " + lateToday + " daqiqa kechikdingiz. Bu oydagi birinchi kechikish — ogohlantirish beriladi, jarima qo'llanmaydi. Keyingi kechikishlar uchun jarima hisoblanadi.";
        }

        long deduction = lateToday * penaltyProperties.getAmountPerLateMinute();
        return "\n\n⚠️ Bugun " + lateToday + " daqiqa kechikdingiz. Jarima: " + formatSum(deduction)
                + ". Bu summa oylik maoshingizdan ushlanadi.";
    }

    // ---- Shift-aware helpers ---------------------------------------------------

    private long lateMinutes(Attendance attendance, Shift shift) {
        if (attendance.getArrivalTime() == null) {
            return 0;
        }
        // Off days (configured weekly off-days + company holidays) are penalty-free:
        // working is voluntary, so arrivals are never counted as late and never penalised.
        // Returning 0 here also keeps such a day from consuming the month's first-late
        // warning, since the breakdown and the check-in note both derive lateness here.
        if (workCalendarService.isPenaltyFreeDay(attendance.getWorkDate())) {
            return 0;
        }
        LocalTime effectiveStart = shift.getStartTime().plusMinutes(penaltyProperties.getGraceMinutes());
        LocalTime arrival = attendance.getArrivalTime().toLocalTime();
        if (!arrival.isAfter(effectiveStart)) {
            return 0;
        }
        return Duration.between(effectiveStart, arrival).toMinutes();
    }

    private long workedMinutes(Attendance attendance) {
        if (attendance.getArrivalTime() == null) {
            return 0;
        }
        return Math.max(0, Duration.between(attendance.getArrivalTime(), effectiveLeaveTime(attendance)).toMinutes());
    }

    /**
     * Leave time used for worked-minute calculations. A missing checkout credits the
     * employee up to their shift end for that day (or only up to "now" if the day is
     * still in progress) instead of dropping the whole day to zero.
     */
    private java.time.LocalDateTime effectiveLeaveTime(Attendance attendance) {
        if (attendance.getLeaveTime() != null) {
            return attendance.getLeaveTime();
        }
        LocalDate workDate = attendance.getWorkDate();
        LocalTime shiftEnd = Shift.orDefault(attendance.getEmployee().getShift()).getEndTime();
        java.time.LocalDateTime shiftEndToday = workDate.atTime(shiftEnd);
        java.time.LocalDateTime now = java.time.LocalDateTime.now(appClock);
        if (workDate.equals(now.toLocalDate()) && now.isBefore(shiftEndToday)) {
            return now;
        }
        return shiftEndToday;
    }

    private LocalTime toLocalTime(java.time.LocalDateTime dateTime) {
        return dateTime == null ? null : dateTime.toLocalTime().withNano(0);
    }

    // ---- Formatting helpers ----------------------------------------------------

    public static String formatSum(long amount) {
        boolean negative = amount < 0;
        String digits = Long.toString(Math.abs(amount));
        StringBuilder grouped = new StringBuilder();
        int count = 0;
        for (int i = digits.length() - 1; i >= 0; i--) {
            grouped.append(digits.charAt(i));
            if (++count % 3 == 0 && i > 0) {
                grouped.append(' ');
            }
        }
        String result = grouped.reverse().toString();
        return (negative ? "-" : "") + result + " so'm";
    }

    public static String formatMinutes(long totalMinutes) {
        long hours = totalMinutes / 60;
        long minutes = totalMinutes % 60;
        return hours + " soat " + minutes + " daqiqa";
    }
}