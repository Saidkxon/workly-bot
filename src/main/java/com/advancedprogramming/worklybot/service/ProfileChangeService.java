package com.advancedprogramming.worklybot.service;

import com.advancedprogramming.worklybot.entity.Employee;
import com.advancedprogramming.worklybot.entity.ProfileChangeRequest;
import com.advancedprogramming.worklybot.entity.enums.AuditActionType;
import com.advancedprogramming.worklybot.entity.enums.CorrectionStatus;
import com.advancedprogramming.worklybot.entity.enums.Shift;
import com.advancedprogramming.worklybot.repository.EmployeeRepository;
import com.advancedprogramming.worklybot.repository.ProfileChangeRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ProfileChangeService {

    private final ProfileChangeRequestRepository profileChangeRequestRepository;
    private final EmployeeRepository employeeRepository;
    private final AuditLogService auditLogService;
    private final Clock appClock;

    public String createRequest(Employee employee, String requestedDepartment, Shift requestedShift) {
        if (profileChangeRequestRepository.existsByEmployeeAndStatus(employee, CorrectionStatus.PENDING)) {
            return "Sizda kutilayotgan profil o'zgartirish so'rovi allaqachon mavjud.";
        }

        Shift currentShift = Shift.orDefault(employee.getShift());
        if (employee.getDepartment().equals(requestedDepartment) && currentShift == requestedShift) {
            return "Tanlangan bo'lim va smena hozirgi profilingiz bilan bir xil.";
        }

        ProfileChangeRequest request = ProfileChangeRequest.builder()
                .employee(employee)
                .currentDepartment(employee.getDepartment())
                .requestedDepartment(requestedDepartment)
                .currentShift(currentShift)
                .requestedShift(requestedShift)
                .status(CorrectionStatus.PENDING)
                .createdAt(LocalDateTime.now(appClock))
                .build();

        profileChangeRequestRepository.save(request);
        return "Bo'lim/smena o'zgartirish so'rovi menejerga yuborildi.";
    }

    public ProfileChangeRequest findLatestPendingRequest(Employee employee) {
        return profileChangeRequestRepository
                .findTopByEmployeeAndStatusOrderByCreatedAtDesc(employee, CorrectionStatus.PENDING)
                .orElse(null);
    }

    public String getPendingRequestsText() {
        List<ProfileChangeRequest> requests = profileChangeRequestRepository
                .findAllByStatusOrderByCreatedAtAsc(CorrectionStatus.PENDING);
        if (requests.isEmpty()) {
            return "Kutilayotgan profil o'zgartirish so'rovlari yo'q.";
        }

        StringBuilder sb = new StringBuilder("Kutilayotgan profil o'zgartirish so'rovlari:\n\n");
        for (ProfileChangeRequest request : requests) {
            sb.append("So'rov ID: ").append(request.getId()).append("\n")
                    .append("Xodim: ").append(request.getEmployee().getFullName()).append("\n")
                    .append("Bo'lim: ").append(request.getCurrentDepartment()).append(" -> ")
                    .append(request.getRequestedDepartment()).append("\n")
                    .append("Smena: ").append(Shift.orDefault(request.getCurrentShift()).getDisplayName()).append(" -> ")
                    .append(request.getRequestedShift().getDisplayName()).append("\n")
                    .append("Tasdiqlash: /approve_profile_").append(request.getId()).append("\n")
                    .append("Rad etish: /reject_profile_").append(request.getId()).append("\n")
                    .append("----------------------\n");
        }
        return sb.toString();
    }

    public CorrectionActionResult approve(Long requestId, Long actorTelegramUserId) {
        ProfileChangeRequest request = profileChangeRequestRepository.findById(requestId).orElse(null);
        if (request == null) {
            return new CorrectionActionResult("Profil o'zgartirish so'rovi topilmadi.", null, null);
        }
        if (request.getStatus() != CorrectionStatus.PENDING) {
            return new CorrectionActionResult("Bu so'rov allaqachon ko'rib chiqilgan.", null, null);
        }

        Employee target = request.getEmployee();
        target.setDepartment(request.getRequestedDepartment());
        target.setShift(request.getRequestedShift());
        employeeRepository.save(target);

        request.setStatus(CorrectionStatus.APPROVED);
        request.setReviewedAt(LocalDateTime.now(appClock));
        request.setReviewedByTelegramUserId(actorTelegramUserId);
        profileChangeRequestRepository.save(request);

        Employee actor = employeeRepository.findByTelegramUserId(actorTelegramUserId).orElse(null);
        auditLogService.logAction(
                AuditActionType.PROFILE_CHANGE_APPROVED,
                actor,
                target,
                "Bo'lim/smena o'zgartirildi: " + request.getRequestedDepartment()
                        + ", " + request.getRequestedShift().getDisplayName()
        );

        return new CorrectionActionResult(
                "Profil o'zgartirish so'rovi tasdiqlandi.",
                target.getChatId(),
                "Bo'lim/smena o'zgartirish so'rovingiz tasdiqlandi."
        );
    }

    public CorrectionActionResult reject(Long requestId, Long actorTelegramUserId) {
        ProfileChangeRequest request = profileChangeRequestRepository.findById(requestId).orElse(null);
        if (request == null) {
            return new CorrectionActionResult("Profil o'zgartirish so'rovi topilmadi.", null, null);
        }
        if (request.getStatus() != CorrectionStatus.PENDING) {
            return new CorrectionActionResult("Bu so'rov allaqachon ko'rib chiqilgan.", null, null);
        }

        request.setStatus(CorrectionStatus.REJECTED);
        request.setReviewedAt(LocalDateTime.now(appClock));
        request.setReviewedByTelegramUserId(actorTelegramUserId);
        profileChangeRequestRepository.save(request);

        Employee actor = employeeRepository.findByTelegramUserId(actorTelegramUserId).orElse(null);
        auditLogService.logAction(
                AuditActionType.PROFILE_CHANGE_REJECTED,
                actor,
                request.getEmployee(),
                "Bo'lim/smena o'zgartirish rad etildi."
        );

        return new CorrectionActionResult(
                "Profil o'zgartirish so'rovi rad etildi.",
                request.getEmployee().getChatId(),
                "Bo'lim/smena o'zgartirish so'rovingiz rad etildi."
        );
    }
}
