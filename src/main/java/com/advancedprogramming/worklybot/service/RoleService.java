package com.advancedprogramming.worklybot.service;

import com.advancedprogramming.worklybot.entity.Employee;
import com.advancedprogramming.worklybot.entity.enums.AuditActionType;
import com.advancedprogramming.worklybot.entity.enums.Role;
import com.advancedprogramming.worklybot.repository.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RoleService {

    private final EmployeeRepository employeeRepository;
    private final AuditLogService auditLogService;

    public String makeManager(Long actorTelegramUserId, Long targetTelegramUserId) {
        Employee actor = employeeRepository.findByTelegramUserId(actorTelegramUserId).orElse(null);
        Employee target = employeeRepository.findByTelegramUserId(targetTelegramUserId).orElse(null);

        if (actor == null || target == null) {
            return "Foydalanuvchi topilmadi.";
        }

        if (actor.getRole() != Role.ADMIN) {
            return "Faqat ADMIN MANAGER rolini bera oladi.";
        }

        if (target.getRole() == Role.ADMIN) {
            return "Bu buyruq bilan ADMIN rolini o'zgartirib bo'lmaydi.";
        }

        target.setRole(Role.MANAGER);
        employeeRepository.save(target);
        auditLogService.logAction(AuditActionType.ROLE_CHANGED, actor, target, "Yangi rol: MANAGER");

        return target.getFullName() + " endi MANAGER.";
    }

    public String makeEmployee(Long actorTelegramUserId, Long targetTelegramUserId) {
        Employee actor = employeeRepository.findByTelegramUserId(actorTelegramUserId).orElse(null);
        Employee target = employeeRepository.findByTelegramUserId(targetTelegramUserId).orElse(null);

        if (actor == null || target == null) {
            return "Foydalanuvchi topilmadi.";
        }

        if (actor.getRole() != Role.ADMIN) {
            return "Faqat ADMIN EMPLOYEE rolini bera oladi.";
        }

        if (target.getRole() == Role.ADMIN) {
            return "Bu buyruq bilan ADMIN rolini pasaytirib bo'lmaydi.";
        }

        target.setRole(Role.EMPLOYEE);
        employeeRepository.save(target);
        auditLogService.logAction(AuditActionType.ROLE_CHANGED, actor, target, "Yangi rol: EMPLOYEE");

        return target.getFullName() + " endi EMPLOYEE.";
    }

    public String makeAdmin(Long actorTelegramUserId, Long targetTelegramUserId) {
        Employee actor = employeeRepository.findByTelegramUserId(actorTelegramUserId).orElse(null);
        Employee target = employeeRepository.findByTelegramUserId(targetTelegramUserId).orElse(null);

        if (actor == null || target == null) {
            return "Foydalanuvchi topilmadi.";
        }

        if (actor.getRole() != Role.ADMIN) {
            return "Faqat ADMIN ADMIN rolini bera oladi.";
        }

        target.setRole(Role.ADMIN);
        employeeRepository.save(target);
        auditLogService.logAction(AuditActionType.ROLE_CHANGED, actor, target, "Yangi rol: ADMIN");

        return target.getFullName() + " endi ADMIN.";
    }
}
