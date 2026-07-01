package com.advancedprogramming.worklybot.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/**
 * A company-wide non-working day. Attendance on a holiday is never counted as late
 * and never penalised, exactly like a configured weekly off day.
 */
@Entity
@Table(name = "holidays")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Holiday {

    @Id
    @Column(name = "holiday_date")
    private LocalDate date;

    @Column(name = "description")
    private String description;
}
