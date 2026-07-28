package com.advancedprogramming.worklybot.service;

import com.advancedprogramming.worklybot.config.BotProperties;
import com.advancedprogramming.worklybot.entity.Attendance;
import com.advancedprogramming.worklybot.entity.Employee;
import com.advancedprogramming.worklybot.entity.enums.Role;
import com.advancedprogramming.worklybot.entity.enums.Shift;
import com.advancedprogramming.worklybot.repository.AttendanceRepository;
import com.advancedprogramming.worklybot.repository.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.time.LocalDate;
import java.util.List;

/**
 * Shift-aware reminders and broadcasts. Reminders only target the employees on the
 * relevant shift, so morning-shift people are not pinged at the evening shift's times.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ReminderService {

    private final EmployeeRepository employeeRepository;
    private final AttendanceRepository attendanceRepository;
    private final BotProperties botProperties;

    public void remindMissingArrivals(Shift shift) {
        TelegramClient client = newClient();
        LocalDate today = LocalDate.now();

        for (Employee employee : employeesOnShift(shift)) {
            Attendance attendance = attendanceRepository.findByEmployeeAndWorkDate(employee, today).orElse(null);
            if (attendance == null || attendance.getArrivalTime() == null) {
                send(employee.getChatId(),
                        "Eslatma: Siz bugun kelgan vaqtingizni belgilamadingiz. ✅ 'Keldim' tugmasini bosing.",
                        client);
            }
        }
    }

    public void remindMissingLeaves(Shift shift) {
        TelegramClient client = newClient();
        LocalDate today = LocalDate.now();

        for (Employee employee : employeesOnShift(shift)) {
            Attendance attendance = attendanceRepository.findByEmployeeAndWorkDate(employee, today).orElse(null);
            if (attendance != null && attendance.getArrivalTime() != null && attendance.getLeaveTime() == null) {
                send(employee.getChatId(),
                        "Eslatma: Siz bugun ketgan vaqtingizni belgilamadingiz. 🚪 'Ketdim' tugmasini bosing.",
                        client);
            }
        }
    }

    public void broadcastAwards(AwardService.MonthlyAwards awards) {
        if (awards == null) {
            return;
        }
        TelegramClient client = newClient();

        String publicMessage = buildPublicAwardMessage(awards);
        List<Employee> activeEmployees = employeeRepository.findAllByActiveTrue();
        for (Employee employee : activeEmployees) {
            send(employee.getChatId(), publicMessage, client);
        }

        if (!awards.topWorked().isEmpty()) {
            String workedListMessage = buildTopListMessage(
                    "💪 " + awards.month() + " oyining eng samarali 10 xodimlari!",
                    awards.topWorked()
            );
            for (Employee employee : activeEmployees) {
                send(employee.getChatId(), workedListMessage, client);
            }
        }

        if (!awards.topPunctual().isEmpty()) {
            String punctualListMessage = buildTopListMessage(
                    "⏱ " + awards.month() + " oyining eng intizomli 10 xodimlari!",
                    awards.topPunctual()
            );
            for (Employee employee : activeEmployees) {
                send(employee.getChatId(), punctualListMessage, client);
            }
        }

        if (awards.mostLate() != null) {
            String managerMessage = "Diqqat (faqat menejer va adminlar uchun):\n"
                    + awards.month() + " oyida eng ko'p kechikkan xodim: "
                    + awards.mostLate().fullName() + " (" + awards.mostLate().department() + ").\n"
                    + "Jami kechikish: " + SalaryService.formatMinutes(awards.mostLate().value()) + ".";
            for (Employee manager : employeeRepository.findAllByActiveTrueAndRoleInOrderByFullNameAsc(List.of(Role.MANAGER, Role.ADMIN))) {
                send(manager.getChatId(), managerMessage, client);
            }
        }
    }

    private String buildPublicAwardMessage(AwardService.MonthlyAwards awards) {
        StringBuilder sb = new StringBuilder();
        sb.append("🏆 ").append(awards.month()).append(" oyi natijalari!\n\n");
        sb.append("Eng effektive xodim: ").append(awards.hardestWorker().fullName())
                .append(" (").append(awards.hardestWorker().department()).append(").\n")
                .append("Jami ishlangan vaqt: ").append(SalaryService.formatMinutes(awards.hardestWorker().value())).append(".\n\n");
        if (awards.mostPunctual() != null) {
            sb.append("Eng intizomli (kam kechikkan) xodim: ").append(awards.mostPunctual().fullName())
                    .append(" (").append(awards.mostPunctual().department()).append(").\n\n");
        }
        sb.append("Tabriklaymiz ularni! 🎉 va kelgusi oyda hammaga omad tilaymiz. Samaradorligingiz uchun rahmat :)");
        return sb.toString();
    }

    private String buildTopListMessage(String title, List<AwardService.AwardListEntry> entries) {
        StringBuilder sb = new StringBuilder();
        sb.append(title).append("\n\n");
        int rank = 1;
        for (AwardService.AwardListEntry entry : entries) {
            sb.append(rank++).append(". ").append(entry.fullName())
                    .append(" (").append(entry.department()).append("), ")
                    .append("ishlangan vaqt: ").append(SalaryService.formatMinutes(entry.workedMinutes())).append(", ")
                    .append("ishlagan kunlar: ").append(entry.workedDays()).append(", ")
                    .append("kechikkan kunlar: ").append(entry.lateDays()).append(", ")
                    .append("kechikish: ").append(SalaryService.formatMinutes(entry.lateMinutes()))
                    .append(".\n");
        }
        sb.append("\nTabriklaymiz va kelgusi oyda ham har birlaringizga omad tilaymiz! 🎉");
        return sb.toString();
    }

    private List<Employee> employeesOnShift(Shift shift) {
        return employeeRepository.findAllByActiveTrue().stream()
                .filter(employee -> Shift.orDefault(employee.getShift()) == shift)
                .toList();
    }

    private TelegramClient newClient() {
        return new OkHttpTelegramClient(botProperties.getToken());
    }

    private void send(Long chatId, String text, TelegramClient client) {
        if (chatId == null) {
            return;
        }
        try {
            client.execute(SendMessage.builder().chatId(chatId.toString()).text(text).build());
        } catch (Exception e) {
            log.error("Failed to send reminder/broadcast to chat {}", chatId, e);
        }
    }
}