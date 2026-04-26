package com.advancedprogramming.worklybot.bot;

import com.advancedprogramming.worklybot.bot.state.UserSession;
import com.advancedprogramming.worklybot.bot.state.UserState;
import com.advancedprogramming.worklybot.config.BotProperties;
import com.advancedprogramming.worklybot.entity.Employee;
import com.advancedprogramming.worklybot.entity.PendingRegistration;
import com.advancedprogramming.worklybot.entity.enums.Role;
import com.advancedprogramming.worklybot.repository.EmployeeRepository;
import com.advancedprogramming.worklybot.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.longpolling.interfaces.LongPollingUpdateConsumer;
import org.telegram.telegrambots.longpolling.starter.SpringLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.location.Location;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import org.telegram.telegrambots.meta.api.methods.send.SendDocument;
import org.telegram.telegrambots.meta.api.objects.InputFile;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
@RequiredArgsConstructor
public class WorklyTelegramBot implements SpringLongPollingBot {

    private final BotProperties botProperties;
    private final EmployeeRepository employeeRepository;
    private final AttendanceService attendanceService;
    private final CorrectionService correctionService;
    private final EarlyLeaveService earlyLeaveService;
    private final ReportService reportService;
    private final EmployeeService employeeService;
    private final PendingRegistrationService pendingRegistrationService;
    private final RoleService roleService;
    private final AuditLogService auditLogService;

    private final Map<Long, UserSession> sessions = new ConcurrentHashMap<>();

    @Override
    public String getBotToken() {
        return botProperties.getToken();
    }

    @Override
    public LongPollingUpdateConsumer getUpdatesConsumer() {
        TelegramClient telegramClient = new OkHttpTelegramClient(getBotToken());

        return updates -> {
            for (Update update : updates) {
                if (update.hasMessage()) {
                    if (update.getMessage().hasText()) {
                        handleMessage(update, telegramClient);
                    } else if (update.getMessage().hasLocation()) {
                        handleLocation(update, telegramClient);
                    }
                }
            }
        };
    }

