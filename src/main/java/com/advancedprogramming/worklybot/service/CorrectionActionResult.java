package com.advancedprogramming.worklybot.service;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class CorrectionActionResult {
    private String managerMessage;
    private Long employeeChatId;
    private String employeeMessage;
}
