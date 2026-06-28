# Аудит Zoomos v4

**Дата**: 2026-04-07  
**Версии**: Java 17, Spring Boot 3.2.12, PostgreSQL, Thymeleaf, Playwright 1.40.0, Apache POI 5.2.3

---

## Сводка

| Severity | Количество |
|----------|-----------|
| CRITICAL | 3 |
| HIGH | 5 |
| MEDIUM | 7 |
| LOW | 5 |

---

## Находки

### SEC-001 [CRITICAL] Секреты в коде, зафиксированы в git

**Файл**: `src/main/resources/application.properties:165-177`

**Проблема**: В файле `application.properties`, который хранится в git-репозитории, жёстко прописаны три чувствительных значения:

```properties
redmine.api-key=13e3f9e71c0c5209ee787fadd9f8546103df066a
zoomos.username=dima@zoomos.by
zoomos.password=TLXMEbAR0
```

При этом `DB_PASSWORD` корректно вынесен через `${DB_PASSWORD:root}`, но пароль БД по умолчанию тоже `root` — что означает он попадёт в логи Spring Boot при старте.

**Риск**: Любой, кто получит доступ к репозиторию (включая публичный доступ в будущем, бэкапы, логи CI/CD), получит рабочие учётные данные сервиса Zoomos и трекера Redmine.

**Исправление**:

```properties
# application.properties — только placeholder'ы
redmine.api-key=${REDMINE_API_KEY}
zoomos.username=${ZOOMOS_USERNAME}
zoomos.password=${ZOOMOS_PASSWORD}
spring.datasource.password=${DB_PASSWORD}
```

Реальные значения хранятся в `.env` (в .gitignore) или передаются через переменные окружения. Немедленно ротировать скомпрометированные учётные данные Redmine и Zoomos.

---

### SEC-002 [CRITICAL] Path Traversal в FileDownloadController

**Файл**: `src/main/java/com/java/controller/FileDownloadController.java:35-46`

**Проблема**: Имя файла принимается из URL-пути (`@PathVariable String filename`) и используется в `resolve()` без нормализации:

```java
Path filePath = pathResolver.getAbsoluteExportDir().resolve(filename);
// ...
if (!filePath.startsWith(pathResolver.getAbsoluteExportDir())) { // НЕБЕЗОПАСНО
```

Java `Path.resolve("../../../etc/passwd")` создаёт ненормализованный путь, а `startsWith()` сравнивает сегменты пути. Запрос `GET /files/download/../../../../etc/passwd` обходит проверку, так как `filePath` до нормализации содержит `..`, и `startsWith()` сравнивает строковые компоненты, а не реальный путь.

**Исправление**:

```java
Path baseDir = pathResolver.getAbsoluteExportDir().toRealPath();
Path filePath = baseDir.resolve(filename).normalize().toRealPath();

if (!filePath.startsWith(baseDir)) {
    log.error("Path traversal attempt: {}", filename);
    return ResponseEntity.status(403).build();
}
```

Дополнительно — проверять, что `filename` не содержит `/`, `\`, `..` перед `resolve()`.

---

### SEC-003 [CRITICAL] Отсутствует аутентификация и авторизация (Spring Security не подключён)

**Файл**: `pom.xml` — зависимость `spring-boot-starter-security` отсутствует

**Проблема**: Всё приложение полностью открыто — нет ни одной проверки доступа. Любой пользователь в сети может:
- Запустить очистку данных `/maintenance/data-cleanup/execute` (удаление всей БД)
- Получить и изменить конфигурацию клиентов
- Запустить/остановить расписания Zoomos
- Скачать файлы с экспортами
- Получить доступ к Actuator-эндпоинтам (`/actuator/*`) без ограничений в dev/silent профилях

Контроллер явно фиксирует анонимный инициатор: `DataCleanupController.java:87` — `.initiatedBy("user")`.

**Контекст**: Приложение описано как pet-проект для локального использования. Если оно когда-либо выйдет в сеть или будет развёрнуто на сервере без сетевых ограничений — это CRITICAL уязвимость.

**Минимальное исправление** (без полной авторизации):

```xml
<!-- pom.xml -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>
```

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health").permitAll()
                .anyRequest().authenticated())
            .formLogin(Customizer.withDefaults())
            .httpBasic(Customizer.withDefaults());
        return http.build();
    }
}
```

---

### SEC-004 [HIGH] Open Redirect через заголовок Referer

**Файл**: `src/main/java/com/java/exception/GlobalExceptionHandler.java:135-137`

**Проблема**: При превышении размера файла происходит редирект на URL из заголовка `Referer` без какой-либо валидации:

```java
String referer = request.getHeader("Referer");
String redirectUrl = (referer != null && !referer.isEmpty()) ? referer : "/";
return new RedirectView(redirectUrl); // редирект на произвольный URL
```

Атакующий может создать страницу, которая сделает POST на `/import/*/upload` с большим файлом, подставив в `Referer: https://evil.com/phishing`. Сервер вернёт `302 → https://evil.com/phishing`.