    private void handleMessage(Update update, TelegramClient telegramClient) {
        Long telegramUserId = update.getMessage().getFrom().getId();
        Long chatId = update.getMessage().getChatId();
        String text = update.getMessage().getText().trim();

        UserSession session = sessions.computeIfAbsent(telegramUserId, id -> new UserSession());

        if (text.equals("/start")) {
            resetSession(session);
            handleStart(telegramUserId, chatId, session, telegramClient);
            return;
        }

        if (text.equals("/cancel")) {
            resetSession(session);

            Employee employee = employeeRepository.findByTelegramUserId(telegramUserId).orElse(null);
            if (employee != null && employee.isActive()) {
                sendMainMenu(employee, chatId, telegramClient, "Amal bekor qilindi.");
            } else {
                sendPlainMessage(chatId, "Amal bekor qilindi.", telegramClient);
            }
            return;
        }

        if (session.getState() != UserState.NONE && isMenuOrManagerCommand(text)) {
            resetSession(session);
        }

        if (session.getState() == UserState.WAITING_FULL_NAME) {
            if (text.isBlank() || text.length() < 3) {
                sendPlainMessage(chatId, BotMessages.INVALID_FULL_NAME, telegramClient);
                return;
            }

            session.setFullName(text);
            session.setState(UserState.WAITING_DEPARTMENT);
            sendPlainMessage(chatId, BotMessages.ENTER_DEPARTMENT, telegramClient);
            return;
        }

        if (session.getState() == UserState.WAITING_DEPARTMENT) {
            if (text.isBlank() || text.length() < 2) {
                sendPlainMessage(chatId, BotMessages.INVALID_DEPARTMENT, telegramClient);
                return;
            }

            registerEmployee(telegramUserId, chatId, session.getFullName(), text, telegramClient);
            resetSession(session);
            return;
        }

        Employee employee = employeeRepository.findByTelegramUserId(telegramUserId).orElse(null);
        PendingRegistration pendingRegistration = pendingRegistrationService.findByTelegramUserId(telegramUserId);

        if (session.getState() == UserState.WAITING_CORRECTION_DATE) {
            try {
                LocalDate date = LocalDate.parse(text, DateTimeFormatter.ofPattern("yyyy-MM-dd"));
                if (date.isAfter(LocalDate.now())) {
                    sendPlainMessage(
                            chatId,
                            "Kelajak sana uchun tuzatish so'rovi yuborib bo'lmaydi.\nBoshqa sanani yyyy-MM-dd formatida kiriting yoki /cancel yuboring.",
                            telegramClient
                    );
                    return;
                }

                session.setCorrectionDate(date);
                session.setState(UserState.WAITING_CORRECTION_TYPE);
                sendPlainMessage(chatId, BotMessages.CORRECTION_TYPE, telegramClient);
            } catch (DateTimeParseException e) {
                sendPlainMessage(chatId, BotMessages.INVALID_DATE, telegramClient);
            }
            return;
        }

        if (session.getState() == UserState.WAITING_CORRECTION_TYPE) {
            if (!text.equals("1") && !text.equals("2") && !text.equals("3")) {
                sendPlainMessage(chatId, BotMessages.INVALID_CORRECTION_TYPE, telegramClient);
                return;
            }

            session.setCorrectionType(text);
            session.setState(UserState.WAITING_CORRECTION_TIME);

            if (text.equals("1")) {
                sendPlainMessage(chatId, BotMessages.ENTER_ARRIVAL_TIME, telegramClient);
            } else if (text.equals("2")) {
                sendPlainMessage(chatId, BotMessages.ENTER_LEAVING_TIME, telegramClient);
            } else {
                sendPlainMessage(chatId, BotMessages.ENTER_BOTH_TIMES, telegramClient);
            }
            return;
        }

        if (session.getState() == UserState.WAITING_CORRECTION_TIME) {
            if (employee == null) {
                sendPlainMessage(chatId, BotMessages.NOT_REGISTERED, telegramClient);
                return;
            }

            String message = correctionService.createCorrectionRequest(employee, session, text);
            if (message.startsWith("Tuzatish so'rovi yuborildi")) {
                notifyManagersAboutCorrectionRequest(employee, session.getCorrectionDate(), telegramClient);
                resetSession(session);
                sendMainMenu(employee, chatId, telegramClient, message);
            } else {
                if (message.equals("Kelajak sana uchun tuzatish so'rovi yuborib bo'lmaydi.")
                        || message.equals("Avval tuzatiladigan sanani kiriting.")) {
                    session.setCorrectionDate(null);
                    session.setCorrectionType(null);
                    session.setState(UserState.WAITING_CORRECTION_DATE);
                    message += "\nBoshqa sanani yyyy-MM-dd formatida kiriting yoki /cancel yuboring.";
                }
                sendPlainMessage(chatId, message, telegramClient);
            }
            return;
        }

        if (session.getState() == UserState.WAITING_EARLY_LEAVE_REASON) {
            if (employee == null) {
                sendPlainMessage(chatId, BotMessages.NOT_REGISTERED, telegramClient);
                return;
            }

            if (text.isBlank() || text.trim().length() < 5) {
                sendPlainMessage(chatId, BotMessages.INVALID_EARLY_LEAVE_REASON, telegramClient);
                return;
            }

            String message = earlyLeaveService.createRequest(employee, text);
            if (message.startsWith("Erta ketish so'rovi menejerga yuborildi")) {
                notifyManagersAboutEarlyLeaveRequest(employee, telegramClient);
                resetSession(session);
                sendMainMenu(employee, chatId, telegramClient, message);
            } else {
                sendPlainMessage(chatId, message, telegramClient);
            }
            return;
        }

        if (employee == null) {
            if (pendingRegistration != null) {
                sendPlainMessage(chatId, BotMessages.REGISTRATION_ALREADY_PENDING, telegramClient);
                return;
            }
            sendPlainMessage(chatId, BotMessages.NOT_REGISTERED, telegramClient);
            return;
        }

        if (!employee.isActive()) {
            sendPlainMessage(chatId, BotMessages.ACCOUNT_INACTIVE, telegramClient);
            return;
        }

        if (employee.getRole() == Role.MANAGER || employee.getRole() == Role.ADMIN) {

            if (text.equals(BotMessages.CMD_TODAY_REPORT)) {
                sendPlainMessage(chatId, reportService.buildTodayReport(), telegramClient);
                return;
            }

            if (text.equals(BotMessages.CMD_MONTH_REPORT)) {
                sendPlainMessage(chatId, reportService.buildMonthReport(), telegramClient);
                return;
            }

            if (text.equals(BotMessages.CMD_MONTH_EXCEL)) {
                try {
                    byte[] fileBytes = reportService.buildMonthExcelReport();
                    if (fileBytes == null) {
                        sendPlainMessage(chatId, BotMessages.EXCEL_EMPTY, telegramClient);
                    } else {
                        sendExcel(chatId, fileBytes, telegramClient);
                    }
                } catch (RuntimeException exception) {
                    log.error("Failed to prepare month Excel report for chat {}", chatId, exception);
                    sendPlainMessage(chatId, BotMessages.EXCEL_SEND_ERROR, telegramClient);
                }
                return;
            }

            if (text.equals(BotMessages.CMD_PENDING_CORRECTIONS)) {
                sendPlainMessage(chatId, correctionService.getPendingCorrectionsText(), telegramClient);
                return;
            }

            if (text.equals(BotMessages.CMD_PENDING_EARLY_LEAVES)) {
                sendPlainMessage(chatId, earlyLeaveService.getPendingRequestsText(), telegramClient);
                return;
            }

            if (text.equals(BotMessages.CMD_PENDING_REGISTRATIONS)) {
                sendPlainMessage(chatId, pendingRegistrationService.getPendingRegistrationsText(), telegramClient);
                return;
            }

            if (text.equals(BotMessages.CMD_EMPLOYEES)) {
                sendPlainMessage(chatId, employeeService.getAllEmployeesText(), telegramClient);
                return;
            }

            if (text.equals(BotMessages.CMD_AUDIT_LOG)) {
                sendPlainMessage(chatId, auditLogService.getRecentActivityText(), telegramClient);
                return;
            }

            if (text.startsWith("/activate_")) {
                try {
                    Long targetId = Long.parseLong(text.replace("/activate_", ""));
                    PendingRegistration pendingRegistrationToApprove =
                            pendingRegistrationService.findByTelegramUserId(targetId);

                    if (pendingRegistrationToApprove != null) {
                        PendingRegistrationActionResult pendingResult = pendingRegistrationService
                                .approvePendingRegistration(employee.getTelegramUserId(), targetId);
                        sendPlainMessage(chatId, pendingResult.getManagerMessage(), telegramClient);

                        if (pendingResult.getChatId() != null && pendingResult.getEmployeeMessage() != null) {
                            sendPlainMessage(pendingResult.getChatId(), pendingResult.getEmployeeMessage(), telegramClient);
                        }
                    } else {
                        String result = employeeService.activateEmployee(employee.getTelegramUserId(), targetId);
                        sendPlainMessage(chatId, result, telegramClient);
                        notifyTargetEmployee(
                                targetId,
                                "Akkauntingiz faollashtirildi. Endi Workly botdan foydalanishingiz mumkin.",
                                result.startsWith("Xodim faollashtirildi"),
                                telegramClient
                        );
                    }
                } catch (Exception e) {
                    sendPlainMessage(chatId, BotMessages.INVALID_ACTIVATE_COMMAND, telegramClient);
                }
                return;
            }

            if (text.startsWith("/deactivate_pending_")) {
                try {
                    Long targetId = Long.parseLong(text.replace("/deactivate_pending_", ""));
                    PendingRegistrationActionResult result = pendingRegistrationService
                            .rejectPendingRegistration(employee.getTelegramUserId(), targetId);
                    sendPlainMessage(chatId, result.getManagerMessage(), telegramClient);

                    if (result.getChatId() != null && result.getEmployeeMessage() != null) {
                        sendPlainMessage(result.getChatId(), result.getEmployeeMessage(), telegramClient);
                    }
                } catch (Exception e) {
                    sendPlainMessage(chatId, BotMessages.INVALID_PENDING_DEACTIVATE_COMMAND, telegramClient);
                }
                return;
            }

            if (text.startsWith("/deactivate_")) {
                try {
                    Long targetId = Long.parseLong(text.replace("/deactivate_", ""));
                    String result = employeeService.deactivateEmployee(employee.getTelegramUserId(), targetId);
                    sendPlainMessage(chatId, result, telegramClient);
                    notifyTargetEmployee(
                            targetId,
                            "Akkauntingiz vaqtincha nofaol qilindi. Batafsil ma'lumot uchun menejer bilan bog'laning.",
                            result.startsWith("Xodim o'chirildi"),
                            telegramClient
                    );
                } catch (Exception e) {
                    sendPlainMessage(chatId, BotMessages.INVALID_DEACTIVATE_COMMAND, telegramClient);
                }
                return;
            }

            if (text.startsWith("/make_manager_")) {
                try {
                    Long targetId = Long.parseLong(text.replace("/make_manager_", ""));
                    String result = roleService.makeManager(employee.getTelegramUserId(), targetId);
                    sendPlainMessage(chatId, result, telegramClient);
                    notifyTargetEmployee(
                            targetId,
                            "Sizga MANAGER roli berildi.",
                            result.endsWith("endi MANAGER."),
                            telegramClient
                    );
                } catch (Exception e) {
                    sendPlainMessage(chatId, BotMessages.INVALID_MAKE_MANAGER_COMMAND, telegramClient);
                }
                return;
            }

            if (text.startsWith("/make_employee_")) {
                try {
                    Long targetId = Long.parseLong(text.replace("/make_employee_", ""));
                    String result = roleService.makeEmployee(employee.getTelegramUserId(), targetId);
                    sendPlainMessage(chatId, result, telegramClient);
                    notifyTargetEmployee(
                            targetId,
                            "Sizning rolingiz EMPLOYEE ga o'zgartirildi.",
                            result.endsWith("endi EMPLOYEE."),
                            telegramClient
                    );
                } catch (Exception e) {
                    sendPlainMessage(chatId, BotMessages.INVALID_MAKE_EMPLOYEE_COMMAND, telegramClient);
                }
                return;
            }

            if (text.startsWith("/make_admin_")) {
                try {
                    Long targetId = Long.parseLong(text.replace("/make_admin_", ""));
                    String result = roleService.makeAdmin(employee.getTelegramUserId(), targetId);
                    sendPlainMessage(chatId, result, telegramClient);
                    notifyTargetEmployee(
                            targetId,
                            "Sizga ADMIN roli berildi.",
                            result.endsWith("endi ADMIN."),
                            telegramClient
                    );
                } catch (Exception e) {
                    sendPlainMessage(chatId, BotMessages.INVALID_MAKE_ADMIN_COMMAND, telegramClient);
                }
                return;
            }

            if (text.startsWith("/approve_early_")) {
                try {
                    Long requestId = Long.parseLong(text.replace("/approve_early_", ""));
                    CorrectionActionResult result = earlyLeaveService.approve(requestId, employee.getTelegramUserId());
                    sendPlainMessage(chatId, result.getManagerMessage(), telegramClient);

                    if (result.getEmployeeChatId() != null && result.getEmployeeMessage() != null) {
                        sendPlainMessage(result.getEmployeeChatId(), result.getEmployeeMessage(), telegramClient);
                    }
                } catch (Exception e) {
                    sendPlainMessage(chatId, "Erta ketish tasdiqlash buyrug'i noto'g'ri.", telegramClient);
                }
                return;
            }

            if (text.startsWith("/reject_early_")) {
                try {
                    Long requestId = Long.parseLong(text.replace("/reject_early_", ""));
                    CorrectionActionResult result = earlyLeaveService.reject(requestId, employee.getTelegramUserId());
                    sendPlainMessage(chatId, result.getManagerMessage(), telegramClient);

                    if (result.getEmployeeChatId() != null && result.getEmployeeMessage() != null) {
                        sendPlainMessage(result.getEmployeeChatId(), result.getEmployeeMessage(), telegramClient);
                    }
                } catch (Exception e) {
                    sendPlainMessage(chatId, "Erta ketish rad etish buyrug'i noto'g'ri.", telegramClient);
                }
                return;
            }

            if (text.startsWith("/approve_")) {
                try {
                    Long requestId = Long.parseLong(text.replace("/approve_", ""));
                    CorrectionActionResult result = correctionService.approveCorrection(requestId, employee.getTelegramUserId());
                    sendPlainMessage(chatId, result.getManagerMessage(), telegramClient);

                    if (result.getEmployeeChatId() != null && result.getEmployeeMessage() != null) {
                        sendPlainMessage(result.getEmployeeChatId(), result.getEmployeeMessage(), telegramClient);
                    }
                } catch (Exception e) {
                    sendPlainMessage(chatId, BotMessages.INVALID_APPROVE_COMMAND, telegramClient);
                }
                return;
            }

            if (text.startsWith("/reject_")) {
                try {
                    Long requestId = Long.parseLong(text.replace("/reject_", ""));
                    CorrectionActionResult result = correctionService.rejectCorrection(requestId, employee.getTelegramUserId());
                    sendPlainMessage(chatId, result.getManagerMessage(), telegramClient);

                    if (result.getEmployeeChatId() != null && result.getEmployeeMessage() != null) {
                        sendPlainMessage(result.getEmployeeChatId(), result.getEmployeeMessage(), telegramClient);
                    }
                } catch (Exception e) {
                    sendPlainMessage(chatId, BotMessages.INVALID_REJECT_COMMAND, telegramClient);
                }
                return;
            }
        }

        switch (text) {
            case BotMessages.BUTTON_ARRIVED -> {
                session.setState(UserState.WAITING_ARRIVAL_LOCATION);
                requestLocation(chatId, BotMessages.SHARE_LOCATION_FOR_ARRIVAL, telegramClient);
                return;
            }
            case BotMessages.BUTTON_LEFT_WORK -> {
                if (!attendanceService.canMarkLeaving(employee)) {
                    sendMainMenu(employee, chatId, telegramClient, attendanceService.getLeavingNotAllowedMessage());
                    return;
                }
                session.setState(UserState.WAITING_LEAVING_LOCATION);
                requestLocation(chatId, BotMessages.SHARE_LOCATION_FOR_LEAVING, telegramClient);
                return;
            }
            case BotMessages.BUTTON_STATUS -> {
                String statusMessage = attendanceService.getTodayStatus(employee);
                sendMainMenu(employee, chatId, telegramClient, statusMessage);
                return;
            }
            case BotMessages.BUTTON_FIX_MISTAKE -> {
                session.setState(UserState.WAITING_CORRECTION_DATE);
                sendPlainMessage(chatId, BotMessages.ENTER_CORRECTION_DATE, telegramClient);
                return;
            }
            case BotMessages.BUTTON_EARLY_LEAVE -> {
                session.setState(UserState.WAITING_EARLY_LEAVE_REASON);
                sendPlainMessage(chatId, BotMessages.ENTER_EARLY_LEAVE_REASON, telegramClient);
                return;
            }
            default -> sendMainMenu(employee, chatId, telegramClient, BotMessages.CHOOSE_ACTION);
        }
    }

