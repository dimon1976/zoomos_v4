package com.java.util;

/**
 * Утилиты для работы с файлами и форматированием размеров.
 */
public final class FileUtils {

    private FileUtils() {}

    /**
     * Форматирование размера в байтах в читаемый вид.
     */
    public static String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
    }
}