**Исправление**:

```java
String referer = request.getHeader("Referer");
String redirectUrl = "/"; // безопасный дефолт
if (referer != null && !referer.isEmpty()) {
    try {
        URI refererUri = new URI(referer);
        // Разрешаем только relative URL или тот же origin
        if (refererUri.getHost() == null ||
            refererUri.getHost().equals(request.getServerName())) {
            redirectUrl = refererUri.getPath();
        }
    } catch (URISyntaxException e) {
        // остаётся "/"
    }
}
```

---

### SEC-005 [HIGH] Actuator без защиты в основном профиле

**Файл**: `src/main/resources/application.properties` — нет блока `management.*`

**Проблема**: В основном `application.properties` отсутствует конфигурация управления Actuator. Spring Boot по умолчанию экспонирует `/actuator/health` и `/actuator/info`. Но зависимость `spring-boot-starter-actuator` подключена — что в dev/silent профилях открывает все эндпоинты без ограничений. Продакшн-профиль (`application-prod.properties:64`) ограничивает до `health,info,metrics`, но не защищает аутентификацией.

**Исправление** в `application.properties`:

```properties
management.endpoints.web.exposure.include=health
management.endpoint.health.show-details=never
management.server.port=9090  # отдельный порт, закрытый снаружи
```

---

### SEC-006 [HIGH] Небезопасный Content-Disposition — потенциальный Header Injection

**Файлы**: `src/main/java/com/java/controller/FileDownloadController.java:58`, `DataMergerController.java:136,230`, и ещё 6 мест

**Проблема**: Имя файла из пользовательского ввода вставляется напрямую в HTTP-заголовок:

```java
.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
```

Если `filename` содержит `\r\n` (CRLF), атакующий может внедрить произвольные HTTP-заголовки. Дополнительно — `DataMergerController.java:136,230` не экранирует кавычки: `"attachment; filename=" + fileName` без кавычек позволяет сломать парсинг заголовка.

**Исправление** (применить ко всем местам):

```java
// Используйте ContentDisposition builder из Spring
ContentDisposition disposition = ContentDisposition.attachment()
    .filename(filename, StandardCharsets.UTF_8)
    .build();
headers.setContentDisposition(disposition);
```

---

### ARCH-001 [HIGH] ZoomosAnalysisController — god object, 2435 строк, 9 прямых репозиториев

**Файл**: `src/main/java/com/java/controller/ZoomosAnalysisController.java` — 2435 строк

**Проблема**: Контроллер инжектирует 9 репозиториев напрямую, содержит бизнес-логику, работает с транзакциями (`@Transactional` в методах контроллера — строки 1915, 2029), выполняет сборку данных для страниц. Это нарушает SRP, делает тестирование невозможным и создаёт нарушение слоёв архитектуры.

В методе `indexPage()` (строки 80-137) контроллер выполняет 7 запросов к разным репозиториям, группировку, фильтрацию стримами — всё это бизнес-логика сервисного слоя.

`@Transactional` в контроллере на строке 1915 опасен: транзакция охватывает HTTP-обработку, что удлиняет её и привязывает транзакционный контекст к web-уровню.

**Исправление**:

```java
// Создать ZoomosViewService для агрегации данных для вью
@Service
public class ZoomosViewService {
    public ZoomosIndexDto buildIndexModel() { ... }
    public ZoomosClientsDto buildClientsModel() { ... }
}

// Контроллер становится тонким
@GetMapping
public String indexPage(Model model) {
    model.addAttribute("data", viewService.buildIndexModel());
    return "zoomos/index";
}
```

---

### ARCH-002 [MEDIUM] ZoomosCheckService — 1739 строк, нарушение SRP

**Файл**: `src/main/java/com/java/service/ZoomosCheckService.java` — 1739 строк, 43 публичных/пакетных метода

**Проблема**: Один сервис отвечает за: парсинг статистики, оценку групп, логин через Playwright, работу с Redmine, WebSocket-уведомления, бизнес-алерты. По принципу SRP это минимум 4-5 отдельных классов.

**Рекомендация**:

```
ZoomosCheckService (оркестратор) →
  ZoomosLoginService (аутентификация на сайте)
  ZoomosStatisticsParser (получение и парсинг данных)
  ZoomosGroupEvaluator (evaluateGroup логика)
  ZoomosAlertService (пороговые оповещения)
```

---

### ARCH-003 [MEDIUM] Бизнес-логика в контроллерах (не только ZoomosAnalysisController)

