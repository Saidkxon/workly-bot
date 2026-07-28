const token = new URLSearchParams(window.location.search).get("token");

const els = {
    title: document.getElementById("testTitle"),
    timer: document.getElementById("timer"),
    content: document.getElementById("content"),
    warnOverlay: document.getElementById("warnOverlay"),
    warnText: document.getElementById("warnText"),
    warnOk: document.getElementById("warnOk"),
};

const WARN_MESSAGE = "Test paytida boshqa oynalarga o'tish taqiqlanadi, agar yana boshqa oynaga o'tsangiz test bloklanadi va siz testtan o'ta olmagan hisoblanasiz!";
const THANK_YOU_MESSAGE = "Javoblaringiz uchun rahmat! Natijangiz tez orada e'lon qilinadi.";

let timerInterval = null;
let secondsLeft = null;
let cheatingGuardActive = false;
let lastViolationAt = 0;
let submitting = false;

let allQuestions = [];
let currentIndex = 0;
const answersState = {}; // questionId -> answer text, persists across navigation

els.warnOk.addEventListener("click", () => { els.warnOverlay.hidden = true; });

async function api(method, path, body) {
    const res = await fetch(`/api/test${path}`, {
        method,
        headers: body ? { "Content-Type": "application/json" } : undefined,
        body: body ? JSON.stringify(body) : undefined,
    });
    if (!res.ok) {
        throw new Error(res.status === 404 ? "Test topilmadi." : "Xatolik yuz berdi.");
    }
    return res.json();
}

function renderError(message) {
    els.content.innerHTML = `<div class="message">${escapeHtml(message)}</div>`;
}

function renderIntro(state) {
    els.title.textContent = state.testTitle || "Xodimlar testi";
    els.content.innerHTML = `
        <div class="intro">
            <p>Test ${state.timerMinutes} daqiqa davom etadi. Boshlangandan so'ng, testni tark etmang yoki boshqa oyna/dasturga o'tmang — bu holat kuzatiladi va qoidabuzarlik hisoblanadi.</p>
            <button id="startBtn" class="btn" type="button">Testni boshlash</button>
        </div>`;
    document.getElementById("startBtn").addEventListener("click", handleStart);
}

function renderMessage(text, cssClass) {
    stopTimer();
    els.timer.hidden = true;
    els.content.innerHTML = `<div class="message ${cssClass || ""}">${escapeHtml(text)}</div>`;
}

function renderQuestions(state) {
    els.title.textContent = state.testTitle || "Xodimlar testi";
    els.timer.hidden = false;
    allQuestions = state.questions;
    currentIndex = 0;
    startCheatingGuard();
    startTimer(state.secondsLeft);
    renderCurrentQuestion();
}

function isAnswered(question) {
    const value = answersState[question.id];
    return Boolean(value && value.trim());
}

function renderNavDots() {
    return allQuestions.map((q, i) => {
        const classes = ["nav-dot"];
        if (i === currentIndex) classes.push("current");
        else if (isAnswered(q)) classes.push("answered");
        return `<button type="button" class="${classes.join(" ")}" data-idx="${i}">${i + 1}</button>`;
    }).join("");
}

