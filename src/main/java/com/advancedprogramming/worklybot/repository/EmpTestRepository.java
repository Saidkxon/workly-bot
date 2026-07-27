package com.advancedprogramming.worklybot.repository;

import com.advancedprogramming.worklybot.entity.EmpTest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EmpTestRepository extends JpaRepository<EmpTest, Long> {
    Optional<EmpTest> findFirstByOrderByIdDesc();
}