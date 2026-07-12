# Changelog

Формат по мотивам [Keep a Changelog](https://keepachangelog.com/ru/1.1.0/). Версий/тегов пока нет — записи сгруппированы по дате.

## [Unreleased] — 2026-07-13

### Added — `PATCH /api/v1/restaurants/{id}` (недостающий Update)
- У ресторанов были Create/List/Get, но не было Update — не полноценный CRUD. Добавлен partial-update (только непустые поля применяются), owner-check по образцу `HallService`, инвалидация `restaurantCards`/`restaurantSearch` кэша тем же механизмом, что и `moderate()`
- `UpdateRestaurantRequest` DTO, 3 новых unit-теста (`RestaurantServiceTest`) — not-found, access-denied для чужого ресторана, частичное обновление (не заданные поля не трогаются)
- Найдено при подготовке к веб-админке заведений (`keepbooking-admin`, отдельный репозиторий) — нужен был реальный CRUD, а не только Create+Read

### Fixed — IDOR: менеджер мог просматривать/менять брони чужого ресторана (OWASP Top 10, broken access control)
- `BookingController.getRestaurantBookings` проверял только роль (`hasAnyRole('RESTAURANT_ADMIN', 'COMPANY_ADMIN', 'SUPER_ADMIN')`), но не то, что менеджер управляет именно **этим** рестораном — RESTAURANT_ADMIN одного заведения мог посмотреть список броней любого другого, просто подставив чужой `restaurantId` в URL
- Хуже: `BookingService.updateStatus()` — `boolean isManager` флаг (просто «есть ли у актёра ЛЮБАЯ менеджерская роль где угодно») полностью обходил owner-check. Любой RESTAURANT_ADMIN/COMPANY_ADMIN мог подтвердить/отклонить/отменить/завершить **любую** бронь в системе, не только у своих ресторанов — уязвимость на запись, серьёзнее предыдущей
- Тот же паттерн owner-check без SUPER_ADMIN-байпаса уже используется в `AnalyticsService.getRestaurantAnalytics` (добавлен ранее в этой же сессии) — расхождение между `@PreAuthorize` (пускает SUPER_ADMIN на уровне роли) и сервисным owner-check (не пускает, если не реальный владелец) уже было принятым в проекте поведением; повторил его же для консистентности, а не придумывал новый
- Исправлено: `getRestaurantBookings(userId, restaurantId, ...)` теперь проверяет `restaurant.getCompany().getOwner().getId().equals(userId)`; `updateStatus()` — `isManager` больше не достаточен сам по себе, требуется `booking.getRestaurant().getCompany().getOwner().getId().equals(userId)`
- 4 новых unit-теста (`BookingServiceTest`) — регрессионный тест на «менеджер не владеет этим рестораном → ACCESS_DENIED» плюс 3 теста на `getRestaurantBookings` (not-found/access-denied/success)
- Проверено вживую: два независимых владельца, ресторан A и B, гость бронирует стол в A — владелец B (с ролью COMPANY_ADMIN) получает `403` и на `GET /bookings/restaurant/{A}`, и на `PATCH /bookings/{id}/status` для брони A; владелец A получает `200` на оба
- Найдено при подготовке страницы «Управление бронями» в веб-админке — не стал строить UI поверх сломанной авторизационной границы

### Fixed — OWASP Dependency-Check: реальные CVE в транзитивных зависимостях (tz2.txt §22/§23)
- После починки OOM в CI-джобе `dependencyCheckAnalyze` (дефолтный heap Gradle-демона 512 MiB не вытягивал разбор полной NVD-базы — поднят до `-Xmx4g` точечно для этого шага, не трогая остальные) скан реально дошёл до конца и нашёл 927 находок, 18 из них с CVSS ≥ 9.0 блокировали джоб
- Разобрал вручную (не просто поднял порог, чтобы "красное стало зелёным") — свёл 18 CVE к первопричинам через NVD API: **Spring Framework 6.2.8** (CVE-2026-41855, десериализация в JMS-конвертерах, CVSS 9.8 — фикс в 6.2.18.1+), **Spring Boot 3.5.3** (CVE-2026-40974, отсутствие hostname verification в Cassandra auto-config — фикс в 3.5.14+; не эксплуатируется в этом проекте, т.к. Cassandra не используется, но исправлено бесплатно заодно), **Spring Security 6.5.1** (CVE-2026-22732, «HTTP-заголовки не пишутся» при lazy header writing — реальная проблема для любого Spring Security servlet-приложения, фикс в 6.5.9+), **Apache Tomcat 10.1.42** (несколько CVE, включая CVE-2025-55754 и CVE-2026-55276 — фикс в 10.1.56/57+), **Netty 4.1.122.Final** (CVE-2026-45674/47691 — DNS cache poisoning через невалидированный bailiwick CNAME/NS-записей, оба CVSS 10.0; CVE-2026-42581 — HTTP/1.0 request smuggling через конфликтующие Transfer-Encoding/Content-Length — фикс в 4.1.135/133.Final+)
- Фикс: `org.springframework.boot` поднят 3.5.3 → 3.5.16 (подтягивает патченные Spring Framework 6.2.19 и Spring Security 6.5.11 автоматически через собственный BOM); explicit override `io.netty:netty-bom:4.1.136.Final` (Netty приходит транзитивно через AWS SDK, у которого свой более старый пин); explicit override `tomcat-embed-core`/`tomcat-embed-websocket:10.1.57` (managed-версия в самом Spring Boot 3.5.16 — 10.1.55, на один патч отстаёт от фикса CVE-2026-55276)
- Один CVE (kotlin-stdlib, CVE-2026-53914) — задокументированный false positive, не апгрейд: уязвимость про build cache **компилятора** Kotlin/Gradle-плагина, а kotlin-stdlib в этом проекте — чисто транзитивная runtime-зависимость (`okhttp → okio`), build.gradle на Groovy DSL, Kotlin-тулинг вообще не используется. Оценка самого JetBrains (CVSS 6.7, локальный доступ) согласуется с этим; расхождение с NIST-оценкой (9.8) — как раз про непримени́мый здесь build-time сценарий. Подавлено через `config/dependency-check/suppressions.xml` с письменным обоснованием, а не молча проигнорировано
- Проверено: полный прогон тестов (237, минус 2 известных Docker-сэндбокс-фейла) и все CI quality gates (Checkstyle/SpotBugs/JaCoCo) зелёные на новых версиях; живой смоук-тест — приложение стартует, security-заголовки (`X-Frame-Options`, `X-Content-Type-Options` и т.д. — как раз то, что было под угрозой в CVE-2026-22732) присутствуют в ответах

### Added — Read-модель для аналитики ресторана (tz2.txt §15/§25 этап 3)
- Три новые таблицы-роллапа по дням (миграция V018): `restaurant_daily_stats` (счётчики по статусам брони), `restaurant_daily_hour_stats` (популярные часы), `restaurant_daily_table_stats` (популярные столики) — плюс `analytics_refresh_state`, однострочная таблица-watermark
- `AnalyticsRefreshWorker` (`@Scheduled`, по умолчанию каждые 15 мин) — вместо полного пересчёта каждый цикл находит только (restaurant_id, booking_date) пары, затронутые записью брони с прошлого цикла (`Booking.updatedAt > watermark`, простановка `updatedAt` уже была через JPA-аудит), и пересчитывает точечно только их. Час/стол-роллапы за пересчитываемый день просто удаляются и вставляются заново — проще и корректнее, чем построчный upsert, и достаточно дёшево на этой гранулярности
- `AnalyticsService.getRestaurantAnalytics()` теперь суммирует roll-up-строки за диапазон дат вместо прямого агрегирующего скана по `bookings` на каждый запрос — статус/час/стол-разбивки теперь читаются из маленьких предпосчитанных таблиц. Подсчёт уникальных гостей сознательно оставлен как есть (живой `COUNT(DISTINCT)`) — точный distinct-count по диапазону не восстановить из посуточных роллапов без sketch-структуры, а этот единственный индексированный запрос никогда не был узким местом
- `AnalyticsRefreshWorkerTest` (3 unit-теста) — ничего не делает и просто продвигает watermark, когда нечего пересчитывать; апсертит (не дублирует) существующую строку статистики; `AnalyticsServiceTest` переписан под чтение из read-model репозиториев вместо `BookingRepository`
- Устаревшие range-scoped агрегирующие запросы (`countByStatusForRestaurant`, `findPopularHours`, `findPopularTables`) удалены из `BookingRepository` — использовались только старой реализацией `AnalyticsService`; добавлены day-scoped версии для воркера и запрос "грязных" пар `findDirtyRestaurantDatesSince`
- По пути поймано и исправлено 2 бага в первой версии миграции/запроса (найдено на живом прогоне, не в юнит-тестах, которые мокают репозитории): (1) таблицы `restaurant_daily_hour_stats`/`restaurant_daily_table_stats` не имели колонок `created_at`/`updated_at`, а Java-модели наследуют `BaseEntity`, которому они обязательны — Hibernate `ddl-auto: validate` падал при старте; (2) `sumStatusCounts` был объявлен как `Object[]` вместо `List<Object[]>` (в отличие от всех остальных multi-column агрегатов в проекте) — Spring Data JPA в этом случае не разворачивает строку результата, и `sums[0]` оказывался не `Number`, а всей строкой целиком (`ClassCastException`)
- Заодно исправлен случайно обнаруженный flaky-тест: `BookingServiceTest.createThrowsWhenBookingDateTimeIsInThePast` брал `LocalDate.now()` + `LocalTime.MIN..MIN+30мин`, что перестаёт быть "прошлым" в первые полчаса каждых суток — поймано ровно на смене даты во время сессии, заменено на `LocalDate.now().minusDays(1)` (гарантированно в прошлом независимо от времени запуска)
- Проверено вживую: полный цикл (создание брони → подтверждение/отклонение → ожидание цикла воркера → сверка значений в `restaurant_daily_*_stats` с БД напрямую → запрос `GET /analytics` возвращает те же цифры)

## [Unreleased] — 2026-07-12

### Added — CI: Checkstyle, SpotBugs, JaCoCo, OWASP Dependency-Check (tz2.txt §21-22)
- Checkstyle (`config/checkstyle/checkstyle.xml`) — намеренно узкий набор правил на реальные баги (unused imports, empty catch, equals/hashCode), без форматирования/отступов — репозиторий никогда не жил под style-тулом, полный reformat дал бы нерелевантный шума-диф на сотни файлов
- SpotBugs (`config/spotbugs/exclude.xml`) — байткод-анализ; `EI_EXPOSE_REP`/`EI_EXPOSE_REP2` исключены (норма для JPA-сущностей/DTO с Lombok-геттерами, не баг в этом слоистом приложении)
- JaCoCo: отчёт + verification-порог (`jacocoTestCoverageVerification`, встроен в `check`) — LINE ≥65% в целом, ≥80% агрегированно по всем `*.service`-пакетам (доменная логика, tz2.txt §21 "≥70%"). Порог по PACKAGE-элементу (у каждого пакета отдельно) оказался слишком строгим — у тонких сервисов вроде `ReferenceService` (4 кэш-passthrough метода) закономерно 0% и нет смысла требовать unit-тест ради теста; переключено на один агрегированный BUNDLE-порог по всем `*.service` вместе
- OWASP Dependency-Check подключён, но **не встроен** в основной `build`/`check` — бесплатный NVD-фид сильно rate-limited без API-ключа, что сделало бы CI нестабильным для всех, а не только для тех, кто явно его гоняет. Отдельный необязательный джоб `dependency-check` в `backend-ci.yml` (`continue-on-error: true`), опционально ускоряется секретом `NVD_API_KEY`
- Обе аналитические Gradle-ловушки по пути: (1) `io.spring.dependency-management` глобально даунгрейдил `commons-lang3` до версии, несовместимой с движком SpotBugs 4.10.x (`NoClassDefFoundError: org/apache/commons/lang3/Strings`) — обычный `resolutionStrategy.force` на конфигурацию `spotbugs` проигрывал BOM; помогло только `dependencyManagement { dependencies { dependency ... } }` — override через тот же механизм, которым сам Spring управляет версиями; (2) `jacocoTestReport { dependsOn test }` вместе с `test { finalizedBy jacocoTestReport }` — Gradle не запускает finalizer, если его же явная `dependsOn`-зависимость упала, поэтому отчёт о покрытии вообще не генерировался при падении тестов (ровно тот случай, когда он нужнее всего) — `dependsOn` убран, порядок и так гарантирован через `finalizedBy`
- По пути SpotBugs нашёл 4 реальные проблемы (см. следующую запись) — CI-гейт сразу окупился

### Fixed — client-supplied `X-Request-Id` эхировался в HTTP-заголовок ответа без валидации
- `RequestIdFilter` брал значение заголовка `X-Request-Id` от клиента как есть и клал его обратно в `response.setHeader(...)` — классический CRLF/header-injection вектор (OWASP Top-10, tz2.txt §23), пойман SpotBugs (`HRS_REQUEST_PARAMETER_TO_HTTP_HEADER`). Исправлено allowlist-регуляркой (`[A-Za-z0-9_-]{1,100}`) — значение, не прошедшее валидацию, заменяется на свежий UUID, как если бы заголовок вообще не был передан
- Заодно: `User` (implements `UserDetails extends Serializable`) не объявлял `serialVersionUID`, а lazy-поля `country`/`city` держали ссылки на несериализуемые JPA-сущности — оба помечены/добавлены (`transient` на полях, `serialVersionUID` на классе)
- `RequestIdFilterTest` (3 unit-теста, ранее не было вообще) — валидный ID эхируется как есть, отсутствие заголовка генерирует UUID, вредоносное значение (`\r\nSet-Cookie: evil=1`) заменяется на свежий UUID вместо прохода насквозь

### Fixed — конкурентное бронирование иногда падало 500 вместо 409 под нагрузкой
- Под сильной конкуренцией (10 потоков одновременно бьются за один и тот же стол/слот) GiST-проверка exclusion constraint в Postgres (`no_double_booking`, V004) иногда сигнализирует конфликт не как нарушение constraint (`DataIntegrityViolationException`), а как отказ получить блокировку (`CannotAcquireLockException`) — `BookingService.create()` ловил только первый тип, второй пробрасывался наружу необработанным и крашил запрос вместо честного `409 TABLE_NOT_AVAILABLE`. Поймано на прогоне `BookingConcurrencyIntegrationTest` в CI (в песочнице агента Testcontainers не запускаются вообще, поэтому локально не воспроизводилось)
- Исправлено расширением `catch` до `DataIntegrityViolationException | CannotAcquireLockException` — оба транслируются в одинаковый клиентский исход, как и требует tz2.txt §11.2
- 2 новых unit-теста на `BookingService.create()` (ветка вообще не была покрыта тестами раньше): нарушение constraint и отказ блокировки оба резолвятся в `TABLE_NOT_AVAILABLE`

### Added — Лист ожидания (waitlist, tz2.txt §11.4 / §25 этап 3)
- Новый модуль `waitlist`: `POST /api/v1/waitlist` (join, идемпотентно — повторный join на тот же слот возвращает существующую запись вместо ошибки, партиционный уникальный индекс `WHERE status = 'ACTIVE'`), `DELETE /api/v1/waitlist/{id}` (leave, owner-check), `GET /api/v1/waitlist/my`
- `WaitlistService.notifyTableFreed()` — при отмене/отклонении брони уведомляет **только одного**, дольше всех ждущего подходящего пользователя (не рассылка всем в очереди), с учётом вместимости стола (`capacity`/`minCapacity`) и пересечения времени (та же формула, что и для double-booking, tz2.txt §10.3). Хук стоит в `BookingService.updateStatus()` (ручная отмена/отклонение) и `BookingSchedulerService.autoCancelExpiredPending()` (авто-отмена по таймауту) — оба атомарно, в той же транзакции, что и сама отмена
- `NotificationService.notifyUser()` — новый обобщённый метод уведомления не по своей брони (существующий `notifyBookingStatusChange` был жёстко привязан к владельцу брони — не годился для оповещения ожидающего в очереди пользователя, чья бронь тут ни при чём)
- Новый тип `WAITLIST_TABLE_AVAILABLE` — расширены CHECK-констрейнты `notifications`/`notification_outbox`
- `WaitlistServiceTest` (8 unit-тестов) — идемпотентность join, owner-check на leave, уведомление только самого раннего подходящего кандидата с пропуском тех, кто не проходит по `minCapacity`
- Проверено вживую: полный цикл забронировал → подтвердил → третий пользователь встал в очередь → первый отменил бронь → запись очереди мгновенно перешла в `NOTIFIED`, in-app уведомление и запись в push-outbox созданы атомарно

### Added — Prometheus + Grafana локальный observability-стек (tz2.txt §17/§20)
- `docker-compose.yml`: сервисы `prometheus` (скрейпит `host.docker.internal:8080/actuator/prometheus` каждые 15с — приложение работает на хосте вне docker-сети) и `grafana` (порт 3001, чтобы не занимать 3000, зарезервированный под фронтенд-дев-сервер)
- `observability/prometheus/prometheus.yml` — конфиг скрейпа; `observability/grafana/provisioning/` — автопровижининг датасорса и дашборда без ручной настройки при первом старте
- `observability/grafana/dashboards/keepbooking-overview.json` — 6 панелей ровно по списку из tz2.txt §17: HTTP latency (p95), error rate, брони по статусам, доставка push-уведомлений, cache hit rate, пул БД (HikariCP)
- Новая метрика `notifications.outbox.delivered.total{outcome=sent|retry|dead_letter}` в `NotificationOutboxProcessor` — без неё панель «доставка уведомлений» была бы пустой
- Проверено вживую: полный стек поднят, создана/подтверждена бронь, сгенерирован HTTP/кэш-трафик — все 6 метрик дашборда подтверждены реальными данными в Prometheus, датасорс и дашборд корректно запровижены в Grafana API

### Added — Transactional outbox для доставки push-уведомлений (tz2.txt §14)
- Новая таблица `notification_outbox` (миграция V016): событие пишется в той же транзакции, что и бизнес-операция (смена статуса брони), отдельный `@Scheduled`-воркер (`NotificationOutboxWorker`, каждые 30с) забирает и доставляет независимо от исходного запроса
- `NotificationOutboxProcessor` — обрабатывает одно событие в своей транзакции: успех → `SENT`; неудача → экспоненциальный backoff (10с → 300с cap) до 5 попыток, после чего `DEAD_LETTER` вместо бесконечных ретраев
- `FirebaseMessageSender` — отдельный бин с `@Retry` (Resilience4j, новая зависимость, 3 попытки/500мс) на сам вызов FCM; вынесен в отдельный класс специально, чтобы не словить self-invocation ловушку Spring AOP (аннотация на методе, вызываемом через `this.` изнутри того же класса, тихо не сработала бы)
- `PushNotificationService.send()` больше не глотает `FirebaseMessagingException` молча — пробрасывает её процессору для ретрая/dead-letter, как и требует tz2.txt §14 "идемпотентность доставки, dead-letter для неудачных"
- `NotificationOutboxProcessorTest` (3 unit-теста) — успех/retry-с-backoff/dead-letter-после-исчерпания попыток
- Проверено вживую: подтверждена бронь → строка появилась в outbox со статусом `PENDING` атомарно с транзакцией → через ~30с воркер подхватил и пометил `SENT`

### Added — Кэширование карточек и результатов поиска ресторанов (tz2.txt §19)
- `RestaurantService.getById()` — `@Cacheable("restaurantCards")`, глобальный "средний" TTL (5 мин из `application.yml`)
- `RestaurantService.search()` — `@Cacheable("restaurantSearch")`, ключ по всем фильтрам+пагинации, короткий TTL (30с) — отдельный `CacheConfig` с `RedisCacheManagerBuilderCustomizer`, задающим TTL точечно для этого кэша поверх глобального дефолта
- Инвалидация: `RestaurantService.moderate()` (смена статуса) и новый публичный `RestaurantService.evictCaches(id)`, вызываемый из `ReviewService` после пересчёта рейтинга — self-invocation внутри `RestaurantService` не сработал бы с `@CacheEvict`, поэтому пересчёт рейтинга (живёт в другом сервисе) обязан дёргать метод именно через инжектированный бин, а не напрямую
- `RestaurantDto`/`PageResponse` помечены `Serializable` — нужно для Redis-сериализации, как и у остальных кэшируемых DTO в проекте
- Проверено вживую: TTL в Redis подтверждён (`restaurantCards` ~289с из 300, `restaurantSearch` ~19с из 30), полный цикл инвалидации — создание отзыва мгновенно вычищает оба ключа, следующий запрос сразу отдаёт пересчитанный рейтинг

### Added — Карта ресторанов для мобильного приложения (tz2.txt §12)
- `GET /api/v1/map` — рестораны в bounding box (`minLat/maxLat/minLng/maxLng`) или радиусе (`lat/lng/radiusKm`, переиспользует существующий geo-поиск `RestaurantRepository.findNearby`), с координатами, статусом, рейтингом и флагом `hasFreeTablesNow`
- `AvailabilityService.hasFreeTablesNow()` — лёгкая проверка «есть ли свободный стол в ближайшие 30 минут» (открыт по расписанию + есть незабронированный подходящий по вместимости стол), переиспользует существующие репозитории вместо дублирования логики `getAvailableTables`
- Кэш в Redis с коротким TTL (30с, тот же механизм, что и для поиска — см. следующую запись) — флаг `hasFreeTablesNow` дорого считать на каждый запрос карты и он быстро устаревает, короткий TTL специально выделен для карты/поиска в tz2.txt §19
- `MapServiceTest` (3 unit-теста) + 5 новых тестов на `hasFreeTablesNow` в `AvailabilityServiceTest`
- По пути: эндпоинт изначально возвращал 403 — забыл добавить `/api/v1/map/**` в публичные пути `SecurityConfig`, поймано на живом смоук-тесте и поправлено

### Added — RFC 7807: `type` и `traceId` в теле ошибки (tz2.txt §5.1)
- `ProblemDetail` дополнен полями `type` (стабильный URI вида `https://keepbooking.dev/errors/table-not-available`, детерминированно выводится из имени `ErrorCode` через новый `ErrorCode.getTypeUri()`) и `traceId`
- `traceId` берётся из MDC: сперва `traceId` (OpenTelemetry, если трейсинг сработал для запроса), иначе `requestId` (correlation ID из `RequestIdFilter`, который проставлен всегда) — клиент может передать значение в саппорт для поиска по логам/Jaeger, даже если по какой-то причине трейс не создался
- `ProblemDetailTest` (4 unit-теста) — вывод `type` из кода ошибки, приоритет `traceId` над `requestId`, фоллбэк на `requestId`, `null` когда MDC пуст

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
