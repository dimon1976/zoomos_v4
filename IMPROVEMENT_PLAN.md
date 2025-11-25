# План реализации 12 улучшений системы

Дата создания: 2025-10-23

## 1. Фильтр по статусам - показывать всегда ✅ УТОЧНЕНО
**Файл**: `statistics/results.html`
- Панель `#fieldFilterPanel` (lines 430-463) показывать всегда
- Селекты делать активными сразу при загрузке страницы
- JavaScript: Вызывать `loadFilterFields()` сразу при загрузке, не дожидаясь выбора операций

## 2. Исправить хлебные крошки (breadcrumbs) ✅ УТОЧНЕНО
**Проблема**: Неправильные ссылки в навигации
- "Главная-Статистика-client-1" → должно быть "Главная → Клиент 'Название' → Статистика"
- "Главная-Экспорт-client-1" → должно быть "Главная → Клиент 'Название' → Экспорт"
- "Главная-Экспорт-Статус-424" → должно быть "Главная → Клиент 'Название' → Экспорт → Статус #424"

**Файлы для модификации**:
- `ExportStatisticsController.java` - добавить в модель breadcrumbs с правильными ссылками
- `ExportController.java` - аналогично добавить breadcrumbs
- `statistics/results.html`, `statistics/setup.html` - использовать breadcrumbs из модели
- `export/start.html`, `export/status.html` - аналогично

**Структура breadcrumb**:
```java
List<Breadcrumb> breadcrumbs = List.of(
    new Breadcrumb("Главная", "/"),
    new Breadcrumb(client.getName(), "/clients/" + clientId),
    new Breadcrumb("Статистика", null) // null для текущей страницы
);
model.addAttribute("breadcrumbs", breadcrumbs);
```

## 3. Доделать экспорт в Excel со страницы статистики
**Файлы**:
- `ExportStatisticsController.java` - endpoint `/statistics/export/excel` (POST)
- Создать метод `exportStatisticsToExcel()` для генерации файла
- Использовать `XlsxFileGenerator` + создать специальный метод для статистики
- Структура: группы (zdravcity, stolichki...) × метрики × операции + отклонения

## 4. Исправить ссылку скачивания в WebSocket уведомлении
**Файл**: `NotificationServiceImpl.java` - метод `sendExportCompletedNotification`
- Текущая проблема: ссылка в уведомлении не работает
- Правильный URL: `/export/download/{sessionId}`
- Проверить формат JSON в NotificationDto

## 5. JavaScript фильтрация метрик по всем группам ✅ УТОЧНЕНО
**Файл**: `statistics/results.html`

**Требование**: Скрывать строки с одинаковой метрикой во ВСЕХ группах одной кнопкой
- Группы: `zdravcity.ru`, `stolichki.ru`, `ozerki.ru`
- Метрики в каждой группе: `competitorPrice`, `competitorPromotionalPrice`, etc.

**Реализация**:
- Добавить панель управления метриками (перед таблицей, after line 427)
- Чекбоксы для каждого типа метрики: `competitorPrice`, `competitorPromotionalPrice`, `productPrice` и т.д.
- При снятии галочки - скрывать ВСЕ строки с этой метрикой во ВСЕХ группах
- JavaScript: добавить data-атрибут `data-metric-type` к `<tr>` для идентификации
- Сохранять состояние в localStorage
- Пример: Снял галочку "competitorPromotionalPrice" → скрылись все строки с этой метрикой в zdravcity, stolichki, ozerki

## 6. Исправить статус "инициализация" на странице импорта
**Задача**: Найти и исправить отображение статуса `/import/status/{id}`
- Найти HTML для страницы статуса импорта (аналог `export/status.html`)
- Проверить маппинг enum статусов в Thymeleaf
- Исправить условие отображения статуса "INITIALIZING"

## 7. Пояснение для "Размер порции" в БД ✅ УТОЧНЕНО
**Файл**: `maintenance/database.html` (lines 189-195)

