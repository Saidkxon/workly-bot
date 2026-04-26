package com.advancedprogramming.worklybot.entity;

import com.advancedprogramming.worklybot.entity.enums.AuditActionType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "audit_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false, length = 64)
    private AuditActionType actionType;

    @Column(name = "actor_telegram_user_id", nullable = false)
    private Long actorTelegramUserId;

    @Column(name = "actor_name", nullable = false)
    private String actorName;

    @Column(name = "target_telegram_user_id")
    private Long targetTelegramUserId;

    @Column(name = "target_name")
    private String targetName;

    @Column(name = "details", length = 1000)
    private String details;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
