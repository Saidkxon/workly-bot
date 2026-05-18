const telegram = window.Telegram?.WebApp;
const state = {
    initData: telegram?.initData || "",
    devUserId: new URLSearchParams(window.location.search).get("userId") || localStorage.getItem("worklyDevUserId") || "",
    employees: [],
    selectedEmployeeId: null,
};

const els = {
    profile: document.getElementById("profile"),
    devAuth: document.getElementById("devAuth"),
    devUserId: document.getElementById("devUserId"),
    devLogin: document.getElementById("devLogin"),
    managerMetrics: document.getElementById("managerMetrics"),
    activeEmployees: document.getElementById("activeEmployees"),
    pendingRegistrations: document.getElementById("pendingRegistrations"),
    pendingCorrections: document.getElementById("pendingCorrections"),
    pendingEarlyLeaves: document.getElementById("pendingEarlyLeaves"),
    todayDate: document.getElementById("todayDate"),
    todayGrid: document.getElementById("todayGrid"),
    todayReportPanel: document.getElementById("todayReportPanel"),
    todayReportDate: document.getElementById("todayReportDate"),
    todayReportSummary: document.getElementById("todayReportSummary"),
    todayReportBody: document.getElementById("todayReportBody"),
    monthPicker: document.getElementById("monthPicker"),
    historyBody: document.getElementById("historyBody"),
    managerPanel: document.getElementById("managerPanel"),
    employeeSelect: document.getElementById("employeeSelect"),
    employeeHistoryBody: document.getElementById("employeeHistoryBody"),
    activitiesPanel: document.getElementById("activitiesPanel"),
    refreshActivities: document.getElementById("refreshActivities"),
    activitiesBody: document.getElementById("activitiesBody"),
    errorBox: document.getElementById("errorBox"),
};

telegram?.ready();
telegram?.expand();

const currentMonth = new Date().toISOString().slice(0, 7);
els.monthPicker.value = currentMonth;
els.devUserId.value = state.devUserId;
els.devAuth.hidden = Boolean(state.initData);

els.devLogin.addEventListener("click", () => {
    state.devUserId = els.devUserId.value.trim();
    localStorage.setItem("worklyDevUserId", state.devUserId);
    loadDashboard();
});

els.monthPicker.addEventListener("change", () => {
    if (state.selectedEmployeeId) {
        loadEmployeeHistory(state.selectedEmployeeId);
    }
});

els.employeeSelect.addEventListener("change", () => {
    state.selectedEmployeeId = Number(els.employeeSelect.value);
    loadEmployeeHistory(state.selectedEmployeeId);
});

els.refreshActivities.addEventListener("click", () => {
    loadActivities();
});

loadDashboard();

async function loadDashboard() {
    clearError();
    try {
        const data = await apiGet("/api/app/me");
        renderProfile(data.employee);
        renderToday(data.today, data.todayDate);
        renderHistory(els.historyBody, data.monthHistory);

        if (data.managerSummary) {
            renderManager(data.employee, data.managerSummary, data.employees);
            renderTodayReport(data.todayReport);
        }
    } catch (error) {
        showError(error.message);
        if (!state.initData) {
            els.devAuth.hidden = false;
        }
    }
}

async function loadEmployeeHistory(telegramUserId) {
    clearError();
    if (!telegramUserId) {
        renderHistory(els.employeeHistoryBody, []);
        return;
    }

    try {
        const rows = await apiGet(`/api/app/employees/${telegramUserId}/history?month=${encodeURIComponent(els.monthPicker.value)}`);
        renderHistory(els.employeeHistoryBody, rows);
    } catch (error) {
        showError(error.message);
    }
}

async function loadActivities() {
    clearError();
    try {
        const rows = await apiGet("/api/app/activities");
        renderActivities(rows);
    } catch (error) {
        showError(error.message);
    }
}

async function apiGet(path) {
    const url = new URL(path, window.location.origin);
    if (!state.initData && state.devUserId && !url.searchParams.has("userId")) {
        url.searchParams.set("userId", state.devUserId);
    }

    const response = await fetch(url, {
        headers: state.initData ? {"X-Telegram-Init-Data": state.initData} : {},
    });

    if (!response.ok) {
        const text = await response.text();
        throw new Error(text || `Request failed: ${response.status}`);
    }

    return response.json();
}

function renderProfile(employee) {
    els.profile.innerHTML = `
        <strong>${escapeHtml(employee.fullName)}</strong><br>
        ${escapeHtml(employee.department)} · ${escapeHtml(employee.role)}
    `;
}

function renderToday(today, todayDate) {
    els.todayDate.textContent = todayDate || new Date().toISOString().slice(0, 10);
    if (!today) {
        els.todayGrid.innerHTML = `<p class="empty">Bugun uchun davomat yozuvi yo'q.</p>`;
        return;
    }

    els.todayGrid.innerHTML = `
        ${metric("Kelgan vaqt", today.arrivalTime || "Belgilanmagan")}
        ${metric("Ketgan vaqt", today.leaveTime || "Belgilanmagan")}
        ${metric("Ishlangan vaqt", today.workedTime)}
        ${metric("Holat", today.status)}
    `;
}

