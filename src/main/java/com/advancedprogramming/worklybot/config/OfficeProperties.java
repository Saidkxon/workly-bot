package com.advancedprogramming.worklybot.config;

import lombok.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "office")
public class OfficeProperties {

    private double latitude;
    private double longitude;
    private double allowedRadiusMeters;
    private String workStartTime;
    private String workEndTime;
}
