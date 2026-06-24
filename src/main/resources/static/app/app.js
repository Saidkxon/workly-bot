"use strict";

const telegram = window.Telegram?.WebApp;
telegram?.ready();
telegram?.expand();

const STATUS_LATE = "Kechikkan";
const STATUS_ABSENT = "Kelmagan";
const STATUS_MISSING_ARRIVAL = "Kelgan vaqt yo'q";
const STATUS_MISSING_CHECKOUT = "Ketgan vaqt yo'q";

const state = {
    initData: telegram?.initData || "",
    devUserId: new URLSearchParams(location.search).get("userId") || localStorage.getItem("worklyDevUserId") || "",
    isManager: false,
    employees: [],
    selectedEmployeeId: null,
    report: { rows: [], filter: null, query: "", dept: "all" },
};

const $ = (id) => document.getElementById(id);
const els = {
    profileAvatar: $("profileAvatar"), profileName: $("profileName"), profileSub: $("profileSub"),
    devAuth: $("devAuth"), devUserId: $("devUserId"), devLogin: $("devLogin"),
    tabs: $("tabs"), tabSelf: $("tabSelf"), tabTeam: $("tabTeam"),
    viewSelf: $("viewSelf"), viewTeam: $("viewTeam"),
    todayDate: $("todayDate"), timeline: $("timeline"), timelineFill: $("timelineFill"),
    timelineNow: $("timelineNow"), timelineIn: $("timelineIn"), timelineInLabel: $("timelineInLabel"),
    timelineOut: $("timelineOut"), timelineOutLabel: $("timelineOutLabel"), todayStats: $("todayStats"),
    payslipMonth: $("payslipMonth"), payslipBody: $("payslipBody"),
    selfMonth: $("selfMonth"), selfHistoryBody: $("selfHistoryBody"),
    metrics: $("metrics"),
    reportDate: $("reportDate"), filterCards: $("filterCards"),
    fcArrived: $("fcArrived"), fcAbsent: $("fcAbsent"), fcLate: $("fcLate"), fcMissing: $("fcMissing"),
    reportSearch: $("reportSearch"), reportDept: $("reportDept"), reportClear: $("reportClear"), reportBody: $("reportBody"),
    empMonth: $("empMonth"), empSelect: $("empSelect"), empHistoryBody: $("empHistoryBody"),
    activitiesCard: $("activitiesCard"), refreshActivities: $("refreshActivities"), activitiesBody: $("activitiesBody"),
    toast: $("toast"),
};

const thisMonth = new Date().toISOString().slice(0, 7);
els.selfMonth.value = thisMonth;
els.empMonth.value = thisMonth;
els.devUserId.value = state.devUserId;
els.devAuth.hidden = Boolean(state.initData);

/* ---------------- events ---------------- */
els.devLogin.addEventListener("click", () => {
    state.devUserId = els.devUserId.value.trim();
    localStorage.setItem("worklyDevUserId", state.devUserId);
    loadDashboard();
});
els.tabSelf.addEventListener("click", () => switchTab("self"));
els.tabTeam.addEventListener("click", () => switchTab("team"));
els.selfMonth.addEventListener("change", () => loadDashboard(els.selfMonth.value));
els.empSelect.addEventListener("change", () => {
    state.selectedEmployeeId = els.empSelect.value;
    loadEmployeeHistory();
});
els.empMonth.addEventListener("change", loadEmployeeHistory);
els.refreshActivities.addEventListener("click", loadActivities);
els.reportSearch.addEventListener("input", () => { state.report.query = els.reportSearch.value.trim().toLowerCase(); renderReportRows(); });
els.reportDept.addEventListener("change", () => { state.report.dept = els.reportDept.value; renderReportRows(); });
els.reportClear.addEventListener("click", clearReportFilters);
els.filterCards.querySelectorAll(".filter-card").forEach((card) => {
    card.addEventListener("click", () => toggleStatusFilter(card.dataset.filter));
});

loadDashboard();

