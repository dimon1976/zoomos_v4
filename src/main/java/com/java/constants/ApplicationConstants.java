package com.java.constants;

/**
 * Константы приложения
 * Содержит часто используемые значения и настройки по умолчанию
 */
public final class ApplicationConstants {

    private ApplicationConstants() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Константы для работы с файлами
     */
    public static final class Files {
        public static final String TEMP_FILE_PREFIX = "zoomos_";
        public static final String CSV_EXTENSION = ".csv";
        public static final String XLSX_EXTENSION = ".xlsx";
        public static final int DEFAULT_BUFFER_SIZE = 8192;

        private Files() {
            throw new UnsupportedOperationException("Utility class");
        }
    }

    /**
     * Константы для HTTP и сети
     */
    public static final class Network {
        public static final int DEFAULT_TIMEOUT_MS = 30000; // 30 seconds
        public static final int DEFAULT_CONNECTION_TIMEOUT_MS = 10000; // 10 seconds
        public static final int MAX_REDIRECTS = 10;
        public static final int CORS_MAX_AGE_SECONDS = 3600; // 1 hour

        private Network() {
            throw new UnsupportedOperationException("Utility class");
        }
    }

    /**
     * Константы для базы данных
     */
    public static final class Database {
        public static final int DEFAULT_BATCH_SIZE = 1000;
        public static final int CLEANUP_BATCH_SIZE = 10000;
        public static final int MIN_RETENTION_DAYS = 7;
        public static final long AUTO_VACUUM_THRESHOLD = 1_000_000L; // 1M records

        private Database() {
            throw new UnsupportedOperationException("Utility class");
        }
    }

    /**
     * Константы для валидации
     */
    public static final class Validation {
        public static final int MIN_CLIENT_NAME_LENGTH = 1;
        public static final int MAX_CLIENT_NAME_LENGTH = 255;
        public static final int MAX_TEMPLATE_NAME_LENGTH = 255;
        public static final int MAX_COLUMN_NAME_LENGTH = 255;

        private Validation() {
            throw new UnsupportedOperationException("Utility class");
        }
    }

    /**
     * Константы для кэширования
     */
    public static final class Cache {
        public static final int MESSAGE_CACHE_SECONDS = 3600; // 1 hour
        public static final int STATS_CACHE_MILLIS = 10000; // 10 seconds

        private Cache() {
            throw new UnsupportedOperationException("Utility class");
        }
    }

    /**
     * Константы для Playwright
     */
    public static final class Playwright {
        public static final int DEFAULT_VIEWPORT_WIDTH = 1920;
        public static final int DEFAULT_VIEWPORT_HEIGHT = 1080;
        public static final String DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

        private Playwright() {
            throw new UnsupportedOperationException("Utility class");
        }
    }

    /**
     * Форматы даты и времени
     */
    public static final class DateFormat {
        public static final String DATETIME_PATTERN = "yyyy-MM-dd HH:mm:ss";
        public static final String DATE_PATTERN = "yyyy-MM-dd";
        public static final String TIME_PATTERN = "HH:mm:ss";

        private DateFormat() {
            throw new UnsupportedOperationException("Utility class");
        }
    }
}
