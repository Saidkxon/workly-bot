package com.advancedprogramming.worklybot.repository;

import com.advancedprogramming.worklybot.entity.FeedbackResponse;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FeedbackResponseRepository extends JpaRepository<FeedbackResponse, Long> {
    List<FeedbackResponse> findTop30ByOrderByCreatedAtDesc();
}
