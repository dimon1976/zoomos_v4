package com.java.dto;

import lombok.Data;

@Data
public class QueryPerformanceDto {
    // Базовые поля
    private String query;
    private String queryHash;
    private long avgExecutionTimeMs;
    private long maxExecutionTimeMs;
    private int callCount;
    private String recommendation;
    private boolean slowQuery;
    private String tableName;
    private double cpuUsagePercent;

    // Новые поля для расширенного анализа
    private String queryType;           // SELECT, INSERT, UPDATE, DELETE, etc.
    private String state;               // active, idle, idle in transaction, waiting
    private String waitEventType;       // Lock, IO, etc.
    private String blockedBy;           // PID процесса, который блокирует
    private Long queryDurationMs;       // Длительность выполнения текущего запроса
    private Long rowsReturned;          // Количество возвращенных строк
    private Long ioTimeMs;              // Время ввода-вывода
    private String severity;            // INFO, WARNING, CRITICAL
    private Long pid;                   // Process ID
    private String applicationName;     // Имя приложения
    private String clientAddr;          // IP адрес клиента
    private String database;            // Название базы данных
    private String userName;            // Имя пользователя
    private Boolean isBlocking;         // Блокирует ли других
}