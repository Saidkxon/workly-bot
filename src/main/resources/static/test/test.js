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

    const questionsHtml = state.questions.map((q, idx) => {
        if (q.type === "MULTIPLE_CHOICE") {
            const options = [
                ["A", q.optionA], ["B", q.optionB], ["C", q.optionC], ["D", q.optionD]
            ].filter(([, text]) => text);
            const optionsHtml = options.map(([letter, text]) => `
                <button type="button" class="mc-btn" data-value="${letter}">
                    <span class="mc-letter">${letter}</span>${escapeHtml(text)}
                </button>`).join("");
            return `
                <div class="question" data-qid="${q.id}" data-type="${q.type}">
                    <p class="q-text">${idx + 1}. ${escapeHtml(q.questionText)}</p>
                    <div class="mc-options">${optionsHtml}</div>
                </div>`;
        }
        return `
            <div class="question" data-qid="${q.id}" data-type="${q.type}">
                <p class="q-text">${idx + 1}. ${escapeHtml(q.questionText)}</p>
                <textarea class="answer-input" placeholder="Javobingizni yozing..."></textarea>
            </div>`;
    }).join("");

    els.content.innerHTML = `${questionsHtml}
        <div class="submit-row"><button id="submitBtn" class="btn" type="button">Yakunlash</button></div>`;

    els.content.querySelectorAll(".mc-btn").forEach((btn) => {
        btn.addEventListener("click", () => {
            btn.parentElement.querySelectorAll(".mc-btn").forEach((b) => b.classList.remove("selected"));
            btn.classList.add("selected");
        });
    });

    document.getElementById("submitBtn").addEventListener("click", () => handleSubmit(false));

    startCheatingGuard();
    startTimer(state.secondsLeft);
}

function collectAnswers() {
    const answers = {};
    els.content.querySelectorAll(".question").forEach((q) => {
        const qid = q.dataset.qid;
        if (q.dataset.type === "MULTIPLE_CHOICE") {
            const selected = q.querySelector(".mc-btn.selected");
            answers[qid] = selected ? selected.dataset.value : "";
        } else {
            const textarea = q.querySelector("textarea");
            answers[qid] = textarea ? textarea.value.trim() : "";
        }
    });
    return answers;
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
        const answers = collectAnswers();
        const state = await api("POST", `/${token}/submit`, { answers });
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