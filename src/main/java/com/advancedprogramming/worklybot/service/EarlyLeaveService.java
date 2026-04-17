package com.advancedprogramming.worklybot.service;

import com.advancedprogramming.worklybot.entity.EarlyLeaveRequest;
import com.advancedprogramming.worklybot.entity.Employee;
import com.advancedprogramming.worklybot.entity.enums.CorrectionStatus;
import com.advancedprogramming.worklybot.repository.EarlyLeaveRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class EarlyLeaveService {

    private final EarlyLeaveRequestRepository earlyLeaveRequestRepository;

    public String createRequest(Employee employee, String reason) {
        LocalDate today = LocalDate.now();

        EarlyLeaveRequest request = EarlyLeaveRequest.builder()
                .employee(employee)
                .workDate(today)
                .reason(reason)
                .status(CorrectionStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .approvedForTodayLeave(false)
                .build();

        earlyLeaveRequestRepository.save(request);
        return "Erta ketish so'rovi menejerga yuborildi.";
    }

    public boolean hasApprovedRequestForToday(Employee employee) {
        return earlyLeaveRequestRepository
                .findTopByEmployeeAndWorkDateAndStatusOrderByCreatedAtDesc(
                        employee,
                        LocalDate.now(),
                        CorrectionStatus.APPROVED
                )
                .map(EarlyLeaveRequest::isApprovedForTodayLeave)
                .orElse(false);
    }

    public String getPendingRequestsText() {
        List<EarlyLeaveRequest> requests = earlyLeaveRequestRepository.findAllByStatus(CorrectionStatus.PENDING);

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

    public CorrectionActionResult approve(Long requestId) {
        EarlyLeaveRequest request = earlyLeaveRequestRepository.findById(requestId).orElse(null);

        if (request == null) {
            return new CorrectionActionResult("Erta ketish so'rovi topilmadi.", null, null);
        }

        if (request.getStatus() != CorrectionStatus.PENDING) {
            return new CorrectionActionResult("Bu so'rov allaqachon ko'rib chiqilgan.", null, null);
        }

        request.setStatus(CorrectionStatus.APPROVED);
        request.setApprovedForTodayLeave(true);
        earlyLeaveRequestRepository.save(request);

        return new CorrectionActionResult(
                "Erta ketish so'rovi tasdiqlandi.",
                request.getEmployee().getChatId(),
                "Sizning bugun erta ketish so'rovingiz tasdiqlandi. Endi joylashuv yuborib ketishni belgilashingiz mumkin."
        );
    }

    public CorrectionActionResult reject(Long requestId) {
        EarlyLeaveRequest request = earlyLeaveRequestRepository.findById(requestId).orElse(null);

        if (request == null) {
            return new CorrectionActionResult("Erta ketish so'rovi topilmadi.", null, null);
        }

        if (request.getStatus() != CorrectionStatus.PENDING) {
            return new CorrectionActionResult("Bu so'rov allaqachon ko'rib chiqilgan.", null, null);
        }

        request.setStatus(CorrectionStatus.REJECTED);
        earlyLeaveRequestRepository.save(request);

        return new CorrectionActionResult(
                "Erta ketish so'rovi rad etildi.",
                request.getEmployee().getChatId(),
                "Sizning bugun erta ketish so'rovingiz rad etildi."
        );
    }
}