/* ---------------- data ---------------- */
async function loadDashboard(month) {
    setLoading(true);
    clearToast();
    try {
        const data = await apiGet("/api/app/me", month ? { month } : {});
        renderProfile(data.employee);
        renderToday(data.today, data.todayDate);
        renderPayslip(data.salary, data.monthHistory, data.todayDate);
        renderSelfHistory(data.salary, data.monthHistory);

        state.isManager = Boolean(data.managerSummary);
        els.tabs.hidden = false;
        els.tabTeam.hidden = !state.isManager;

        if (state.isManager) {
            renderMetrics(data.managerSummary);
            renderEmployees(data.employees);
            setReport(data.todayReport);
            els.activitiesCard.hidden = data.employee.role !== "ADMIN";
            if (data.employee.role === "ADMIN") loadActivities();
        } else {
            switchTab("self");
        }
    } catch (error) {
        showToast(error.message);
        if (!state.initData) els.devAuth.hidden = false;
    } finally {
        setLoading(false);
    }
}

async function loadEmployeeHistory() {
    if (!state.selectedEmployeeId) { renderHistory(els.empHistoryBody, []); return; }
    setLoading(true);
    try {
        const rows = await apiGet(`/api/app/employees/${state.selectedEmployeeId}/history`, { month: els.empMonth.value });
        renderHistory(els.empHistoryBody, rows);
    } catch (error) {
        showToast(error.message);
    } finally {
        setLoading(false);
    }
}

async function loadActivities() {
    try {
        const rows = await apiGet("/api/app/activities", {});
        renderActivities(rows);
    } catch (error) {
        showToast(error.message);
    }
}

async function apiGet(path, params) {
    const url = new URL(path, location.origin);
    Object.entries(params || {}).forEach(([k, v]) => { if (v) url.searchParams.set(k, v); });
    if (!state.initData && state.devUserId && !url.searchParams.has("userId")) {
        url.searchParams.set("userId", state.devUserId);
    }
    const response = await fetch(url, { headers: state.initData ? { "X-Telegram-Init-Data": state.initData } : {} });
    if (!response.ok) throw new Error((await response.text()) || `So'rov bajarilmadi (${response.status})`);
    return response.json();
}

/* ---------------- render: profile / today ---------------- */
function renderProfile(e) {
    els.profileAvatar.textContent = initials(e.fullName);
    els.profileName.textContent = e.fullName;
    els.profileSub.textContent = [e.department, e.shift, roleLabel(e.role)].filter(Boolean).join(" · ");
}

function renderToday(today, date) {
    els.todayDate.textContent = date || "";
    if (!today) {
        els.todayStats.innerHTML = `<p class="empty" style="grid-column:1/-1">Bugun uchun yozuv yo'q.</p>`;
        renderTimeline(null, null);
        return;
    }
    els.todayStats.innerHTML =
        stat("Kelgan", today.arrivalTime || "—") +
        stat("Ketgan", today.leaveTime || "—") +
        stat("Ishlangan", today.workedTime) +
        statPill("Holat", today.status);
    renderTimeline(today.arrivalTime, today.leaveTime);
}

function renderTimeline(arrival, leave) {
    const startMin = 6 * 60, endMin = 23 * 60, span = endMin - startMin;
    const pos = (t) => Math.max(0, Math.min(100, ((toMinutes(t) - startMin) / span) * 100));
    const arr = toMinutes(arrival), lv = toMinutes(leave);
    const now = new Date();
    const nowMin = now.getHours() * 60 + now.getMinutes();

    place(els.timelineIn, arr != null, arr != null ? pos(arrival) : 0, els.timelineInLabel, fmtHm(arrival));
    place(els.timelineOut, lv != null, lv != null ? pos(leave) : 0, els.timelineOutLabel, fmtHm(leave));
    const showNow = nowMin >= startMin && nowMin <= endMin;
    place(els.timelineNow, showNow, showNow ? ((nowMin - startMin) / span) * 100 : 0);

    if (arr == null) { els.timelineFill.style.left = "0"; els.timelineFill.style.width = "0"; return; }
    const left = pos(arrival);
    const right = lv != null ? pos(leave) : Math.max(left, ((Math.min(nowMin, endMin) - startMin) / span) * 100);
    els.timelineFill.style.left = left + "%";
    els.timelineFill.style.width = Math.max(0, right - left) + "%";
}

