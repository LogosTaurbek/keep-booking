# KeepBooking — Keys & TODO

## Стек
- Java 21 · Spring Boot 3.5.3 · Gradle 9.0
- PostgreSQL 16 · Redis 7 · Liquibase
- Spring Security · JWT (JJWT 0.12.6)
- MapStruct · Lombok · springdoc-openapi 2.8.6
- Testcontainers · Docker Compose

## Ключевые решения (архитектура)
- Модульный монолит — пакеты: `common`, `auth`, `user`, `reference`, `restaurant`, `booking`
- Роли через `@ElementCollection` → таблица `user_roles` (VARCHAR + CHECK)
- Статусы сущностей — VARCHAR + CHECK constraint (не PostgreSQL ENUM)
- Ошибки — RFC 7807 Problem Details, коды в `ErrorCode` enum
- Пагинация — единый `PageResponse<T>`
- JWT — access 15 мин + refresh в БД (хеш SHA-256), ротация при обновлении
- Защита от double-booking — ДВОЙНАЯ гарантия:
  1. JPQL-проверка в `BookingService.existsConflictingBooking`
  2. `exclusion constraint` (btree_gist + tsrange) в миграции V004
- State machine броней — `BookingStatus.canTransitionTo()` описывает разрешённые переходы
- Владение ресурсом — `company.owner.id == userId` проверяется в сервисном слое (не только роль)
- Email/password-reset токены — единая таблица `user_tokens` (purpose VARCHAR+CHECK, хеш SHA-256, как refresh-токены). Реальная отправка email не подключена — токен логируется в консоль (`AuthService.issueToken`)
- Идемпотентность броней — ТРОЙНАЯ гарантия (по аналогии с double-booking):
  1. Redis-кэш ответа по `Idempotency-Key` (быстрый путь)
  2. Поиск по `bookings.idempotency_key` в БД при промахе кэша (рестарт Redis и т.п.)
  3. `UNIQUE` constraint на `idempotency_key` — финальная гарантия при гонке параллельных ретраев
- Файловое хранилище — AWS SDK v2 S3-клиент с `endpointOverride` на MinIO (path-style access), а не MinIO-специфичный клиент — совместимо и с реальным S3 в проде. Bucket создаётся и получает публичную read-политику при старте (`FileStorageService.ensureBucketExists`, `@PostConstruct`)
- `Restaurant.rating`/`reviewsCount` пересчитываются синхронно при создании отзыва (`ReviewService.recalculateRestaurantRating` — AVG/COUNT одним запросом), а не хранятся денормализованно без источника истины

## Тесты (критичные пути по tz2.txt §21: авторизация, брони, доступность, права доступа)