    private void handleLocation(Update update, TelegramClient telegramClient) {
        Long telegramUserId = update.getMessage().getFrom().getId();
        Long chatId = update.getMessage().getChatId();
        Location location = update.getMessage().getLocation();

        if (update.getMessage().getForwardOrigin() != null) {
            sendPlainMessage(
                    chatId,
                    "Forward qilingan joylashuv qabul qilinmaydi. O'zingizning joylashuvingizni yuboring.",
                    telegramClient
            );
            return;
        }

        Integer livePeriod = location.getLivePeriod();
        if (livePeriod == null) {
            sendPlainMessage(
                    chatId,
                    "Oddiy yoki qo'lda tanlangan joylashuv qabul qilinmaydi.\n\n\"Share my live location\" orqali LIVE joylashuvingizni yuboring.",
                    telegramClient
            );
            return;
        }

        UserSession session = sessions.computeIfAbsent(telegramUserId, id -> new UserSession());
        Employee employee = employeeRepository.findByTelegramUserId(telegramUserId).orElse(null);

        if (employee == null) {
            sendPlainMessage(chatId, BotMessages.NOT_REGISTERED, telegramClient);
            return;
        }

        if (!employee.isActive()) {
            sendPlainMessage(chatId, BotMessages.ACCOUNT_INACTIVE, telegramClient);
            return;
        }

        double userLat = location.getLatitude();
        double userLon = location.getLongitude();

        double distance = attendanceService.calculateDistanceMeters(
                attendanceService.getOfficeLatitude(),
                attendanceService.getOfficeLongitude(),
                userLat,
                userLon
        );

        if (distance > attendanceService.getAllowedRadiusMeters()) {
            session.setState(UserState.NONE);
            sendMainMenu(
                    employee,
                    chatId,
                    telegramClient,
                    BotMessages.OUTSIDE_OFFICE + formatDistance(distance)
            );
            return;
        }

        if (session.getState() == UserState.WAITING_ARRIVAL_LOCATION) {
            session.setState(UserState.NONE);
            String message = attendanceService.saveArrivalAfterLocationCheck(employee);
            sendMainMenu(employee, chatId, telegramClient, message);
            return;
        }

        if (session.getState() == UserState.WAITING_LEAVING_LOCATION) {
            session.setState(UserState.NONE);
            String message = attendanceService.saveLeavingAfterLocationCheck(employee);
            sendMainMenu(employee, chatId, telegramClient, message);
            return;
        }

        if (earlyLeaveService.hasApprovedRequestForToday(employee) && attendanceService.hasOpenAttendanceForToday(employee)) {
            session.setState(UserState.NONE);
            String message = attendanceService.saveLeavingAfterLocationCheck(employee);
            sendMainMenu(employee, chatId, telegramClient, message);
            return;
        }

        sendMainMenu(employee, chatId, telegramClient, BotMessages.NO_PENDING_LOCATION_ACTION);
    }

