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

        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS department_salaries (
                    department VARCHAR(32) PRIMARY KEY,
                    monthly_amount BIGINT NOT NULL
                )
                """);

        jdbcTemplate.execute("""
                ALTER TABLE employees
                ADD COLUMN IF NOT EXISTS shift VARCHAR(16)
                """);

        jdbcTemplate.execute("""
                ALTER TABLE pending_registrations
                ADD COLUMN IF NOT EXISTS shift VARCHAR(16)
                """);

        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS feedback_responses (
                    id BIGSERIAL PRIMARY KEY,
                    telegram_user_id BIGINT NOT NULL,
                    full_name VARCHAR(255) NOT NULL,
                    department VARCHAR(64),
                    message VARCHAR(2000) NOT NULL,
                    created_at TIMESTAMP NOT NULL
                )
                """);

        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS profile_change_requests (
                    id BIGSERIAL PRIMARY KEY,
                    employee_id BIGINT NOT NULL REFERENCES employees(id),
                    current_department VARCHAR(255) NOT NULL,
                    requested_department VARCHAR(255) NOT NULL,
                    current_shift VARCHAR(16),
                    requested_shift VARCHAR(16) NOT NULL,
                    status VARCHAR(32) NOT NULL,
                    created_at TIMESTAMP NOT NULL,
                    reviewed_at TIMESTAMP,
                    reviewed_by_telegram_user_id BIGINT
                )
                """);

        dropStaleEnumCheckConstraints();
    }

    /**
     * Hibernate (6+/7) auto-generates a CHECK constraint such as
     * {@code col in ('A','B',...)} for every {@code @Enumerated(EnumType.STRING)}
     * column. With {@code ddl-auto: update} those constraints are created once and
     * never refreshed, so adding a new enum value (e.g. a new Shift or a new
     * AuditActionType) makes every INSERT carrying that value fail with a constraint
     * violation. The bot then swallows the exception, and the user just sees a generic
     * error or no response at all.
     *
     * <p>This codebase defines no handwritten CHECK constraints, so every CHECK
     * constraint in the public schema is one of these Hibernate enum whitelists and is
     * safe to drop. The columns stay plain VARCHAR, so any present or future enum value
     * is accepted. Runs on every boot and is idempotent: once a constraint is dropped,
     * Hibernate's {@code update} mode does not re-add it to an already-existing column.
     *
     * <p>We match on {@code contype = 'c'} (the constraint type) rather than the text of
     * the constraint definition. An earlier attempt matched {@code pg_get_constraintdef()}
     * against {@code 'in ('}, but PostgreSQL rewrites {@code col IN (...)} into
     * {@code col = ANY (ARRAY[...])} when it stores the constraint, so that text never
     * appeared and the audit_logs constraint was missed.
     *
     * <p>NOTE: if you ever add a genuine business CHECK constraint by hand, restrict the
     * filter below to the specific enum columns/tables so you do not drop your own.
     */
    private void dropStaleEnumCheckConstraints() {
        jdbcTemplate.execute("""
                DO $$
                DECLARE r RECORD;
                BEGIN
                    FOR r IN
                        SELECT conrelid::regclass AS tbl, conname
                        FROM pg_constraint
                        WHERE contype = 'c'
                          AND connamespace = 'public'::regnamespace
                    LOOP
                        EXECUTE format('ALTER TABLE %s DROP CONSTRAINT %I', r.tbl, r.conname);
                    END LOOP;
                END $$;
                """);

        log.info("Dropped stale Hibernate-generated enum CHECK constraints (if any)");
    }
}