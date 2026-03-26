package com.java.dto.handbook;

public record DomainRenameResult(
        int updatedUrls,
        int deletedDuplicates,
        boolean merged,
        String targetDomain
) {}
