# workly-bot

Spring Boot asosidagi Telegram attendance bot.

## Lokal ishga tushirish

Kerakli environment variable'lar:

- `SPRING_DATASOURCE_URL`
- `SPRING_DATASOURCE_USERNAME`
- `SPRING_DATASOURCE_PASSWORD`
- `BOT_TOKEN`
- `BOT_USERNAME` (ixtiyoriy, default: `workly_attendance_bot`)
- `OFFICE_LATITUDE`
- `OFFICE_LONGITUDE`
- `OFFICE_ALLOWED_RADIUS_METERS`
- `OFFICE_WORK_START_TIME`
- `OFFICE_WORK_END_TIME`

Misol:

```powershell
$env:SPRING_DATASOURCE_URL="jdbc:postgresql://localhost:5432/workly_bot"
$env:SPRING_DATASOURCE_USERNAME="postgres"
$env:SPRING_DATASOURCE_PASSWORD="postgres"
$env:BOT_TOKEN="telegram-bot-token"
./mvnw spring-boot:run
```

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
  OFFICE_LATITUDE="41.360470" \
  OFFICE_LONGITUDE="69.226713" \
  OFFICE_ALLOWED_RADIUS_METERS="50.0" \
  OFFICE_WORK_START_TIME="09:00" \
  OFFICE_WORK_END_TIME="18:00"
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