function place(marker, show, leftPct, labelEl, labelText) {
    marker.hidden = !show;
    if (!show) return;
    marker.style.left = leftPct + "%";
    if (labelEl && labelText != null) labelEl.textContent = labelText;
}

/* ---------------- render: payslip ---------------- */
function renderPayslip(salary, monthHistory, date) {
    els.payslipMonth.textContent = salary?.month || (date ? date.slice(0, 7) : thisMonth);
    if (!salary) {
        els.payslipBody.innerHTML = `<p class="payslip-empty">Maosh hisob-kitobi tez orada shu yerda ko'rinadi.</p>`;
        return;
    }
    const note = [];
    note.push(chip(`${plural(salary.lateDays, "kechikkan kun")}`));
    if (salary.penalizedDays > 0) note.push(chip(`${plural(salary.penalizedDays, "jarimali kun")}`, "warn"));
    note.push(chip(`Ishlangan: ${formatMinutes(salary.totalWorkedMinutes)}`));

    els.payslipBody.innerHTML = `
        <div class="ledger">
            <div class="ledger-row"><span class="lbl">Sizning fiksa maoshingiz</span><span class="val">${formatSum(salary.baseSalary)}</span></div>
            <div class="ledger-row deduct"><span class="lbl">Jarimalaringiz</span><span class="val">${salary.totalDeduction ? "−" + formatSum(salary.totalDeduction) : formatSum(0)}</span></div>
            <div class="ledger-row total"><span class="lbl">Umumiy miqdor</span><span class="val">${formatSum(salary.netSalary)}</span></div>
        </div>
        <div class="payslip-note">${note.join("")}</div>`;
}

/* ---------------- render: self month history ---------------- */
function renderSelfHistory(salary, monthHistory) {
    if (salary?.days?.length) {
        els.selfHistoryBody.innerHTML = salary.days.map((d) => {
            const status = statusFromDay(d);
            return `<tr>
                <td>${esc(d.date)}</td>
                <td>${esc(d.arrival || "—")}</td>
                <td>${esc(d.leave || "—")}</td>
                <td>${esc(formatMinutes(d.workedMinutes))}</td>
                <td>${esc(d.lateMinutes ? formatMinutes(d.lateMinutes) : "—")}</td>
                <td class="${d.deduction ? "cell-deduct" : "cell-zero"}">${d.deduction ? "−" + formatSum(d.deduction) : "—"}</td>
                <td>${pill(status)}</td></tr>`;
        }).join("");
        if (!salary.days.length) emptyRow(els.selfHistoryBody, 7);
        return;
    }
    // fallback before salary is wired into the backend
    const rows = monthHistory || [];
    if (!rows.length) return emptyRow(els.selfHistoryBody, 7);
    els.selfHistoryBody.innerHTML = rows.map((r) => `<tr>
        <td>${esc(r.date)}</td><td>${esc(r.arrivalTime || "—")}</td><td>${esc(r.leaveTime || "—")}</td>
        <td>${esc(r.workedTime)}</td><td>${esc(r.lateTime)}</td><td class="cell-zero">—</td><td>${pill(r.status)}</td></tr>`).join("");
}

/* ---------------- render: team ---------------- */
function renderMetrics(s) {
    els.metrics.innerHTML =
        metric("Faol xodimlar", s.activeEmployees) +
        metric("Yangi xodimlar", s.pendingRegistrations) +
        metric("Tuzatishlar", s.pendingCorrections) +
        metric("Erta ketish", s.pendingEarlyLeaves);
}

