package com.advancedprogramming.worklybot.service;

import com.advancedprogramming.worklybot.entity.Employee;
import com.advancedprogramming.worklybot.entity.FeedbackResponse;
import com.advancedprogramming.worklybot.repository.FeedbackResponseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Stores employee feedback gathered from admin questionnaires (/survey -> /feedback).
 */
@Service
@RequiredArgsConstructor
public class FeedbackService {

    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final int MAX_LENGTH = 2000;

    private final FeedbackResponseRepository feedbackResponseRepository;
    private final Clock appClock;

    public void save(Employee employee, String message) {
        String trimmed = message == null ? "" : message.trim();
        if (trimmed.length() > MAX_LENGTH) {
            trimmed = trimmed.substring(0, MAX_LENGTH);
        }
        feedbackResponseRepository.save(FeedbackResponse.builder()
                .telegramUserId(employee.getTelegramUserId())
                .fullName(employee.getFullName())
                .department(employee.getDepartment())
                .message(trimmed)
                .createdAt(LocalDateTime.now(appClock))
                .build());
    }

    public String recentFeedbackText() {
        List<FeedbackResponse> rows = feedbackResponseRepository.findTop30ByOrderByCreatedAtDesc();
        if (rows.isEmpty()) {
            return "Hozircha fikr-mulohazalar yo'q.";
        }

        StringBuilder sb = new StringBuilder("Oxirgi fikr-mulohazalar:\n\n");
        for (FeedbackResponse row : rows) {
            sb.append("Vaqt: ").append(row.getCreatedAt().format(FORMAT)).append("\n")
                    .append("Xodim: ").append(row.getFullName())
                    .append(" (").append(row.getDepartment()).append(")\n")
                    .append("Fikr: ").append(row.getMessage()).append("\n")
                    .append("----------------------\n");
        }
        return sb.toString();
    }
}
