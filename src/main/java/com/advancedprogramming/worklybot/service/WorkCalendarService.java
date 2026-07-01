package com.advancedprogramming.worklybot.service;

import com.advancedprogramming.worklybot.config.PenaltyProperties;
import com.advancedprogramming.worklybot.entity.Holiday;
import com.advancedprogramming.worklybot.repository.HolidayRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Single source of truth for which calendar days are penalty-free. A day is penalty-free
 * when it is a configured weekly off day (default: Sunday) or a company holiday. Both the
 * live attendance/report code ({@code AttendanceService}) and the salary/award code
 * ({@code SalaryService}) route their "is this day exempt from lateness?" check here, so
 * the rule can never drift between them.
 */
@Service
@RequiredArgsConstructor
public class WorkCalendarService {

    private final PenaltyProperties penaltyProperties;
    private final HolidayRepository holidayRepository;

    /** Lazily loaded cache of holiday dates; invalidated whenever holidays change. */
    private volatile Set<LocalDate> holidayCache;

    public boolean isPenaltyFreeDay(LocalDate date) {
        if (date == null) {
            return false;
        }
        if (penaltyProperties.getOffDays().contains(date.getDayOfWeek())) {
            return true;
        }
        return holidayDates().contains(date);
    }

    public boolean isHoliday(LocalDate date) {
        return date != null && holidayDates().contains(date);
    }

    public List<Holiday> listHolidays() {
        return holidayRepository.findAllByOrderByDateAsc();
    }

    public Holiday addHoliday(LocalDate date, String description) {
        Holiday holiday = holidayRepository.save(Holiday.builder().date(date).description(description).build());
        invalidate();
        return holiday;
    }

    public void removeHoliday(LocalDate date) {
        holidayRepository.deleteById(date);
        invalidate();
    }

    public void invalidate() {
        holidayCache = null;
    }

    private Set<LocalDate> holidayDates() {
        Set<LocalDate> cache = holidayCache;
        if (cache == null) {
            cache = new HashSet<>();
            for (Holiday holiday : holidayRepository.findAll()) {
                cache.add(holiday.getDate());
            }
            holidayCache = cache;
        }
        return cache;
    }
}
