package com.advancedprogramming.worklybot.service;

import com.advancedprogramming.worklybot.bot.state.UserSession;
import com.advancedprogramming.worklybot.entity.Attendance;
import com.advancedprogramming.worklybot.entity.CorrectionRequest;
import com.advancedprogramming.worklybot.entity.Employee;
import com.advancedprogramming.worklybot.entity.enums.AuditActionType;
import com.advancedprogramming.worklybot.entity.enums.CorrectionStatus;
import com.advancedprogramming.worklybot.repository.AttendanceRepository;
import com.advancedprogramming.worklybot.repository.CorrectionRequestRepository;
import com.advancedprogramming.worklybot.repository.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CorrectionService {

    private final CorrectionRequestRepository correctionRequestRepository;
    private final AttendanceRepository attendanceRepository;
    private final EmployeeRepository employeeRepository;
    private final AuditLogService auditLogService;
    private final Clock appClock;

    public String createCorrectionRequest(Employee employee, UserSession session, String text) {
        LocalDate workDate = session.getCorrectionDate();
        if (workDate == null) {
            return "Avval tuzatiladigan sanani kiriting.";
        }

        if (workDate.isAfter(LocalDate.now(appClock))) {
            return "Kelajak sana uchun tuzatish so'rovi yuborib bo'lmaydi.";
        }

        if (correctionRequestRepository.existsByEmployeeAndWorkDateAndStatus(employee, workDate, CorrectionStatus.PENDING)) {
            return "Bu sana uchun kutilayotgan tuzatish so'rovi allaqachon mavjud.";
        }

        try {
            LocalTime requestedArrival = null;
            LocalTime requestedLeaving = null;

            if ("1".equals(session.getCorrectionType())) {
                requestedArrival = LocalTime.parse(text, DateTimeFormatter.ofPattern("HH:mm"));
            } else if ("2".equals(session.getCorrectionType())) {
                requestedLeaving = LocalTime.parse(text, DateTimeFormatter.ofPattern("HH:mm"));
            } else if ("3".equals(session.getCorrectionType())) {
                String[] parts = text.split(",");
                if (parts.length != 2) {
                    return "Format noto'g'ri. HH:mm,HH:mm ko'rinishida yuboring.";
                }

                requestedArrival = LocalTime.parse(parts[0].trim(), DateTimeFormatter.ofPattern("HH:mm"));
                requestedLeaving = LocalTime.parse(parts[1].trim(), DateTimeFormatter.ofPattern("HH:mm"));
            }

            if (requestedArrival != null && requestedLeaving != null && requestedLeaving.isBefore(requestedArrival)) {
                return "Ketish vaqti kelish vaqtidan oldin bo'lishi mumkin emas.";
            }

            Attendance currentAttendance = attendanceRepository.findByEmployeeAndWorkDate(employee, workDate).orElse(null);
            LocalTime effectiveArrival = requestedArrival;
            LocalTime effectiveLeaving = requestedLeaving;

            if (currentAttendance != null) {
                if (effectiveArrival == null && currentAttendance.getArrivalTime() != null) {
                    effectiveArrival = currentAttendance.getArrivalTime().toLocalTime();
                }

                if (effectiveLeaving == null && currentAttendance.getLeaveTime() != null) {
                    effectiveLeaving = currentAttendance.getLeaveTime().toLocalTime();
                }
            }

            if (effectiveArrival != null && effectiveLeaving != null && effectiveLeaving.isBefore(effectiveArrival)) {
                return "Natijaviy kelish va ketish vaqtlari noto'g'ri tartibda bo'ladi.";
            }

            CorrectionRequest correctionRequest = CorrectionRequest.builder()
                    .employee(employee)
                    .workDate(workDate)
                    .requestedArrivalTime(requestedArrival)
                    .requestedLeaveTime(requestedLeaving)
                    .reason("Xodim tuzatish so'rovi yubordi")
                    .status(CorrectionStatus.PENDING)
                    .createdAt(LocalDateTime.now(appClock))
                    .build();

            correctionRequestRepository.save(correctionRequest);

            return "Tuzatish so'rovi yuborildi.\nMenejer tasdiqlashini kuting.";
        } catch (DateTimeParseException e) {
            return "Vaqt formati noto'g'ri. HH:mm formatidan foydalaning.";
        }
    }

    public String getPendingCorrectionsText() {
        List<CorrectionRequest> requests = correctionRequestRepository.findAllByStatusOrderByCreatedAtAsc(CorrectionStatus.PENDING);

        if (requests.isEmpty()) {
            return "Kutilayotgan tuzatish so'rovlari yo'q.";
        }

        StringBuilder sb = new StringBuilder("Kutilayotgan tuzatish so'rovlari:\n\n");

        for (CorrectionRequest request : requests) {
            sb.append("So'rov ID: ").append(request.getId()).append("\n")
                    .append("Xodim: ").append(request.getEmployee().getFullName()).append("\n")
                    .append("Bo'lim: ").append(request.getEmployee().getDepartment()).append("\n")
                    .append("Sana: ").append(request.getWorkDate()).append("\n")
                    .append("So'ralgan kelgan vaqt: ")
                    .append(request.getRequestedArrivalTime() == null ? "O'zgarmaydi" : request.getRequestedArrivalTime())
                    .append("\n")
                    .append("So'ralgan ketgan vaqt: ")
                    .append(request.getRequestedLeaveTime() == null ? "O'zgarmaydi" : request.getRequestedLeaveTime())
                    .append("\n")
                    .append("Tasdiqlash: /approve_").append(request.getId()).append("\n")
                    .append("Rad etish: /reject_").append(request.getId()).append("\n")
                    .append("----------------------\n");
        }

        return sb.toString();
    }

    public CorrectionRequest findLatestPendingRequest(Employee employee, LocalDate workDate) {
        return correctionRequestRepository
                .findTopByEmployeeAndWorkDateAndStatusOrderByCreatedAtDesc(employee, workDate, CorrectionStatus.PENDING)
                .orElse(null);
    }

    public CorrectionActionResult approveCorrection(Long requestId, Long actorTelegramUserId) {
        CorrectionRequest request = correctionRequestRepository.findById(requestId).orElse(null);

        if (request == null) {
            return new CorrectionActionResult("Tuzatish so'rovi topilmadi.", null, null);
        }

        if (request.getStatus() != CorrectionStatus.PENDING) {
            return new CorrectionActionResult("Bu so'rov allaqachon ko'rib chiqilgan.", null, null);
        }

        Attendance attendance = attendanceRepository
                .findByEmployeeAndWorkDate(request.getEmployee(), request.getWorkDate())
                .orElse(null);

        if (attendance == null) {
            attendance = Attendance.builder()
                    .employee(request.getEmployee())
                    .workDate(request.getWorkDate())
                    .build();
        }

        if (request.getRequestedArrivalTime() != null) {
            attendance.setArrivalTime(LocalDateTime.of(request.getWorkDate(), request.getRequestedArrivalTime()));
        }

        if (request.getRequestedLeaveTime() != null) {
            attendance.setLeaveTime(LocalDateTime.of(request.getWorkDate(), request.getRequestedLeaveTime()));
        }

        attendanceRepository.save(attendance);

        request.setStatus(CorrectionStatus.APPROVED);
        request.setReviewedAt(LocalDateTime.now(appClock));
        request.setReviewedByTelegramUserId(actorTelegramUserId);
        correctionRequestRepository.save(request);

        Employee actor = employeeRepository.findByTelegramUserId(actorTelegramUserId).orElse(null);
        auditLogService.logAction(
                AuditActionType.CORRECTION_APPROVED,
                actor,
                request.getEmployee(),
                "Sana: " + request.getWorkDate()
        );

        return new CorrectionActionResult(
                "Tuzatish so'rovi tasdiqlandi.",
                request.getEmployee().getChatId(),
                "Sizning tuzatish so'rovingiz tasdiqlandi. Sana: " + request.getWorkDate()
        );
    }

    public CorrectionActionResult rejectCorrection(Long requestId, Long actorTelegramUserId) {
        CorrectionRequest request = correctionRequestRepository.findById(requestId).orElse(null);

        if (request == null) {
            return new CorrectionActionResult("Tuzatish so'rovi topilmadi.", null, null);
        }

        if (request.getStatus() != CorrectionStatus.PENDING) {
            return new CorrectionActionResult("Bu so'rov allaqachon ko'rib chiqilgan.", null, null);
        }

        request.setStatus(CorrectionStatus.REJECTED);
        request.setReviewedAt(LocalDateTime.now(appClock));
        request.setReviewedByTelegramUserId(actorTelegramUserId);
        correctionRequestRepository.save(request);

        Employee actor = employeeRepository.findByTelegramUserId(actorTelegramUserId).orElse(null);
        auditLogService.logAction(
                AuditActionType.CORRECTION_REJECTED,
                actor,
                request.getEmployee(),
                "Sana: " + request.getWorkDate()
        );

        return new CorrectionActionResult(
                "Tuzatish so'rovi rad etildi.",
                request.getEmployee().getChatId(),
                "Sizning tuzatish so'rovingiz rad etildi. Sana: " + request.getWorkDate()
        );
    }
}
