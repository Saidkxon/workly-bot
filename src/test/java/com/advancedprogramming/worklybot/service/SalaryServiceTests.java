package com.advancedprogramming.worklybot.service;

import com.advancedprogramming.worklybot.config.PenaltyProperties;
import com.advancedprogramming.worklybot.entity.Attendance;
import com.advancedprogramming.worklybot.entity.Employee;
import com.advancedprogramming.worklybot.entity.enums.Role;
import com.advancedprogramming.worklybot.entity.enums.Shift;
import com.advancedprogramming.worklybot.repository.AttendanceRepository;
import com.advancedprogramming.worklybot.repository.DepartmentSalaryRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SalaryServiceTests {

    @Test
    void firstLateOfMonthIsWarningAndLaterLatesAreCharged() {
        DepartmentSalaryRepository departmentSalaryRepository = mock(DepartmentSalaryRepository.class);
        AttendanceRepository attendanceRepository = mock(AttendanceRepository.class);
        PenaltyProperties penalty = new PenaltyProperties(); // grace 10, 3000/min, Qabul 4,000,000
        Clock clock = Clock.fixed(Instant.parse("2026-04-15T06:00:00Z"), ZoneId.of("Asia/Tashkent"));

        SalaryService salaryService = new SalaryService(departmentSalaryRepository, attendanceRepository, penalty, clock);

        Employee employee = Employee.builder()
                .id(1L).telegramUserId(101L).chatId(201L)
                .fullName("Ali Valiyev").department("Qabul bo'limi")
                .shift(Shift.MORNING).role(Role.EMPLOYEE).active(true).build();

        YearMonth month = YearMonth.of(2026, 4);

        // Day 1: 08:55 -> 15 late min (first late -> warning, no deduction)
        // Day 2: 08:41 -> 1 late min  -> charged 3,000
        // Day 3: 08:35 -> on time (within grace)
        List<Attendance> attendances = List.of(
                day(employee, 1, 8, 55),
                day(employee, 2, 8, 41),
                day(employee, 3, 8, 35)
        );

        when(departmentSalaryRepository.findById(any())).thenReturn(Optional.empty());
        when(attendanceRepository.findAllByEmployeeAndWorkDateBetweenOrderByWorkDateAsc(
                eq(employee), eq(month.atDay(1)), eq(month.atEndOfMonth()))).thenReturn(attendances);

        MonthlySalaryBreakdown breakdown = salaryService.computeBreakdown(employee, month);

        assertEquals(4_000_000L, breakdown.baseSalary());
        assertEquals(2, breakdown.lateDays());
        assertEquals(1, breakdown.penalizedDays());
        assertEquals(16, breakdown.totalLateMinutes());
        assertEquals(3_000L, breakdown.totalDeduction());
        assertEquals(3_997_000L, breakdown.netSalary());
    }

    private Attendance day(Employee employee, int dayOfMonth, int hour, int minute) {
        return Attendance.builder()
                .employee(employee)
                .workDate(LocalDate.of(2026, 4, dayOfMonth))
                .arrivalTime(LocalDateTime.of(2026, 4, dayOfMonth, hour, minute))
                .leaveTime(LocalDateTime.of(2026, 4, dayOfMonth, 18, 0))
                .build();
    }
}
