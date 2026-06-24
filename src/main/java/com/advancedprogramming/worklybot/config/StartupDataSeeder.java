package com.advancedprogramming.worklybot.config;

import com.advancedprogramming.worklybot.service.SalaryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Seeds the five department_salaries rows with their configured defaults on startup.
 * Runs after the schema is in place and is idempotent (only inserts missing rows),
 * so existing/edited salaries are never overwritten.
 */
@Component
@Order(100)
@Slf4j
@RequiredArgsConstructor
public class StartupDataSeeder implements CommandLineRunner {

    private final SalaryService salaryService;

    @Override
    public void run(String... args) {
        salaryService.seedDefaults();
        log.info("Default department salaries ensured");
    }
}
