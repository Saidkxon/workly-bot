package com.advancedprogramming.worklybot.web;

import com.advancedprogramming.worklybot.entity.EmpTestAttempt;
import com.advancedprogramming.worklybot.entity.EmpTestQuestion;
import com.advancedprogramming.worklybot.entity.enums.EmpTestAttemptStatus;
import com.advancedprogramming.worklybot.repository.EmpTestQuestionRepository;
import com.advancedprogramming.worklybot.service.EmpTestService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Public-facing API for the standalone (non-Telegram) test page at /test?token=...
 * The token itself is the only credential — this page is meant to be opened in a
 * regular desktop browser, outside the Telegram Mini App WebView, so window/tab
 * switching can actually be detected.
 */
@RestController
@RequestMapping("/api/test")
@RequiredArgsConstructor
public class EmpTestApiController {

    private final EmpTestService empTestService;
    private final EmpTestQuestionRepository questionRepository;
    private final Clock appClock;

    @GetMapping("/{token}")
    public ResponseEntity<AttemptStateView> getState(@PathVariable String token) {
        EmpTestAttempt attempt = fetchOrNotFound(token);
        List<QuestionView> questions = attempt.getStatus() == EmpTestAttemptStatus.NOT_STARTED
                ? List.of()
                : questionRepository.findAllByTestOrderByOrderIndexAsc(attempt.getTest()).stream()
                .map(this::toQuestionView)
                .toList();
        return ResponseEntity.ok(toStateView(attempt, questions));
    }

    @PostMapping("/{token}/start")
    public ResponseEntity<AttemptStateView> start(@PathVariable String token) {
        EmpTestAttempt attempt = empTestService.startAttempt(token);
        List<QuestionView> questions = questionRepository.findAllByTestOrderByOrderIndexAsc(attempt.getTest()).stream()
                .map(this::toQuestionView)
                .toList();
        return ResponseEntity.ok(toStateView(attempt, questions));
    }

    @PostMapping("/{token}/violation")
    public ResponseEntity<ViolationResponse> violation(@PathVariable String token) {
        String action = empTestService.recordViolation(token);
        return ResponseEntity.ok(new ViolationResponse(action));
    }

    @PostMapping("/{token}/submit")
    public ResponseEntity<AttemptStateView> submit(@PathVariable String token, @RequestBody SubmitRequest request) {
        Map<Long, String> answers = request.answers() == null ? Map.of() : request.answers();
        EmpTestAttempt attempt = empTestService.submit(token, answers);
        return ResponseEntity.ok(toStateView(attempt, List.of()));
    }

    private EmpTestAttempt fetchOrNotFound(String token) {
        try {
            return empTestService.getAttemptByToken(token);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    private QuestionView toQuestionView(EmpTestQuestion question) {
        return new QuestionView(
                question.getId(), question.getQuestionText(), question.getType().name(),
                question.getOptionA(), question.getOptionB(), question.getOptionC(), question.getOptionD()
        );
    }

    private AttemptStateView toStateView(EmpTestAttempt attempt, List<QuestionView> questions) {
        Integer secondsLeft = null;
        if (attempt.getStatus() == EmpTestAttemptStatus.IN_PROGRESS && attempt.getDeadlineAt() != null) {
            long seconds = Duration.between(LocalDateTime.now(appClock), attempt.getDeadlineAt()).getSeconds();
            secondsLeft = (int) Math.max(0, seconds);
        }
        return new AttemptStateView(
                attempt.getStatus().name(),
                attempt.getTest().getTitle(),
                attempt.getTest().getTimerMinutes(),
                secondsLeft,
                attempt.getViolationCount(),
                questions
        );
    }

    public record QuestionView(Long id, String questionText, String type,
                               String optionA, String optionB, String optionC, String optionD) {
    }

    public record AttemptStateView(String status, String testTitle, Integer timerMinutes,
                                   Integer secondsLeft, int violationCount, List<QuestionView> questions) {
    }

    public record ViolationResponse(String action) {
    }

    public record SubmitRequest(Map<Long, String> answers) {
    }
}