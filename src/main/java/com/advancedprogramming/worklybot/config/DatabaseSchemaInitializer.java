package com.advancedprogramming.worklybot.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class DatabaseSchemaInitializer {

    private final JdbcTemplate jdbcTemplate;

    @PostConstruct
    void ensureSchema() {
        log.info("Ensuring database schema for custom runtime tables and columns");

        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS audit_logs (
                    id BIGSERIAL PRIMARY KEY,
                    action_type VARCHAR(64) NOT NULL,
                    actor_telegram_user_id BIGINT NOT NULL,
                    actor_name VARCHAR(255) NOT NULL,
                    target_telegram_user_id BIGINT,
                    target_name VARCHAR(255),
                    details VARCHAR(1000),
                    created_at TIMESTAMP NOT NULL
                )
                """);

        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS pending_registrations (
                    id BIGSERIAL PRIMARY KEY,
                    telegram_user_id BIGINT NOT NULL UNIQUE,
                    chat_id BIGINT NOT NULL,
                    full_name VARCHAR(255) NOT NULL,
                    department VARCHAR(255) NOT NULL,
                    created_at TIMESTAMP NOT NULL
                )
                """);

        jdbcTemplate.execute("""
                ALTER TABLE correction_requests
                ADD COLUMN IF NOT EXISTS reviewed_at TIMESTAMP
                """);

        jdbcTemplate.execute("""
                ALTER TABLE correction_requests
                ADD COLUMN IF NOT EXISTS reviewed_by_telegram_user_id BIGINT
                """);

        jdbcTemplate.execute("""
                ALTER TABLE early_leave_requests
                ADD COLUMN IF NOT EXISTS reviewed_at TIMESTAMP
                """);

        jdbcTemplate.execute("""
                ALTER TABLE early_leave_requests
                ADD COLUMN IF NOT EXISTS reviewed_by_telegram_user_id BIGINT
                """);
    }
}