**Вопрос**: Используется ли значение batchSize?
**Ответ по коду**: ДА, используется! Значение из UI передаётся в `DataCleanupRequestDto.batchSize` и используется в `DataCleanupService`

**Задача**: Улучшить описание
- Заменить "Рекомендуется 10000" на детальное объяснение
- Добавить tooltip с иконкой: "Количество записей, удаляемых за одну транзакцию. Меньше значение = безопаснее (можно откатить), но медленнее. Больше значение = быстрее, но при ошибке потеряется больше прогресса."

## 8. Настройки анализа статистики ✅ УТОЧНЕНО
**Вопрос**: Нужны ли настройки warningPercentage/criticalPercentage в UI?
**Проверка**: Найти страницу `/statistics/settings` и определить, используются ли эти параметры

**Варианты**:
- Если используются → оставить с пояснениями
- Если НЕ используются → убрать из UI, оставить только в application.properties

## 9. Процент от общего при фильтрации по статусу ✅ УТОЧНЕНО
**Файлы**:
- `ExportStatisticsService.java` - расчёт процентов
- `StatisticsComparisonDto.java` - добавить поле `percentageOfTotal`
- `statistics/results.html` - отображение

**Формула**: `(filtered_count / total_count_in_group) * 100`
**Пример отображения**: "В наличии: 1250 записей (65% от 1923)"
- Рассчитывать для COUNT метрик
- Сравнивать отфильтрованное количество с общим количеством ПО ГРУППЕ (не по всем данным!)

## 10. Фильтрация экспортов по выбранному шаблону ✅ УТОЧНЕНО
**Файл**: `ExportStatisticsController.java` (method `showStatisticsSetup`)

**Требование**: При выборе конкретного шаблона показывать только экспорты, сделанные с этим шаблоном

**Реализация**:
- Добавить параметр `?templateId=X` в запрос
- В контроллере: если templateId указан, фильтровать `recentExports` только по этому шаблону
- В `statistics/setup.html`: добавить select для выбора шаблона → при выборе перезагружать список операций
- SQL: `WHERE template_id = :templateId` в запросе к sessionRepository

## 11. Исключить автопреобразование дат для STRING полей
**Файл**: `ImportProcessorService.java` (lines 712-714)

**Проблема**: "20.10.2025" преобразуется в "Mon Oct 20 06:32:00 MSK 2025"
**Причина**: `DateUtil.isCellDateFormatted(cell)` конвертирует в Date даже для STRING полей

**Решение**:
- Добавить параметр в метод: `getCellValueAsString(Cell cell, FieldMappingDetail mapping)`
- Проверять тип целевого поля: `if (mapping.getTargetField().contains("Additional"))`
- Для STRING полей (competitorAdditional4, productAdditional*) - НЕ применять DateUtil
- Всегда возвращать raw string значение без форматирования

## 12. Пустые ячейки для отсутствующей цены
**Файл**: `XlsxFileGenerator.java` (method `setCellValue`, lines 305-336)

**Требование**: Если цена = null или 0, оставлять ячейку пустой

**Реализация**:
```java
if (value instanceof Number) {
    double numValue = ((Number) value).doubleValue();
    // Для полей цены: если 0 или null, оставляем пустую ячейку
    if (numValue == 0.0) {
        cell.setBlank();
    } else {
        cell.setCellValue(numValue);
        cell.setCellStyle(styles.getNumberStyle());
    }
}
```

---

## Приоритет выполнения:
1. **CRITICAL** (#11, #12): Данные - даты и цены
2. **HIGH** (#2, #4, #6): Навигация и уведомления
3. **MEDIUM** (#5, #9, #10): UX статистики
4. **LOW** (#1, #3, #7, #8): Улучшения UI

## Создать DTO класс:
```java
@Data
@Builder
public class Breadcrumb {
    private String label;
    private String url; // null для активной страницы
}
```
