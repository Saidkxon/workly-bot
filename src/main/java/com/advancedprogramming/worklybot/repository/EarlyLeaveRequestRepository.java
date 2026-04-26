package com.advancedprogramming.worklybot.repository;

import com.advancedprogramming.worklybot.entity.EarlyLeaveRequest;
import com.advancedprogramming.worklybot.entity.Employee;
import com.advancedprogramming.worklybot.entity.enums.CorrectionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface EarlyLeaveRequestRepository extends JpaRepository<EarlyLeaveRequest, Long> {

    List<EarlyLeaveRequest> findAllByStatus(CorrectionStatus status);
    List<EarlyLeaveRequest> findAllByStatusOrderByCreatedAtAsc(CorrectionStatus status);
    boolean existsByEmployeeAndWorkDateAndStatus(Employee employee, LocalDate workDate, CorrectionStatus status);

    Optional<EarlyLeaveRequest> findTopByEmployeeAndWorkDateAndStatusOrderByCreatedAtDesc(
            Employee employee,
            LocalDate workDate,
            CorrectionStatus status
    );
}
