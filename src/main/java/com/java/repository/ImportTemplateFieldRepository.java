package com.java.repository;

import com.java.model.entity.ImportTemplateField;
import com.java.model.entity.ImportTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ImportTemplateFieldRepository extends JpaRepository<ImportTemplateField, Long> {

    // Найти поля шаблона отсортированные по индексу
    List<ImportTemplateField> findByTemplateOrderByColumnIndexAsc(ImportTemplate template);

    // Найти поле по имени колонки
    List<ImportTemplateField> findByTemplateAndColumnName(ImportTemplate template, String columnName);

    // Найти уникальные поля шаблона
    List<ImportTemplateField> findByTemplateAndIsUniqueTrue(ImportTemplate template);
}