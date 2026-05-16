package com.advancedprogramming.worklybot.web;

import com.advancedprogramming.worklybot.config.BotProperties;
import com.advancedprogramming.worklybot.entity.Attendance;
import com.advancedprogramming.worklybot.entity.Employee;
import com.advancedprogramming.worklybot.entity.enums.CorrectionStatus;
import com.advancedprogramming.worklybot.entity.enums.Role;
import com.advancedprogramming.worklybot.repository.AttendanceRepository;
import com.advancedprogramming.worklybot.repository.CorrectionRequestRepository;
import com.advancedprogramming.worklybot.repository.EarlyLeaveRequestRepository;
import com.advancedprogramming.worklybot.repository.EmployeeRepository;
import com.advancedprogramming.worklybot.repository.PendingRegistrationRepository;
import com.advancedprogramming.worklybot.service.AttendanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/app")
@RequiredArgsConstructor
public class MiniAppController {

    private final BotProperties botProperties;
    private final EmployeeRepository employeeRepository;
    private final AttendanceRepository attendanceRepository;
    private final CorrectionRequestRepository correctionRequestRepository;
    private final EarlyLeaveRequestRepository earlyLeaveRequestRepository;
    private final PendingRegistrationRepository pendingRegistrationRepository;
    private final AttendanceService attendanceService;
    private final Clock appClock;

    @Value("${app.mini-app.dev-auth-enabled:true}")
    private boolean devAuthEnabled;

    @GetMapping("/me")
    public ResponseEntity<DashboardResponse> me(
            @RequestHeader(value = "X-Telegram-Init-Data", required = false) String initData,
            @RequestParam(value = "userId", required = false) Long devUserId
    ) {
        Employee employee = resolveEmployee(initData, devUserId);
        LocalDate today = LocalDate.now(appClock);
        YearMonth currentMonth = YearMonth.now(appClock);

        Attendance todayAttendance = attendanceRepository.findByEmployeeAndWorkDate(employee, today).orElse(null);
        List<AttendanceRow> history = attendanceRepository
                .findAllByEmployeeAndWorkDateBetweenOrderByWorkDateAsc(
                        employee,
                        currentMonth.atDay(1),
                        currentMonth.atEndOfMonth()
                )
                .stream()
                .map(this::toAttendanceRow)
                .toList();

        ManagerSummary managerSummary = null;
        List<EmployeeOption> employees = List.of();
        if (isManager(employee)) {
            managerSummary = new ManagerSummary(
                    employeeRepository.findAllByActiveTrue().size(),
                    pendingRegistrationRepository.findAllByOrderByCreatedAtAsc().size(),
                    correctionRequestRepository.findAllByStatus(CorrectionStatus.PENDING).size(),
                    earlyLeaveRequestRepository.findAllByStatus(CorrectionStatus.PENDING).size()
            );
            employees = employeeRepository.findAllByActiveTrueOrderByFullNameAsc()
                    .stream()
                    .map(this::toEmployeeOption)
                    .toList();
        }

        DashboardResponse response = new DashboardResponse(
                toEmployeeProfile(employee),
                todayAttendance == null ? null : toAttendanceRow(todayAttendance),
                history,
                managerSummary,
                employees
        );
        return ResponseEntity.ok(response);
    }

    @GetMapping("/employees/{telegramUserId}/history")
    public ResponseEntity<List<AttendanceRow>> employeeHistory(
            @org.springframework.web.bind.annotation.PathVariable Long telegramUserId,
            @RequestHeader(value = "X-Telegram-Init-Data", required = false) String initData,
            @RequestParam(value = "userId", required = false) Long devUserId,
            @RequestParam(value = "month", required = false) String month
    ) {
        Employee requester = resolveEmployee(initData, devUserId);
        if (!isManager(requester)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Manager or admin access required.");
        }

        Employee target = employeeRepository.findByTelegramUserId(telegramUserId)
                .filter(Employee::isActive)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Employee not found."));

        YearMonth selectedMonth = parseMonth(month);
        List<AttendanceRow> rows = attendanceRepository
                .findAllByEmployeeAndWorkDateBetweenOrderByWorkDateAsc(
                        target,
                        selectedMonth.atDay(1),
                        selectedMonth.atEndOfMonth()
                )
                .stream()
                .map(this::toAttendanceRow)
                .toList();

        return ResponseEntity.ok(rows);
    }