- `BookingStatusTest` (unit, 14 тестов) — state machine, все переходы + терминальные статусы.
- `BookingServiceTest` (unit/Mockito, 15 тестов) — все проверки перед созданием брони, проверки владения при смене статуса.
- `BookingConcurrencyIntegrationTest` (Testcontainers) — 10 параллельных запросов на один стол/слот → ровно 1 успех, остальные `TABLE_NOT_AVAILABLE` (tz2.txt §11.2). Не прогнан в этой песочнице (несовместимость Docker API), **подтверждён зелёным в реальном GitHub Actions CI**.
- `AuthServiceTest` (unit/Mockito, 16 тестов) — регистрация (дубль email, хеш пароля, verification-токен), login (не найден/заблокирован/успех), refresh (не найден/просрочен-с-отзывом/ротация), verify-email, forgot/reset password (не палит существование email, отзывает refresh-токены), change-password (неверный текущий пароль). `JwtTokenProvider` использован как реальный инстанс, а не мок — токены выпускаются по-настоящему.
- `AvailabilityServiceTest` (unit/Mockito, 9 тестов) — ресторан не найден/не активен, `from >= to`, дата в прошлом, нет графика работы на день, выходной день, вне часов работы, фильтрация уже забронированных столиков.
- `FavoriteServiceTest` (unit/Mockito, 6 тестов) — идемпотентность add/remove, ресторан не найден, маппинг в RestaurantDto.
- `ReviewServiceTest` (unit/Mockito, 7 тестов) — бронь не найдена/не своя/не COMPLETED, дубль отзыва, пересчёт rating/reviewsCount при создании и удалении (включая обнуление, когда отзывов не осталось).
- `HallServiceTest` (unit/Mockito, 9 тестов) — паттерн owner-check (`restaurant.company.owner.id == userId`), общий для Hall/Table/MenuItem/RestaurantPhoto: не найдено, не владелец, дефолты при создании, частичный update, delete.
- `CompanyServiceTest` (unit/Mockito, 9 тестов) — create (юзер не найден, успех), getById (не найдено/не владелец/успех), setBlocked (не найдено, true→BLOCKED, false→ACTIVE), getAllCompanies (paged).
- `TableServiceTest` (unit/Mockito, 13 тестов) — тот же owner-check паттерн + отдельная логика `batchUpdatePositions`: hall не найден/не владелец, id стола не входит в Map этого зала → `TABLE_NOT_FOUND` (ловит в т.ч. "стол из другого зала"), успешное обновление трогает только position-поля (posX/Y/width/height/rotation), не задевая number/capacity.
- `MenuItemServiceTest` (unit/Mockito, 9 тестов) — owner-check CRUD, дефолт `position=0`, частичный update.
- `RestaurantPhotoServiceTest` (unit/Mockito, 7 тестов) — owner-check, авто-инкремент `position` по количеству существующих фото, `delete` проверяет ещё и `photo.restaurant.id == restaurantId` из пути (фото другого ресторана → `RESTAURANT_PHOTO_NOT_FOUND`, а не 403/утечка), успешный delete вызывает и `repository.delete`, и `fileStorageService.delete(url)`.
- Итого: **114 unit-тестов реально прогнаны и зелёные** (подтверждено по XML-отчётам) + 1 integration-тест подтверждён в реальном CI.
- Не покрыто автотестами: admin, notifications, history, rate limiting, search — тестировалось только вручную через curl в течение сессии. Весь owner-check CRUD restaurant-модуля (Hall/Table/MenuItem/RestaurantPhoto) теперь закрыт тестами. До цели tz2 (≥70% покрытия доменной логики) ещё есть куда расти, но все явно перечисленные в tz2 §21 критичные пути закрыты, плюс основные бизнес-правила остальных модулей.
- Геопоиск — PostgreSQL `cube`/`earthdistance` (contrib-модули, как `btree_gist` для double-booking), не PostGIS: `earth_box(...) @>` как index-friendly предфильтр по кубу + точный `earth_distance(...) <=` для реального круга радиуса
- Rate limiting — hand-rolled на Redis вместо Bucket4j (не было интернета проверить актуальные Maven-координаты/API 8.x). Fixed-window, atomic INCR+PEXPIRE через Lua-скрипт (аналогично IdempotencyService). `RateLimitFilter` регистрируется через `.addFilterBefore(jwtAuthFilter, ...)` СНАЧАЛА, затем `.addFilterBefore(rateLimitFilter, JwtAuthFilter.class)` — иначе Spring Security падает с "Filter class does not have a registered order" (нельзя ссылаться на custom-фильтр как якорь до его собственной регистрации)
- Audit log намеренно НЕ пытается покрыть все действия в проекте — только переходы статуса брони (самое бизнес-критичное). Не проектировали заранее generic audit-aspect/interceptor под гипотетические будущие сущности

---

## TODO

### Этап 1 — MVP (в процессе)

#### Инфраструктура
- [x] build.gradle (Spring Boot 3.5.3, Gradle 9.0, все зависимости)
- [x] docker-compose.yml (PostgreSQL + Redis)
- [x] application.yml / application-local.yml
- [x] Liquibase миграции V001–V004 (reference, users, restaurants, bookings)
- [x] .gitignore
- [x] Gradle wrapper jar (сгенерирован, gradle 9.0.0)
- [x] CI (GitHub Actions): `./gradlew build` — компиляция + Testcontainers-тест (реальный Postgres, все Liquibase-миграции, Hibernate schema validate) + сборка jar. Не проверено вживую в этой песочнице (несовместимость Docker API), проверка — через реальный push

#### Модуль common
- [x] BaseEntity (createdAt, updatedAt)
- [x] PageResponse<T>
- [x] ErrorCode enum
- [x] ApiException
- [x] GlobalExceptionHandler (Problem Details)
- [x] ProblemDetail
- [x] AppProperties (@ConfigurationProperties)
- [x] OpenApiConfig (Swagger + Bearer auth)

