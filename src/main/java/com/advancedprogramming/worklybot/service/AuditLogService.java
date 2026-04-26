package com.advancedprogramming.worklybot.service;

import com.advancedprogramming.worklybot.entity.AuditLog;
import com.advancedprogramming.worklybot.entity.Employee;
import com.advancedprogramming.worklybot.entity.enums.AuditActionType;
import com.advancedprogramming.worklybot.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AuditLogService {

    private static final DateTimeFormatter AUDIT_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final AuditLogRepository auditLogRepository;
    private final Clock appClock;

    public void logAction(AuditActionType actionType, Employee actor, Employee target, String details) {
        if (actor == null) {
            return;
        }

        auditLogRepository.save(
                AuditLog.builder()
                        .actionType(actionType)
                        .actorTelegramUserId(actor.getTelegramUserId())
                        .actorName(actor.getFullName())
                        .targetTelegramUserId(target == null ? null : target.getTelegramUserId())
                        .targetName(target == null ? null : target.getFullName())
                        .details(details)
                        .createdAt(LocalDateTime.now(appClock))
                        .build()
        );
    }

    public String getRecentActivityText() {
        List<AuditLog> logs = auditLogRepository.findTop20ByOrderByCreatedAtDesc();

        if (logs.isEmpty()) {
            return "Audit log hozircha bo'sh.";
        }

        StringBuilder sb = new StringBuilder("Oxirgi audit yozuvlari:\n\n");

        for (AuditLog log : logs) {
            sb.append("Vaqt: ").append(log.getCreatedAt().format(AUDIT_TIME_FORMAT)).append("\n")
                    .append("Amal: ").append(log.getActionType()).append("\n")
                    .append("Kim bajardi: ").append(log.getActorName())
                    .append(" (").append(log.getActorTelegramUserId()).append(")\n");

            if (log.getTargetName() != null) {
                sb.append("Nishon: ").append(log.getTargetName());

                if (log.getTargetTelegramUserId() != null) {
                    sb.append(" (").append(log.getTargetTelegramUserId()).append(")");
                }

                sb.append("\n");
            }

            if (log.getDetails() != null && !log.getDetails().isBlank()) {
                sb.append("Tafsilot: ").append(log.getDetails()).append("\n");
            }

            sb.append("----------------------\n");
        }

        return sb.toString();
    }
}
