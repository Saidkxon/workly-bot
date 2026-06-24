package com.advancedprogramming.worklybot.service;

import com.advancedprogramming.worklybot.bot.BotMessages;
import com.advancedprogramming.worklybot.entity.Attendance;
import com.advancedprogramming.worklybot.entity.Employee;
import com.advancedprogramming.worklybot.entity.enums.Role;
import com.advancedprogramming.worklybot.repository.AttendanceRepository;
import com.advancedprogramming.worklybot.repository.EmployeeRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReportServiceTests {

    private final Clock clock = Clock.fixed(Instant.parse("2026-04-24T06:00:00Z"), ZoneId.of("Asia/Tashkent"));

    private ReportService newReportService(AttendanceRepository attendanceRepository,
                                           EmployeeRepository employeeRepository,
                                           AttendanceService attendanceService) {
        return new ReportService(attendanceRepository, employeeRepository, attendanceService,
                mock(SalaryService.class), clock);
    }

    private Employee employee(long id, String name, String dept) {
        return Employee.builder().id(id).telegramUserId(100 + id).chatId(200 + id)
                .fullName(name).department(dept).role(Role.EMPLOYEE).active(true).build();
    }

    @Test
    void todayReportIncludesAbsentEmployeesInSummary() {
        EmployeeRepository employeeRepository = mock(EmployeeRepository.class);
        AttendanceRepository attendanceRepository = mock(AttendanceRepository.class);
        AttendanceService attendanceService = mock(AttendanceService.class);

        Employee present = employee(1L, "Ali Valiyev", "Platforma");
        Employee absent = employee(2L, "Vali Aliyev", "Call Center");

        Attendance attendance = Attendance.builder().employee(present)
                .workDate(LocalDate.of(2026, 4, 24))
                .arrivalTime(LocalDateTime.of(2026, 4, 24, 8, 30))
                .leaveTime(LocalDateTime.of(2026, 4, 24, 18, 0)).build();

        when(employeeRepository.findAllByActiveTrueOrderByFullNameAsc()).thenReturn(List.of(present, absent));
        when(attendanceRepository.findAllByWorkDate(LocalDate.of(2026, 4, 24))).thenReturn(List.of(attendance));
        when(attendanceService.calculateLateStatus(attendance)).thenReturn(BotMessages.STATUS_ON_TIME);
        when(attendanceService.calculateLateMinutes(attendance)).thenReturn(0L);
        when(attendanceService.formatMinutesAsHours(0L)).thenReturn("0 soat 0 daqiqa");

        String report = newReportService(attendanceRepository, employeeRepository, attendanceService).buildTodayReport();

        assertTrue(report.contains("Vali Aliyev"));
        assertTrue(report.contains("Holat: " + BotMessages.STATUS_ABSENT));
        assertTrue(report.contains("Kelmaganlar: 1"));
    }

    @Test
    void todayReportCountsLateAndMissingCheckout() {
        EmployeeRepository employeeRepository = mock(EmployeeRepository.class);
        AttendanceRepository attendanceRepository = mock(AttendanceRepository.class);
        AttendanceService attendanceService = mock(AttendanceService.class);

        Employee late = employee(1L, "Ali Valiyev", "Platforma");
        Attendance attendance = Attendance.builder().employee(late)
                .workDate(LocalDate.of(2026, 4, 24))
                .arrivalTime(LocalDateTime.of(2026, 4, 24, 9, 17)).build();

        when(employeeRepository.findAllByActiveTrueOrderByFullNameAsc()).thenReturn(List.of(late));
        when(attendanceRepository.findAllByWorkDate(LocalDate.of(2026, 4, 24))).thenReturn(List.of(attendance));
        when(attendanceService.calculateLateStatus(attendance)).thenReturn(BotMessages.STATUS_LATE);
        when(attendanceService.calculateLateMinutes(attendance)).thenReturn(37L);
        when(attendanceService.formatMinutesAsHours(37L)).thenReturn("0 soat 37 daqiqa");

        String report = newReportService(attendanceRepository, employeeRepository, attendanceService).buildTodayReport();

        assertTrue(report.contains("Kechikish: 0 soat 37 daqiqa"));
        assertTrue(report.contains("Kechikkanlar: 1"));
        assertTrue(report.contains("Ketishni belgilamaganlar: 1"));
    }

    @Test
    void lateEmployeesListIncludesOnlyLateArrivalsForToday() {
        EmployeeRepository employeeRepository = mock(EmployeeRepository.class);
        AttendanceRepository attendanceRepository = mock(AttendanceRepository.class);
        AttendanceService attendanceService = mock(AttendanceService.class);

        Employee late = employee(1L, "Ali Valiyev", "Platforma");
        Employee onTime = employee(2L, "Vali Aliyev", "Call Center");

        Attendance lateAttendance = Attendance.builder().employee(late)
                .workDate(LocalDate.of(2026, 4, 24))
                .arrivalTime(LocalDateTime.of(2026, 4, 24, 9, 17)).build();
        Attendance onTimeAttendance = Attendance.builder().employee(onTime)
                .workDate(LocalDate.of(2026, 4, 24))
                .arrivalTime(LocalDateTime.of(2026, 4, 24, 8, 35)).build();

        when(employeeRepository.findAllByActiveTrueOrderByFullNameAsc()).thenReturn(List.of(late, onTime));
        when(attendanceRepository.findAllByWorkDate(LocalDate.of(2026, 4, 24)))
                .thenReturn(List.of(lateAttendance, onTimeAttendance));
        when(attendanceService.calculateLateMinutes(lateAttendance)).thenReturn(37L);
        when(attendanceService.calculateLateMinutes(onTimeAttendance)).thenReturn(0L);
        when(attendanceService.formatMinutesAsHours(37L)).thenReturn("0 soat 37 daqiqa");

        String report = newReportService(attendanceRepository, employeeRepository, attendanceService).buildTodayLateEmployeesList();

        assertTrue(report.contains("Ali Valiyev"));
        assertTrue(report.contains("Kechikkanlar: 1"));
        assertFalse(report.contains("Vali Aliyev"));
    }

    @Test
    void monthlyHistoryShowsSelectedEmployeeArrivalAndLeaveTimes() {
        EmployeeRepository employeeRepository = mock(EmployeeRepository.class);
        AttendanceRepository attendanceRepository = mock(AttendanceRepository.class);
        AttendanceService attendanceService = mock(AttendanceService.class);

        Employee employee = employee(1L, "Ali Valiyev", "Platforma");
        Attendance attendance = Attendance.builder().employee(employee)
                .workDate(LocalDate.of(2026, 4, 24))
                .arrivalTime(LocalDateTime.of(2026, 4, 24, 9, 17))
                .leaveTime(LocalDateTime.of(2026, 4, 24, 18, 5)).build();

        when(attendanceRepository.findAllByEmployeeAndWorkDateBetweenOrderByWorkDateAsc(
                employee, LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30))).thenReturn(List.of(attendance));
        when(attendanceService.calculateWorkedMinutes(attendance)).thenReturn(528L);
        when(attendanceService.calculateLateMinutes(attendance)).thenReturn(37L);
        when(attendanceService.calculateLateStatus(attendance)).thenReturn(BotMessages.STATUS_LATE);
        when(attendanceService.formatMinutesAsHours(528L)).thenReturn("8 soat 48 daqiqa");
        when(attendanceService.formatMinutesAsHours(37L)).thenReturn("0 soat 37 daqiqa");

        String report = newReportService(attendanceRepository, employeeRepository, attendanceService)
                .buildMonthlyHistory(employee, YearMonth.of(2026, 4));

        assertTrue(report.contains("Xodim: Ali Valiyev"));
        assertTrue(report.contains("Oy: 2026-04"));
        assertTrue(report.contains("Kelgan vaqt: 09:17"));
        assertTrue(report.contains("Ketgan vaqt: 18:05"));
    }

    @Test
    void employeeHistorySelectionIncludesHistoryCommands() {
        EmployeeRepository employeeRepository = mock(EmployeeRepository.class);
        AttendanceRepository attendanceRepository = mock(AttendanceRepository.class);
        AttendanceService attendanceService = mock(AttendanceService.class);

        Employee employee = employee(1L, "Ali Valiyev", "Platforma");
        when(employeeRepository.findAllByActiveTrueOrderByFullNameAsc()).thenReturn(List.of(employee));

        String report = newReportService(attendanceRepository, employeeRepository, attendanceService)
                .buildEmployeeHistorySelectionText();

        assertTrue(report.contains("Ali Valiyev"));
        assertTrue(report.contains("Tanlash: /history_101"));
    }
}
