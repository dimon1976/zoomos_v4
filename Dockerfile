# =======================================================
# ZOOMOS V4 - Docker Multi-Stage Build
# =======================================================

# -------------------------------------------------------
# Стадия 1: Build приложения
# -------------------------------------------------------
FROM maven:3.9-eclipse-temurin-17 AS builder

WORKDIR /build

# Копируем pom.xml и скачиваем зависимости (для кэширования слоев)
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Копируем исходный код и собираем приложение
COPY src ./src
RUN mvn clean package -DskipTests -B

# -------------------------------------------------------
# Стадия 2: Runtime образ
# -------------------------------------------------------
FROM eclipse-temurin:17-jre-jammy

# Метаданные образа
LABEL maintainer="Zoomos Team"
LABEL description="Zoomos v4 - File Processing Application"
LABEL version="4.0"

# Установка необходимых системных пакетов
RUN apt-get update && apt-get install -y --no-install-recommends \
    curl \
    bash \
    tzdata \
    && rm -rf /var/lib/apt/lists/*

# Настройка часового пояса
ENV TZ=Europe/Moscow
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

# Создание пользователя приложения (безопасность)
RUN groupadd -g 1000 zoomos && \
    useradd -r -u 1000 -g zoomos zoomos

# Рабочая директория
WORKDIR /app

# Копирование JAR файла из builder стадии
COPY --from=builder /build/target/*.jar app.jar

# Создание директорий для данных
RUN mkdir -p /app/data/upload/imports \
             /app/data/upload/exports \
             /app/data/temp \
             /app/logs && \
    chown -R zoomos:zoomos /app

# Playwright для RedirectFinderService будет использоваться если доступен
# В контейнере будет работать CurlStrategy и HttpClientStrategy

# Переключение на непривилегированного пользователя
USER zoomos

# Порт приложения
EXPOSE 8081

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8081/actuator/health || exit 1

# JVM параметры для оптимизации в контейнере
ENV JAVA_OPTS="-XX:+UseContainerSupport \
               -XX:MaxRAMPercentage=75.0 \
               -XX:+ExitOnOutOfMemoryError \
               -Djava.security.egd=file:/dev/./urandom"

# Запуск приложения с docker профилем
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar --spring.profiles.active=docker"]
