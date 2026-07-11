# Changelog

Формат по мотивам [Keep a Changelog](https://keepachangelog.com/ru/1.1.0/). Версий/тегов пока нет — записи сгруппированы по дате.

## [Unreleased] — 2026-07-11

### Added — Этап 2
- Избранное — новый модуль `favorite`: `Favorite` entity (миграция V008, unique constraint `user_id, restaurant_id`), `GET/POST/DELETE /api/v1/favorites`, add/remove идемпотентны, список отдаёт `RestaurantDto` (JOIN FETCH, без N+1)
- Отзывы — новый модуль `review`: `Review` entity (миграция V009), `POST /api/v1/reviews` (только после `COMPLETED`-брони, 1 отзыв на бронь), `GET /api/v1/restaurants/{id}/reviews` (публично), `GET /api/v1/reviews/my`. Пересчитывает `Restaurant.rating`/`reviewsCount` синхронно при создании отзыва

## [Unreleased] — 2026-07-10 — Этап 1 (MVP)

### Added — Инфраструктура
- Gradle wrapper (`gradlew`, `gradlew.bat`, `gradle-wrapper.jar`, Gradle 9.0.0)
- GitHub Actions CI (`.github/workflows/backend-ci.yml`): `./gradlew build` на push/PR в `backend/**` — компиляция, Testcontainers-тест (реальный Postgres, все Liquibase-миграции, Hibernate schema validate) и сборка jar одним шагом

### Added — Auth
- Подтверждение email: токен выдаётся при регистрации, `POST /api/v1/auth/verify-email`. Реальная отправка письма не подключена — токен логируется в консоль (`AuthService.issueToken`)
- Восстановление пароля: `POST /api/v1/auth/forgot-password` (всегда 204, не палит существование email) + `POST /api/v1/auth/reset-password`
- Смена пароля: `POST /api/v1/auth/change-password` (авторизованный пользователь)
- Общая таблица `user_tokens` (миграция V005) для email-verification и password-reset токенов — хеш SHA-256, как у refresh-токенов
- Сброс/смена пароля отзывают все refresh-токены пользователя

### Added — Restaurant
- `HallController` — CRUD `/api/v1/halls`, публичное чтение, владелец ресторана для записи
- `TableController` — CRUD `/api/v1/tables` + batch-update расположения столиков (`PUT /api/v1/tables/batch`) для редактора схемы зала
- `WorkingHoursController` — `GET`/`PUT` (полная замена недели) `/api/v1/restaurants/{id}/working-hours`
- Availability endpoint — `GET /api/v1/restaurants/{id}/availability?date=&from=&to=&guests=`, учитывает статус ресторана, график работы и занятость столиков
- Меню — `MenuItem` entity (миграция V006) + CRUD `/api/v1/menu-items`, публичное чтение, владелец ресторана для записи
- Redis-кэш справочников (`@Cacheable` для countries/cities/cuisines через `ReferenceService`, TTL 5 мин)
- Загрузка фотографий ресторана — MinIO в docker-compose, AWS SDK v2 S3-клиент (path-style access), `RestaurantPhoto` entity (миграция V007) + `/api/v1/restaurants/{id}/photos` (upload/list/delete), публичная read-политика на bucket, лимит 5MB / jpeg,png,webp

### Added — Booking
- Идемпотентность создания брони через заголовок `Idempotency-Key`: Redis-кэш ответа → поиск по `idempotency_key` в БД при промахе кэша → `UNIQUE` constraint как финальная гарантия при гонке
- Scheduled jobs (`BookingSchedulerService`): авто-отмена просроченных `PENDING`-броней по таймауту, авто-перевод прошедших `CONFIRMED`-броней в `COMPLETED`. `NO_SHOW` остаётся ручным действием персонала

### Fixed
- `WorkingHours.dayOfWeek`: несоответствие JDBC-типа (`SMALLINT` в БД vs `INTEGER` по умолчанию для `Integer` в Hibernate) ломало schema-validation при старте — исправлено через `@JdbcTypeCode(SqlTypes.SMALLINT)`
- `WorkingHoursItemRequest.isDayOff`: примитив `boolean` с префиксом `is` конфликтовал с Lombok-генерацией сеттера (`setDayOff` вместо `setIsDayOff`) — Jackson тихо игнорировал поле при десериализации. Заменено на boxed `Boolean`
- `GlobalExceptionHandler` не обрабатывал `HandlerMethodValidationException` — валидация `@Valid List<...>` в теле запроса (Spring 6.1+) падала в 500 вместо 400. Добавлен обработчик — фиксит валидацию списков по всему проекту, не только для working-hours
- Идемпотентность броней: проверка конфликта (`existsConflictingBooking`) срабатывала раньше DB-фолбэка по `Idempotency-Key`, из-за чего повтор запроса после потери Redis-кэша возвращал 409 вместо исходной брони — добавлена прямая проверка по `idempotencyKey` в БД перед проверкой конфликта
- Конфликт порта 5432 между Docker Postgres и системным PostgreSQL — `docker-compose.yml`/`application-local.yml` перемаплены на 5433
- Reference DTO (`CountryDto`, `CityDto`, `CuisineDto`) не реализовывали `Serializable` — дефолтная JDK-сериализация Spring Boot Redis-кэша упала бы в рантайме при первой попытке закэшировать объект
- MinIO bucket приватный по умолчанию — публичные фото ресторанов отдавали 403; добавлена read-политика `s3:GetObject` для `*` на bucket
- `MaxUploadSizeExceededException` (файл больше лимита Spring Multipart) не обрабатывался `GlobalExceptionHandler` — падал в 500 вместо 413

### Changed
- `Co-Authored-By: Claude` trailer убран из истории коммитов (переписана история, force-push)
