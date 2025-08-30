package com.java.service.maintenance;

import com.java.dto.*;
import com.java.service.CleanupService;
import com.java.util.PathResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
@Slf4j
@RequiredArgsConstructor
public class FileManagementService {

    private final PathResolver pathResolver;
    private final CleanupService cleanupService;

    @Value("${file.management.archive.enabled:true}")
    private boolean archiveEnabled;

    @Value("${file.management.duplicate.scan.enabled:true}")
    private boolean duplicateScanEnabled;

    @Value("${file.management.archive.max.size.gb:5}")
    private int maxArchiveSizeGb;

    public List<DirectoryStatsDto> analyzeDiskSpace() {
        log.info("Запуск анализа дискового пространства");
        
        List<DirectoryStatsDto> stats = new ArrayList<>();
        
        try {
            stats.add(analyzeDirectory("temp", pathResolver.getAbsoluteTempDir()));
            stats.add(analyzeDirectory("upload", pathResolver.getAbsoluteUploadDir()));
            stats.add(analyzeDirectory("export", pathResolver.getAbsoluteExportDir()));
            stats.add(analyzeDirectory("import", pathResolver.getAbsoluteImportDir()));
            
            log.info("Анализ дискового пространства завершен. Проанализировано {} директорий", stats.size());
            return stats;
            
        } catch (Exception e) {
            log.error("Ошибка при анализе дискового пространства", e);
            return Collections.emptyList();
        }
    }

    private DirectoryStatsDto analyzeDirectory(String name, Path directory) {
        DirectoryStatsDto stats = new DirectoryStatsDto();
        stats.setDirectoryName(name);
        stats.setRelativePath(directory.toString());
        
        if (!Files.exists(directory)) {
            stats.setTotalSizeBytes(0);
            stats.setFileCount(0);
            stats.setUsagePercentage(0.0);
            stats.setFormattedSize("0 B");
            return stats;
        }

        AtomicLong totalSize = new AtomicLong(0);
        AtomicInteger fileCount = new AtomicInteger(0);
        
        try {
            Files.walkFileTree(directory, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    totalSize.addAndGet(attrs.size());
                    fileCount.incrementAndGet();
                    return FileVisitResult.CONTINUE;
                }
            });

            stats.setTotalSizeBytes(totalSize.get());
            stats.setFileCount(fileCount.get());
            stats.setFormattedSize(formatBytes(totalSize.get()));
            stats.setUsagePercentage(calculateUsagePercentage(totalSize.get()));
            
