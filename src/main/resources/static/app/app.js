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
    monthPicker: document.getElementById("monthPicker"),
    historyBody: document.getElementById("historyBody"),
    managerPanel: document.getElementById("managerPanel"),
    employeeSelect: document.getElementById("employeeSelect"),
    employeeHistoryBody: document.getElementById("employeeHistoryBody"),
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

loadDashboard();

async function loadDashboard() {
    clearError();
    try {
        const data = await apiGet("/api/app/me");
        renderProfile(data.employee);
        renderToday(data.today);
        renderHistory(els.historyBody, data.monthHistory);

        if (data.managerSummary) {
            renderManager(data.managerSummary, data.employees);
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

function renderToday(today) {
    els.todayDate.textContent = new Date().toISOString().slice(0, 10);
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

function renderManager(summary, employees) {
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

function escapeHtml(value) {
    return String(value ?? "")
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll('"', "&quot;")
        .replaceAll("'", "&#039;");
}
