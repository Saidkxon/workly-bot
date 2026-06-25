package com.advancedprogramming.worklybot.repository;

import com.advancedprogramming.worklybot.entity.Employee;
import com.advancedprogramming.worklybot.entity.ProfileChangeRequest;
import com.advancedprogramming.worklybot.entity.enums.CorrectionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProfileChangeRequestRepository extends JpaRepository<ProfileChangeRequest, Long> {
    boolean existsByEmployeeAndStatus(Employee employee, CorrectionStatus status);
    long countByStatus(CorrectionStatus status);
    List<ProfileChangeRequest> findAllByStatusOrderByCreatedAtAsc(CorrectionStatus status);
    Optional<ProfileChangeRequest> findTopByEmployeeAndStatusOrderByCreatedAtDesc(Employee employee, CorrectionStatus status);
}
