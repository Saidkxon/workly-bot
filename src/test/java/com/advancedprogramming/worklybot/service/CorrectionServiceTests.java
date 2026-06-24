package com.advancedprogramming.worklybot.service;

import com.advancedprogramming.worklybot.bot.state.UserSession;
import com.advancedprogramming.worklybot.entity.Attendance;
import com.advancedprogramming.worklybot.entity.Employee;
import com.advancedprogramming.worklybot.entity.enums.Role;
import com.advancedprogramming.worklybot.repository.AttendanceRepository;
import com.advancedprogramming.worklybot.repository.CorrectionRequestRepository;
import com.advancedprogramming.worklybot.repository.EmployeeRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CorrectionServiceTests {

    @Test
    void rejectsFutureCorrectionDates() {
        CorrectionService correctionService = new CorrectionService(
                mock(CorrectionRequestRepository.class),
                mock(AttendanceRepository.class),
                mock(EmployeeRepository.class),
                mock(AuditLogService.class),
                Clock.fixed(Instant.parse("2026-04-24T08:00:00Z"), ZoneId.of("Asia/Tashkent"))
        );

        UserSession session = new UserSession();
        session.setCorrectionDate(LocalDate.of(2026, 4, 25));
        session.setCorrectionType("1");

        String result = correctionService.createCorrectionRequest(buildEmployee(), session, "09:30");

        assertEquals("Kelajak sana uchun tuzatish so'rovi yuborib bo'lmaydi.", result);
    }

    @Test
    void rejectsCorrectionThatWouldInvertArrivalAndLeavingTimes() {
        CorrectionRequestRepository correctionRequestRepository = mock(CorrectionRequestRepository.class);
        AttendanceRepository attendanceRepository = mock(AttendanceRepository.class);

        when(correctionRequestRepository.existsByEmployeeAndWorkDateAndStatus(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        )).thenReturn(false);

        when(attendanceRepository.findByEmployeeAndWorkDate(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        )).thenReturn(Optional.of(
                Attendance.builder()
                        .workDate(LocalDate.of(2026, 4, 20))
                        .arrivalTime(LocalDateTime.of(2026, 4, 20, 9, 0))
                        .leaveTime(LocalDateTime.of(2026, 4, 20, 18, 0))
                        .build()
        ));

        CorrectionService correctionService = new CorrectionService(
                correctionRequestRepository,
                attendanceRepository,
                mock(EmployeeRepository.class),
                mock(AuditLogService.class),
                Clock.fixed(Instant.parse("2026-04-24T08:00:00Z"), ZoneId.of("Asia/Tashkent"))
        );

        UserSession session = new UserSession();
        session.setCorrectionDate(LocalDate.of(2026, 4, 20));
        session.setCorrectionType("1");

        String result = correctionService.createCorrectionRequest(buildEmployee(), session, "19:30");

        assertEquals("Natijaviy kelish va ketish vaqtlari noto'g'ri tartibda bo'ladi.", result);
    }

    private Employee buildEmployee() {
        return Employee.builder()
                .id(1L)
                .telegramUserId(99L)
                .chatId(199L)
                .fullName("Test User")
                .department("QA")
                .role(Role.EMPLOYEE)
                .active(true)
                .build();
    }
}
