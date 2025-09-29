# Руководство по информационным блокам Zoomos v4

## Обзор

В Zoomos v4 реализована унифицированная система информационных блоков с использованием CSS классов `info-section`. Эта система обеспечивает визуальную согласованность и упрощает разработку новых шаблонов.

## Архитектура

### CSS Framework

Основной CSS файл: `src/main/resources/static/css/info-sections.css`

**Базовая структура:**
```css
.info-section {
  /* Базовые стили для всех блоков */
}

.info-section__title {
  /* Стили для заголовков */
}

.info-section__body {
  /* Стили для содержимого */
}

.info-section__icon {
  /* Стили для иконок */
}
```

### Типы блоков

1. **Description** (`.info-section--description`) - Синяя тема
2. **Requirements** (`.info-section--requirements`) - Оранжевая тема
3. **Instructions** (`.info-section--instructions`) - Зелёная тема
4. **Examples** (`.info-section--examples`) - Фиолетовая тема
5. **Warnings** (`.info-section--warnings`) - Красная тема

## Использование в шаблонах

### Базовая структура блока

```html
<section class="info-section info-section--description">
    <h3 class="info-section__title">
        <i class="info-section__icon fas fa-info-circle"></i>
        <span>Заголовок блока</span>
    </h3>
    <div class="info-section__body">
        <!-- Содержимое блока -->
    </div>
</section>
```

### Примеры использования

#### 1. Блок описания
```html
<section class="info-section info-section--description">
    <h3 class="info-section__title">
        <i class="info-section__icon fas fa-info-circle"></i>
        <span>Описание утилиты</span>
    </h3>
    <div class="info-section__body">
        <p>Утилита выполняет обработку файлов с автоматическим определением формата.</p>
        <div class="row">
            <div class="col-md-4">
                <i class="fas fa-file fa-2x text-primary"></i>
                <small>Поддержка CSV/Excel</small>
            </div>
        </div>
    </div>
</section>
```

#### 2. Блок требований
```html
<section class="info-section info-section--requirements">
    <h3 class="info-section__title">
        <i class="info-section__icon fas fa-file-alt"></i>
        <span>Требования к файлу</span>
    </h3>
    <div class="info-section__body">
        <ul class="list-with-icons">
            <li>Файл должен содержать заголовки</li>
            <li>Максимальный размер: 600MB</li>
            <li>Поддерживаемые форматы: CSV, Excel</li>
        </ul>
    </div>
</section>
```

#### 3. Блок инструкций
```html
<section class="info-section info-section--instructions">
    <h3 class="info-section__title">
        <i class="info-section__icon fas fa-list-ol"></i>
        <span>Как это работает</span>
    </h3>
    <div class="info-section__body">
        <div class="info-section__process-steps">
            <div class="info-section__process-step">
                <div class="info-section__process-icon">
                    <i class="fas fa-upload"></i>
                </div>
                <div class="info-section__process-title">Загрузка</div>
                <div class="info-section__process-description">Выберите файл для обработки</div>
            </div>
        </div>
    </div>
</section>
```

#### 4. Блок примеров
```html
<section class="info-section info-section--examples">
    <h3 class="info-section__title">
        <i class="info-section__icon fas fa-lightbulb"></i>
        <span>Пример обработки</span>
    </h3>
    <div class="info-section__body">
        <div class="row">
            <div class="col-md-6">
                <strong>Входные данные:</strong>
                <div class="code-example">
                    <div>ID: 12345</div>
                    <div>URL: https://example.com</div>
                </div>
            </div>
            <div class="col-md-6">
                <strong>Результат:</strong>
                <div class="code-example">
                    <div>ID: 12345</div>
                    <div>Обработанный URL: https://example.com/clean</div>
                </div>
            </div>
        </div>
    </div>
</section>
```

#### 5. Блок предупреждений
```html
<section class="info-section info-section--warnings">
    <h3 class="info-section__title">
        <i class="info-section__icon fas fa-exclamation-triangle"></i>
        <span>Важные замечания</span>
    </h3>
    <div class="info-section__body">
        <ul class="list-with-icons">
            <li class="list-item--warning">Обработка может занять значительное время</li>
            <li class="list-item--info">Система автоматически сохраняет прогресс</li>
        </ul>
    </div>
</section>
```

