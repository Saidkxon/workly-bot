package com.advancedprogramming.worklybot.bot.state;

import lombok.Getter;
import lombok.Setter;

import com.advancedprogramming.worklybot.entity.enums.Shift;
import java.time.LocalDate;

@Getter
@Setter
public class UserSession {
    private UserState state = UserState.NONE;
    private String fullName;
    private String department;
    private Shift shift;

    private LocalDate correctionDate;
    private String correctionType;
    private String earlyLeaveReason;
    private Long historyEmployeeTelegramUserId;
    private String requestedDepartment;
}
