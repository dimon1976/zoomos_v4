// src/main/java/com/java/util/PathResolver.java
package com.java.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * Утилита для работы с путями файлов
 */
@Component
@Slf4j
public class PathResolver {

    @Value("${application.upload.dir:data/upload}")
    private String uploadDir;

    @Value("${application.export.dir:data/upload/exports}")
    private String exportDir;

    @Value("${application.import.dir:data/upload/imports}")
    private String importDir;

    @Value("${application.temp.dir:data/temp}")
    private String tempDir;

    /**
     * Инициализация директорий при запуске
     */
    public void init() {
        try {
            Path tempDirPath = getAbsoluteTempDir();
            Path uploadDirPath = getAbsoluteUploadDir();
            Path exportDirPath = getAbsoluteExportDir();
            Path importDirPath = getAbsoluteImportDir();

            Files.createDirectories(tempDirPath);
            Files.createDirectories(uploadDirPath);
            Files.createDirectories(exportDirPath);
            Files.createDirectories(importDirPath);

            log.info("Инициализированы директории для файлов:");
            log.info("Временная директория: {}", tempDirPath);
            log.info("Директория загрузок: {}", uploadDirPath);
            log.info("Директория экспорта: {}", exportDirPath);
            log.info("Директория импорта: {}", importDirPath);
        } catch (IOException e) {
            log.error("Не удалось создать директории для файлов: {}", e.getMessage(), e);
        }
    }

    /**
     * Сохраняет загруженный файл во временную директорию
     */
    public Path saveToTempFile(MultipartFile file, String prefix) throws IOException {
        // Создаем временную директорию, если не существует
        Path tempDirPath = getAbsoluteTempDir();
        Files.createDirectories(tempDirPath);

        // Генерируем уникальное имя файла
        String originalFilename = file.getOriginalFilename();
        String filename = prefix + "_" + UUID.randomUUID() +
                (originalFilename != null ? getFileExtension(originalFilename) : "");

        Path tempFile = tempDirPath.resolve(filename);

        // Сохраняем файл
        Files.copy(file.getInputStream(), tempFile, StandardCopyOption.REPLACE_EXISTING);
        log.debug("Сохранен временный файл: {}", tempFile.toAbsolutePath());
        return tempFile;
    }

    /**
     * Создает пустой временный файл
     */
    public Path createTempFile(String prefix, String suffix) throws IOException {
        // Создаем временную директорию, если не существует
        Path tempDirPath = getAbsoluteTempDir();
        Files.createDirectories(tempDirPath);

        // Генерируем уникальное имя файла
        String filename = prefix + "_" + UUID.randomUUID() + suffix;
        Path tempFile = tempDirPath.resolve(filename);

        // Создаем пустой файл
        Files.createFile(tempFile);
        log.debug("Создан пустой временный файл: {}", tempFile.toAbsolutePath());

        return tempFile;
    }

    /**
     * Создает пустой временный файл с датой в имени
     */
    public Path createTempFileWithDate(String prefix, String suffix) throws IOException {
        // Создаем временную директорию, если не существует
        Path tempDirPath = getAbsoluteTempDir();
        Files.createDirectories(tempDirPath);

        // Генерируем уникальное имя файла с датой
        String date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String filename = prefix + "_" + date + "_" + UUID.randomUUID() + suffix;
        Path tempFile = tempDirPath.resolve(filename);

        // Создаем пустой файл
        Files.createFile(tempFile);
        log.debug("Создан пустой временный файл с датой: {}", tempFile.toAbsolutePath());

        return tempFile;
    }

    /**
     * Перемещает файл из временной директории в директорию экспорта
     */
    public Path moveFromTempToExport(Path tempFilePath, String prefix) throws IOException {
        return moveFromTempToExport(tempFilePath, prefix, true);
    }

