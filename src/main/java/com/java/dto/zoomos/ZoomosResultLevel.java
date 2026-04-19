package com.java.dto.zoomos;

/** Уровни статуса результата проверки. Чем меньше priority, тем выше приоритет. */
public enum ZoomosResultLevel {
    CRITICAL(0),
    WARNING(1),
    TREND(2),
    IN_PROGRESS(3),
    OK(4);

    public final int priority;

    ZoomosResultLevel(int priority) {
        this.priority = priority;
    }
}
