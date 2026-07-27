package com.advancedprogramming.worklybot.service;

import com.advancedprogramming.worklybot.entity.EmpTest;
import com.advancedprogramming.worklybot.entity.EmpTestAnswer;
import com.advancedprogramming.worklybot.entity.EmpTestAttempt;
import com.advancedprogramming.worklybot.entity.EmpTestQuestion;
import com.advancedprogramming.worklybot.entity.Employee;
import com.advancedprogramming.worklybot.entity.enums.EmpTestAttemptStatus;
import com.advancedprogramming.worklybot.entity.enums.EmpTestQuestionType;
import com.advancedprogramming.worklybot.entity.enums.EmpTestStatus;
import com.advancedprogramming.worklybot.entity.enums.Role;
import com.advancedprogramming.worklybot.repository.EmpTestAnswerRepository;
import com.advancedprogramming.worklybot.repository.EmpTestAttemptRepository;
import com.advancedprogramming.worklybot.repository.EmpTestQuestionRepository;
import com.advancedprogramming.worklybot.repository.EmpTestRepository;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Backs the standalone employee test: one active {@link EmpTest} at a time, with
 * true/false and keyword-graded short-answer questions auto-scored, open questions
 * left for manual review, a customizable per-attempt timer, and a two-strike
 * anti-cheat rule (tab/window switch) that blocks the attempt on the second violation.
 */
@Service
@RequiredArgsConstructor
public class EmpTestService {

    public static final String BLOCK_REASON = "Test paytida boshqa pagelardan foydalandi";
    private static final int MAX_VIOLATIONS_BEFORE_BLOCK = 2;

    private final EmpTestRepository testRepository;
    private final EmpTestQuestionRepository questionRepository;
    private final EmpTestAttemptRepository attemptRepository;
    private final EmpTestAnswerRepository answerRepository;
    private final Clock appClock;

    // ---- Admin: test configuration -----------------------------------------------

    public EmpTest getOrCreateCurrentTest() {
        return testRepository.findFirstByOrderByIdDesc().orElseGet(() -> {
            EmpTest fresh = EmpTest.builder()
                    .title("Xodimlar testi")
                    .timerMinutes(15)
                    .status(EmpTestStatus.DRAFT)
                    .visibleToAllEmployees(false)
                    .createdAt(LocalDateTime.now(appClock))
                    .build();
            return testRepository.save(fresh);
        });
    }

    @Transactional
    public EmpTest updateConfig(String title, Integer timerMinutes, Boolean visibleToAllEmployees) {
        EmpTest test = getOrCreateCurrentTest();
        if (title != null && !title.isBlank()) {
            test.setTitle(title.trim());
        }
        if (timerMinutes != null && timerMinutes > 0) {
            test.setTimerMinutes(timerMinutes);
        }
        if (visibleToAllEmployees != null) {
            test.setVisibleToAllEmployees(visibleToAllEmployees);
        }
        return testRepository.save(test);
    }

    @Transactional
    public EmpTest setStatus(EmpTestStatus status) {
        EmpTest test = getOrCreateCurrentTest();
        test.setStatus(status);
        return testRepository.save(test);
    }

    public List<EmpTestQuestion> listQuestions() {
        return questionRepository.findAllByTestOrderByOrderIndexAsc(getOrCreateCurrentTest());
    }

    @Transactional
    public EmpTestQuestion addQuestion(String questionText, EmpTestQuestionType type, String correctAnswer, Integer points) {
        EmpTest test = getOrCreateCurrentTest();
        int nextOrder = (int) questionRepository.countByTest(test);
        EmpTestQuestion question = EmpTestQuestion.builder()
                .test(test)
                .orderIndex(nextOrder)
                .questionText(questionText.trim())
                .type(type)
                .correctAnswer(type == EmpTestQuestionType.OPEN ? null : normalizeAnswerStorage(correctAnswer))
                .points(points == null || points < 1 ? 1 : points)
                .build();
        return questionRepository.save(question);
    }

    @Transactional
    public void deleteQuestion(Long questionId) {
        questionRepository.deleteById(questionId);
    }

    private String normalizeAnswerStorage(String raw) {
        return raw == null ? null : raw.trim();
    }

    // ---- Employee: getting a link ---------------------------------------------

