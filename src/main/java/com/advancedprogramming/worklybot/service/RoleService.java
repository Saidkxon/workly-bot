package com.advancedprogramming.worklybot.service;

import com.advancedprogramming.worklybot.entity.Employee;
import com.advancedprogramming.worklybot.entity.enums.AuditActionType;
import com.advancedprogramming.worklybot.entity.enums.Role;
import com.advancedprogramming.worklybot.repository.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RoleService {

    private final EmployeeRepository employeeRepository;
    private final AuditLogService auditLogService;

    @Transactional
    public RoleChangeResult makeManager(Long actorTelegramUserId, Long targetTelegramUserId) {
        Employee actor = employeeRepository.findByTelegramUserId(actorTelegramUserId).orElse(null);
        Employee target = employeeRepository.findByTelegramUserId(targetTelegramUserId).orElse(null);

        if (actor == null || target == null) {
            return unchanged("Foydalanuvchi topilmadi.", targetTelegramUserId);
        }

        if (actor.getRole() != Role.ADMIN) {
            return unchanged("Faqat ADMIN MANAGER rolini bera oladi.", targetTelegramUserId);
        }

        if (target.getRole() == Role.ADMIN) {
            return unchanged("Bu buyruq bilan ADMIN rolini o'zgartirib bo'lmaydi.", targetTelegramUserId);
        }

        if (target.getRole() == Role.MANAGER) {
            return unchanged(target.getFullName() + " allaqachon MANAGER.", targetTelegramUserId);
        }

        target.setRole(Role.MANAGER);
        employeeRepository.saveAndFlush(target);
        auditLogService.logAction(AuditActionType.ROLE_CHANGED, actor, target, "Yangi rol: MANAGER");

        return changed(target.getFullName() + " endi MANAGER.", targetTelegramUserId);
    }

    @Transactional
    public RoleChangeResult makeEmployee(Long actorTelegramUserId, Long targetTelegramUserId) {
        Employee actor = employeeRepository.findByTelegramUserId(actorTelegramUserId).orElse(null);
        Employee target = employeeRepository.findByTelegramUserId(targetTelegramUserId).orElse(null);

        if (actor == null || target == null) {
            return unchanged("Foydalanuvchi topilmadi.", targetTelegramUserId);
        }

        if (actor.getRole() != Role.ADMIN) {
            return unchanged("Faqat ADMIN EMPLOYEE rolini bera oladi.", targetTelegramUserId);
        }

        if (target.getRole() == Role.ADMIN) {
            return unchanged("Bu buyruq bilan ADMIN rolini pasaytirib bo'lmaydi.", targetTelegramUserId);
        }

        if (target.getRole() == Role.EMPLOYEE) {
            return unchanged(target.getFullName() + " allaqachon EMPLOYEE.", targetTelegramUserId);
        }

        target.setRole(Role.EMPLOYEE);
        employeeRepository.saveAndFlush(target);
        auditLogService.logAction(AuditActionType.ROLE_CHANGED, actor, target, "Yangi rol: EMPLOYEE");

        return changed(target.getFullName() + " endi EMPLOYEE.", targetTelegramUserId);
    }

    @Transactional
    public RoleChangeResult makeAdmin(Long actorTelegramUserId, Long targetTelegramUserId) {
        Employee actor = employeeRepository.findByTelegramUserId(actorTelegramUserId).orElse(null);
        Employee target = employeeRepository.findByTelegramUserId(targetTelegramUserId).orElse(null);

        if (actor == null || target == null) {
            return unchanged("Foydalanuvchi topilmadi.", targetTelegramUserId);
        }

        if (actor.getRole() != Role.ADMIN) {
            return unchanged("Faqat ADMIN ADMIN rolini bera oladi.", targetTelegramUserId);
        }

        if (target.getRole() == Role.ADMIN) {
            return unchanged(target.getFullName() + " allaqachon ADMIN.", targetTelegramUserId);
        }

        target.setRole(Role.ADMIN);
        employeeRepository.saveAndFlush(target);
        auditLogService.logAction(AuditActionType.ROLE_CHANGED, actor, target, "Yangi rol: ADMIN");

        return changed(target.getFullName() + " endi ADMIN.", targetTelegramUserId);
    }

    private RoleChangeResult changed(String message, Long targetTelegramUserId) {
        return new RoleChangeResult(message, targetTelegramUserId, true);
    }

    private RoleChangeResult unchanged(String message, Long targetTelegramUserId) {
        return new RoleChangeResult(message, targetTelegramUserId, false);
    }
}
