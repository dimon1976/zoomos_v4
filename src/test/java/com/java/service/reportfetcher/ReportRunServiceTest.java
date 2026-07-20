package com.java.service.reportfetcher;

import com.java.model.entity.ReportConfig;
import com.java.model.entity.ReportRun;
import com.java.model.enums.ReportRunStatus;
import com.java.repository.ReportConfigRepository;
import com.java.repository.ReportRunRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ReportRunServiceTest {

    private ReportRunRepository reportRunRepository;
    private ReportConfigRepository reportConfigRepository;
    private ReportRunExecutorService executorService;
    private ReportRunService reportRunService;

    @BeforeEach
    void setUp() {
        reportRunRepository = mock(ReportRunRepository.class);
        reportConfigRepository = mock(ReportConfigRepository.class);
        executorService = mock(ReportRunExecutorService.class);
        reportRunService = new ReportRunService(reportRunRepository, reportConfigRepository, executorService);

        when(reportConfigRepository.findById(1L))
                .thenReturn(Optional.of(ReportConfig.builder().id(1L).name("Test").build()));
        when(reportRunRepository.save(any(ReportRun.class))).thenAnswer(inv -> {
            ReportRun run = inv.getArgument(0);
            run.setId(100L);
            return run;
        });
    }

    @Test
    void shouldCreatePendingRunAndDispatchExecution() {
        when(reportRunRepository.existsByConfigIdAndStatusIn(eq(1L), anyList())).thenReturn(false);

        ReportRun run = reportRunService.startRun(1L);

        assertEquals(ReportRunStatus.PENDING, run.getStatus());
        verify(executorService).execute(100L);
    }

    @Test
    void shouldRejectStartWhenActiveRunExists() {
        when(reportRunRepository.existsByConfigIdAndStatusIn(eq(1L), anyList())).thenReturn(true);

        assertThrows(IllegalStateException.class, () -> reportRunService.startRun(1L));
        verify(executorService, never()).execute(anyLong());
    }

    @Test
    void shouldThrowWhenConfigMissing() {
        when(reportConfigRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> reportRunService.startRun(99L));
    }
}
