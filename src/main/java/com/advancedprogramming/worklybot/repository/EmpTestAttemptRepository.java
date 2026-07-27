package com.advancedprogramming.worklybot.repository;

import com.advancedprogramming.worklybot.entity.EmpTest;
import com.advancedprogramming.worklybot.entity.Employee;
import com.advancedprogramming.worklybot.entity.EmpTestAttempt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EmpTestAttemptRepository extends JpaRepository<EmpTestAttempt, Long> {
    Optional<EmpTestAttempt> findByToken(String token);
    Optional<EmpTestAttempt> findByTestAndEmployee(EmpTest test, Employee employee);
    List<EmpTestAttempt> findAllByTestOrderByEmployee_FullNameAsc(EmpTest test);
}