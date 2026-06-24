package com.advancedprogramming.worklybot.service;

import com.advancedprogramming.worklybot.bot.BotMessages;
import com.advancedprogramming.worklybot.entity.Attendance;
import com.advancedprogramming.worklybot.entity.Employee;
import com.advancedprogramming.worklybot.repository.AttendanceRepository;
import com.advancedprogramming.worklybot.repository.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class ReportService {

    private final AttendanceRepository attendanceRepository;
    private final EmployeeRepository employeeRepository;
    private final AttendanceService attendanceService;
    private final SalaryService salaryService;
    private final Clock appClock;

    public String buildTodayReport() {
        LocalDate today = LocalDate.now(appClock);
        List<Employee> employees = employeeRepository.findAllByActiveTrueOrderByFullNameAsc();

        if (employees.isEmpty()) {
            return "Faol xodimlar topilmadi.";
        }

        Map<Long, Attendance> attendanceByEmployeeId = attendanceByEmployee(today);

        long arrivedCount = 0;
        long absentCount = 0;
        long missingCheckoutCount = 0;
        long lateCount = 0;

        StringBuilder report = new StringBuilder("Bugungi davomat hisoboti:\n\n");

        for (Employee employee : employees) {
            Attendance attendance = attendanceByEmployeeId.get(employee.getId());
            String arrival = "Belgilanmagan";
            String leaving = "Belgilanmagan";
            String status = BotMessages.STATUS_ABSENT;
            long lateMinutes = 0;

            if (attendance != null) {
                if (attendance.getArrivalTime() != null) {
                    arrival = attendance.getArrivalTime().toLocalTime().withNano(0).toString();
                    arrivedCount++;
                }
                if (attendance.getLeaveTime() != null) {
                    leaving = attendance.getLeaveTime().toLocalTime().withNano(0).toString();
                }
                status = attendanceService.calculateLateStatus(attendance);
                lateMinutes = attendanceService.calculateLateMinutes(attendance);
                if (lateMinutes > 0) {
                    lateCount++;
                }
                if (attendance.getArrivalTime() != null && attendance.getLeaveTime() == null) {
                    missingCheckoutCount++;
                }
            } else {
                absentCount++;
            }

            report.append("Ism familiya: ").append(employee.getFullName()).append("\n")
                    .append("Bo'lim: ").append(employee.getDepartment()).append("\n")
                    .append("Kelgan vaqt: ").append(arrival).append("\n")
                    .append("Ketgan vaqt: ").append(leaving).append("\n")
                    .append("Kechikish: ").append(attendanceService.formatMinutesAsHours(lateMinutes)).append("\n")
                    .append("Holat: ").append(status).append("\n")
                    .append("----------------------\n");
        }

        report.append("\nJami faol xodimlar: ").append(employees.size()).append("\n")
                .append("Kelganlar: ").append(arrivedCount).append("\n")
                .append("Kelmaganlar: ").append(absentCount).append("\n")
                .append("Ketishni belgilamaganlar: ").append(missingCheckoutCount).append("\n")
                .append("Kechikkanlar: ").append(lateCount)
                .append(" (ro'yxat: ").append(BotMessages.CMD_LATE_EMPLOYEES_LIST).append(")");

        return report.toString();
    }

    public String buildTodayLateEmployeesList() {
        LocalDate today = LocalDate.now(appClock);
        List<Employee> employees = employeeRepository.findAllByActiveTrueOrderByFullNameAsc();

        if (employees.isEmpty()) {
            return "Faol xodimlar topilmadi.";
        }

        Map<Long, Attendance> attendanceByEmployeeId = attendanceByEmployee(today);
        StringBuilder report = new StringBuilder("Bugun kechikkan xodimlar:\n\n");
        long lateCount = 0;

        for (Employee employee : employees) {
            Attendance attendance = attendanceByEmployeeId.get(employee.getId());
            if (attendance == null) {
                continue;
            }
            long lateMinutes = attendanceService.calculateLateMinutes(attendance);
            if (lateMinutes <= 0) {
                continue;
            }
            lateCount++;

            String arrival = attendance.getArrivalTime() == null
                    ? "Belgilanmagan"
                    : attendance.getArrivalTime().toLocalTime().withNano(0).toString();

            report.append("Ism familiya: ").append(employee.getFullName()).append("\n")
                    .append("Bo'lim: ").append(employee.getDepartment()).append("\n")
                    .append("Kelgan vaqt: ").append(arrival).append("\n")
                    .append("Kechikish: ").append(attendanceService.formatMinutesAsHours(lateMinutes)).append("\n")
                    .append("----------------------\n");
        }

        if (lateCount == 0) {
            return "Bugun kechikkan xodimlar topilmadi.";
        }

        report.append("\nKechikkanlar: ").append(lateCount);
        return report.toString();
    }

    /**
     * Monthly text summary. Routed through the salary engine, so it counts every day
     * (weekends included) and never inflates results with days that have not happened yet.
     */
    public String buildMonthReport() {
        YearMonth month = YearMonth.now(appClock);
        List<Employee> employees = employeeRepository.findAllByActiveTrueOrderByFullNameAsc();
        if (employees.isEmpty()) {
            return "Faol xodimlar topilmadi.";
        }

        long totalWorkedDays = 0;
        long totalLateDays = 0;
        long totalPenalty = 0;

        StringBuilder report = new StringBuilder("Oylik davomat va maosh hisoboti:\n");
        report.append("Oy: ").append(month).append("\n\n");

        for (Employee employee : employees) {
            MonthlySalaryBreakdown breakdown = salaryService.computeBreakdown(employee, month);
            totalWorkedDays += breakdown.days().size();
            totalLateDays += breakdown.lateDays();
            totalPenalty += breakdown.totalDeduction();

            report.append("Ism familiya: ").append(breakdown.fullName()).append("\n")
                    .append("Bo'lim: ").append(employee.getDepartment()).append("\n")
                    .append("Smena: ").append(breakdown.shiftName()).append("\n")
                    .append("Kelgan kunlar: ").append(breakdown.days().size()).append("\n")
                    .append("Kechikkan kunlar: ").append(breakdown.lateDays()).append("\n")
                    .append("Jarimali kunlar: ").append(breakdown.penalizedDays()).append("\n")
                    .append("Jami ishlangan: ").append(SalaryService.formatMinutes(breakdown.totalWorkedMinutes())).append("\n")
                    .append("Fiksa maoshi: ").append(SalaryService.formatSum(breakdown.baseSalary())).append("\n")
                    .append("Jarimalar: ").append(SalaryService.formatSum(breakdown.totalDeduction())).append("\n")
                    .append("Umumiy miqdor: ").append(SalaryService.formatSum(breakdown.netSalary())).append("\n")
                    .append("----------------------\n");
        }

        report.append("\nXulosa:\n")
                .append("Faol xodimlar: ").append(employees.size()).append("\n")
                .append("Jami kelgan kunlar: ").append(totalWorkedDays).append("\n")
                .append("Jami kechikkan kunlar: ").append(totalLateDays).append("\n")
                .append("Jami jarimalar: ").append(SalaryService.formatSum(totalPenalty));

        return report.toString();
    }

    public String buildCurrentMonthHistory(Employee employee) {
        return buildMonthlyHistory(employee, YearMonth.now(appClock));
    }

    public String buildMonthlyHistory(Employee employee, YearMonth month) {
        if (employee == null) {
            return "Xodim topilmadi.";
        }

        LocalDate startOfMonth = month.atDay(1);
        LocalDate endOfMonth = month.atEndOfMonth();
        List<Attendance> attendances = attendanceRepository
                .findAllByEmployeeAndWorkDateBetweenOrderByWorkDateAsc(employee, startOfMonth, endOfMonth);

        StringBuilder report = new StringBuilder("Oylik kelish-ketish tarixi:\n");
        report.append("Xodim: ").append(employee.getFullName()).append("\n")
                .append("Bo'lim: ").append(employee.getDepartment()).append("\n")
                .append("Oy: ").append(month).append("\n\n");

        if (attendances.isEmpty()) {
            return report.append("Bu oy uchun davomat yozuvlari topilmadi.").toString();
        }

        long workedDays = 0;
        long missingCheckoutDays = 0;
        long lateDays = 0;
        long totalWorkedMinutes = 0;
        long totalLateMinutes = 0;

        for (Attendance attendance : attendances) {
            long workedMinutes = attendanceService.calculateWorkedMinutes(attendance);
            long lateMinutes = attendanceService.calculateLateMinutes(attendance);

            if (attendance.getArrivalTime() != null) {
                workedDays++;
            }
            if (attendance.getArrivalTime() != null && attendance.getLeaveTime() == null) {
                missingCheckoutDays++;
            }
            if (lateMinutes > 0) {
                lateDays++;
            }
            totalWorkedMinutes += workedMinutes;
            totalLateMinutes += lateMinutes;

            report.append("Sana: ").append(attendance.getWorkDate()).append("\n")
                    .append("Kelgan vaqt: ").append(formatAttendanceTime(attendance.getArrivalTime())).append("\n")
                    .append("Ketgan vaqt: ").append(formatAttendanceTime(attendance.getLeaveTime())).append("\n")
                    .append("Ishlangan vaqt: ").append(attendanceService.formatMinutesAsHours(workedMinutes)).append("\n")
                    .append("Kechikish: ").append(attendanceService.formatMinutesAsHours(lateMinutes)).append("\n")
                    .append("Holat: ").append(attendanceService.calculateLateStatus(attendance)).append("\n")
                    .append("----------------------\n");
        }

        report.append("\nXulosa:\n")
                .append("Kelgan kunlar: ").append(workedDays).append("\n")
                .append("Ketishni belgilamagan kunlar: ").append(missingCheckoutDays).append("\n")
                .append("Kechikkan kunlar: ").append(lateDays).append("\n")
                .append("Jami ishlangan vaqt: ").append(attendanceService.formatMinutesAsHours(totalWorkedMinutes)).append("\n")
                .append("Jami kechikish: ").append(attendanceService.formatMinutesAsHours(totalLateMinutes));

        return report.toString();
    }

    public String buildEmployeeHistorySelectionText() {
        List<Employee> employees = employeeRepository.findAllByActiveTrueOrderByFullNameAsc();
        if (employees.isEmpty()) {
            return "Faol xodimlar topilmadi.";
        }

        StringBuilder report = new StringBuilder("Excel hisobotini ko'rmoqchi bo'lgan xodimni tanlang:\n\n");
        for (Employee employee : employees) {
            report.append("Ism familiya: ").append(employee.getFullName()).append("\n")
                    .append("Bo'lim: ").append(employee.getDepartment()).append("\n")
                    .append("Tanlash: /history_").append(employee.getTelegramUserId()).append("\n")
                    .append("----------------------\n");
        }
        return report.toString();
    }

    private Map<Long, Attendance> attendanceByEmployee(LocalDate date) {
        Map<Long, Attendance> map = new HashMap<>();
        for (Attendance attendance : attendanceRepository.findAllByWorkDate(date)) {
            map.put(attendance.getEmployee().getId(), attendance);
        }
        return map;
    }

    private String formatAttendanceTime(java.time.LocalDateTime dateTime) {
        return dateTime == null ? "Belgilanmagan" : dateTime.toLocalTime().withNano(0).toString();
    }
}
