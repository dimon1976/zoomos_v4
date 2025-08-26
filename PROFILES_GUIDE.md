# Руководство по профилям конфигурации ZOOMOS v4

## Доступные профили

### 🚀 **dev** (по умолчанию)
**Назначение**: Разработка и локальное тестирование

**Особенности:**
- Умеренное логирование с отладкой ключевых компонентов
- DevTools включены (горячая перезагрузка)
- Отключено кэширование Thymeleaf
- Уменьшенные batch-размеры для быстрого тестирования
- DEBUG для TaskReportExportStrategy

**Активация:**
```properties
spring.profiles.active=dev
```

---

### 🔇 **silent**
**Назначение**: Тихий режим для тестирования без шума в логах

**Особенности:**
- Минимальное логирование (только ошибки и важные сообщения)
- DevTools включены
- Те же параметры производительности что в dev
- Отключена отладка стратегий

**Активация:**
```properties
spring.profiles.active=silent
```

---

### 🔍 **verbose**
**Назначение**: Максимальная отладка для выявления проблем

**Особенности:**
- Полное SQL логирование с параметрами
- TRACE уровень для всех сервисов приложения
- Максимальная детализация стратегий экспорта
- Маленькие batch-размеры для пошаговой отладки
- Один поток для последовательной обработки

**Активация:**
```properties
spring.profiles.active=verbose
```

---

### 🏭 **prod**
**Назначение**: Продакшен окружение

**Особенности:**
- Минимальное логирование для производительности
- Логи в файл с ротацией
- DevTools отключены
- Включено кэширование Thymeleaf
- Максимальные batch-размеры и потоки
- Строгие настройки Flyway
- Мониторинг через Actuator

**Активация:**
```properties
spring.profiles.active=prod
```

## Сравнение профилей

| Параметр | dev | silent | verbose | prod |
|----------|-----|--------|---------|------|
| **Логирование** | INFO/DEBUG | WARN/ERROR | DEBUG/TRACE | INFO/WARN |
| **SQL логи** | Отключены | Отключены | Включены | Отключены |
| **DevTools** | ✅ | ✅ | ✅ | ❌ |
| **Thymeleaf кэш** | ❌ | ❌ | ❌ | ✅ |
| **Import batch** | 100 | 100 | 50 | 1000 |
| **Export batch** | 250 | 250 | 100 | 2000 |
| **Max потоки** | 1-2 | 1-2 | 1 | 4-8 |
| **Логи в файл** | ❌ | ❌ | ❌ | ✅ |

## Переключение профилей

### 1. В application.properties
```properties
spring.profiles.active=prod
```

### 2. Через переменную окружения
```bash
export SPRING_PROFILES_ACTIVE=prod
java -jar app.jar
```

### 3. Через аргумент командной строки
```bash
java -jar app.jar --spring.profiles.active=prod
```

### 4. В IDE (IntelliJ IDEA)
```
Run Configuration → Environment Variables → SPRING_PROFILES_ACTIVE=verbose
```

## Рекомендации по использованию

### 📋 Разработка новых функций
```properties
spring.profiles.active=dev
```

### 🐛 Отладка сложных проблем
```properties
spring.profiles.active=verbose
```

### 🧪 Автоматические тесты
```properties
spring.profiles.active=silent
```

### 🚀 Продакшен деплой
```properties
spring.profiles.active=prod
```

## Отладка TaskReportExportStrategy

Для решения проблемы с фильтрацией записей в TaskReportExportStrategy используйте:

### Первичная диагностика
```properties
spring.profiles.active=dev
```

### Глубокая отладка
```properties  
spring.profiles.active=verbose
```

Профиль **verbose** покажет:
- Детальные SQL запросы с параметрами
- Пошаговую обработку каждой записи
- Процесс построения и сравнения ключей
- Причины пропуска записей

## Файловая структура

```
src/main/resources/
├── application.properties          # Базовые настройки
├── application-dev.properties      # Профиль разработки  
├── application-silent.properties   # Тихий режим
├── application-verbose.properties  # Максимальная отладка
└── application-prod.properties     # Продакшен
```

---
*Обновлено: 26.08.2025*