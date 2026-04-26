package com.advancedprogramming.worklybot.service;

import com.advancedprogramming.worklybot.entity.EarlyLeaveRequest;
import com.advancedprogramming.worklybot.entity.Employee;
import com.advancedprogramming.worklybot.entity.enums.AuditActionType;
import com.advancedprogramming.worklybot.entity.enums.CorrectionStatus;
import com.advancedprogramming.worklybot.repository.EmployeeRepository;
import com.advancedprogramming.worklybot.repository.EarlyLeaveRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class EarlyLeaveService {

    private final EarlyLeaveRequestRepository earlyLeaveRequestRepository;
    private final EmployeeRepository employeeRepository;
    private final AuditLogService auditLogService;
    private final Clock appClock;

    public String createRequest(Employee employee, String reason) {
        LocalDate today = LocalDate.now(appClock);

        if (reason == null || reason.isBlank() || reason.trim().length() < 5) {
            return "Sababni aniqroq yozing. Kamida 5 ta belgi bo'lishi kerak.";
        }

        if (earlyLeaveRequestRepository.existsByEmployeeAndWorkDateAndStatus(employee, today, CorrectionStatus.PENDING)) {
            return "Bugun uchun kutilayotgan erta ketish so'rovi allaqachon mavjud.";
        }

        EarlyLeaveRequest request = EarlyLeaveRequest.builder()
                .employee(employee)
                .workDate(today)
                .reason(reason.trim())
                .status(CorrectionStatus.PENDING)
                .createdAt(LocalDateTime.now(appClock))
                .approvedForTodayLeave(false)
                .build();

        earlyLeaveRequestRepository.save(request);
        return "Erta ketish so'rovi menejerga yuborildi.";
    }

    public boolean hasApprovedRequestForToday(Employee employee) {
        return earlyLeaveRequestRepository
                .findTopByEmployeeAndWorkDateAndStatusOrderByCreatedAtDesc(
                        employee,
                        LocalDate.now(appClock),
                        CorrectionStatus.APPROVED
                )
                .map(EarlyLeaveRequest::isApprovedForTodayLeave)
                .orElse(false);
    }

    public String getPendingRequestsText() {
        List<EarlyLeaveRequest> requests = earlyLeaveRequestRepository.findAllByStatusOrderByCreatedAtAsc(CorrectionStatus.PENDING);

        if (requests.isEmpty()) {
            return "Kutilayotgan erta ketish so'rovlari yo'q.";
        }

        StringBuilder sb = new StringBuilder("Kutilayotgan erta ketish so'rovlari:\n\n");

        for (EarlyLeaveRequest request : requests) {
            sb.append("So'rov ID: ").append(request.getId()).append("\n")
                    .append("Xodim: ").append(request.getEmployee().getFullName()).append("\n")
                    .append("Bo'lim: ").append(request.getEmployee().getDepartment()).append("\n")
                    .append("Sana: ").append(request.getWorkDate()).append("\n")
                    .append("Sabab: ").append(request.getReason()).append("\n")
                    .append("Tasdiqlash: /approve_early_").append(request.getId()).append("\n")
                    .append("Rad etish: /reject_early_").append(request.getId()).append("\n")
                    .append("----------------------\n");
        }

        return sb.toString();
    }

    public EarlyLeaveRequest findLatestPendingRequest(Employee employee) {
        return earlyLeaveRequestRepository
                .findTopByEmployeeAndWorkDateAndStatusOrderByCreatedAtDesc(
                        employee,
                        LocalDate.now(appClock),
                        CorrectionStatus.PENDING
                )
                .orElse(null);
    }

    public CorrectionActionResult approve(Long requestId, Long actorTelegramUserId) {
        EarlyLeaveRequest request = earlyLeaveRequestRepository.findById(requestId).orElse(null);

        if (request == null) {
            return new CorrectionActionResult("Erta ketish so'rovi topilmadi.", null, null);
        }

        if (request.getStatus() != CorrectionStatus.PENDING) {
            return new CorrectionActionResult("Bu so'rov allaqachon ko'rib chiqilgan.", null, null);
        }

        request.setStatus(CorrectionStatus.APPROVED);
        request.setApprovedForTodayLeave(true);
        request.setReviewedAt(LocalDateTime.now(appClock));
        request.setReviewedByTelegramUserId(actorTelegramUserId);
        earlyLeaveRequestRepository.save(request);

        Employee actor = employeeRepository.findByTelegramUserId(actorTelegramUserId).orElse(null);
        auditLogService.logAction(
                AuditActionType.EARLY_LEAVE_APPROVED,
                actor,
                request.getEmployee(),
                "Sana: " + request.getWorkDate() + ", sabab: " + request.getReason()
        );

        return new CorrectionActionResult(
                "Erta ketish so'rovi tasdiqlandi.",
                request.getEmployee().getChatId(),
                "Sizning bugun erta ketish so'rovingiz tasdiqlandi. Endi joylashuv yuborib ketishni belgilashingiz mumkin."
        );
    }

    public CorrectionActionResult reject(Long requestId, Long actorTelegramUserId) {
        EarlyLeaveRequest request = earlyLeaveRequestRepository.findById(requestId).orElse(null);

        if (request == null) {
            return new CorrectionActionResult("Erta ketish so'rovi topilmadi.", null, null);
        }

        if (request.getStatus() != CorrectionStatus.PENDING) {
            return new CorrectionActionResult("Bu so'rov allaqachon ko'rib chiqilgan.", null, null);
        }

        request.setStatus(CorrectionStatus.REJECTED);
        request.setReviewedAt(LocalDateTime.now(appClock));
        request.setReviewedByTelegramUserId(actorTelegramUserId);
        earlyLeaveRequestRepository.save(request);

        Employee actor = employeeRepository.findByTelegramUserId(actorTelegramUserId).orElse(null);
        auditLogService.logAction(
                AuditActionType.EARLY_LEAVE_REJECTED,
                actor,
                request.getEmployee(),
                "Sana: " + request.getWorkDate() + ", sabab: " + request.getReason()
        );

        return new CorrectionActionResult(
                "Erta ketish so'rovi rad etildi.",
                request.getEmployee().getChatId(),
                "Sizning bugun erta ketish so'rovingiz rad etildi."
        );
    }
}
