package com.advancedprogramming.worklybot.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Penalty and default-salary settings. Overridable via application config, e.g.
 *   penalty.grace-minutes=10
 *   penalty.amount-per-late-minute=3000
 *   penalty.default-qabul-salary=4000000
 *   penalty.default-base-salary=3000000
 *
 * The per-department base salary is stored in the database (DepartmentSalary) and
 * is editable at runtime; these defaults are only used to seed/fallback.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "penalty")
public class PenaltyProperties {

    /** Minutes of grace after shift start before lateness begins. */
    private int graceMinutes = 10;

    /** So'm deducted per late minute (after the first warning day of the month). */
    private long amountPerLateMinute = 3000;

    /** Default monthly base salary for Qabul bo'limi. */
    private long defaultQabulSalary = 4_000_000;

    /** Default monthly base salary for every other department. */
    private long defaultBaseSalary = 3_000_000;
}
