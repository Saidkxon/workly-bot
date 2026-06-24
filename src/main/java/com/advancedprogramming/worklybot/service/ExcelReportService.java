package com.advancedprogramming.worklybot.service;

import com.advancedprogramming.worklybot.entity.Employee;
import com.advancedprogramming.worklybot.repository.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.time.DayOfWeek;
import java.time.YearMonth;
import java.util.List;

/**
 * Excel reports with salary analysis. Every day with attendance counts (weekends included).
 *   - {@link #buildEmployeeSalaryWorkbook} : one employee, their own monthly file with the
 *     salary block (Fiksa maoshi / Jarimalar / Umumiy miqdor).
 *   - {@link #buildAllEmployeesSalaryWorkbook} : unified file with a per-employee summary
 *     sheet and a day-by-day detail sheet.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ExcelReportService {

    private final EmployeeRepository employeeRepository;
    private final SalaryService salaryService;

    public byte[] buildEmployeeSalaryWorkbook(Employee employee, YearMonth month) {
        MonthlySalaryBreakdown breakdown = salaryService.computeBreakdown(employee, month);

        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            CellStyle bold = boldStyle(workbook);
            Sheet sheet = workbook.createSheet("Hisobot");

            int r = 0;
            r = titleRow(sheet, r, bold, "Xodim:", employee.getFullName());
            r = titleRow(sheet, r, bold, "Bo'lim:", employee.getDepartment());
            r = titleRow(sheet, r, bold, "Smena:", breakdown.shiftName());
            r = titleRow(sheet, r, bold, "Oy:", month.toString());
            r++;

            String[] headers = {"Sana", "Hafta kuni", "Kelgan", "Ketgan", "Ishlangan", "Kechikish", "Jarima", "Holat"};
            writeHeader(sheet.createRow(r++), bold, headers);

            for (SalaryDayRow day : breakdown.days()) {
                Row row = sheet.createRow(r++);
                row.createCell(0).setCellValue(day.date().toString());
                row.createCell(1).setCellValue(weekday(day.date().getDayOfWeek()));
                row.createCell(2).setCellValue(day.arrival() == null ? "Belgilanmagan" : day.arrival().toString());
                row.createCell(3).setCellValue(day.leave() == null ? "Belgilanmagan" : day.leave().toString());
                row.createCell(4).setCellValue(SalaryService.formatMinutes(day.workedMinutes()));
                row.createCell(5).setCellValue(day.lateMinutes() == 0 ? "-" : day.lateMinutes() + " daqiqa");
                row.createCell(6).setCellValue(dayPenaltyText(day));
                row.createCell(7).setCellValue(statusText(day));
            }

            r++;
            r = titleRow(sheet, r, bold, "Kelgan kunlar:", String.valueOf(breakdown.days().size()));
            r = titleRow(sheet, r, bold, "Kechikkan kunlar:", String.valueOf(breakdown.lateDays()));
            r = titleRow(sheet, r, bold, "Jarimali kunlar:", String.valueOf(breakdown.penalizedDays()));
            r = titleRow(sheet, r, bold, "Jami ishlangan:", SalaryService.formatMinutes(breakdown.totalWorkedMinutes()));
            r++;
            r = titleRow(sheet, r, bold, "Sizning fiksa maoshingiz:", SalaryService.formatSum(breakdown.baseSalary()));
            r = titleRow(sheet, r, bold, "Jarimalaringiz:", SalaryService.formatSum(breakdown.totalDeduction()));
            r = titleRow(sheet, r, bold, "Umumiy miqdor:", SalaryService.formatSum(breakdown.netSalary()));

            autoSize(sheet, headers.length);
            workbook.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            log.error("Failed to build employee salary workbook for {}", employee.getTelegramUserId(), e);
            throw new IllegalStateException("Failed to build employee salary workbook", e);
        }
    }

    public byte[] buildAllEmployeesSalaryWorkbook(YearMonth month) {
        List<Employee> employees = employeeRepository.findAllByActiveTrueOrderByFullNameAsc();
        if (employees.isEmpty()) {
            return null;
        }

        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            CellStyle bold = boldStyle(workbook);

            Sheet summary = workbook.createSheet("Umumiy");
            String[] summaryHeaders = {
                    "Ism familiya", "Bo'lim", "Smena", "Kelgan kunlar", "Kechikkan kunlar",
                    "Jarimali kunlar", "Jami ishlangan", "Jami kechikish (daq)",
                    "Fiksa maoshi", "Jarimalar", "Umumiy miqdor"
            };
            writeHeader(summary.createRow(0), bold, summaryHeaders);

            Sheet detail = workbook.createSheet("Tafsilot");
            String[] detailHeaders = {"Sana", "Ism familiya", "Bo'lim", "Kelgan", "Ketgan", "Ishlangan", "Kechikish (daq)", "Jarima"};
            writeHeader(detail.createRow(0), bold, detailHeaders);

            int sRow = 1;
            int dRow = 1;
            for (Employee employee : employees) {
                MonthlySalaryBreakdown breakdown = salaryService.computeBreakdown(employee, month);

                Row row = summary.createRow(sRow++);
                row.createCell(0).setCellValue(breakdown.fullName());
                row.createCell(1).setCellValue(employee.getDepartment());
                row.createCell(2).setCellValue(breakdown.shiftName());
                row.createCell(3).setCellValue(breakdown.days().size());
                row.createCell(4).setCellValue(breakdown.lateDays());
                row.createCell(5).setCellValue(breakdown.penalizedDays());
                row.createCell(6).setCellValue(SalaryService.formatMinutes(breakdown.totalWorkedMinutes()));
                row.createCell(7).setCellValue(breakdown.totalLateMinutes());
                row.createCell(8).setCellValue(SalaryService.formatSum(breakdown.baseSalary()));
                row.createCell(9).setCellValue(SalaryService.formatSum(breakdown.totalDeduction()));
                row.createCell(10).setCellValue(SalaryService.formatSum(breakdown.netSalary()));

                for (SalaryDayRow day : breakdown.days()) {
                    Row d = detail.createRow(dRow++);
                    d.createCell(0).setCellValue(day.date().toString());
                    d.createCell(1).setCellValue(breakdown.fullName());
                    d.createCell(2).setCellValue(employee.getDepartment());
                    d.createCell(3).setCellValue(day.arrival() == null ? "Belgilanmagan" : day.arrival().toString());
                    d.createCell(4).setCellValue(day.leave() == null ? "Belgilanmagan" : day.leave().toString());
                    d.createCell(5).setCellValue(SalaryService.formatMinutes(day.workedMinutes()));
                    d.createCell(6).setCellValue(day.lateMinutes());
                    d.createCell(7).setCellValue(dayPenaltyText(day));
                }
            }

            autoSize(summary, summaryHeaders.length);
            autoSize(detail, detailHeaders.length);
            workbook.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            log.error("Failed to build all-employees salary workbook", e);
            throw new IllegalStateException("Failed to build all-employees salary workbook", e);
        }
    }

    // ---- helpers ---------------------------------------------------------------

    private String dayPenaltyText(SalaryDayRow day) {
        if (day.warning()) {
            return "Ogohlantirish";
        }
        return day.deduction() == 0 ? "-" : SalaryService.formatSum(day.deduction());
    }

    private String statusText(SalaryDayRow day) {
        if (day.arrival() == null) {
            return "Kelgan vaqt yo'q";
        }
        if (day.lateMinutes() > 0) {
            return "Kechikkan";
        }
        if (day.leave() == null) {
            return "Ketgan vaqt yo'q";
        }
        return "Vaqtida";
    }

    private String weekday(DayOfWeek dayOfWeek) {
        return switch (dayOfWeek) {
            case MONDAY -> "Dushanba";
            case TUESDAY -> "Seshanba";
            case WEDNESDAY -> "Chorshanba";
            case THURSDAY -> "Payshanba";
            case FRIDAY -> "Juma";
            case SATURDAY -> "Shanba";
            case SUNDAY -> "Yakshanba";
        };
    }

    private int titleRow(Sheet sheet, int rowIndex, CellStyle bold, String label, String value) {
        Row row = sheet.createRow(rowIndex);
        Cell labelCell = row.createCell(0);
        labelCell.setCellValue(label);
        labelCell.setCellStyle(bold);
        row.createCell(1).setCellValue(value);
        return rowIndex + 1;
    }

    private void writeHeader(Row row, CellStyle bold, String[] headers) {
        for (int i = 0; i < headers.length; i++) {
            Cell cell = row.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(bold);
        }
    }

    private CellStyle boldStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        return style;
    }

    private void autoSize(Sheet sheet, int columns) {
        for (int i = 0; i < columns; i++) {
            try {
                sheet.autoSizeColumn(i);
            } catch (Exception ignored) {
                // headless font metrics can fail; ignore and keep default width
            }
        }
    }
}
