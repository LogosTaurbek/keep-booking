# Changelog

Формат по мотивам [Keep a Changelog](https://keepachangelog.com/ru/1.1.0/). Версий/тегов пока нет — записи сгруппированы по дате.

## [Unreleased] — 2026-07-12

### Added — Праздники и ночной график в рабочих часах (tz2.txt §8)
- Новая таблица `restaurant_working_hours_overrides` (миграция V015): per-date override недельного расписания (`date`, `openTime`/`closeTime`, `isClosed`), уникальна по `(restaurant_id, date)`. Полностью замещает недельную запись на конкретную дату, когда присутствует
- `WorkingHoursResolver` — единая точка резолвинга "открыт ли ресторан на [from,to) в дату X": сначала смотрит override на эту дату, иначе — недельное расписание по `dayOfWeek`. Понимает ночной график (`closeTime < openTime`, например 18:00–02:00): вечерняя часть проверяется в рамках текущего дня, а раннее утро (например бронь на 01:00) дополнительно сверяется с ночным графиком ВЧЕРАШНЕГО дня — раньше при закрытии после полуночи бронь в первые часы суток либо неверно отклонялась, либо неверно принималась в зависимости от того, как была настроена запись на "сегодня"
- `AvailabilityService` и `WorkingHoursService` переведены на общий `WorkingHoursResolver` вместо прямой работы с `WorkingHoursRepository`, чтобы override/overnight-логика не дублировалась
- `PUT/GET /api/v1/restaurants/{id}/working-hours/overrides`, `DELETE /api/v1/restaurants/{id}/working-hours/overrides/{date}` — CRUD праздников/спецдней, owner-check как у остального расписания
- Валидация: если день/override не помечен как closed/dayOff, `openTime`/`closeTime` обязательны и не могут совпадать (раньше это не проверялось вообще — можно было сохранить нерабочую конфигурацию без ошибки)

### Fixed — `BookingService.create()` не проверял рабочие часы вообще
- Проверка графика работы существовала только в read-only `GET /availability` (для списка свободных столиков); сам `POST /bookings` её не делал — можно было создать бронь в закрытый день или вне часов работы, просто не заходя на `/availability` перед этим. Обнаружено по пути при добавлении overrides/overnight-логики. `BookingService.create()` теперь тоже вызывает `WorkingHoursResolver.isOpenAt(...)` и возвращает `BOOKING_RESTAURANT_CLOSED` (как и `/availability`)
- `WorkingHoursResolverTest` (11 unit-тестов) — override поверх недельного расписания, обычные часы, ночной график (вечерняя часть и перенос через полночь с проверкой предыдущего дня), отсутствие переноса с не-ночного предыдущего дня. `AvailabilityServiceTest`/`BookingServiceTest`/`WorkingHoursServiceTest` обновлены под новые зависимости и дополнены тестами на override CRUD, валидацию часов и `BOOKING_RESTAURANT_CLOSED` при создании брони

## [Unreleased] — 2026-07-11

### Added — Аналитика ресторана (tz2.txt §15)
- Новый модуль `analytics`: `GET /api/v1/restaurants/{restaurantId}/analytics?from=&to=`, только для владельца ресторана (`RESTAURANT_ADMIN`/`COMPANY_ADMIN`/`SUPER_ADMIN` + owner-check по `restaurant.company.owner`, тот же паттерн, что у Hall/Table/MenuItem/RestaurantPhoto/WorkingHours)
- Отдаёт по диапазону дат: разбивку броней по статусам (`PENDING/CONFIRMED/REJECTED/CANCELLED/COMPLETED/NO_SHOW`), топ-5 популярных часов (группировка по `EXTRACT(HOUR FROM timeFrom)`), топ-5 популярных столиков, число уникальных гостей (`COUNT(DISTINCT userId)`) — новые агрегирующие JPQL-запросы в `BookingRepository`
- `confirmationRate` — доля броней, покинувших `PENDING` подтверждёнными (`CONFIRMED/COMPLETED/NO_SHOW`) против отклонённых/отменённых. Это оценка снизу: бронь, подтверждённую и затем отменённую, невозможно отличить от отменённой сразу из `PENDING` без таблицы истории статусов — ограничение задокументировано в Javadoc `RestaurantAnalyticsDto.confirmationRate`
- Сознательно не реализовано в этой итерации: «средняя загрузка» (требует моделирования времени/вместимости, которого сейчас нет), «средний чек» (цена не привязана к брони) и «динамика рейтинга» (нет исторических снепшотов рейтинга) — решено не подделывать эти метрики приблизительными числами, а оставить их до появления нужных данных
- Ранее был только `AdminStatsService` — общесистемная статистика для `SUPER_ADMIN`, без разбивки по конкретному ресторану для его владельца
- `AnalyticsServiceTest` (6 unit-тестов) — owner-check, `from > to` → `VALIDATION_ERROR`, агрегация по статусам, расчёт `confirmationRate`, нулевой `confirmationRate` когда все брони ещё `PENDING`

### Fixed — проверки времени брони игнорировали часовой пояс ресторана
- `BookingService.validateBookingTime` и `AvailabilityService.validateTimeRange` сравнивали `date`+`timeFrom` (локальное время заведения) с `LocalDateTime.now(ZoneId.of("UTC"))` — жёстко захардкоженным UTC вместо `restaurant.getTimezone()`. Колонка `restaurants.timezone` существовала в БД и API с самого начала (Этап 1), но нигде не читалась при проверке "бронь не в прошлом". Для ресторана не в UTC (например, Алматы, UTC+5) это давало неверный результат на границе суток — 23:30 по местному времени ресторана могло ошибочно приниматься как уже прошедшее (или наоборот, пропускать бронь, которая по местному времени уже в прошлом). Найдено при сверке проекта с tz2.txt §6/§7 (требование "бизнес-правила бронирования считаются в таймзоне заведения")
- Исправлено: обе проверки теперь берут `restaurant.getTimezone()` и сравнивают с `LocalDateTime.now(ZoneId.of(restaurantTimezone))`. `WorkingHoursService`/`AvailabilityService.validateOpenHours` уже были корректны — они не считают "now", а сравнивают время интервала с графиком работы посуточно, поэтому не требовали изменений
- `BookingServiceTest`/`AvailabilityServiceTest` зелёные без изменений — тестовые `Restaurant` строятся через `.builder()` без явного `.timezone(...)`, `@Builder.Default` подставляет `"UTC"`, что сохраняет прежнее поведение тестов

### Fixed — коллизия refresh-токенов при login/register в одну секунду
- `JwtTokenProvider.buildToken()` строил `iat`/`exp` из `Date` без `jti` — JWT-спека сериализует эти поля с точностью до секунды, поэтому `generateRefreshToken(email)` был чистой функцией от `(email, текущая секунда, secret)`. Два токена одному пользователю в одну и ту же секунду (например `register`, сразу за которым `login`, или два параллельных логина) получались побайтово идентичными → одинаковый SHA-256-хэш → второй `INSERT` в `refresh_tokens` бился об `UNIQUE(token_hash)` → `DataIntegrityViolationException`. Найдено вживую при smoke-тесте push-уведомлений (register сразу за которым login). Исправлено добавлением случайного `jti` (JWT ID, RFC 7519 §4.1.7) в каждый токен
- `GlobalExceptionHandler.handleDataIntegrity()` слепо мапил любое `DataIntegrityViolationException` на бронирования-специфичный `TABLE_002/TABLE_NOT_AVAILABLE` — коллизия refresh-токена возвращала абсурдный "стол недоступен" на попытку логина. Добавлен честный generic-код `DATA_CONFLICT` (`GEN_003`) как fallback; кейс `no_double_booking` по-прежнему маппится на `TABLE_NOT_AVAILABLE`
- Проверено вживую: register сразу за которым login (та же секунда) и 2 параллельных логина одному юзеру — оба сценария теперь `200 OK` вместо `409 TABLE_002`

### Added — Push-уведомления (Firebase FCM)
- `com.google.firebase:firebase-admin:9.4.3` — версия не проверена вживую на Maven Central (нет доступа в интернет из песочницы, как ранее с Bucket4j), стоит перепроверить перед `./gradlew build` с реальным интернетом
- `device_tokens` (миграция V014): один или несколько FCM-токенов на пользователя (`user_id`, `token` UNIQUE, `platform` ANDROID/IOS/WEB)
- `POST /api/v1/device-tokens` (register, идемпотентно — если токен уже привязан к другому юзеру, переattach-ится, как при повторном логине на том же устройстве другим аккаунтом), `DELETE /api/v1/device-tokens/{token}` (unregister, например на logout)
- `FirebaseConfig` регистрирует `FirebaseMessaging`-бин только при `app.firebase.enabled=true` (`FIREBASE_ENABLED` / `FIREBASE_CREDENTIALS_PATH` env). По умолчанию выключено — большинство окружений (локальная разработка, CI) не имеют service account key
- `PushNotificationService` принимает `Optional<FirebaseMessaging>` и молча деградирует в no-op, если бин не создан — остальной notification-модуль (in-app уведомления) работает без изменений вне зависимости от наличия Firebase credentials
- Подключено к `NotificationService.notifyBookingStatusChange` — push уходит с тем же title/message, что и in-app уведомление, при переходах брони в `CONFIRMED`/`REJECTED`/`CANCELLED`/`COMPLETED`
- `sendEachForMulticast` разом на все токены пользователя; при ошибке `MessagingErrorCode.UNREGISTERED` для конкретного токена (приложение удалено/токен истёк) — токен удаляется из `device_tokens`, остальные токены партии не страдают
- Ошибки отправки (`FirebaseMessagingException`) логируются и проглатываются — не откатывают транзакцию сохранения in-app уведомления

### Added — OpenTelemetry-трейсинг
- `micrometer-tracing-bridge-otel` + `opentelemetry-exporter-otlp`, экспорт по OTLP/HTTP (`management.otlp.tracing.endpoint`, по умолчанию `http://localhost:4318/v1/traces`), sampling 100% по умолчанию, оба параметра переопределяются через env
- В `docker-compose.yml` добавлен сервис `jaeger` (all-in-one) — UI на :16686, OTLP receiver на :4318
- HTTP-запросы, Spring Security filter chain и `@Scheduled`-задачи `BookingSchedulerService` трассируются автоматически из коробки
- traceId/spanId из MDC micrometer-tracing добавлены в JSON-логи (LogstashEncoder `includeMdcKeyName`) и в консольный паттерн local-профиля — логи и трейсы коррелируются по requestId/traceId/spanId
- Проверено вживую: docker-compose up + bootRun + curl → трейсы видны в Jaeger UI и через `/api/services`, `/api/traces`; traceId/spanId подтверждены в JSON-логах

### Fixed
- `BookingDto` не имел no-args конструктора (`@Data @Builder` без `@NoArgsConstructor`), из-за чего `IdempotencyService.get()` никогда не мог десериализовать закэшированный ответ из Redis — каждый cache hit тихо трактовался как cache miss (перехватывалось и логировалось как warning). Redis-кэш идемпотентности не работал с момента внедрения; спасал только DB-фоллбэк по `idempotency_key`, поэтому баг не проявлялся в ручном/curl-тестировании. Найдено при написании `IdempotencyServiceTest`. Исправлено добавлением `@NoArgsConstructor @AllArgsConstructor`

### Added — Тесты RestaurantService/UserService/WorkingHoursService/BookingScheduler/Idempotency
- `RestaurantServiceTest` (9 unit-тестов), `UserServiceTest` (9), `WorkingHoursServiceTest` (6), `BookingSchedulerServiceTest` (4), `IdempotencyServiceTest` (5)
- Итого 178 unit-тестов реально прогнаны и зелёные (+ 1 integration-тест подтверждён в CI)

### Added — Тесты rate limiting и search-фильтров
- `RateLimitServiceTest` (4 unit-теста) — контракт tryConsume: true при current<=limit, false при превышении, fail-closed при null от Redis
- `RateLimitFilterTest` (7 unit-тестов) — exempt-пути, auth vs general лимит/окно, X-Forwarded-For приоритет над remoteAddr, форма 429-ответа
- `RestaurantSpecificationsTest` (9 unit-тестов) — каждый опциональный фильтр возвращает null Predicate при null/blank входе (иначе AND-комбинирование в поиске сломается), корректный predicate/join/distinct при непустом значении
- Итого 145 unit-тестов реально прогнаны и зелёные (+ 1 integration-тест подтверждён в CI). Единственный намеренно не покрытый юнит-тестами кусок — `AdminStatsService` (чистая агрегация count())

### Added — Тесты NotificationService/SearchHistoryService
- `NotificationServiceTest` (5 unit-тестов) — markAsRead (не найдено/чужое/успех), markAllAsRead
- `SearchHistoryServiceTest` (6 unit-тестов) — тихий no-op при анонимном или беcфильтровом поиске, сохранение при наличии фильтра
- Итого 125 unit-тестов реально прогнаны и зелёные (+ 1 integration-тест подтверждён в CI)

### Added — Тесты MenuItemService/RestaurantPhotoService
- `MenuItemServiceTest` (9 unit-тестов) — owner-check CRUD, дефолт position=0, частичный update
- `RestaurantPhotoServiceTest` (7 unit-тестов) — owner-check, авто-инкремент position, delete проверяет принадлежность фото указанному restaurantId (не только существование), удаление файла из storage
- Итого 114 unit-тестов реально прогнаны и зелёные (+ 1 integration-тест подтверждён в CI) — весь owner-check CRUD restaurant-модуля (Hall/Table/MenuItem/RestaurantPhoto) теперь покрыт тестами

### Added — Тесты CompanyService/TableService
- `CompanyServiceTest` (9 unit-тестов) — create, getById owner-check, setBlocked (true/false), getAllCompanies (paged)
- `TableServiceTest` (13 unit-тестов) — owner-check CRUD + `batchUpdatePositions` (hall не найден/не владелец, table id не в этом зале → `TABLE_NOT_FOUND`, обновляются только position-поля)
- Итого 98 unit-тестов реально прогнаны и зелёные (+ 1 integration-тест подтверждён в CI)

### Added — Суперадмин панель
- Новый модуль `admin`: `GET/PATCH /api/v1/admin/users` (список, block/unblock), `GET/PATCH /api/v1/admin/companies` (список, block/unblock), `GET/PATCH /api/v1/admin/restaurants` (список по статусу, approve/reject с причиной, block), `DELETE /api/v1/admin/reviews/{id}`, `GET /api/v1/admin/stats`
- Все эндпоинты только для `SUPER_ADMIN`, все мутирующие действия пишутся в `audit_log`
- Реализовано расширением существующих `UserService`/`CompanyService`/`RestaurantService`/`ReviewService`, а не отдельным дублирующим слоем логики

### Added — Тесты favorites/reviews/restaurant CRUD
- `FavoriteServiceTest` (6 unit-тестов) — идемпотентность add/remove
- `ReviewServiceTest` (7 unit-тестов) — бизнес-правило "только после COMPLETED, 1 отзыв на бронь", пересчёт rating/reviewsCount при создании и удалении
- `HallServiceTest` (9 unit-тестов) — паттерн owner-check, общий для Hall/Table/MenuItem/RestaurantPhoto
- Итого 76 unit-тестов реально прогнаны и зелёные

### Added — Тесты авторизации и доступности
- `AuthServiceTest` (16 unit-тестов) — регистрация, login, refresh-ротация, verify-email, forgot/reset/change password. Реальный `JwtTokenProvider`, не мок
- `AvailabilityServiceTest` (9 unit-тестов) — все проверки доступности столиков (статус ресторана, время, график работы, фильтрация занятых)
- `BookingConcurrencyIntegrationTest` подтверждён зелёным в реальном GitHub Actions CI (не только компилируется — реально проверяет double-booking guarantee под конкурентной нагрузкой)
- Итого 54 unit-теста реально прогнаны и зелёные — все критичные пути из tz2.txt §21 (авторизация, брони, доступность, права доступа) теперь покрыты

### Added — Тесты критичного пути бронирования
- `BookingStatusTest` (14 unit-тестов) — вся state machine `BookingStatus.canTransitionTo()`
- `BookingServiceTest` (15 unit-тестов, Mockito) — все проверки перед созданием брони и при смене статуса, идемпотентность по кэшу. Реально прогнаны локально (29/29 зелёных, подтверждено по XML-отчётам, не только по "BUILD SUCCESSFUL")
- `BookingConcurrencyIntegrationTest` (Testcontainers) — 10 параллельных запросов на один стол/слот → ровно 1 успех (tz2.txt §11.2). Компилируется, но не прогнан в этой песочнице — известная несовместимость Docker API с Testcontainers (см. CI); ожидает подтверждения в GitHub Actions

### Fixed
- CI: Testcontainers/Postgres/Redis в GitHub Actions отработали нормально (в отличие от локальной песочницы), но `@SpringBootTest`-контексты (`KeepBookingApplicationTests`, `BookingConcurrencyIntegrationTest`) падали при старте — `FileStorageService.ensureBucketExists()` (`@PostConstruct`) не мог достучаться до MinIO, которого не было в CI-воркфлоу. MinIO нельзя поднять через `services:` (образу нужна кастомная команда, которую `services:` не поддерживает) — добавлен явный шаг `docker run minio/minio server /data ...` + health-check перед сборкой

### Added — Этап 2
- Избранное — новый модуль `favorite`: `Favorite` entity (миграция V008, unique constraint `user_id, restaurant_id`), `GET/POST/DELETE /api/v1/favorites`, add/remove идемпотентны, список отдаёт `RestaurantDto` (JOIN FETCH, без N+1)
- Отзывы — новый модуль `review`: `Review` entity (миграция V009), `POST /api/v1/reviews` (только после `COMPLETED`-брони, 1 отзыв на бронь), `GET /api/v1/restaurants/{id}/reviews` (публично), `GET /api/v1/reviews/my`. Пересчитывает `Restaurant.rating`/`reviewsCount` синхронно при создании отзыва
- Поиск ресторанов с фильтрами — `GET /api/v1/restaurants?name=&cuisine=&minRating=&cityId=`, композиция через `RestaurantSpecifications` (Spring Data JPA Specification API)
- Геопоиск/карта — `GET /api/v1/restaurants/nearby?lat=&lng=&radiusKm=`, PostgreSQL `cube`+`earthdistance` extensions (миграция V010)
- История — `GET /api/v1/bookings/my?status=` для истории посещений (переиспользует существующий booking-эндпоинт); новый модуль `history` — `SearchHistory` entity (миграция V011), `GET /api/v1/search-history/my`, логируется только для авторизованных пользователей при непустых фильтрах поиска
- Rate limiting — `RateLimitFilter` + `RateLimitService`, fixed-window счётчик на Redis (atomic Lua INCR+PEXPIRE). General 100 запросов/60с + строгий лимит 10/60с на `/api/v1/auth/**`, ключ по IP. Не Bucket4j (см. Fixed) — hand-rolled решение по тому же паттерну, что `IdempotencyService`
- In-app уведомления — новый модуль `notification`: `Notification` entity (миграция V012), `GET /api/v1/notifications/my`, `/unread-count`, `PATCH /{id}/read`, `POST /read-all`. Триггерится из `BookingService.updateStatus` и `BookingSchedulerService` при переходах в `CONFIRMED`/`REJECTED`/`CANCELLED`/`COMPLETED`. Push (Firebase FCM) отложен — нужны внешние credentials
- Structured JSON логи — `logback-spring.xml` + `logstash-logback-encoder`, JSON везде кроме `local`-профиля (там читаемый паттерн для дев-опыта). `RequestIdFilter` — correlation ID в MDC (из `X-Request-Id` или генерируется), эхается в заголовке ответа и попадает в каждую JSON-строку лога
- Audit log — таблица `audit_log` (миграция V013), `AuditLogService`, `GET /api/v1/audit-log?entityType=&entityId=` (только `SUPER_ADMIN`). Подключено к переходам статуса брони (ручным и авто через scheduler); `actorId` nullable для системных действий
- Метрики — `micrometer-registry-prometheus` + кастомные бизнес-счётчики `bookings.new.total` и `bookings.status.transitions.total{status,trigger}` (trigger=manual|auto). HTTP-latency по эндпоинтам — бесплатно от Spring Boot (`http.server.requests`)

### Fixed
- `MissingServletRequestParameterException` и `MethodArgumentTypeMismatchException` не обрабатывались `GlobalExceptionHandler` — отсутствующий или некорректный query-параметр падал в 500 вместо 400. Фиксит все query-параметры по проекту, не только `/restaurants/nearby`
- При регистрации `RateLimitFilter` через `addFilterBefore(rateLimitFilter, JwtAuthFilter.class)` до регистрации самого `JwtAuthFilter` — Spring Security падал при старте ("Filter class does not have a registered order"); порядок вызовов `addFilterBefore` пришлось поменять
- 429-ответ от `RateLimitFilter` отдавался с `charset=ISO-8859-1` вместо `UTF-8` (сервлетный дефолт) — потенциальная порча кириллицы в будущих сообщениях об ошибках
- `/actuator/prometheus` уже был указан в `management.endpoints.web.exposure.include`, но падал в 500 — отсутствовала зависимость `micrometer-registry-prometheus` (эндпоинт был замаплен, но без реального `PrometheusMeterRegistry` бина)
- Счётчик с именем, содержащим слово `created` (`bookings.created.total`), тихо терял это слово в Prometheus-выводе (`bookings_total` вместо `bookings_created_total`) — коллизия с зарезервированным OpenMetrics-суффиксом `_created`. Переименован в `bookings.new.total`

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