    /**
     * Returns the attempt an employee should use, creating one if this is their first
     * time, or null if there's no test they're currently allowed to see.
     */
    @Transactional
    public EmpTestAttempt getOrCreateAttemptFor(Employee employee) {
        EmpTest test = getOrCreateCurrentTest();
        if (test.getStatus() != EmpTestStatus.ACTIVE) {
            return null;
        }
        boolean allowed = test.isVisibleToAllEmployees() || employee.getRole() == Role.ADMIN;
        if (!allowed) {
            return null;
        }
        return attemptRepository.findByTestAndEmployee(test, employee).orElseGet(() -> {
            EmpTestAttempt attempt = EmpTestAttempt.builder()
                    .test(test)
                    .employee(employee)
                    .token(UUID.randomUUID().toString().replace("-", ""))
                    .status(EmpTestAttemptStatus.NOT_STARTED)
                    .violationCount(0)
                    .build();
            return attemptRepository.save(attempt);
        });
    }

    // ---- Standalone test page: token-based flow --------------------------------

    public EmpTestAttempt getAttemptByToken(String token) {
        EmpTestAttempt attempt = attemptRepository.findByToken(token)
                .orElseThrow(() -> new IllegalArgumentException("Test topilmadi."));
        expireIfPastDeadline(attempt);
        return attempt;
    }

    @Transactional
    public EmpTestAttempt startAttempt(String token) {
        EmpTestAttempt attempt = getAttemptByToken(token);
        if (attempt.getStatus() == EmpTestAttemptStatus.NOT_STARTED) {
            LocalDateTime now = LocalDateTime.now(appClock);
            attempt.setStatus(EmpTestAttemptStatus.IN_PROGRESS);
            attempt.setStartedAt(now);
            attempt.setDeadlineAt(now.plusMinutes(attempt.getTest().getTimerMinutes()));
            attemptRepository.save(attempt);
        }
        return attempt;
    }

    /**
     * Records a tab/window-switch violation. First one is a warning; the second one
     * blocks the attempt outright and stamps it for the Excel export.
     */
    @Transactional
    public String recordViolation(String token) {
        EmpTestAttempt attempt = getAttemptByToken(token);
        if (attempt.getStatus() != EmpTestAttemptStatus.IN_PROGRESS) {
            return "IGNORED";
        }
        attempt.setViolationCount(attempt.getViolationCount() + 1);
        if (attempt.getViolationCount() >= MAX_VIOLATIONS_BEFORE_BLOCK) {
            attempt.setStatus(EmpTestAttemptStatus.BLOCKED);
            attempt.setBlockReason(BLOCK_REASON);
            attempt.setSubmittedAt(LocalDateTime.now(appClock));
            attemptRepository.save(attempt);
            return "BLOCKED";
        }
        attemptRepository.save(attempt);
        return "WARN";
    }

    /**
     * Grades and finalizes an attempt. answers maps questionId -> raw answer text.
     */
    @Transactional
    public EmpTestAttempt submit(String token, Map<Long, String> answers) {
        EmpTestAttempt attempt = getAttemptByToken(token);
        if (attempt.getStatus() != EmpTestAttemptStatus.IN_PROGRESS) {
            return attempt;
        }

        List<EmpTestQuestion> questions = questionRepository.findAllByTestOrderByOrderIndexAsc(attempt.getTest());
        int score = 0;
        int maxScore = 0;

        for (EmpTestQuestion question : questions) {
            maxScore += question.getPoints();
            String raw = answers.getOrDefault(question.getId(), "");
            Boolean correct = grade(question, raw);
            if (Boolean.TRUE.equals(correct)) {
                score += question.getPoints();
            }
            answerRepository.save(EmpTestAnswer.builder()
                    .attempt(attempt)
                    .question(question)
                    .answerText(raw)
                    .correct(correct)
                    .build());
        }

        attempt.setScore(score);
        attempt.setMaxScore(maxScore);
        attempt.setStatus(EmpTestAttemptStatus.SUBMITTED);
        attempt.setSubmittedAt(LocalDateTime.now(appClock));
        return attemptRepository.save(attempt);
    }

