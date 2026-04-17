package com.advancedprogramming.worklybot.service;

import com.advancedprogramming.worklybot.bot.BotMessages;
import com.advancedprogramming.worklybot.entity.Attendance;
import com.advancedprogramming.worklybot.repository.AttendanceRepository;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ReportService {

    private final AttendanceRepository attendanceRepository;
    private final AttendanceService attendanceService;

    public String buildTodayReport() {
        LocalDate today = LocalDate.now();
        List<Attendance> attendanceList = attendanceRepository.findAllByWorkDate(today);

        if (attendanceList.isEmpty()) {
            return "Bugun uchun davomat ma'lumotlari yo'q.";
        }

        StringBuilder report = new StringBuilder("Bugungi davomat hisoboti:\n\n");

        for (Attendance attendance : attendanceList) {
            String employeeName = attendance.getEmployee().getFullName();
            String department = attendance.getEmployee().getDepartment();

            String arrival = attendance.getArrivalTime() == null
                    ? "Belgilanmagan"
                    : attendance.getArrivalTime().toLocalTime().withNano(0).toString();

            String leaving = attendance.getLeaveTime() == null
                    ? "Belgilanmagan"
                    : attendance.getLeaveTime().toLocalTime().withNano(0).toString();

            report.append("Ism familiya: ").append(employeeName).append("\n")
                    .append("Bo'lim: ").append(department).append("\n")
                    .append("Kelgan vaqt: ").append(arrival).append("\n")
                    .append("Ketgan vaqt: ").append(leaving).append("\n")
                    .append("----------------------\n");
        }

        return report.toString();
    }

    public String buildMonthReport() {
        LocalDate now = LocalDate.now();
        LocalDate startOfMonth = now.withDayOfMonth(1);
        LocalDate endOfMonth = now.withDayOfMonth(now.lengthOfMonth());

        List<Attendance> attendanceList = attendanceRepository.findAllByWorkDateBetween(startOfMonth, endOfMonth);

        if (attendanceList.isEmpty()) {
            return "Bu oy uchun davomat ma'lumotlari yo'q.";
        }

        StringBuilder report = new StringBuilder("Oylik davomat hisoboti:\n");
        report.append("Davr: ").append(startOfMonth).append(" dan ").append(endOfMonth).append(" gacha\n\n");

        for (Attendance attendance : attendanceList) {
            String employeeName = attendance.getEmployee().getFullName();
            String department = attendance.getEmployee().getDepartment();

            String arrival = attendance.getArrivalTime() == null
                    ? "Belgilanmagan"
                    : attendance.getArrivalTime().toLocalTime().withNano(0).toString();

            String leaving = attendance.getLeaveTime() == null
                    ? "Belgilanmagan"
                    : attendance.getLeaveTime().toLocalTime().withNano(0).toString();

            report.append("Sana: ").append(attendance.getWorkDate()).append("\n")
                    .append("Ism familiya: ").append(employeeName).append("\n")
                    .append("Bo'lim: ").append(department).append("\n")
                    .append("Kelgan vaqt: ").append(arrival).append("\n")
                    .append("Ketgan vaqt: ").append(leaving).append("\n")
                    .append("----------------------\n");
        }

        return report.toString();
    }

    public byte[] buildMonthExcelReport() {
        LocalDate now = LocalDate.now();
        LocalDate startOfMonth = now.withDayOfMonth(1);
        LocalDate endOfMonth = now.withDayOfMonth(now.lengthOfMonth());

        List<Attendance> attendanceList = attendanceRepository.findAllByWorkDateBetween(startOfMonth, endOfMonth);

        if (attendanceList.isEmpty()) {
            return null;
        }

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
            dailyHeader.createCell(7).setCellValue(BotMessages.COLUMN_LATE_MINUTES);
            dailyHeader.createCell(8).setCellValue(BotMessages.COLUMN_ATTENDANCE_STATUS);

            Map<String, EmployeeMonthlySummary> summaryMap = new HashMap<>();

            int dailyRowNum = 1;

            for (Attendance attendance : attendanceList) {
                Row row = dailySheet.createRow(dailyRowNum++);

                String fullName = attendance.getEmployee().getFullName();
                String department = attendance.getEmployee().getDepartment();

                String arrival = attendance.getArrivalTime() == null
                        ? "Belgilanmagan"
                        : attendance.getArrivalTime().toLocalTime().withNano(0).toString();

                String leaving = attendance.getLeaveTime() == null
                        ? "Belgilanmagan"
                        : attendance.getLeaveTime().toLocalTime().withNano(0).toString();

                String workedHoursText = attendanceService.calculateWorkedHours(attendance);
                long lateMinutes = attendanceService.calculateLateMinutes(attendance);
                String attendanceStatus = attendanceService.calculateLateStatus(attendance);

                row.createCell(0).setCellValue(attendance.getWorkDate().toString());
                row.createCell(1).setCellValue(fullName);
                row.createCell(2).setCellValue(department);
                row.createCell(3).setCellValue(attendanceService.getWorkStartTime().toString());
                row.createCell(4).setCellValue(arrival);
                row.createCell(5).setCellValue(leaving);
                row.createCell(6).setCellValue(workedHoursText);
                row.createCell(7).setCellValue(lateMinutes);
                row.createCell(8).setCellValue(attendanceStatus);

                String key = fullName + "||" + department;
                EmployeeMonthlySummary summary = summaryMap.getOrDefault(
                        key,
                        new EmployeeMonthlySummary(fullName, department)
                );

                if (attendance.getArrivalTime() != null) {
                    summary.incrementWorkedDays();
                }

                summary.addMinutes(attendanceService.calculateWorkedMinutes(attendance));
                summary.addLateMinutes(lateMinutes);

                if (lateMinutes > 0) {
                    summary.incrementLateCount();
                }

                summaryMap.put(key, summary);
            }

            for (int i = 0; i < 9; i++) {
                dailySheet.autoSizeColumn(i);
            }

            Row summaryHeader = summarySheet.createRow(0);
            summaryHeader.createCell(0).setCellValue(BotMessages.COLUMN_FULL_NAME);
            summaryHeader.createCell(1).setCellValue(BotMessages.COLUMN_DEPARTMENT);
            summaryHeader.createCell(2).setCellValue(BotMessages.COLUMN_WORKED_DAYS);
            summaryHeader.createCell(3).setCellValue(BotMessages.COLUMN_TOTAL_HOURS);
            summaryHeader.createCell(4).setCellValue(BotMessages.COLUMN_LATE_COUNT);
            summaryHeader.createCell(5).setCellValue(BotMessages.COLUMN_TOTAL_LATE_MINUTES);

            int summaryRowNum = 1;

            for (EmployeeMonthlySummary summary : summaryMap.values()) {
                Row row = summarySheet.createRow(summaryRowNum++);

                row.createCell(0).setCellValue(summary.getFullName());
                row.createCell(1).setCellValue(summary.getDepartment());
                row.createCell(2).setCellValue(summary.getWorkedDays());
                row.createCell(3).setCellValue(attendanceService.formatMinutesAsHours(summary.getTotalWorkedMinutes()));
                row.createCell(4).setCellValue(summary.getLateCount());
                row.createCell(5).setCellValue(summary.getTotalLateMinutes());
            }

            for (int i = 0; i < 6; i++) {
                summarySheet.autoSizeColumn(i);
            }

            workbook.write(outputStream);
            return outputStream.toByteArray();

        } catch (Exception e) {
            return null;
        }
    }

    private static class EmployeeMonthlySummary {
        private final String fullName;
        private final String department;
        private int workedDays;
        private long totalWorkedMinutes;
        private int lateCount;
        private long totalLateMinutes;

        public EmployeeMonthlySummary(String fullName, String department) {
            this.fullName = fullName;
            this.department = department;
        }

        public void incrementWorkedDays() {
            this.workedDays++;
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

        public int getWorkedDays() {
            return workedDays;
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