    private void handleStart(Long telegramUserId, Long chatId, UserSession session, TelegramClient telegramClient) {
        Employee employee = employeeRepository.findByTelegramUserId(telegramUserId).orElse(null);
        PendingRegistration pendingRegistration = pendingRegistrationService.findByTelegramUserId(telegramUserId);

        if (employee != null) {
            if (employee.isActive()) {
                sendMainMenu(employee, chatId, telegramClient, BotMessages.ALREADY_REGISTERED);
            } else {
                sendPlainMessage(chatId, BotMessages.ACCOUNT_INACTIVE, telegramClient);
            }
        } else if (pendingRegistration != null) {
            sendPlainMessage(chatId, BotMessages.REGISTRATION_ALREADY_PENDING, telegramClient);
        } else {
            session.setState(UserState.WAITING_FULL_NAME);
            sendPlainMessage(chatId, BotMessages.WELCOME_ENTER_FULL_NAME, telegramClient);
        }
    }

    private void registerEmployee(Long telegramUserId, Long chatId, String fullName, String department, TelegramClient telegramClient) {
        boolean bootstrapAdmin = botProperties.isAdminUser(telegramUserId);

        String registrationMessage = BotMessages.REGISTRATION_SUCCESS + "\n"
                + BotMessages.NAME_LABEL + fullName + "\n"
                + BotMessages.DEPARTMENT_LABEL + department;

        if (bootstrapAdmin) {
            Employee employee = Employee.builder()
                    .telegramUserId(telegramUserId)
                    .chatId(chatId)
                    .fullName(fullName)
                    .department(department)
                    .role(Role.ADMIN)
                    .active(true)
                    .build();

            employeeRepository.save(employee);
            sendMainMenu(
                    employee,
                    chatId,
                    telegramClient,
                    registrationMessage + "\n" + BotMessages.REGISTRATION_ADMIN_BOOTSTRAPPED
            );
            return;
        }

        PendingRegistration pendingRegistration = pendingRegistrationService.createPendingRegistration(
                telegramUserId,
                chatId,
                fullName,
                department
        );
        sendPlainMessage(chatId, registrationMessage + "\n" + BotMessages.REGISTRATION_PENDING_APPROVAL, telegramClient);
        notifyManagersAboutPendingRegistration(pendingRegistration, telegramClient);
    }

