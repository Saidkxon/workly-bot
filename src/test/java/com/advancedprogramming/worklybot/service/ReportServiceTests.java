package com.advancedprogramming.worklybot.service;

import com.advancedprogramming.worklybot.bot.BotMessages;
import com.advancedprogramming.worklybot.entity.Attendance;
import com.advancedprogramming.worklybot.entity.Employee;
import com.advancedprogramming.worklybot.entity.enums.Role;
import com.advancedprogramming.worklybot.repository.AttendanceRepository;
import com.advancedprogramming.worklybot.repository.EmployeeRepository;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReportServiceTests {

    @Test
    void todayReportIncludesAbsentEmployeesInSummary() {
        EmployeeRepository employeeRepository = mock(EmployeeRepository.class);
        AttendanceRepository attendanceRepository = mock(AttendanceRepository.class);
        AttendanceService attendanceService = mock(AttendanceService.class);

        Employee presentEmployee = Employee.builder()
                .id(1L)
                .telegramUserId(101L)
                .chatId(201L)
                .fullName("Ali Valiyev")
                .department("IT")
                .role(Role.EMPLOYEE)
                .active(true)
                .build();

        Employee absentEmployee = Employee.builder()
                .id(2L)
                .telegramUserId(102L)
                .chatId(202L)
                .fullName("Vali Aliyev")
                .department("HR")
                .role(Role.EMPLOYEE)
                .active(true)
                .build();

        Attendance attendance = Attendance.builder()
                .employee(presentEmployee)
                .workDate(LocalDate.of(2026, 4, 24))
                .arrivalTime(LocalDateTime.of(2026, 4, 24, 9, 0))
                .leaveTime(LocalDateTime.of(2026, 4, 24, 18, 0))
                .build();

        when(employeeRepository.findAllByActiveTrueOrderByFullNameAsc()).thenReturn(List.of(presentEmployee, absentEmployee));
        when(attendanceRepository.findAllByWorkDate(LocalDate.of(2026, 4, 24))).thenReturn(List.of(attendance));
        when(attendanceService.calculateLateStatus(attendance)).thenReturn(BotMessages.STATUS_ON_TIME);
        when(attendanceService.calculateLateMinutes(attendance)).thenReturn(0L);
        when(attendanceService.formatMinutesAsHours(0L)).thenReturn("0 soat 0 daqiqa");

        ReportService reportService = new ReportService(
                attendanceRepository,
                employeeRepository,
                attendanceService,
                Clock.fixed(Instant.parse("2026-04-24T06:00:00Z"), ZoneId.of("Asia/Tashkent"))
        );

        String report = reportService.buildTodayReport();

        assertTrue(report.contains("Vali Aliyev"));
        assertTrue(report.contains("Holat: " + BotMessages.STATUS_ABSENT));
        assertTrue(report.contains("Kelmaganlar: 1"));
    }

    @Test
    void todayReportIncludesLateMinutesAfterWorkStart() {
        EmployeeRepository employeeRepository = mock(EmployeeRepository.class);
        AttendanceRepository attendanceRepository = mock(AttendanceRepository.class);
        AttendanceService attendanceService = mock(AttendanceService.class);

        Employee lateEmployee = Employee.builder()
                .id(1L)
                .telegramUserId(101L)
                .chatId(201L)
                .fullName("Ali Valiyev")
                .department("IT")
                .role(Role.EMPLOYEE)
                .active(true)
                .build();

        Attendance attendance = Attendance.builder()
                .employee(lateEmployee)
                .workDate(LocalDate.of(2026, 4, 24))
                .arrivalTime(LocalDateTime.of(2026, 4, 24, 9, 17))
                .build();

        when(employeeRepository.findAllByActiveTrueOrderByFullNameAsc()).thenReturn(List.of(lateEmployee));
        when(attendanceRepository.findAllByWorkDate(LocalDate.of(2026, 4, 24))).thenReturn(List.of(attendance));
        when(attendanceService.calculateLateStatus(attendance)).thenReturn(BotMessages.STATUS_LATE);
        when(attendanceService.calculateLateMinutes(attendance)).thenReturn(17L);
        when(attendanceService.formatMinutesAsHours(17L)).thenReturn("0 soat 17 daqiqa");

        ReportService reportService = new ReportService(
                attendanceRepository,
                employeeRepository,
                attendanceService,
                Clock.fixed(Instant.parse("2026-04-24T06:00:00Z"), ZoneId.of("Asia/Tashkent"))
        );

        String report = reportService.buildTodayReport();

        assertTrue(report.contains("Kechikish: 0 soat 17 daqiqa"));
        assertTrue(report.contains("Kechikkanlar: 1"));
        assertTrue(report.contains(BotMessages.CMD_LATE_EMPLOYEES_LIST));
        assertFalse(report.contains("Jami kechikish:"));
    }

    @Test
    void lateEmployeesListIncludesOnlyLateArrivalsForToday() {
        EmployeeRepository employeeRepository = mock(EmployeeRepository.class);
        AttendanceRepository attendanceRepository = mock(AttendanceRepository.class);
        AttendanceService attendanceService = mock(AttendanceService.class);

        Employee lateEmployee = Employee.builder()
                .id(1L)
                .telegramUserId(101L)
                .chatId(201L)
                .fullName("Ali Valiyev")
                .department("IT")
                .role(Role.EMPLOYEE)
                .active(true)
                .build();

        Employee onTimeEmployee = Employee.builder()
                .id(2L)
                .telegramUserId(102L)
                .chatId(202L)
                .fullName("Vali Aliyev")
                .department("HR")
                .role(Role.EMPLOYEE)
                .active(true)
                .build();

        Attendance lateAttendance = Attendance.builder()
                .employee(lateEmployee)
                .workDate(LocalDate.of(2026, 4, 24))
                .arrivalTime(LocalDateTime.of(2026, 4, 24, 9, 17))
                .build();

        Attendance onTimeAttendance = Attendance.builder()
                .employee(onTimeEmployee)
                .workDate(LocalDate.of(2026, 4, 24))
                .arrivalTime(LocalDateTime.of(2026, 4, 24, 8, 55))
                .build();

        when(employeeRepository.findAllByActiveTrueOrderByFullNameAsc()).thenReturn(List.of(lateEmployee, onTimeEmployee));
        when(attendanceRepository.findAllByWorkDate(LocalDate.of(2026, 4, 24)))
                .thenReturn(List.of(lateAttendance, onTimeAttendance));
        when(attendanceService.calculateLateMinutes(lateAttendance)).thenReturn(17L);
        when(attendanceService.calculateLateMinutes(onTimeAttendance)).thenReturn(0L);
        when(attendanceService.formatMinutesAsHours(17L)).thenReturn("0 soat 17 daqiqa");

        ReportService reportService = new ReportService(
                attendanceRepository,
                employeeRepository,
                attendanceService,
                Clock.fixed(Instant.parse("2026-04-24T06:00:00Z"), ZoneId.of("Asia/Tashkent"))
        );

        String report = reportService.buildTodayLateEmployeesList();

        assertTrue(report.contains("Ali Valiyev"));
        assertTrue(report.contains("Kechikish: 0 soat 17 daqiqa"));
        assertTrue(report.contains("Kechikkanlar: 1"));
        assertFalse(report.contains("Jami kechikish:"));
        assertFalse(report.contains("Vali Aliyev"));
    }

    @Test
    void monthExcelReportUsesReadableLateTimeColumns() throws Exception {
        EmployeeRepository employeeRepository = mock(EmployeeRepository.class);
        AttendanceRepository attendanceRepository = mock(AttendanceRepository.class);
        AttendanceService attendanceService = mock(AttendanceService.class);

        Employee employee = Employee.builder()
                .id(1L)
                .telegramUserId(101L)
                .chatId(201L)
                .fullName("Ali Valiyev")
                .department("IT")
                .role(Role.EMPLOYEE)
                .active(true)
                .build();

        Attendance attendance = Attendance.builder()
                .employee(employee)
                .workDate(LocalDate.of(2026, 4, 24))
                .arrivalTime(LocalDateTime.of(2026, 4, 24, 10, 15))
                .leaveTime(LocalDateTime.of(2026, 4, 24, 18, 0))
                .build();

        when(employeeRepository.findAllByActiveTrueOrderByFullNameAsc()).thenReturn(List.of(employee));
        when(attendanceRepository.findAllByWorkDateBetween(
                LocalDate.of(2026, 4, 1),
                LocalDate.of(2026, 4, 30)
        )).thenReturn(List.of(attendance));
        when(attendanceService.getWorkStartTime()).thenReturn(LocalTime.of(9, 0));
        when(attendanceService.calculateWorkedHours(attendance)).thenReturn("7 soat 45 daqiqa");
        when(attendanceService.calculateWorkedMinutes(attendance)).thenReturn(465L);
        when(attendanceService.calculateLateMinutes(attendance)).thenReturn(75L);
        when(attendanceService.calculateLateStatus(attendance)).thenReturn(BotMessages.STATUS_LATE);
        when(attendanceService.formatMinutesAsHours(75L)).thenReturn("1 soat 15 daqiqa");
        when(attendanceService.formatMinutesAsHours(465L)).thenReturn("7 soat 45 daqiqa");

        ReportService reportService = new ReportService(
                attendanceRepository,
                employeeRepository,
                attendanceService,
                Clock.fixed(Instant.parse("2026-04-24T06:00:00Z"), ZoneId.of("Asia/Tashkent"))
        );

        byte[] fileBytes = reportService.buildMonthExcelReport();

        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(fileBytes))) {
            Sheet dailySheet = workbook.getSheet(BotMessages.DAILY_REPORT_SHEET);
            Sheet summarySheet = workbook.getSheet(BotMessages.SUMMARY_SHEET);

            assertEquals(BotMessages.COLUMN_LATE_TIME, dailySheet.getRow(0).getCell(7).getStringCellValue());
            assertEquals("1 soat 15 daqiqa", dailySheet.getRow(1).getCell(7).getStringCellValue());
            assertEquals(BotMessages.COLUMN_TOTAL_LATE_TIME, summarySheet.getRow(0).getCell(8).getStringCellValue());
            assertEquals("1 soat 15 daqiqa", summarySheet.getRow(1).getCell(8).getStringCellValue());
        }
    }

    @Test
    void monthlyHistoryShowsSelectedEmployeeArrivalAndLeaveTimes() {
        EmployeeRepository employeeRepository = mock(EmployeeRepository.class);
        AttendanceRepository attendanceRepository = mock(AttendanceRepository.class);
        AttendanceService attendanceService = mock(AttendanceService.class);

        Employee employee = Employee.builder()
                .id(1L)
                .telegramUserId(101L)
                .chatId(201L)
                .fullName("Ali Valiyev")
                .department("IT")
                .role(Role.EMPLOYEE)
                .active(true)
                .build();

        Attendance attendance = Attendance.builder()
                .employee(employee)
                .workDate(LocalDate.of(2026, 4, 24))
                .arrivalTime(LocalDateTime.of(2026, 4, 24, 9, 17))
                .leaveTime(LocalDateTime.of(2026, 4, 24, 18, 5))
                .build();

        when(attendanceRepository.findAllByEmployeeAndWorkDateBetweenOrderByWorkDateAsc(
                employee,
                LocalDate.of(2026, 4, 1),
                LocalDate.of(2026, 4, 30)
        )).thenReturn(List.of(attendance));
        when(attendanceService.calculateWorkedMinutes(attendance)).thenReturn(528L);
        when(attendanceService.calculateLateMinutes(attendance)).thenReturn(17L);
        when(attendanceService.calculateLateStatus(attendance)).thenReturn(BotMessages.STATUS_LATE);
        when(attendanceService.formatMinutesAsHours(528L)).thenReturn("8 soat 48 daqiqa");
        when(attendanceService.formatMinutesAsHours(17L)).thenReturn("0 soat 17 daqiqa");

        ReportService reportService = new ReportService(
                attendanceRepository,
                employeeRepository,
                attendanceService,
                Clock.fixed(Instant.parse("2026-04-24T06:00:00Z"), ZoneId.of("Asia/Tashkent"))
        );

        String report = reportService.buildMonthlyHistory(employee, YearMonth.of(2026, 4));

        assertTrue(report.contains("Xodim: Ali Valiyev"));
        assertTrue(report.contains("Oy: 2026-04"));
        assertTrue(report.contains("Sana: 2026-04-24"));
        assertTrue(report.contains("Kelgan vaqt: 09:17"));
        assertTrue(report.contains("Ketgan vaqt: 18:05"));
        assertTrue(report.contains("Jami ishlangan vaqt: 8 soat 48 daqiqa"));
    }

    @Test
    void employeeHistorySelectionIncludesHistoryCommands() {
        EmployeeRepository employeeRepository = mock(EmployeeRepository.class);
        AttendanceRepository attendanceRepository = mock(AttendanceRepository.class);
        AttendanceService attendanceService = mock(AttendanceService.class);

        Employee employee = Employee.builder()
                .id(1L)
                .telegramUserId(101L)
                .chatId(201L)
                .fullName("Ali Valiyev")
                .department("IT")
                .role(Role.EMPLOYEE)
                .active(true)
                .build();

        when(employeeRepository.findAllByActiveTrueOrderByFullNameAsc()).thenReturn(List.of(employee));

        ReportService reportService = new ReportService(
                attendanceRepository,
                employeeRepository,
                attendanceService,
                Clock.fixed(Instant.parse("2026-04-24T06:00:00Z"), ZoneId.of("Asia/Tashkent"))
        );

        String report = reportService.buildEmployeeHistorySelectionText();

        assertTrue(report.contains("Ali Valiyev"));
        assertTrue(report.contains("Tanlash: /history_101"));
    }
}
