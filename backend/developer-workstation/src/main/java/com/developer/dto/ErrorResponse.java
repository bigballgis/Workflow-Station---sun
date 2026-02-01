package com.developer.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Error response DTO for API error responses.
 * 
 * Requirements: 3.2, 3.3
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ErrorResponse {
    
    private String code;
    private String message;
    private List<Map<String, String>> details;
    private String suggestion;
    private Instant timestamp;
    private String traceId;
    private String path;
}
