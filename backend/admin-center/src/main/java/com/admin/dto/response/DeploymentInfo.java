package com.admin.dto.response;

import com.admin.entity.FunctionUnit;
import com.admin.entity.FunctionUnitDeployment;
import com.admin.enums.DeploymentEnvironment;
import com.admin.enums.DeploymentStatus;
import com.admin.enums.DeploymentStrategy;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 功能单元部署记录列表项
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeploymentInfo {

    private String id;
    private String functionUnitId;
    private String functionUnitName;
    private String functionUnitCode;
    private String version;
    private DeploymentEnvironment environment;
    private DeploymentStrategy strategy;
    private DeploymentStatus status;
    private Instant deployedAt;
    private String deployedBy;
    private Instant completedAt;
    private Instant createdAt;

    public static DeploymentInfo fromEntity(FunctionUnitDeployment entity) {
        if (entity == null) {
            return null;
        }
        FunctionUnit fu = entity.getFunctionUnit();
        Instant deployedAt = entity.getDeployedAt();
        if (deployedAt == null) {
            deployedAt = entity.getCompletedAt() != null ? entity.getCompletedAt() : entity.getStartedAt();
        }
        return DeploymentInfo.builder()
                .id(entity.getId())
                .functionUnitId(fu != null ? fu.getId() : null)
                .functionUnitName(fu != null ? fu.getName() : null)
                .functionUnitCode(fu != null ? fu.getCode() : null)
                .version(fu != null ? fu.getVersion() : null)
                .environment(entity.getEnvironment())
                .strategy(entity.getStrategy())
                .status(entity.getStatus())
                .deployedAt(deployedAt)
                .deployedBy(entity.getDeployedBy())
                .completedAt(entity.getCompletedAt())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
