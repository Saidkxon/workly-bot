package com.advancedprogramming.worklybot.service;

public record RoleChangeResult(String message, Long targetTelegramUserId, boolean changed) {
}
