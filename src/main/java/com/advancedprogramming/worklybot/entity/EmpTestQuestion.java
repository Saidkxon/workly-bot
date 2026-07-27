package com.advancedprogramming.worklybot.entity;

import com.advancedprogramming.worklybot.entity.enums.EmpTestQuestionType;
import jakarta.persistence.*;
import lombok.*;

/**
 * One question in a test.
 * For MULTIPLE_CHOICE: optionA..optionD hold the four choices, correctAnswer holds
 * the correct letter ("A"/"B"/"C"/"D").
 * For TEXT: optionA..optionD are unused; correctAnswer optionally holds a
 * comma-separated list of accepted keywords — left null/blank, it's always
 * graded manually by the admin.
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

    @Column(name = "option_a", length = 500)
    private String optionA;

    @Column(name = "option_b", length = 500)
    private String optionB;

    @Column(name = "option_c", length = 500)
    private String optionC;

    @Column(name = "option_d", length = 500)
    private String optionD;

    @Column(name = "correct_answer", length = 1000)
    private String correctAnswer;

    @Column(nullable = false)
    private Integer points;
}