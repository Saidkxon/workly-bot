package com.advancedprogramming.worklybot.entity;

import com.advancedprogramming.worklybot.entity.enums.CorrectionStatus;
import com.advancedprogramming.worklybot.entity.enums.Shift;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "profile_change_requests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProfileChangeRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "current_department", nullable = false)
    private String currentDepartment;

    @Column(name = "requested_department", nullable = false)
    private String requestedDepartment;

    @Enumerated(EnumType.STRING)
    @Column(name = "current_shift")
    private Shift currentShift;

    @Enumerated(EnumType.STRING)
    @Column(name = "requested_shift", nullable = false)
    private Shift requestedShift;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CorrectionStatus status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "reviewed_by_telegram_user_id")
    private Long reviewedByTelegramUserId;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;
}
