package com.java.dto.zoomos;

/** Причина статуса — тип проблемы, уровень, короткий ярлык и шаблон сообщения. */
public enum StatusReason {
    NOT_FOUND          (ZoomosResultLevel.CRITICAL,    "Нет данных за период",                   "Нет данных за период"),
    DEADLINE_MISSED    (ZoomosResultLevel.CRITICAL,    "Выкачка не завершена, дедлайн прошёл",   "Дедлайн пропущен"),
    STALLED            (ZoomosResultLevel.CRITICAL,    "Выкачка зависла (нет обновлений {N} мин)","Выкачка зависла"),
    STOCK_ZERO         (ZoomosResultLevel.CRITICAL,    "В наличии = 0",                           "В наличии = 0"),
    STOCK_DROP         (ZoomosResultLevel.CRITICAL,    "В наличии упало на {N}% (порог {T}%)",   "В наличии упало"),
    CITIES_MISSING     (ZoomosResultLevel.CRITICAL,    "Не все города выкачались: ожидалось {expected}, получено {actual}", "Не все города выкачались"),
    ACCOUNT_MISSING    (ZoomosResultLevel.CRITICAL,    "Нет выкачки с нужным аккаунтом ({account})", "Нет выкачки с нужным аккаунтом"),
    CATEGORY_MISSING   (ZoomosResultLevel.CRITICAL,    "Нужная категория не найдена в парсере",  "Категория не найдена"),
    NO_PRODUCTS        (ZoomosResultLevel.CRITICAL,    "Количество товаров = 0",                  "Нет товаров"),
    STOCK_TREND_DOWN   (ZoomosResultLevel.WARNING,     "inStock снижается {N} дней подряд",      "inStock снижается"),
    ERROR_GROWTH       (ZoomosResultLevel.WARNING,     "Ошибок парсинга больше baseline на {N}%","Ошибок парсинга больше нормы"),
    SPEED_TREND        (ZoomosResultLevel.TREND,       "Выкачка замедляется: {from} → {to} мин/1000 тов",                                   "Выкачка замедляется"),
    SPEED_SPIKE        (ZoomosResultLevel.TREND,       "Разовое замедление: {current} мин/1000 тов (baseline {baseline} мин/1000 тов)",  "Разовое замедление скорости"),
    IN_PROGRESS_OK     (ZoomosResultLevel.IN_PROGRESS, "Идёт, ожидаемое завершение {time}",      "В процессе"),
    IN_PROGRESS_RISK   (ZoomosResultLevel.CRITICAL,    "Идёт, не успеет к дедлайну {deadline}",  "Не успеет к дедлайну");

    public final ZoomosResultLevel level;
    public final String messageTemplate;
    public final String shortLabel;

    StatusReason(ZoomosResultLevel level, String messageTemplate, String shortLabel) {
        this.level           = level;
        this.messageTemplate = messageTemplate;
        this.shortLabel      = shortLabel;
    }
}
