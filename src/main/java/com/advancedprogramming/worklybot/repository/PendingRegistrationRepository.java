package com.advancedprogramming.worklybot.repository;

import com.advancedprogramming.worklybot.entity.PendingRegistration;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PendingRegistrationRepository extends JpaRepository<PendingRegistration, Long> {
    Optional<PendingRegistration> findByTelegramUserId(Long telegramUserId);
    List<PendingRegistration> findAllByOrderByCreatedAtAsc();
}
