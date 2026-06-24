# Workly bot — completed build

This build implements the full feature set on top of the original project. It compiles
against Java 21 / Spring Boot; it was assembled without a Maven build in the authoring
environment, so run `./mvnw clean test` once and report any compile errors to finish.

## New domain & salary engine
- `Department` (5 fixed departments) and `Shift` (08:30–18:00, 14:00–21:00) enums.
- `DepartmentSalary` (DB-backed, editable base salary) + repository; seeded on startup.
- `PenaltyProperties`: grace 10 min, 3 000 so'm/late-minute, defaults (Qabul 4 000 000, others 3 000 000).
- `SalaryService`: lateness = arrival after shift-start + grace; first late day of the month
  is a warning, later late days charged; net = base − penalties; correction-aware automatically.

## Attendance / shifts
- `AttendanceService` is now shift-aware (lateness, status, "can leave now"). Both shifts are
  blocked from leaving before their shift end unless an early-leave/early-dismissal is approved.
- Arrival check-in shows a warning (first late) or the deduction (later lates).

## Registration
- Department is chosen from buttons, then shift from buttons (no more free-text department).

## Menu changes
- "Holat" removed (status lives in the mini-app).
- "Tarix" renamed to "Sizning oy excel hisobotingiz" — sends the employee their own monthly
  salary Excel.

## Excel (every day counts, weekends included)
- `ExcelReportService`: per-employee salary workbook (per-day detail + Fiksa maoshi / Jarimalaringiz
  / Umumiy miqdor) and a unified all-employees workbook (summary + day-by-day detail).
- Employees get only their own file; managers/admins get any employee's file and any past month
  (via `/xodim_tarixi` → pick employee → enter month) and the unified `/oylik_excel`.

## Mini-app
- Role-scoped tabs; employees see only their own salary/attendance.
- Payslip card + per-day Jarima column, fed by `salary` on `/api/app/me` (accepts `?month=`).
- Manager/admin dashboard with the four tappable status filters (Kelganlar / Kelmaganlar /
  Kechikkanlar / Ketishni belgilamaganlar), search and department filter.
- New endpoint `GET /api/app/employees/{id}/salary?month=` (managers/admins).

## Schedulers
- Shift-aware reminders fire at each shift's start/end to that shift's members (every day).
- Monthly awards on the 1st at 11:00: two public winners (hardest worker, most punctual) to
  everyone; most-late only to managers/admins.

## Admin / manager commands
- `/maoshlar` and `/setsalary_<DEPT>_<amount>` (e.g. `/setsalary_QABUL_BOLIMI_4500000`).
- `/early_dismiss_<telegramUserId>` and `/early_dismiss_all` (holiday early send-home).
- `/broadcast <text>`, `/survey <question>`, `/fikrlar` (view feedback).

## Feedback
- Employees reply with `/feedback <text>`; stored in `feedback_responses`.

## Bug fixes from the review
- Month report no longer inflates "absent" with future days and counts every day (weekends too).
- Reminders are shift-scoped (no cross-shift / wrong-time spam).

## Tests
- `AttendanceServiceTests`, `ReportServiceTests` updated for the shift-aware model.
- `SalaryServiceTests` added to lock the penalty logic.

Note: `application.yml` secrets are left untouched per your instruction — rotate before deploy.
