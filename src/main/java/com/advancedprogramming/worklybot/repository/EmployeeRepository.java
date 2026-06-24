package com.advancedprogramming.worklybot.repository;

import com.advancedprogramming.worklybot.entity.Employee;
import com.advancedprogramming.worklybot.entity.enums.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface EmployeeRepository extends JpaRepository<Employee, Long> {
    Optional<Employee> findByTelegramUserId(Long telegramUserId);
    List<Employee> findAllByActiveTrue();
    List<Employee> findAllByActiveTrueOrderByFullNameAsc();
    List<Employee> findAllByActiveFalseOrderByFullNameAsc();
    List<Employee> findAllByActiveTrueAndRoleInOrderByFullNameAsc(Collection<Role> roles);
    List<Employee> findAllByOrderByFullNameAsc();
}