## Дополнительные CSS классы

### Модификаторы блоков
- `.info-section--compact` - Компактная версия блока
- `.info-section--bordered` - Блок с границей

### Утилитарные классы
- `.list-with-icons` - Список с иконками
- `.list-item--warning` - Элемент списка с предупреждением
- `.list-item--info` - Элемент списка с информацией
- `.code-example` - Блок с примером кода
- `.strategy-badge` - Бейдж для стратегий

## Рекомендации для разработчиков

### ✅ Что делать

1. **Используйте унифицированные блоки** вместо создания собственных
2. **Следуйте БЭМ-подобной методологии** в CSS классах
3. **Применяйте соответствующие иконки** из Font Awesome
4. **Тестируйте адаптивность** на разных размерах экрана
5. **Используйте семантические HTML теги** (`<section>`, `<h3>`)

### ❌ Чего избегать

1. **Не создавайте сложные Thymeleaf фрагменты** с множественными параметрами
2. **Не используйте inline стили** - всё через CSS классы
3. **Избегайте глубокой вложенности** HTML элементов
4. **Не смешивайте разные стили** в одном блоке
5. **Не используйте #maps.toMap/#lists.toList** с 6+ параметрами

### Миграция старых шаблонов

При обновлении существующих шаблонов:

1. **Определите тип блока** (описание, требования, инструкции, примеры, предупреждения)
2. **Замените пользовательские div/card** на `info-section`
3. **Примените соответствующий CSS класс** блока
4. **Перенесите содержимое** в `info-section__body`
5. **Добавьте заголовок** с иконкой в `info-section__title`

### Пример миграции

**До (старый подход):**
```html
<div class="card custom-info-block">
    <div class="card-header">
        <h5>Требования</h5>
    </div>
    <div class="card-body">
        <ul>
            <li>Файл должен быть в формате CSV</li>
        </ul>
    </div>
</div>
```

**После (новый подход):**
```html
<section class="info-section info-section--requirements">
    <h3 class="info-section__title">
        <i class="info-section__icon fas fa-file-alt"></i>
        <span>Требования</span>
    </h3>
    <div class="info-section__body">
        <ul class="list-with-icons">
            <li>Файл должен быть в формате CSV</li>
        </ul>
    </div>
</section>
```

## Интеграция с Bootstrap

Система полностью совместима с Bootstrap 5.3.0:

- **Grid System**: Используйте `.row` и `.col-*` внутри блоков
- **Utilities**: Применяйте Bootstrap утилиты (`.text-*`, `.mb-*`, `.d-*`)
- **Components**: Комбинируйте с Bootstrap компонентами (badges, buttons)

## Тестирование

При создании новых шаблонов обязательно тестируйте:

1. **Визуальное отображение** всех типов блоков
2. **Адаптивность** на мобильных устройствах
3. **Доступность** (ARIA attributes, keyboard navigation)
4. **Темную тему** через CSS media queries
5. **Совместимость** с существующими компонентами

## Поддержка и развитие

### Добавление нового типа блока

1. Создайте CSS класс в `info-sections.css`:
```css
.info-section--new-type {
  --info-section-color: #your-color;
  --info-section-bg: #your-bg-color;
}
```

2. Добавьте документацию в этот файл
3. Создайте пример использования
4. Протестируйте во всех браузерах

### Отладка проблем

- **Проверьте консоль браузера** на ошибки CSS
- **Используйте DevTools** для анализа стилей
- **Тестируйте без JavaScript** для проверки базовой функциональности
- **Валидируйте HTML** с помощью W3C Validator

## Примеры использования

Рабочие примеры можно найти в:
- `src/main/resources/templates/utils/url-cleaner.html`
- `src/main/resources/templates/utils/link-extractor.html`
- `src/main/resources/templates/utils/redirect-finder.html`
- `src/main/resources/templates/utils/stats-processor.html`
- `src/main/resources/templates/utils/data-merger.html`
- `src/main/resources/templates/clients/import.html`

---

**Версия:** 1.0
**Дата:** 2025-09-29
**Автор:** Экосистема агентов Zoomos v4