# Multi-stage build для Spring Boot приложения
FROM gradle:8.5-jdk17 AS build

WORKDIR /app

# Копируем файлы проекта
COPY build.gradle settings.gradle gradle.properties ./
COPY gradle ./gradle
COPY src ./src

# Собираем JAR
RUN gradle bootJar --no-daemon

# Финальный образ с поддержкой GPU
FROM nvidia/cuda:11.8.0-runtime-ubuntu22.04

# Устанавливаем wget для healthcheck
RUN apt-get update && apt-get install -y wget && rm -rf /var/lib/apt/lists/*

# Устанавливаем Java в финальном образе
RUN apt-get update && apt-get install -y openjdk-17-jdk && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Копируем JAR из stage сборки
COPY --from=build /app/build/libs/ai-dungeon-master-*.jar app.jar

# Создаем директорию для данных
RUN mkdir -p /app/data

# Открываем порт
EXPOSE 8080

# Запускаем приложение с UTF-8 кодировкой
ENTRYPOINT ["java", "-Dfile.encoding=UTF-8", "-Duser.language=ru", "-Duser.country=RU", "-jar", "app.jar"]
