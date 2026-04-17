package com.advancedprogramming.worklybot.scheduler;

import com.advancedprogramming.worklybot.service.ReminderService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AttendanceReminderScheduler {

    private final ReminderService reminderService;

    // Morning reminder for missing arrival
    @Scheduled(cron = "0 0 9 * * MON-SUN", zone = "Asia/Tashkent")
    public void remindArrivals() {
        reminderService.remindMissingArrivals();
    }

    // Evening reminder for missing leave
    @Scheduled(cron = "0 0 21 * * MON-SUN", zone = "Asia/Tashkent")
    public void remindLeaves() {
        reminderService.remindMissingLeaves();
    }
}