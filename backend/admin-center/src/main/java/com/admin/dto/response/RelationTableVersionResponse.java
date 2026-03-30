package com.admin.dto.response;

import com.admin.entity.RelationTableVersion;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Relation Table 版本历史响应 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RelationTableVersionResponse {

    private Long id;
    private Integer versionNumber;
    private String snapshotData;
    private String deployedBy;
    private Instant deployedAt;
    private String changeLog;

    /**
     * 从版本实体转换
     */
    public static RelationTableVersionResponse fromEntity(RelationTableVersion entity) {
        if (entity == null) {
            return null;
        }
        return RelationTableVersionResponse.builder()
                .id(entity.getId())
                .versionNumber(entity.getVersionNumber())
                .snapshotData(entity.getSnapshotData())
                .deployedBy(entity.getDeployedBy())
                .deployedAt(entity.getDeployedAt())
                .changeLog(entity.getChangeLog())
                .build();
    }
}
