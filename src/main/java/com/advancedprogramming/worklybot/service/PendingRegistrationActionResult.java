package com.advancedprogramming.worklybot.service;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class PendingRegistrationActionResult {
    private final String managerMessage;
    private final Long chatId;
    private final String employeeMessage;
}
