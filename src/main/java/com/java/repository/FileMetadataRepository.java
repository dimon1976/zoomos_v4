package com.java.repository;

import com.java.model.entity.FileMetadata;
import com.java.model.entity.ImportSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface FileMetadataRepository extends JpaRepository<FileMetadata, Long> {

    // Найти метаданные по сессии
    Optional<FileMetadata> findByImportSession(ImportSession session);

    // Найти по хешу файла (для проверки дубликатов файлов)
    Optional<FileMetadata> findByFileHash(String fileHash);

    // Найти по временному пути
    Optional<FileMetadata> findByTempFilePath(String tempFilePath);
}