# workly-bot

Spring Boot asosidagi Telegram attendance bot.

## Lokal ishga tushirish

Kerakli environment variable'lar:

- `SPRING_DATASOURCE_URL`
- `SPRING_DATASOURCE_USERNAME`
- `SPRING_DATASOURCE_PASSWORD`
- `BOT_TOKEN`
- `BOT_USERNAME` (ixtiyoriy, default: `workly_attendance_bot`)
- `BOT_ADMIN_TELEGRAM_USER_IDS` (`123456789,987654321` ko'rinishida, birinchi admin(lar) uchun)
- `APP_TIME_ZONE` (ixtiyoriy, default: `Asia/Tashkent`)
- `OFFICE_LATITUDE`
- `OFFICE_LONGITUDE`
- `OFFICE_ALLOWED_RADIUS_METERS`
- `OFFICE_WORK_START_TIME`
- `OFFICE_WORK_END_TIME`
- `APP_BASE_URL` (Mini App uchun HTTPS bazaviy URL, masalan `https://your-app.fly.dev`)
- `APP_MINI_APP_DEV_AUTH_ENABLED` (ixtiyoriy, lokal brauzer test uchun `true`)
- `SPRING_JPA_HIBERNATE_DDL_AUTO` (ixtiyoriy, lokalda kerak bo'lsa `update`)

Misol:

```powershell
$env:SPRING_DATASOURCE_URL="jdbc:postgresql://localhost:5432/workly_bot"
$env:SPRING_DATASOURCE_USERNAME="postgres"
$env:SPRING_DATASOURCE_PASSWORD="postgres"
$env:BOT_TOKEN="telegram-bot-token"
$env:BOT_ADMIN_TELEGRAM_USER_IDS="123456789"
$env:APP_TIME_ZONE="Asia/Tashkent"
$env:APP_BASE_URL="https://your-app.fly.dev"
$env:SPRING_JPA_HIBERNATE_DDL_AUTO="update"
./mvnw spring-boot:run
```

`application.yml` ichida faqat xavfsiz default qiymatlar qoldirilgan. Haqiqiy token, parol va production koordinatalarni faqat environment variable yoki deploy secret orqali bering.

## Yangi boshqaruv imkoniyatlari

- Yangi ro'yxatdan o'tgan xodimlar ` /yangi_xodimlar ` orqali ko'rinadi va menejer/admin faollashtiradi.
- Xodimlar `🏃 Erta ketish` tugmasi orqali sabab yuborib, menejer tasdiqlashini kutishi mumkin.
- Menejer/admin uchun qo'shimcha buyruqlar:
  - `/erta_ketish_so'rovlari`
  - `/audit_log`
- Oylik hisobot endi Du-Juma bo'yicha rejalashtirilgan ish kunlari, kelmagan kunlar va ketishni belgilamagan kunlarni ham ko'rsatadi.
- Telegram Mini App paneli `/app/index.html` orqali ochiladi. Bot menyusidagi `🌐 Ilova` tugmasi ishlashi uchun `APP_BASE_URL` sozlangan bo'lishi kerak.
- Lokal brauzerda sinash uchun vaqtincha `APP_MINI_APP_DEV_AUTH_ENABLED=true` qilib, `/app/index.html?userId=<telegram-user-id>` orqali kirish mumkin.

## Fly.io ga deploy

1. Fly CLI o'rnating va login qiling:

```bash
fly auth login
```

2. App yarating:

```bash
fly launch --no-deploy
```

3. Postgres ulang. Agar alohida Fly Postgres app'ingiz bo'lsa, connection string'dan foydalaning:

```bash
fly secrets set \
  SPRING_DATASOURCE_URL="jdbc:postgresql://<host>:5432/<db>?sslmode=require" \
  SPRING_DATASOURCE_USERNAME="<user>" \
  SPRING_DATASOURCE_PASSWORD="<password>" \
  BOT_TOKEN="<telegram-bot-token>" \
  BOT_USERNAME="workly_attendance_bot" \
  BOT_ADMIN_TELEGRAM_USER_IDS="<admin-telegram-user-id>" \
  APP_TIME_ZONE="Asia/Tashkent" \
  OFFICE_LATITUDE="41.360470" \
  OFFICE_LONGITUDE="69.226713" \
  OFFICE_ALLOWED_RADIUS_METERS="50.0" \
  OFFICE_WORK_START_TIME="09:00" \
  OFFICE_WORK_END_TIME="18:00" \
  APP_BASE_URL="https://<app-name>.fly.dev" \
  SPRING_JPA_HIBERNATE_DDL_AUTO="none"
```

4. Deploy qiling:

```bash
fly deploy
```

5. Tekshirish:

```bash
fly status
fly logs
```

Health check endpoint:

- `GET /health`
