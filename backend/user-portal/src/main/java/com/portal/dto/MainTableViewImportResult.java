package com.portal.dto;

import lombok.Builder;

import java.util.List;

@Builder
public record MainTableViewImportResult(
        int createdCount,
        int updatedCount,
        int skippedCount,
        int errorCount,
        List<String> errors
) {}
