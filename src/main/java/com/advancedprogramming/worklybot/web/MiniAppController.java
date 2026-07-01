package com.advancedprogramming.worklybot.web;

import com.advancedprogramming.worklybot.bot.BotMessages;
import com.advancedprogramming.worklybot.config.BotProperties;
import com.advancedprogramming.worklybot.entity.AuditLog;
import com.advancedprogramming.worklybot.entity.Attendance;
import com.advancedprogramming.worklybot.entity.Employee;
import com.advancedprogramming.worklybot.entity.FeedbackResponse;
import com.advancedprogramming.worklybot.entity.Holiday;
import com.advancedprogramming.worklybot.entity.enums.CorrectionStatus;
import com.advancedprogramming.worklybot.entity.enums.Role;
import com.advancedprogramming.worklybot.entity.enums.Shift;
import com.advancedprogramming.worklybot.repository.AttendanceRepository;
import com.advancedprogramming.worklybot.repository.CorrectionRequestRepository;
import com.advancedprogramming.worklybot.repository.EarlyLeaveRequestRepository;
import com.advancedprogramming.worklybot.repository.EmployeeRepository;
import com.advancedprogramming.worklybot.repository.PendingRegistrationRepository;
import com.advancedprogramming.worklybot.repository.ProfileChangeRequestRepository;
import com.advancedprogramming.worklybot.service.AttendanceService;
import com.advancedprogramming.worklybot.service.AuditLogService;
import com.advancedprogramming.worklybot.service.AwardService;
import com.advancedprogramming.worklybot.service.ExcelReportService;
import com.advancedprogramming.worklybot.service.FeedbackService;
import com.advancedprogramming.worklybot.service.MonthlySalaryBreakdown;
import com.advancedprogramming.worklybot.service.SalaryDayRow;
import com.advancedprogramming.worklybot.service.SalaryService;
import com.advancedprogramming.worklybot.service.WorkCalendarService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
    private final ProfileChangeRequestRepository profileChangeRequestRepository;
    private final AttendanceService attendanceService;
    private final AuditLogService auditLogService;
    private final SalaryService salaryService;
    private final FeedbackService feedbackService;
    private final AwardService awardService;
    private final ExcelReportService excelReportService;
    private final WorkCalendarService workCalendarService;
    private final Clock appClock;

    @Value("${app.mini-app.dev-auth-enabled:true}")
    private boolean devAuthEnabled;

    @GetMapping("/me")
    public ResponseEntity<DashboardResponse> me(
            @RequestHeader(value = "X-Telegram-Init-Data", required = false) String initData,
            @RequestParam(value = "userId", required = false) Long devUserId,
            @RequestParam(value = "month", required = false) String month
    ) {
        Employee employee = resolveEmployee(initData, devUserId);
        LocalDate today = LocalDate.now(appClock);
        YearMonth selectedMonth = parseMonth(month);

        Attendance todayAttendance = attendanceRepository.findByEmployeeAndWorkDate(employee, today).orElse(null);
        List<AttendanceRow> history = attendanceRepository
                .findAllByEmployeeAndWorkDateBetweenOrderByWorkDateAsc(
                        employee,
                        selectedMonth.atDay(1),
                        selectedMonth.atEndOfMonth()
                )
                .stream()
                .map(this::toAttendanceRow)
                .toList();

        ManagerSummary managerSummary = null;
        List<EmployeeOption> employees = List.of();
        TodayReport todayReport = null;
        if (isManager(employee)) {
            managerSummary = new ManagerSummary(
                    employeeRepository.findAllByActiveTrue().size(),
                    pendingRegistrationRepository.findAllByOrderByCreatedAtAsc().size(),
                    correctionRequestRepository.findAllByStatus(CorrectionStatus.PENDING).size(),
                    earlyLeaveRequestRepository.findAllByStatus(CorrectionStatus.PENDING).size(),
                    profileChangeRequestRepository.countByStatus(CorrectionStatus.PENDING)
            );
            employees = employeeRepository.findAllByActiveTrueOrderByFullNameAsc()
                    .stream()
                    .map(this::toEmployeeOption)
                    .toList();
            todayReport = buildTodayReport(today, employees);
        }

        SalaryView salary = toSalaryView(salaryService.computeBreakdown(employee, selectedMonth));

        DashboardResponse response = new DashboardResponse(
                toEmployeeProfile(employee),
                today.toString(),
                todayAttendance == null ? null : toAttendanceRow(todayAttendance),
                history,
                managerSummary,
                employees,
                todayReport,
                salary
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

    @GetMapping("/employees/{telegramUserId}/salary")
    public ResponseEntity<SalaryView> employeeSalary(
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
        return ResponseEntity.ok(toSalaryView(salaryService.computeBreakdown(target, parseMonth(month))));
    }

    @GetMapping("/activities")
    public ResponseEntity<List<ActivityRow>> activities(
            @RequestHeader(value = "X-Telegram-Init-Data", required = false) String initData,
            @RequestParam(value = "userId", required = false) Long devUserId
    ) {
        Employee requester = resolveEmployee(initData, devUserId);
        if (!isAdmin(requester)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin access required.");
        }

        List<ActivityRow> rows = auditLogService.getRecentActivities()
                .stream()
                .map(this::toActivityRow)
                .toList();

        return ResponseEntity.ok(rows);
    }

    @GetMapping("/audit-log")
    public ResponseEntity<List<ActivityRow>> auditLog(
            @RequestHeader(value = "X-Telegram-Init-Data", required = false) String initData,
            @RequestParam(value = "userId", required = false) Long devUserId
    ) {
        Employee requester = resolveEmployee(initData, devUserId);
        if (!isAdmin(requester)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin access required.");
        }

        List<ActivityRow> rows = auditLogService.getRecentAuditLogs()
                .stream()
                .map(this::toActivityRow)
                .toList();
        return ResponseEntity.ok(rows);
    }

    @GetMapping("/feedbacks")
    public ResponseEntity<List<FeedbackRow>> feedbacks(
            @RequestHeader(value = "X-Telegram-Init-Data", required = false) String initData,
            @RequestParam(value = "userId", required = false) Long devUserId
    ) {
        Employee requester = resolveEmployee(initData, devUserId);
        if (!isAdmin(requester)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin access required.");
        }

        return ResponseEntity.ok(feedbackService.recentFeedbacks()
                .stream()
                .map(this::toFeedbackRow)
                .toList());
    }

    @GetMapping("/awards")
    public ResponseEntity<AwardsView> awards(
            @RequestHeader(value = "X-Telegram-Init-Data", required = false) String initData,
            @RequestParam(value = "userId", required = false) Long devUserId,
            @RequestParam(value = "month", required = false) String month
    ) {
        Employee requester = resolveEmployee(initData, devUserId);
        YearMonth selectedMonth = parseMonth(month);
        AwardService.MonthlyAwards awards = awardService.computeAwards(selectedMonth);
        return ResponseEntity.ok(toAwardsView(awards, selectedMonth, isManager(requester)));
    }

    @GetMapping("/report/excel")
    public ResponseEntity<byte[]> reportExcel(
            @RequestHeader(value = "X-Telegram-Init-Data", required = false) String initData,
            @RequestParam(value = "userId", required = false) Long devUserId,
            @RequestParam(value = "month", required = false) String month
    ) {
        Employee requester = resolveEmployee(initData, devUserId);
        if (!isManager(requester)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Manager or admin access required.");
        }
        YearMonth selectedMonth = parseMonth(month);
        byte[] bytes = excelReportService.buildAllEmployeesSalaryWorkbook(selectedMonth);
        return excelResponse(bytes, "workly-hisobot-" + selectedMonth + ".xlsx");
    }

    @GetMapping("/employees/{telegramUserId}/excel")
    public ResponseEntity<byte[]> employeeExcel(
            @PathVariable Long telegramUserId,
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
        byte[] bytes = excelReportService.buildEmployeeSalaryWorkbook(target, selectedMonth);
        return excelResponse(bytes, "workly-" + target.getTelegramUserId() + "-" + selectedMonth + ".xlsx");
    }

    @GetMapping("/holidays")
    public ResponseEntity<List<HolidayView>> holidays(
            @RequestHeader(value = "X-Telegram-Init-Data", required = false) String initData,
            @RequestParam(value = "userId", required = false) Long devUserId
    ) {
        Employee requester = resolveEmployee(initData, devUserId);
        if (!isManager(requester)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Manager or admin access required.");
        }
        return ResponseEntity.ok(workCalendarService.listHolidays().stream()
                .map(h -> new HolidayView(h.getDate().toString(), h.getDescription()))
                .toList());
    }

    @PostMapping("/holidays")
    public ResponseEntity<HolidayView> addHoliday(
            @RequestBody HolidayRequest body,
            @RequestHeader(value = "X-Telegram-Init-Data", required = false) String initData,
            @RequestParam(value = "userId", required = false) Long devUserId
    ) {
        Employee requester = resolveEmployee(initData, devUserId);
        if (!isAdmin(requester)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin access required.");
        }
        LocalDate date = parseIsoDate(body == null ? null : body.date());
        Holiday saved = workCalendarService.addHoliday(date, body == null ? null : body.description());
        return ResponseEntity.ok(new HolidayView(saved.getDate().toString(), saved.getDescription()));
    }

    @DeleteMapping("/holidays/{date}")
    public ResponseEntity<Void> deleteHoliday(
            @PathVariable String date,
            @RequestHeader(value = "X-Telegram-Init-Data", required = false) String initData,
            @RequestParam(value = "userId", required = false) Long devUserId
    ) {
        Employee requester = resolveEmployee(initData, devUserId);
        if (!isAdmin(requester)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin access required.");
        }
        workCalendarService.removeHoliday(parseIsoDate(date));
        return ResponseEntity.noContent().build();
    }

    private AwardsView toAwardsView(AwardService.MonthlyAwards awards, YearMonth month, boolean includeMostLate) {
        if (awards == null) {
            return new AwardsView(month.toString(), null, null, null);
        }
        return new AwardsView(
                awards.month().toString(),
                toAwardView(awards.hardestWorker()),
                toAwardView(awards.mostPunctual()),
                includeMostLate ? toAwardView(awards.mostLate()) : null
        );
    }

    private AwardView toAwardView(AwardService.Award award) {
        return award == null ? null : new AwardView(award.fullName(), award.department(), award.value());
    }

    private ResponseEntity<byte[]> excelResponse(byte[] bytes, String filename) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(bytes);
    }

    private LocalDate parseIsoDate(String value) {
        try {
            return LocalDate.parse(value);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Sana formati noto'g'ri (yyyy-MM-dd).");
        }
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

    private boolean isAdmin(Employee employee) {
        return employee.getRole() == Role.ADMIN;
    }

    private EmployeeProfile toEmployeeProfile(Employee employee) {
        return new EmployeeProfile(
                employee.getTelegramUserId(),
                employee.getFullName(),
                employee.getDepartment(),
                Shift.orDefault(employee.getShift()).getDisplayName(),
                employee.getRole().name()
        );
    }

    private SalaryView toSalaryView(MonthlySalaryBreakdown b) {
        java.time.format.DateTimeFormatter hm = java.time.format.DateTimeFormatter.ofPattern("HH:mm");
        List<SalaryDay> days = b.days().stream()
                .map(d -> new SalaryDay(
                        d.date().toString(),
                        d.arrival() == null ? null : d.arrival().format(hm),
                        d.leave() == null ? null : d.leave().format(hm),
                        d.workedMinutes(),
                        d.lateMinutes(),
                        d.deduction(),
                        d.warning()))
                .toList();
        return new SalaryView(
                b.month().toString(),
                b.baseSalary(),
                b.totalDeduction(),
                b.netSalary(),
                b.lateDays(),
                b.penalizedDays(),
                b.totalLateMinutes(),
                b.totalWorkedMinutes(),
                b.onTimeDays(),
                b.punctualityScore(),
                days
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

    private ActivityRow toActivityRow(AuditLog auditLog) {
        return new ActivityRow(
                auditLog.getCreatedAt().toString(),
                auditLog.getActorTelegramUserId(),
                auditLog.getActorName(),
                auditLog.getDetails()
        );
    }

    private FeedbackRow toFeedbackRow(FeedbackResponse feedback) {
        return new FeedbackRow(
                feedback.getCreatedAt().toString(),
                feedback.getFullName(),
                feedback.getDepartment(),
                feedback.getMessage()
        );
    }

    private TodayReport buildTodayReport(LocalDate today, List<EmployeeOption> employees) {
        Map<Long, Attendance> attendanceByEmployeeId = new HashMap<>();
        for (Attendance attendance : attendanceRepository.findAllByWorkDate(today)) {
            attendanceByEmployeeId.put(attendance.getEmployee().getId(), attendance);
        }

        long arrivedCount = 0;
        long absentCount = 0;
        long missingCheckoutCount = 0;
        long lateCount = 0;

        List<TodayReportRow> rows = employeeRepository.findAllByActiveTrueOrderByFullNameAsc()
                .stream()
                .map(employee -> {
                    Attendance attendance = attendanceByEmployeeId.get(employee.getId());
                    String arrival = null;
                    String leaving = null;
                    String status = BotMessages.STATUS_ABSENT;
                    String lateTime = "0 soat 0 daqiqa";

                    if (attendance != null) {
                        if (attendance.getArrivalTime() != null) {
                            arrival = attendance.getArrivalTime().toLocalTime().withNano(0).toString();
                        }

                        if (attendance.getLeaveTime() != null) {
                            leaving = attendance.getLeaveTime().toLocalTime().withNano(0).toString();
                        }

                        long lateMinutes = attendanceService.calculateLateMinutes(attendance);
                        status = attendanceService.calculateLateStatus(attendance);
                        lateTime = attendanceService.formatMinutesAsHours(lateMinutes);
                    }

                    return new TodayReportRow(
                            employee.getFullName(),
                            employee.getDepartment(),
                            arrival,
                            leaving,
                            lateTime,
                            status
                    );
                })
                .toList();

        for (TodayReportRow row : rows) {
            if (row.arrivalTime() == null) {
                absentCount++;
            } else {
                arrivedCount++;
            }

            if (row.arrivalTime() != null && row.leaveTime() == null) {
                missingCheckoutCount++;
            }

            if (!"0 soat 0 daqiqa".equals(row.lateTime())) {
                lateCount++;
            }
        }

        return new TodayReport(
                today.toString(),
                employees.size(),
                arrivedCount,
                absentCount,
                missingCheckoutCount,
                lateCount,
                rows
        );
    }

    public record DashboardResponse(
            EmployeeProfile employee,
            String todayDate,
            AttendanceRow today,
            List<AttendanceRow> monthHistory,
            ManagerSummary managerSummary,
            List<EmployeeOption> employees,
            TodayReport todayReport,
            SalaryView salary
    ) {
    }

    public record EmployeeProfile(Long telegramUserId, String fullName, String department, String shift, String role) {
    }

    public record SalaryView(
            String month,
            long baseSalary,
            long totalDeduction,
            long netSalary,
            int lateDays,
            int penalizedDays,
            long totalLateMinutes,
            long totalWorkedMinutes,
            int onTimeDays,
            int punctualityScore,
            List<SalaryDay> days
    ) {
    }

    public record SalaryDay(
            String date,
            String arrival,
            String leave,
            long workedMinutes,
            long lateMinutes,
            long deduction,
            boolean warning
    ) {
    }

    public record EmployeeOption(Long telegramUserId, String fullName, String department, String role) {
    }

    public record ManagerSummary(int activeEmployees, int pendingRegistrations, int pendingCorrections, int pendingEarlyLeaves,
                                 long pendingProfileChanges) {
    }

    public record AttendanceRow(String date, String arrivalTime, String leaveTime, String workedTime, String lateTime, String status) {
    }

    public record ActivityRow(String createdAt, Long actorTelegramUserId, String actorName, String details) {
    }

    public record FeedbackRow(String createdAt, String fullName, String department, String message) {
    }

    public record AwardsView(String month, AwardView hardestWorker, AwardView mostPunctual, AwardView mostLate) {
    }

    public record AwardView(String fullName, String department, long value) {
    }

    public record HolidayView(String date, String description) {
    }

    public record HolidayRequest(String date, String description) {
    }

    public record TodayReport(
            String date,
            long activeEmployees,
            long arrived,
            long absent,
            long missingCheckout,
            long late,
            List<TodayReportRow> rows
    ) {
    }

    public record TodayReportRow(
            String fullName,
            String department,
            String arrivalTime,
            String leaveTime,
            String lateTime,
            String status
    ) {
    }
}