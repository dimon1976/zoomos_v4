package com.java.service.reportfetcher;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java.dto.reportfetcher.ReportRunStatusDto;
import com.java.model.entity.ReportConfig;
import com.java.model.entity.ReportRun;
import com.java.model.enums.ReportRunStatus;
import com.java.repository.ReportConfigRepository;
import com.java.repository.ReportRunRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.ZonedDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReportRunExecutorService {

    private final ReportRunRepository reportRunRepository;
    private final ReportConfigRepository reportConfigRepository;
    private final ReportDownloadService downloadService;
    private final ReportTransformService transformService;
    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;

    @Async("reportFetchExecutor")
    public void execute(Long runId) {
        ReportRun run;
        try {
            run = reportRunRepository.findByIdWithConfigAndOutputColumns(runId)
                    .orElseThrow(() -> new IllegalStateException("ReportRun не найден: " + runId));
        } catch (Exception e) {
            log.error("Не удалось загрузить ReportRun {}: {}", runId, e.getMessage(), e);
            return;
        }

        ReportDownloadService.ReportDownloadResult downloaded = null;
        try {
            ReportConfig config = run.getConfig();
            run.setStartedAt(ZonedDateTime.now());
            updateStatus(run, ReportRunStatus.DOWNLOADING, null);

            downloaded = downloadService.download(config.getSourceUrl());

            updateStatus(run, ReportRunStatus.TRANSFORMING, null);

            ReportTransformService.ReportTransformResult result = transformService.transform(
                    downloaded.filePath(),
                    "report." + downloaded.format().toLowerCase(),
                    downloaded.format(),
                    config);

            config.setDetectedReportColumns(toJson(result.reportHeaders()));
            reportConfigRepository.save(config);

            run.setResultFilePath(result.resultFilePath().toString());
            run.setOriginalFileName(downloaded.originalFileName());
            run.setFinishedAt(ZonedDateTime.now());
            updateStatus(run, ReportRunStatus.DONE, null);
        } catch (Exception e) {
            log.error("Ошибка выполнения ReportRun {}: {}", runId, e.getMessage(), e);
            run.setFinishedAt(ZonedDateTime.now());
            String errorMessage = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            updateStatus(run, ReportRunStatus.ERROR, errorMessage);
        } finally {
            if (downloaded != null) {
                try {
                    java.nio.file.Files.deleteIfExists(downloaded.filePath());
                } catch (java.io.IOException e) {
                    log.warn("Не удалось удалить временный файл {}: {}", downloaded.filePath(), e.getMessage());
                }
            }
        }
    }

    private void updateStatus(ReportRun run, ReportRunStatus status, String errorMessage) {
        run.setStatus(status);
        run.setErrorMessage(errorMessage);
        reportRunRepository.save(run);
        messagingTemplate.convertAndSend("/topic/report-fetcher/" + run.getId(),
                new ReportRunStatusDto(run.getId(), status.name(), errorMessage));
    }

    private String toJson(List<String> headers) {
        try {
            return objectMapper.writeValueAsString(headers);
        } catch (Exception e) {
            log.warn("Не удалось сериализовать detectedReportColumns: {}", e.getMessage());
            return "[]";
        }
    }
}
