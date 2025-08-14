package com.java.dto;

import com.java.model.FileOperation;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardFilterDto {

    private Long clientId;
    private FileOperation.OperationType operationType;
    private FileOperation.OperationStatus status;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate dateFrom;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate dateTo;

    private String fileName; // поиск по имени файла
    private String fileType; // фильтр по типу файла

    // Параметры пагинации
    @Builder.Default
    private Integer page = 0;

    @Builder.Default
    private Integer size = 20;

    @Builder.Default
    private String sort = "startedAt";

    @Builder.Default
    private String direction = "desc";
}