**Файлы**: `src/main/java/com/java/controller/MaintenanceController.java:90`, `ExportStatisticsController.java`

**Проблема**: `MaintenanceController` вызывает `clientRepository.findAll()` напрямую (строка 90) — обход сервисного слоя. `ExportStatisticsController` (489 строк) содержит вычислительную логику.

**Исправление**: Все обращения к репозиториям переместить в соответствующие сервисы. Контроллер должен только: принять запрос → вызвать сервис → вернуть вид.

---

### PERF-001 [HIGH] N+1 запрос в ZoomosAnalysisController.schedulePage()

**Файл**: `src/main/java/com/java/controller/ZoomosAnalysisController.java:2054-2058`

**Проблема**: Для каждого магазина в цикле выполняются отдельные запросы:

```java
for (ZoomosShop shop : shops) {
    List<ZoomosShopSchedule> list = scheduleRepository.findAllByShopId(shop.getId()); // N запросов
    checkRunRepository.findFirstByShopIdOrderByStartedAtDesc(shop.getId())  // N запросов
```

При 50 магазинах — 100+ запросов на одну страницу.

**Исправление**:

```java
// Batch-загрузка всех расписаний одним запросом
List<Long> shopIds = shops.stream().map(ZoomosShop::getId).toList();
Map<Long, List<ZoomosShopSchedule>> schedules = scheduleRepository
    .findAllByShopIdIn(shopIds)  // 1 запрос
    .stream()
    .collect(Collectors.groupingBy(ZoomosShopSchedule::getShopId));
```

---

### PERF-002 [MEDIUM] SELECT * в нативных запросах

**Файлы**: `src/main/java/com/java/service/exports/strategies/TaskReportExportStrategy.java:320,336,344`, `repository/ZoomosParsingStatsRepository.java:58,72,87`

**Проблема**: `SELECT * FROM av_handbook` загружает все ~30 колонок широкой таблицы, когда нужно 2-3.

**Исправление**:

```java
// TaskReportExportStrategy.java:320
String sql = "SELECT handbook_region_code, handbook_retail_network_code FROM av_handbook WHERE handbook_web_site = ? LIMIT 1";
```

---

### PERF-003 [MEDIUM] findAll() без пагинации на потенциально больших таблицах

**Файл**: `src/main/java/com/java/controller/ZoomosAnalysisController.java:91,112,178,200,693,1278`

**Проблема**: `cityAddressRepository.findAll()` на строке 200 вызывается для fallback при пустом параметре `cityIds` — это может быть миллионы адресов.

**Исправление**:

```java
// Строка 200 - запрещаем "выдать всё" без фильтра
if (cityIds == null || cityIds.isBlank()) {
    return ResponseEntity.badRequest()
        .body(Map.of("error", "Параметр cityIds обязателен"));
}
```

---

### QUAL-001 [MEDIUM] Жёстко закодированный initiatedBy = "user"

**Файл**: `src/main/java/com/java/controller/DataCleanupController.java:87,143`

**Проблема**: Поле инициатора операции всегда записывается как строка `"user"`. В истории очистки невозможно отследить, кто запустил потенциально деструктивную операцию.

**Исправление**:

```java
.initiatedBy(request.getRemoteAddr()) // или "system" для авто-задач
```

---

### QUAL-002 [MEDIUM] Исключения проксируют внутренние детали пользователю

**Файлы**: `src/main/java/com/java/controller/ImportController.java:98`, `ExportController.java:119`, множество мест

**Проблема**: `e.getMessage()` может содержать внутренние пути, SQL-ошибки с именами таблиц, фрагменты stack trace.

**Исправление**:

```java
log.error("Ошибка загрузки файлов для клиента {}", clientId, e);
redirectAttributes.addFlashAttribute("errorMessage",
    "Не удалось загрузить файлы. Обратитесь к администратору.");
```

---

### QUAL-003 [MEDIUM] Flyway validate-on-migrate=false в основном профиле

**Файл**: `src/main/resources/application.properties:71`

**Проблема**: Изменённые миграции не обнаруживаются в dev-среде. В production-профиле правильно выставлено `true`, но разработка идёт без этой защиты.

**Исправление**:

```properties
# application.properties
spring.flyway.validate-on-migrate=true
# Только для dev: application-dev.properties
spring.flyway.validate-on-migrate=false
```

---

### QUAL-004 [LOW] Устаревший метод moveFromTempToUpload без удаления

**Файл**: `src/main/java/com/java/util/PathResolver.java:200-205`

**Проблема**: Метод помечен `@Deprecated` и делает только `log.warn` + делегирует. Нужно найти все вызовы через grep, заменить на актуальные методы и удалить deprecated-метод.

---

### QUAL-005 [LOW] Нет тестов, кроме WeekNumberUtilsTest