function renderTodayReport(report) {
    if (!report) {
        els.todayReportPanel.hidden = true;
        return;
    }

    els.todayReportPanel.hidden = false;
    els.todayReportDate.textContent = report.date || "";
    els.todayReportSummary.innerHTML = `
        ${metric("Faol xodimlar", report.activeEmployees)}
        ${metric("Kelganlar", report.arrived)}
        ${metric("Kelmaganlar", report.absent)}
        ${metric("Ketishni belgilamaganlar", report.missingCheckout)}
        ${metric("Kechikkanlar", report.late)}
    `;

    if (!report.rows || report.rows.length === 0) {
        els.todayReportBody.innerHTML = `<tr><td colspan="6" class="empty">Bugungi report uchun ma'lumot yo'q.</td></tr>`;
        return;
    }

    els.todayReportBody.innerHTML = report.rows.map(row => `
        <tr>
            <td>${escapeHtml(row.fullName)}</td>
            <td>${escapeHtml(row.department)}</td>
            <td>${escapeHtml(row.arrivalTime || "Belgilanmagan")}</td>
            <td>${escapeHtml(row.leaveTime || "Belgilanmagan")}</td>
            <td>${escapeHtml(row.lateTime)}</td>
            <td><span class="${statusClass(row.status)}">${escapeHtml(row.status)}</span></td>
        </tr>
    `).join("");
}

function renderHistory(target, rows) {
    if (!rows || rows.length === 0) {
        target.innerHTML = `<tr><td colspan="6" class="empty">Bu oy uchun yozuvlar yo'q.</td></tr>`;
        return;
    }

    target.innerHTML = rows.map(row => `
        <tr>
            <td>${escapeHtml(row.date)}</td>
            <td>${escapeHtml(row.arrivalTime || "Belgilanmagan")}</td>
            <td>${escapeHtml(row.leaveTime || "Belgilanmagan")}</td>
            <td>${escapeHtml(row.workedTime)}</td>
            <td>${escapeHtml(row.lateTime)}</td>
            <td><span class="${statusClass(row.status)}">${escapeHtml(row.status)}</span></td>
        </tr>
    `).join("");
}

function renderManager(employee, summary, employees) {
    els.managerMetrics.hidden = false;
    els.managerPanel.hidden = false;
    els.activeEmployees.textContent = summary.activeEmployees;
    els.pendingRegistrations.textContent = summary.pendingRegistrations;
    els.pendingCorrections.textContent = summary.pendingCorrections;
    els.pendingEarlyLeaves.textContent = summary.pendingEarlyLeaves;
    state.employees = employees || [];

    els.employeeSelect.innerHTML = state.employees.map(employee => `
        <option value="${employee.telegramUserId}">
            ${escapeHtml(employee.fullName)} · ${escapeHtml(employee.department)}
        </option>
    `).join("");

    state.selectedEmployeeId = state.employees[0]?.telegramUserId || null;
    if (state.selectedEmployeeId) {
        els.employeeSelect.value = String(state.selectedEmployeeId);
        loadEmployeeHistory(state.selectedEmployeeId);
    }

    if (employee.role === "ADMIN") {
        els.activitiesPanel.hidden = false;
        loadActivities();
    }
}

function renderActivities(rows) {
    if (!rows || rows.length === 0) {
        els.activitiesBody.innerHTML = `<tr><td colspan="4" class="empty">Activities hozircha yo'q.</td></tr>`;
        return;
    }

    els.activitiesBody.innerHTML = rows.map(row => `
        <tr>
            <td>${escapeHtml(formatDateTime(row.createdAt))}</td>
            <td>${escapeHtml(row.actorName)}</td>
            <td>${escapeHtml(row.actorTelegramUserId)}</td>
            <td>${escapeHtml(row.details)}</td>
        </tr>
    `).join("");
}

function metric(label, value) {
    return `<div><span>${escapeHtml(label)}</span><strong>${escapeHtml(value)}</strong></div>`;
}

function statusClass(status) {
    const value = (status || "").toLowerCase();
    if (value.includes("kechik") || value.includes("ketgan vaqt")) {
        return "status warn";
    }
    if (value.includes("kelmagan") || value.includes("yo'q")) {
        return "status danger";
    }
    return "status";
}

function showError(message) {
    els.errorBox.hidden = false;
    els.errorBox.textContent = message;
}

function clearError() {
    els.errorBox.hidden = true;
    els.errorBox.textContent = "";
}

function formatDateTime(value) {
    if (!value) {
        return "";
    }

    return String(value).replace("T", " ").slice(0, 16);
}

function escapeHtml(value) {
    return String(value ?? "")
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll('"', "&quot;")
        .replaceAll("'", "&#039;");
}
