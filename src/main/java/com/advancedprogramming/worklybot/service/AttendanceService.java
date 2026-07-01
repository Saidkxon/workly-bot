package com.advancedprogramming.worklybot.service;

import com.advancedprogramming.worklybot.bot.BotMessages;
import com.advancedprogramming.worklybot.config.OfficeProperties;
import com.advancedprogramming.worklybot.config.PenaltyProperties;
import com.advancedprogramming.worklybot.entity.Attendance;
import com.advancedprogramming.worklybot.entity.Employee;
import com.advancedprogramming.worklybot.entity.enums.Shift;
import com.advancedprogramming.worklybot.repository.AttendanceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Attendance + lateness. Lateness, the "can leave now" rule and reminders are all
 * derived from the employee's {@link Shift} (start/end) plus a configurable grace
 * period, instead of a single office-wide work time.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AttendanceService {

    private final AttendanceRepository attendanceRepository;
    private final OfficeProperties officeProperties;
    private final EarlyLeaveService earlyLeaveService;
    private final PenaltyProperties penaltyProperties;
    private final WorkCalendarService workCalendarService;
    private final Clock appClock;

    @Transactional
    public String saveArrivalAfterLocationCheck(Employee employee) {
        LocalDateTime now = LocalDateTime.now(appClock);
        LocalDate today = now.toLocalDate();

        Attendance attendance = attendanceRepository.findByEmployeeAndWorkDate(employee, today).orElse(null);

        if (attendance != null && attendance.getArrivalTime() != null) {
            return "Siz bugungi kelgan vaqtingizni allaqachon belgilagansiz.";
        }

        if (attendance == null) {
            attendance = Attendance.builder()
                    .employee(employee)
                    .workDate(today)
                    .arrivalTime(now)
                    .build();
        } else {
            attendance.setArrivalTime(now);
        }

        try {
            attendanceRepository.saveAndFlush(attendance);
        } catch (DataIntegrityViolationException exception) {
            log.warn("Duplicate attendance row blocked for employee {} on {}", employee.getTelegramUserId(), today);
            return "Siz bugungi kelgan vaqtingizni allaqachon belgilagansiz.";
        }

        return "Ishga kelish vaqti tasdiqlandi va saqlandi: "
                + attendance.getArrivalTime().toLocalTime().withNano(0);
    }

    @Transactional
    public String saveLeavingAfterLocationCheck(Employee employee) {
        LocalDateTime now = LocalDateTime.now(appClock);
        LocalDate today = now.toLocalDate();

        Attendance attendance = attendanceRepository.findByEmployeeAndWorkDate(employee, today).orElse(null);

        if (attendance == null || attendance.getArrivalTime() == null) {
            return "Siz bugun hali ishga kelishni belgilamagansiz.";
        }

        if (attendance.getLeaveTime() != null) {
            return "Siz bugungi ketish vaqtingizni allaqachon belgilagansiz.";
        }

        if (!canMarkLeaving(employee)) {
            return getLeavingNotAllowedMessage(employee);
        }

        attendance.setLeaveTime(now);
        attendanceRepository.save(attendance);

        return "Ishdan ketish vaqti tasdiqlandi va saqlandi: "
                + attendance.getLeaveTime().toLocalTime().withNano(0);
    }

    public String getTodayStatus(Employee employee) {
        LocalDate today = LocalDate.now(appClock);

        Attendance attendance = attendanceRepository.findByEmployeeAndWorkDate(employee, today).orElse(null);

        if (attendance == null) {
            return "Bugun uchun davomat ma'lumoti topilmadi.";
        }

        String arrival = attendance.getArrivalTime() == null
                ? "Belgilanmagan"
                : attendance.getArrivalTime().toLocalTime().withNano(0).toString();

        String leaving = attendance.getLeaveTime() == null
                ? "Belgilanmagan"
                : attendance.getLeaveTime().toLocalTime().withNano(0).toString();

        return "Bugungi holat:\nKelgan vaqt: " + arrival + "\nKetgan vaqt: " + leaving;
    }

    public long calculateWorkedMinutes(Attendance attendance) {
        if (attendance.getArrivalTime() == null) {
            return 0;
        }
        LocalDateTime end = effectiveLeaveTime(attendance);
        return Math.max(0, Duration.between(attendance.getArrivalTime(), end).toMinutes());
    }

    /**
     * The leave time to use for worked-time calculations. When an employee forgot to
     * mark leaving, they are credited up to their shift end for that day (and only up to
     * "now" if the day is still in progress), instead of losing the whole day as 0.
     */
    private LocalDateTime effectiveLeaveTime(Attendance attendance) {
        if (attendance.getLeaveTime() != null) {
            return attendance.getLeaveTime();
        }
        LocalDate workDate = attendance.getWorkDate();
        LocalDateTime shiftEndToday = workDate.atTime(shiftEnd(attendance.getEmployee()));
        LocalDateTime now = LocalDateTime.now(appClock);
        if (workDate.equals(now.toLocalDate()) && now.isBefore(shiftEndToday)) {
            return now; // still on the clock today — don't credit future minutes
        }
        return shiftEndToday;
    }

    private boolean isPenaltyFreeDay(Attendance attendance) {
        return workCalendarService.isPenaltyFreeDay(attendance.getWorkDate());
    }

    public String calculateWorkedHours(Attendance attendance) {
        return formatMinutesAsHours(calculateWorkedMinutes(attendance));
    }

    public String formatMinutesAsHours(long totalMinutes) {
        long hours = totalMinutes / 60;
        long minutes = totalMinutes % 60;
        return hours + " soat " + minutes + " daqiqa";
    }

    /** Late minutes counted from the employee's shift start + grace period. */
    public long calculateLateMinutes(Attendance attendance) {
        if (attendance.getArrivalTime() == null) {
            return 0;
        }
        // Off days (weekly off-days + holidays) are penalty-free — never counted as late.
        if (isPenaltyFreeDay(attendance)) {
            return 0;
        }

        LocalTime effectiveStart = effectiveStart(attendance.getEmployee());
        LocalTime arrivalTime = attendance.getArrivalTime().toLocalTime();

        if (!arrivalTime.isAfter(effectiveStart)) {
            return 0;
        }
        return Duration.between(effectiveStart, arrivalTime).toMinutes();
    }

    public String calculateLateStatus(Attendance attendance) {
        if (attendance.getArrivalTime() == null && attendance.getLeaveTime() == null) {
            return BotMessages.STATUS_ABSENT;
        }
        if (attendance.getArrivalTime() == null) {
            return BotMessages.STATUS_MISSING_ARRIVAL;
        }
        if (!isPenaltyFreeDay(attendance)
                && attendance.getArrivalTime().toLocalTime().isAfter(effectiveStart(attendance.getEmployee()))) {
            return BotMessages.STATUS_LATE;
        }
        if (attendance.getLeaveTime() == null) {
            return BotMessages.STATUS_MISSING_CHECKOUT;
        }
        return BotMessages.STATUS_ON_TIME;
    }

    public double calculateDistanceMeters(double lat1, double lon1, double lat2, double lon2) {
        final int EARTH_RADIUS = 6371000;

        double lat1Rad = Math.toRadians(lat1);
        double lat2Rad = Math.toRadians(lat2);
        double deltaLat = Math.toRadians(lat2 - lat1);
        double deltaLon = Math.toRadians(lon2 - lon1);

        double a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2)
                + Math.cos(lat1Rad) * Math.cos(lat2Rad)
                * Math.sin(deltaLon / 2) * Math.sin(deltaLon / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS * c;
    }

    // ---- Shift helpers ---------------------------------------------------------

    public LocalTime shiftStart(Employee employee) {
        return shiftOf(employee).getStartTime();
    }

    public LocalTime shiftEnd(Employee employee) {
        return shiftOf(employee).getEndTime();
    }

    private LocalTime effectiveStart(Employee employee) {
        return shiftStart(employee).plusMinutes(penaltyProperties.getGraceMinutes());
    }

    private Shift shiftOf(Employee employee) {
        return employee == null ? Shift.MORNING : Shift.orDefault(employee.getShift());
    }

    public boolean canMarkLeaving(Employee employee) {
        LocalTime now = LocalTime.now(appClock);
        return !now.isBefore(shiftEnd(employee)) || earlyLeaveService.hasApprovedRequestForToday(employee);
    }

    public boolean hasOpenAttendanceForToday(Employee employee) {
        if (employee == null) {
            return false;
        }
        return attendanceRepository.findByEmployeeAndWorkDate(employee, LocalDate.now(appClock))
                .map(attendance -> attendance.getArrivalTime() != null && attendance.getLeaveTime() == null)
                .orElse(false);
    }

    public String getLeavingNotAllowedMessage(Employee employee) {
        return shiftEnd(employee) + " dan oldin ketish mumkin emas. Erta ketish uchun sabab yuboring yoki menejer ruxsatini kuting.";
    }

    public double getOfficeLatitude() {
        return officeProperties.getLatitude();
    }

    public double getOfficeLongitude() {
        return officeProperties.getLongitude();
    }

    public double getAllowedRadiusMeters() {
        return officeProperties.getAllowedRadiusMeters();
    }
}