    private void requestLocation(Long chatId, String text, TelegramClient telegramClient) {
        KeyboardButton locationButton = new KeyboardButton(BotMessages.LOCATION_BUTTON);
        locationButton.setRequestLocation(true);

        KeyboardRow row = new KeyboardRow();
        row.add(locationButton);

        List<KeyboardRow> keyboard = new ArrayList<>();
        keyboard.add(row);

        ReplyKeyboardMarkup markup = new ReplyKeyboardMarkup(
                keyboard,
                true,
                true,
                false,
                null,
                false
        );

        try {
            telegramClient.execute(
                    SendMessage.builder()
                            .chatId(chatId.toString())
                            .text(text)
                            .replyMarkup(markup)
                            .build()
            );
        } catch (Exception e) {
            log.error("Failed to request location from chat {}", chatId, e);
        }
    }

    private void sendExcel(Long chatId, byte[] fileBytes, TelegramClient telegramClient) {
        try {
            InputStream inputStream = new ByteArrayInputStream(fileBytes);
            InputFile inputFile = new InputFile(inputStream, "oylik_davomat_hisoboti.xlsx");

            telegramClient.execute(
                    SendDocument.builder()
                            .chatId(chatId.toString())
                            .document(inputFile)
                            .caption(BotMessages.EXCEL_CAPTION)
                            .build()
            );
        } catch (Exception e) {
            log.error("Failed to send Excel report to chat {}", chatId, e);
            sendPlainMessage(chatId, BotMessages.EXCEL_SEND_ERROR, telegramClient);
        }
    }

