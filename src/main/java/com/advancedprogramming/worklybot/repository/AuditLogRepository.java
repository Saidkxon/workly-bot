package com.advancedprogramming.worklybot.repository;

import com.advancedprogramming.worklybot.entity.AuditLog;
import com.advancedprogramming.worklybot.entity.enums.AuditActionType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    List<AuditLog> findTop20ByOrderByCreatedAtDesc();

    List<AuditLog> findTop50ByActionTypeOrderByCreatedAtDesc(AuditActionType actionType);
}