    /**
     * Returns true/false if auto-gradable and matched/unmatched, or null if this
     * needs manual review (OPEN questions, or a SHORT_ANSWER with no keyword match).
     */
    private Boolean grade(EmpTestQuestion question, String rawAnswer) {
        String answer = rawAnswer == null ? "" : normalize(rawAnswer);
        switch (question.getType()) {
            case TRUE_FALSE -> {
                String expected = normalize(question.getCorrectAnswer());
                return answer.equals(expected);
            }
            case SHORT_ANSWER -> {
                if (answer.isBlank()) return false;
                String[] keywords = question.getCorrectAnswer() == null
                        ? new String[0]
                        : question.getCorrectAnswer().split(",");
                for (String keyword : keywords) {
                    String normalizedKeyword = normalize(keyword);
                    if (!normalizedKeyword.isBlank() && answer.contains(normalizedKeyword)) {
                        return true;
                    }
                }
                return null; // no match -> flagged for manual review, not auto-failed
            }
            default -> {
                return null; // OPEN -> always manual
            }
        }
    }

    private String normalize(String s) {
        return s == null ? "" : s.trim().toLowerCase().replaceAll("[.,!?'\"()]", "");
    }

    private void expireIfPastDeadline(EmpTestAttempt attempt) {
        if (attempt.getStatus() == EmpTestAttemptStatus.IN_PROGRESS
                && attempt.getDeadlineAt() != null
                && LocalDateTime.now(appClock).isAfter(attempt.getDeadlineAt())) {
            attempt.setStatus(EmpTestAttemptStatus.EXPIRED);
            attempt.setSubmittedAt(LocalDateTime.now(appClock));
            attemptRepository.save(attempt);
        }
    }

    // ---- Admin: results export --------------------------------------------------

    public byte[] buildResultsWorkbook() {
        EmpTest test = getOrCreateCurrentTest();
        List<EmpTestAttempt> attempts = attemptRepository.findAllByTestOrderByEmployee_FullNameAsc(test);
        List<EmpTestQuestion> questions = questionRepository.findAllByTestOrderByOrderIndexAsc(test);

        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("Natijalar");
            CellStyle bold = boldStyle(workbook);

            Row header = sheet.createRow(0);
            String[] fixedCols = {"Ism familiya", "Bo'lim", "Holat", "Izoh", "Ball"};
            int col = 0;
            for (String title : fixedCols) {
                setCell(header, col++, title, bold);
            }
            for (EmpTestQuestion question : questions) {
                setCell(header, col++, question.getQuestionText(), bold);
            }

            int rowNum = 1;
            for (EmpTestAttempt attempt : attempts) {
                Row row = sheet.createRow(rowNum++);
                Map<Long, EmpTestAnswer> byQuestion = new HashMap<>();
                for (EmpTestAnswer answer : answerRepository.findAllByAttempt(attempt)) {
                    byQuestion.put(answer.getQuestion().getId(), answer);
                }

                int c = 0;
                setCell(row, c++, attempt.getEmployee().getFullName(), null);
                setCell(row, c++, attempt.getEmployee().getDepartment(), null);
                setCell(row, c++, statusLabel(attempt), null);
                setCell(row, c++, attempt.getBlockReason() == null ? "" : attempt.getBlockReason(), null);
                setCell(row, c++, attempt.getScore() == null ? "" : attempt.getScore() + "/" + attempt.getMaxScore(), null);

                for (EmpTestQuestion question : questions) {
                    EmpTestAnswer answer = byQuestion.get(question.getId());
                    String text = answer == null ? "" : answer.getAnswerText();
                    if (answer != null && answer.getCorrect() == null && question.getType() != EmpTestQuestionType.OPEN) {
                        text = text + " (ko'rib chiqish kerak)";
                    }
                    setCell(row, c++, text, null);
                }
            }

            for (int i = 0; i < fixedCols.length + questions.size(); i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Excel yaratishda xatolik: " + e.getMessage(), e);
        }
    }

    private String statusLabel(EmpTestAttempt attempt) {
        return switch (attempt.getStatus()) {
            case NOT_STARTED -> "Boshlamagan";
            case IN_PROGRESS -> "Jarayonda";
            case SUBMITTED -> "Yakunlangan";
            case BLOCKED -> "Bloklangan";
            case EXPIRED -> "Vaqt tugadi";
        };
    }

    private void setCell(Row row, int col, String value, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellValue(value == null ? "" : value);
        if (style != null) cell.setCellStyle(style);
    }

    private CellStyle boldStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        return style;
    }
}