    private void resetSession(UserSession session) {
        session.setState(UserState.NONE);
        session.setFullName(null);
        session.setCorrectionDate(null);
        session.setCorrectionType(null);
        session.setEarlyLeaveReason(null);
    }

    private String formatDistance(double distanceMeters) {
        if (distanceMeters < 1000) {
            return Math.round(distanceMeters) + " metr";
        }

        double distanceKm = distanceMeters / 1000.0;
        return String.format("%.2f km", distanceKm);
    }

    private void sendPlainMessage(Long chatId, String text, TelegramClient telegramClient) {
        for (String chunk : splitMessage(text, 3500)) {
            try {
                telegramClient.execute(
                        SendMessage.builder()
                                .chatId(chatId.toString())
                                .text(chunk)
                                .build()
                );
            } catch (Exception e) {
                log.error("Failed to send plain message to chat {}", chatId, e);
                break;
            }
        }
    }

    private void sendMainMenu(Employee employee, Long chatId, TelegramClient telegramClient, String text) {
        KeyboardRow row1 = new KeyboardRow();
        row1.add(BotMessages.BUTTON_ARRIVED);
        row1.add(BotMessages.BUTTON_LEFT_WORK);

        KeyboardRow row2 = new KeyboardRow();
        row2.add(BotMessages.BUTTON_STATUS);

        KeyboardRow row3 = new KeyboardRow();
        row3.add(BotMessages.BUTTON_FIX_MISTAKE);
        row3.add(BotMessages.BUTTON_EARLY_LEAVE);

        List<KeyboardRow> keyboard = new ArrayList<>();
        keyboard.add(row1);
        keyboard.add(row2);
        keyboard.add(row3);

        if (employee != null && (employee.getRole() == Role.MANAGER || employee.getRole() == Role.ADMIN)) {
            KeyboardRow row4 = new KeyboardRow();
            row4.add(BotMessages.CMD_TODAY_REPORT);
            row4.add(BotMessages.CMD_MONTH_REPORT);

            KeyboardRow row5 = new KeyboardRow();
            row5.add(BotMessages.CMD_MONTH_EXCEL);

            KeyboardRow row6 = new KeyboardRow();
            row6.add(BotMessages.CMD_PENDING_CORRECTIONS);

            KeyboardRow row7 = new KeyboardRow();
            row7.add(BotMessages.CMD_EMPLOYEES);

            KeyboardRow row8 = new KeyboardRow();
            row8.add(BotMessages.CMD_PENDING_EARLY_LEAVES);
            row8.add(BotMessages.CMD_PENDING_REGISTRATIONS);

            KeyboardRow row9 = new KeyboardRow();
            row9.add(BotMessages.CMD_AUDIT_LOG);

            keyboard.add(row4);
            keyboard.add(row5);
            keyboard.add(row6);
            keyboard.add(row7);
            keyboard.add(row8);
            keyboard.add(row9);
        }

        ReplyKeyboardMarkup replyKeyboardMarkup = new ReplyKeyboardMarkup(
                keyboard,
                true,
                false,
                false,
                null,
                false
        );

        try {
            telegramClient.execute(
                    SendMessage.builder()
                            .chatId(chatId.toString())
                            .text(text)
                            .replyMarkup(replyKeyboardMarkup)
                            .build()
            );
        } catch (Exception e) {
            log.error("Failed to send menu message to chat {}", chatId, e);
        }
    }

