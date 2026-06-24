package com.advancedprogramming.worklybot.scheduler;

import com.advancedprogramming.worklybot.entity.enums.Shift;
import com.advancedprogramming.worklybot.service.AwardService;
import com.advancedprogramming.worklybot.service.ReminderService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.EnumMap;
import java.util.Map;

/**
 * Fires shift-aware arrival/leave reminders at each shift's start/end (every day,
 * including weekends), and broadcasts the monthly awards on the 1st at 11:00.
 */
@Component
@RequiredArgsConstructor
public class AttendanceReminderScheduler {

    private final Clock appClock;
    private final ReminderService reminderService;
    private final AwardService awardService;

    private final Map<Shift, LocalDate> lastArrivalReminder = new EnumMap<>(Shift.class);
    private final Map<Shift, LocalDate> lastLeaveReminder = new EnumMap<>(Shift.class);

    @Scheduled(cron = "0 * * * * *", zone = "${app.time-zone:Asia/Tashkent}")
    public void triggerReminders() {
        LocalDate today = LocalDate.now(appClock);
        LocalTime now = LocalTime.now(appClock).withSecond(0).withNano(0);

        for (Shift shift : Shift.values()) {
            if (!today.equals(lastArrivalReminder.get(shift)) && now.equals(shift.getStartTime())) {
                reminderService.remindMissingArrivals(shift);
                lastArrivalReminder.put(shift, today);
            }
            if (!today.equals(lastLeaveReminder.get(shift)) && now.equals(shift.getEndTime())) {
                reminderService.remindMissingLeaves(shift);
                lastLeaveReminder.put(shift, today);
            }
        }
    }

    @Scheduled(cron = "0 0 11 1 * *", zone = "${app.time-zone:Asia/Tashkent}")
    public void announceMonthlyAwards() {
        YearMonth previousMonth = YearMonth.from(LocalDate.now(appClock)).minusMonths(1);
        reminderService.broadcastAwards(awardService.computeAwards(previousMonth));
    }
}
