package com.advancedprogramming.worklybot.service;

import com.advancedprogramming.worklybot.entity.Employee;
import com.advancedprogramming.worklybot.entity.enums.Role;
import com.advancedprogramming.worklybot.repository.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class EmployeeService {

    private final EmployeeRepository employeeRepository;

    public String getAllEmployeesText() {
        List<Employee> employees = employeeRepository.findAllByOrderByFullNameAsc();

        if (employees.isEmpty()) {
            return "Xodimlar topilmadi.";
        }

        StringBuilder sb = new StringBuilder("Xodimlar ro'yxati:\n\n");

        for (Employee employee : employees) {
            sb.append("Ism familiya: ").append(employee.getFullName()).append("\n")
                    .append("Bo'lim: ").append(employee.getDepartment()).append("\n")
                    .append("Telegram user ID: ").append(employee.getTelegramUserId()).append("\n")
                    .append("Rol: ").append(employee.getRole()).append("\n")
                    .append("Faolligi: ").append(employee.isActive() ? "HA" : "YO'Q").append("\n")
                    .append("O'chirish: /deactivate_").append(employee.getTelegramUserId()).append("\n")
                    .append("Faollashtirish: /activate_").append(employee.getTelegramUserId()).append("\n")
                    .append("Manager qilish: /make_manager_").append(employee.getTelegramUserId()).append("\n")
                    .append("Employee qilish: /make_employee_").append(employee.getTelegramUserId()).append("\n")
                    .append("Admin qilish: /make_admin_").append(employee.getTelegramUserId()).append("\n")
                    .append("----------------------\n");
        }

        return sb.toString();
    }

    public String activateEmployee(Long actorTelegramUserId, Long targetTelegramUserId) {
        Employee actor = employeeRepository.findByTelegramUserId(actorTelegramUserId).orElse(null);
        Employee target = employeeRepository.findByTelegramUserId(targetTelegramUserId).orElse(null);

        if (actor == null || target == null) {
            return "Xodim topilmadi.";
        }

        if (actor.getRole() == Role.EMPLOYEE) {
            return "Sizda foydalanuvchini faollashtirish huquqi yo'q.";
        }

        if (target.getRole() == Role.ADMIN && actor.getRole() != Role.ADMIN) {
            return "Faqat ADMIN ADMIN foydalanuvchini boshqara oladi.";
        }

        if (target.isActive()) {
            return "Xodim allaqachon faol.";
        }

        target.setActive(true);
        employeeRepository.save(target);

        return "Xodim faollashtirildi: " + target.getFullName();
    }

    public String deactivateEmployee(Long actorTelegramUserId, Long targetTelegramUserId) {
        Employee actor = employeeRepository.findByTelegramUserId(actorTelegramUserId).orElse(null);
        Employee target = employeeRepository.findByTelegramUserId(targetTelegramUserId).orElse(null);

        if (actor == null || target == null) {
            return "Xodim topilmadi.";
        }

        if (actor.getRole() == Role.EMPLOYEE) {
            return "Sizda foydalanuvchini o'chirish huquqi yo'q.";
        }

        if (actor.getTelegramUserId().equals(targetTelegramUserId)) {
            return "O'zingizni o'chira olmaysiz.";
        }

        if (target.getRole() == Role.ADMIN) {
            return "ADMIN foydalanuvchini bu buyruq bilan o'chirib bo'lmaydi.";
        }

        if (actor.getRole() == Role.MANAGER && target.getRole() == Role.MANAGER) {
            return "MANAGER boshqa MANAGER ni o'chira olmaydi.";
        }

        if (!target.isActive()) {
            return "Xodim allaqachon faol emas.";
        }

        target.setActive(false);
        employeeRepository.save(target);

        return "Xodim o'chirildi: " + target.getFullName();
    }
}