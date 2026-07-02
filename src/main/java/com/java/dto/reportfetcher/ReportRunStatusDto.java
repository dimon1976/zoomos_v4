package com.java.dto.reportfetcher;

public record ReportRunStatusDto(Long runId, String status, String errorMessage) {
}
