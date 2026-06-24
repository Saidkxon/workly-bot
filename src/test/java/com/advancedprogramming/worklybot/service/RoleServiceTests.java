package com.advancedprogramming.worklybot.service;

import com.advancedprogramming.worklybot.entity.Employee;
import com.advancedprogramming.worklybot.entity.enums.AuditActionType;
import com.advancedprogramming.worklybot.entity.enums.Role;
import com.advancedprogramming.worklybot.repository.EmployeeRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RoleServiceTests {

    @Test
    void makeManagerReturnsAlreadyManagerWhenRoleIsUnchanged() {
        EmployeeRepository employeeRepository = mock(EmployeeRepository.class);
        AuditLogService auditLogService = mock(AuditLogService.class);
        Employee actor = buildEmployee(1L, "Admin User", Role.ADMIN);
        Employee target = buildEmployee(2L, "Manager User", Role.MANAGER);

        when(employeeRepository.findByTelegramUserId(1L)).thenReturn(Optional.of(actor));
        when(employeeRepository.findByTelegramUserId(2L)).thenReturn(Optional.of(target));

        RoleService roleService = new RoleService(employeeRepository, auditLogService);

        RoleChangeResult result = roleService.makeManager(1L, 2L);

        assertEquals("Manager User allaqachon MANAGER.", result.message());
        assertFalse(result.changed());
        verify(employeeRepository, never()).save(target);
        verify(employeeRepository, never()).saveAndFlush(target);
        verify(auditLogService, never()).logAction(AuditActionType.ROLE_CHANGED, actor, target, "Yangi rol: MANAGER");
    }

    @Test
    void makeManagerPersistsRoleChangeAndReportsChanged() {
        EmployeeRepository employeeRepository = mock(EmployeeRepository.class);
        AuditLogService auditLogService = mock(AuditLogService.class);
        Employee actor = buildEmployee(1L, "Admin User", Role.ADMIN);
        Employee target = buildEmployee(2L, "Mubinaxon", Role.EMPLOYEE);

        when(employeeRepository.findByTelegramUserId(1L)).thenReturn(Optional.of(actor));
        when(employeeRepository.findByTelegramUserId(2L)).thenReturn(Optional.of(target));

        RoleService roleService = new RoleService(employeeRepository, auditLogService);

        RoleChangeResult result = roleService.makeManager(1L, 2L);

        assertEquals("Mubinaxon endi MANAGER.", result.message());
        assertEquals(2L, result.targetTelegramUserId());
        assertTrue(result.changed());
        assertEquals(Role.MANAGER, target.getRole());
        verify(employeeRepository).saveAndFlush(target);
        verify(auditLogService).logAction(AuditActionType.ROLE_CHANGED, actor, target, "Yangi rol: MANAGER");
    }

    @Test
    void makeEmployeeReturnsAlreadyEmployeeWhenRoleIsUnchanged() {
        EmployeeRepository employeeRepository = mock(EmployeeRepository.class);
        AuditLogService auditLogService = mock(AuditLogService.class);
        Employee actor = buildEmployee(1L, "Admin User", Role.ADMIN);
        Employee target = buildEmployee(2L, "Employee User", Role.EMPLOYEE);

        when(employeeRepository.findByTelegramUserId(1L)).thenReturn(Optional.of(actor));
        when(employeeRepository.findByTelegramUserId(2L)).thenReturn(Optional.of(target));

        RoleService roleService = new RoleService(employeeRepository, auditLogService);

        RoleChangeResult result = roleService.makeEmployee(1L, 2L);

        assertEquals("Employee User allaqachon EMPLOYEE.", result.message());
        assertFalse(result.changed());
        verify(employeeRepository, never()).save(target);
        verify(employeeRepository, never()).saveAndFlush(target);
        verify(auditLogService, never()).logAction(AuditActionType.ROLE_CHANGED, actor, target, "Yangi rol: EMPLOYEE");
    }

    @Test
    void makeAdminReturnsAlreadyAdminWhenRoleIsUnchanged() {
        EmployeeRepository employeeRepository = mock(EmployeeRepository.class);
        AuditLogService auditLogService = mock(AuditLogService.class);
        Employee actor = buildEmployee(1L, "Admin User", Role.ADMIN);
        Employee target = buildEmployee(2L, "Other Admin", Role.ADMIN);

        when(employeeRepository.findByTelegramUserId(1L)).thenReturn(Optional.of(actor));
        when(employeeRepository.findByTelegramUserId(2L)).thenReturn(Optional.of(target));

        RoleService roleService = new RoleService(employeeRepository, auditLogService);

        RoleChangeResult result = roleService.makeAdmin(1L, 2L);

        assertEquals("Other Admin allaqachon ADMIN.", result.message());
        assertFalse(result.changed());
        verify(employeeRepository, never()).save(target);
        verify(employeeRepository, never()).saveAndFlush(target);
        verify(auditLogService, never()).logAction(AuditActionType.ROLE_CHANGED, actor, target, "Yangi rol: ADMIN");
    }

    private Employee buildEmployee(Long telegramUserId, String fullName, Role role) {
        return Employee.builder()
                .telegramUserId(telegramUserId)
                .chatId(telegramUserId + 1000)
                .fullName(fullName)
                .department("Test")
                .role(role)
                .active(true)
                .build();
    }
}
