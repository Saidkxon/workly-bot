package com.advancedprogramming.worklybot.config;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BotPropertiesTests {

    @Test
    void parsesAdminTelegramUserIdsFromCommaSeparatedString() {
        BotProperties properties = new BotProperties();
        properties.setAdminTelegramUserIds("123, 456,789");

        assertEquals(Set.of(123L, 456L, 789L), properties.getAdminTelegramUserIdSet());
        assertTrue(properties.isAdminUser(456L));
        assertFalse(properties.isAdminUser(999L));
    }

    @Test
    void returnsEmptySetWhenAdminTelegramUserIdsMissing() {
        BotProperties properties = new BotProperties();
        properties.setAdminTelegramUserIds("   ");

        assertTrue(properties.getAdminTelegramUserIdSet().isEmpty());
    }
}
