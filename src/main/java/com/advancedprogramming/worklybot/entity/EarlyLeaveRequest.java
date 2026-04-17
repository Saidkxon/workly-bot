package com.advancedprogramming.worklybot.entity;

import com.advancedprogramming.worklybot.entity.enums.CorrectionStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "early_leave_requests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EarlyLeaveRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDate workDate;

    @Column(nullable = false, length = 1000)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CorrectionStatus status;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private boolean approvedForTodayLeave;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;
}
