package com.java.repository;

import com.java.model.entity.ImportError;
import com.java.model.entity.ImportSession;
import com.java.model.enums.ErrorType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ImportErrorRepository extends JpaRepository<ImportError, Long> {

    // Найти ошибки сессии
    Page<ImportError> findByImportSession(ImportSession session, Pageable pageable);

    // Найти ошибки по типу
    List<ImportError> findByImportSessionAndErrorType(ImportSession session, ErrorType errorType);

    // Подсчет ошибок по типам
    @Query("SELECT e.errorType, COUNT(e) FROM ImportError e " +
            "WHERE e.importSession = :session " +
            "GROUP BY e.errorType")
    List<Object[]> countErrorsByType(@Param("session") ImportSession session);

    // Найти ошибки определенной строки
    List<ImportError> findByImportSessionAndRowNumber(ImportSession session, Long rowNumber);

    // Удалить все ошибки сессии (для отката)
    void deleteByImportSession(ImportSession session);
    
    // Удалить все ошибки сессии по ID
    void deleteByImportSessionId(Long importSessionId);
    
    // Подсчитать количество ошибок сессии по ID
    long countByImportSessionId(Long importSessionId);
}