    /**
     * Перемещает файл из временной директории в директорию экспорта
     *
     * @param tempFilePath путь к временному файлу
     * @param fileName     исходное имя файла или префикс
     * @param addUuid      добавлять ли UUID к имени файла
     */
    public Path moveFromTempToExport(Path tempFilePath, String fileName, boolean addUuid) throws IOException {

        // Создаем директорию для экспорта, если не существует
        Path exportDirPath = getAbsoluteExportDir();
        Files.createDirectories(exportDirPath);

        // Проверяем существование исходного файла
        if (!Files.exists(tempFilePath)) {
            throw new IOException("Временный файл не существует: " + tempFilePath.toAbsolutePath());
        }

        String extension = getFileExtension(tempFilePath.getFileName().toString());
        String finalName;

        if (addUuid) {
            String prefix = removeExtension(fileName);
            finalName = prefix + "_" + UUID.randomUUID() + extension;
        } else {
            // Если расширения нет, добавляем его
            finalName = fileName.endsWith(extension) ? fileName : fileName + extension;
        }

        Path targetPath = exportDirPath.resolve(finalName);

        // Перемещаем файл
        Files.move(tempFilePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
        log.info("Файл перемещен из временной директории в директорию экспорта: {} -> {}",
                tempFilePath.toAbsolutePath(), targetPath.toAbsolutePath());

        return targetPath;
    }

    /**
     * Перемещает файл из временной директории в директорию импорта
     */
    public Path moveFromTempToImport(Path tempFilePath, String prefix) throws IOException {
        // Создаем директорию для импорта, если не существует
        Path importDirPath = getAbsoluteImportDir();
        Files.createDirectories(importDirPath);

        // Проверяем существование исходного файла
        if (!Files.exists(tempFilePath)) {
            throw new IOException("Временный файл не существует: " + tempFilePath.toAbsolutePath());
        }

        // Генерируем уникальное имя файла
        String filename = prefix + "_" + UUID.randomUUID() +
                getFileExtension(tempFilePath.getFileName().toString());

        Path targetPath = importDirPath.resolve(filename);

        // Перемещаем файл
        Files.move(tempFilePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
        log.info("Файл перемещен из временной директории в директорию импорта: {} -> {}",
                tempFilePath.toAbsolutePath(), targetPath.toAbsolutePath());

        return targetPath;
    }

    /**
     * Перемещает файл из временной директории в директорию загрузок
     *
     * @deprecated Используйте moveFromTempToExport или moveFromTempToImport
     */
    @Deprecated
    public Path moveFromTempToUpload(Path tempFilePath, String prefix) throws IOException {
        // Для обратной совместимости перенаправляем на метод moveFromTempToExport
        log.warn("Использование устаревшего метода moveFromTempToUpload. Используйте moveFromTempToExport");
        return moveFromTempToExport(tempFilePath, prefix);
    }

    /**
     * Копирует файл из директории экспорта во временную директорию
     */
    public Path copyFromExportToTemp(Path exportFilePath) throws IOException {
        // Создаем временную директорию, если не существует
        Path tempDirPath = getAbsoluteTempDir();
        Files.createDirectories(tempDirPath);

        // Проверяем существование исходного файла
        if (!Files.exists(exportFilePath)) {
            throw new IOException("Файл экспорта не существует: " + exportFilePath.toAbsolutePath());
        }

        // Генерируем уникальное имя файла
        String filename = "copy_" + UUID.randomUUID() +
                getFileExtension(exportFilePath.getFileName().toString());

        Path targetPath = tempDirPath.resolve(filename);

        // Копируем файл
        Files.copy(exportFilePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
        log.debug("Файл скопирован из директории экспорта во временную директорию: {} -> {}",
                exportFilePath.toAbsolutePath(), targetPath.toAbsolutePath());

        return targetPath;
    }

    /**
     * Получает абсолютный путь ко временной директории
     */
    public Path getAbsoluteTempDir() {
        return getAbsolutePath(tempDir);
    }

    /**
     * Получает абсолютный путь к директории загрузок
     */
    public Path getAbsoluteUploadDir() {
        return getAbsolutePath(uploadDir);
    }

    /**
     * Получает абсолютный путь к директории экспорта
     */
    public Path getAbsoluteExportDir() {
        return getAbsolutePath(exportDir);
    }

    /**
     * Получает абсолютный путь к директории импорта
     */
    public Path getAbsoluteImportDir() {
        return getAbsolutePath(importDir);
    }

    /**
     * Получает абсолютный путь из относительного
     */
    public Path getAbsolutePath(String path) {
        File file = new File(path);
        if (!file.isAbsolute()) {
            file = new File(System.getProperty("user.dir"), path);
        }
        return file.toPath();
    }

    /**
     * Удаляет файл
     */
    public void deleteFile(Path filePath) {
        try {
            Files.deleteIfExists(filePath);
            log.debug("Удален файл: {}", filePath.toAbsolutePath());
        } catch (IOException e) {
            log.warn("Не удалось удалить файл {}: {}", filePath, e.getMessage());
        }
    }

    /**
     * Получает размер файла
     */
    public long getFileSize(Path filePath) {
        try {
            return Files.size(filePath);
        } catch (IOException e) {
            log.warn("Не удалось получить размер файла {}: {}", filePath, e.getMessage());
            return 0;
        }
    }

    /**
     * Проверяет существование файла
     */
    public boolean fileExists(Path filePath) {
        return Files.exists(filePath);
    }

    /**
     * Проверяет доступность директории для записи
     */
    public boolean isDirectoryWritable(Path dirPath) {
        return Files.isWritable(dirPath);
    }

    /**
     * Удаляет расширение из имени файла
     */
    private String removeExtension(String filename) {
        int lastDotIndex = filename.lastIndexOf('.');
        return lastDotIndex > 0 ? filename.substring(0, lastDotIndex) : filename;
    }


    /**
     * Получает расширение файла
     */
    private String getFileExtension(String filename) {
        int lastDotIndex = filename.lastIndexOf('.');
        return lastDotIndex > 0 ? filename.substring(lastDotIndex) : "";
    }
}