            if (fileCount.get() > 0) {
                stats.setLastModified(getLastModifiedTime(directory));
            }

        } catch (IOException e) {
            log.warn("Не удалось проанализировать директорию {}: {}", directory, e.getMessage());
            stats.setTotalSizeBytes(0);
            stats.setFileCount(0);
            stats.setUsagePercentage(0.0);
            stats.setFormattedSize("0 B");
        }

        return stats;
    }

    public ArchiveResultDto archiveOldFiles(int olderThanDays) {
        log.info("Запуск архивирования файлов старше {} дней", olderThanDays);
        
        if (!archiveEnabled) {
            log.info("Архивирование отключено в конфигурации");
            return createArchiveResult(0, 0, null, false, "Архивирование отключено");
        }

        try {
            Path archiveDir = pathResolver.getAbsoluteUploadDir().resolve("archive");
            Files.createDirectories(archiveDir);

            String archiveName = "archive_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")) + ".zip";
            Path archivePath = archiveDir.resolve(archiveName);

            List<Path> filesToArchive = findOldFiles(olderThanDays);
            
            if (filesToArchive.isEmpty()) {
                log.info("Не найдено файлов для архивирования");
                return createArchiveResult(0, 0, null, true, null);
            }

            long totalSize = createZipArchive(archivePath, filesToArchive);
            
            for (Path file : filesToArchive) {
                Files.deleteIfExists(file);
            }

            log.info("Архивирование завершено. Файлов: {}, размер: {} байт", filesToArchive.size(), totalSize);
            return createArchiveResult(filesToArchive.size(), totalSize, archivePath.toString(), true, null);

        } catch (Exception e) {
            log.error("Ошибка при архивировании файлов", e);
            return createArchiveResult(0, 0, null, false, e.getMessage());
        }
    }

    public List<DuplicateFileDto> findDuplicateFiles() {
        log.info("Запуск поиска дублирующихся файлов");
        
        if (!duplicateScanEnabled) {
            log.info("Поиск дубликатов отключен в конфигурации");
            return Collections.emptyList();
        }

        try {
            Map<String, List<Path>> hashToFiles = new HashMap<>();
            
            List<Path> allDirectories = Arrays.asList(
                pathResolver.getAbsoluteUploadDir(),
                pathResolver.getAbsoluteExportDir(),
                pathResolver.getAbsoluteImportDir()
            );

            for (Path directory : allDirectories) {
                if (Files.exists(directory)) {
                    scanForDuplicates(directory, hashToFiles);
                }
            }

            List<DuplicateFileDto> duplicates = hashToFiles.entrySet().stream()
                .filter(entry -> entry.getValue().size() > 1)
                .map(this::createDuplicateFileDto)
                .collect(Collectors.toList());

            log.info("Поиск дубликатов завершен. Найдено {} групп дублирующихся файлов", duplicates.size());
            return duplicates;

        } catch (Exception e) {
            log.error("Ошибка при поиске дубликатов", e);
            return Collections.emptyList();
        }
    }

    public FileOperationStatsDto getFileOperationStats() {
        log.info("Сбор статистики файловых операций");
        
        FileOperationStatsDto stats = new FileOperationStatsDto();
        stats.setPeriodStart(LocalDateTime.now().minusDays(30));
        stats.setPeriodEnd(LocalDateTime.now());
        
        try {
            int importCount = countFilesInDirectory(pathResolver.getAbsoluteImportDir());
            int exportCount = countFilesInDirectory(pathResolver.getAbsoluteExportDir());
            
            stats.setTotalImports(importCount);
            stats.setTotalExports(exportCount);
            stats.setTotalOperations(importCount + exportCount);
            
            long averageSize = calculateAverageFileSize();
            stats.setAverageFileSizeBytes(averageSize);
            stats.setFormattedAverageSize(formatBytes(averageSize));
            
            log.info("Статистика собрана: импорт={}, экспорт={}, средний размер={}", 
                importCount, exportCount, formatBytes(averageSize));
            
            return stats;

        } catch (Exception e) {
            log.error("Ошибка при сборе статистики", e);
            return stats;
        }
    }

    public CleanupResultDto manualCleanup() {
        log.info("Запуск ручной очистки всех временных файлов");
        
        try {
            Path tempDir = pathResolver.getAbsoluteTempDir();
            
            if (!Files.exists(tempDir)) {
                return createCleanupResult(0, 0, 0, true, null);
            }

            AtomicInteger deletedFiles = new AtomicInteger(0);
            AtomicInteger deletedDirs = new AtomicInteger(0);
            AtomicLong freedSpace = new AtomicLong(0);

            Files.walkFileTree(tempDir, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    freedSpace.addAndGet(attrs.size());
                    Files.delete(file);
                    deletedFiles.incrementAndGet();
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    if (!dir.equals(tempDir)) {
                        Files.delete(dir);
                        deletedDirs.incrementAndGet();
                    }
                    return FileVisitResult.CONTINUE;
                }
            });

            log.info("Ручная очистка завершена. Удалено файлов: {}, папок: {}, освобождено: {} байт", 
                deletedFiles.get(), deletedDirs.get(), freedSpace.get());

            return createCleanupResult(deletedFiles.get(), freedSpace.get(), deletedDirs.get(), true, null);

        } catch (Exception e) {
            log.error("Ошибка при ручной очистке", e);
            return createCleanupResult(0, 0, 0, false, e.getMessage());
        }
    }

    private List<Path> findOldFiles(int olderThanDays) throws IOException {
        List<Path> oldFiles = new ArrayList<>();
        LocalDateTime cutoffTime = LocalDateTime.now().minusDays(olderThanDays);

        List<Path> searchDirectories = Arrays.asList(
            pathResolver.getAbsoluteExportDir(),
            pathResolver.getAbsoluteImportDir()
        );

        for (Path directory : searchDirectories) {
            if (Files.exists(directory)) {
                Files.walkFileTree(directory, new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                        if (attrs.lastModifiedTime().toInstant().isBefore(cutoffTime.atZone(java.time.ZoneId.systemDefault()).toInstant())) {
                            oldFiles.add(file);
                        }
                        return FileVisitResult.CONTINUE;
                    }
                });
            }
        }

        return oldFiles;
    }

    private long createZipArchive(Path archivePath, List<Path> files) throws IOException {
        long totalSize = 0;
        
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(archivePath))) {
            for (Path file : files) {
                String entryName = file.getFileName().toString();
                ZipEntry entry = new ZipEntry(entryName);
                zos.putNextEntry(entry);
                
                Files.copy(file, zos);
                zos.closeEntry();
                totalSize += Files.size(file);
            }
        }
        
        return totalSize;
    }

    private void scanForDuplicates(Path directory, Map<String, List<Path>> hashToFiles) throws IOException {
        Files.walkFileTree(directory, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                try {
                    String hash = calculateFileHash(file);
                    hashToFiles.computeIfAbsent(hash, k -> new ArrayList<>()).add(file);
                } catch (Exception e) {
                    log.warn("Не удалось вычислить хеш для файла {}: {}", file, e.getMessage());
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private String calculateFileHash(Path file) throws IOException, NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("MD5");
        byte[] buffer = new byte[8192];
        
        try (var inputStream = Files.newInputStream(file)) {
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                md.update(buffer, 0, bytesRead);
            }
        }
        
        byte[] hash = md.digest();
        StringBuilder sb = new StringBuilder();
        for (byte b : hash) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private DuplicateFileDto createDuplicateFileDto(Map.Entry<String, List<Path>> entry) {
        DuplicateFileDto dto = new DuplicateFileDto();
        dto.setHash(entry.getKey());
        dto.setFilePaths(entry.getValue().stream().map(Path::toString).collect(Collectors.toList()));
        dto.setDuplicateCount(entry.getValue().size());
        
        try {
            long fileSize = Files.size(entry.getValue().get(0));
            dto.setFileSizeBytes(fileSize);
            dto.setFormattedFileSize(formatBytes(fileSize));
            dto.setFileName(entry.getValue().get(0).getFileName().toString());
        } catch (IOException e) {
            dto.setFileSizeBytes(0);
            dto.setFormattedFileSize("Unknown");
            dto.setFileName("Unknown");
        }
        
        return dto;
    }

    private int countFilesInDirectory(Path directory) {
        if (!Files.exists(directory)) return 0;
        
        AtomicInteger count = new AtomicInteger(0);
        try {
            Files.walkFileTree(directory, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    count.incrementAndGet();
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            log.warn("Ошибка при подсчете файлов в директории {}: {}", directory, e.getMessage());
        }
        return count.get();
    }

    private long calculateAverageFileSize() {
        List<Path> allDirectories = Arrays.asList(
            pathResolver.getAbsoluteExportDir(),
            pathResolver.getAbsoluteImportDir()
        );

        AtomicLong totalSize = new AtomicLong(0);
        AtomicInteger fileCount = new AtomicInteger(0);

        for (Path directory : allDirectories) {
            if (Files.exists(directory)) {
                try {
                    Files.walkFileTree(directory, new SimpleFileVisitor<Path>() {
                        @Override
                        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                            totalSize.addAndGet(attrs.size());
                            fileCount.incrementAndGet();
                            return FileVisitResult.CONTINUE;
                        }
                    });
                } catch (IOException e) {
                    log.warn("Ошибка при расчете среднего размера файлов в {}: {}", directory, e.getMessage());
                }
            }
        }

        return fileCount.get() > 0 ? totalSize.get() / fileCount.get() : 0;
    }

    private LocalDateTime getLastModifiedTime(Path directory) {
        try {
            return Files.getLastModifiedTime(directory).toInstant()
                .atZone(java.time.ZoneId.systemDefault()).toLocalDateTime();
        } catch (IOException e) {
            return LocalDateTime.now();
        }
    }

    private double calculateUsagePercentage(long sizeBytes) {
        long totalSpace = pathResolver.getAbsoluteTempDir().toFile().getTotalSpace();
        return totalSpace > 0 ? (double) sizeBytes / totalSpace * 100 : 0.0;
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
    }

    private ArchiveResultDto createArchiveResult(int files, long size, String path, boolean success, String error) {
        ArchiveResultDto result = new ArchiveResultDto();
        result.setArchivedFiles(files);
        result.setTotalArchivedSizeBytes(size);
        result.setArchivePath(path);
        result.setSuccess(success);
        result.setErrorMessage(error);
        result.setArchiveTime(LocalDateTime.now());
        result.setFormattedArchivedSize(formatBytes(size));
        return result;
    }

    private CleanupResultDto createCleanupResult(int files, long freedSpace, int dirs, boolean success, String error) {
        CleanupResultDto result = new CleanupResultDto();
        result.setDeletedFiles(files);
        result.setFreedSpaceBytes(freedSpace);
        result.setDeletedDirectories(dirs);
        result.setSuccess(success);
        result.setErrorMessage(error);
        result.setCleanupTime(LocalDateTime.now());
        result.setFormattedFreedSpace(formatBytes(freedSpace));
        return result;
    }
}