    private void notifyManagersAboutPendingRegistration(PendingRegistration registration, TelegramClient telegramClient) {
        String message = "Yangi xodim ro'yxatdan o'tdi va faollashtirishni kutmoqda.\n"
                + "Ism familiya: " + registration.getFullName() + "\n"
                + "Bo'lim: " + registration.getDepartment() + "\n"
                + "Telegram user ID: " + registration.getTelegramUserId() + "\n"
                + "Faollashtirish: /activate_" + registration.getTelegramUserId() + "\n"
                + "Rad etish: /deactivate_pending_" + registration.getTelegramUserId();
        notifyManagers(message, telegramClient);
    }

    private void notifyManagersAboutCorrectionRequest(Employee employee, LocalDate workDate, TelegramClient telegramClient) {
        var request = correctionService.findLatestPendingRequest(employee, workDate);
        if (request == null) {
            return;
        }

        String message = "Yangi tuzatish so'rovi keldi.\n"
                + "Xodim: " + employee.getFullName() + "\n"
                + "Bo'lim: " + employee.getDepartment() + "\n"
                + "Sana: " + request.getWorkDate() + "\n"
                + "Kelgan vaqt: " + (request.getRequestedArrivalTime() == null ? "O'zgarmaydi" : request.getRequestedArrivalTime()) + "\n"
                + "Ketgan vaqt: " + (request.getRequestedLeaveTime() == null ? "O'zgarmaydi" : request.getRequestedLeaveTime()) + "\n"
                + "Tasdiqlash: /approve_" + request.getId() + "\n"
                + "Rad etish: /reject_" + request.getId();
        notifyManagers(message, telegramClient);
    }

