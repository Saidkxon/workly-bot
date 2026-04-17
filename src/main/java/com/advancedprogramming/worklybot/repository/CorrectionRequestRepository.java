package com.advancedprogramming.worklybot.repository;

import com.advancedprogramming.worklybot.entity.CorrectionRequest;
import com.advancedprogramming.worklybot.entity.enums.CorrectionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CorrectionRequestRepository extends JpaRepository<CorrectionRequest, Long> {
    List<CorrectionRequest> findAllByStatus(CorrectionStatus status);
}
