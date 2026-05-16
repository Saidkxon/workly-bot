package com.advancedprogramming.worklybot.bot;

public class BotMessages {

    private BotMessages() {
    }

    public static final String WELCOME_ENTER_FULL_NAME =
            "Workly botiga xush kelibsiz!\nIsm familiyangizni kiriting:";

    public static final String INVALID_FULL_NAME =
            "Ism familiya noto'g'ri. Qaytadan to'liq kiriting:";

    public static final String ENTER_DEPARTMENT =
            "Bo'limingizni kiriting:";

    public static final String INVALID_DEPARTMENT =
            "Bo'lim nomi noto'g'ri. Qaytadan kiriting:";

    public static final String ALREADY_REGISTERED =
            "Siz allaqachon ro'yxatdan o'tgansiz.";

    public static final String NOT_REGISTERED =
            "Siz hali ro'yxatdan o'tmagansiz. Boshlash uchun /start ni yuboring.";

    public static final String REGISTRATION_SUCCESS =
            "Ro'yxatdan o'tish muvaffaqiyatli yakunlandi!";

    public static final String REGISTRATION_PENDING_APPROVAL =
            "Ro'yxatdan o'tish qabul qilindi. Akkauntingiz faollashtirilishini kuting.";

    public static final String REGISTRATION_ALREADY_PENDING =
            "Sizning ro'yxatdan o'tish so'rovingiz allaqachon yuborilgan. Faollashtirilishini kuting.";

    public static final String REGISTRATION_ADMIN_BOOTSTRAPPED =
            "Siz admin sifatida ro'yxatdan o'tdingiz.";

    public static final String NAME_LABEL =
            "Ism familiya: ";

    public static final String DEPARTMENT_LABEL =
            "Bo'lim: ";

    public static final String CHOOSE_ACTION =
            "Kerakli amalni tanlang:";

    public static final String ACCOUNT_INACTIVE =
            "Sizning akkauntingiz faol emas. Menejer bilan bog'laning.";

    public static final String SHARE_LOCATION_FOR_ARRIVAL =
            "Ishga kelganingizni tasdiqlash uchun LIVE joylashuvingizni yuboring.\n\nTelegram oynasidan \"Share my live location\" ni tanlang.";

    public static final String SHARE_LOCATION_FOR_LEAVING =
            "Ishdan ketganingizni tasdiqlash uchun LIVE joylashuvingizni yuboring.\n\nTelegram oynasidan \"Share my live location\" ni tanlang.";

    public static final String LOCATION_BUTTON =
            "📍 Live joylashuv";

    public static final String OUTSIDE_OFFICE =
            "Siz ofis hududida emassiz.\nMasofa: ";

    public static final String METERS =
            " metr.";

    public static final String NO_PENDING_LOCATION_ACTION =
            "Joylashuv qabul qilindi, lekin kutilayotgan amal topilmadi.";

    public static final String ENTER_CORRECTION_DATE =
            "Sanani yyyy-MM-dd formatida kiriting\nMasalan: 2026-04-15";

    public static final String INVALID_DATE =
            "Sana noto'g'ri. yyyy-MM-dd formatida kiriting.";

    public static final String CORRECTION_TYPE =
            "Nimani o'zgartirmoqchisiz?\n1. Kelgan vaqt\n2. Ketgan vaqt\n3. Ikkalasi ham\n\n1 yoki 2 yoki 3 yuboring.";

    public static final String INVALID_CORRECTION_TYPE =
            "Noto'g'ri tanlov. 1 - kelgan vaqt, 2 - ketgan vaqt, 3 - ikkalasi.";

    public static final String ENTER_ARRIVAL_TIME =
            "To'g'rilangan kelgan vaqtni HH:mm formatida kiriting\nMasalan: 08:15";

    public static final String ENTER_LEAVING_TIME =
            "To'g'rilangan ketgan vaqtni HH:mm formatida kiriting\nMasalan: 18:40";

    public static final String ENTER_BOTH_TIMES =
            "Ikkala vaqtni HH:mm,HH:mm formatida kiriting\nMasalan: 08:15,18:40";

    public static final String ENTER_EARLY_LEAVE_REASON =
            "Erta ketish sababini yozing:";

    public static final String INVALID_EARLY_LEAVE_REASON =
            "Sabab juda qisqa. Kamida 5 ta belgi bilan yozing.";

    public static final String INVALID_APPROVE_COMMAND =
            "Tasdiqlash buyrug'i noto'g'ri.";

