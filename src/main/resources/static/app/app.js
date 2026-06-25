"use strict";

const telegram = window.Telegram?.WebApp;
telegram?.ready();
telegram?.expand();

const STATUS_LATE = "Kechikkan";
const STATUS_ABSENT = "Kelmagan";
const STATUS_MISSING_ARRIVAL = "Kelgan vaqt yo'q";
const STATUS_MISSING_CHECKOUT = "Ketgan vaqt yo'q";

const reduceMotion = window.matchMedia?.("(prefers-reduced-motion: reduce)").matches;

const state = {
    initData: telegram?.initData || "",
    devUserId: new URLSearchParams(location.search).get("userId") || localStorage.getItem("worklyDevUserId") || "",
    isManager: false,
    employees: [],
    selectedEmployeeId: null,
    report: { rows: [], filter: null, query: "", dept: "all" },
    currentMonth: null,
};

const $ = (id) => document.getElementById(id);
const els = {
    profileAvatar: $("profileAvatar"), profileName: $("profileName"), profileSub: $("profileSub"),
    themeBtn: $("themeBtn"), refreshBtn: $("refreshBtn"),
    devAuth: $("devAuth"), devUserId: $("devUserId"), devLogin: $("devLogin"),
    tabs: $("tabs"), tabSelf: $("tabSelf"), tabTeam: $("tabTeam"),
    viewSelf: $("viewSelf"), viewTeam: $("viewTeam"),
    todayDate: $("todayDate"), timeline: $("timeline"), timelineFill: $("timelineFill"),
    timelineNow: $("timelineNow"), timelineIn: $("timelineIn"), timelineInLabel: $("timelineInLabel"),
    timelineOut: $("timelineOut"), timelineOutLabel: $("timelineOutLabel"), todayStats: $("todayStats"),
    performanceMonth: $("performanceMonth"), performanceBody: $("performanceBody"),
    payslipMonth: $("payslipMonth"), payslipBody: $("payslipBody"),
    selfMonth: $("selfMonth"), selfHistoryBody: $("selfHistoryBody"),
    pulseCard: $("pulseCard"), pulseRing: $("pulseRing"), pulseLegend: $("pulseLegend"),
    metrics: $("metrics"),
    reportDate: $("reportDate"), filterCards: $("filterCards"),
    fcArrived: $("fcArrived"), fcAbsent: $("fcAbsent"), fcLate: $("fcLate"), fcMissing: $("fcMissing"),
    reportSearch: $("reportSearch"), reportDept: $("reportDept"), reportClear: $("reportClear"), reportBody: $("reportBody"),
    empMonth: $("empMonth"), empSelect: $("empSelect"), empPerformance: $("empPerformance"), empHistoryBody: $("empHistoryBody"),
    activitiesCard: $("activitiesCard"), refreshActivities: $("refreshActivities"), activitiesBody: $("activitiesBody"),
    auditCard: $("auditCard"), refreshAudit: $("refreshAudit"), auditBody: $("auditBody"),
    feedbackCard: $("feedbackCard"), refreshFeedbacks: $("refreshFeedbacks"), feedbackBody: $("feedbackBody"),
    toast: $("toast"),
};

const thisMonth = new Date().toISOString().slice(0, 7);
els.selfMonth.value = thisMonth;
els.empMonth.value = thisMonth;
els.devUserId.value = state.devUserId;
els.devAuth.hidden = Boolean(state.initData);

/* ---------------- theme ---------------- */
const THEME_KEY = "worklyTheme";
const THEME_ORDER = ["auto", "light", "dark"];
const THEME_LABEL = { auto: "Mavzu: avtomatik", light: "Mavzu: yorug'", dark: "Mavzu: qorong'i" };

function effectiveTheme(choice) {
    if (choice === "dark" || choice === "light") return choice;
    if (telegram?.colorScheme === "dark" || telegram?.colorScheme === "light") return telegram.colorScheme;
    return window.matchMedia?.("(prefers-color-scheme: dark)").matches ? "dark" : "light";
}
function applyTheme(choice) {
    const root = document.documentElement;
    if (choice === "auto") root.removeAttribute("data-theme");
    else root.setAttribute("data-theme", choice);
    els.themeBtn.dataset.themeState = choice;
    els.themeBtn.title = THEME_LABEL[choice];
    const meta = document.querySelector('meta[name="theme-color"]');
    if (meta) meta.content = effectiveTheme(choice) === "dark" ? "#0c111e" : "#f5f6fb";
}
let themeChoice = localStorage.getItem(THEME_KEY) || "auto";
applyTheme(themeChoice);
els.themeBtn.addEventListener("click", () => {
    themeChoice = THEME_ORDER[(THEME_ORDER.indexOf(themeChoice) + 1) % THEME_ORDER.length];
    localStorage.setItem(THEME_KEY, themeChoice);
    applyTheme(themeChoice);
    haptic("light");
});
telegram?.onEvent?.("themeChanged", () => { if (themeChoice === "auto") applyTheme("auto"); });

