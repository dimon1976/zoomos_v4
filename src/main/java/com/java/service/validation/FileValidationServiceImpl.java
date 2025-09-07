package com.java.service.validation;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

/**
 * Реализация сервиса валидации файлов.
 * Обеспечивает централизованную валидацию файлов в системе.
 */
@Slf4j
@Service
public class FileValidationServiceImpl implements FileValidationService {
    
    private static final List<String> ALLOWED_EXTENSIONS = Arrays.asList("xlsx", "xls", "csv");
    private static final long DEFAULT_MAX_FILE_SIZE = 1200L * 1024 * 1024; // 1200MB
    
    @Override
    public void validateFile(MultipartFile file) throws ValidationException {
        if (file == null) {
            throw new ValidationException("file", null, "Файл не может быть null");
        }
        
        if (file.isEmpty()) {
            throw new ValidationException("file", file.getOriginalFilename(), "Файл не может быть пустым");
        }
        
        // Валидация имени файла
        String filename = file.getOriginalFilename();
        if (filename == null || filename.trim().isEmpty()) {
            throw new ValidationException("filename", filename, "Имя файла не может быть пустым");
        }
        
        // Валидация размера файла
        validateFileSize(file.getSize(), DEFAULT_MAX_FILE_SIZE);
        
        // Валидация формата файла
        validateFileFormat(filename, ALLOWED_EXTENSIONS.toArray(new String[0]));
        
        log.debug("Файл {} успешно прошел валидацию", filename);
    }
    
    @Override
    public void validateFile(String filePath) throws ValidationException {
        if (filePath == null || filePath.trim().isEmpty()) {
            throw new ValidationException("filePath", filePath, "Путь к файлу не может быть пустым");
        }
        
        Path path = Paths.get(filePath);
        if (!Files.exists(path)) {
            throw new ValidationException("filePath", filePath, "Файл не существует");
        }
        
        if (!Files.isRegularFile(path)) {
            throw new ValidationException("filePath", filePath, "Указанный путь не является файлом");
        }
        
        File file = path.toFile();
        validateFile(file);
    }
    
    @Override
    public void validateFile(File file) throws ValidationException {
        if (file == null) {
            throw new ValidationException("file", null, "Файл не может быть null");
        }
        
        if (!file.exists()) {
            throw new ValidationException("file", file.getAbsolutePath(), "Файл не существует");
        }
        
        if (!file.isFile()) {
            throw new ValidationException("file", file.getAbsolutePath(), "Указанный путь не является файлом");
        }
        
        if (!file.canRead()) {
            throw new ValidationException("file", file.getAbsolutePath(), "Файл недоступен для чтения");
        }
        
        // Валидация размера файла
        validateFileSize(file.length(), DEFAULT_MAX_FILE_SIZE);
        
        // Валидация формата файла
        validateFileFormat(file.getName(), ALLOWED_EXTENSIONS.toArray(new String[0]));
        
        log.debug("Файл {} успешно прошел валидацию", file.getName());
    }
    
    @Override
    public void validateFileSize(long fileSize, long maxSize) throws ValidationException {
        if (fileSize <= 0) {
            throw new ValidationException("fileSize", fileSize, "Размер файла должен быть больше 0");
        }
        
        if (fileSize > maxSize) {
            long maxSizeMB = maxSize / (1024 * 1024);
            long fileSizeMB = fileSize / (1024 * 1024);
            throw new ValidationException("fileSize", fileSizeMB + "MB", 
                String.format("Размер файла (%d МБ) превышает максимально допустимый (%d МБ)", 
                             fileSizeMB, maxSizeMB));
        }
        
        log.debug("Размер файла {} байт прошел валидацию", fileSize);
    }
    
    @Override
    public void validateFileFormat(String filename, String... allowedExtensions) throws ValidationException {
        if (filename == null || filename.trim().isEmpty()) {
            throw new ValidationException("filename", filename, "Имя файла не может быть пустым");
        }
        
        if (allowedExtensions == null || allowedExtensions.length == 0) {
            log.warn("Не указаны допустимые расширения файлов для валидации");
            return;
        }
        
        String extension = getFileExtension(filename);
        if (extension == null || extension.isEmpty()) {
            throw new ValidationException("filename", filename, "Файл должен иметь расширение");
        }
        
        boolean isValidExtension = Arrays.stream(allowedExtensions)
            .anyMatch(ext -> ext.toLowerCase().equals(extension.toLowerCase()));
            
        if (!isValidExtension) {
            throw new ValidationException("filename", filename, 
                String.format("Неподдерживаемый формат файла. Допустимые форматы: %s", 
                             String.join(", ", allowedExtensions)));
        }
        
        log.debug("Формат файла {} прошел валидацию", extension);
    }
    
    @Override
    public <T> void validate(T object) throws ValidationException {
        if (object instanceof MultipartFile) {
            validateFile((MultipartFile) object);
        } else if (object instanceof File) {
            validateFile((File) object);
        } else if (object instanceof String) {
            validateFile((String) object);
        } else {
            throw new ValidationException("object", object != null ? object.getClass().getName() : "null", 
                "Неподдерживаемый тип объекта для валидации файлов");
        }
    }
    
    @Override
    public boolean canValidate(Class<?> objectClass) {
        return MultipartFile.class.isAssignableFrom(objectClass) ||
               File.class.isAssignableFrom(objectClass) ||
               String.class.equals(objectClass);
    }
    
    /**
     * Извлекает расширение файла из его имени.
     * 
     * @param filename имя файла
     * @return расширение файла без точки или null если расширения нет
     */
    private String getFileExtension(String filename) {
        if (filename == null || filename.lastIndexOf('.') == -1) {
            return null;
        }
        
        int lastDotIndex = filename.lastIndexOf('.');
        if (lastDotIndex == filename.length() - 1) {
            return null; // Файл заканчивается точкой
        }
        
        return filename.substring(lastDotIndex + 1);
    }
}