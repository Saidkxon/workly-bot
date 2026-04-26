package com.advancedprogramming.worklybot.scheduler;

import com.advancedprogramming.worklybot.service.ReminderService;
import com.advancedprogramming.worklybot.service.AttendanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;

@Component
@RequiredArgsConstructor
public class AttendanceReminderScheduler {

    private final Clock appClock;
    private final AttendanceService attendanceService;
    private final ReminderService reminderService;
    private LocalDate lastArrivalReminderDate;
    private LocalDate lastLeaveReminderDate;

    @Scheduled(cron = "0 * * * * *", zone = "${app.time-zone:Asia/Tashkent}")
    public void triggerReminders() {
        LocalDate today = LocalDate.now(appClock);
        LocalTime now = LocalTime.now(appClock).withSecond(0).withNano(0);
        LocalTime workStart = attendanceService.getWorkStartTime();
        LocalTime workEnd = attendanceService.getWorkEndTime();

        if (!today.equals(lastArrivalReminderDate)
                && !now.isBefore(workStart)
                && now.isBefore(workStart.plusMinutes(5))) {
            reminderService.remindMissingArrivals();
            lastArrivalReminderDate = today;
        }

        if (!today.equals(lastLeaveReminderDate)
                && !now.isBefore(workEnd)
                && now.isBefore(workEnd.plusMinutes(5))) {
            reminderService.remindMissingLeaves();
            lastLeaveReminderDate = today;
        }
    }
}
