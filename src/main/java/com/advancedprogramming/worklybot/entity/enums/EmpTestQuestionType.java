package com.advancedprogramming.worklybot.entity.enums;

public enum EmpTestQuestionType {
    /** Four lettered options (A/B/C/D), one correct — fully auto-graded. */
    MULTIPLE_CHOICE,
    /** Free-text answer, no options shown. Optional keyword list enables
     *  auto-grading; left blank, it's always graded manually by the admin. */
    TEXT
}