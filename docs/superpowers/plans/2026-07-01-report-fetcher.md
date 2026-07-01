# Report Fetcher Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the "Report Fetcher" utility (`/utils/report-fetcher`): reusable configs that
download long-running Zoomos reports in the background, transform rows via SpEL formulas and a
lookup file, and produce a downloadable result file, with live WebSocket status.

**Architecture:** Spring MVC controller + services in a new `com.java.service.reportfetcher`
package, three new entities (`ReportConfig`, `ReportOutputColumn`, `ReportRun`) plus
`ZoomosAuthSession` for cookie persistence. Downloading uses `java.net.http.HttpClient` with a
`CookieManager`; transformation reuses the project's existing `FileReaderUtils` /
`FileGeneratorService` infrastructure via transient (non-persisted) `FileMetadata` objects and a
transient `ExportTemplate`. Long-running work runs on a dedicated `@Async` executor; status is
persisted on `ReportRun` and pushed over the existing STOMP broker (`/topic/report-fetcher/{runId}`).

**Tech Stack:** Spring Boot 3.2.12, Java 17, Hibernate/JPA, PostgreSQL, Flyway, Spring SpEL
(`spring-expression`, already on the classpath via `spring-context`), `java.net.http.HttpClient`
(JDK built-in), Spring WebSocket/STOMP (already configured in `WebSocketConfig`), JUnit 5 +
Mockito, `com.sun.net.httpserver.HttpServer` (JDK built-in) for HTTP-level tests without new
test dependencies.

**Spec:** `docs/superpowers/specs/2026-07-01-report-fetcher-design.md`

**Deviations from the spec, discovered during planning (both already corrected in the spec file):**
1. `lookupFileMetadataId` (FK on `FileMetadata`) is not implementable — `file_metadata.import_session_id`
   is `NOT NULL UNIQUE` in the DB (`V2__create_import_system_tables.sql:76`), tightly coupled to the
   import system. Lookup file info is instead stored directly on `ReportConfig`
   (`lookupFileOriginalName`, `lookupFileStoredPath`, `lookupFileFormat`, `lookupFileDelimiter`,
   `lookupFileEncoding`). Reading still goes through `FileReaderUtils` via a transient (not
   persisted) `FileMetadata` object — same trick used for the downloaded report itself.
2. SpEL syntax in the user-facing help was wrong (`#Колонка` is invalid for column names with
   spaces, since `#name` requires a bound variable with a valid identifier). Corrected to
   `['Название колонки']` bracket indexing on the row `Map`, which works for any column name
   without special-casing.

---

## File Structure

**New files:**
- `src/main/resources/db/migration/V60__create_report_fetcher_tables.sql` — schema
- `src/main/java/com/java/model/enums/ReportRunStatus.java`
- `src/main/java/com/java/model/enums/ReportOutputColumnType.java`
- `src/main/java/com/java/model/enums/ReportRunTrigger.java`
- `src/main/java/com/java/model/entity/ReportConfig.java`
- `src/main/java/com/java/model/entity/ReportOutputColumn.java`
- `src/main/java/com/java/model/entity/ReportRun.java`
- `src/main/java/com/java/model/entity/ZoomosAuthSession.java`
- `src/main/java/com/java/repository/ReportConfigRepository.java`
- `src/main/java/com/java/repository/ReportRunRepository.java`
- `src/main/java/com/java/repository/ZoomosAuthSessionRepository.java`
- `src/main/java/com/java/service/reportfetcher/ReportExpressionException.java` — thrown by the evaluator
- `src/main/java/com/java/service/reportfetcher/ReportRowExpressionEvaluator.java` — SpEL formula/filter evaluation (pure logic)
- `src/main/java/com/java/service/reportfetcher/ReportLookupIndex.java` — in-memory lookup-file index (pure logic)
- `src/main/java/com/java/service/reportfetcher/ZoomosAuthException.java`
- `src/main/java/com/java/service/reportfetcher/ZoomosAuthService.java` — cookie load/save, login
- `src/main/java/com/java/service/reportfetcher/ReportDownloadService.java` — auth-aware GET
- `src/main/java/com/java/service/reportfetcher/ReportTransformService.java` — parse + compute + lookup + filter + generate file
- `src/main/java/com/java/service/reportfetcher/ReportRunExecutorService.java` — the `@Async` orchestration
- `src/main/java/com/java/service/reportfetcher/ReportRunService.java` — starts runs, enforces "one active run per config"
- `src/main/java/com/java/service/reportfetcher/ReportConfigService.java` — CRUD, lookup file upload, validation
- `src/main/java/com/java/dto/reportfetcher/ReportConfigDto.java`
- `src/main/java/com/java/dto/reportfetcher/ReportOutputColumnDto.java`
- `src/main/java/com/java/dto/reportfetcher/ReportRunStatusDto.java`
- `src/main/java/com/java/controller/utils/ReportFetcherController.java`
- `src/main/resources/templates/utils/report-fetcher-list.html`
- `src/main/resources/templates/utils/report-fetcher-form.html`
- `src/main/resources/templates/utils/report-fetcher-history.html`
- Tests: `src/test/java/com/java/service/reportfetcher/*Test.java` (one per service above)

**Modified files:**
- `src/main/java/com/java/config/AsyncConfig.java` — add `reportFetchExecutor` bean
- `src/main/resources/application.properties` — add `report-fetcher.*` properties
- `src/main/java/com/java/controller/utils/UtilsController.java` — register the new utility card

---

## Task 1: Database schema

**Files:**
- Create: `src/main/resources/db/migration/V60__create_report_fetcher_tables.sql`

- [ ] **Step 1: Write the migration**

```sql
-- V60__create_report_fetcher_tables.sql

CREATE TABLE report_configs (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    client_id BIGINT,
    source_url TEXT NOT NULL,
    lookup_file_original_name VARCHAR(255),
    lookup_file_stored_path VARCHAR(500),
    lookup_file_format VARCHAR(10),
    lookup_file_delimiter VARCHAR(5),
    lookup_file_encoding VARCHAR(50),
    detected_report_columns TEXT,
    output_format VARCHAR(10) NOT NULL DEFAULT 'XLSX',
    row_filter_expression TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_report_config_client FOREIGN KEY (client_id)
        REFERENCES clients(id) ON DELETE SET NULL
);

CREATE TABLE report_output_columns (
    id BIGSERIAL PRIMARY KEY,
    config_id BIGINT NOT NULL,
    type VARCHAR(20) NOT NULL,
    output_header_name VARCHAR(255) NOT NULL,
    column_order INTEGER NOT NULL,
    included BOOLEAN NOT NULL DEFAULT TRUE,
    source_column_name VARCHAR(255),
    formula TEXT,
    key_column_in_report VARCHAR(255),
    key_column_in_lookup VARCHAR(255),
    value_column_in_lookup VARCHAR(255),

    CONSTRAINT fk_report_output_column_config FOREIGN KEY (config_id)
        REFERENCES report_configs(id) ON DELETE CASCADE
);

CREATE INDEX idx_report_output_columns_config ON report_output_columns(config_id);

CREATE TABLE report_runs (
    id BIGSERIAL PRIMARY KEY,
    config_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    started_at TIMESTAMP WITH TIME ZONE,
    finished_at TIMESTAMP WITH TIME ZONE,
    error_message TEXT,
    result_file_path VARCHAR(500),
    triggered_by VARCHAR(20) NOT NULL DEFAULT 'MANUAL',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_report_run_config FOREIGN KEY (config_id)
        REFERENCES report_configs(id) ON DELETE CASCADE
);

CREATE INDEX idx_report_runs_config ON report_runs(config_id);
CREATE INDEX idx_report_runs_status ON report_runs(status);

CREATE TABLE zoomos_auth_sessions (
    id BIGSERIAL PRIMARY KEY,
    cookies TEXT NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
```

Note: the DB column is `column_order` (not `position`, which is a SQL function name in
PostgreSQL) — the Java entity field is still called `position` for readability.

- [ ] **Step 2: Verify migration applies**

Run: `mvn flyway:info`
Expected: `V60` listed as `Pending` (not yet applied — applies automatically on next
`spring-boot:run`, or run `mvn flyway:migrate` to apply immediately and confirm no SQL errors).

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/db/migration/V60__create_report_fetcher_tables.sql
git commit -m "feat(report-fetcher): add DB schema for report configs, output columns, runs, auth session"
```

---

## Task 2: Enums

**Files:**
- Create: `src/main/java/com/java/model/enums/ReportRunStatus.java`
- Create: `src/main/java/com/java/model/enums/ReportOutputColumnType.java`
- Create: `src/main/java/com/java/model/enums/ReportRunTrigger.java`

- [ ] **Step 1: Write the enums**

```java
package com.java.model.enums;

public enum ReportRunStatus {
    PENDING,
    DOWNLOADING,
    TRANSFORMING,
    DONE,
    ERROR
}
```

```java
package com.java.model.enums;

public enum ReportOutputColumnType {
    SOURCE,
    COMPUTED,
    LOOKUP
}
```

```java
package com.java.model.enums;

public enum ReportRunTrigger {
    MANUAL
}
```

- [ ] **Step 2: Compile**

Run: `mvn compile -q`
Expected: `BUILD SUCCESS`

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/java/model/enums/ReportRunStatus.java \
        src/main/java/com/java/model/enums/ReportOutputColumnType.java \
        src/main/java/com/java/model/enums/ReportRunTrigger.java
git commit -m "feat(report-fetcher): add ReportRunStatus, ReportOutputColumnType, ReportRunTrigger enums"
```

---

## Task 3: Entities

**Files:**
- Create: `src/main/java/com/java/model/entity/ReportConfig.java`
- Create: `src/main/java/com/java/model/entity/ReportOutputColumn.java`
- Create: `src/main/java/com/java/model/entity/ReportRun.java`
- Create: `src/main/java/com/java/model/entity/ZoomosAuthSession.java`

Depends on: Task 1 (schema), Task 2 (enums).

- [ ] **Step 1: Write `ReportOutputColumn`**

```java
package com.java.model.entity;

import com.java.model.enums.ReportOutputColumnType;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "report_output_columns")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = "config")
public class ReportOutputColumn {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "config_id", nullable = false)
    private ReportConfig config;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private ReportOutputColumnType type;

    @Column(name = "output_header_name", nullable = false)
    private String outputHeaderName;

    @Column(name = "column_order", nullable = false)
    private Integer position;

    @Column(name = "included", nullable = false)
    @Builder.Default
    private Boolean included = true;

    @Column(name = "source_column_name")
    private String sourceColumnName;

    @Column(name = "formula", columnDefinition = "TEXT")
    private String formula;

    @Column(name = "key_column_in_report")
    private String keyColumnInReport;

    @Column(name = "key_column_in_lookup")
    private String keyColumnInLookup;

    @Column(name = "value_column_in_lookup")
    private String valueColumnInLookup;
}
```

- [ ] **Step 2: Write `ReportConfig`**

```java
package com.java.model.entity;

import com.java.model.Client;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "report_configs")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = {"client", "outputColumns"})
public class ReportConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id")
    private Client client;

    @Column(name = "source_url", columnDefinition = "TEXT", nullable = false)
    private String sourceUrl;

    @Column(name = "lookup_file_original_name")
    private String lookupFileOriginalName;

    @Column(name = "lookup_file_stored_path")
    private String lookupFileStoredPath;

    @Column(name = "lookup_file_format")
    private String lookupFileFormat;

    @Column(name = "lookup_file_delimiter")
    private String lookupFileDelimiter;

    @Column(name = "lookup_file_encoding")
    private String lookupFileEncoding;

    @Column(name = "detected_report_columns", columnDefinition = "TEXT")
    private String detectedReportColumns;

    @Column(name = "output_format", nullable = false)
    @Builder.Default
    private String outputFormat = "XLSX";

    @Column(name = "row_filter_expression", columnDefinition = "TEXT")
    private String rowFilterExpression;

    @OneToMany(mappedBy = "config", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    @Builder.Default
    private List<ReportOutputColumn> outputColumns = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private ZonedDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private ZonedDateTime updatedAt;
}
```

- [ ] **Step 3: Write `ReportRun`**

```java
package com.java.model.entity;

import com.java.model.enums.ReportRunStatus;
import com.java.model.enums.ReportRunTrigger;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.ZonedDateTime;

@Entity
@Table(name = "report_runs")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = "config")
public class ReportRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "config_id", nullable = false)
    private ReportConfig config;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private ReportRunStatus status = ReportRunStatus.PENDING;

    @Column(name = "started_at")
    private ZonedDateTime startedAt;

    @Column(name = "finished_at")
    private ZonedDateTime finishedAt;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "result_file_path")
    private String resultFilePath;

    @Enumerated(EnumType.STRING)
    @Column(name = "triggered_by", nullable = false)
    @Builder.Default
    private ReportRunTrigger triggeredBy = ReportRunTrigger.MANUAL;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private ZonedDateTime createdAt;
}
```

