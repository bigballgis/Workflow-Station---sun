package com.platform.common.functionunit;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Result of function unit import.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImportResult {
    
    private boolean success;
    private String functionUnitId;
    private String functionUnitName;
    private String version;
    private LocalDateTime importedAt;
    private String importedBy;
    private List<String> importedComponents;
    private List<String> errors;
    private List<String> warnings;
}
