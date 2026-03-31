package com.java.dto.zoomos;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Результат оценки группы выкачек: статус и готовые issue-сообщения.
 * Возвращается из {@link com.java.service.ZoomosCheckService#evaluateAndBuildIssues}.
 */
public record GroupEvalResult(String status, List<Map<String, Object>> issues) {

    public static GroupEvalResult ok() {
        return new GroupEvalResult("OK", Collections.emptyList());
    }
}
