package com.java.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.ZonedDateTime;

/**
 * Сущность для хранения статистики работы стратегий редиректов
 */
@Entity
@Table(name = "redirect_statistics")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class RedirectStatistics {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @Column(name = "original_url", nullable = false, length = 2000)
    private String originalUrl;

    @Column(name = "final_url", nullable = false, length = 2000)
    private String finalUrl;

    @Column(name = "strategy_name", nullable = false, length = 50)
    private String strategyName;

    @Column(name = "status", nullable = false, length = 50)
    private String status;

    @Column(name = "redirect_count", nullable = false)
    @Builder.Default
    private Integer redirectCount = 0;

    @Column(name = "processing_time_ms", nullable = false)
    @Builder.Default
    private Long processingTimeMs = 0L;

    @Column(name = "success", nullable = false)
    @Builder.Default
    private Boolean success = false;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Column(name = "domain", length = 255)
    private String domain;

    @Column(name = "http_status_code")
    private Integer httpStatusCode;

    @Column(name = "is_blocked", nullable = false)
    @Builder.Default
    private Boolean isBlocked = false;

    @Column(name = "retry_count")
    @Builder.Default
    private Integer retryCount = 0;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private ZonedDateTime createdAt;

    // Бизнес-методы
    public static RedirectStatistics fromResult(String originalUrl, String strategyName, 
            String status, Integer redirectCount, Long processingTime) {
        String domain = extractDomain(originalUrl);
        boolean success = "SUCCESS".equals(status);
        boolean isBlocked = status.contains("BLOCKED") || status.contains("403") || status.contains("429");
        
        return RedirectStatistics.builder()
                .originalUrl(originalUrl)
                .finalUrl(originalUrl) // будет обновлен позже
                .strategyName(strategyName)
                .status(status)
                .redirectCount(redirectCount)
                .processingTimeMs(processingTime)
                .success(success)
                .domain(domain)
                .isBlocked(isBlocked)
                .build();
    }

    private static String extractDomain(String url) {
        try {
            if (url == null || url.isEmpty()) return null;
            
            // Убираем протокол
            String withoutProtocol = url.replaceFirst("^https?://", "");
            
            // Берем только доменную часть до первого слеша
            int slashIndex = withoutProtocol.indexOf('/');
            String domain = slashIndex > 0 ? withoutProtocol.substring(0, slashIndex) : withoutProtocol;
            
            // Убираем порт если есть
            int colonIndex = domain.lastIndexOf(':');
            if (colonIndex > 0 && colonIndex > domain.lastIndexOf(']')) { // IPv6 проверка
                domain = domain.substring(0, colonIndex);
            }
            
            return domain.toLowerCase();
        } catch (Exception e) {
            return null;
        }
    }
}