    private Employee resolveEmployee(String initData, Long devUserId) {
        Long telegramUserId = extractTelegramUserId(initData)
                .or(() -> devAuthEnabled ? Optional.ofNullable(devUserId) : Optional.empty())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Telegram identity is required."));

        return employeeRepository.findByTelegramUserId(telegramUserId)
                .filter(Employee::isActive)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Active employee not found."));
    }

    private Optional<Long> extractTelegramUserId(String initData) {
        if (initData == null || initData.isBlank() || !isValidInitData(initData)) {
            return Optional.empty();
        }

        Map<String, String> params = parseQuery(initData);
        String userJson = params.get("user");
        if (userJson == null) {
            return Optional.empty();
        }

        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("\"id\"\\s*:\\s*(\\d+)")
                .matcher(userJson);
        if (!matcher.find()) {
            return Optional.empty();
        }

        return Optional.of(Long.parseLong(matcher.group(1)));
    }

    private boolean isValidInitData(String initData) {
        Map<String, String> params = parseQuery(initData);
        String receivedHash = params.remove("hash");
        if (receivedHash == null || receivedHash.isBlank()) {
            return false;
        }

        String dataCheckString = params.entrySet()
                .stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining("\n"));

        try {
            byte[] secretKey = hmacSha256("WebAppData".getBytes(StandardCharsets.UTF_8), botProperties.getToken());
            String calculatedHash = HexFormat.of().formatHex(hmacSha256(secretKey, dataCheckString));
            return MessageDigest.isEqual(
                    calculatedHash.getBytes(StandardCharsets.UTF_8),
                    receivedHash.getBytes(StandardCharsets.UTF_8)
            );
        } catch (Exception exception) {
            return false;
        }
    }

    private byte[] hmacSha256(byte[] key, String data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
    }

    private Map<String, String> parseQuery(String query) {
        Map<String, String> params = new HashMap<>();
        for (String pair : query.split("&")) {
            int equalsIndex = pair.indexOf('=');
            if (equalsIndex <= 0) {
                continue;
            }

            String key = URLDecoder.decode(pair.substring(0, equalsIndex), StandardCharsets.UTF_8);
            String value = URLDecoder.decode(pair.substring(equalsIndex + 1), StandardCharsets.UTF_8);
            params.put(key, value);
        }
        return params;
    }

    private YearMonth parseMonth(String month) {
        if (month == null || month.isBlank()) {
            return YearMonth.now(appClock);
        }
        try {
            return YearMonth.parse(month);
        } catch (RuntimeException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Month must use yyyy-MM format.");
        }
    }

    private boolean isManager(Employee employee) {
        return employee.getRole() == Role.MANAGER || employee.getRole() == Role.ADMIN;
    }

    private EmployeeProfile toEmployeeProfile(Employee employee) {
        return new EmployeeProfile(
                employee.getTelegramUserId(),
                employee.getFullName(),
                employee.getDepartment(),
                employee.getRole().name()
        );
    }

    private EmployeeOption toEmployeeOption(Employee employee) {
        return new EmployeeOption(
                employee.getTelegramUserId(),
                employee.getFullName(),
                employee.getDepartment(),
                employee.getRole().name()
        );
    }

    private AttendanceRow toAttendanceRow(Attendance attendance) {
        return new AttendanceRow(
                attendance.getWorkDate().toString(),
                attendance.getArrivalTime() == null ? null : attendance.getArrivalTime().toLocalTime().withNano(0).toString(),
                attendance.getLeaveTime() == null ? null : attendance.getLeaveTime().toLocalTime().withNano(0).toString(),
                attendanceService.calculateWorkedHours(attendance),
                attendanceService.formatMinutesAsHours(attendanceService.calculateLateMinutes(attendance)),
                attendanceService.calculateLateStatus(attendance)
        );
    }

    public record DashboardResponse(
            EmployeeProfile employee,
            AttendanceRow today,
            List<AttendanceRow> monthHistory,
            ManagerSummary managerSummary,
            List<EmployeeOption> employees
    ) {
    }

    public record EmployeeProfile(Long telegramUserId, String fullName, String department, String role) {
    }

    public record EmployeeOption(Long telegramUserId, String fullName, String department, String role) {
    }

    public record ManagerSummary(int activeEmployees, int pendingRegistrations, int pendingCorrections, int pendingEarlyLeaves) {
    }

    public record AttendanceRow(String date, String arrivalTime, String leaveTime, String workedTime, String lateTime, String status) {
    }
}