/* ---------------- haptics + ripple ---------------- */
function haptic(kind = "light") {
    try { telegram?.HapticFeedback?.impactOccurred(kind); } catch (_) { /* no-op outside Telegram */ }
}
document.addEventListener("pointerdown", (e) => {
    if (reduceMotion) return;
    const target = e.target.closest(".btn, .tab, .filter-card, .icon-btn");
    if (!target) return;
    const rect = target.getBoundingClientRect();
    const size = Math.max(rect.width, rect.height);
    const ripple = document.createElement("span");
    ripple.className = "ripple";
    ripple.style.width = ripple.style.height = size + "px";
    ripple.style.left = (e.clientX - rect.left - size / 2) + "px";
    ripple.style.top = (e.clientY - rect.top - size / 2) + "px";
    target.appendChild(ripple);
    ripple.addEventListener("animationend", () => ripple.remove());
});

/* ---------------- events ---------------- */
els.devLogin.addEventListener("click", () => {
    state.devUserId = els.devUserId.value.trim();
    localStorage.setItem("worklyDevUserId", state.devUserId);
    loadDashboard();
});
els.refreshBtn.addEventListener("click", () => {
    haptic("medium");
    els.refreshBtn.classList.remove("spinning");
    void els.refreshBtn.offsetWidth;            // restart the spin animation
    els.refreshBtn.classList.add("spinning");
    loadDashboard(els.selfMonth.value);
});
els.tabSelf.addEventListener("click", () => { switchTab("self"); haptic(); });
els.tabTeam.addEventListener("click", () => { switchTab("team"); haptic(); });
els.selfMonth.addEventListener("change", () => loadDashboard(els.selfMonth.value));
els.empSelect.addEventListener("change", () => {
    state.selectedEmployeeId = els.empSelect.value;
    loadEmployeeHistory();
});
els.empMonth.addEventListener("change", loadEmployeeHistory);
els.refreshActivities.addEventListener("click", loadActivities);
els.refreshAudit.addEventListener("click", loadAuditLog);
els.refreshFeedbacks.addEventListener("click", loadFeedbacks);
els.reportSearch.addEventListener("input", () => { state.report.query = els.reportSearch.value.trim().toLowerCase(); renderReportRows(); });
els.reportDept.addEventListener("change", () => { state.report.dept = els.reportDept.value; renderReportRows(); });
els.reportClear.addEventListener("click", clearReportFilters);
els.filterCards.querySelectorAll(".filter-card").forEach((card) => {
    card.addEventListener("click", () => { toggleStatusFilter(card.dataset.filter); haptic(); });
});

loadDashboard();

