package com.java.model.utils;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class RedirectProcessingRequest {
    private List<RedirectUrlData> urls;
    private int maxRedirects;
    private int timeoutMs;
    private int delayMs; // Задержка между запросами
    private boolean includeId;
    private boolean includeModel;
    private String idColumnName;
    private String modelColumnName;
}