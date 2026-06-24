package com.advancedprogramming.worklybot.service;

import com.advancedprogramming.worklybot.entity.Employee;
import com.advancedprogramming.worklybot.entity.PendingRegistration;
import com.advancedprogramming.worklybot.entity.enums.Role;
import com.advancedprogramming.worklybot.repository.EmployeeRepository;
import com.advancedprogramming.worklybot.repository.PendingRegistrationRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PendingRegistrationServiceTests {

    @Test
    void approvingPendingRegistrationCreatesActiveEmployeeAndRemovesPendingRequest() {
        PendingRegistrationRepository pendingRegistrationRepository = mock(PendingRegistrationRepository.class);
        EmployeeRepository employeeRepository = mock(EmployeeRepository.class);
        AuditLogService auditLogService = mock(AuditLogService.class);

        Employee actor = Employee.builder()
                .telegramUserId(1L)
                .chatId(10L)
                .fullName("Manager User")
                .department("HR")
                .role(Role.MANAGER)
                .active(true)
                .build();

        PendingRegistration pendingRegistration = PendingRegistration.builder()
                .telegramUserId(77L)
                .chatId(177L)
                .fullName("New Employee")
                .department("QA")
                .build();

        when(employeeRepository.findByTelegramUserId(1L)).thenReturn(Optional.of(actor));
        when(pendingRegistrationRepository.findByTelegramUserId(77L)).thenReturn(Optional.of(pendingRegistration));

        PendingRegistrationService service = new PendingRegistrationService(
                pendingRegistrationRepository,
                employeeRepository,
                auditLogService,
                Clock.fixed(Instant.parse("2026-04-25T09:00:00Z"), ZoneId.of("Asia/Tashkent"))
        );

        PendingRegistrationActionResult result = service.approvePendingRegistration(1L, 77L);

        ArgumentCaptor<Employee> employeeCaptor = ArgumentCaptor.forClass(Employee.class);
        verify(employeeRepository).save(employeeCaptor.capture());
        verify(pendingRegistrationRepository).delete(pendingRegistration);

        Employee savedEmployee = employeeCaptor.getValue();
        assertEquals(77L, savedEmployee.getTelegramUserId());
        assertEquals(177L, savedEmployee.getChatId());
        assertEquals(Role.EMPLOYEE, savedEmployee.getRole());
        assertEquals("Xodim faollashtirildi: New Employee", result.getManagerMessage());
        assertEquals(177L, result.getChatId());
        assertNotNull(result.getEmployeeMessage());
    }

    @Test
    void rejectingPendingRegistrationDeletesRequestAndReturnsNotificationPayload() {
        PendingRegistrationRepository pendingRegistrationRepository = mock(PendingRegistrationRepository.class);
        EmployeeRepository employeeRepository = mock(EmployeeRepository.class);
        AuditLogService auditLogService = mock(AuditLogService.class);

        Employee actor = Employee.builder()
                .telegramUserId(2L)
                .chatId(20L)
                .fullName("Admin User")
                .department("Admin")
                .role(Role.ADMIN)
                .active(true)
                .build();

        PendingRegistration pendingRegistration = PendingRegistration.builder()
                .telegramUserId(88L)
                .chatId(188L)
                .fullName("Rejected User")
                .department("Sales")
                .build();

        when(employeeRepository.findByTelegramUserId(2L)).thenReturn(Optional.of(actor));
        when(pendingRegistrationRepository.findByTelegramUserId(88L)).thenReturn(Optional.of(pendingRegistration));

        PendingRegistrationService service = new PendingRegistrationService(
                pendingRegistrationRepository,
                employeeRepository,
                auditLogService,
                Clock.fixed(Instant.parse("2026-04-25T09:00:00Z"), ZoneId.of("Asia/Tashkent"))
        );

        PendingRegistrationActionResult result = service.rejectPendingRegistration(2L, 88L);

        verify(pendingRegistrationRepository).delete(pendingRegistration);
        verify(auditLogService).logAction(any(), any(), any(), any());
        assertEquals("Ro'yxatdan o'tish rad etildi: Rejected User", result.getManagerMessage());
        assertEquals(188L, result.getChatId());
        assertNotNull(result.getEmployeeMessage());
    }
}
