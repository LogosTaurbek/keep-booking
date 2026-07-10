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

---

## TODO

### Этап 1 — MVP (в процессе)

#### Инфраструктура
- [x] build.gradle (Spring Boot 3.5.3, Gradle 9.0, все зависимости)
- [x] docker-compose.yml (PostgreSQL + Redis)
- [x] application.yml / application-local.yml
- [x] Liquibase миграции V001–V004 (reference, users, restaurants, bookings)
- [x] .gitignore
- [ ] Gradle wrapper jar (сгенерировать: `gradle wrapper --gradle-version=9.0`)
- [ ] CI (GitHub Actions): build + test + liquibase validate

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
- [ ] Подтверждение email (токен + эндпоинт /verify-email)
- [ ] Восстановление пароля (forgot-password / reset-password)
- [ ] Смена пароля (/change-password)

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
- [ ] Кэш в Redis (@Cacheable для справочников)

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
- [ ] HallController (CRUD /api/v1/halls)
- [ ] TableController (CRUD /api/v1/tables, batch-update схемы)
- [ ] WorkingHoursController (CRUD /api/v1/restaurants/{id}/working-hours)
- [ ] Эндпоинт доступности: GET /restaurants/{id}/availability?date=&from=&to=&guests=
- [ ] Загрузка фотографий (S3/MinIO)
- [ ] Меню (MenuItem entity + CRUD)

#### Модуль booking
- [x] Booking entity
- [x] BookingStatus enum + state machine (canTransitionTo)
- [x] BookingRepository (existsConflictingBooking)
- [x] BookingService (create с двойной защитой от double-booking, updateStatus)
- [x] BookingController (POST, GET /my, GET /restaurant/{id}, PATCH /{id}/status)
- [ ] Идемпотентность через Redis (Idempotency-Key header → кэш ответа)
- [ ] Авто-отмена PENDING по таймауту (scheduled job)
- [ ] Авто-перевод в COMPLETED/NO_SHOW (scheduled job)

---

### Этап 2

- [ ] Избранное (Favorite entity, GET/POST/DELETE /api/v1/favorites)
- [ ] Отзывы (Review entity, только после COMPLETED-брони)
- [ ] История (посещения, поиски)
- [ ] Поиск с фильтрами (название, кухня, рейтинг, радиус)
- [ ] Карта (GET рестораны в bbox/радиусе)
- [ ] Push-уведомления (Firebase FCM, transactional outbox)
- [ ] In-app уведомления (Notification entity)
- [ ] Rate limiting (Bucket4j + Redis)
- [ ] Кэш справочников в Redis (@Cacheable)
- [ ] Structured JSON логи (logback + logstash-encoder)
- [ ] Audit log (таблица audit_log)
- [ ] Метрики (Micrometer → Prometheus → Grafana)
- [ ] Трейсинг (OpenTelemetry)

### Этап 3

- [ ] Аналитика (read-модели для ресторана и супер-админа)
- [ ] Суперадмин панель (модерация, блокировки, статистика)
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
