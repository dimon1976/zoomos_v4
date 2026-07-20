# Полная синхронизация списка сайтов из страницы матчинга

## Суть в одном предложении

Функция загружает список сайтов со страницы матчинга, сравнивает с существующими, определяет какие добавить/удалить, и применяет эти изменения в БД вместе с их city_ids.

---

## Аналогия (Склад по управлению товарами)

Представьте себе склад магазина конкурентов:

```
Менеджер склада (preview) → смотрит на полку с товарами в системе
                           ↓
                        сравнивает с тем, что нужно отследить
                           ↓
                        составляет список (добавить, удалить, оставить)
                           ↓
Менеджер склада (apply) → берёт этот список и его реализует
                        (добавляет новые товары, убирает старые)
```

Функция работает по тому же принципу: **preview** говорит "вот что надо изменить", а **apply** это меняет.

---

## ASCII-диаграмма

```
┌──────────────────────────────────────────────────────────────┐
│                    Страница матчинга                          │
│   (Select: site option с названиями конкурентов)            │
└────────────────────────┬─────────────────────────────────────┘
                         │
                         │ page.evaluate() извлечь список сайтов
                         ↓
            ┌────────────────────────────┐
            │ previewSyncFromMatching()  │
            │ (без сохранения в БД)      │
            └────────────┬───────────────┘
                         │
         ┌───────────────┼───────────────┐
         ↓               ↓               ↓
    [toAdd]         [existing]      [toDelete]
    новые            уже есть       надо удалить
    сайты            (не трогаем)   
         │               │               │
         └───────────────┼───────────────┘
                         │
                         │ результат: Map {toAdd, toDelete, existing}
                         ↓
            ┌────────────────────────────┐
            │ applySyncFromMatching()    │
            │ (ДА, сохраняем в БД)       │
            └────────────┬───────────────┘
                         │
         ┌───────────────┼───────────────┐
         ↓               ↓               ↓
    cityIdRepository   (пропуск)   cityIdRepository
    .save(new)                      .delete(old)
         │                               │
         └───────────────┬───────────────┘
                         │
                         ↓
            shop.setLastSyncedAt()
            shopRepository.save()
```

---

## Пошагово

### Шаг 1. Preview: парсинг страницы матчинга
**Файл:** `ZoomosParserService.java:360-422`

```java
public Map<String, Object> previewSyncFromMatching(String shopName) {
    // 360: находим магазин по имени
    ZoomosShop shop = shopRepository.findByShopName(shopName.trim().toLowerCase());
    
    // 366-380: создаём Playwright браузер, логинимся, открываем страницу матчинга
    // URL: /shop/{shopName}/sites-items-mapping
    Page page = context.newPage();
    playwrightHelper.navigateWithRetry(page, url);
    
    // 384-386: JavaScript-вычисление — извлекаем ВСЕ значения из Select
    List<String> siteNames = (List<String>) page.evaluate(
        "() => Array.from(document.querySelectorAll('select[name=\"site\"] option'))" +
        ".filter(o => o.value).map(o => o.value)");
    // Результат: [alibaba.com, ozon.ru, wildberries.ru, ...]
}
```

### Шаг 2. Сравнение (Diff трёх групп)
**Файл:** `ZoomosParserService.java:393-406`

```java
// 393-394: загружаем существующие сайты для магазина из БД
Map<String, ZoomosCityId> existingMap = 
    cityIdRepository.findByShopIdOrderBySiteName(shop.getId())
        .stream()
        .collect(Collectors.toMap(ZoomosCityId::getSiteName, c -> c));
// Результат: {alibaba.com → ZoomosCityId(...), wildberries.ru → ZoomosCityId(...)}

// 396-399: сайты, которых ещё нет в нашей БД
List<String> toAdd = siteNames.stream()
    .filter(s -> !existingMap.containsKey(s))
    .collect(Collectors.toList());
// Если на странице: [alibaba, ozon, wildberries, yandex_market]
// А в БД есть: [alibaba, wildberries]
// То toAdd = [ozon, yandex_market]

// 400-403: сайты, которые в БД, но на странице матчинга больше нет
List<String> toDelete = existingMap.keySet().stream()
    .filter(s -> !siteSet.contains(s))
    .collect(Collectors.toList());

// 404-406: сайты, которые уже в БД И есть на странице
List<String> existing = siteNames.stream()
    .filter(existingMap::containsKey)
    .collect(Collectors.toList());
```