- [ ] **Step 4: Write `ZoomosAuthSession`**

```java
package com.java.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.ZonedDateTime;

@Entity
@Table(name = "zoomos_auth_sessions")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ZoomosAuthSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cookies", columnDefinition = "TEXT", nullable = false)
    private String cookies;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private ZonedDateTime updatedAt;
}
```

- [ ] **Step 5: Compile**

Run: `mvn compile -q`
Expected: `BUILD SUCCESS`

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/java/model/entity/ReportConfig.java \
        src/main/java/com/java/model/entity/ReportOutputColumn.java \
        src/main/java/com/java/model/entity/ReportRun.java \
        src/main/java/com/java/model/entity/ZoomosAuthSession.java
git commit -m "feat(report-fetcher): add ReportConfig, ReportOutputColumn, ReportRun, ZoomosAuthSession entities"
```

---

## Task 4: Repositories

**Files:**
- Create: `src/main/java/com/java/repository/ReportConfigRepository.java`
- Create: `src/main/java/com/java/repository/ReportRunRepository.java`
- Create: `src/main/java/com/java/repository/ZoomosAuthSessionRepository.java`

Depends on: Task 3.

- [ ] **Step 1: Write the repositories**

```java
package com.java.repository;

import com.java.model.entity.ReportConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReportConfigRepository extends JpaRepository<ReportConfig, Long> {

    List<ReportConfig> findAllByOrderByNameAsc();

    List<ReportConfig> findAllByClientIdOrderByNameAsc(Long clientId);
}
```

```java
package com.java.repository;

import com.java.model.entity.ReportRun;
import com.java.model.enums.ReportRunStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReportRunRepository extends JpaRepository<ReportRun, Long> {

    List<ReportRun> findAllByConfigIdOrderByCreatedAtDesc(Long configId);

    boolean existsByConfigIdAndStatusIn(Long configId, List<ReportRunStatus> statuses);
}
```

```java
package com.java.repository;

import com.java.model.entity.ZoomosAuthSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ZoomosAuthSessionRepository extends JpaRepository<ZoomosAuthSession, Long> {

    Optional<ZoomosAuthSession> findTopByOrderByUpdatedAtDesc();
}
```

- [ ] **Step 2: Compile**

Run: `mvn compile -q`
Expected: `BUILD SUCCESS`

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/java/repository/ReportConfigRepository.java \
        src/main/java/com/java/repository/ReportRunRepository.java \
        src/main/java/com/java/repository/ZoomosAuthSessionRepository.java
git commit -m "feat(report-fetcher): add repositories for report configs, runs, auth session"
```

---

## Task 5: SpEL formula/filter evaluator (TDD)

**Files:**
- Create: `src/main/java/com/java/service/reportfetcher/ReportExpressionException.java`
- Create: `src/main/java/com/java/service/reportfetcher/ReportRowExpressionEvaluator.java`
- Test: `src/test/java/com/java/service/reportfetcher/ReportRowExpressionEvaluatorTest.java`

Pure logic, no Spring context needed for the test.

- [ ] **Step 1: Write the failing test**

```java
package com.java.service.reportfetcher;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ReportRowExpressionEvaluatorTest {

    private final ReportRowExpressionEvaluator evaluator = new ReportRowExpressionEvaluator();

    private Map<String, Object> row() {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("Цена", 150);
        row.put("РРЦ", 100);
        row.put("Цена конкурента", 120);
        row.put("ОГРН", "1234567890");
        row.put("Название", "Товар со скидкой");
        return row;
    }

    @Test
    void shouldEvaluateArithmeticFormula() {
        Object result = evaluator.evaluateFormula("['Цена'] - ['РРЦ']", row());
        assertEquals(50, result);
    }

    @Test
    void shouldEvaluateFormulaWithSpacedColumnName() {
        Object result = evaluator.evaluateFormula("['Цена'] - ['Цена конкурента']", row());
        assertEquals(30, result);
    }

    @Test
    void shouldReturnNullOnBrokenFormula() {
        Object result = evaluator.evaluateFormula("['Цена'] +++ ", row());
        assertNull(result);
    }

    @Test
    void shouldEvaluateFilterToTrueWhenConditionMatches() {
        boolean result = evaluator.evaluateFilter("['ОГРН'] != null and ['ОГРН'] != ''", row());
        assertTrue(result);
    }

    @Test
    void shouldEvaluateFilterToFalseWhenConditionDoesNotMatch() {
        boolean result = evaluator.evaluateFilter("['Цена'] > 1000", row());
        assertFalse(result);
    }

    @Test
    void shouldEvaluateFilterToFalseOnBrokenExpression() {
        boolean result = evaluator.evaluateFilter("this is not spel", row());
        assertFalse(result);
    }

    @Test
    void shouldSupportContainsAndTernary() {
        assertTrue((boolean) evaluator.evaluateFormula("['Название'].contains('скидкой')", row()));
        assertEquals("Дороже", evaluator.evaluateFormula("['Цена'] > ['РРЦ'] ? 'Дороже' : 'Дешевле или равно'", row()));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=ReportRowExpressionEvaluatorTest -q`
Expected: FAIL — `ReportRowExpressionEvaluator` does not exist (compile error)

- [ ] **Step 3: Write the exception and the evaluator**

```java
package com.java.service.reportfetcher;

public class ReportExpressionException extends RuntimeException {
    public ReportExpressionException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

```java
package com.java.service.reportfetcher;

import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Evaluates SpEL expressions against a single report row (Map&lt;columnHeader, value&gt;).
 * Column references use bracket indexing on the row map: ['Column Name'].
 */
@Component
public class ReportRowExpressionEvaluator {

    private final ExpressionParser parser = new SpelExpressionParser();

