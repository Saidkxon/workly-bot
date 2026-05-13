package com.advancedprogramming.worklybot.service;

import com.advancedprogramming.worklybot.bot.BotMessages;
import com.advancedprogramming.worklybot.entity.Attendance;
import com.advancedprogramming.worklybot.entity.Employee;
import com.advancedprogramming.worklybot.repository.AttendanceRepository;
import com.advancedprogramming.worklybot.repository.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
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
    private final Clock appClock;

    public String buildTodayReport() {
        LocalDate today = LocalDate.now(appClock);
        List<Employee> employees = employeeRepository.findAllByActiveTrueOrderByFullNameAsc();

        if (employees.isEmpty()) {
            return "Faol xodimlar topilmadi.";
        }

        Map<Long, Attendance> attendanceByEmployeeId = new HashMap<>();
        for (Attendance attendance : attendanceRepository.findAllByWorkDate(today)) {
            attendanceByEmployeeId.put(attendance.getEmployee().getId(), attendance);
        }

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
                    .append("Kechikish: ").append(formatLateTime(lateMinutes)).append("\n")
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

        Map<Long, Attendance> attendanceByEmployeeId = new HashMap<>();
        for (Attendance attendance : attendanceRepository.findAllByWorkDate(today)) {
            attendanceByEmployeeId.put(attendance.getEmployee().getId(), attendance);
        }

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
                    .append("Kechikish: ").append(formatLateTime(lateMinutes)).append("\n")
                    .append("----------------------\n");
        }

        if (lateCount == 0) {
            return "Bugun kechikkan xodimlar topilmadi.";
        }

        report.append("\nKechikkanlar: ").append(lateCount);

        return report.toString();
    }

    public String buildMonthReport() {
        LocalDate now = LocalDate.now(appClock);
        LocalDate startOfMonth = now.withDayOfMonth(1);
        LocalDate endOfMonth = now.withDayOfMonth(now.lengthOfMonth());
        List<EmployeeMonthlySummary> summaries = buildMonthlySummaries(startOfMonth, endOfMonth);

        if (summaries.isEmpty()) {
            return "Faol xodimlar topilmadi.";
        }

        long totalWorkedDays = 0;
        long totalAbsentDays = 0;
        long totalLateCount = 0;

        StringBuilder report = new StringBuilder("Oylik davomat hisoboti:\n");
        report.append("Davr: ").append(startOfMonth).append(" dan ").append(endOfMonth).append(" gacha\n")
                .append("Ish kunlari hisobida Du-Juma ishlatildi.\n\n");

        for (EmployeeMonthlySummary summary : summaries) {
            totalWorkedDays += summary.getWorkedDays();
            totalAbsentDays += summary.getAbsentDays();
            totalLateCount += summary.getLateCount();

            report.append("Ism familiya: ").append(summary.getFullName()).append("\n")
                    .append("Bo'lim: ").append(summary.getDepartment()).append("\n")
                    .append("Ish kunlari (Du-Juma): ").append(summary.getExpectedWorkDays()).append("\n")
                    .append("Kelgan kunlar: ").append(summary.getWorkedDays()).append("\n")
                    .append("Kelmagan kunlar: ").append(summary.getAbsentDays()).append("\n")
                    .append("Ketishni belgilamagan kunlar: ").append(summary.getMissingCheckoutCount()).append("\n")
                    .append("Kechikkan kunlar: ").append(summary.getLateCount()).append("\n")
                    .append("Jami kechikish: ").append(formatLateTime(summary.getTotalLateMinutes())).append("\n")
                    .append("Jami ishlangan vaqt: ")
                    .append(attendanceService.formatMinutesAsHours(summary.getTotalWorkedMinutes())).append("\n")
                    .append("----------------------\n");
        }

        report.append("\nXulosa:\n")
                .append("Faol xodimlar: ").append(summaries.size()).append("\n")
                .append("Jami kelgan kunlar: ").append(totalWorkedDays).append("\n")
                .append("Jami kelmagan kunlar: ").append(totalAbsentDays).append("\n")
                .append("Jami kechikkan holatlar: ").append(totalLateCount);

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
                    .append("Kechikish: ").append(formatLateTime(lateMinutes)).append("\n")
                    .append("Holat: ").append(attendanceService.calculateLateStatus(attendance)).append("\n")
                    .append("----------------------\n");
        }

        report.append("\nXulosa:\n")
                .append("Kelgan kunlar: ").append(workedDays).append("\n")
                .append("Ketishni belgilamagan kunlar: ").append(missingCheckoutDays).append("\n")
                .append("Kechikkan kunlar: ").append(lateDays).append("\n")
                .append("Jami ishlangan vaqt: ").append(attendanceService.formatMinutesAsHours(totalWorkedMinutes)).append("\n")
                .append("Jami kechikish: ").append(formatLateTime(totalLateMinutes));

        return report.toString();
    }

    public String buildEmployeeHistorySelectionText() {
        List<Employee> employees = employeeRepository.findAllByActiveTrueOrderByFullNameAsc();

        if (employees.isEmpty()) {
            return "Faol xodimlar topilmadi.";
        }

        StringBuilder report = new StringBuilder("Tarixini ko'rmoqchi bo'lgan xodimni tanlang:\n\n");

        for (Employee employee : employees) {
            report.append("Ism familiya: ").append(employee.getFullName()).append("\n")
                    .append("Bo'lim: ").append(employee.getDepartment()).append("\n")
                    .append("Tanlash: /history_").append(employee.getTelegramUserId()).append("\n")
                    .append("----------------------\n");
        }

        return report.toString();
    }

    public byte[] buildMonthExcelReport() {
        LocalDate now = LocalDate.now(appClock);
        LocalDate startOfMonth = now.withDayOfMonth(1);
        LocalDate endOfMonth = now.withDayOfMonth(now.lengthOfMonth());
        List<Employee> employees = employeeRepository.findAllByActiveTrueOrderByFullNameAsc();
        List<Attendance> attendanceList = new ArrayList<>(attendanceRepository.findAllByWorkDateBetween(startOfMonth, endOfMonth));

        if (employees.isEmpty()) {
            return null;
        }

        attendanceList.sort(Comparator
                .comparing(Attendance::getWorkDate)
                .thenComparing(attendance -> attendance.getEmployee().getFullName()));

        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {

            Sheet dailySheet = workbook.createSheet(BotMessages.DAILY_REPORT_SHEET);
            Sheet summarySheet = workbook.createSheet(BotMessages.SUMMARY_SHEET);

            Row dailyHeader = dailySheet.createRow(0);
            dailyHeader.createCell(0).setCellValue(BotMessages.COLUMN_DATE);
            dailyHeader.createCell(1).setCellValue(BotMessages.COLUMN_FULL_NAME);
            dailyHeader.createCell(2).setCellValue(BotMessages.COLUMN_DEPARTMENT);
            dailyHeader.createCell(3).setCellValue(BotMessages.COLUMN_EXPECTED_START);
            dailyHeader.createCell(4).setCellValue(BotMessages.COLUMN_ARRIVAL);
            dailyHeader.createCell(5).setCellValue(BotMessages.COLUMN_LEAVING);
            dailyHeader.createCell(6).setCellValue(BotMessages.COLUMN_WORKED_HOURS);
            dailyHeader.createCell(7).setCellValue(BotMessages.COLUMN_LATE_TIME);
            dailyHeader.createCell(8).setCellValue(BotMessages.COLUMN_ATTENDANCE_STATUS);

            int dailyRowNum = 1;

            for (Attendance attendance : attendanceList) {
                Row row = dailySheet.createRow(dailyRowNum++);

                String arrival = attendance.getArrivalTime() == null
                        ? "Belgilanmagan"
                        : attendance.getArrivalTime().toLocalTime().withNano(0).toString();

                String leaving = attendance.getLeaveTime() == null
                        ? "Belgilanmagan"
                        : attendance.getLeaveTime().toLocalTime().withNano(0).toString();

                row.createCell(0).setCellValue(attendance.getWorkDate().toString());
                row.createCell(1).setCellValue(attendance.getEmployee().getFullName());
                row.createCell(2).setCellValue(attendance.getEmployee().getDepartment());
                row.createCell(3).setCellValue(attendanceService.getWorkStartTime().toString());
                row.createCell(4).setCellValue(arrival);
                row.createCell(5).setCellValue(leaving);
                row.createCell(6).setCellValue(attendanceService.calculateWorkedHours(attendance));
                row.createCell(7).setCellValue(formatLateTime(attendanceService.calculateLateMinutes(attendance)));
                row.createCell(8).setCellValue(attendanceService.calculateLateStatus(attendance));
            }

            for (int i = 0; i < 9; i++) {
                dailySheet.autoSizeColumn(i);
            }

            Row summaryHeader = summarySheet.createRow(0);
            summaryHeader.createCell(0).setCellValue(BotMessages.COLUMN_FULL_NAME);
            summaryHeader.createCell(1).setCellValue(BotMessages.COLUMN_DEPARTMENT);
            summaryHeader.createCell(2).setCellValue(BotMessages.COLUMN_EXPECTED_DAYS);
            summaryHeader.createCell(3).setCellValue(BotMessages.COLUMN_WORKED_DAYS);
            summaryHeader.createCell(4).setCellValue(BotMessages.COLUMN_ABSENT_DAYS);
            summaryHeader.createCell(5).setCellValue(BotMessages.COLUMN_MISSING_CHECKOUT_DAYS);
            summaryHeader.createCell(6).setCellValue(BotMessages.COLUMN_TOTAL_HOURS);
            summaryHeader.createCell(7).setCellValue(BotMessages.COLUMN_LATE_COUNT);
            summaryHeader.createCell(8).setCellValue(BotMessages.COLUMN_TOTAL_LATE_TIME);

            int summaryRowNum = 1;

            for (EmployeeMonthlySummary summary : buildMonthlySummaries(startOfMonth, endOfMonth)) {
                Row row = summarySheet.createRow(summaryRowNum++);

                row.createCell(0).setCellValue(summary.getFullName());
                row.createCell(1).setCellValue(summary.getDepartment());
                row.createCell(2).setCellValue(summary.getExpectedWorkDays());
                row.createCell(3).setCellValue(summary.getWorkedDays());
                row.createCell(4).setCellValue(summary.getAbsentDays());
                row.createCell(5).setCellValue(summary.getMissingCheckoutCount());
                row.createCell(6).setCellValue(attendanceService.formatMinutesAsHours(summary.getTotalWorkedMinutes()));
                row.createCell(7).setCellValue(summary.getLateCount());
                row.createCell(8).setCellValue(formatLateTime(summary.getTotalLateMinutes()));
            }

            for (int i = 0; i < 9; i++) {
                summarySheet.autoSizeColumn(i);
            }

            workbook.write(outputStream);
            return outputStream.toByteArray();

        } catch (Exception e) {
            log.error("Failed to build month Excel report", e);
            throw new IllegalStateException("Failed to build month Excel report", e);
        }
    }

    private List<EmployeeMonthlySummary> buildMonthlySummaries(LocalDate startDate, LocalDate endDate) {
        List<Employee> employees = employeeRepository.findAllByActiveTrueOrderByFullNameAsc();
        if (employees.isEmpty()) {
            return List.of();
        }

        Map<Long, List<Attendance>> attendanceByEmployeeId = new HashMap<>();
        for (Attendance attendance : attendanceRepository.findAllByWorkDateBetween(startDate, endDate)) {
            attendanceByEmployeeId
                    .computeIfAbsent(attendance.getEmployee().getId(), ignored -> new ArrayList<>())
                    .add(attendance);
        }

        int expectedWorkDays = countWeekdays(startDate, endDate);
        List<EmployeeMonthlySummary> summaries = new ArrayList<>();

        for (Employee employee : employees) {
            EmployeeMonthlySummary summary = new EmployeeMonthlySummary(employee.getFullName(), employee.getDepartment(), expectedWorkDays);
            List<Attendance> attendances = attendanceByEmployeeId.getOrDefault(employee.getId(), List.of());

            for (Attendance attendance : attendances) {
                if (attendance.getArrivalTime() != null) {
                    summary.incrementWorkedDays();
                }

                if (attendance.getArrivalTime() != null && attendance.getLeaveTime() == null) {
                    summary.incrementMissingCheckoutCount();
                }

                long lateMinutes = attendanceService.calculateLateMinutes(attendance);
                summary.addMinutes(attendanceService.calculateWorkedMinutes(attendance));
                summary.addLateMinutes(lateMinutes);

                if (lateMinutes > 0) {
                    summary.incrementLateCount();
                }
            }

            summary.setAbsentDays(Math.max(0, expectedWorkDays - summary.getWorkedDays()));
            summaries.add(summary);
        }

        return summaries;
    }

    private int countWeekdays(LocalDate startDate, LocalDate endDate) {
        int count = 0;

        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            DayOfWeek dayOfWeek = date.getDayOfWeek();
            if (dayOfWeek != DayOfWeek.SATURDAY && dayOfWeek != DayOfWeek.SUNDAY) {
                count++;
            }
        }

        return count;
    }

    private String formatLateTime(long lateMinutes) {
        return attendanceService.formatMinutesAsHours(lateMinutes);
    }

    private String formatAttendanceTime(java.time.LocalDateTime dateTime) {
        return dateTime == null ? "Belgilanmagan" : dateTime.toLocalTime().withNano(0).toString();
    }

    private static class EmployeeMonthlySummary {
        private final String fullName;
        private final String department;
        private final int expectedWorkDays;
        private int workedDays;
        private int absentDays;
        private int missingCheckoutCount;
        private long totalWorkedMinutes;
        private int lateCount;
        private long totalLateMinutes;

        private EmployeeMonthlySummary(String fullName, String department, int expectedWorkDays) {
            this.fullName = fullName;
            this.department = department;
            this.expectedWorkDays = expectedWorkDays;
        }

        public void incrementWorkedDays() {
            this.workedDays++;
        }

        public void incrementMissingCheckoutCount() {
            this.missingCheckoutCount++;
        }

        public void addMinutes(long minutes) {
            this.totalWorkedMinutes += minutes;
        }

        public void incrementLateCount() {
            this.lateCount++;
        }

        public void addLateMinutes(long minutes) {
            this.totalLateMinutes += minutes;
        }

        public String getFullName() {
            return fullName;
        }

        public String getDepartment() {
            return department;
        }

        public int getExpectedWorkDays() {
            return expectedWorkDays;
        }

        public int getWorkedDays() {
            return workedDays;
        }

        public int getAbsentDays() {
            return absentDays;
        }

        public void setAbsentDays(int absentDays) {
            this.absentDays = absentDays;
        }

        public int getMissingCheckoutCount() {
            return missingCheckoutCount;
        }

        public long getTotalWorkedMinutes() {
            return totalWorkedMinutes;
        }

        public int getLateCount() {
            return lateCount;
        }

        public long getTotalLateMinutes() {
            return totalLateMinutes;
        }
    }
}
