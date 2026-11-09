package com.admin.dto.response;

import com.admin.entity.FunctionUnit;
import com.admin.enums.FunctionUnitStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * API payload: FunctionUnit summary for Admin Center lists/detail.
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
    private Boolean enabled;
    private Long packageSize;
    private String checksum;
    private Instant importedAt;
    private String importedBy;
    private Instant validatedAt;
    private String validatedBy;
    private Instant createdAt;
    private Instant updatedAt;
    private String updatedBy;
    private String iconSvg;
    /** Owning team of the unit's code (its latest imported row); set by the catalog list only. */
    private String teamCode;
    /** Name of that team; null when the team no longer exists. */
    private String teamName;
    /** What the catalog's team column shows and exports: the team name, or its code once the team is deleted. */
    private String team;
    /** Whether the caller may change this unit (its team is one they lead, or they are SYS_ADMIN); lists only. */
    private Boolean canManage;

    /**
     * Map from persisted {@link FunctionUnit} entity.
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
                .enabled(entity.getEnabled())
                .packageSize(entity.getPackageSize())
                .checksum(entity.getChecksum())
                .importedAt(entity.getImportedAt())
                .importedBy(entity.getImportedBy())
                .validatedAt(entity.getValidatedAt())
                .validatedBy(entity.getValidatedBy())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .updatedBy(entity.getUpdatedBy())
                .iconSvg(entity.getIconSvg())
                .build();
    }
}