function renderCurrentQuestion() {
    const q = allQuestions[currentIndex];
    const total = allQuestions.length;
    const savedAnswer = answersState[q.id] || "";

    let questionBodyHtml;
    if (q.type === "MULTIPLE_CHOICE") {
        const options = [
            ["A", q.optionA], ["B", q.optionB], ["C", q.optionC], ["D", q.optionD]
        ].filter(([, text]) => text);
        const optionsHtml = options.map(([letter, text]) => `
            <button type="button" class="mc-btn${letter === savedAnswer ? " selected" : ""}" data-value="${letter}">
                <span class="mc-letter">${letter}</span>${escapeHtml(text)}
            </button>`).join("");
        questionBodyHtml = `<div class="mc-options">${optionsHtml}</div>`;
    } else {
        questionBodyHtml = `<textarea class="answer-input" placeholder="Javobingizni yozing...">${escapeHtml(savedAnswer)}</textarea>`;
    }

    els.content.innerHTML = `
        <div class="progress-row">
            <span class="progress-label">Savol ${currentIndex + 1} / ${total}</span>
        </div>
        <div class="nav-dots">${renderNavDots()}</div>
        <div class="question" data-qid="${q.id}" data-type="${q.type}">
            <p class="q-text">${escapeHtml(q.questionText)}</p>
            ${questionBodyHtml}
        </div>
        <div class="nav-buttons">
            <button id="prevBtn" class="btn btn-secondary" type="button" ${currentIndex === 0 ? "disabled" : ""}>Oldingi</button>
            ${currentIndex < total - 1
        ? `<button id="nextBtn" class="btn" type="button">Keyingi (o'tkazib yuborish)</button>`
        : ""}
            <button id="submitBtn" class="btn btn-primary" type="button">Yakunlash</button>
        </div>`;

    els.content.querySelectorAll(".mc-btn").forEach((btn) => {
        btn.addEventListener("click", () => {
            els.content.querySelectorAll(".mc-btn").forEach((b) => b.classList.remove("selected"));
            btn.classList.add("selected");
            answersState[q.id] = btn.dataset.value;
            refreshCurrentNavDot();
        });
    });

    const textarea = els.content.querySelector("textarea.answer-input");
    if (textarea) {
        textarea.addEventListener("input", () => {
            answersState[q.id] = textarea.value;
            refreshCurrentNavDot();
        });
    }

    els.content.querySelectorAll(".nav-dot").forEach((dot) => {
        dot.addEventListener("click", () => goToQuestion(Number(dot.dataset.idx)));
    });

    const prevBtn = document.getElementById("prevBtn");
    if (prevBtn) prevBtn.addEventListener("click", () => goToQuestion(currentIndex - 1));

    const nextBtn = document.getElementById("nextBtn");
    if (nextBtn) nextBtn.addEventListener("click", () => goToQuestion(currentIndex + 1));

    document.getElementById("submitBtn").addEventListener("click", () => handleSubmit(false));
}

function refreshCurrentNavDot() {
    const dot = els.content.querySelector(`.nav-dot[data-idx="${currentIndex}"]`);
    if (dot && isAnswered(allQuestions[currentIndex])) {
        dot.classList.add("answered");
    }
}

function goToQuestion(index) {
    if (index < 0 || index >= allQuestions.length || index === currentIndex) return;
    currentIndex = index;
    renderCurrentQuestion();
}

function startTimer(initialSeconds) {
    secondsLeft = initialSeconds;
    updateTimerDisplay();
    stopTimer();
    timerInterval = setInterval(() => {
        secondsLeft -= 1;
        updateTimerDisplay();
        if (secondsLeft <= 0) {
            handleSubmit(true);
        }
    }, 1000);
}

function stopTimer() {
    if (timerInterval) clearInterval(timerInterval);
    timerInterval = null;
}

function updateTimerDisplay() {
    const m = Math.max(0, Math.floor(secondsLeft / 60));
    const s = Math.max(0, secondsLeft % 60);
    els.timer.textContent = `${String(m).padStart(2, "0")}:${String(s).padStart(2, "0")}`;
    els.timer.classList.toggle("low", secondsLeft <= 60);
}

function startCheatingGuard() {
    if (cheatingGuardActive) return;
    cheatingGuardActive = true;
    document.addEventListener("visibilitychange", onPotentialViolation);
    window.addEventListener("blur", onPotentialViolation);
}

function stopCheatingGuard() {
    cheatingGuardActive = false;
    document.removeEventListener("visibilitychange", onPotentialViolation);
    window.removeEventListener("blur", onPotentialViolation);
}

function onPotentialViolation() {
    if (!cheatingGuardActive) return;
    if (document.visibilityState === "visible" && document.hasFocus && document.hasFocus()) return;
    const now = Date.now();
    if (now - lastViolationAt < 1200) return;
    lastViolationAt = now;
    reportViolation();
}

async function reportViolation() {
    try {
        const result = await api("POST", `/${token}/violation`, {});
        if (result.action === "WARN") {
            els.warnText.textContent = WARN_MESSAGE;
            els.warnOverlay.hidden = false;
        } else if (result.action === "BLOCKED") {
            stopCheatingGuard();
            renderMessage("Siz test qoidalarini buzganingiz uchun bloklandingiz.", "blocked");
        }
    } catch (_) {
        // network hiccup on a background check — don't disrupt the test over this
    }
}

async function handleStart() {
    try {
        const state = await api("POST", `/${token}/start`, {});
        applyState(state);
    } catch (e) {
        renderError(e.message);
    }
}

async function handleSubmit(auto) {
    if (submitting) return;
    submitting = true;
    stopCheatingGuard();
    stopTimer();
    try {
        const state = await api("POST", `/${token}/submit`, { answers: answersState });
        applyState(state);
    } catch (e) {
        submitting = false;
        renderError(e.message);
    }
}

function applyState(state) {
    switch (state.status) {
        case "NOT_STARTED":
            renderIntro(state);
            break;
        case "IN_PROGRESS":
            renderQuestions(state);
            break;
        case "SUBMITTED":
            renderMessage(THANK_YOU_MESSAGE, "success");
            break;
        case "BLOCKED":
            renderMessage("Siz test qoidalarini buzganingiz uchun bloklandingiz.", "blocked");
            break;
        case "EXPIRED":
            renderMessage("Test vaqti tugadi.", "");
            break;
        case "UNAVAILABLE":
            renderMessage("Test hozircha mavjud emas.", "");
            break;
        default:
            renderError("Noma'lum holat.");
    }
}

function escapeHtml(str) {
    const div = document.createElement("div");
    div.textContent = str == null ? "" : String(str);
    return div.innerHTML;
}

(async function init() {
    if (!token) {
        renderError("Test havolasi noto'g'ri.");
        return;
    }
    try {
        const state = await api("GET", `/${token}`, null);
        applyState(state);
    } catch (e) {
        renderError(e.message);
    }
})();