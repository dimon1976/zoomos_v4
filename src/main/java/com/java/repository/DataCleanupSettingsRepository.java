package com.java.repository;

import com.java.model.entity.DataCleanupSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Репозиторий для работы с настройками очистки данных
 */
@Repository
public interface DataCleanupSettingsRepository extends JpaRepository<DataCleanupSettings, Long> {

    /**
     * Найти настройки по типу данных
     */
    Optional<DataCleanupSettings> findByEntityType(String entityType);

    /**
     * Проверить существование настроек для типа данных
     */
    boolean existsByEntityType(String entityType);
}
