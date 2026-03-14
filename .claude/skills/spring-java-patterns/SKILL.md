---
name: spring-java-patterns
description: "Context skill: automatically loaded when writing Java/Spring Boot code in Zoomos v4. Provides patterns for services, controllers, async operations, transactions. Not user-invocable — loaded implicitly when creating/editing Java files."
user-invocable: false
disable-model-invocation: false
---

# Zoomos v4 Spring Boot Patterns

## Code Style
- Java 17, Spring Boot 3.2.12, Lombok (@Slf4j, @RequiredArgsConstructor, @Getter)
- KISS, YAGNI, MVP — pet проект, не усложнять
- Комментарии и переменные — на английском, общение — на русском

## Async Pattern (canonical)
```java
// 1. Controller — sync session creation + async launch
@PostMapping("/start")
public String startOperation(...) {
    return asyncService.startOperation(file, template, client);
}

// 2. Service — sync creation, async via afterCommit()
@Transactional
public String startOperation(...) {
    FileOperation op = createFileOperation(); // sync
    ImportSession session = createSession();  // sync
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                taskExecutor.execute(() -> processInBackground(session.getId()));
            }
        }
    );
    return session.getId().toString();
}
```

## @Transactional Rules
- Используй @Transactional(propagation = REQUIRES_NEW) для независимых операций (например, запись результата проверки)
- Не вызывай @Async методы на том же бине (self-invocation trap)
- afterCommit() — для запуска фона ПОСЛЕ commit'а транзакции

## WebSocket Progress Pattern
```java
@Autowired SimpMessagingTemplate messagingTemplate;

// Send progress
messagingTemplate.convertAndSend(
    "/topic/progress/" + operationId,
    Map.of("percent", 50, "status", "processing", "message", "...")
);
// Final status
messagingTemplate.convertAndSend(
    "/topic/progress/" + operationId,
    Map.of("percent", 100, "status", "completed")
);
```

## JDBC Batch (для массовых операций)
Prefer over JPA (~100x faster). See BarcodeHandbookService for reference.

## New EntityType checklist
1. Add to EntityType.java enum
2. Add switch case in EntityPersistenceService
3. Register handler in ImportProcessorService if needed

## Package Structure
```
src/main/java/com/java/
├── config/         — Spring configs (Async, WebSocket, Redmine, etc.)
├── controller/     — @Controller, @RestController
├── service/        — Business logic
├── dto/            — Data transfer objects
├── model/entity/   — JPA entities
├── constants/      — Constants
└── util/           — Utilities
```
