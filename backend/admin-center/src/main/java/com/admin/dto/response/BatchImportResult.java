package com.admin.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 批量导入结果DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchImportResult {
    
    private boolean success;
    private String fileName;
    private int totalCount;
    private int successCount;
    private int failureCount;
    private String errors;
    private Instant startTime;
    private Instant endTime;
    
    public long getDurationMillis() {
        if (startTime != null && endTime != null) {
            return endTime.toEpochMilli() - startTime.toEpochMilli();
        }
        return 0;
    }
}
