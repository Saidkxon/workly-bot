package com.advancedprogramming.worklybot.config;

import jakarta.annotation.PostConstruct;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "bot")
public class BotProperties {

    @NotBlank
    private String token;

    @NotBlank
    private String username = "workly_attendance_bot";
    private String adminTelegramUserIds = "";

    @PostConstruct
    void validateAdminTelegramUserIds() {
        getAdminTelegramUserIdSet();
    }

    public boolean isAdminUser(Long telegramUserId) {
        return getAdminTelegramUserIdSet().contains(telegramUserId);
    }

    public Set<Long> getAdminTelegramUserIdSet() {
        if (adminTelegramUserIds == null || adminTelegramUserIds.isBlank()) {
            return Set.of();
        }

        return Arrays.stream(adminTelegramUserIds.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(Long::parseLong)
                .collect(Collectors.toUnmodifiableSet());
    }
}