#### Модуль auth
- [x] JwtTokenProvider (JJWT 0.12.x API)
- [x] JwtAuthFilter
- [x] SecurityConfig (Spring Security 6, stateless)
- [x] CustomUserDetailsService
- [x] RefreshToken entity + repository
- [x] AuthService (register, login, refresh, logout)
- [x] AuthController (POST /api/v1/auth/*)
- [x] Подтверждение email (токен + эндпоинт /verify-email; письмо не отправляется — токен логируется, пока нет email-провайдера)
- [x] Восстановление пароля (forgot-password / reset-password)
- [x] Смена пароля (/change-password)

#### Модуль user
- [x] User entity (implements UserDetails)
- [x] UserRole enum, UserStatus enum
- [x] UserRepository
- [x] UserService (getProfile, updateProfile, deleteAccount soft)
- [x] UserController (GET/PATCH/DELETE /api/v1/users/me)
- [x] UserProfileDto, UpdateProfileRequest, UserMapper

#### Модуль reference
- [x] Country, City, Cuisine entities
- [x] CountryRepository, CityRepository, CuisineRepository
- [x] ReferenceController (GET /countries, /cities, /cuisines — public)
- [x] Кэш в Redis (@Cacheable для справочников; DTO implements Serializable — дефолтная JDK-сериализация Spring Boot Redis cache)

#### Модуль restaurant
- [x] Company entity + CompanyStatus enum
- [x] Restaurant entity + RestaurantStatus enum
- [x] WorkingHours entity
- [x] Hall entity
- [x] RestaurantTable entity + TableType + TableStatus enums
- [x] CompanyRepository, RestaurantRepository, HallRepository, TableRepository
- [x] CompanyService (create, getMyCompanies, owner-check)
- [x] RestaurantService (create, getById, listActive, getMyRestaurants)
- [x] CompanyController (POST /companies, GET /companies/my)
- [x] RestaurantController (GET /restaurants, GET /restaurants/{id}, POST, GET /my)
- [x] HallController (CRUD /api/v1/halls)
- [x] TableController (CRUD /api/v1/tables, batch-update схемы)
- [x] WorkingHoursController (GET + PUT full-replace /api/v1/restaurants/{id}/working-hours)
- [x] Эндпоинт доступности: GET /restaurants/{id}/availability?date=&from=&to=&guests=
- [x] Загрузка фотографий (S3/MinIO): MinIO в docker-compose, AWS SDK v2 S3-клиент (path-style), `RestaurantPhoto` (миграция V007) + `/api/v1/restaurants/{id}/photos`, публичный bucket policy для чтения, лимит 5MB / jpeg,png,webp
- [x] Меню (MenuItem entity + CRUD, /api/v1/menu-items, owner-check как у Hall/Table)

#### Модуль booking
- [x] Booking entity
- [x] BookingStatus enum + state machine (canTransitionTo)
- [x] BookingRepository (existsConflictingBooking)
- [x] BookingService (create с двойной защитой от double-booking, updateStatus)
- [x] BookingController (POST, GET /my, GET /restaurant/{id}, PATCH /{id}/status)
- [x] Идемпотентность через Redis (Idempotency-Key header → кэш ответа, DB unique constraint как финальная гарантия)
- [x] Авто-отмена PENDING по таймауту (scheduled job)
- [x] Авто-перевод в COMPLETED (scheduled job). NO_SHOW остаётся ручным действием персонала — нет сигнала, чтобы отличить от обычного визита

---

### Этап 2

- [x] Избранное (Favorite entity, миграция V008, GET/POST/DELETE /api/v1/favorites — идемпотентные add/remove, unique constraint (user_id, restaurant_id))
- [x] Отзывы (Review entity, миграция V009, POST /api/v1/reviews + GET /api/v1/restaurants/{id}/reviews + GET /api/v1/reviews/my; только после COMPLETED-брони, 1 отзыв на бронь; пересчитывает Restaurant.rating/reviewsCount)
- [x] История (посещения, поиски): GET /api/v1/bookings/my?status= (переиспользует bookings, без нового модуля для визитов) + новый модуль `history` — SearchHistory (миграция V011), GET /api/v1/search-history/my; логируется только для авторизованных при непустых фильтрах
- [x] Поиск с фильтрами (название, кухня, рейтинг) — GET /api/v1/restaurants?name=&cuisine=&minRating=&cityId=, через Specification API (JpaSpecificationExecutor)
- [x] Карта / радиус — GET /api/v1/restaurants/nearby?lat=&lng=&radiusKm=, PostgreSQL cube+earthdistance extensions (миграция V010), earth_box index-friendly pre-filter + точная earth_distance проверка
- [ ] Push-уведомления (Firebase FCM, transactional outbox) — требует внешних credentials, отложено
- [x] In-app уведомления (Notification entity, миграция V012). GET /api/v1/notifications/my, /unread-count, PATCH /{id}/read, POST /read-all. Триггерится из BookingService.updateStatus и BookingSchedulerService (CONFIRMED/REJECTED/CANCELLED/COMPLETED)
- [x] Rate limiting — свой fixed-window лимитер на Redis (atomic Lua INCR+PEXPIRE), не Bucket4j (риск неверных Maven-координат без интернета). `RateLimitFilter` перед JwtAuthFilter, general 100/60с + строгий auth 10/60с на /api/v1/auth/**, по IP (X-Forwarded-For/remoteAddr), исключения — actuator/health, swagger, api-docs
- [x] Structured JSON логи — logback-spring.xml, `net.logstash.logback:logstash-logback-encoder`. JSON везде кроме local-профиля (там читаемый паттерн). `RequestIdFilter` кладёт correlation ID в MDC (X-Request-Id из заголовка или генерируется), попадает в каждую JSON-строку лога
- [x] Audit log — таблица audit_log (миграция V013), `AuditLogService.record(actorId, action, entityType, entityId, details)`, actorId nullable для system/scheduled действий. GET /api/v1/audit-log?entityType=&entityId= (SUPER_ADMIN only). Подключено к BookingService.updateStatus + BookingSchedulerService (auto-cancel/auto-complete)
- [x] Метрики — добавлена `micrometer-registry-prometheus` (её не было, /actuator/prometheus падал в 500 несмотря на упоминание в конфиге). /actuator/prometheus публичный (как /actuator/health) — Prometheus-скрейпер не шлёт JWT. Кастомные счётчики: `bookings.new.total`, `bookings.status.transitions.total{status,trigger}` (trigger=manual|auto). HTTP-latency по эндпоинтам уже бесплатно даёт Spring Boot (`http.server.requests`). Grafana не настраивал — только экспорт метрик
- [ ] Трейсинг (OpenTelemetry)

### Этап 3

- [ ] Аналитика (read-модели для ресторана и супер-админа)
- [x] Суперадмин панель — новый модуль `admin` (@PreAuthorize("hasRole('SUPER_ADMIN')") на весь класс):
  - GET/PATCH /api/v1/admin/users (список, block/unblock — блокировка реально мешает логину через UserDetails.isAccountNonLocked)
  - GET/PATCH /api/v1/admin/companies (список, block/unblock)
  - GET/PATCH /api/v1/admin/restaurants (список по статусу, approve/reject с причиной/block)
  - DELETE /api/v1/admin/reviews/{id} (пересчитывает рейтинг ресторана)
  - GET /api/v1/admin/stats (пользователи/компании/рестораны/брони по статусам)
  - Все мутирующие действия пишутся в audit_log (actorId = админ, details = причина для reject)
  - Реализовано расширением существующих UserService/CompanyService/RestaurantService/ReviewService, а не дублированием логики в отдельном admin-сервисе (только AdminStatsService — агрегация счётчиков)
- [ ] Лист ожидания
- [ ] Предзаказ блюд
- [ ] Онлайн-оплата депозита
- [ ] OAuth (Google, Apple)
- [ ] Интеграция с POS-системами
- [ ] Мультиязычность
- [ ] Elasticsearch для поиска (если PostgreSQL недостаточно)

---

## Запуск

```bash
cd backend
gradle wrapper --gradle-version=9.0   # один раз
chmod +x gradlew
docker compose up -d
./gradlew bootRun --args='--spring.profiles.active=local'
# Swagger: http://localhost:8080/swagger-ui.html
```