    public static final String INVALID_REJECT_COMMAND =
            "Rad qilish buyrug'i noto'g'ri.";

    public static final String INVALID_ACTIVATE_COMMAND =
            "Activate buyrug'i noto'g'ri.";

    public static final String INVALID_DEACTIVATE_COMMAND =
            "Deactivate buyrug'i noto'g'ri.";

    public static final String INVALID_PENDING_DEACTIVATE_COMMAND =
            "deactivate_pending buyrug'i noto'g'ri.";

    public static final String INVALID_MAKE_MANAGER_COMMAND =
            "make_manager buyrug'i noto'g'ri.";

    public static final String INVALID_MAKE_EMPLOYEE_COMMAND =
            "make_employee buyrug'i noto'g'ri.";

    public static final String INVALID_MAKE_ADMIN_COMMAND =
            "make_admin buyrug'i noto'g'ri.";

    public static final String EXCEL_EMPTY =
            "Bu oy uchun davomat ma'lumotlari yo'q.";

    public static final String EXCEL_SEND_ERROR =
            "Excel hisobotni yuborib bo'lmadi.";

    public static final String EXCEL_CAPTION =
            "Oylik davomat Excel hisoboti";

    public static final String BUTTON_ARRIVED =
            "✅ Keldim";

    public static final String BUTTON_LEFT_WORK =
            "🚪 Ketdim";

    public static final String BUTTON_STATUS =
            "📋 Status";

    public static final String BUTTON_HISTORY =
            "📅 Tarix";

    public static final String BUTTON_OPEN_APP =
            "🌐 Ilova";

    public static final String BUTTON_FIX_MISTAKE =
            "✏️ Vaqtni o'zgartirish";

    public static final String BUTTON_EARLY_LEAVE =
            "🏃 Erta ketish";

    public static final String CMD_TODAY_REPORT = "/bugungi_report";
    public static final String CMD_LATE_EMPLOYEES_LIST = "/late_employees_list";
    public static final String CMD_MONTH_REPORT = "/oylik_report";
    public static final String CMD_MONTH_EXCEL = "/oylik_excel";
    public static final String CMD_EMPLOYEE_HISTORY = "/xodim_tarixi";
    public static final String CMD_PENDING_CORRECTIONS = "/vaqtni_o'zgartirish_so'rovlari";
    public static final String CMD_PENDING_EARLY_LEAVES = "/erta_ketish_so'rovlari";
    public static final String CMD_PENDING_REGISTRATIONS = "/yangi_xodimlar";
    public static final String CMD_AUDIT_LOG = "/audit_log";
    public static final String CMD_EMPLOYEES = "/Ishchilar";

    public static final String DAILY_REPORT_SHEET = "Kunlik hisobot";
    public static final String SUMMARY_SHEET = "Umumiy hisobot";

    public static final String COLUMN_DATE = "Sana";
    public static final String COLUMN_FULL_NAME = "Ism familiya";
    public static final String COLUMN_DEPARTMENT = "Bo'lim";
    public static final String COLUMN_EXPECTED_START = "Ish boshlanish vaqti";
    public static final String COLUMN_ARRIVAL = "Kelgan vaqt";
    public static final String COLUMN_LEAVING = "Ketgan vaqt";
    public static final String COLUMN_WORKED_HOURS = "Ishlangan vaqt";
    public static final String COLUMN_LATE_TIME = "Kechikish vaqti";
    public static final String COLUMN_ATTENDANCE_STATUS = "Holat";
    public static final String COLUMN_WORKED_DAYS = "Ish kunlari";
    public static final String COLUMN_EXPECTED_DAYS = "Rejadagi ish kunlari";
    public static final String COLUMN_ABSENT_DAYS = "Kelmagan kunlar";
    public static final String COLUMN_MISSING_CHECKOUT_DAYS = "Ketishni belgilamagan kunlar";
    public static final String COLUMN_TOTAL_HOURS = "Jami ishlangan vaqt";
    public static final String COLUMN_LATE_COUNT = "Kechikkan kunlar";
    public static final String COLUMN_TOTAL_LATE_TIME = "Jami kechikish vaqti";

    public static final String STATUS_ABSENT = "Kelmagan";
    public static final String STATUS_MISSING_ARRIVAL = "Kelgan vaqt yo'q";
    public static final String STATUS_LATE = "Kechikkan";
    public static final String STATUS_MISSING_CHECKOUT = "Ketgan vaqt yo'q";
    public static final String STATUS_ON_TIME = "Vaqtida";
}
