package com.advancedprogramming.worklybot.service;

import com.advancedprogramming.worklybot.bot.BotMessages;
import com.advancedprogramming.worklybot.config.OfficeProperties;
import com.advancedprogramming.worklybot.entity.Attendance;
import com.advancedprogramming.worklybot.entity.Employee;
import com.advancedprogramming.worklybot.repository.AttendanceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Service
@Slf4j
@RequiredArgsConstructor
public class AttendanceService {

    private final AttendanceRepository attendanceRepository;
    private final OfficeProperties officeProperties;
    private final EarlyLeaveService earlyLeaveService;
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
            return getLeavingNotAllowedMessage();
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
        if (attendance.getArrivalTime() == null || attendance.getLeaveTime() == null) {
            return 0;
        }

        return Math.max(0, Duration.between(attendance.getArrivalTime(), attendance.getLeaveTime()).toMinutes());
    }

    public String calculateWorkedHours(Attendance attendance) {
        return formatMinutesAsHours(calculateWorkedMinutes(attendance));
    }

    public String formatMinutesAsHours(long totalMinutes) {
        long hours = totalMinutes / 60;
        long minutes = totalMinutes % 60;
        return hours + " soat " + minutes + " daqiqa";
    }

    public long calculateLateMinutes(Attendance attendance) {
        if (attendance.getArrivalTime() == null) {
            return 0;
        }

        LocalTime arrivalTime = attendance.getArrivalTime().toLocalTime();

        if (!arrivalTime.isAfter(getWorkStartTime())) {
            return 0;
        }

        return Duration.between(getWorkStartTime(), arrivalTime).toMinutes();
    }

    public String calculateLateStatus(Attendance attendance) {
        if (attendance.getArrivalTime() == null && attendance.getLeaveTime() == null) {
            return BotMessages.STATUS_ABSENT;
        }

        if (attendance.getArrivalTime() == null) {
            return BotMessages.STATUS_MISSING_ARRIVAL;
        }

        if (attendance.getArrivalTime().toLocalTime().isAfter(getWorkStartTime())) {
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

    public LocalTime getWorkStartTime() {
        return LocalTime.parse(officeProperties.getWorkStartTime());
    }

    public LocalTime getWorkEndTime() {
        return LocalTime.parse(officeProperties.getWorkEndTime());
    }

    public boolean canMarkLeaving(Employee employee) {
        LocalTime now = LocalTime.now(appClock);
        return !now.isBefore(getWorkEndTime()) || earlyLeaveService.hasApprovedRequestForToday(employee);
    }

    public boolean hasOpenAttendanceForToday(Employee employee) {
        if (employee == null) {
            return false;
        }

        return attendanceRepository.findByEmployeeAndWorkDate(employee, LocalDate.now(appClock))
                .map(attendance -> attendance.getArrivalTime() != null && attendance.getLeaveTime() == null)
                .orElse(false);
    }

    public String getLeavingNotAllowedMessage() {
        return getWorkEndTime() + " dan oldin ketish mumkin emas yoki erta ketish va uning sababini yuboring.";
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
