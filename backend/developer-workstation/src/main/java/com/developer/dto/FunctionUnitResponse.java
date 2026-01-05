package com.developer.dto;

import com.developer.enums.FunctionUnitStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 功能单元响应DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FunctionUnitResponse {
    
    private Long id;
    private String name;
    private String description;
    private Long iconId;
    private FunctionUnitStatus status;
    private String currentVersion;
    private String createdBy;
    private Instant createdAt;
    private String updatedBy;
    private Instant updatedAt;
    private int tableCount;
    private int formCount;
    private int actionCount;
    private boolean hasProcess;
}
