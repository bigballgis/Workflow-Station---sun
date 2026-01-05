package com.platform.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * Pagination request parameters.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PageRequest {
    
    /**
     * Page number (0-based)
     */
    @Min(0)
    @Builder.Default
    private int page = 0;
    
    /**
     * Page size
     */
    @Min(1)
    @Max(100)
    @Builder.Default
    private int size = 20;
    
    /**
     * Sort field
     */
    private String sortBy;
    
    /**
     * Sort direction (asc/desc)
     */
    @Builder.Default
    private String sortDirection = "asc";
    
    /**
     * Calculate offset for SQL queries
     */
    public int getOffset() {
        return page * size;
    }
    
    /**
     * Check if sorting is ascending
     */
    public boolean isAscending() {
        return !"desc".equalsIgnoreCase(sortDirection);
    }
}
