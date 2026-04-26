package com.advancedprogramming.worklybot.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;
import java.util.TimeZone;

@Configuration
public class TimeConfiguration {

    @Bean
    ZoneId appZoneId(@Value("${app.time-zone:Asia/Tashkent}") String timeZone) {
        ZoneId zoneId = ZoneId.of(timeZone);
        TimeZone.setDefault(TimeZone.getTimeZone(zoneId));
        return zoneId;
    }

    @Bean
    Clock appClock(ZoneId appZoneId) {
        return Clock.system(appZoneId);
    }
}
