package com.java.config;

import com.java.util.PathResolver;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

/**
 * Компонент для инициализации директорий при запуске приложения
 * Устойчив к проблемам с DevTools RestartClassLoader
 */
@Component
@Slf4j
public class PathInitializer {

    @Autowired
    private ApplicationContext applicationContext;

    private PathResolver getPathResolver() {
        try {
            return applicationContext.getBean(PathResolver.class);
        } catch (Exception e) {
            log.error("Не удалось получить PathResolver через ApplicationContext: {}", e.getMessage());
            throw new RuntimeException("PathResolver недоступен", e);
        }
    }

    @PostConstruct
    public void init() {
        try {
            log.info("Инициализация файловой системы...");

            PathResolver pathResolver = getPathResolver();
            pathResolver.init();

            // Проверка прав доступа к директориям
            checkDirectoryAccess(pathResolver);

            log.info("Инициализация файловой системы завершена успешно");
        } catch (Exception e) {
            log.error("Ошибка при инициализации файловой системы: {}", e.getMessage(), e);
            // Не пробрасываем исключение, чтобы не прерывать запуск приложения
        }
    }

    private void checkDirectoryAccess(PathResolver pathResolver) {
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
