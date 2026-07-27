package com.advancedprogramming.worklybot.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * One employee's answer to one question within an attempt.
 * correct is null when the question needs manual review (OPEN type, or a
 * SHORT_ANSWER that didn't match any accepted keyword).
 */
@Entity
@Table(name = "emp_test_answers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmpTestAnswer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "attempt_id", nullable = false)
    private EmpTestAttempt attempt;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "question_id", nullable = false)
    private EmpTestQuestion question;

    @Column(name = "answer_text", length = 4000)
    private String answerText;

    @Column(name = "is_correct")
    private Boolean correct;
}