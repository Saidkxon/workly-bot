package com.advancedprogramming.worklybot.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "attendance",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_attendance_employee_work_date",
                columnNames = {"employee_id", "work_date"}
        )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Attendance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "work_date", nullable = false)
    private LocalDate workDate;

    @Column(name = "arrival_time")
    private LocalDateTime arrivalTime;

    @Column(name = "leave_time")
    private LocalDateTime leaveTime;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;
}
