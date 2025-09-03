package com.java.service.utils;

public interface HttpStrategy {
    RedirectCollectorService.RedirectResult execute(String originalUrl, int maxRedirects, int timeoutSeconds);
}
