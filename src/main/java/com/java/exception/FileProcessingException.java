package com.java.exception;

/**
 * Исключение при обработке файла
 */
public class FileProcessingException extends ImportException {

    private final String fileName;
    private final Long rowNumber;

    public FileProcessingException(String message, String fileName) {
        super("file.processing.error", message, fileName);
        this.fileName = fileName;
        this.rowNumber = null;
    }

    public FileProcessingException(String message, String fileName, Long rowNumber) {
        super("file.processing.error.row", message, fileName, rowNumber);
        this.fileName = fileName;
        this.rowNumber = rowNumber;
    }

    public FileProcessingException(String message, String fileName, Throwable cause) {
        super(message, cause);
        this.fileName = fileName;
        this.rowNumber = null;
    }

    public String getFileName() {
        return fileName;
    }

    public Long getRowNumber() {
        return rowNumber;
    }
}