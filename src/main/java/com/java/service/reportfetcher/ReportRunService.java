package com.java.service.reportfetcher;

import com.java.model.entity.ReportConfig;
import com.java.model.entity.ReportRun;
import com.java.model.enums.ReportRunStatus;
import com.java.model.enums.ReportRunTrigger;
import com.java.repository.ReportConfigRepository;
import com.java.repository.ReportRunRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ReportRunService {

    private static final List<ReportRunStatus> ACTIVE_STATUSES =
            List.of(ReportRunStatus.PENDING, ReportRunStatus.DOWNLOADING, ReportRunStatus.TRANSFORMING);

    private final ReportRunRepository reportRunRepository;
    private final ReportConfigRepository reportConfigRepository;
    private final ReportRunExecutorService executorService;

    public ReportRun startRun(Long configId) {
        ReportConfig config = reportConfigRepository.findById(configId)
                .orElseThrow(() -> new IllegalArgumentException("Конфиг не найден: " + configId));

        if (reportRunRepository.existsByConfigIdAndStatusIn(configId, ACTIVE_STATUSES)) {
            throw new IllegalStateException("По этому конфигу уже выполняется запуск");
        }

        ReportRun run = ReportRun.builder()
                .config(config)
                .status(ReportRunStatus.PENDING)
                .triggeredBy(ReportRunTrigger.MANUAL)
                .build();
        run = reportRunRepository.save(run);

        executorService.execute(run.getId());
        return run;
    }
}
