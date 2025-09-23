# Агенты Zoomos v4

Экосистема из 11 специализированных агентов для разработки и поддержки Spring Boot приложения Zoomos v4.

## 🚀 Быстрый старт

### Основные агенты для ежедневной работы

```bash
# Анализ и координация задач
@agent-orchestrator "описание проблемы или задачи"

# Оптимизация производительности
@performance-optimizer "медленная обработка файлов"

# Исправление ошибок
@error-analyzer "async операции зависают"

# Работа с шаблонами
@template-wizard "создать шаблон для нового клиента"
```

## 📋 Полный список агентов

### Высокоприоритетные (Production-critical)
- **@performance-optimizer** - оптимизация thread pools, memory, SQL запросов
- **@database-maintenance-specialist** - PostgreSQL и Flyway миграции
- **@error-analyzer** - анализ ошибок и exception handling
- **@file-processing-expert** - CSV/Excel обработка и character encoding

### Функциональные (Feature-focused)
- **@template-wizard** - автоматизация шаблонов import/export
- **@websocket-enhancer** - real-time уведомления и WebSocket
- **@async-architecture-specialist** - асинхронная архитектура
- **@security-auditor** - security audit и защита от уязвимостей

### UX и мониторинг
- **@ui-modernizer** - frontend и responsive design
- **@monitoring-dashboard-builder** - системный мониторинг

### Мета-агент (Координация)
- **@agent-orchestrator** - управление всей экосистемой агентов

## 🎯 Примеры использования

### Типичные сценарии

**Новый клиент:**
```bash
@agent-orchestrator "крупный интернет-магазин хочет интегрироваться с особыми требованиями к файлам"
```

**Проблемы с производительностью:**
```bash
@performance-optimizer "система тормозит при обработке Excel файлов размером 500MB"
```

**Критические ошибки:**
```bash
@agent-orchestrator emergency "пользователи жалуются на потерю данных в async операциях"
```

**Плановые задачи:**
```bash
@database-maintenance-specialist "создать индексы для улучшения запросов ClientService"
@template-wizard "автоматически создать шаблон на основе структуры загруженного файла"
```

## 💡 Принципы работы

1. **Один агент = одна экспертная область** - каждый агент специализируется на конкретной области
2. **Координация через оркестратор** - для сложных задач используйте @agent-orchestrator
3. **Русскоязычная коммуникация** - все агенты работают на русском языке
4. **KISS + MVP подход** - простота и итеративная разработка

## 📚 Документация

- **CLAUDE.md** - основная документация проекта Zoomos v4
- **agents.md** - детальное описание всех 11 агентов с примерами
- Каждый файл агента содержит подробную специализацию и примеры

## 🔧 Архитектура проекта

Агенты созданы специально для Spring Boot 3.2.12 приложения с:
- Java 17 + PostgreSQL
- Асинхронная обработка файлов (CSV/Excel)
- WebSocket real-time уведомления
- Система шаблонов import/export
- Maintenance и мониторинг

---

*Создано для проекта Zoomos v4 - система обработки файлов с client-based функциональностью*