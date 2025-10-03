# 🐳 Zoomos v4 - Docker Deployment Guide

## Быстрый старт

### Требования
- Docker Desktop for Windows 4.0+ ([скачать](https://www.docker.com/products/docker-desktop))
- WSL2 (Windows Subsystem for Linux 2)
- 2GB+ свободной оперативной памяти
- 10GB+ свободного места на диске

### Установка Docker на Windows

1. **Скачать Docker Desktop**
   - Перейти на https://www.docker.com/products/docker-desktop
   - Скачать Docker Desktop for Windows
   - Запустить установщик

2. **Включить WSL2** (если не включен)
   ```powershell
   # Запустить PowerShell от имени администратора
   wsl --install

   # Или обновить WSL2
   wsl --update
   ```

3. **Настроить Docker Desktop**
   - Запустить Docker Desktop
   - Settings → General → Use WSL2 based engine ✅
   - Settings → Resources → WSL Integration → Enable integration
   - Apply & Restart

4. **Проверить установку**
   ```powershell
   docker --version
   docker compose version
   ```

### Запуск приложения на Windows

```powershell
# 1. Открыть PowerShell или CMD
# Перейти в папку проекта
cd d:\project\zoomos_v4

# 2. Запустить приложение в фоновом режиме
docker compose up -d

# 3. Открыть браузер
# Перейти на http://localhost:8081
```

**Альтернативно через Git Bash:**
```bash
cd /d/project/zoomos_v4
docker compose up -d
```

**Готово!** Приложение автоматически:
- ✅ Соберет JAR файл
- ✅ Создаст Docker образ
- ✅ Запустит PostgreSQL базу данных
- ✅ Выполнит миграции Flyway
- ✅ Установит Playwright браузеры
- ✅ Запустит веб-приложение

---

## 📋 Основные команды

### Управление контейнерами

```bash
# Запуск приложения
docker compose up -d

# Остановка приложения
docker compose down

# Перезапуск приложения
docker compose restart

# Просмотр логов
docker compose logs -f

# Просмотр логов только приложения
docker compose logs -f app

# Просмотр логов только БД
docker compose logs -f postgres

# Статус контейнеров
docker compose ps
```

### Сборка и обновление

```bash
# Пересборка образа после изменения кода
docker compose build --no-cache

# Пересборка и запуск
docker compose up -d --build

# Остановка и удаление всех контейнеров
docker compose down
```

### Полная очистка (ВНИМАНИЕ: удалит все данные!)

```bash
# Остановка и удаление контейнеров, volumes, сетей
docker compose down -v

# Удаление образов
docker rmi zoomos_v4-app postgres:16-alpine
```

---

## 🗄️ Управление данными

### Volumes (постоянное хранение)

Приложение использует Docker volumes для хранения:

- **zoomos_postgres_data** - База данных PostgreSQL
- **zoomos_uploads** - Загруженные файлы (импорт)
- **zoomos_exports** - Экспортированные файлы
- **zoomos_temp** - Временные файлы
- **zoomos_logs** - Логи приложения

### Маппинг директорий файлов

| Внутри контейнера | Docker Volume | Назначение |
|-------------------|---------------|------------|
| `/app/data/upload` | `zoomos_uploads` | **Импортированные файлы** |
| `/app/data/upload/exports` | `zoomos_exports` | **Экспортированные файлы** |
| `/app/data/temp` | `zoomos_temp` | Временные файлы обработки |
| `/app/logs` | `zoomos_logs` | Логи приложения |

### Просмотр volumes

```bash
# Список всех volumes
docker volume ls | grep zoomos

# Информация о конкретном volume
docker volume inspect zoomos_uploads

# Размер занятого места
docker system df -v
```

### Доступ к файлам импорта/экспорта

```bash
# Просмотр экспортированных файлов
docker exec -it zoomos_app ls -lh /app/data/upload/exports

# Просмотр импортированных файлов
docker exec -it zoomos_app ls -lh /app/data/upload

# Открыть shell для навигации
docker exec -it zoomos_app sh
cd /app/data/upload/exports
ls -lh
```

### Резервное копирование данных

```bash
# Бэкап базы данных
docker exec zoomos_postgres pg_dump -U postgres zoomos_v4 > backup_$(date +%Y%m%d).sql

# Бэкап загруженных файлов
docker run --rm -v zoomos_uploads:/data -v $(pwd):/backup alpine tar czf /backup/uploads_$(date +%Y%m%d).tar.gz /data

# Восстановление базы данных
cat backup_20250101.sql | docker exec -i zoomos_postgres psql -U postgres zoomos_v4
```

### Копирование файлов из контейнера на хост

```bash
# Скопировать ВСЕ экспортированные файлы на хост
docker cp zoomos_app:/app/data/upload/exports ./local_exports/

# Скопировать конкретный экспортированный файл
docker cp zoomos_app:/app/data/upload/exports/my_export.xlsx ./

# Скопировать импортированные файлы
docker cp zoomos_app:/app/data/upload ./local_backup/

# Скопировать логи
docker cp zoomos_app:/app/logs ./logs_backup/
```

**Важно:**
- ✅ Файлы **НЕ удаляются** при обновлении контейнера (`docker compose up -d --build`)
- ✅ Данные сохраняются в volumes даже после `docker compose down`
- ❌ Удаляются только при `docker compose down -v` (флаг `-v` удаляет volumes)

---

## 🔧 Настройка и конфигурация

### Переменные окружения

Редактируйте [docker-compose.yml](docker-compose.yml) для изменения настроек:

```yaml
environment:
  # Память JVM
  JAVA_OPTS: -Xms512m -Xmx2048m

  # Database credentials
  SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/zoomos_v4
  SPRING_DATASOURCE_USERNAME: postgres
  SPRING_DATASOURCE_PASSWORD: root
```

### Изменение порта приложения

В `docker-compose.yml`:

```yaml
ports:
  - "8080:8081"  # Доступ через http://localhost:8080
```

### Настройка памяти

Для больших файлов увеличьте память:

```yaml
environment:
  JAVA_OPTS: -Xms1g -Xmx4g  # 1GB минимум, 4GB максимум
```

---

## 🔍 Мониторинг и диагностика

### Health Check

```bash
# Проверка состояния приложения
curl http://localhost:8081/actuator/health

# Проверка метрик
curl http://localhost:8081/actuator/metrics
```

### Просмотр логов

```bash
# Все логи в реальном времени
docker compose logs -f

# Последние 100 строк
docker compose logs --tail=100

# Логи с временными метками
docker compose logs -t

# Логи только за сегодня
docker compose logs --since $(date +%Y-%m-%d)
```

### Подключение к контейнерам

```bash
# Shell в контейнере приложения
docker exec -it zoomos_app sh

# PostgreSQL CLI
docker exec -it zoomos_postgres psql -U postgres -d zoomos_v4

# Просмотр процессов
docker top zoomos_app
```

### Использование ресурсов

```bash
# Статистика в реальном времени
docker stats

# Только для Zoomos контейнеров
docker stats zoomos_app zoomos_postgres
```

---

## 🚀 Production Deployment

### Рекомендации для продакшена

1. **Безопасность**
   ```yaml
   # Измените пароли БД!
   environment:
     POSTGRES_PASSWORD: your_strong_password_here
   ```

2. **SSL/HTTPS**
   - Используйте Nginx/Traefik как reverse proxy
   - Настройте SSL сертификаты
   - Пример: Nginx + Let's Encrypt

3. **Мониторинг**
   - Настройте Prometheus + Grafana
   - Используйте Spring Boot Actuator endpoints
   - Настройте alerting

4. **Backup**
   - Автоматизируйте резервное копирование БД (cron)
   - Регулярно архивируйте volumes
   - Храните бэкапы вне сервера

5. **Ресурсы**
   ```yaml
   # Ограничение ресурсов
   deploy:
     resources:
       limits:
         cpus: '2.0'
         memory: 4G
       reservations:
         cpus: '1.0'
         memory: 2G
   ```

### Автозапуск при загрузке системы

**Windows:**
```powershell
# Docker Desktop настроен на автозапуск по умолчанию
# Проверить: Docker Desktop → Settings → General → Start Docker Desktop when you log in ✅

# Контейнеры перезапустятся автоматически (restart: unless-stopped в docker-compose.yml)
```

**Linux:**
```bash
# Включить автозапуск Docker
sudo systemctl enable docker

# Контейнеры перезапустятся автоматически (restart: unless-stopped)
```

### Логирование в продакшене

```yaml
logging:
  driver: "json-file"
  options:
    max-size: "100m"
    max-file: "10"
```

---

## 🐛 Troubleshooting

### Проблема: Контейнер не запускается

**Windows PowerShell:**
```powershell
# Проверить логи
docker compose logs app

# Проверить состояние
docker compose ps

# Проверить health check (PowerShell)
docker inspect zoomos_app | Select-String -Pattern "Health" -Context 10
```

**Git Bash/Linux:**
```bash
docker inspect zoomos_app | grep -A 10 Health
```

### Проблема: БД не подключается

```powershell
# Проверить что PostgreSQL запущен
docker compose ps postgres

# Проверить логи БД
docker compose logs postgres

# Попробовать подключиться вручную
docker exec -it zoomos_postgres psql -U postgres -d zoomos_v4
```

### Проблема: Недостаточно памяти

```powershell
# Увеличить память в docker-compose.yml
# Открыть файл в блокноте
notepad docker-compose.yml

# Изменить:
environment:
  JAVA_OPTS: -Xms512m -Xmx4096m

# Перезапустить
docker compose restart app
```

### Проблема: Порт занят

**Windows:**
```powershell
# Найти процесс использующий порт 8081
netstat -ano | findstr :8081

# Убить процесс (замените PID на номер из предыдущей команды)
taskkill /PID <PID> /F

# Или изменить порт в docker-compose.yml
```

**Linux/Mac:**
```bash
lsof -i :8081
kill -9 <PID>
```

### Проблема: Docker Desktop не запускается

**Windows:**
```powershell
# 1. Перезапустить WSL
wsl --shutdown
wsl

# 2. Перезапустить Docker Desktop
# Диспетчер задач → Завершить Docker Desktop → Запустить снова

# 3. Переустановить WSL2 kernel
wsl --update --web-download
```

### Проблема: Playwright браузеры не работают

```bash
# Пересоздать контейнер
docker compose down
docker compose up -d --build --force-recreate app
```

---

## 📊 Структура проекта Docker

```
zoomos_v4/
├── Dockerfile                      # Multi-stage build конфигурация
├── docker-compose.yml              # Оркестрация контейнеров
├── .dockerignore                   # Исключения при сборке
├── src/main/resources/
│   └── application-docker.properties  # Docker профиль
└── README-DOCKER.md                # Эта документация
```

---

## 💡 Полезные команды

```bash
# Очистка неиспользуемых ресурсов Docker
docker system prune -a

# Просмотр размера образа
docker images zoomos_v4-app

# Инспекция сети
docker network inspect zoomos_network

# Экспорт образа
docker save zoomos_v4-app:latest | gzip > zoomos_v4_app.tar.gz

# Импорт образа
docker load < zoomos_v4_app.tar.gz
```

---

## 📞 Поддержка

При возникновении проблем:
1. Проверьте логи: `docker compose logs -f`
2. Проверьте health check: `curl http://localhost:8081/actuator/health`
3. Изучите документацию Docker: https://docs.docker.com/

---

## 🔄 Обновление приложения

### Стандартная процедура обновления

```bash
# 1. Остановить текущую версию
docker compose down

# 2. Получить последний код из main (если нужно)
git pull origin main

# 3. Пересобрать образ и запустить
docker compose up -d --build

# 4. Проверить статус и логи
docker compose ps
docker compose logs -f app
```

### Быстрое обновление без остановки БД

```bash
# Пересобрать только приложение (БД продолжает работать)
docker compose build --no-cache app
docker compose up -d --no-deps app
```

### Полное обновление с очисткой кэша

```bash
# Если нужна полная пересборка всех слоев
docker compose down
docker compose build --no-cache
docker compose up -d
```

### Процедура на будущее

**При изменении кода:**
1. Коммит изменений в git
2. `docker compose up -d --build` (пересборка + запуск)

**При pull из репозитория:**
1. `git pull origin main`
2. `docker compose down`
3. `docker compose up -d --build`

**Важно:** Данные в volumes (БД, файлы) сохраняются при обновлении!

---

## ✅ Готово!

Теперь ваше приложение Zoomos v4 работает в Docker и:
- ✅ Автоматически запускается при перезагрузке сервера
- ✅ Использует PostgreSQL в отдельном контейнере
- ✅ Сохраняет данные в volumes
- ✅ Логирует работу для диагностики
- ✅ Поддерживает health checks
- ✅ Готово к масштабированию

**Приложение доступно:** http://localhost:8081
