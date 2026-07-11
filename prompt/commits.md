# Как закоммитить Firebase push + фикс логина двумя коммитами

Локально с Firebase (не коммитить в application-local.yml — путь машинно-специфичный):

```bash
export FIREBASE_ENABLED=true
export FIREBASE_CREDENTIALS_PATH=/home/tmakhambet/course/pet/KeepBooking/prompt/keep-booking-firebase-adminsdk-fbsvc-d924da91b8.json
./gradlew bootRun --args='--spring.profiles.active=local'
```

## Коммит 1 — Firebase push notifications

```bash
git add backend/build.gradle
git add backend/src/main/java/com/keepbooking/common/config/FirebaseConfig.java
git add backend/src/main/java/com/keepbooking/common/config/AppProperties.java
git add backend/src/main/java/com/keepbooking/notification/
git add backend/src/test/java/com/keepbooking/notification/service/NotificationServiceTest.java
git add backend/src/main/resources/application.yml
git add backend/src/main/resources/db/changelog/
git add backend/.gitignore
git add .gitignore
```

Дальше частями — в `CHANGELOG.md` обе темы (push и фикс логина) слиплись в один hunk, режем вручную:

```bash
git add -p CHANGELOG.md
```

На `Stage this hunk [y,n,q,a,d,e,?]?` жми `e`. В открывшемся редакторе удали 4 строки:
`+### Fixed — коллизия...` и три строки-пункта под ней вместе с пустой строкой после.
Оставь только `+### Added — Push-уведомления...` и всё, что ниже. Сохрани и выйди.

```bash
git add -p versions/keys.md
```

Первый hunk (про JWT/jti) → `n` (это часть багфикса, не сюда).
Второй hunk (чекбокс Push-уведомлений) → `y`.

Проверь и коммить:

```bash
git status
git commit -m "add Firebase Cloud Messaging push notifications"
```

## Коммит 2 — фикс логина (коллизия refresh-токенов)

```bash
git add backend/src/main/java/com/keepbooking/auth/security/JwtTokenProvider.java
git add backend/src/main/java/com/keepbooking/common/exception/GlobalExceptionHandler.java
git add backend/src/main/java/com/keepbooking/common/exception/ErrorCode.java
git add CHANGELOG.md versions/keys.md
git status
git commit -m "fix refresh-token collision on same-second login/register"
```

После второго коммита `git status` должен показать чистое дерево, кроме
`prompt/keep-booking-firebase-adminsdk-fbsvc-d924da91b8.json` (untracked — он в `.gitignore`, это правильно)
и этого файла `prompt/commits.md` (тоже untracked, т.к. `prompt/*.json` игнорится, а `.md` — нет; можно закоммитить
или удалить по желанию).
