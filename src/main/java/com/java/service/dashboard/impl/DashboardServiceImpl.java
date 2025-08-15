package com.java.service.dashboard.impl;

import com.java.dto.DashboardFilterDto;
import com.java.dto.DashboardOperationDto;
import com.java.dto.DashboardStatsDto;
import com.java.mapper.DashboardMapper;
import com.java.model.FileOperation;
import com.java.repository.ClientRepository;
import com.java.repository.FileOperationRepository;
import com.java.service.dashboard.DashboardService;
import com.java.util.PathResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringBootVersion;
import org.springframework.core.env.Environment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.criteria.Predicate;
import java.lang.management.ManagementFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class DashboardServiceImpl implements DashboardService {

    private final FileOperationRepository fileOperationRepository;
    private final ClientRepository clientRepository;
    private final DashboardMapper dashboardMapper;
    private final Environment environment;
    private final PathResolver pathResolver;

    @Override
    @Transactional(readOnly = true)
    public DashboardStatsDto getDashboardStats() {
        log.debug("Получение статистики дашборда");

        // Основные счетчики
        Long totalOperations = fileOperationRepository.count();
        Long totalClients = clientRepository.count();

        // Статистика по статусам
        Long activeOperations = fileOperationRepository.countByStatusIn(
                List.of(FileOperation.OperationStatus.PENDING, FileOperation.OperationStatus.PROCESSING)
        );
        Long completedOperations = fileOperationRepository.countByStatus(FileOperation.OperationStatus.COMPLETED);
        Long failedOperations = fileOperationRepository.countByStatus(FileOperation.OperationStatus.FAILED);

        // Статистика по типам операций
        Long importOperations = fileOperationRepository.countByOperationType(FileOperation.OperationType.IMPORT);
        Long exportOperations = fileOperationRepository.countByOperationType(FileOperation.OperationType.EXPORT);
        Long processOperations = fileOperationRepository.countByOperationType(FileOperation.OperationType.PROCESS);

        // Файловая статистика
        FileStats fileStats = getFileStats();
        DirectoryStats dirStats = getDirectorySizes();

        // Статистика за периоды
        PeriodStats periodStats = getPeriodStats();

        // Производительность
        PerformanceStats performanceStats = getPerformanceStats();

        // Системная информация
        DashboardStatsDto.SystemInfoDto systemInfo = getSystemInfo();

        return DashboardStatsDto.builder()
                .totalOperations(totalOperations)
                .totalClients(totalClients)
                .activeOperations(activeOperations)
                .completedOperations(completedOperations)
                .failedOperations(failedOperations)
                .importOperations(importOperations)
                .exportOperations(exportOperations)
                .processOperations(processOperations)
                .totalFilesProcessed(fileStats.totalFiles())
                .totalFileSizeBytes(dirStats.totalSizeBytes())
                .totalFileSizeFormatted(dirStats.totalSizeFormatted())
                .tempDirSizeBytes(dirStats.tempSizeBytes())
                .tempDirSizeFormatted(dirStats.tempSizeFormatted())
                .importDirSizeBytes(dirStats.importSizeBytes())
                .importDirSizeFormatted(dirStats.importSizeFormatted())
                .exportDirSizeBytes(dirStats.exportSizeBytes())
                .exportDirSizeFormatted(dirStats.exportSizeFormatted())
                .totalRecordsProcessed(fileStats.totalRecords())
                .operationsToday(periodStats.today())
                .operationsThisWeek(periodStats.thisWeek())
                .operationsThisMonth(periodStats.thisMonth())
                .averageProcessingTimeMinutes(performanceStats.avgProcessingTime())
                .successRate(performanceStats.successRate())
                .lastUpdateTime(ZonedDateTime.now())
                .topClientByOperations(getTopClient())
                .mostUsedFileType(getMostUsedFileType())
                .systemInfo(systemInfo)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<DashboardOperationDto> getFilteredOperations(DashboardFilterDto filter) {
        log.debug("Получение операций с фильтрами: {}", filter);

        Specification<FileOperation> spec = createSpecification(filter);

        Sort sort = Sort.by(
                "desc".equalsIgnoreCase(filter.getDirection()) ? Sort.Direction.DESC : Sort.Direction.ASC,
                filter.getSort()
        );

        Pageable pageable = PageRequest.of(filter.getPage(), filter.getSize(), sort);

        Page<FileOperation> operations = fileOperationRepository.findAll(spec, pageable);

        return operations.map(dashboardMapper::toOperationDto);
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> getAvailableFileTypes() {
        return fileOperationRepository.findDistinctFileTypes();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ClientStatsDto> getClientStats() {
        return fileOperationRepository.getClientStatistics();
    }

    private Specification<FileOperation> createSpecification(DashboardFilterDto filter) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (filter.getClientId() != null) {
                predicates.add(criteriaBuilder.equal(root.get("client").get("id"), filter.getClientId()));
            }

            if (filter.getOperationType() != null) {
                predicates.add(criteriaBuilder.equal(root.get("operationType"), filter.getOperationType()));
            }

            if (filter.getStatus() != null) {
                predicates.add(criteriaBuilder.equal(root.get("status"), filter.getStatus()));
            }

            if (filter.getDateFrom() != null) {
                ZonedDateTime from = filter.getDateFrom().atStartOfDay(ZoneId.systemDefault());
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("startedAt"), from));
            }

            if (filter.getDateTo() != null) {
                ZonedDateTime to = filter.getDateTo().plusDays(1).atStartOfDay(ZoneId.systemDefault());
                predicates.add(criteriaBuilder.lessThan(root.get("startedAt"), to));
            }

            if (filter.getFileName() != null && !filter.getFileName().trim().isEmpty()) {
                predicates.add(criteriaBuilder.like(
                        criteriaBuilder.lower(root.get("fileName")),
                        "%" + filter.getFileName().toLowerCase() + "%"
                ));
            }

            if (filter.getFileType() != null && !filter.getFileType().trim().isEmpty()) {
                predicates.add(criteriaBuilder.equal(root.get("fileType"), filter.getFileType()));
            }

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }

    private FileStats getFileStats() {
        Long totalFiles = fileOperationRepository.countNonNullFileSize();
        Long totalSizeBytes = fileOperationRepository.sumFileSize();
        Long totalRecords = fileOperationRepository.sumRecordCount();

        if (totalSizeBytes == null) totalSizeBytes = 0L;

        String formattedSize = formatFileSize(totalSizeBytes);

        return new FileStats(
                totalFiles != null ? totalFiles : 0L,
                totalSizeBytes,
                formattedSize,
                totalRecords != null ? totalRecords : 0L
        );
    }

    private DirectoryStats getDirectorySizes() {
        long tempSize = getDirectorySize(pathResolver.getAbsoluteTempDir());
        long importSize = getDirectorySize(pathResolver.getAbsoluteImportDir());
        long exportSize = getDirectorySize(pathResolver.getAbsoluteExportDir());
        long total = tempSize + importSize + exportSize;

        return new DirectoryStats(
                tempSize,
                importSize,
                exportSize,
                total,
                formatFileSize(tempSize),
                formatFileSize(importSize),
                formatFileSize(exportSize),
                formatFileSize(total)
        );
    }

    private long getDirectorySize(Path path) {
        if (path == null || !Files.exists(path)) {
            return 0L;
        }
        try {
            return Files.walk(path)
                    .filter(Files::isRegularFile)
                    .mapToLong(p -> {
                        try {
                            return Files.size(p);
                        } catch (IOException e) {
                            log.warn("Не удалось получить размер файла {}: {}", p, e.getMessage());
                            return 0L;
                        }
                    })
                    .sum();
        } catch (IOException e) {
            log.warn("Не удалось получить размер директории {}: {}", path, e.getMessage());
            return 0L;
        }
    }

    private PeriodStats getPeriodStats() {
        ZonedDateTime now = ZonedDateTime.now();
        ZonedDateTime startOfDay = now.toLocalDate().atStartOfDay(now.getZone());
        ZonedDateTime startOfWeek = startOfDay.minusDays(now.getDayOfWeek().getValue() - 1);
        ZonedDateTime startOfMonth = now.toLocalDate().withDayOfMonth(1).atStartOfDay(now.getZone());

        Long today = fileOperationRepository.countByStartedAtGreaterThanEqual(startOfDay);
        Long thisWeek = fileOperationRepository.countByStartedAtGreaterThanEqual(startOfWeek);
        Long thisMonth = fileOperationRepository.countByStartedAtGreaterThanEqual(startOfMonth);

        return new PeriodStats(today, thisWeek, thisMonth);
    }

    private PerformanceStats getPerformanceStats() {
        Double avgProcessingTime = fileOperationRepository.getAverageProcessingTimeMinutes();
        Long completedCount = fileOperationRepository.countByStatus(FileOperation.OperationStatus.COMPLETED);
        Long totalCount = fileOperationRepository.count();

        Double successRate = totalCount > 0 ? (completedCount.doubleValue() / totalCount) * 100 : 0.0;

        return new PerformanceStats(
                avgProcessingTime != null ? avgProcessingTime : 0.0,
                Math.round(successRate * 100.0) / 100.0
        );
    }

    private DashboardStatsDto.SystemInfoDto getSystemInfo() {
        Runtime runtime = Runtime.getRuntime();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = totalMemory - freeMemory;

        long uptimeMs = ManagementFactory.getRuntimeMXBean().getUptime();
        long uptimeMinutes = uptimeMs / (1000 * 60);

        return DashboardStatsDto.SystemInfoDto.builder()
                .javaVersion(System.getProperty("java.version"))
                .springBootVersion(SpringBootVersion.getVersion())
                .totalMemoryMb(totalMemory / (1024 * 1024))
                .usedMemoryMb(usedMemory / (1024 * 1024))
                .freeMemoryMb(freeMemory / (1024 * 1024))
                .operatingSystem(System.getProperty("os.name"))
                .databaseUrl(environment.getProperty("spring.datasource.url"))
                .uptimeMinutes(uptimeMinutes)
                .build();
    }

    private String getTopClient() {
        return fileOperationRepository.findTopClientByOperationCount()
                .orElse("Нет данных");
    }

    private String getMostUsedFileType() {
        return fileOperationRepository.findMostUsedFileType()
                .orElse("Нет данных");
    }

    private String formatFileSize(Long sizeInBytes) {
        if (sizeInBytes == null || sizeInBytes == 0) {
            return "0 Б";
        }

        final String[] units = {"Б", "КБ", "МБ", "ГБ", "ТБ"};
        int unitIndex = 0;
        double size = sizeInBytes.doubleValue();

        while (size >= 1024 && unitIndex < units.length - 1) {
            size /= 1024;
            unitIndex++;
        }

        return String.format("%.1f %s", size, units[unitIndex]);
    }

    // Внутренние record классы для статистики
    private record FileStats(Long totalFiles, Long totalSizeBytes, String totalSizeFormatted, Long totalRecords) {}
    private record PeriodStats(Long today, Long thisWeek, Long thisMonth) {}
    private record PerformanceStats(Double avgProcessingTime, Double successRate) {}
    private record DirectoryStats(long tempSizeBytes, long importSizeBytes, long exportSizeBytes,
                                  long totalSizeBytes, String tempSizeFormatted,
                                  String importSizeFormatted, String exportSizeFormatted,
                                  String totalSizeFormatted) {}
}