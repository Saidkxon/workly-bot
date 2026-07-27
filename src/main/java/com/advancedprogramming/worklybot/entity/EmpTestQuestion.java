package com.advancedprogramming.worklybot.entity;

import com.advancedprogramming.worklybot.entity.enums.EmpTestQuestionType;
import jakarta.persistence.*;
import lombok.*;

/**
 * One question in a test. For TRUE_FALSE, the correctAnswer is "TRUE"/"FALSE".
 * For SHORT_ANSWER, correctAnswer holds a comma-separated list of accepted keywords.
 * For OPEN, the correctAnswer is null — always graded manually by the admin.
 */
@Entity
@Table(name = "emp_test_questions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmpTestQuestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "test_id", nullable = false)
    private EmpTest test;

    @Column(name = "order_index", nullable = false)
    private Integer orderIndex;

    @Column(name = "question_text", nullable = false, length = 2000)
    private String questionText;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EmpTestQuestionType type;

    @Column(name = "correct_answer", length = 1000)
    private String correctAnswer;

    @Column(nullable = false)
    private Integer points;
}