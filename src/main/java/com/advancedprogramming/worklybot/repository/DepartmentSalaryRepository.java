package com.advancedprogramming.worklybot.repository;

import com.advancedprogramming.worklybot.entity.DepartmentSalary;
import com.advancedprogramming.worklybot.entity.enums.Department;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DepartmentSalaryRepository extends JpaRepository<DepartmentSalary, Department> {
}
