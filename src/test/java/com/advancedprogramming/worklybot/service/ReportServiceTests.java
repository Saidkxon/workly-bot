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
import java.time.ZoneId;
import java.util.List;

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
}
