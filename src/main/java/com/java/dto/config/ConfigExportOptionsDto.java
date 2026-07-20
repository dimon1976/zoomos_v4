package com.java.dto.config;

import lombok.Data;

@Data
public class ConfigExportOptionsDto {

    private boolean includeClients = true;
    private boolean includeImportTemplates = true;
    private boolean includeExportTemplates = true;
}
