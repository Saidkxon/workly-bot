package com.advancedprogramming.worklybot.repository;

import com.advancedprogramming.worklybot.entity.EmpTestAnswer;
import com.advancedprogramming.worklybot.entity.EmpTestAttempt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EmpTestAnswerRepository extends JpaRepository<EmpTestAnswer, Long> {
    List<EmpTestAnswer> findAllByAttempt(EmpTestAttempt attempt);
}