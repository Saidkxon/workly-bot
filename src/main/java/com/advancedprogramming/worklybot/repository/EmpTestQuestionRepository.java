package com.advancedprogramming.worklybot.repository;

import com.advancedprogramming.worklybot.entity.EmpTest;
import com.advancedprogramming.worklybot.entity.EmpTestQuestion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EmpTestQuestionRepository extends JpaRepository<EmpTestQuestion, Long> {
    List<EmpTestQuestion> findAllByTestOrderByOrderIndexAsc(EmpTest test);
    long countByTest(EmpTest test);
}