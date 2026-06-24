package com.advancedprogramming.worklybot.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "feedback_responses")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FeedbackResponse {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "telegram_user_id", nullable = false)
    private Long telegramUserId;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(name = "department")
    private String department;

    @Column(name = "message", nullable = false, length = 2000)
    private String message;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
