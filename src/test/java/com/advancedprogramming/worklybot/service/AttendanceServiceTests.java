package com.advancedprogramming.worklybot.service;

import com.advancedprogramming.worklybot.bot.BotMessages;
import com.advancedprogramming.worklybot.config.OfficeProperties;
import com.advancedprogramming.worklybot.entity.Attendance;
import com.advancedprogramming.worklybot.entity.Employee;
import com.advancedprogramming.worklybot.entity.enums.Role;
import com.advancedprogramming.worklybot.repository.AttendanceRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AttendanceServiceTests {

    @Test
    void usesConfiguredOfficeTimesAndFormatsWorkedHours() {
        OfficeProperties officeProperties = new OfficeProperties();
        officeProperties.setLatitude(41.360470);
        officeProperties.setLongitude(69.226713);
        officeProperties.setAllowedRadiusMeters(50.0);
        officeProperties.setWorkStartTime("09:00");
        officeProperties.setWorkEndTime("18:00");

        Clock appClock = Clock.fixed(Instant.parse("2026-04-20T04:15:00Z"), ZoneId.of("Asia/Tashkent"));
        AttendanceService attendanceService = new AttendanceService(null, officeProperties, mock(EarlyLeaveService.class), appClock);
        Attendance attendance = Attendance.builder()
                .arrivalTime(LocalDateTime.of(2026, 4, 20, 9, 15))
                .leaveTime(LocalDateTime.of(2026, 4, 20, 18, 0))
                .build();

        assertEquals(LocalTime.of(9, 0), attendanceService.getWorkStartTime());
        assertEquals(LocalTime.of(18, 0), attendanceService.getWorkEndTime());
        assertEquals("8 soat 45 daqiqa", attendanceService.calculateWorkedHours(attendance));
        assertEquals(15, attendanceService.calculateLateMinutes(attendance));
        assertEquals(BotMessages.STATUS_LATE, attendanceService.calculateLateStatus(attendance));
    }

    @Test
    void workedMinutesNeverGoNegativeWhenTimesAreInvalid() {
        OfficeProperties officeProperties = new OfficeProperties();
        officeProperties.setLatitude(41.360470);
        officeProperties.setLongitude(69.226713);
        officeProperties.setAllowedRadiusMeters(50.0);
        officeProperties.setWorkStartTime("09:00");
        officeProperties.setWorkEndTime("18:00");

        AttendanceService attendanceService = new AttendanceService(
                null,
                officeProperties,
                mock(EarlyLeaveService.class),
                Clock.fixed(Instant.parse("2026-04-20T04:15:00Z"), ZoneId.of("Asia/Tashkent"))
        );

        Attendance invalidAttendance = Attendance.builder()
                .arrivalTime(LocalDateTime.of(2026, 4, 20, 18, 0))
                .leaveTime(LocalDateTime.of(2026, 4, 20, 9, 0))
                .build();

        assertEquals(0, attendanceService.calculateWorkedMinutes(invalidAttendance));
    }

    @Test
    void leavingIsBlockedBeforeConfiguredEndTimeWithoutApprovedEarlyLeave() {
        OfficeProperties officeProperties = new OfficeProperties();
        officeProperties.setLatitude(41.360470);
        officeProperties.setLongitude(69.226713);
        officeProperties.setAllowedRadiusMeters(50.0);
        officeProperties.setWorkStartTime("09:00");
        officeProperties.setWorkEndTime("18:00");

        EarlyLeaveService earlyLeaveService = mock(EarlyLeaveService.class);
        when(earlyLeaveService.hasApprovedRequestForToday(org.mockito.ArgumentMatchers.any())).thenReturn(false);

        AttendanceService attendanceService = new AttendanceService(
                null,
                officeProperties,
                earlyLeaveService,
                Clock.fixed(Instant.parse("2026-04-20T12:00:00Z"), ZoneId.of("Asia/Tashkent"))
        );

        assertFalse(attendanceService.canMarkLeaving(null));
        assertEquals(
                "18:00 dan oldin ketish mumkin emas yoki erta ketish va uning sababini yuboring.",
                attendanceService.getLeavingNotAllowedMessage()
        );
    }

    @Test
    void leavingIsAllowedBeforeEndTimeWhenEarlyLeaveRequestWasApproved() {
        OfficeProperties officeProperties = new OfficeProperties();
        officeProperties.setLatitude(41.360470);
        officeProperties.setLongitude(69.226713);
        officeProperties.setAllowedRadiusMeters(50.0);
        officeProperties.setWorkStartTime("09:00");
        officeProperties.setWorkEndTime("18:00");

        EarlyLeaveService earlyLeaveService = mock(EarlyLeaveService.class);
        when(earlyLeaveService.hasApprovedRequestForToday(org.mockito.ArgumentMatchers.any())).thenReturn(true);

        AttendanceService attendanceService = new AttendanceService(
                null,
                officeProperties,
                earlyLeaveService,
                Clock.fixed(Instant.parse("2026-04-20T12:00:00Z"), ZoneId.of("Asia/Tashkent"))
        );

        assertTrue(attendanceService.canMarkLeaving(null));
    }

    @Test
    void openAttendanceForTodayIsDetectedForApprovedEarlyLeaveFlow() {
        OfficeProperties officeProperties = new OfficeProperties();
        officeProperties.setLatitude(41.360470);
        officeProperties.setLongitude(69.226713);
        officeProperties.setAllowedRadiusMeters(50.0);
        officeProperties.setWorkStartTime("09:00");
        officeProperties.setWorkEndTime("18:00");

        AttendanceRepository attendanceRepository = mock(AttendanceRepository.class);
        Employee employee = Employee.builder()
                .telegramUserId(99L)
                .chatId(199L)
                .fullName("Test User")
                .department("QA")
                .role(Role.EMPLOYEE)
                .active(true)
                .build();

        when(attendanceRepository.findByEmployeeAndWorkDate(
                employee,
                java.time.LocalDate.of(2026, 4, 20)
        )).thenReturn(Optional.of(
                Attendance.builder()
                        .arrivalTime(LocalDateTime.of(2026, 4, 20, 9, 0))
                        .leaveTime(null)
                        .build()
        ));

        AttendanceService attendanceService = new AttendanceService(
                attendanceRepository,
                officeProperties,
                mock(EarlyLeaveService.class),
                Clock.fixed(Instant.parse("2026-04-20T12:00:00Z"), ZoneId.of("Asia/Tashkent"))
        );

        assertTrue(attendanceService.hasOpenAttendanceForToday(employee));
    }
}
