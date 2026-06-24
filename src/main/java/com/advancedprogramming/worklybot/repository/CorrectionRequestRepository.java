package com.advancedprogramming.worklybot.repository;

import com.advancedprogramming.worklybot.entity.CorrectionRequest;
import com.advancedprogramming.worklybot.entity.Employee;
import com.advancedprogramming.worklybot.entity.enums.CorrectionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface CorrectionRequestRepository extends JpaRepository<CorrectionRequest, Long> {
    List<CorrectionRequest> findAllByStatus(CorrectionStatus status);
    List<CorrectionRequest> findAllByStatusOrderByCreatedAtAsc(CorrectionStatus status);
    boolean existsByEmployeeAndWorkDateAndStatus(Employee employee, LocalDate workDate, CorrectionStatus status);
    Optional<CorrectionRequest> findTopByEmployeeAndWorkDateAndStatusOrderByCreatedAtDesc(
            Employee employee,
            LocalDate workDate,
            CorrectionStatus status
    );
}
