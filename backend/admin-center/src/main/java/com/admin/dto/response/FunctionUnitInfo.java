package com.admin.dto.response;

import com.admin.entity.FunctionUnit;
import com.admin.enums.FunctionUnitStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 功能单元信息响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FunctionUnitInfo {
    
    private String id;
    private String name;
    private String code;
    private String version;
    private String description;
    private FunctionUnitStatus status;
    private Long packageSize;
    private String checksum;
    private Instant importedAt;
    private String importedBy;
    private Instant validatedAt;
    private String validatedBy;
    private Instant createdAt;
    private Instant updatedAt;
    
    /**
     * 从实体转换
     */
    public static FunctionUnitInfo fromEntity(FunctionUnit entity) {
        if (entity == null) {
            return null;
        }
        return FunctionUnitInfo.builder()
                .id(entity.getId())
                .name(entity.getName())
                .code(entity.getCode())
                .version(entity.getVersion())
                .description(entity.getDescription())
                .status(entity.getStatus())
                .packageSize(entity.getPackageSize())
                .checksum(entity.getChecksum())
                .importedAt(entity.getImportedAt())
                .importedBy(entity.getImportedBy())
                .validatedAt(entity.getValidatedAt())
                .validatedBy(entity.getValidatedBy())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
