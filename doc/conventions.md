# Code Conventions для Zoomos v4

> **Для code-ассистентов:** Эти правила обязательны при генерации кода для проекта Zoomos v4.
>
> **Базовый документ:** [vision.md](vision.md) - полное техническое видение проекта

---

## 🎯 Главные принципы

### KISS - Keep It Simple, Stupid
- **Один класс вместо иерархии** - если можно решить в одном классе, решаем в одном
- **Прямые решения** - никаких абстракций "про запас"
- **Минимум паттернов** - используем паттерны только когда это реально упрощает код

### MVP - Minimum Viable Product
- **Только то, что нужно СЕЙЧАС** - не пишем код "на будущее"
- **Базовая функциональность сначала** - улучшения потом
- **Работает > Идеально** - рабочий простой код лучше идеальной архитектуры

### Fail Fast
- **Валидация в начале метода** - проверяем входные данные первыми строками
- **Бросаем исключения сразу** - не пытаемся "угадать" намерения
- **Четкие сообщения об ошибках** - понятные пользователю

---

## 📝 Стандарты кода

### Lombok обязателен
```java
@Data                        // для DTO и POJO
@Builder                     // для объектов с >3 полями
@Slf4j                       // для логирования
@RequiredArgsConstructor     // для DI
```

### Именование
```java
ClassName                    // PascalCase для классов
methodName()                 // camelCase для методов
CONSTANT_NAME                // UPPER_SNAKE_CASE для констант
```

### Структура класса
```java
@Service
@RequiredArgsConstructor
@Slf4j
public class ExampleService {

    private final Repository repository;  // DI через constructor

    public ReturnType mainMethod() {
        // 1. Валидация входных данных
        if (input == null) {
            throw new IllegalArgumentException("Input cannot be null");
        }

        // 2. Основная логика
        // 3. Возврат результата
    }

    private void helperMethod() {
        // Приватные методы в конце
    }
}
```

---

## 🏗️ Архитектура

### Слои приложения
```
Controller → Service → Repository → Entity
```

**Правила:**
- Controller: только маршрутизация + валидация
- Service: вся бизнес-логика
- Repository: только Spring Data JPA, никакой логики
- **НИКОГДА не вызываем Repository из Controller напрямую**

### Пакеты
```
com/java/
├── controller/      # Endpoints
├── service/         # Логика
├── repository/      # Data access
├── dto/             # Data transfer
├── model/entity/    # JPA entities
└── exception/       # Exceptions
```

---

## 💾 База данных

### Миграции Flyway
```sql
-- V15__add_new_feature.sql
-- НИКОГДА не меняем выполненные миграции!
-- Откат только через новую миграцию
```

### Hibernate
```properties
spring.jpa.hibernate.ddl-auto=none  # Только миграции!
```

### Индексы
```sql
-- Добавляем в миграции сразу:
-- 1. Для foreign keys
-- 2. Для часто используемых WHERE/ORDER BY
-- НЕ индексируем "на всякий случай"
```

---

## 🚫 Что НЕ делаем

```java
// ❌ НЕ делаем сложные иерархии
public abstract class AbstractBaseProcessor extends GenericHandler<T> { }

// ✅ Делаем простой класс
public class DataProcessor { }

// ❌ НЕ создаем интерфейсы с одной реализацией
public interface UserService { }
public class UserServiceImpl implements UserService { }

// ✅ Просто сервис
public class UserService { }

// ❌ НЕ делаем универсальные решения
public class UniversalDataConverter<T, R, S> { }

// ✅ Конкретное решение конкретной задачи
public class CsvToExcelConverter { }

// ❌ НЕ кэшируем без измерений
@Cacheable("users")  // Зачем? Есть проблемы с производительностью?

// ✅ Кэшируем только когда точно знаем что медленно
```

---

## ✅ Примеры правильного кода

