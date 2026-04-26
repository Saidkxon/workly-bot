package com.advancedprogramming.worklybot.service;

import com.advancedprogramming.worklybot.entity.Employee;
import com.advancedprogramming.worklybot.entity.PendingRegistration;
import com.advancedprogramming.worklybot.entity.enums.AuditActionType;
import com.advancedprogramming.worklybot.entity.enums.Role;
import com.advancedprogramming.worklybot.repository.EmployeeRepository;
import com.advancedprogramming.worklybot.repository.PendingRegistrationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PendingRegistrationService {

    private final PendingRegistrationRepository pendingRegistrationRepository;
    private final EmployeeRepository employeeRepository;
    private final AuditLogService auditLogService;
    private final Clock appClock;

    public PendingRegistration createPendingRegistration(Long telegramUserId, Long chatId, String fullName, String department) {
        PendingRegistration pendingRegistration = pendingRegistrationRepository.findByTelegramUserId(telegramUserId)
                .orElseGet(PendingRegistration::new);

        pendingRegistration.setTelegramUserId(telegramUserId);
        pendingRegistration.setChatId(chatId);
        pendingRegistration.setFullName(fullName);
        pendingRegistration.setDepartment(department);

        if (pendingRegistration.getCreatedAt() == null) {
            pendingRegistration.setCreatedAt(LocalDateTime.now(appClock));
        }

        return pendingRegistrationRepository.save(pendingRegistration);
    }

    public PendingRegistration findByTelegramUserId(Long telegramUserId) {
        return pendingRegistrationRepository.findByTelegramUserId(telegramUserId).orElse(null);
    }

    public String getPendingRegistrationsText() {
        List<PendingRegistration> registrations = pendingRegistrationRepository.findAllByOrderByCreatedAtAsc();

        if (registrations.isEmpty()) {
            return "Faollashtirishni kutayotgan xodimlar yo'q.";
        }

        StringBuilder sb = new StringBuilder("Faollashtirishni kutayotgan xodimlar:\n\n");

        for (PendingRegistration registration : registrations) {
            sb.append("Ism familiya: ").append(registration.getFullName()).append("\n")
                    .append("Bo'lim: ").append(registration.getDepartment()).append("\n")
                    .append("Telegram user ID: ").append(registration.getTelegramUserId()).append("\n")
                    .append("Faollashtirish: /activate_").append(registration.getTelegramUserId()).append("\n")
                    .append("Rad etish: /deactivate_pending_").append(registration.getTelegramUserId()).append("\n")
                    .append("----------------------\n");
        }

        return sb.toString();
    }

    public PendingRegistrationActionResult approvePendingRegistration(Long actorTelegramUserId, Long targetTelegramUserId) {
        Employee actor = employeeRepository.findByTelegramUserId(actorTelegramUserId).orElse(null);
        PendingRegistration registration = pendingRegistrationRepository.findByTelegramUserId(targetTelegramUserId).orElse(null);

        if (actor == null || registration == null) {
            return new PendingRegistrationActionResult("Xodim topilmadi.", null, null);
        }

        if (actor.getRole() == Role.EMPLOYEE) {
            return new PendingRegistrationActionResult("Sizda foydalanuvchini faollashtirish huquqi yo'q.", null, null);
        }

        Employee employee = Employee.builder()
                .telegramUserId(registration.getTelegramUserId())
                .chatId(registration.getChatId())
                .fullName(registration.getFullName())
                .department(registration.getDepartment())
                .role(Role.EMPLOYEE)
                .active(true)
                .build();

        employeeRepository.save(employee);
        pendingRegistrationRepository.delete(registration);
        auditLogService.logAction(
                AuditActionType.REGISTRATION_APPROVED,
                actor,
                employee,
                "Yangi xodim ro'yxatdan o'tishi tasdiqlandi."
        );

        return new PendingRegistrationActionResult(
                "Xodim faollashtirildi: " + employee.getFullName(),
                employee.getChatId(),
                "Akkauntingiz faollashtirildi. Endi Workly botdan foydalanishingiz mumkin."
        );
    }

    public PendingRegistrationActionResult rejectPendingRegistration(Long actorTelegramUserId, Long targetTelegramUserId) {
        Employee actor = employeeRepository.findByTelegramUserId(actorTelegramUserId).orElse(null);
        PendingRegistration registration = pendingRegistrationRepository.findByTelegramUserId(targetTelegramUserId).orElse(null);

        if (actor == null || registration == null) {
            return new PendingRegistrationActionResult("Kutilayotgan ro'yxatdan o'tish topilmadi.", null, null);
        }

        if (actor.getRole() == Role.EMPLOYEE) {
            return new PendingRegistrationActionResult("Sizda foydalanuvchini rad etish huquqi yo'q.", null, null);
        }

        Employee pendingTarget = Employee.builder()
                .telegramUserId(registration.getTelegramUserId())
                .chatId(registration.getChatId())
                .fullName(registration.getFullName())
                .department(registration.getDepartment())
                .role(Role.EMPLOYEE)
                .active(false)
                .build();

        pendingRegistrationRepository.delete(registration);
        auditLogService.logAction(
                AuditActionType.REGISTRATION_REJECTED,
                actor,
                pendingTarget,
                "Yangi xodim ro'yxatdan o'tishi rad etildi."
        );

        return new PendingRegistrationActionResult(
                "Ro'yxatdan o'tish rad etildi: " + registration.getFullName(),
                registration.getChatId(),
                "Sizning ro'yxatdan o'tish so'rovingiz rad etildi. Qayta murojaat qilish uchun menejer yoki admin bilan bog'laning."
        );
    }
}