function renderEmployees(employees) {
    state.employees = employees || [];
    els.empSelect.innerHTML = state.employees
        .map((e) => `<option value="${e.telegramUserId}">${esc(e.fullName)} · ${esc(e.department)}</option>`)
        .join("");
    state.selectedEmployeeId = state.employees[0]?.telegramUserId || null;
    if (state.selectedEmployeeId) {
        els.empSelect.value = String(state.selectedEmployeeId);
        loadEmployeeHistory();
    } else {
        emptyRow(els.empHistoryBody, 6);
    }
}

function setReport(report) {
    state.report.rows = report?.rows || [];
    els.reportDate.textContent = report?.date || "";
    els.fcArrived.textContent = report?.arrived ?? 0;
    els.fcAbsent.textContent = report?.absent ?? 0;
    els.fcLate.textContent = report?.late ?? 0;
    els.fcMissing.textContent = report?.missingCheckout ?? 0;

    const depts = [...new Set(state.report.rows.map((r) => r.department).filter(Boolean))].sort();
    els.reportDept.innerHTML = `<option value="all">Barcha bo'limlar</option>` +
        depts.map((d) => `<option value="${esc(d)}">${esc(d)}</option>`).join("");
    renderReportRows();
}

function toggleStatusFilter(filter) {
    state.report.filter = state.report.filter === filter ? null : filter;
    els.filterCards.querySelectorAll(".filter-card").forEach((c) =>
        c.classList.toggle("is-on", c.dataset.filter === state.report.filter));
    renderReportRows();
}

function clearReportFilters() {
    state.report.filter = null;
    state.report.query = "";
    state.report.dept = "all";
    els.reportSearch.value = "";
    els.reportDept.value = "all";
    els.filterCards.querySelectorAll(".filter-card").forEach((c) => c.classList.remove("is-on"));
    renderReportRows();
}

function renderReportRows() {
    const rows = state.report.rows.filter(matchesReportFilters);
    if (!state.report.rows.length) return emptyRow(els.reportBody, 6, "Bugungi report uchun ma'lumot yo'q.");
    if (!rows.length) return emptyRow(els.reportBody, 6, "Tanlangan filtr bo'yicha hech kim yo'q.");
    els.reportBody.innerHTML = rows.map((r) => `<tr>
        <td>${esc(r.fullName)}</td><td>${esc(r.department)}</td>
        <td>${esc(r.arrivalTime || "—")}</td><td>${esc(r.leaveTime || "—")}</td>
        <td>${esc(r.lateTime)}</td><td>${pill(r.status)}</td></tr>`).join("");
}

function matchesReportFilters(r) {
    const f = state.report.filter;
    if (f === "arrived" && !r.arrivalTime) return false;
    if (f === "absent" && r.arrivalTime) return false;
    if (f === "late" && r.status !== STATUS_LATE) return false;
    if (f === "missing" && !(r.arrivalTime && !r.leaveTime)) return false;
    if (state.report.dept !== "all" && r.department !== state.report.dept) return false;
    if (state.report.query) {
        const hay = `${r.fullName || ""} ${r.department || ""}`.toLowerCase();
        if (!hay.includes(state.report.query)) return false;
    }
    return true;
}

function renderHistory(body, rows) {
    if (!rows || !rows.length) return emptyRow(body, 6);
    body.innerHTML = rows.map((r) => `<tr>
        <td>${esc(r.date)}</td><td>${esc(r.arrivalTime || "—")}</td><td>${esc(r.leaveTime || "—")}</td>
        <td>${esc(r.workedTime)}</td><td>${esc(r.lateTime)}</td><td>${pill(r.status)}</td></tr>`).join("");
}

function renderActivities(rows) {
    if (!rows || !rows.length) return emptyRow(els.activitiesBody, 4, "Faoliyat hozircha yo'q.");
    els.activitiesBody.innerHTML = rows.map((r) => `<tr>
        <td>${esc(fmtDateTime(r.createdAt))}</td><td>${esc(r.actorName)}</td>
        <td>${esc(r.actorTelegramUserId)}</td><td>${esc(r.details)}</td></tr>`).join("");
}

