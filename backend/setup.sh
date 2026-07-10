#!/usr/bin/env bash
# Первичная настройка проекта. Запустить один раз после клонирования.

set -e

echo "=== KeepBooking Backend Setup ==="

if ! command -v gradle &>/dev/null; then
    echo "Gradle не найден. Установите Gradle 9.x: https://gradle.org/install/"
    exit 1
fi

echo "Генерируем Gradle Wrapper (9.0)..."
gradle wrapper --gradle-version=9.0
chmod +x gradlew

echo ""
echo "Запускаем инфраструктуру (PostgreSQL + Redis)..."
docker compose up -d

echo ""
echo "Ждём PostgreSQL..."
until docker exec keepbooking-postgres pg_isready -U keepbooking; do
    sleep 2
done

echo ""
echo "Сборка проекта..."
./gradlew build -x test

echo ""
echo "Готово! Запуск: ./gradlew bootRun --args='--spring.profiles.active=local'"
