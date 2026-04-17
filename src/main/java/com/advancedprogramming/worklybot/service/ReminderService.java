package com.advancedprogramming.worklybot.service;

import com.advancedprogramming.worklybot.config.BotProperties;
import com.advancedprogramming.worklybot.entity.Attendance;
import com.advancedprogramming.worklybot.entity.Employee;
import com.advancedprogramming.worklybot.repository.AttendanceRepository;
import com.advancedprogramming.worklybot.repository.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReminderService {

    private final EmployeeRepository employeeRepository;
    private final AttendanceRepository attendanceRepository;
    private final BotProperties botProperties;

    public void remindMissingArrivals() {
        TelegramClient telegramClient = new OkHttpTelegramClient(botProperties.getToken());
        LocalDate today = LocalDate.now();

        List<Employee> employees = employeeRepository.findAllByActiveTrue();

        for (Employee employee : employees) {
            Attendance attendance = attendanceRepository.findByEmployeeAndWorkDate(employee, today).orElse(null);

            if (attendance == null || attendance.getArrivalTime() == null) {
                sendMessage(
                        employee.getChatId(),
                        "Eslatma: Siz bugun kelgan vaqtingizni belgilamadingiz. ✅ 'Keldim' tugmasini bosing.",
                        telegramClient
                );
            }
        }
    }

    public void remindMissingLeaves() {
        TelegramClient telegramClient = new OkHttpTelegramClient(botProperties.getToken());
        LocalDate today = LocalDate.now();

        List<Employee> employees = employeeRepository.findAllByActiveTrue();

        for (Employee employee : employees) {
            Attendance attendance = attendanceRepository.findByEmployeeAndWorkDate(employee, today).orElse(null);

            if (attendance != null && attendance.getArrivalTime() != null && attendance.getLeaveTime() == null) {
                sendMessage(
                        employee.getChatId(),
                        "Eslatma: Siz bugun ketgan vaqtingizni belgilamadingiz. 🚪 'Ketdim' tugmasini bosing.",
                        telegramClient
                );
            }
        }
    }

    private void sendMessage(Long chatId, String text, TelegramClient telegramClient) {
        try {
            telegramClient.execute(
                    SendMessage.builder()
                            .chatId(chatId.toString())
                            .text(text)
                            .build()
            );
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
