package com.java.config;

import com.java.util.PathResolver;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Компонент для инициализации директорий при запуске приложения
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class PathInitializer {
    private final PathResolver pathResolver;

    @PostConstruct
    public void init() {
        log.info("Инициализация файловой системы...");
        pathResolver.init();

        // Проверка прав доступа к директориям
        checkDirectoryAccess();
    }

    private void checkDirectoryAccess() {
        boolean tempDirWritable = pathResolver.isDirectoryWritable(pathResolver.getAbsoluteTempDir());
        boolean exportDirWritable = pathResolver.isDirectoryWritable(pathResolver.getAbsoluteExportDir());
        boolean importDirWritable = pathResolver.isDirectoryWritable(pathResolver.getAbsoluteImportDir());

        if (!tempDirWritable) {
            log.error("ВНИМАНИЕ! Временная директория недоступна для записи: {}",
                    pathResolver.getAbsoluteTempDir());
        }

        if (!exportDirWritable) {
            log.error("ВНИМАНИЕ! Директория экспорта недоступна для записи: {}",
                    pathResolver.getAbsoluteExportDir());
        }

        if (!importDirWritable) {
            log.error("ВНИМАНИЕ! Директория импорта недоступна для записи: {}",
                    pathResolver.getAbsoluteImportDir());
        }

        log.info("Проверка прав доступа к директориям завершена.");
    }
}