### Сервис
```java
@Service
@RequiredArgsConstructor
@Slf4j
public class ExportService {

    private final ExportRepository repository;
    private final FileGenerator fileGenerator;

    public ExportResult exportData(ExportRequest request) {
        // Валидация
        validateRequest(request);

        // Логика
        List<Data> data = repository.findByFilters(request.getFilters());
        File file = fileGenerator.generate(data, request.getFormat());

        log.info("Exported {} records to {}", data.size(), file.getName());

        return ExportResult.builder()
            .file(file)
            .recordCount(data.size())
            .build();
    }

    private void validateRequest(ExportRequest request) {
        if (request == null || request.getFilters() == null) {
            throw new IllegalArgumentException("Request and filters are required");
        }
    }
}
```

### DTO
```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExportRequest {
    private Long templateId;
    private List<String> filters;
    private String format;
}
```

### Controller
```java
@Controller
@RequiredArgsConstructor
@Slf4j
public class ExportController {

    private final ExportService exportService;

    @PostMapping("/export")
    public ResponseEntity<Resource> export(@RequestBody ExportRequest request) {
        log.info("Export request: {}", request);

        ExportResult result = exportService.exportData(request);

        return ResponseEntity.ok()
            .header("Content-Disposition", "attachment; filename=" + result.getFile().getName())
            .body(new FileSystemResource(result.getFile()));
    }
}
```

### Repository
```java
public interface ExportRepository extends JpaRepository<ExportData, Long> {

    // Простые query methods
    List<ExportData> findByClientId(Long clientId);

    // Сложные запросы через @Query
    @Query("SELECT e FROM ExportData e WHERE e.status = :status AND e.createdAt > :date")
    List<ExportData> findByStatusAfterDate(@Param("status") String status,
                                           @Param("date") LocalDateTime date);
}
```

---

## 🔍 Логирование

```java
log.debug("Detailed info: {}", detailedData);        // Для разработки
log.info("Operation started: {}", operationId);      // Важные события
log.warn("Unexpected state: {}", state);             // Что-то не так
log.error("Failed to process: {}", error, exception); // Ошибки
```

**Правило:** Всегда логируем с контекстом (ID операции, параметры)

---

## 🧪 Тестирование

### Минималистичный подход
- Тесты на **реальных данных** при разработке
- Unit-тесты **только для сложной логики**
- Не пишем тесты "для галочки"

```java
// ✅ Тест с реальными данными
@Test
public void testRealExportScenario() {
    // Given: реальный файл из production
    File testFile = new File("test-data/real-export-sample.csv");

    // When
    ExportResult result = service.exportData(testFile);

    // Then
    assertThat(result.getRecordCount()).isEqualTo(150);
    assertThat(result.getErrors()).isEmpty();
}
```

---

## 🔄 Git

### Коммиты
```
feat: добавлена фильтрация статистики
fix: исправлена ошибка в подсчете метрик
refactor: упрощен ExportService
docs: обновлен README
```

**Правило:** Частые маленькие коммиты > один большой

---

## 📋 Чеклист перед коммитом

- [ ] Код максимально простой (KISS)?
- [ ] Решает только текущую задачу (MVP)?
- [ ] Валидация в начале методов (Fail Fast)?
- [ ] Используется Lombok где возможно?
- [ ] Нет абстракций "про запас"?
- [ ] Нет универсальных решений без необходимости?
- [ ] Логирование с контекстом?
- [ ] Понятные имена классов и методов?

---

## 🎓 Правило трех (Rule of Three)

**Рефакторинг делаем когда:**
- Код повторяется **3 раза** → выносим в метод
- Три похожих класса → создаем базовый класс
- **НЕ раньше!**

```java
// ❌ Первое повторение - оставляем как есть
service.calculatePrice(item1);

// ❌ Второе повторение - оставляем как есть
service.calculatePrice(item2);

// ✅ Третье повторение - выносим в метод
private void processItems(List<Item> items) {
    items.forEach(item -> service.calculatePrice(item));
}
```

---

## 📚 Дополнительная информация

Полное описание архитектуры, технологий и принципов см. в **[vision.md](vision.md)**

---

**Помни:** Этот pet-проект для проверки идей. Простой работающий код важнее идеальной архитектуры.
