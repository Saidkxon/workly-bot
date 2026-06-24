package com.advancedprogramming.worklybot.entity;

import com.advancedprogramming.worklybot.entity.enums.Department;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Editable monthly base salary for a department. One row per department.
 * Admins/managers update {@code monthlyAmount} and all reports recalculate from it.
 */
@Entity
@Table(name = "department_salaries")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DepartmentSalary {

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "department", nullable = false, length = 32)
    private Department department;

    @Column(name = "monthly_amount", nullable = false)
    private long monthlyAmount;
}
