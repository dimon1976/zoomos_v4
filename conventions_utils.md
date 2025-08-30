# Соглашения по разработке утилит

> Основано на техническом видении из [@vision_utils.md](vision_utils.md)

## Принципы кода

**KISS - Keep It Simple, Stupid**
- Одна утилита = один контроллер + один сервис
- Копируй код между утилитами вместо создания абстракций
- Никаких сложных паттернов проектирования
- Прямолинейная логика без лишних слоев

**Переиспользование**
- Используй существующие сервисы: `FileAnalyzerService`, `ControllerUtils`, `ImportTemplateField`
- Переиспользуй существующие настройки из `application.properties`
- Используй существующую валидацию и обработку ошибок

## Структура кода

**Контроллеры** (`src/main/java/com/java/controller/utils/`)
```java
@Controller
@RequestMapping("/utils/utility-name")
@RequiredArgsConstructor
@Slf4j
public class UtilityNameController {
    private final FileAnalyzerService fileAnalyzerService;
    private final ControllerUtils controllerUtils;
    private final UtilityNameService utilityNameService;
}
```

**Сервисы** (`src/main/java/com/java/service/utils/`)
```java
@Service
@RequiredArgsConstructor
@Slf4j
public class UtilityNameService {
    // Только критичные ошибки: log.error()
    // НЕ логируй обычные операции
}
```

**DTO** (`src/main/java/com/java/dto/utils/`)
- Отдельный DTO для каждой утилиты
- Используй validation аннотации из существующих DTO
- Наследуй структуру от существующих моделей где возможно

## Шаблоны HTML

**Расположение**: `src/main/resources/templates/utils/`

**Принципы**:
- Простые HTML формы
- Bootstrap для стилизации
- Минимум JavaScript (только для спиннера)
- Понятные сообщения об ошибках

## Обработка файлов

**Всегда используй существующие сервисы:**
- `FileAnalyzerService` для анализа и валидации
- `PathResolver` для работы с путями
- Систему `ImportTemplateField` для выбора колонок

**Паттерн обработки:**
1. Загрузка → `FileAnalyzerService.analyzeFile()`
2. Выбор полей → переиспользование логики `ImportTemplateField`
3. Обработка → бизнес-логика утилиты
4. Результат → прямая отдача файла

## Сессии и состояние

- Сохраняй настройки утилиты в HTTP сессии
- Используй stateless обработку
- Очищай временные файлы после обработки
- Без сохранения в БД

## Ошибки и логирование

**Логируй только:**
```java
log.error("Failed to process utility for file: {}", fileName, ex);
```

**НЕ логируй:**
- Обычные операции
- Пользовательские действия
- Промежуточные результаты

## Async обработка

- Используй существующие thread pool настройки
- Переиспользуй async конфигурацию из проекта
- При превышении лимита → сообщение "попробуйте позже"

## URL структура

```
/utils                     # Главная страница со списком
/utils/barcode-match       # Сопоставление штрихкодов
/utils/url-cleaner         # Очистка URL
/utils/link-extractor      # Сбор ссылок с ID
/utils/redirect-collector  # Сбор финальных URL
```

## Workflow

Каждая утилита следует одному паттерну:
1. Загрузка файла
2. Анализ + выбор параметров
3. Обработка + спиннер
4. Скачивание результата
5. "Только заново" (без возврата назад)

## Не делай

- ❌ Сложные абстракции и базовые классы
- ❌ Новые конфигурационные параметры без необходимости
- ❌ Сохранение в БД
- ❌ Детальное логирование операций
- ❌ Прогресс-бары (только спиннер)
- ❌ Превью результатов

## Делай

- ✅ Копируй код между утилитами
- ✅ Переиспользуй существующие сервисы
- ✅ Простые формы и понятные сообщения
- ✅ Stateless обработку
- ✅ Прямую отдачу результатов
- ✅ Graceful обработку ошибок