/* ---------------- data ---------------- */
async function loadDashboard(month) {
    setLoading(true);
    clearToast();
    try {
        const data = await apiGet("/api/app/me", month ? { month } : {});
        state.currentMonth = data.salary?.month || (data.todayDate ? data.todayDate.slice(0, 7) : thisMonth);
        renderProfile(data.employee);
        renderToday(data.today, data.todayDate);
        renderPerformance(data.salary);
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
            els.auditCard.hidden = data.employee.role !== "ADMIN";
            els.feedbackCard.hidden = data.employee.role !== "ADMIN";
            if (data.employee.role === "ADMIN") {
                loadActivities();
                loadAuditLog();
                loadFeedbacks();
            }
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
    if (!state.selectedEmployeeId) {
        renderDaysTable(els.empHistoryBody, []);
        renderEmpPerformance(null);
        return;
    }
    setLoading(true);
    try {
        const salary = await apiGet(`/api/app/employees/${state.selectedEmployeeId}/salary`, { month: els.empMonth.value });
        renderEmpPerformance(salary);
        renderDaysTable(els.empHistoryBody, salary?.days || []);
    } catch (error) {
        showToast(error.message);
    } finally {
        setLoading(false);
    }
}

async function loadActivities() {
    try { renderActivities(await apiGet("/api/app/activities", {})); }
    catch (error) { showToast(error.message); }
}
async function loadAuditLog() {
    try { renderAuditLog(await apiGet("/api/app/audit-log", {})); }
    catch (error) { showToast(error.message); }
}
async function loadFeedbacks() {
    try { renderFeedbacks(await apiGet("/api/app/feedbacks", {})); }
    catch (error) { showToast(error.message); }
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

/* ---------------- performance ---------------- */
const TIERS = [
    { min: 95, grade: "A+", name: "Oltin daraja", cls: "is-gold", color: "var(--gold)" },
    { min: 85, grade: "A", name: "Kumush daraja", cls: "", color: "var(--silver)" },
    { min: 70, grade: "B", name: "Bronza daraja", cls: "", color: "var(--bronze)" },
    { min: 50, grade: "C", name: "O'rtacha", cls: "", color: "var(--accent)" },
    { min: 0, grade: "D", name: "Yaxshilash kerak", cls: "", color: "var(--neg)" },
];

function computePerf(salary) {
    const days = salary?.days || [];
    const worked = days.filter((d) => d.arrival);
    const workedDays = worked.length;
    if (!workedDays) return null;
    const lateDays = salary.lateDays ?? worked.filter((d) => d.lateMinutes > 0).length;
    const onTimeDays = Math.max(0, workedDays - lateDays);
    const score = Math.round((onTimeDays / workedDays) * 100);

    // longest run of consecutive on-time worked days (chronological)
    let streak = 0, longest = 0;
    for (const d of worked) {
        if (d.lateMinutes > 0) streak = 0;
        else { streak += 1; longest = Math.max(longest, streak); }
    }
    const tier = TIERS.find((t) => score >= t.min);
    return {
        score, tier, workedDays, onTimeDays, lateDays, longest,
        penalizedDays: salary.penalizedDays || 0,
        totalLateMinutes: salary.totalLateMinutes || 0,
        totalWorkedMinutes: salary.totalWorkedMinutes || 0,
    };
}

function ringSvg(score, color) {
    const r = 52, circ = 2 * Math.PI * r;
    const offset = circ * (1 - Math.max(0, Math.min(100, score)) / 100);
    return `
        <svg viewBox="0 0 116 116" aria-hidden="true">
            <circle class="ring-track" cx="58" cy="58" r="${r}"></circle>
            <circle class="ring-bar" cx="58" cy="58" r="${r}"
                    stroke="${color}" stroke-dasharray="${circ.toFixed(1)}"
                    stroke-dashoffset="${circ.toFixed(1)}" data-target="${offset.toFixed(1)}"></circle>
        </svg>
        <div class="ring-center"><div class="ring-num" data-count="${score}">0</div><div class="ring-unit">ball</div></div>`;
}

function animateRing(container) {
    const bar = container.querySelector(".ring-bar");
    const num = container.querySelector(".ring-num");
    if (bar) {
        const target = bar.dataset.target;
        if (reduceMotion) bar.style.strokeDashoffset = target;
        else requestAnimationFrame(() => requestAnimationFrame(() => { bar.style.strokeDashoffset = target; }));
    }
    if (num) animateCount(num, Number(num.dataset.count) || 0);
}

function badge(text, cls, icon) {
    return `<span class="badge ${cls || ""}">${icon || ""}${esc(text)}</span>`;
}
const ICO_CHECK = `<svg class="badge-ico" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"><path d="M20 6 9 17l-5-5"/></svg>`;
const ICO_STAR = `<svg class="badge-ico" viewBox="0 0 24 24" fill="currentColor" stroke="none"><path d="m12 2 2.9 6.3 6.9.7-5.1 4.6 1.4 6.8L12 17.8 5.9 20.4l1.4-6.8L2.2 9l6.9-.7z"/></svg>`;

function renderPerformance(salary) {
    els.performanceMonth.textContent = salary?.month || state.currentMonth || thisMonth;
    const p = computePerf(salary);
    if (!p) {
        els.performanceBody.innerHTML = `<p class="score-empty">Bu oy uchun davomat yozuvlari yig'ilgach, samaradorlik bahosi shu yerda chiqadi.</p>`;
        return;
    }
    const badges = [];
    if (p.lateDays === 0) badges.push(badge("Mukammal oy", "is-gold", ICO_STAR));
    else if (p.score >= 90) badges.push(badge("Aniq vaqtida", "is-pos", ICO_CHECK));
    if (p.penalizedDays === 0) badges.push(badge("Jarimasiz", "is-pos", ICO_CHECK));
    if (p.longest >= 5) badges.push(badge(`${p.longest} kun ketma-ket`, "is-amber"));
    if (p.workedDays >= 20) badges.push(badge(`Faol — ${p.workedDays} kun`, ""));

    els.performanceBody.innerHTML = `
        <div class="scorecard" style="--tier:${p.tier.color}">
            <div class="score-ring">${ringSvg(p.score, p.tier.color)}</div>
            <div class="score-side">
                <span class="medal"><span class="medal-disc">${p.tier.grade}</span>${esc(p.tier.name)}</span>
                <div class="score-meta">
                    <div class="sm"><span class="sm-val">${p.onTimeDays}</span><span class="sm-lbl">Vaqtida kun</span></div>
                    <div class="sm"><span class="sm-val">${p.lateDays}</span><span class="sm-lbl">Kechikkan kun</span></div>
                    <div class="sm"><span class="sm-val">${formatMinutes(p.totalWorkedMinutes)}</span><span class="sm-lbl">Ishlangan</span></div>
                </div>
            </div>
        </div>
        ${badges.length ? `<div class="badges">${badges.join("")}</div>` : ""}`;
    animateRing(els.performanceBody);
}

function renderEmpPerformance(salary) {
    const p = computePerf(salary);
    if (!p) { els.empPerformance.innerHTML = ""; return; }
    const days = salary.days || [];
    const cap = 60; // minutes mapped to a full-height bar
    const spark = days.map((d) => {
        if (!d.arrival) return `<div class="spark-bar miss" style="height:14%"></div>`;
        if (d.lateMinutes > 0) {
            const h = Math.min(100, 24 + (d.lateMinutes / cap) * 76);
            return `<div class="spark-bar late" style="height:${h.toFixed(0)}%" title="${esc(d.date)}: ${d.lateMinutes} daq"></div>`;
        }
        return `<div class="spark-bar on" style="height:24%" title="${esc(d.date)}: vaqtida"></div>`;
    }).join("");

    els.empPerformance.innerHTML = `
        <div class="scorecard" style="--tier:${p.tier.color}">
            <div class="score-ring">${ringSvg(p.score, p.tier.color)}</div>
            <div class="score-side">
                <span class="medal"><span class="medal-disc">${p.tier.grade}</span>${esc(p.tier.name)}</span>
                <div class="score-meta">
                    <div class="sm"><span class="sm-val">${p.onTimeDays}</span><span class="sm-lbl">Vaqtida kun</span></div>
                    <div class="sm"><span class="sm-val">${p.lateDays}</span><span class="sm-lbl">Kechikkan kun</span></div>
                    <div class="sm"><span class="sm-val">${formatMinutes(p.totalLateMinutes)}</span><span class="sm-lbl">Jami kechikish</span></div>
                </div>
            </div>
        </div>
        <div class="progress">
            <div class="progress-head"><span>Aniq vaqtida kelish</span><b>${p.score}%</b></div>
            <div class="progress-track"><div class="progress-bar" data-w="${p.score}"></div></div>
        </div>
        ${days.length ? `<div class="sparkline">${spark}</div>
        <div class="sparkline-cap"><span>Oy boshi</span><span>Kunlik kechikish</span><span>Oy oxiri</span></div>` : ""}`;

    animateRing(els.empPerformance);
    const bar = els.empPerformance.querySelector(".progress-bar");
    if (bar) {
        if (reduceMotion) bar.style.width = bar.dataset.w + "%";
        else requestAnimationFrame(() => requestAnimationFrame(() => { bar.style.width = bar.dataset.w + "%"; }));
    }
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

/* ---------------- render: month history (shared) ---------------- */
function renderDaysTable(body, days) {
    if (!days || !days.length) return emptyRow(body, 7);
    body.innerHTML = days.map((d) => {
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
}

function renderSelfHistory(salary, monthHistory) {
    if (salary?.days?.length) { renderDaysTable(els.selfHistoryBody, salary.days); return; }
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
        metric("Erta ketish", s.pendingEarlyLeaves) +
        metric("Profil so'rovlari", s.pendingProfileChanges);
    els.metrics.querySelectorAll(".metric strong[data-count]").forEach((n) => animateCount(n, Number(n.dataset.count) || 0));
}

function renderPulse(report) {
    const active = Number(report?.activeEmployees) || 0;
    const arrived = Number(report?.arrived) || 0;
    const late = Number(report?.late) || 0;
    const missing = Number(report?.missingCheckout) || 0;
    const absent = Number(report?.absent) || 0;
    const onTime = Math.max(0, arrived - late);

    if (!active) { els.pulseCard.hidden = true; return; }
    els.pulseCard.hidden = false;

    const segs = [
        { v: onTime, color: "var(--pos)" },
        { v: late, color: "var(--accent)" },
        { v: missing, color: "var(--warn)" },
        { v: absent, color: "var(--neg)" },
    ];
    const denom = segs.reduce((a, b) => a + b.v, 0) || active;
    const r = 46, circ = 2 * Math.PI * r;
    let acc = 0;
    const arcs = segs.map((s) => {
        const frac = s.v / denom;
        const dash = `${(frac * circ).toFixed(2)} ${circ.toFixed(2)}`;
        const offset = (-acc * circ).toFixed(2);
        acc += frac;
        return `<circle class="pulse-seg" cx="58" cy="58" r="${r}" stroke="${s.color}"
                        stroke-dasharray="${dash}" stroke-dashoffset="${offset}"></circle>`;
    }).join("");
    const pct = Math.round((arrived / active) * 100);

    els.pulseRing.innerHTML = `
        <svg viewBox="0 0 116 116" aria-hidden="true">
            <circle class="pulse-seg" cx="58" cy="58" r="${r}" stroke="var(--surface-2)" stroke-dasharray="${circ.toFixed(2)} 0"></circle>
            ${arcs}
        </svg>
        <div class="pulse-center"><div class="pulse-num" data-count="${pct}">0</div><div class="pulse-lbl">hozir</div></div>`;
    els.pulseLegend.innerHTML =
        pulseRow("on", "Vaqtida", onTime) +
        pulseRow("late", "Kechikkan", late) +
        pulseRow("miss", "Ketmagan", missing) +
        pulseRow("absent", "Kelmagan", absent);

    const num = els.pulseRing.querySelector(".pulse-num");
    if (num) animateCount(num, pct, "%");
}
function pulseRow(dot, label, value) {
    return `<div class="pl"><span class="pl-dot ${dot}"></span><span>${esc(label)}</span><span class="pl-val">${value}</span></div>`;
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
        renderEmpPerformance(null);
        emptyRow(els.empHistoryBody, 7);
    }
}

function setReport(report) {
    state.report.rows = report?.rows || [];
    els.reportDate.textContent = report?.date || "";
    animateCount(els.fcArrived, report?.arrived ?? 0);
    animateCount(els.fcAbsent, report?.absent ?? 0);
    animateCount(els.fcLate, report?.late ?? 0);
    animateCount(els.fcMissing, report?.missingCheckout ?? 0);

    renderPulse(report);

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

function renderActivities(rows) {
    if (!rows || !rows.length) return emptyRow(els.activitiesBody, 4, "Faoliyat hozircha yo'q.");
    els.activitiesBody.innerHTML = rows.map((r) => `<tr>
        <td>${esc(fmtDateTime(r.createdAt))}</td><td>${esc(r.actorName)}</td>
        <td>${esc(r.actorTelegramUserId)}</td><td>${esc(r.details)}</td></tr>`).join("");
}

function renderAuditLog(rows) {
    if (!rows || !rows.length) return emptyRow(els.auditBody, 4, "Audit log hozircha yo'q.");
    els.auditBody.innerHTML = rows.map((r) => `<tr>
        <td>${esc(fmtDateTime(r.createdAt))}</td><td>${esc(r.actorName)}</td>
        <td>${esc(r.actorTelegramUserId)}</td><td>${esc(r.details)}</td></tr>`).join("");
}

function renderFeedbacks(rows) {
    if (!rows || !rows.length) return emptyRow(els.feedbackBody, 4, "Fikrlar hozircha yo'q.");
    els.feedbackBody.innerHTML = rows.map((r) => `<tr>
        <td>${esc(fmtDateTime(r.createdAt))}</td><td>${esc(r.fullName)}</td>
        <td>${esc(r.department || "—")}</td><td>${esc(r.message)}</td></tr>`).join("");
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
function metric(label, value) { return `<div class="metric"><span>${esc(label)}</span><strong data-count="${Number(value) || 0}">0</strong></div>`; }
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

/* ---------------- animations ---------------- */
function animateCount(el, to, suffix = "") {
    to = Number(to) || 0;
    if (reduceMotion) { el.textContent = to + suffix; return; }
    const from = Number(String(el.textContent).replace(/[^\d-]/g, "")) || 0;
    if (from === to) { el.textContent = to + suffix; return; }
    const dur = 600, t0 = performance.now();
    function step(now) {
        const k = Math.min(1, (now - t0) / dur);
        const eased = 1 - Math.pow(1 - k, 3);
        el.textContent = Math.round(from + (to - from) * eased) + suffix;
        if (k < 1) requestAnimationFrame(step);
    }
    requestAnimationFrame(step);
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
    els.refreshBtn.disabled = on;
}
let toastTimer;
function showToast(message) {
    els.toast.hidden = false;
    els.toast.textContent = message;
    clearTimeout(toastTimer);
    toastTimer = setTimeout(clearToast, 4000);
}
function clearToast() { els.toast.hidden = true; els.toast.textContent = ""; }