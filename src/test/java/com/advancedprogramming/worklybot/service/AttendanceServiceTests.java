package com.advancedprogramming.worklybot.service;

import com.advancedprogramming.worklybot.bot.BotMessages;
import com.advancedprogramming.worklybot.config.OfficeProperties;
import com.advancedprogramming.worklybot.config.PenaltyProperties;
import com.advancedprogramming.worklybot.entity.Attendance;
import com.advancedprogramming.worklybot.entity.Employee;
import com.advancedprogramming.worklybot.entity.enums.Role;
import com.advancedprogramming.worklybot.entity.enums.Shift;
import com.advancedprogramming.worklybot.repository.AttendanceRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AttendanceServiceTests {

    private OfficeProperties office() {
        OfficeProperties officeProperties = new OfficeProperties();
        officeProperties.setLatitude(41.360470);
        officeProperties.setLongitude(69.226713);
        officeProperties.setAllowedRadiusMeters(50.0);
        officeProperties.setWorkStartTime("09:00");
        officeProperties.setWorkEndTime("18:00");
        return officeProperties;
    }

    private Employee morningEmployee() {
        return Employee.builder()
                .telegramUserId(99L)
                .chatId(199L)
                .fullName("Test User")
                .department("Platforma")
                .shift(Shift.MORNING)
                .role(Role.EMPLOYEE)
                .active(true)
                .build();
    }

    @Test
    void latenessUsesShiftStartPlusGraceAndFormatsWorkedHours() {
        Clock appClock = Clock.fixed(Instant.parse("2026-04-20T04:15:00Z"), ZoneId.of("Asia/Tashkent"));
        AttendanceService attendanceService = new AttendanceService(
                null, office(), mock(EarlyLeaveService.class), new PenaltyProperties(), appClock);

        // Morning shift starts 08:30, +10 min grace -> 08:40. Arrival 09:15 -> 35 late minutes.
        Attendance attendance = Attendance.builder()
                .employee(morningEmployee())
                .arrivalTime(LocalDateTime.of(2026, 4, 20, 9, 15))
                .leaveTime(LocalDateTime.of(2026, 4, 20, 18, 0))
                .build();

        assertEquals("8 soat 45 daqiqa", attendanceService.calculateWorkedHours(attendance));
        assertEquals(35, attendanceService.calculateLateMinutes(attendance));
        assertEquals(BotMessages.STATUS_LATE, attendanceService.calculateLateStatus(attendance));
    }

    @Test
    void arrivalWithinGraceIsNotLate() {
        AttendanceService attendanceService = new AttendanceService(
                null, office(), mock(EarlyLeaveService.class), new PenaltyProperties(),
                Clock.fixed(Instant.parse("2026-04-20T04:00:00Z"), ZoneId.of("Asia/Tashkent")));

        Attendance attendance = Attendance.builder()
                .employee(morningEmployee())
                .arrivalTime(LocalDateTime.of(2026, 4, 20, 8, 39))
                .leaveTime(LocalDateTime.of(2026, 4, 20, 18, 0))
                .build();

        assertEquals(0, attendanceService.calculateLateMinutes(attendance));
        assertEquals(BotMessages.STATUS_ON_TIME, attendanceService.calculateLateStatus(attendance));
    }

    @Test
    void workedMinutesNeverGoNegativeWhenTimesAreInvalid() {
        AttendanceService attendanceService = new AttendanceService(
                null, office(), mock(EarlyLeaveService.class), new PenaltyProperties(),
                Clock.fixed(Instant.parse("2026-04-20T04:15:00Z"), ZoneId.of("Asia/Tashkent")));

        Attendance invalidAttendance = Attendance.builder()
                .employee(morningEmployee())
                .arrivalTime(LocalDateTime.of(2026, 4, 20, 18, 0))
                .leaveTime(LocalDateTime.of(2026, 4, 20, 9, 0))
                .build();

        assertEquals(0, attendanceService.calculateWorkedMinutes(invalidAttendance));
    }

    @Test
    void leavingIsBlockedBeforeShiftEndWithoutApprovedEarlyLeave() {
        EarlyLeaveService earlyLeaveService = mock(EarlyLeaveService.class);
        when(earlyLeaveService.hasApprovedRequestForToday(org.mockito.ArgumentMatchers.any())).thenReturn(false);

        AttendanceService attendanceService = new AttendanceService(
                null, office(), earlyLeaveService, new PenaltyProperties(),
                Clock.fixed(Instant.parse("2026-04-20T12:00:00Z"), ZoneId.of("Asia/Tashkent")));

        Employee employee = morningEmployee();
        assertFalse(attendanceService.canMarkLeaving(employee));
        assertEquals(
                "18:00 dan oldin ketish mumkin emas. Erta ketish uchun sabab yuboring yoki menejer ruxsatini kuting.",
                attendanceService.getLeavingNotAllowedMessage(employee)
        );
    }

    @Test
    void leavingIsAllowedBeforeShiftEndWhenEarlyLeaveWasApproved() {
        EarlyLeaveService earlyLeaveService = mock(EarlyLeaveService.class);
        when(earlyLeaveService.hasApprovedRequestForToday(org.mockito.ArgumentMatchers.any())).thenReturn(true);

        AttendanceService attendanceService = new AttendanceService(
                null, office(), earlyLeaveService, new PenaltyProperties(),
                Clock.fixed(Instant.parse("2026-04-20T12:00:00Z"), ZoneId.of("Asia/Tashkent")));

        assertTrue(attendanceService.canMarkLeaving(morningEmployee()));
    }

    @Test
    void openAttendanceForTodayIsDetectedForApprovedEarlyLeaveFlow() {
        AttendanceRepository attendanceRepository = mock(AttendanceRepository.class);
        Employee employee = morningEmployee();

        when(attendanceRepository.findByEmployeeAndWorkDate(employee, java.time.LocalDate.of(2026, 4, 20)))
                .thenReturn(Optional.of(Attendance.builder()
                        .arrivalTime(LocalDateTime.of(2026, 4, 20, 9, 0))
                        .leaveTime(null)
                        .build()));

        AttendanceService attendanceService = new AttendanceService(
                attendanceRepository, office(), mock(EarlyLeaveService.class), new PenaltyProperties(),
                Clock.fixed(Instant.parse("2026-04-20T12:00:00Z"), ZoneId.of("Asia/Tashkent")));

        assertTrue(attendanceService.hasOpenAttendanceForToday(employee));
    }
}
