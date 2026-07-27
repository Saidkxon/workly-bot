package com.advancedprogramming.worklybot.entity.enums;

public enum EmpTestQuestionType {
    /** Four lettered options (A/B/C/D), one correct — fully auto-graded. */
    MULTIPLE_CHOICE,
    /** Free-text answer, no options shown. Optional keyword list enables
     *  auto-grading; left blank, it's always graded manually by the admin. */
    TEXT,

    /**
     * Legacy values, no longer offered when adding a new question — kept only
     * so any question already saved with one of these types can still be
     * loaded (and deleted) instead of crashing the whole question list.
     * Grading treats them the same as TEXT.
     */
    @Deprecated TRUE_FALSE,
    @Deprecated SHORT_ANSWER,
    @Deprecated OPEN
}