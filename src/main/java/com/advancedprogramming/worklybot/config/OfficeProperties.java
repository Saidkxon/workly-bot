package com.advancedprogramming.worklybot.config;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "office")
public class OfficeProperties {

    @DecimalMin("-90.0")
    private double latitude;

    @DecimalMin("-180.0")
    private double longitude;

    @DecimalMin("0.0")
    private double allowedRadiusMeters;

    @NotBlank
    private String workStartTime;

    @NotBlank
    private String workEndTime;
}
