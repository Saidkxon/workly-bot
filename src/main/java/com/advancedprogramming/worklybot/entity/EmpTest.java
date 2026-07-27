package com.advancedprogramming.worklybot.entity;

import com.advancedprogramming.worklybot.entity.enums.EmpTestStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * A single "current" test configuration: title, per-attempt timer, visibility, lifecycle.
 * Only one row is expected to be actively used at a time (the most recently created one).
 */
@Entity
@Table(name = "emp_tests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmpTest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(name = "timer_minutes", nullable = false)
    private Integer timerMinutes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EmpTestStatus status;

    @Column(name = "visible_to_all_employees", nullable = false)
    private boolean visibleToAllEmployees;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}