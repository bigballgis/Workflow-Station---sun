package com.developer.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO for displaying version history in the UI.
 * Contains all versions of a function unit with detailed information.
 * 
 * Requirements: 3.3, 3.4, 3.5 - Version History Display
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VersionHistoryDisplay {
    
    /**
     * The name of the function unit
     */
    private String functionUnitName;
    
    /**
     * List of all versions ordered by version number descending
     */
    private List<VersionHistoryEntry> versions;
}