**Файл**: `src/test/java/com/java/util/WeekNumberUtilsTest.java` — единственный тестовый файл

**Проблема**: 322 production-класса, 1 тест. Критически важная логика работает без верификации.

**Приоритет для покрытия**:
1. `ZoomosCheckService.evaluateGroup()` — основная бизнес-логика оценки
2. `DataCleanupService.buildDeleteSql()` — критичная деструктивная операция
3. `UrlSecurityValidator.validateUrl()` — уже хорошо изолирован, тест тривиален

---

### UI-001 [LOW] Content-Security-Policy и X-Frame-Options не установлены

**Файл**: `src/main/java/com/java/config/WebConfig.java` — нет security headers

**Проблема**: Приложение не отправляет `Content-Security-Policy`, `X-Frame-Options`, `X-Content-Type-Options`.

**Исправление**:

```java
@Bean
public FilterRegistrationBean<OncePerRequestFilter> securityHeadersFilter() {
    FilterRegistrationBean<OncePerRequestFilter> bean = new FilterRegistrationBean<>();
    bean.setFilter(new OncePerRequestFilter() {
        @Override
        protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
                throws ServletException, IOException {
            res.setHeader("X-Frame-Options", "DENY");
            res.setHeader("X-Content-Type-Options", "nosniff");
            res.setHeader("X-XSS-Protection", "1; mode=block");
            chain.doFilter(req, res);
        }
    });
    bean.setOrder(Ordered.HIGHEST_PRECEDENCE);
    return bean;
}
```

---

### UI-002 [LOW] th:utext отсутствует — это хорошо, но риски в JS-блоках

**Файлы**: все 71 Thymeleaf-шаблон

**Хорошая новость**: `th:utext` не используется нигде — данные везде экранируются через `th:text`.

**Потенциальный риск**: В JavaScript-блоках шаблонов (`<script th:inline="javascript">`) необходимо убедиться, что нет конкатенации строк с пользовательскими данными. Использовать `/*[[${var}]]*/` синтаксис вместо ручной конкатенации.

---

## Рекомендации по приоритету

### Немедленно (до следующего деплоя на любой сервер):
1. **SEC-001** — Ротировать утёкшие credentials Redmine и Zoomos, вынести в env-переменные
2. **SEC-002** — Добавить `.normalize().toRealPath()` в `FileDownloadController`
3. **SEC-004** — Убрать редирект по заголовку `Referer`

### Перед выходом из "только localhost":
4. **SEC-003** — Подключить Spring Security хотя бы с HTTP Basic
5. **SEC-005** — Закрыть Actuator-эндпоинты в базовом профиле
6. **SEC-006** — Экранировать filename в Content-Disposition через `ContentDisposition` builder

### В ходе рефакторинга (технический долг):
7. **ARCH-001** — Начать декомпозицию `ZoomosAnalysisController`: выделить `ZoomosViewService`
8. **PERF-001** — Исправить N+1 в `schedulePage()`
9. **QUAL-003** — Включить Flyway validation в dev-профиле

### При следующей итерации:
10. **ARCH-002** — Декомпозиция `ZoomosCheckService`
11. **QUAL-005** — Покрыть тестами `evaluateGroup` и `UrlSecurityValidator`
12. **PERF-002/003** — Заменить SELECT * на проекции, добавить limit на city-addresses

---

## Сильные стороны

- Корректное использование `FetchType.LAZY` во всех `@ManyToOne`/`@OneToMany` — нет EAGER-загрузок без необходимости
- `UrlSecurityValidator` — хорошо изолированный компонент с правильной защитой от SSRF
- Глобальный `GlobalExceptionHandler` покрывает все основные типы исключений
- Везде используется `@RequiredArgsConstructor` + `private final` (конструктор-инъекция)
- Flyway для миграций с `clean-disabled=true` в продакшне
- Batch-паттерн (`findByShopIdIn`, `JOIN FETCH`) применяется в большинстве случаев — N+1 решён везде кроме `schedulePage()`
- `th:utext` не используется нигде — весь вывод в шаблонах безопасно экранирован

---

## Системные паттерны (требуют обсуждения)

1. **Прямой доступ к репозиториям в контроллерах** — встречается в 3+ контроллерах. Нужно либо принять как допустимый компромисс для pet-проекта, либо ввести правило: контроллер работает только через сервисный слой.

2. **SQL конкатенация для `IN`-клаузы с числами** — паттерн в `DataCleanupService` 6 раз. Числовые `Long` значения безопасны, но паттерн стоит вынести в утилитный метод во избежание копирования с ошибкой на строковые данные.

3. **Размер сервисов** — `DataCleanupService` (800 строк), `DatabaseMaintenanceService` (846 строк), `ZoomosCheckService` (1739 строк) — сервисы растут без декомпозиции.