    /**
     * Evaluates a COMPUTED column formula. Returns null (empty cell) on any parse/evaluation error
     * instead of throwing, per spec: a broken formula must not abort the whole run.
     */
    public Object evaluateFormula(String formula, Map<String, Object> row) {
        try {
            Expression expression = parser.parseExpression(formula);
            return expression.getValue(row);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Evaluates rowFilterExpression. Returns false (row excluded) on any parse/evaluation error,
     * per spec.
     */
    public boolean evaluateFilter(String filterExpression, Map<String, Object> row) {
        try {
            Expression expression = parser.parseExpression(filterExpression);
            Object result = expression.getValue(row);
            return Boolean.TRUE.equals(result);
        } catch (Exception e) {
            return false;
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=ReportRowExpressionEvaluatorTest -q`
Expected: `Tests run: 7, Failures: 0, Errors: 0`

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/java/service/reportfetcher/ReportExpressionException.java \
        src/main/java/com/java/service/reportfetcher/ReportRowExpressionEvaluator.java \
        src/test/java/com/java/service/reportfetcher/ReportRowExpressionEvaluatorTest.java
git commit -m "feat(report-fetcher): add SpEL formula/filter evaluator with TDD coverage"
```

---

## Task 6: Lookup index (TDD)

**Files:**
- Create: `src/main/java/com/java/service/reportfetcher/ReportLookupIndex.java`
- Test: `src/test/java/com/java/service/reportfetcher/ReportLookupIndexTest.java`

Pure logic — builds an in-memory index from `List<List<String>>` rows (first row = headers, same
shape `FileReaderUtils.readAllRows(...)` returns).

- [ ] **Step 1: Write the failing test**

```java
package com.java.service.reportfetcher;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ReportLookupIndexTest {

    private final List<List<String>> lookupRows = List.of(
            List.of("ОГРН", "Юр. лицо", "Город"),
            List.of("111", "ООО Ромашка", "Минск"),
            List.of("222", "ООО Василёк", "Гродно")
    );

    @Test
    void shouldFindValueByKey() {
        ReportLookupIndex index = ReportLookupIndex.build(lookupRows, "ОГРН");

        assertEquals(Optional.of("ООО Ромашка"), index.getValue("111", "Юр. лицо"));
        assertEquals(Optional.of("Минск"), index.getValue("111", "Город"));
        assertEquals(Optional.of("Гродно"), index.getValue("222", "Город"));
    }

    @Test
    void shouldReturnEmptyWhenKeyNotFound() {
        ReportLookupIndex index = ReportLookupIndex.build(lookupRows, "ОГРН");

        assertEquals(Optional.empty(), index.getValue("999", "Юр. лицо"));
    }

    @Test
    void shouldReturnEmptyWhenKeyValueIsNull() {
        ReportLookupIndex index = ReportLookupIndex.build(lookupRows, "ОГРН");

        assertEquals(Optional.empty(), index.getValue(null, "Юр. лицо"));
    }

    @Test
    void shouldKeepFirstRowOnDuplicateKeys() {
        List<List<String>> withDuplicate = List.of(
                List.of("ОГРН", "Юр. лицо"),
                List.of("111", "Первый"),
                List.of("111", "Второй")
        );
        ReportLookupIndex index = ReportLookupIndex.build(withDuplicate, "ОГРН");

        assertEquals(Optional.of("Первый"), index.getValue("111", "Юр. лицо"));
    }

    @Test
    void shouldThrowWhenKeyColumnMissing() {
        assertThrows(IllegalArgumentException.class,
                () -> ReportLookupIndex.build(lookupRows, "Несуществующая колонка"));
    }

    @Test
    void shouldHandleEmptyRows() {
        ReportLookupIndex index = ReportLookupIndex.build(List.of(), null);

        assertEquals(Optional.empty(), index.getValue("111", "Юр. лицо"));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=ReportLookupIndexTest -q`
Expected: FAIL — `ReportLookupIndex` does not exist (compile error)

- [ ] **Step 3: Write the implementation**

```java
package com.java.service.reportfetcher;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * In-memory index of a lookup file, keyed by one column's value.
 * Built once per ReportRun and reused for every LOOKUP output column that shares the same key.
 */
public final class ReportLookupIndex {

    private final Map<String, Map<String, String>> rowsByKey;

    private ReportLookupIndex(Map<String, Map<String, String>> rowsByKey) {
        this.rowsByKey = rowsByKey;
    }

    public static ReportLookupIndex build(List<List<String>> allRows, String keyColumnName) {
        if (allRows.isEmpty()) {
            return new ReportLookupIndex(Map.of());
        }

        List<String> headers = allRows.get(0);
        int keyIndex = headers.indexOf(keyColumnName);
        if (keyIndex < 0) {
            throw new IllegalArgumentException(
                    "Колонка-ключ '" + keyColumnName + "' не найдена в справочнике");
        }

        Map<String, Map<String, String>> index = new HashMap<>();
        for (int i = 1; i < allRows.size(); i++) {
            List<String> row = allRows.get(i);
            if (keyIndex >= row.size()) {
                continue;
            }
            String key = row.get(keyIndex);
            if (key == null || key.isBlank()) {
                continue;
            }
            index.computeIfAbsent(key, k -> {
                Map<String, String> rowMap = new LinkedHashMap<>();
                for (int col = 0; col < headers.size() && col < row.size(); col++) {
                    rowMap.put(headers.get(col), row.get(col));
                }
                return rowMap;
            });
        }
        return new ReportLookupIndex(index);
    }

    public Optional<String> getValue(String keyValue, String valueColumnName) {
        if (keyValue == null) {
            return Optional.empty();
        }
        Map<String, String> row = rowsByKey.get(keyValue);
        if (row == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(row.get(valueColumnName));
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=ReportLookupIndexTest -q`
Expected: `Tests run: 6, Failures: 0, Errors: 0`

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/java/service/reportfetcher/ReportLookupIndex.java \
        src/test/java/com/java/service/reportfetcher/ReportLookupIndexTest.java
git commit -m "feat(report-fetcher): add in-memory lookup index with TDD coverage"
```

---

## Task 7: Executor bean and application properties

**Files:**
- Modify: `src/main/java/com/java/config/AsyncConfig.java`
- Modify: `src/main/resources/application.properties`

Depends on: nothing (independent, but needed before Task 9/10/12).

- [ ] **Step 1: Add the executor bean**

Open `src/main/java/com/java/config/AsyncConfig.java`. Find the existing `utilsTaskExecutor` bean
(around line 113) and add a new bean right after it, following the exact same style:

```java
    @Bean(name = "reportFetchExecutor")
    public Executor reportFetchExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(3);
        executor.setQueueCapacity(20);
        executor.setThreadNamePrefix("ReportFetchExecutor-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();

        log.info("Инициализирован пул потоков для Report Fetcher: core=2, max=3, queue=20");

        return executor;
    }
```

- [ ] **Step 2: Add properties**

Open `src/main/resources/application.properties`. Add a new section right after the
`ZOOMOS.BY` section (after line 179, `zoomos.retry-delay-seconds=5`):

```properties
# =======================================================
# REPORT FETCHER - скачивание и трансформация отчётов Zoomos
# =======================================================
report-fetcher.result.dir=data/upload/report-fetcher-results
report-fetcher.lookup-file.dir=data/upload/report-fetcher-lookups
report-fetcher.download.timeout-hours=3
```

- [ ] **Step 3: Compile**

Run: `mvn compile -q`
Expected: `BUILD SUCCESS`

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/java/config/AsyncConfig.java src/main/resources/application.properties
git commit -m "feat(report-fetcher): add reportFetchExecutor bean and report-fetcher.* properties"
```

---

## Task 8: Zoomos authentication service (TDD with a local HTTP server)

**Files:**
- Create: `src/main/java/com/java/service/reportfetcher/ZoomosAuthException.java`
- Create: `src/main/java/com/java/service/reportfetcher/ZoomosAuthService.java`
- Test: `src/test/java/com/java/service/reportfetcher/ZoomosAuthServiceTest.java`

Uses `com.sun.net.httpserver.HttpServer` (built into the JDK) to simulate export.zoomos.by's
`/login` behavior without any new test dependency or real network calls.

Depends on: Task 4 (`ZoomosAuthSessionRepository`).

- [ ] **Step 1: Write the exception**

```java
package com.java.service.reportfetcher;

public class ZoomosAuthException extends RuntimeException {
    public ZoomosAuthException(String message) {
        super(message);
    }
}
```

- [ ] **Step 2: Write the failing test**

```java
package com.java.service.reportfetcher;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java.model.entity.ZoomosAuthSession;
import com.java.repository.ZoomosAuthSessionRepository;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ZoomosAuthServiceTest {

    private HttpServer server;
    private String baseUrl;
    private ZoomosAuthSessionRepository sessionRepository;
    private ZoomosAuthService authService;
    private final AtomicBoolean sessionCookieValid = new AtomicBoolean(false);

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/login", exchange -> {
            if ("POST".equals(exchange.getRequestMethod())) {
                exchange.getRequestBody().readAllBytes(); // drain
                sessionCookieValid.set(true);
                exchange.getResponseHeaders().add("Set-Cookie", "JSESSIONID=valid-session; Path=/");
                exchange.getResponseHeaders().add("Location", "/");
                exchange.sendResponseHeaders(302, -1);
            } else {
                exchange.sendResponseHeaders(200, 0);
                exchange.getResponseBody().close();
            }
            exchange.close();
        });
        server.createContext("/report", exchange -> {
            String cookieHeader = exchange.getRequestHeaders().getFirst("Cookie");
            boolean authenticated = cookieHeader != null && cookieHeader.contains("JSESSIONID=valid-session");
            if (authenticated) {
                byte[] body = "col1;col2\nval1;val2\n".getBytes();
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
            } else {
                exchange.getResponseHeaders().add("Location", "/login");
                exchange.sendResponseHeaders(302, -1);
            }
            exchange.close();
        });
        server.start();
        baseUrl = "http://localhost:" + server.getAddress().getPort();

        sessionRepository = mock(ZoomosAuthSessionRepository.class);
        when(sessionRepository.findTopByOrderByUpdatedAtDesc()).thenReturn(Optional.empty());
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        authService = new ZoomosAuthService(sessionRepository, new ObjectMapper());
        authService.setBaseUrl(baseUrl);
        authService.setUsername("test-user");
        authService.setPassword("test-pass");
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void shouldDetectLoginRedirect() {
        assertTrue(authService.isLoginPage(java.net.URI.create(baseUrl + "/login")));
        assertFalse(authService.isLoginPage(java.net.URI.create(baseUrl + "/report")));
    }

    @Test
    void shouldLoginAndSaveCookies() throws Exception {
        HttpClient client = authService.buildAuthenticatedClient();
        authService.login(client);

        verify(sessionRepository).save(argThat(session -> session.getCookies().contains("JSESSIONID")));
    }

    @Test
    void shouldThrowWhenLoginFailsForBadCredentials() throws Exception {
        server.removeContext("/login");
        server.createContext("/login", exchange -> {
            exchange.getRequestBody().readAllBytes();
            exchange.getResponseHeaders().add("Location", "/login");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });

        HttpClient client = authService.buildAuthenticatedClient();
        assertThrows(ZoomosAuthException.class, () -> authService.login(client));
    }

    @Test
    void shouldLoadCookiesFromExistingSession() throws Exception {
        String cookiesJson = new ObjectMapper().writeValueAsString(java.util.List.of(
                new ZoomosAuthService.SerializableCookie("JSESSIONID", "valid-session", "localhost", "/")
        ));
        when(sessionRepository.findTopByOrderByUpdatedAtDesc())
                .thenReturn(Optional.of(ZoomosAuthSession.builder().id(1L).cookies(cookiesJson).build()));

        HttpClient client = authService.buildAuthenticatedClient();
        java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder(
                        java.net.URI.create(baseUrl + "/report"))
                .GET().build();
        java.net.http.HttpResponse<String> response =
                client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());

        assertFalse(authService.isLoginPage(response.uri()));
        assertTrue(response.body().contains("val1"));
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `mvn test -Dtest=ZoomosAuthServiceTest -q`
Expected: FAIL — `ZoomosAuthService` does not exist (compile error)

- [ ] **Step 4: Write the implementation**

```java
package com.java.service.reportfetcher;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java.model.entity.ZoomosAuthSession;
import com.java.repository.ZoomosAuthSessionRepository;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpCookie;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

@Service
@Slf4j
public class ZoomosAuthService {

    private final ZoomosAuthSessionRepository sessionRepository;
    private final ObjectMapper objectMapper;

    @Setter
    @Value("${zoomos.base-url}")
    private String baseUrl;

    @Setter
    @Value("${zoomos.username}")
    private String username;

    @Setter
    @Value("${zoomos.password}")
    private String password;

    public ZoomosAuthService(ZoomosAuthSessionRepository sessionRepository, ObjectMapper objectMapper) {
        this.sessionRepository = sessionRepository;
        this.objectMapper = objectMapper;
    }

    public HttpClient buildAuthenticatedClient() {
        CookieManager cookieManager = new CookieManager();
        cookieManager.setCookiePolicy(CookiePolicy.ACCEPT_ALL);
        loadCookies(cookieManager);
        return HttpClient.newBuilder()
                .cookieHandler(cookieManager)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(30))
                .build();
    }

    public boolean isLoginPage(URI responseUri) {
        return responseUri.getPath() != null && responseUri.getPath().contains("/login");
    }

    public void login(HttpClient client) throws IOException, InterruptedException {
        String form = "j_username=" + URLEncoder.encode(username, StandardCharsets.UTF_8)
                + "&j_password=" + URLEncoder.encode(password, StandardCharsets.UTF_8);
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/login"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .timeout(Duration.ofSeconds(30))
                .build();
        HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
        if (isLoginPage(response.uri())) {
            throw new ZoomosAuthException(
                    "Авторизация не удалась — проверьте логин/пароль в настройках zoomos.*");
        }
        saveCookies(client);
        log.info("Авторизация на {} выполнена успешно", baseUrl);
    }

    public void saveCookies(HttpClient client) {
        CookieManager cookieManager = (CookieManager) client.cookieHandler().orElseThrow();
        List<HttpCookie> cookies = cookieManager.getCookieStore().getCookies();
        List<SerializableCookie> serializable = cookies.stream()
                .map(c -> new SerializableCookie(c.getName(), c.getValue(), c.getDomain(), c.getPath()))
                .toList();
        try {
            String json = objectMapper.writeValueAsString(serializable);
            ZoomosAuthSession session = sessionRepository.findTopByOrderByUpdatedAtDesc()
                    .orElseGet(ZoomosAuthSession::new);
            session.setCookies(json);
            sessionRepository.save(session);
        } catch (Exception e) {
            log.warn("Не удалось сохранить куки Zoomos: {}", e.getMessage());
        }
    }

    private void loadCookies(CookieManager cookieManager) {
        sessionRepository.findTopByOrderByUpdatedAtDesc().ifPresent(session -> {
            try {
                List<SerializableCookie> cookies = objectMapper.readValue(session.getCookies(),
                        new TypeReference<List<SerializableCookie>>() {});
                URI uri = URI.create(baseUrl);
                for (SerializableCookie c : cookies) {
                    HttpCookie cookie = new HttpCookie(c.name(), c.value());
                    if (c.domain() != null) cookie.setDomain(c.domain());
                    if (c.path() != null) cookie.setPath(c.path());
                    cookieManager.getCookieStore().add(uri, cookie);
                }
            } catch (Exception e) {
                log.warn("Не удалось загрузить куки Zoomos: {}", e.getMessage());
            }
        });
    }

    public record SerializableCookie(String name, String value, String domain, String path) {}
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `mvn test -Dtest=ZoomosAuthServiceTest -q`
Expected: `Tests run: 4, Failures: 0, Errors: 0`

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/java/service/reportfetcher/ZoomosAuthException.java \
        src/main/java/com/java/service/reportfetcher/ZoomosAuthService.java \
        src/test/java/com/java/service/reportfetcher/ZoomosAuthServiceTest.java
git commit -m "feat(report-fetcher): add ZoomosAuthService with cookie persistence, TDD against local HTTP server"
```

---

## Task 9: Report download service (TDD with a local HTTP server)

**Files:**
- Create: `src/main/java/com/java/service/reportfetcher/ReportDownloadService.java`
- Test: `src/test/java/com/java/service/reportfetcher/ReportDownloadServiceTest.java`

Depends on: Task 8.

- [ ] **Step 1: Write the failing test**

```java
package com.java.service.reportfetcher;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java.repository.ZoomosAuthSessionRepository;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReportDownloadServiceTest {

    private HttpServer server;
    private String baseUrl;
    private ZoomosAuthService authService;
    private ReportDownloadService downloadService;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/login", exchange -> {
            exchange.getRequestBody().readAllBytes();
            exchange.getResponseHeaders().add("Set-Cookie", "JSESSIONID=valid-session; Path=/");
            exchange.getResponseHeaders().add("Location", "/");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        server.createContext("/report.xls", exchange -> {
            String cookieHeader = exchange.getRequestHeaders().getFirst("Cookie");
            boolean authenticated = cookieHeader != null && cookieHeader.contains("JSESSIONID=valid-session");
            if (authenticated) {
                exchange.getResponseHeaders().add("Content-Type", "application/vnd.ms-excel");
                byte[] body = "col1;col2\nval1;val2\n".getBytes();
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
            } else {
                exchange.getResponseHeaders().add("Location", "/login");
                exchange.sendResponseHeaders(302, -1);
            }
            exchange.close();
        });
        server.createContext("/broken.xls", exchange -> {
            exchange.sendResponseHeaders(500, -1);
            exchange.close();
        });
        server.start();
        baseUrl = "http://localhost:" + server.getAddress().getPort();

        ZoomosAuthSessionRepository sessionRepository = mock(ZoomosAuthSessionRepository.class);
        when(sessionRepository.findTopByOrderByUpdatedAtDesc()).thenReturn(Optional.empty());
        when(sessionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        authService = new ZoomosAuthService(sessionRepository, new ObjectMapper());
        authService.setBaseUrl(baseUrl);
        authService.setUsername("test-user");
        authService.setPassword("test-pass");

        downloadService = new ReportDownloadService(authService);
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void shouldLoginThenDownloadWhenNotAuthenticatedYet() throws Exception {
        ReportDownloadService.ReportDownloadResult result = downloadService.download(baseUrl + "/report.xls");

        assertEquals("XLS", result.format());
        String content = Files.readString(result.filePath());
        assertTrue(content.contains("val1"));
    }

    @Test
    void shouldThrowOnServerError() {
        assertThrows(IOException.class, () -> downloadService.download(baseUrl + "/broken.xls"));
    }

    @Test
    void shouldThrowOnUnknownHost() {
        assertThrows(Exception.class, () -> downloadService.download("http://localhost:1/report.xls"));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=ReportDownloadServiceTest -q`
Expected: FAIL — `ReportDownloadService` does not exist (compile error)

- [ ] **Step 3: Write the implementation**

```java
package com.java.service.reportfetcher;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReportDownloadService {

    private final ZoomosAuthService authService;

    @Value("${report-fetcher.download.timeout-hours:3}")
    private int downloadTimeoutHours = 3;

    public ReportDownloadResult download(String sourceUrl) throws IOException, InterruptedException {
        HttpClient client = authService.buildAuthenticatedClient();
        HttpResponse<byte[]> response = sendGet(client, sourceUrl);

        if (authService.isLoginPage(response.uri())) {
            log.info("Сессия Zoomos недействительна, выполняется повторная авторизация");
            authService.login(client);
            response = sendGet(client, sourceUrl);
            if (authService.isLoginPage(response.uri())) {
                throw new ZoomosAuthException(
                        "Авторизация не удалась — проверьте логин/пароль в настройках zoomos.*");
            }
        } else {
            authService.saveCookies(client);
        }

        if (response.statusCode() != 200) {
            throw new IOException("Zoomos вернул HTTP " + response.statusCode());
        }
        if (response.body() == null || response.body().length == 0) {
            throw new IOException("Zoomos вернул пустой ответ");
        }

        String contentType = response.headers().firstValue("Content-Type").orElse("");
        String format = detectFormat(sourceUrl, contentType);

        Path tempFile = Files.createTempFile("report-fetcher-", "." + format.toLowerCase());
        Files.write(tempFile, response.body());

        return new ReportDownloadResult(tempFile, format);
    }

    private HttpResponse<byte[]> sendGet(HttpClient client, String url)
            throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .GET()
                .timeout(Duration.ofHours(downloadTimeoutHours))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofByteArray());
    }

    private String detectFormat(String sourceUrl, String contentType) {
        String ct = contentType.toLowerCase();
        if (ct.contains("spreadsheetml")) return "XLSX";
        if (ct.contains("ms-excel")) return "XLS";
        if (ct.contains("csv")) return "CSV";

        String lowerUrl = sourceUrl.toLowerCase();
        if (lowerUrl.contains("xlsx")) return "XLSX";
        if (lowerUrl.contains("xls")) return "XLS";
        if (lowerUrl.contains("csv")) return "CSV";
        return "XLSX";
    }

    public record ReportDownloadResult(Path filePath, String format) {}
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=ReportDownloadServiceTest -q`
Expected: `Tests run: 3, Failures: 0, Errors: 0`

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/java/service/reportfetcher/ReportDownloadService.java \
        src/test/java/com/java/service/reportfetcher/ReportDownloadServiceTest.java
git commit -m "feat(report-fetcher): add ReportDownloadService with reactive re-auth, TDD against local HTTP server"
```

---

## Task 10: Report transform service (TDD)

**Files:**
- Create: `src/main/java/com/java/service/reportfetcher/ReportTransformService.java`
- Test: `src/test/java/com/java/service/reportfetcher/ReportTransformServiceTest.java`

Depends on: Task 5, Task 6, Task 3 (entities). Uses real `FileReaderUtils` and
`FileGeneratorService` beans (both are plain `@Service`/`@Component` classes with no external
dependencies beyond the filesystem, so this test instantiates them directly rather than
bootstrapping the full Spring context).

- [ ] **Step 1: Write the failing test**

```java
package com.java.service.reportfetcher;

import com.java.model.entity.ExportTemplate;
import com.java.model.entity.ReportConfig;
import com.java.model.entity.ReportOutputColumn;
import com.java.model.enums.ReportOutputColumnType;
import com.java.service.exports.FileGeneratorService;
import com.java.service.exports.formatter.ValueFormatter;
import com.java.service.exports.generator.CsvFileGenerator;
import com.java.service.exports.generator.XlsxFileGenerator;
import com.java.service.exports.style.ExcelStyleFactory;
import com.java.util.FileReaderUtils;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ReportTransformServiceTest {

    private final FileReaderUtils fileReaderUtils = new FileReaderUtils();
    private final FileGeneratorService fileGeneratorService =
            new FileGeneratorService(List.of(
                    new CsvFileGenerator(new ValueFormatter()),
                    new XlsxFileGenerator(new ValueFormatter(), new ExcelStyleFactory())));
    private final ReportRowExpressionEvaluator evaluator = new ReportRowExpressionEvaluator();
    private final ReportTransformService transformService =
            new ReportTransformService(fileReaderUtils, fileGeneratorService, evaluator);

    private Path writeCsv(String content) throws IOException {
        Path file = Files.createTempFile("report-fetcher-test-", ".csv");
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return file;
    }

    private ReportConfig baseConfig() {
        return ReportConfig.builder()
                .id(1L)
                .name("Test Config")
                .outputFormat("CSV")
                .outputColumns(new ArrayList<>())
                .build();
    }

    @Test
    void shouldPassThroughSourceColumnsAndDetectHeaders() throws Exception {
        Path source = writeCsv("ОГРН;Цена;РРЦ\n111;150;100\n222;80;100\n");
        ReportConfig config = baseConfig();
        config.getOutputColumns().add(ReportOutputColumn.builder()
                .config(config).type(ReportOutputColumnType.SOURCE)
                .outputHeaderName("ОГРН").sourceColumnName("ОГРН").position(0).included(true).build());
        config.getOutputColumns().add(ReportOutputColumn.builder()
                .config(config).type(ReportOutputColumnType.SOURCE)
                .outputHeaderName("Цена").sourceColumnName("Цена").position(1).included(true).build());

        ReportTransformService.ReportTransformResult result =
                transformService.transform(source, "report.csv", "CSV", config);

        assertEquals(List.of("ОГРН", "Цена", "РРЦ"), result.reportHeaders());
        String resultContent = Files.readString(result.resultFilePath());
        assertTrue(resultContent.contains("111"));
        assertTrue(resultContent.contains("150"));
    }

    @Test
    void shouldComputeFormulaColumn() throws Exception {
        Path source = writeCsv("Цена;РРЦ\n150;100\n");
        ReportConfig config = baseConfig();
        config.getOutputColumns().add(ReportOutputColumn.builder()
                .config(config).type(ReportOutputColumnType.COMPUTED)
                .outputHeaderName("Разница").formula("['Цена'] - ['РРЦ']").position(0).included(true).build());

        ReportTransformService.ReportTransformResult result =
                transformService.transform(source, "report.csv", "CSV", config);

        String content = Files.readString(result.resultFilePath());
        assertTrue(content.contains("50"));
    }

    @Test
    void shouldApplyMultipleLookupColumnsFromSameKey() throws Exception {
        Path source = writeCsv("ОГРН;Цена\n111;150\n999;80\n");
        Path lookup = writeCsv("ОГРН;Юр. лицо;Город\n111;ООО Ромашка;Минск\n");

        ReportConfig config = baseConfig();
        config.setLookupFileStoredPath(lookup.toString());
        config.setLookupFileOriginalName("lookup.csv");
        config.setLookupFileFormat("CSV");
        config.setLookupFileDelimiter(";");
        config.setLookupFileEncoding("UTF-8");
        config.getOutputColumns().add(ReportOutputColumn.builder()
                .config(config).type(ReportOutputColumnType.LOOKUP)
                .outputHeaderName("Юр. лицо").keyColumnInReport("ОГРН").keyColumnInLookup("ОГРН")
                .valueColumnInLookup("Юр. лицо").position(0).included(true).build());
        config.getOutputColumns().add(ReportOutputColumn.builder()
                .config(config).type(ReportOutputColumnType.LOOKUP)
                .outputHeaderName("Город юр. лица").keyColumnInReport("ОГРН").keyColumnInLookup("ОГРН")
                .valueColumnInLookup("Город").position(1).included(true).build());

        ReportTransformService.ReportTransformResult result =
                transformService.transform(source, "report.csv", "CSV", config);

        String content = Files.readString(result.resultFilePath());
        assertTrue(content.contains("ООО Ромашка"));
        assertTrue(content.contains("Минск"));
    }

    @Test
    void shouldExcludeRowsFailingFilter() throws Exception {
        Path source = writeCsv("ОГРН;Цена\n111;150\n;80\n");
        ReportConfig config = baseConfig();
        config.setRowFilterExpression("['ОГРН'] != null and ['ОГРН'] != ''");
        config.getOutputColumns().add(ReportOutputColumn.builder()
                .config(config).type(ReportOutputColumnType.SOURCE)
                .outputHeaderName("ОГРН").sourceColumnName("ОГРН").position(0).included(true).build());

        ReportTransformService.ReportTransformResult result =
                transformService.transform(source, "report.csv", "CSV", config);

        String content = Files.readString(result.resultFilePath());
        assertTrue(content.contains("111"));
        assertFalse(content.contains("\n;")); // the row with empty ОГРН must be gone
    }

    @Test
    void shouldExcludeColumnsNotIncluded() throws Exception {
        Path source = writeCsv("ОГРН;Цена\n111;150\n");
        ReportConfig config = baseConfig();
        config.getOutputColumns().add(ReportOutputColumn.builder()
                .config(config).type(ReportOutputColumnType.SOURCE)
                .outputHeaderName("ОГРН").sourceColumnName("ОГРН").position(0).included(true).build());
        config.getOutputColumns().add(ReportOutputColumn.builder()
                .config(config).type(ReportOutputColumnType.SOURCE)
                .outputHeaderName("Цена").sourceColumnName("Цена").position(1).included(false).build());

        ReportTransformService.ReportTransformResult result =
                transformService.transform(source, "report.csv", "CSV", config);

        String content = Files.readString(result.resultFilePath());
        assertFalse(content.contains("Цена"));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=ReportTransformServiceTest -q`
Expected: FAIL — `ReportTransformService` does not exist (compile error)

- [ ] **Step 3: Write the implementation**

```java
package com.java.service.reportfetcher;

import com.java.model.entity.ExportTemplate;
import com.java.model.entity.ExportTemplateField;
import com.java.model.entity.FileMetadata;
import com.java.model.entity.ReportConfig;
import com.java.model.entity.ReportOutputColumn;
import com.java.model.enums.ReportOutputColumnType;
import com.java.service.exports.FileGeneratorService;
import com.java.util.FileReaderUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReportTransformService {

    private final FileReaderUtils fileReaderUtils;
    private final FileGeneratorService fileGeneratorService;
    private final ReportRowExpressionEvaluator expressionEvaluator;

    public ReportTransformResult transform(Path downloadedFilePath, String downloadedFileName,
                                            String downloadedFileFormat, ReportConfig config)
            throws IOException {
        FileMetadata reportMetadata = FileMetadata.builder()
                .originalFilename(downloadedFileName)
                .fileFormat(downloadedFileFormat)
                .tempFilePath(downloadedFilePath.toString())
                .detectedDelimiter(";")
                .detectedEncoding("UTF-8")
                .detectedQuoteChar("\"")
                .hasHeader(true)
                .build();

        List<List<String>> reportRows = fileReaderUtils.readAllRows(reportMetadata);
        if (reportRows.isEmpty()) {
            throw new IllegalStateException("Скачанный отчёт пуст");
        }
        List<String> reportHeaders = reportRows.get(0);

        ReportLookupIndex lookupIndex = buildLookupIndex(config);

        List<ReportOutputColumn> lookupColumns = filterSortedByType(config, ReportOutputColumnType.LOOKUP);
        List<ReportOutputColumn> computedColumns = filterSortedByType(config, ReportOutputColumnType.COMPUTED);
        List<ReportOutputColumn> includedColumns = config.getOutputColumns().stream()
                .filter(ReportOutputColumn::getIncluded)
                .sorted(Comparator.comparing(ReportOutputColumn::getPosition))
                .toList();

        int warnings = 0;
        List<Map<String, Object>> outputRows = new ArrayList<>();
        for (int i = 1; i < reportRows.size(); i++) {
            List<String> sourceRow = reportRows.get(i);
            Map<String, Object> rowValues = new LinkedHashMap<>();
            for (int col = 0; col < reportHeaders.size() && col < sourceRow.size(); col++) {
                rowValues.put(reportHeaders.get(col), sourceRow.get(col));
            }

            for (ReportOutputColumn lookupColumn : lookupColumns) {
                Object keyValue = rowValues.get(lookupColumn.getKeyColumnInReport());
                String value = lookupIndex
                        .getValue(keyValue == null ? null : keyValue.toString(), lookupColumn.getValueColumnInLookup())
                        .orElse(null);
                rowValues.put(lookupColumn.getOutputHeaderName(), value);
            }

            for (ReportOutputColumn computedColumn : computedColumns) {
                Object value = expressionEvaluator.evaluateFormula(computedColumn.getFormula(), rowValues);
                if (value == null && computedColumn.getFormula() != null) {
                    log.warn("Формула '{}' не вычислена для строки {} рана — пустая ячейка",
                            computedColumn.getFormula(), i);
                    warnings++;
                }
                rowValues.put(computedColumn.getOutputHeaderName(), value);
            }

            String filterExpression = config.getRowFilterExpression();
            if (filterExpression != null && !filterExpression.isBlank()) {
                boolean include = expressionEvaluator.evaluateFilter(filterExpression, rowValues);
                if (!include) {
                    continue;
                }
            }

            Map<String, Object> outputRow = new LinkedHashMap<>();
            for (ReportOutputColumn column : includedColumns) {
                String sourceKey = column.getType() == ReportOutputColumnType.SOURCE
                        ? column.getSourceColumnName()
                        : column.getOutputHeaderName();
                outputRow.put(column.getOutputHeaderName(), rowValues.get(sourceKey));
            }
            outputRows.add(outputRow);
        }

        ExportTemplate template = buildTemplate(config, includedColumns);
        String fileName = "report_" + config.getId() + "_" + System.currentTimeMillis();
        Path resultPath = fileGeneratorService.generateFile(outputRows.stream(), outputRows.size(), template, fileName);

        return new ReportTransformResult(resultPath, reportHeaders, warnings);
    }

    private List<ReportOutputColumn> filterSortedByType(ReportConfig config, ReportOutputColumnType type) {
        return config.getOutputColumns().stream()
                .filter(c -> c.getType() == type)
                .sorted(Comparator.comparing(ReportOutputColumn::getPosition))
                .toList();
    }

    private ReportLookupIndex buildLookupIndex(ReportConfig config) throws IOException {
        boolean hasLookupColumns = config.getOutputColumns().stream()
                .anyMatch(c -> c.getType() == ReportOutputColumnType.LOOKUP);
        if (!hasLookupColumns) {
            return ReportLookupIndex.build(List.of(), null);
        }
        if (config.getLookupFileStoredPath() == null) {
            throw new IllegalStateException(
                    "В конфиге есть LOOKUP-колонки, но файл-справочник не загружен");
        }

        FileMetadata lookupMetadata = FileMetadata.builder()
                .originalFilename(config.getLookupFileOriginalName())
                .fileFormat(config.getLookupFileFormat())
                .tempFilePath(config.getLookupFileStoredPath())
                .detectedDelimiter(config.getLookupFileDelimiter())
                .detectedEncoding(config.getLookupFileEncoding())
                .detectedQuoteChar("\"")
                .hasHeader(true)
                .build();
        List<List<String>> lookupRows = fileReaderUtils.readAllRows(lookupMetadata);

        String keyColumn = config.getOutputColumns().stream()
                .filter(c -> c.getType() == ReportOutputColumnType.LOOKUP)
                .map(ReportOutputColumn::getKeyColumnInLookup)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Не задана колонка-ключ справочника"));

        return ReportLookupIndex.build(lookupRows, keyColumn);
    }

    private ExportTemplate buildTemplate(ReportConfig config, List<ReportOutputColumn> includedColumns) {
        ExportTemplate template = ExportTemplate.builder()
                .name(config.getName())
                .fileFormat("CSV".equalsIgnoreCase(config.getOutputFormat()) ? "CSV" : "XLSX")
                .csvDelimiter(";")
                .csvEncoding("UTF-8")
                .csvQuoteChar("\"")
                .csvIncludeHeader(true)
                .fields(new ArrayList<>())
                .build();

        List<ExportTemplateField> fields = new ArrayList<>();
        int order = 1;
        for (ReportOutputColumn column : includedColumns) {
            fields.add(ExportTemplateField.builder()
                    .template(template)
                    .entityFieldName(column.getOutputHeaderName())
                    .exportColumnName(column.getOutputHeaderName())
                    .fieldOrder(order++)
                    .isIncluded(true)
                    .build());
        }
        template.setFields(fields);
        return template;
    }

    public record ReportTransformResult(Path resultFilePath, List<String> reportHeaders, int warningCount) {}
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=ReportTransformServiceTest -q`
Expected: `Tests run: 5, Failures: 0, Errors: 0`

Note on the test's manual instantiation (verified by reading the source, not a runtime guess):
`FileGeneratorService(List<FileGenerator> generators)` is its only constructor; `CsvFileGenerator`
requires a `ValueFormatter`, `XlsxFileGenerator` requires a `ValueFormatter` and an
`ExcelStyleFactory` — both of those two collaborator classes have plain no-arg constructors (no
required fields of their own), so `new CsvFileGenerator(new ValueFormatter())` and
`new XlsxFileGenerator(new ValueFormatter(), new ExcelStyleFactory())` compile and run standalone
without a Spring context. All five tests in this file use `outputFormat("CSV")`, so only
`CsvFileGenerator.generate(...)` actually executes — `XlsxFileGenerator` is only instantiated to
satisfy `FileGeneratorService`'s generator list, never invoked (its `@Value`-injected `batchSize`
stays `0` outside Spring, which would misbehave for XLSX generation, but is irrelevant here since
that code path never runs).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/java/service/reportfetcher/ReportTransformService.java \
        src/test/java/com/java/service/reportfetcher/ReportTransformServiceTest.java
git commit -m "feat(report-fetcher): add ReportTransformService (lookup, formulas, filter, file generation)"
```

---

## Task 11: WebSocket status DTO

**Files:**
- Create: `src/main/java/com/java/dto/reportfetcher/ReportRunStatusDto.java`

Depends on: nothing.

- [ ] **Step 1: Write the DTO**

```java
package com.java.dto.reportfetcher;

public record ReportRunStatusDto(Long runId, String status, String errorMessage) {
}
```

- [ ] **Step 2: Compile**

Run: `mvn compile -q`
Expected: `BUILD SUCCESS`

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/java/dto/reportfetcher/ReportRunStatusDto.java
git commit -m "feat(report-fetcher): add ReportRunStatusDto for WebSocket status push"
```

---

## Task 12: Run orchestration (executor + service)

**Files:**
- Create: `src/main/java/com/java/service/reportfetcher/ReportRunExecutorService.java`
- Create: `src/main/java/com/java/service/reportfetcher/ReportRunService.java`
- Test: `src/test/java/com/java/service/reportfetcher/ReportRunServiceTest.java`

Depends on: Task 4, Task 7 (executor bean name), Task 9, Task 10, Task 11.

Two classes, not one, to avoid the classic Spring self-invocation pitfall: an `@Async` method
called from another method **on the same bean** bypasses the proxy and runs synchronously.
`ReportRunService.startRun` calls `ReportRunExecutorService.execute` — a different bean — so the
`@Async` proxy is honored.

- [ ] **Step 1: Write the executor**

```java
package com.java.service.reportfetcher;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java.dto.reportfetcher.ReportRunStatusDto;
import com.java.model.entity.ReportConfig;
import com.java.model.entity.ReportRun;
import com.java.model.enums.ReportRunStatus;
import com.java.repository.ReportConfigRepository;
import com.java.repository.ReportRunRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.ZonedDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReportRunExecutorService {

    private final ReportRunRepository reportRunRepository;
    private final ReportConfigRepository reportConfigRepository;
    private final ReportDownloadService downloadService;
    private final ReportTransformService transformService;
    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;

    @Async("reportFetchExecutor")
    public void execute(Long runId) {
        ReportRun run = reportRunRepository.findById(runId)
                .orElseThrow(() -> new IllegalStateException("ReportRun не найден: " + runId));
        ReportConfig config = run.getConfig();

        run.setStartedAt(ZonedDateTime.now());
        updateStatus(run, ReportRunStatus.DOWNLOADING, null);

        try {
            ReportDownloadService.ReportDownloadResult downloaded =
                    downloadService.download(config.getSourceUrl());

            updateStatus(run, ReportRunStatus.TRANSFORMING, null);

            ReportTransformService.ReportTransformResult result = transformService.transform(
                    downloaded.filePath(),
                    "report." + downloaded.format().toLowerCase(),
                    downloaded.format(),
                    config);

            config.setDetectedReportColumns(toJson(result.reportHeaders()));
            reportConfigRepository.save(config);

            run.setResultFilePath(result.resultFilePath().toString());
            run.setFinishedAt(ZonedDateTime.now());
            updateStatus(run, ReportRunStatus.DONE, null);
        } catch (Exception e) {
            log.error("Ошибка выполнения ReportRun {}: {}", runId, e.getMessage(), e);
            run.setFinishedAt(ZonedDateTime.now());
            updateStatus(run, ReportRunStatus.ERROR, e.getMessage());
        }
    }

    private void updateStatus(ReportRun run, ReportRunStatus status, String errorMessage) {
        run.setStatus(status);
        run.setErrorMessage(errorMessage);
        reportRunRepository.save(run);
        messagingTemplate.convertAndSend("/topic/report-fetcher/" + run.getId(),
                new ReportRunStatusDto(run.getId(), status.name(), errorMessage));
    }

    private String toJson(List<String> headers) {
        try {
            return objectMapper.writeValueAsString(headers);
        } catch (Exception e) {
            log.warn("Не удалось сериализовать detectedReportColumns: {}", e.getMessage());
            return "[]";
        }
    }
}
```

- [ ] **Step 2: Write `ReportRunService`**

```java
package com.java.service.reportfetcher;

import com.java.model.entity.ReportConfig;
import com.java.model.entity.ReportRun;
import com.java.model.enums.ReportRunStatus;
import com.java.model.enums.ReportRunTrigger;
import com.java.repository.ReportConfigRepository;
import com.java.repository.ReportRunRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ReportRunService {

    private static final List<ReportRunStatus> ACTIVE_STATUSES =
            List.of(ReportRunStatus.PENDING, ReportRunStatus.DOWNLOADING, ReportRunStatus.TRANSFORMING);

    private final ReportRunRepository reportRunRepository;
    private final ReportConfigRepository reportConfigRepository;
    private final ReportRunExecutorService executorService;

    public ReportRun startRun(Long configId) {
        ReportConfig config = reportConfigRepository.findById(configId)
                .orElseThrow(() -> new IllegalArgumentException("Конфиг не найден: " + configId));

        if (reportRunRepository.existsByConfigIdAndStatusIn(configId, ACTIVE_STATUSES)) {
            throw new IllegalStateException("По этому конфигу уже выполняется запуск");
        }

        ReportRun run = ReportRun.builder()
                .config(config)
                .status(ReportRunStatus.PENDING)
                .triggeredBy(ReportRunTrigger.MANUAL)
                .build();
        run = reportRunRepository.save(run);

        executorService.execute(run.getId());
        return run;
    }
}
```

- [ ] **Step 3: Write the test**

```java
package com.java.service.reportfetcher;

import com.java.model.entity.ReportConfig;
import com.java.model.entity.ReportRun;
import com.java.model.enums.ReportRunStatus;
import com.java.repository.ReportConfigRepository;
import com.java.repository.ReportRunRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ReportRunServiceTest {

    private ReportRunRepository reportRunRepository;
    private ReportConfigRepository reportConfigRepository;
    private ReportRunExecutorService executorService;
    private ReportRunService reportRunService;

    @BeforeEach
    void setUp() {
        reportRunRepository = mock(ReportRunRepository.class);
        reportConfigRepository = mock(ReportConfigRepository.class);
        executorService = mock(ReportRunExecutorService.class);
        reportRunService = new ReportRunService(reportRunRepository, reportConfigRepository, executorService);

        when(reportConfigRepository.findById(1L))
                .thenReturn(Optional.of(ReportConfig.builder().id(1L).name("Test").build()));
        when(reportRunRepository.save(any(ReportRun.class))).thenAnswer(inv -> {
            ReportRun run = inv.getArgument(0);
            run.setId(100L);
            return run;
        });
    }

    @Test
    void shouldCreatePendingRunAndDispatchExecution() {
        when(reportRunRepository.existsByConfigIdAndStatusIn(eq(1L), anyList())).thenReturn(false);

        ReportRun run = reportRunService.startRun(1L);

        assertEquals(ReportRunStatus.PENDING, run.getStatus());
        verify(executorService).execute(100L);
    }

    @Test
    void shouldRejectStartWhenActiveRunExists() {
        when(reportRunRepository.existsByConfigIdAndStatusIn(eq(1L), anyList())).thenReturn(true);

        assertThrows(IllegalStateException.class, () -> reportRunService.startRun(1L));
        verify(executorService, never()).execute(anyLong());
    }

    @Test
    void shouldThrowWhenConfigMissing() {
        when(reportConfigRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> reportRunService.startRun(99L));
    }
}
```

- [ ] **Step 4: Run tests**

Run: `mvn test -Dtest=ReportRunServiceTest -q`
Expected: `Tests run: 3, Failures: 0, Errors: 0`

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/java/service/reportfetcher/ReportRunExecutorService.java \
        src/main/java/com/java/service/reportfetcher/ReportRunService.java \
        src/test/java/com/java/service/reportfetcher/ReportRunServiceTest.java
git commit -m "feat(report-fetcher): add run orchestration (ReportRunService + async executor), TDD"
```

---

## Task 13: Report config service (CRUD, lookup upload, validation)

**Files:**
- Create: `src/main/java/com/java/dto/reportfetcher/ReportOutputColumnDto.java`
- Create: `src/main/java/com/java/dto/reportfetcher/ReportConfigDto.java`
- Create: `src/main/java/com/java/service/reportfetcher/ReportConfigService.java`
- Test: `src/test/java/com/java/service/reportfetcher/ReportConfigServiceTest.java`

Depends on: Task 3, Task 4.

- [ ] **Step 1: Write the DTOs**

```java
package com.java.dto.reportfetcher;

import com.java.model.enums.ReportOutputColumnType;
import lombok.Data;

@Data
public class ReportOutputColumnDto {
    private Long id;
    private ReportOutputColumnType type;
    private String outputHeaderName;
    private Boolean included = true;
    private String sourceColumnName;
    private String formula;
    private String keyColumnInReport;
    private String keyColumnInLookup;
    private String valueColumnInLookup;
}
```

```java
package com.java.dto.reportfetcher;

import com.java.model.entity.ReportConfig;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Data
public class ReportConfigDto {
    private Long id;
    private String name;
    private Long clientId;
    private String sourceUrl;
    private String outputFormat = "XLSX";
    private String rowFilterExpression;
    private List<ReportOutputColumnDto> outputColumns = new ArrayList<>();

    public static ReportConfigDto fromEntity(ReportConfig config) {
        ReportConfigDto dto = new ReportConfigDto();
        dto.setId(config.getId());
        dto.setName(config.getName());
        dto.setClientId(config.getClient() != null ? config.getClient().getId() : null);
        dto.setSourceUrl(config.getSourceUrl());
        dto.setOutputFormat(config.getOutputFormat());
        dto.setRowFilterExpression(config.getRowFilterExpression());
        dto.setOutputColumns(config.getOutputColumns().stream().map(column -> {
            ReportOutputColumnDto columnDto = new ReportOutputColumnDto();
            columnDto.setId(column.getId());
            columnDto.setType(column.getType());
            columnDto.setOutputHeaderName(column.getOutputHeaderName());
            columnDto.setIncluded(column.getIncluded());
            columnDto.setSourceColumnName(column.getSourceColumnName());
            columnDto.setFormula(column.getFormula());
            columnDto.setKeyColumnInReport(column.getKeyColumnInReport());
            columnDto.setKeyColumnInLookup(column.getKeyColumnInLookup());
            columnDto.setValueColumnInLookup(column.getValueColumnInLookup());
            return columnDto;
        }).collect(Collectors.toList()));
        return dto;
    }
}
```

- [ ] **Step 2: Write the failing test**

```java
package com.java.service.reportfetcher;

import com.java.dto.reportfetcher.ReportConfigDto;
import com.java.dto.reportfetcher.ReportOutputColumnDto;
import com.java.model.entity.ReportConfig;
import com.java.model.enums.ReportOutputColumnType;
import com.java.repository.ClientRepository;
import com.java.repository.ReportConfigRepository;
import com.java.util.FileReaderUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ReportConfigServiceTest {

    private ReportConfigRepository reportConfigRepository;
    private ReportConfigService service;

    @BeforeEach
    void setUp() {
        reportConfigRepository = mock(ReportConfigRepository.class);
        ClientRepository clientRepository = mock(ClientRepository.class);
        FileReaderUtils fileReaderUtils = new FileReaderUtils();
        service = new ReportConfigService(reportConfigRepository, clientRepository, fileReaderUtils);

        when(reportConfigRepository.save(any(ReportConfig.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private ReportConfigDto dtoWithColumns(ReportOutputColumnDto... columns) {
        ReportConfigDto dto = new ReportConfigDto();
        dto.setName("Test config");
        dto.setSourceUrl("https://export.zoomos.by/shop/test/export");
        dto.setOutputFormat("XLSX");
        dto.setOutputColumns(List.of(columns));
        return dto;
    }

    private ReportOutputColumnDto lookupColumn(String outputName, String keyColumnInLookup) {
        ReportOutputColumnDto column = new ReportOutputColumnDto();
        column.setType(ReportOutputColumnType.LOOKUP);
        column.setOutputHeaderName(outputName);
        column.setIncluded(true);
        column.setKeyColumnInReport("ОГРН");
        column.setKeyColumnInLookup(keyColumnInLookup);
        column.setValueColumnInLookup(outputName);
        return column;
    }

    @Test
    void shouldSaveNewConfigWithOutputColumns() {
        ReportOutputColumnDto sourceColumn = new ReportOutputColumnDto();
        sourceColumn.setType(ReportOutputColumnType.SOURCE);
        sourceColumn.setOutputHeaderName("Цена");
        sourceColumn.setIncluded(true);
        sourceColumn.setSourceColumnName("Цена");

        ReportConfig saved = service.save(dtoWithColumns(sourceColumn));

        assertEquals("Test config", saved.getName());
        assertEquals(1, saved.getOutputColumns().size());
        assertEquals(0, saved.getOutputColumns().get(0).getPosition());
    }

    @Test
    void shouldRejectMixedLookupKeyColumns() {
        ReportConfigDto dto = dtoWithColumns(
                lookupColumn("Юр. лицо", "ОГРН"),
                lookupColumn("Город", "ИНН") // different key column in the lookup file — not allowed
        );

        assertThrows(IllegalArgumentException.class, () -> service.save(dto));
    }

    @Test
    void shouldAllowMultipleLookupColumnsWithSameKey() {
        ReportConfig saved = service.save(dtoWithColumns(
                lookupColumn("Юр. лицо", "ОГРН"),
                lookupColumn("Город", "ОГРН")
        ));

        assertEquals(2, saved.getOutputColumns().size());
    }

    @Test
    void shouldThrowWhenUpdatingMissingConfig() {
        when(reportConfigRepository.findById(42L)).thenReturn(Optional.empty());
        ReportConfigDto dto = dtoWithColumns();
        dto.setId(42L);

        assertThrows(IllegalArgumentException.class, () -> service.save(dto));
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `mvn test -Dtest=ReportConfigServiceTest -q`
Expected: FAIL — `ReportConfigService` does not exist (compile error)

- [ ] **Step 4: Write the implementation**

```java
package com.java.service.reportfetcher;

import com.java.dto.reportfetcher.ReportConfigDto;
import com.java.dto.reportfetcher.ReportOutputColumnDto;
import com.java.model.entity.FileMetadata;
import com.java.model.entity.ReportConfig;
import com.java.model.entity.ReportOutputColumn;
import com.java.model.enums.ReportOutputColumnType;
import com.java.repository.ClientRepository;
import com.java.repository.ReportConfigRepository;
import com.java.util.FileReaderUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReportConfigService {

    private final ReportConfigRepository reportConfigRepository;
    private final ClientRepository clientRepository;
    private final FileReaderUtils fileReaderUtils;

    @Value("${report-fetcher.lookup-file.dir:data/upload/report-fetcher-lookups}")
    private String lookupFileDir = "data/upload/report-fetcher-lookups";

    @Transactional
    public ReportConfig save(ReportConfigDto dto) {
        validateOutputColumns(dto.getOutputColumns());

        ReportConfig config;
        if (dto.getId() != null) {
            config = reportConfigRepository.findById(dto.getId())
                    .orElseThrow(() -> new IllegalArgumentException("Конфиг не найден: " + dto.getId()));
        } else {
            config = new ReportConfig();
        }

        config.setName(dto.getName());
        config.setSourceUrl(dto.getSourceUrl());
        config.setOutputFormat(dto.getOutputFormat());
        config.setRowFilterExpression(dto.getRowFilterExpression());
        config.setClient(dto.getClientId() != null
                ? clientRepository.findById(dto.getClientId()).orElse(null)
                : null);

        config.getOutputColumns().clear();
        int position = 0;
        for (ReportOutputColumnDto columnDto : dto.getOutputColumns()) {
            ReportOutputColumn column = ReportOutputColumn.builder()
                    .config(config)
                    .type(columnDto.getType())
                    .outputHeaderName(columnDto.getOutputHeaderName())
                    .position(position++)
                    .included(columnDto.getIncluded() != null ? columnDto.getIncluded() : true)
                    .sourceColumnName(columnDto.getSourceColumnName())
                    .formula(columnDto.getFormula())
                    .keyColumnInReport(columnDto.getKeyColumnInReport())
                    .keyColumnInLookup(columnDto.getKeyColumnInLookup())
                    .valueColumnInLookup(columnDto.getValueColumnInLookup())
                    .build();
            config.getOutputColumns().add(column);
        }

        return reportConfigRepository.save(config);
    }

    private void validateOutputColumns(List<ReportOutputColumnDto> columns) {
        Set<String> lookupKeys = columns.stream()
                .filter(c -> c.getType() == ReportOutputColumnType.LOOKUP)
                .map(ReportOutputColumnDto::getKeyColumnInLookup)
                .collect(Collectors.toSet());
        if (lookupKeys.size() > 1) {
            throw new IllegalArgumentException(
                    "Все LOOKUP-колонки конфига должны использовать одну и ту же колонку-ключ справочника");
        }
    }

    public void attachLookupFile(Long configId, MultipartFile file) throws IOException {
        ReportConfig config = getEntity(configId);

        Path dir = Path.of(lookupFileDir);
        Files.createDirectories(dir);
        String storedName = configId + "_" + System.currentTimeMillis() + "_" + file.getOriginalFilename();
        Path storedPath = dir.resolve(storedName);
        file.transferTo(storedPath);

        config.setLookupFileOriginalName(file.getOriginalFilename());
        config.setLookupFileStoredPath(storedPath.toString());
        config.setLookupFileFormat(detectFormat(file.getOriginalFilename()));
        config.setLookupFileDelimiter(";");
        config.setLookupFileEncoding("UTF-8");
        reportConfigRepository.save(config);
    }

    public List<String> getLookupFileColumns(Long configId) throws IOException {
        ReportConfig config = getEntity(configId);
        if (config.getLookupFileStoredPath() == null) {
            return List.of();
        }
        FileMetadata metadata = FileMetadata.builder()
                .originalFilename(config.getLookupFileOriginalName())
                .fileFormat(config.getLookupFileFormat())
                .tempFilePath(config.getLookupFileStoredPath())
                .detectedDelimiter(config.getLookupFileDelimiter())
                .detectedEncoding(config.getLookupFileEncoding())
                .detectedQuoteChar("\"")
                .hasHeader(true)
                .build();
        List<List<String>> rows = fileReaderUtils.readAllRows(metadata);
        return rows.isEmpty() ? List.of() : rows.get(0);
    }

    private String detectFormat(String filename) {
        String lower = filename.toLowerCase();
        if (lower.endsWith(".xlsx")) return "XLSX";
        if (lower.endsWith(".xls")) return "XLS";
        return "CSV";
    }

    public List<ReportConfig> findAll(Long clientId) {
        return clientId != null
                ? reportConfigRepository.findAllByClientIdOrderByNameAsc(clientId)
                : reportConfigRepository.findAllByOrderByNameAsc();
    }

    public ReportConfig getEntity(Long id) {
        return reportConfigRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Конфиг не найден: " + id));
    }

    public ReportConfigDto toDto(Long id) {
        return ReportConfigDto.fromEntity(getEntity(id));
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `mvn test -Dtest=ReportConfigServiceTest -q`
Expected: `Tests run: 4, Failures: 0, Errors: 0`

- [ ] **Step 6: Commit**

```bash
git add src/main/java/com/java/dto/reportfetcher/ReportOutputColumnDto.java \
        src/main/java/com/java/dto/reportfetcher/ReportConfigDto.java \
        src/main/java/com/java/service/reportfetcher/ReportConfigService.java \
        src/test/java/com/java/service/reportfetcher/ReportConfigServiceTest.java
git commit -m "feat(report-fetcher): add ReportConfigService (CRUD, lookup upload, lookup-key validation), TDD"
```

---

## Task 14: Controller

**Files:**
- Create: `src/main/java/com/java/controller/utils/ReportFetcherController.java`

Depends on: Task 12, Task 13.

- [ ] **Step 1: Write the controller**

```java
package com.java.controller.utils;

import com.java.dto.reportfetcher.ReportConfigDto;
import com.java.dto.reportfetcher.ReportRunStatusDto;
import com.java.model.entity.ReportConfig;
import com.java.model.entity.ReportRun;
import com.java.model.enums.ReportRunStatus;
import com.java.repository.ClientRepository;
import com.java.repository.ReportRunRepository;
import com.java.service.reportfetcher.ReportConfigService;
import com.java.service.reportfetcher.ReportRunService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

@Controller
@RequestMapping("/utils/report-fetcher")
@RequiredArgsConstructor
@Slf4j
public class ReportFetcherController {

    private final ReportConfigService reportConfigService;
    private final ReportRunService reportRunService;
    private final ReportRunRepository reportRunRepository;
    private final ClientRepository clientRepository;

    @GetMapping
    public String list(@RequestParam(required = false) Long clientId, Model model) {
        model.addAttribute("pageTitle", "Report Fetcher");
        model.addAttribute("configs", reportConfigService.findAll(clientId));
        model.addAttribute("clients", clientRepository.findAll());
        model.addAttribute("selectedClientId", clientId);
        return "utils/report-fetcher-list";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("pageTitle", "Новый конфиг отчёта");
        model.addAttribute("config", new ReportConfigDto());
        model.addAttribute("clients", clientRepository.findAll());
        return "utils/report-fetcher-form";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        model.addAttribute("pageTitle", "Редактирование конфига отчёта");
        model.addAttribute("config", reportConfigService.toDto(id));
        model.addAttribute("clients", clientRepository.findAll());
        return "utils/report-fetcher-form";
    }

    @PostMapping("/save")
    public String save(@ModelAttribute ReportConfigDto dto, RedirectAttributes redirectAttributes) {
        try {
            reportConfigService.save(dto);
            redirectAttributes.addFlashAttribute("success", "Конфиг сохранён");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/utils/report-fetcher";
    }

    @PostMapping("/{id}/lookup-file")
    public String uploadLookupFile(@PathVariable Long id, @RequestParam("file") MultipartFile file,
                                    RedirectAttributes redirectAttributes) {
        try {
            reportConfigService.attachLookupFile(id, file);
            redirectAttributes.addFlashAttribute("success", "Справочник загружен");
        } catch (IOException e) {
            log.error("Ошибка загрузки справочника для конфига {}", id, e);
            redirectAttributes.addFlashAttribute("error", "Ошибка загрузки: " + e.getMessage());
        }
        return "redirect:/utils/report-fetcher/" + id + "/edit";
    }

    @PostMapping("/{id}/run")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> run(@PathVariable Long id) {
        try {
            ReportRun run = reportRunService.startRun(id);
            return ResponseEntity.ok(Map.of("runId", run.getId()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/{configId}/history")
    public String history(@PathVariable Long configId, Model model) {
        ReportConfig config = reportConfigService.getEntity(configId);
        model.addAttribute("pageTitle", "История запусков — " + config.getName());
        model.addAttribute("config", config);
        model.addAttribute("runs", reportRunRepository.findAllByConfigIdOrderByCreatedAtDesc(configId));
        return "utils/report-fetcher-history";
    }

    @GetMapping("/runs/{runId}")
    @ResponseBody
    public ResponseEntity<ReportRunStatusDto> runStatus(@PathVariable Long runId) {
        ReportRun run = reportRunRepository.findById(runId)
                .orElseThrow(() -> new IllegalArgumentException("Run не найден: " + runId));
        return ResponseEntity.ok(new ReportRunStatusDto(run.getId(), run.getStatus().name(), run.getErrorMessage()));
    }

    @GetMapping("/runs/{runId}/download")
    public ResponseEntity<Resource> download(@PathVariable Long runId) throws IOException {
        ReportRun run = reportRunRepository.findById(runId)
                .orElseThrow(() -> new IllegalArgumentException("Run не найден: " + runId));
        if (run.getStatus() != ReportRunStatus.DONE || run.getResultFilePath() == null) {
            throw new IllegalStateException("Результат ещё не готов");
        }
        Path path = Path.of(run.getResultFilePath());
        byte[] data = Files.readAllBytes(path);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + path.getFileName() + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(new ByteArrayResource(data));
    }
}
```

- [ ] **Step 2: Compile**

Run: `mvn compile -q`
Expected: `BUILD SUCCESS`

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/java/controller/utils/ReportFetcherController.java
git commit -m "feat(report-fetcher): add ReportFetcherController (pages + run/status/download endpoints)"
```

---

## Task 15: Register the utility on `/utils`

**Files:**
- Modify: `src/main/java/com/java/controller/utils/UtilsController.java`

Depends on: nothing (cosmetic, can run any time after Task 14).

- [ ] **Step 1: Add a card entry**

In `getAvailableUtilities()` (around line 96, right before the closing `return utilities;`), add:

```java
        utilities.add(Map.of(
            "id", "report-fetcher",
            "title", "Report Fetcher",
            "description", "Скачивание длинных отчётов Zoomos в фоне, трансформация и справочники",
            "icon", "fas fa-file-download",
            "url", "/utils/report-fetcher",
            "status", "ready"
        ));

        return utilities;
```

(Replace the existing bare `return utilities;` with the block above — the new `utilities.add(...)`
goes immediately before it.)

- [ ] **Step 2: Compile**

Run: `mvn compile -q`
Expected: `BUILD SUCCESS`

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/java/controller/utils/UtilsController.java
git commit -m "feat(report-fetcher): register Report Fetcher card on /utils"
```

---

## Task 16: List page template

**Files:**
- Create: `src/main/resources/templates/utils/report-fetcher-list.html`

Depends on: Task 14. Follow the layout conventions of `src/main/resources/templates/utils/url-cleaner.html`
(Thymeleaf layout fragment, Bootstrap 5 / Tabler classes) — read that file first for the exact
`layout:decorate` header and card markup used elsewhere in `/utils`.

- [ ] **Step 1: Write the template**

```html
<!DOCTYPE html>
<html lang="ru" xmlns:th="http://www.thymeleaf.org" xmlns:layout="http://www.ultraq.net.nz/thymeleaf/layout"
      layout:decorate="~{layout/main}">
<head>
    <title th:text="${pageTitle}">Report Fetcher</title>
</head>
<body>
<div layout:fragment="content">
    <div class="container-xl">
        <div class="page-header d-print-none">
            <div class="row align-items-center">
                <div class="col">
                    <h2 class="page-title" th:text="${pageTitle}">Report Fetcher</h2>
                </div>
                <div class="col-auto">
                    <a href="/utils/report-fetcher/new" class="btn btn-primary">
                        <i class="fas fa-plus"></i> Новый конфиг
                    </a>
                </div>
            </div>
        </div>

        <div th:if="${success}" class="alert alert-success" th:text="${success}"></div>
        <div th:if="${error}" class="alert alert-danger" th:text="${error}"></div>

        <div class="card mt-3">
            <div class="card-body">
                <form method="get" class="mb-3 row g-2 align-items-end">
                    <div class="col-auto">
                        <label class="form-label">Клиент</label>
                        <select name="clientId" class="form-select" onchange="this.form.submit()">
                            <option value="">Все</option>
                            <option th:each="c : ${clients}" th:value="${c.id}" th:text="${c.name}"
                                    th:selected="${selectedClientId != null and selectedClientId == c.id}"></option>
                        </select>
                    </div>
                </form>

                <table class="table table-vcenter">
                    <thead>
                    <tr>
                        <th>Название</th>
                        <th>Клиент</th>
                        <th></th>
                    </tr>
                    </thead>
                    <tbody>
                    <tr th:each="config : ${configs}">
                        <td th:text="${config.name}"></td>
                        <td th:text="${config.client != null ? config.client.name : '—'}"></td>
                        <td class="text-end">
                            <a th:href="@{/utils/report-fetcher/{id}/history(id=${config.id})}"
                               class="btn btn-sm btn-outline-secondary">История</a>
                            <a th:href="@{/utils/report-fetcher/{id}/edit(id=${config.id})}"
                               class="btn btn-sm btn-outline-secondary">Редактировать</a>
                            <button type="button" class="btn btn-sm btn-primary"
                                    th:attr="data-config-id=${config.id}" onclick="runConfig(this)">Запустить</button>
                        </td>
                    </tr>
                    </tbody>
                </table>
            </div>
        </div>
    </div>
</div>

<th:block layout:fragment="scripts">
<script th:inline="javascript">
    function runConfig(button) {
        var configId = button.getAttribute('data-config-id');
        fetch('/utils/report-fetcher/' + configId + '/run', {method: 'POST'})
            .then(function (response) { return response.json().then(function (data) { return {ok: response.ok, data: data}; }); })
            .then(function (result) {
                if (result.ok) {
                    window.location.href = '/utils/report-fetcher/' + configId + '/history';
                } else {
                    alert(result.data.error || 'Не удалось запустить отчёт');
                }
            });
    }
</script>
</th:block>
</body>
</html>
```

- [ ] **Step 2: Manual check**

Start the server (`mvn spring-boot:run -Dspring-boot.run.profiles=silent`) and open
`http://localhost:8081/utils/report-fetcher`. Expected: page renders with an empty table and a
working "Новый конфиг" link.

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/templates/utils/report-fetcher-list.html
git commit -m "feat(report-fetcher): add report-fetcher list page template"
```

---

## Task 17: Form template (create/edit with drag-and-drop `outputColumns` builder)

**Files:**
- Create: `src/main/resources/templates/utils/report-fetcher-form.html`

Depends on: Task 16 (same layout conventions). Read
`src/main/resources/templates/clients/list.html:280-300` first for the exact SortableJS
initialization pattern to mirror.

- [ ] **Step 1: Write the template**

```html
<!DOCTYPE html>
<html lang="ru" xmlns:th="http://www.thymeleaf.org" xmlns:layout="http://www.ultraq.net.nz/thymeleaf/layout"
      layout:decorate="~{layout/main}">
<head>
    <title th:text="${pageTitle}">Конфиг отчёта</title>
</head>
<body>
<div layout:fragment="content">
    <div class="container-xl">
        <h2 class="page-title" th:text="${pageTitle}">Конфиг отчёта</h2>

        <form method="post" action="/utils/report-fetcher/save" id="configForm">
            <input type="hidden" name="id" th:value="${config.id}"/>

            <div class="card mt-3">
                <div class="card-body">
                    <div class="mb-3">
                        <label class="form-label">Название</label>
                        <input type="text" name="name" class="form-control" th:value="${config.name}" required/>
                    </div>
                    <div class="mb-3">
                        <label class="form-label">Клиент (необязательно)</label>
                        <select name="clientId" class="form-select">
                            <option value="">—</option>
                            <option th:each="c : ${clients}" th:value="${c.id}" th:text="${c.name}"
                                    th:selected="${config.clientId != null and config.clientId == c.id}"></option>
                        </select>
                    </div>
                    <div class="mb-3">
                        <label class="form-label">URL запроса отчёта</label>
                        <textarea name="sourceUrl" class="form-control" rows="3" required th:text="${config.sourceUrl}"></textarea>
                    </div>
                    <div class="mb-3">
                        <label class="form-label">Формат итогового файла</label>
                        <select name="outputFormat" class="form-select">
                            <option value="XLSX" th:selected="${config.outputFormat == 'XLSX'}">XLSX</option>
                            <option value="CSV" th:selected="${config.outputFormat == 'CSV'}">CSV</option>
                        </select>
                    </div>
                    <div class="mb-3">
                        <label class="form-label">
                            Фильтр строк (SpEL, необязательно)
                            <button type="button" class="btn btn-sm btn-link" data-bs-toggle="modal" data-bs-target="#spelHelpModal">Как писать?</button>
                        </label>
                        <input type="text" name="rowFilterExpression" class="form-control"
                               th:value="${config.rowFilterExpression}"
                               placeholder="['ОГРН'] != null and ['ОГРН'] != ''"/>
                    </div>
                </div>
            </div>

            <div class="card mt-3" th:if="${config.id != null}">
                <div class="card-header">Файл-справочник (для LOOKUP-колонок)</div>
                <div class="card-body">
                    <form th:action="@{/utils/report-fetcher/{id}/lookup-file(id=${config.id})}" method="post" enctype="multipart/form-data" class="row g-2">
                        <div class="col-auto">
                            <input type="file" name="file" class="form-control" accept=".csv,.xlsx,.xls" required/>
                        </div>
                        <div class="col-auto">
                            <button type="submit" class="btn btn-outline-primary">Загрузить / заменить</button>
                        </div>
                    </form>
                </div>
            </div>

            <div class="card mt-3">
                <div class="card-header d-flex justify-content-between align-items-center">
                    <span>Колонки итогового файла</span>
                    <button type="button" class="btn btn-sm btn-primary" onclick="addColumnRow()">Добавить колонку</button>
                </div>
                <div class="card-body">
                    <div id="columnsList"></div>
                </div>
            </div>

            <button type="submit" class="btn btn-primary mt-3">Сохранить</button>
        </form>
    </div>

    <div class="modal modal-blur fade" id="spelHelpModal" tabindex="-1">
        <div class="modal-dialog modal-lg">
            <div class="modal-content">
                <div class="modal-header"><h5 class="modal-title">Справка по SpEL</h5>
                    <button type="button" class="btn-close" data-bs-dismiss="modal"></button></div>
                <div class="modal-body">
                    <p>Название колонки всегда в квадратных скобках: <code>['Название колонки']</code>.</p>
                    <ul>
                        <li><code>['Цена'] - ['РРЦ']</code> — разница</li>
                        <li><code>['ОГРН'] != null and ['ОГРН'] != ''</code> — не пусто</li>
                        <li><code>['Название'].contains('Акция')</code> — содержит текст</li>
                        <li><code>['Цена'] > ['РРЦ'] ? 'Дороже' : 'Дешевле или равно'</code> — если-то-иначе</li>
                    </ul>
                </div>
            </div>
        </div>
    </div>
</div>

<th:block layout:fragment="scripts">
<script src="https://cdn.jsdelivr.net/npm/sortablejs@1.15.3/Sortable.min.js"></script>
<script th:inline="javascript">
    var initialColumns = /*[[${config.outputColumns}]]*/ [];
    var columnsList = document.getElementById('columnsList');
    var rowCounter = 0;

    function addColumnRow(data) {
        data = data || {type: 'SOURCE', included: true};
        var index = rowCounter++;
        var row = document.createElement('div');
        row.className = 'card mb-2 column-row';
        row.innerHTML =
            '<div class="card-body d-flex gap-2 align-items-start">' +
            '<span class="drag-handle" style="cursor:move;padding-top:8px;">&#9776;</span>' +
            '<div class="flex-grow-1 row g-2">' +
            '<div class="col-2"><label class="form-label">Тип</label>' +
            '<select class="form-select col-type" name="outputColumns[' + index + '].type">' +
            '<option value="SOURCE">SOURCE</option><option value="COMPUTED">COMPUTED</option><option value="LOOKUP">LOOKUP</option>' +
            '</select></div>' +
            '<div class="col-2"><label class="form-label">Заголовок</label>' +
            '<input class="form-control" name="outputColumns[' + index + '].outputHeaderName" value="' + (data.outputHeaderName || '') + '"/></div>' +
            '<div class="col-2 field-source"><label class="form-label">Исходная колонка</label>' +
            '<input class="form-control" name="outputColumns[' + index + '].sourceColumnName" value="' + (data.sourceColumnName || '') + '"/></div>' +
            '<div class="col-3 field-computed" style="display:none"><label class="form-label">Формула</label>' +
            '<input class="form-control" name="outputColumns[' + index + '].formula" value="' + (data.formula || '') + '"/></div>' +
            '<div class="col-2 field-lookup" style="display:none"><label class="form-label">Ключ в отчёте</label>' +
            '<input class="form-control" name="outputColumns[' + index + '].keyColumnInReport" value="' + (data.keyColumnInReport || '') + '"/></div>' +
            '<div class="col-2 field-lookup" style="display:none"><label class="form-label">Ключ в справочнике</label>' +
            '<input class="form-control" name="outputColumns[' + index + '].keyColumnInLookup" value="' + (data.keyColumnInLookup || '') + '"/></div>' +
            '<div class="col-2 field-lookup" style="display:none"><label class="form-label">Значение из справочника</label>' +
            '<input class="form-control" name="outputColumns[' + index + '].valueColumnInLookup" value="' + (data.valueColumnInLookup || '') + '"/></div>' +
            '<div class="col-1"><label class="form-label">Вкл.</label><br/>' +
            '<input type="checkbox" name="outputColumns[' + index + '].included" value="true"' + (data.included ? ' checked' : '') + '/></div>' +
            '</div>' +
            '<button type="button" class="btn btn-sm btn-outline-danger" onclick="this.closest(\'.column-row\').remove()">&times;</button>' +
            '</div>';
        columnsList.appendChild(row);

        var typeSelect = row.querySelector('.col-type');
        typeSelect.value = data.type || 'SOURCE';
        function toggleFields() {
            var type = typeSelect.value;
            row.querySelector('.field-source').style.display = type === 'SOURCE' ? '' : 'none';
            row.querySelector('.field-computed').style.display = type === 'COMPUTED' ? '' : 'none';
            row.querySelectorAll('.field-lookup').forEach(function (el) { el.style.display = type === 'LOOKUP' ? '' : 'none'; });
        }
        typeSelect.addEventListener('change', toggleFields);
        toggleFields();
    }

    initialColumns.forEach(function (c) { addColumnRow(c); });

    Sortable.create(columnsList, {animation: 150, handle: '.drag-handle'});
</script>
</th:block>
</body>
</html>
```

Note on binding: `outputColumns[N].field` naming with a plain `@ModelAttribute ReportConfigDto`
relies on Spring's indexed-property binding, which requires the list to already have N+1 elements
or a `Set`/`List` with a no-arg-constructible element type — `ReportOutputColumnDto` qualifies
(public no-arg constructor via Lombok `@Data`, no `@AllArgsConstructor` forcing a different
constructor). If indexed binding proves unreliable in manual testing (Step 2), the safe fallback
is switching `@ModelAttribute ReportConfigDto dto` in `ReportFetcherController.save` to
`@RequestBody ReportConfigDto dto` with the form posting JSON instead of a normal form
submission — note this as a fallback, only implement it if the indexed binding manual test fails.

- [ ] **Step 2: Manual check**

Start the server, go to `/utils/report-fetcher/new`, fill in name + URL, add two columns (one
`SOURCE`, one `COMPUTED` with formula `['Цена'] - ['РРЦ']`), drag to reorder them, save. Expected:
redirected to the list with a success message, and re-opening `/edit` shows both columns in the
saved order with correct field values.

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/templates/utils/report-fetcher-form.html
git commit -m "feat(report-fetcher): add report-fetcher config form with drag-and-drop column builder"
```

---

## Task 18: History page with WebSocket status

**Files:**
- Create: `src/main/resources/templates/utils/report-fetcher-history.html`

Depends on: Task 14. Read `src/main/resources/templates/operations/status.html:375-420` first —
mirror its exact `SockJS`/`Stomp.over` connection pattern.

- [ ] **Step 1: Write the template**

```html
<!DOCTYPE html>
<html lang="ru" xmlns:th="http://www.thymeleaf.org" xmlns:layout="http://www.ultraq.net.nz/thymeleaf/layout"
      layout:decorate="~{layout/main}">
<head>
    <title th:text="${pageTitle}">История запусков</title>
</head>
<body>
<div layout:fragment="content">
    <div class="container-xl">
        <h2 class="page-title" th:text="${pageTitle}">История запусков</h2>

        <table class="table table-vcenter mt-3">
            <thead>
            <tr>
                <th>ID</th>
                <th>Статус</th>
                <th>Начат</th>
                <th>Завершён</th>
                <th>Ошибка</th>
                <th></th>
            </tr>
            </thead>
            <tbody>
            <tr th:each="run : ${runs}" th:id="'run-' + ${run.id}">
                <td th:text="${run.id}"></td>
                <td class="run-status" th:text="${run.status}"></td>
                <td th:text="${run.startedAt}"></td>
                <td th:text="${run.finishedAt}"></td>
                <td class="run-error" th:text="${run.errorMessage}"></td>
                <td>
                    <a th:if="${run.status.name() == 'DONE'}"
                       th:href="@{/utils/report-fetcher/runs/{id}/download(id=${run.id})}"
                       class="btn btn-sm btn-outline-primary">Скачать</a>
                </td>
            </tr>
            </tbody>
        </table>
    </div>
</div>

<th:block layout:fragment="scripts">
<script th:inline="javascript">
    var activeRunIds = /*[[${runs}]]*/ [];
    var pendingIds = [];
    document.querySelectorAll('.run-status').forEach(function (cell) {
        var status = cell.textContent.trim();
        if (status === 'PENDING' || status === 'DOWNLOADING' || status === 'TRANSFORMING') {
            var row = cell.closest('tr');
            pendingIds.push(row.id.replace('run-', ''));
        }
    });

    if (pendingIds.length > 0) {
        var socket = new SockJS('/ws');
        var stompClient = Stomp.over(socket);
        stompClient.connect({}, function () {
            pendingIds.forEach(function (runId) {
                stompClient.subscribe('/topic/report-fetcher/' + runId, function (message) {
                    var status = JSON.parse(message.body);
                    var row = document.getElementById('run-' + status.runId);
                    if (!row) return;
                    row.querySelector('.run-status').textContent = status.status;
                    row.querySelector('.run-error').textContent = status.errorMessage || '';
                    if (status.status === 'DONE' || status.status === 'ERROR') {
                        location.reload();
                    }
                });
            });
        });
    }
</script>
</th:block>
</body>
</html>
```

- [ ] **Step 2: Manual check**

From the list page, click "Запустить" on a saved config with a reachable `sourceUrl` (or a small
test HTTP endpoint returning a CSV immediately, to avoid waiting on a real long-running Zoomos
report). Expected: redirected to the history page, status updates live from `PENDING` through
`DOWNLOADING`/`TRANSFORMING` to `DONE` without a manual refresh, and the "Скачать" link produces
a working file download.

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/templates/utils/report-fetcher-history.html
git commit -m "feat(report-fetcher): add run history page with live WebSocket status updates"
```

---

## Task 19: Full test suite and manual smoke test

**Files:** none (verification only)

- [ ] **Step 1: Run the full test suite**

Run: `mvn test -q`
Expected: `BUILD SUCCESS`, all `com.java.service.reportfetcher.*Test` classes pass alongside the
existing suite.

- [ ] **Step 2: Start the server and verify the DB migration**

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=silent
```

Then in another shell:

```bash
psql -d zoomos_v4 -c "\d report_configs"
psql -d zoomos_v4 -c "\d report_output_columns"
psql -d zoomos_v4 -c "\d report_runs"
psql -d zoomos_v4 -c "\d zoomos_auth_sessions"
```

Expected: all four tables exist with the columns defined in Task 1.

- [ ] **Step 3: Manual end-to-end smoke test against a real Zoomos report**

1. Go to `http://localhost:8081/utils/report-fetcher/new`.
2. Paste a real (short/fast) export.zoomos.by report URL, save.
3. Add one `SOURCE` output column matching a real column from that report.
4. Click "Запустить" from the list page.
5. Confirm status moves `PENDING → DOWNLOADING → TRANSFORMING → DONE` live on the history page.
6. Confirm the very first run triggers a Zoomos login (check application logs for "Авторизация на
   ... выполнена успешно" if `zoomos_auth_sessions` was empty) and that a second run against the
   same config does **not** re-login (cookies reused).
7. Download the result file and confirm it contains the expected column and data.
8. Stop the server per `CLAUDE.md` ("Закрывать сервер после тестирования"): find the PID with
   `netstat -ano | findstr :8081` and `taskkill /F /PID <PID>` — never `taskkill /F /IM java.exe`.

- [ ] **Step 4: Update documentation**

Add a new file `docs/utils.md`-style entry, or update the existing utils documentation table in
`CLAUDE.md`'s `docs/info/utils.md` if that file documents each utility individually — check
`docs/info/utils.md` current content first and add a short "Report Fetcher" section following its
existing structure (this is required by `CLAUDE.md`'s Documentation Rules — "После любых изменений
в функционале — обновить соответствующий файл в docs/").

- [ ] **Step 5: Final commit**

```bash
git add docs/info/utils.md
git commit -m "docs: document Report Fetcher utility in docs/info/utils.md"
```

---

## Self-Review Notes

- **Spec coverage:** DB schema (Task 1), auth/cookies (Task 8), download with reactive re-auth
  (Task 9), formulas/lookup/filter/output assembly (Task 5, 6, 10), status machine + WebSocket
  (Task 12), one-active-run-per-config guard (Task 12), config CRUD + lookup file + same-lookup-key
  validation (Task 13), UI (Task 16-18), client link (Task 13/16/17) are all covered by a task.
  Email and scheduling are explicitly out of scope per the spec (separate future specs).
- **Placeholder scan:** no `TBD`/`TODO` remain; the one caveat (indexed form binding fallback in
  Task 17) states a concrete fallback action, not an unresolved question.
- **Type consistency:** `ReportOutputColumnType`, `ReportRunStatus`, `ReportRunTrigger` field
  names and the `ReportRun`/`ReportConfig`/`ReportOutputColumn` property names are used
  identically across Task 3 (entities), Task 10 (transform), Task 12 (executor), Task 13 (DTO/
  service), and Task 14 (controller).
