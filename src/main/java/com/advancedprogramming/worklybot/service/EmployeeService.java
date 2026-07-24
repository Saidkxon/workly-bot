package com.advancedprogramming.worklybot.service;

import com.advancedprogramming.worklybot.entity.Employee;
import com.advancedprogramming.worklybot.entity.enums.AuditActionType;
import com.advancedprogramming.worklybot.entity.enums.Role;
import com.advancedprogramming.worklybot.repository.AttendanceRepository;
import com.advancedprogramming.worklybot.repository.CorrectionRequestRepository;
import com.advancedprogramming.worklybot.repository.EarlyLeaveRequestRepository;
import com.advancedprogramming.worklybot.repository.EmployeeRepository;
import com.advancedprogramming.worklybot.repository.PendingRegistrationRepository;
import com.advancedprogramming.worklybot.repository.ProfileChangeRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class EmployeeService {

    private final EmployeeRepository employeeRepository;
    private final AuditLogService auditLogService;
    private final AttendanceRepository attendanceRepository;
    private final CorrectionRequestRepository correctionRequestRepository;
    private final EarlyLeaveRequestRepository earlyLeaveRequestRepository;
    private final ProfileChangeRequestRepository profileChangeRequestRepository;
    private final PendingRegistrationRepository pendingRegistrationRepository;
    private final FeedbackService feedbackService;

    public String getAllEmployeesText() {
        List<Employee> employees = employeeRepository.findAllByOrderByFullNameAsc();

        if (employees.isEmpty()) {
            return "Xodimlar topilmadi.";
        }

        StringBuilder sb = new StringBuilder("Xodimlar ro'yxati:\n\n");

        for (Employee employee : employees) {
            sb.append("Ism familiya: ").append(employee.getFullName()).append("\n")
                    .append("Bo'lim: ").append(employee.getDepartment()).append("\n")
                    .append("Telegram user ID: ").append(employee.getTelegramUserId()).append("\n")
                    .append("Rol: ").append(employee.getRole()).append("\n")
                    .append("Faolligi: ").append(employee.isActive() ? "HA" : "YO'Q").append("\n")
                    .append("O'chirish: /deactivate_").append(employee.getTelegramUserId()).append("\n")
                    .append("Faollashtirish: /activate_").append(employee.getTelegramUserId()).append("\n")
                    .append("Manager qilish: /make_manager_").append(employee.getTelegramUserId()).append("\n")
                    .append("Employee qilish: /make_employee_").append(employee.getTelegramUserId()).append("\n")
                    .append("Admin qilish: /make_admin_").append(employee.getTelegramUserId()).append("\n")
                    .append("----------------------\n");
        }

        return sb.toString();
    }

    public String activateEmployee(Long actorTelegramUserId, Long targetTelegramUserId) {
        Employee actor = employeeRepository.findByTelegramUserId(actorTelegramUserId).orElse(null);
        Employee target = employeeRepository.findByTelegramUserId(targetTelegramUserId).orElse(null);

        if (actor == null || target == null) {
            return "Xodim topilmadi.";
        }

        if (actor.getRole() == Role.EMPLOYEE) {
            return "Sizda foydalanuvchini faollashtirish huquqi yo'q.";
        }

        if (target.getRole() == Role.ADMIN && actor.getRole() != Role.ADMIN) {
            return "Faqat ADMIN ADMIN foydalanuvchini boshqara oladi.";
        }

        if (target.isActive()) {
            return "Xodim allaqachon faol.";
        }

        target.setActive(true);
        employeeRepository.save(target);
        auditLogService.logAction(AuditActionType.EMPLOYEE_ACTIVATED, actor, target, "Xodim faollashtirildi.");

        return "Xodim faollashtirildi: " + target.getFullName();
    }

    public String deactivateEmployee(Long actorTelegramUserId, Long targetTelegramUserId) {
        Employee actor = employeeRepository.findByTelegramUserId(actorTelegramUserId).orElse(null);
        Employee target = employeeRepository.findByTelegramUserId(targetTelegramUserId).orElse(null);

        if (actor == null || target == null) {
            return "Xodim topilmadi.";
        }

        if (actor.getRole() == Role.EMPLOYEE) {
            return "Sizda foydalanuvchini o'chirish huquqi yo'q.";
        }

        if (actor.getTelegramUserId().equals(targetTelegramUserId)) {
            return "O'zingizni o'chira olmaysiz.";
        }

        if (target.getRole() == Role.ADMIN) {
            return "ADMIN foydalanuvchini bu buyruq bilan o'chirib bo'lmaydi.";
        }

        if (actor.getRole() == Role.MANAGER && target.getRole() == Role.MANAGER) {
            return "MANAGER boshqa MANAGER ni o'chira olmaydi.";
        }

        if (!target.isActive()) {
            return "Xodim allaqachon faol emas.";
        }

        target.setActive(false);
        employeeRepository.save(target);
        auditLogService.logAction(AuditActionType.EMPLOYEE_DEACTIVATED, actor, target, "Xodim nofaol qilindi.");

        return "Xodim o'chirildi: " + target.getFullName();
    }

    /**
     * Permanently removes an employee (fired/inactive) and every record tied to them:
     * attendance history, correction/early-leave/profile-change requests, feedback,
     * and any stale pending-registration row. Once removed, the person must go through
     * the /register flow again from scratch if they want to use the bot.
     * Callers (e.g. the mini-app) are responsible for permission checks (admin-only,
     * not self, not another ADMIN) before invoking this — this method only performs
     * the actual wipe, wrapped in a single transaction so it either fully succeeds or
     * fully rolls back.
     */
    @Transactional
    public void deleteEmployeeCompletely(Employee actor, Employee target) {
        auditLogService.logAction(
                AuditActionType.EMPLOYEE_DELETED,
                actor,
                target,
                "Xodim butunlay o'chirildi: barcha davomat, so'rov va fikr yozuvlari bilan birga. " +
                        "Qayta foydalanish uchun /register orqali qaytadan ro'yxatdan o'tishi kerak."
        );

        attendanceRepository.deleteAllByEmployee(target);
        correctionRequestRepository.deleteAllByEmployee(target);
        earlyLeaveRequestRepository.deleteAllByEmployee(target);
        profileChangeRequestRepository.deleteAllByEmployee(target);
        feedbackService.deleteByTelegramUserId(target.getTelegramUserId());
        pendingRegistrationRepository.deleteByTelegramUserId(target.getTelegramUserId());

        employeeRepository.delete(target);
    }
}