package com.advancedprogramming.worklybot.repository;

import com.advancedprogramming.worklybot.entity.Attendance;
import com.advancedprogramming.worklybot.entity.Employee;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface AttendanceRepository extends JpaRepository<Attendance, Long> {
    Optional<Attendance> findByEmployeeAndWorkDate(Employee employee, LocalDate workDate);
    List<Attendance> findAllByWorkDate(LocalDate workDate);
    List<Attendance> findAllByWorkDateBetween(LocalDate startDate, LocalDate endDate);
}