### Шаг 3. Apply: добавление и удаление
**Файл:** `ZoomosParserService.java:429-458`

```java
public String applySyncFromMatching(String shopName, 
                                    List<String> toAdd, 
                                    List<String> toDelete) {
    
    // 434-445: для каждого нового сайта создаём запись ZoomosCityId
    for (String siteName : toAdd) {
        Optional<ZoomosKnownSite> known = 
            knownSiteRepository.findBySiteName(siteName);
        String checkType = known
            .map(ZoomosKnownSite::getCheckType)
            .orElse("ITEM");
        
        cityIdRepository.save(ZoomosCityId.builder()
            .shop(shop)
            .siteName(siteName)
            .checkType(checkType)
            // cityIds НЕ устанавливаем — это отдельный процесс!
            .build());
        
        if (known.isEmpty()) {
            knownSiteRepository.save(ZoomosKnownSite.builder()
                .siteName(siteName)
                .checkType(checkType)
                .build());
        }
        added++;
    }
    
    // 448-452: удаляем старые записи
    for (String siteName : toDelete) {
        cityIdRepository
            .findByShopIdAndSiteName(shop.getId(), siteName)
            .ifPresent(cityIdRepository::delete);
        deleted++;
    }
    
    // 454: обновляем timestamp последней синхронизации
    shop.setLastSyncedAt(ZonedDateTime.now());
    shopRepository.save(shop);
}
```

---

## Ловушки (Non-obvious behaviour)

### Главная ловушка: City_ids НЕ заполняются автоматически!

```java
cityIdRepository.save(ZoomosCityId.builder()
    .shop(shop)
    .siteName(siteName)
    .checkType(checkType)
    // ❌ cityIds НЕ установлены!
    .build());
```

**Что происходит:**
- Функция из матчинга добавляет ТОЛЬКО имя сайта в таблицу `zoomos_city_ids`
- **Но city_ids остаются `null`** — это числовые коды городов!
- Чтобы заполнить city_ids, нужно отдельно запустить `syncShopSettings()` (строка 220)

**Сценарий, где это "поймает":**
```
1. На странице матчинга появился новый сайт "example.com"
2. Юзер кликает "Preview" → видит что добавить example.com
3. Юзер кликает "Apply" → пишется в БД
4. Юзер видит в таблице: [example.com | null | ITEM | false | ...]
                                       ↑ это пусто!
5. Парсер не сможет проверить цены, т.к. city_ids = null
6. Нужно дополнительно открыть страницу Settings и запустить sync оттуда
```

### Другая ловушка: Существующие сайты не трогаются

Строка 305:
```java
ZoomosCityId existing = existingMap.get(sn);
if (existing == null) continue; // ← если есть в БД, пропускаем
```

Если сайт уже в БД и есть на матчинге — **city_ids не обновляется**.
Для обновления city_ids существующих сайтов используйте `syncShopSettings()` или `previewSyncSettings()`.

---

## Итоговая схема: два независимых процесса

```
┌─────────────────────────────────────────────────────────────┐
│              ДВА независимых процесса синхронизации         │
└─────────────────────────────────────────────────────────────┘

1️⃣ СПИСОК САЙТОВ (из матчинга)
   previewSyncFromMatching()  → показать diff
   applySyncFromMatching()    → добавить/удалить сайты
   
   Затрагивает:
   - ✅ добавление новых сайтов
   - ✅ удаление старых сайтов
   - ✅ checkType (из справочника ZoomosKnownSite)
   - ❌ city_ids (остаются null!)
   
2️⃣ CITY_IDS (из страницы Settings)
   syncShopSettings()         → сразу сохранить
   previewSyncSettings()      → показать diff
   applySyncSettings()        → обновить city_ids
   
   Затрагивает:
   - ✅ обновление city_ids СУЩЕСТВУЮЩИХ сайтов
   - ❌ добавление новых сайтов
   - ❌ удаление старых сайтов

ПРАВИЛЬНЫЙ ПОРЯДОК:
Step 1: applySyncFromMatching() — добавить новые сайты
Step 2: syncShopSettings() или applySyncSettings() — заполнить city_ids
```

**Главное понимание:** Матчинг и Settings — это **две разные страницы** с **разными данными**.
Матчинг говорит "какие сайты нужны", Settings говорит "какие города отслеживать".
