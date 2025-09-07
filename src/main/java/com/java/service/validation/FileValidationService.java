package com.java.service.validation;

import org.springframework.web.multipart.MultipartFile;
import java.io.File;

/**
 * Интерфейс для валидации файлов.
 * Обеспечивает централизованную валидацию файлов в системе.
 */
public interface FileValidationService extends ValidationService {
    
    /**
     * Валидирует загружаемый файл.
     * 
     * @param file загружаемый файл
     * @throws ValidationException если файл не прошел валидацию
     */
    void validateFile(MultipartFile file) throws ValidationException;
    
    /**
     * Валидирует файл по пути.
     * 
     * @param filePath путь к файлу
     * @throws ValidationException если файл не прошел валидацию
     */
    void validateFile(String filePath) throws ValidationException;
    
    /**
     * Валидирует файл.
     * 
     * @param file файл для валидации
     * @throws ValidationException если файл не прошел валидацию
     */
    void validateFile(File file) throws ValidationException;
    
    /**
     * Валидирует размер файла.
     * 
     * @param fileSize размер файла в байтах
     * @param maxSize максимально допустимый размер в байтах
     * @throws ValidationException если размер превышает допустимый
     */
    void validateFileSize(long fileSize, long maxSize) throws ValidationException;
    
    /**
     * Валидирует формат файла по расширению.
     * 
     * @param filename имя файла
     * @param allowedExtensions допустимые расширения
     * @throws ValidationException если формат не поддерживается
     */
    void validateFileFormat(String filename, String... allowedExtensions) throws ValidationException;
}