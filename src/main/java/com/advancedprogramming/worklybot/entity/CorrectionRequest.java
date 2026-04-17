package com.advancedprogramming.worklybot.entity;

import com.advancedprogramming.worklybot.entity.enums.CorrectionStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Table(name = "correction_requests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CorrectionRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "work_date", nullable = false)
    private LocalDate workDate;

    @Column(name = "requested_arrival_time")
    private LocalTime requestedArrivalTime;

    @Column(name = "requested_leave_time")
    private LocalTime requestedLeaveTime;

    @Column(name = "reason")
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CorrectionStatus status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;
}
