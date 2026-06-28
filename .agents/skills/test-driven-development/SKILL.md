---
name: test-driven-development
description: "TDD workflow for Java/Spring Boot: Red-Green-Refactor with JUnit 5 and Maven. Use when implementing new features or fixing bugs. Trigger keywords: TDD, напиши тест, test-driven, тест сначала, написать тесты, JUnit."
user-invocable: true
context: fork
agent: general-purpose
argument-hint: "[что реализовать]"
allowed-tools: Read, Grep, Glob, Edit, Write, Bash
---

Отвечай на русском языке.

# Test-Driven Development — JUnit 5 + Maven

**ЖЕЛЕЗНЫЙ ЗАКОН: НЕТ PRODUCTION КОДА БЕЗ FAILING ТЕСТА.**

Если код написан до теста — он должен быть удалён, не адаптирован.

---

## Цикл Red-Green-Refactor

### RED — Напиши failing тест
```java
// src/test/java/com/java/.../<ClassName>Test.java
@Test
void shouldDoSomething() {
    // given
    var service = new MyService();

    // when
    var result = service.doSomething("input");

    // then
    assertThat(result).isEqualTo("expected");
}
```

**Запусти и убедись что тест ПАДАЕТ:**
```bash
cd /e/workspace/zoomos_v4 && mvn test -Dtest=ClassNameTest -q 2>&1
# Ожидается: Tests run: 1, Failures: 1 (или Errors: 1)
```

Если тест сразу зелёный — он ничего не проверяет. Переосмысли.

### GREEN — Напиши минимальный код для прохождения
- Никакого лишнего кода
- Только то что нужно чтобы тест прошёл

```bash
cd /e/workspace/zoomos_v4 && mvn test -Dtest=ClassNameTest -q 2>&1
# Ожидается: Tests run: 1, Failures: 0, Errors: 0
```

### REFACTOR — Улучши без изменения поведения
- Убери дублирование
- Улучши читаемость
- После каждого изменения: тест должен оставаться зелёным

```bash
cd /e/workspace/zoomos_v4 && mvn test -Dtest=ClassNameTest -q 2>&1
# Ожидается: Tests run: N, Failures: 0, Errors: 0
```

---

## Шаблоны тестов для Zoomos v4

### Unit тест (без Spring контекста)
```java
@ExtendWith(MockitoExtension.class)
class ServiceTest {

    @Mock
    private MyRepository repository;

    @InjectMocks
    private MyService service;

    @Test
    void shouldReturnResult() {
        // given
        when(repository.findById(1L)).thenReturn(Optional.of(new Entity()));

        // when
        var result = service.process(1L);

        // then
        assertThat(result).isNotNull();
        verify(repository).findById(1L);
    }
}
```

### Spring Boot тест (с контекстом)
```java
@SpringBootTest
@Transactional
class ServiceIntegrationTest {

    @Autowired
    private MyService service;

    @Test
    void shouldPersistData() {
        // given + when
        var result = service.create(new CreateRequest("test"));

        // then
        assertThat(result.getId()).isNotNull();
    }
}
```

### Тест репозитория (DataJpaTest)
```java
@DataJpaTest
class RepositoryTest {

    @Autowired
    private MyRepository repository;

    @Test
    void shouldFindByName() {
        // given
        repository.save(new Entity("test"));

        // when
        var found = repository.findByName("test");

        // then
        assertThat(found).isPresent();
    }
}
```

---

## Запуск тестов

```bash
# Один тест
mvn test -Dtest=ClassNameTest -q 2>&1

# Один метод
mvn test -Dtest=ClassNameTest#methodName -q 2>&1

# Все тесты
mvn test -q 2>&1

# С профилем
mvn test -Dspring.profiles.active=test -q 2>&1
```

---

## Важно для этого проекта

- Тесты в: `src/test/java/com/java/`
- Профиль для тестов: по умолчанию Spring Boot test
- База данных в тестах: H2 in-memory (если настроена) или реальная PostgreSQL
- Единственный существующий тест: `WeekNumberUtilsTest.java`
