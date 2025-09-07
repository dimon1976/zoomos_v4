package com.java.service.utils.redirect;

import com.java.model.utils.PageStatus;
import com.java.model.utils.RedirectResult;

public interface RedirectStrategy {
    RedirectResult followRedirects(String url, int maxRedirects, int timeoutMs);
    boolean canHandle(String url, PageStatus previousStatus);
    int getPriority();
    String getStrategyName();
}