/* ---------------- tabs ---------------- */
function switchTab(which) {
    const self = which === "self";
    els.tabSelf.classList.toggle("is-active", self);
    els.tabTeam.classList.toggle("is-active", !self);
    els.tabSelf.setAttribute("aria-selected", String(self));
    els.tabTeam.setAttribute("aria-selected", String(!self));
    els.viewSelf.classList.toggle("is-active", self);
    els.viewTeam.classList.toggle("is-active", !self);
    els.viewSelf.hidden = !self;
    els.viewTeam.hidden = self;
}

/* ---------------- small builders ---------------- */
function stat(label, value) { return `<div class="stat"><span>${esc(label)}</span><strong>${esc(value)}</strong></div>`; }
function statPill(label, status) { return `<div class="stat"><span>${esc(label)}</span><strong>${pill(status)}</strong></div>`; }
function metric(label, value) { return `<div class="metric"><span>${esc(label)}</span><strong>${esc(value)}</strong></div>`; }
function chip(text, cls) { return `<span class="chip ${cls || ""}">${esc(text)}</span>`; }
function emptyRow(body, cols, text) { body.innerHTML = `<tr><td colspan="${cols}" class="empty">${esc(text || "Bu oy uchun yozuv yo'q.")}</td></tr>`; }

function pill(status) {
    const cls = status === "Vaqtida" ? "ok"
        : status === STATUS_LATE ? "late"
        : status === STATUS_MISSING_CHECKOUT ? "warn"
        : (status === STATUS_ABSENT || status === STATUS_MISSING_ARRIVAL) ? "absent"
        : "ok";
    return `<span class="pill ${cls}">${esc(status)}</span>`;
}

function statusFromDay(d) {
    if (!d.arrival && !d.leave) return STATUS_ABSENT;
    if (!d.arrival) return STATUS_MISSING_ARRIVAL;
    if (d.lateMinutes > 0) return STATUS_LATE;
    if (!d.leave) return STATUS_MISSING_CHECKOUT;
    return "Vaqtida";
}

/* ---------------- format helpers ---------------- */
function formatSum(amount) {
    const neg = amount < 0;
    const digits = String(Math.abs(Math.round(amount)));
    let out = "", c = 0;
    for (let i = digits.length - 1; i >= 0; i--) { out = digits[i] + out; if (++c % 3 === 0 && i > 0) out = " " + out; }
    return (neg ? "−" : "") + out + " so'm";
}
function formatMinutes(total) { total = total || 0; return `${Math.floor(total / 60)} soat ${total % 60} daqiqa`; }
function plural(n, word) { return `${n} ${word}`; }
function roleLabel(role) { return role === "ADMIN" ? "Admin" : role === "MANAGER" ? "Menejer" : "Xodim"; }
function initials(name) { return (name || "?").trim().split(/\s+/).slice(0, 2).map((p) => p[0] || "").join("") || "?"; }
function toMinutes(t) { if (!t) return null; const [h, m] = String(t).split(":"); return Number(h) * 60 + Number(m); }
function fmtHm(t) { return t ? String(t).slice(0, 5) : ""; }
function fmtDateTime(v) { return v ? String(v).replace("T", " ").slice(0, 16) : ""; }
function esc(v) {
    return String(v ?? "").replaceAll("&", "&amp;").replaceAll("<", "&lt;").replaceAll(">", "&gt;").replaceAll('"', "&quot;").replaceAll("'", "&#039;");
}

/* ---------------- ui state ---------------- */
function setLoading(on) {
    document.body.classList.toggle("loading", on);
    els.devLogin.disabled = on;
    els.refreshActivities.disabled = on;
}
let toastTimer;
function showToast(message) {
    els.toast.hidden = false;
    els.toast.textContent = message;
    clearTimeout(toastTimer);
    toastTimer = setTimeout(clearToast, 4000);
}
function clearToast() { els.toast.hidden = true; els.toast.textContent = ""; }