    private void notifyManagersAboutEarlyLeaveRequest(Employee employee, TelegramClient telegramClient) {
        var request = earlyLeaveService.findLatestPendingRequest(employee);
        if (request == null) {
            return;
        }

        String message = "Yangi erta ketish so'rovi keldi.\n"
                + "Xodim: " + employee.getFullName() + "\n"
                + "Bo'lim: " + employee.getDepartment() + "\n"
                + "Sana: " + request.getWorkDate() + "\n"
                + "Sabab: " + request.getReason() + "\n"
                + "Tasdiqlash: /approve_early_" + request.getId() + "\n"
                + "Rad etish: /reject_early_" + request.getId();
        notifyManagers(message, telegramClient);
    }

    private void notifyManagers(String text, TelegramClient telegramClient) {
        List<Employee> managers = employeeRepository.findAllByActiveTrueAndRoleInOrderByFullNameAsc(List.of(Role.MANAGER, Role.ADMIN));
        Set<Long> chatIds = new HashSet<>();

        for (Employee manager : managers) {
            if (manager.getChatId() != null && chatIds.add(manager.getChatId())) {
                sendPlainMessage(manager.getChatId(), text, telegramClient);
            }
        }
    }

    private void notifyTargetEmployee(Long targetTelegramUserId, String message, boolean shouldNotify, TelegramClient telegramClient) {
        if (!shouldNotify) {
            return;
        }

        Employee target = employeeRepository.findByTelegramUserId(targetTelegramUserId).orElse(null);
        if (target != null && target.getChatId() != null) {
            sendPlainMessage(target.getChatId(), message, telegramClient);
        }
    }

    private List<String> splitMessage(String text, int maxLength) {
        if (text == null || text.isBlank()) {
            return List.of(" ");
        }

        if (text.length() <= maxLength) {
            return List.of(text);
        }

        List<String> chunks = new ArrayList<>();
        String remaining = text;

        while (remaining.length() > maxLength) {
            int splitIndex = remaining.lastIndexOf('\n', maxLength);
            if (splitIndex <= 0) {
                splitIndex = maxLength;
            }

            chunks.add(remaining.substring(0, splitIndex));
            remaining = remaining.substring(splitIndex).trim();
        }

        if (!remaining.isEmpty()) {
            chunks.add(remaining);
        }

        return chunks;
    }

    private boolean isMenuOrManagerCommand(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }

        return text.equals(BotMessages.BUTTON_ARRIVED)
                || text.equals(BotMessages.BUTTON_LEFT_WORK)
                || text.equals(BotMessages.BUTTON_STATUS)
                || text.equals(BotMessages.BUTTON_FIX_MISTAKE)
                || text.equals(BotMessages.BUTTON_EARLY_LEAVE)
                || text.equals(BotMessages.CMD_TODAY_REPORT)
                || text.equals(BotMessages.CMD_MONTH_REPORT)
                || text.equals(BotMessages.CMD_MONTH_EXCEL)
                || text.equals(BotMessages.CMD_PENDING_CORRECTIONS)
                || text.equals(BotMessages.CMD_PENDING_EARLY_LEAVES)
                || text.equals(BotMessages.CMD_PENDING_REGISTRATIONS)
                || text.equals(BotMessages.CMD_EMPLOYEES)
                || text.equals(BotMessages.CMD_AUDIT_LOG)
                || text.startsWith("/activate_")
                || text.startsWith("/deactivate_pending_")
                || text.startsWith("/deactivate_")
                || text.startsWith("/make_manager_")
                || text.startsWith("/make_employee_")
                || text.startsWith("/make_admin_")
                || text.startsWith("/approve_")
                || text.startsWith("/reject_");
    }
}
