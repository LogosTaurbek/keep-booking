# KeepBooking

Online restaurant table reservation platform.

## Tech stack

- Java 21 · Spring Boot 3.5.3 · Gradle 9.0
- PostgreSQL 16 · Redis 7 · Liquibase
- Spring Security · JWT · MapStruct · Lombok
- MinIO (S3-compatible object storage) · AWS SDK v2
- springdoc-openapi (Swagger) · Docker Compose · Testcontainers

## Project structure

```
backend/
├── src/main/java/com/keepbooking/
│   ├── auth/          JWT auth, refresh tokens
│   ├── user/          User profile
│   ├── reference/     Countries, cities, cuisines
│   ├── restaurant/    Companies, restaurants, halls, tables
│   ├── booking/       Reservations
│   └── common/        Shared: errors, pagination, config
└── src/main/resources/
    ├── application.yml
    └── db/changelog/  Liquibase migrations
```

## Quick start

```bash
cd backend

# 1. Generate Gradle wrapper (once)
gradle wrapper --gradle-version=9.0
chmod +x gradlew

# 2. Start infrastructure
docker compose up -d

# 3. Run the app
./gradlew bootRun --args='--spring.profiles.active=local'
```

Swagger UI: http://localhost:8080/swagger-ui.html

## API endpoints

| Method | Path | Description |
|--------|------|-------------|
| POST | /api/v1/auth/register | Register |
| POST | /api/v1/auth/login | Login |
| POST | /api/v1/auth/refresh | Refresh token |
| GET | /api/v1/users/me | My profile |
| GET | /api/v1/restaurants | List restaurants |
| GET | /api/v1/restaurants/{id} | Restaurant details |
| POST | /api/v1/bookings | Create booking |
| GET | /api/v1/bookings/my | My bookings |
| PATCH | /api/v1/bookings/{id}/status | Update booking status |
| GET | /api/v1/countries | Countries |
| GET | /api/v1/cities | Cities |
| GET | /api/v1/cuisines | Cuisines |
