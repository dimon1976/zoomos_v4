package com.java.service.reportfetcher;

import com.java.dto.reportfetcher.ReportConfigDto;
import com.java.dto.reportfetcher.ReportOutputColumnDto;
import com.java.model.entity.FileMetadata;
import com.java.model.entity.ReportConfig;
import com.java.model.entity.ReportOutputColumn;
import com.java.model.entity.ReportRun;
import com.java.model.enums.ReportOutputColumnType;
import com.java.model.enums.ReportRunStatus;
import com.java.repository.ClientRepository;
import com.java.repository.ReportConfigRepository;
import com.java.repository.ReportRunRepository;
import com.java.util.FileReaderUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReportConfigService {

    private static final List<ReportRunStatus> ACTIVE_RUN_STATUSES =
            List.of(ReportRunStatus.PENDING, ReportRunStatus.DOWNLOADING, ReportRunStatus.TRANSFORMING);

    private final ReportConfigRepository reportConfigRepository;
    private final ClientRepository clientRepository;
    private final ReportRunRepository reportRunRepository;
    private final FileReaderUtils fileReaderUtils;

    @Value("${report-fetcher.lookup-file.dir:data/upload/report-fetcher-lookups}")
    private String lookupFileDir = "data/upload/report-fetcher-lookups";

    @Transactional
    public ReportConfig save(ReportConfigDto dto) {
        validateOutputColumns(dto.getOutputColumns());

        ReportConfig config;
        if (dto.getId() != null) {
            config = reportConfigRepository.findById(dto.getId())
                    .orElseThrow(() -> new IllegalArgumentException("Конфиг не найден: " + dto.getId()));
        } else {
            config = new ReportConfig();
        }

        config.setName(dto.getName());
        config.setSourceUrl(dto.getSourceUrl());
        config.setOutputFormat(dto.getOutputFormat());
        config.setRowFilterExpression(dto.getRowFilterExpression());
        config.setClient(dto.getClientId() != null
                ? clientRepository.findById(dto.getClientId()).orElse(null)
                : null);

        config.getOutputColumns().clear();
        int position = 0;
        for (ReportOutputColumnDto columnDto : dto.getOutputColumns()) {
            ReportOutputColumn column = ReportOutputColumn.builder()
                    .config(config)
                    .type(columnDto.getType())
                    .outputHeaderName(columnDto.getOutputHeaderName())
                    .position(position++)
                    .included(columnDto.getIncluded() != null ? columnDto.getIncluded() : true)
                    .sourceColumnName(columnDto.getSourceColumnName())
                    .formula(columnDto.getFormula())
                    .keyColumnInReport(columnDto.getKeyColumnInReport())
                    .keyColumnInLookup(columnDto.getKeyColumnInLookup())
                    .valueColumnInLookup(columnDto.getValueColumnInLookup())
                    .build();
            config.getOutputColumns().add(column);
        }

        return reportConfigRepository.save(config);
    }

    private void validateOutputColumns(List<ReportOutputColumnDto> columns) {
        Set<String> lookupKeys = columns.stream()
                .filter(c -> c.getType() == ReportOutputColumnType.LOOKUP)
                .map(ReportOutputColumnDto::getKeyColumnInLookup)
                .collect(Collectors.toSet());
        if (lookupKeys.size() > 1) {
            throw new IllegalArgumentException(
                    "Все LOOKUP-колонки конфига должны использовать одну и ту же колонку-ключ справочника");
        }
    }

    public void attachLookupFile(Long configId, MultipartFile file) throws IOException {
        ReportConfig config = getEntity(configId);

        Path dir = Path.of(lookupFileDir);
        Files.createDirectories(dir);
        String storedName = configId + "_" + UUID.randomUUID() + extractExtension(file.getOriginalFilename());
        Path storedPath = dir.resolve(storedName);
        file.transferTo(storedPath);

        config.setLookupFileOriginalName(file.getOriginalFilename());
        config.setLookupFileStoredPath(storedPath.toString());
        config.setLookupFileFormat(detectFormat(file.getOriginalFilename()));
        config.setLookupFileDelimiter(";");
        config.setLookupFileEncoding("UTF-8");
        reportConfigRepository.save(config);
    }

    public List<String> getLookupFileColumns(Long configId) throws IOException {
        ReportConfig config = getEntity(configId);
        if (config.getLookupFileStoredPath() == null) {
            return List.of();
        }
        FileMetadata metadata = FileMetadata.builder()
                .originalFilename(config.getLookupFileOriginalName())
                .fileFormat(config.getLookupFileFormat())
                .tempFilePath(config.getLookupFileStoredPath())
                .detectedDelimiter(config.getLookupFileDelimiter())
                .detectedEncoding(config.getLookupFileEncoding())
                .detectedQuoteChar("\"")
                .hasHeader(true)
                .build();
        List<List<String>> rows = fileReaderUtils.readAllRows(metadata);
        return rows.isEmpty() ? List.of() : rows.get(0);
    }

    private String detectFormat(String filename) {
        String lower = filename.toLowerCase();
        if (lower.endsWith(".xlsx")) return "XLSX";
        if (lower.endsWith(".xls")) return "XLS";
        return "CSV";
    }

    private String extractExtension(String filename) {
        if (filename == null) {
            return "";
        }
        int dotIndex = filename.lastIndexOf('.');
        return dotIndex >= 0 ? filename.substring(dotIndex) : "";
    }

    public List<ReportConfig> findAll(Long clientId) {
        return clientId != null
                ? reportConfigRepository.findAllByClientIdOrderByNameAsc(clientId)
                : reportConfigRepository.findAllByOrderByNameAsc();
    }

    public ReportConfig getEntity(Long id) {
        return reportConfigRepository.findByIdWithClientAndOutputColumns(id)
                .orElseThrow(() -> new IllegalArgumentException("Конфиг не найден: " + id));
    }

    public ReportConfigDto toDto(Long id) {
        ReportConfigDto dto = ReportConfigDto.fromEntity(getEntity(id));
        try {
            dto.setLookupFileColumns(getLookupFileColumns(id));
        } catch (IOException e) {
            log.warn("Не удалось прочитать колонки файла-справочника для конфига {}: {}", id, e.getMessage());
        }
        return dto;
    }

    @Transactional
    public void delete(Long id) {
        ReportConfig config = getEntity(id);
        if (reportRunRepository.existsByConfigIdAndStatusIn(id, ACTIVE_RUN_STATUSES)) {
            throw new IllegalStateException("Нельзя удалить конфиг с активным запуском");
        }

        // Удаляем все файлы результатов запусков этого конфига — сами записи ReportRun
        // будут удалены каскадно на уровне БД (ON DELETE CASCADE), но файлы на диске
        // каскад не трогает и без этого остались бы висеть без ссылок.
        for (ReportRun run : reportRunRepository.findAllByConfigIdOrderByCreatedAtDesc(id)) {
            deleteFileQuietly(run.getResultFilePath());
        }
        deleteFileQuietly(config.getLookupFileStoredPath());

        reportConfigRepository.delete(config);
    }

    private void deleteFileQuietly(String path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(Path.of(path));
        } catch (IOException e) {
            log.warn("Не удалось удалить файл {}: {}", path, e.getMessage());
        }
    }
}
