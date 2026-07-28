package com.advancedprogramming.worklybot.entity;

import com.advancedprogramming.worklybot.entity.enums.EmpTestAttemptStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * One employee's attempt at a test, identified by a unique token used on the
 * standalone test page (no Telegram WebApp auth there — the token is the identity).
 */
@Entity
@Table(name = "emp_test_attempts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmpTestAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "test_id", nullable = false)
    private EmpTest test;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Column(nullable = false, unique = true, length = 64)
    private String token;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EmpTestAttemptStatus status;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "deadline_at")
    private LocalDateTime deadlineAt;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @Column(name = "violation_count", nullable = false)
    private int violationCount;

    @Column(name = "block_reason", length = 500)
    private String blockReason;

    @Column(name = "score")
    private Integer score;

    @Column(name = "max_score")
    private Integer maxScore;

    /** Comma-separated question IDs in the order shown to this employee (shuffled
     *  once per attempt so different employees see a different order, but it stays
     *  consistent for that employee across reloads). Null for attempts created
     *  before this feature existed — handled with a fallback in EmpTestService. */
    @Column(name = "question_order", length = 2000)
    private String questionOrder;
}