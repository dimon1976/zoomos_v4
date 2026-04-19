package com.java.dto.zoomos;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Конкретная проблема сайта: тип (StatusReason) + форматированное сообщение с параметрами.
 * shortLabel() — короткий текст для свёрнутой строки, message — полное сообщение для развёрнутого вида.
 */
public record SiteIssue(StatusReason reason, String message) {
    @JsonProperty("level")
    public ZoomosResultLevel level() {
        return reason.level;
    }

    /** Короткий ярлык для свёрнутой строки карточки — без цифр и деталей. */
    @JsonProperty("shortLabel")
    public String shortLabel() {
        return reason.shortLabel;
    }
}
