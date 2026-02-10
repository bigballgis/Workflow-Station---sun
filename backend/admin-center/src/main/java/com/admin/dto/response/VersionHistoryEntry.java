package com.admin.dto.response;

import com.admin.enums.FunctionUnitStatus;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 版本历史记录 DTO
 * 用于展示功能单元的版本历史信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VersionHistoryEntry {
    
    /**
     * 版本号
     */
    private String version;
    
    /**
     * 部署状态
     */
    private FunctionUnitStatus status;
    
    /**
     * 是否启用
     */
    private Boolean enabled;
    
    /**
     * 创建时间
     */
    private Instant createdAt;
    
    /**
     * 创建人
     */
    private String createdBy;
    
    /**
     * 部署时间
     */
    private Instant deployedAt;
    
    /**
     * 验证时间
     */
    private Instant validatedAt;
    
    /**
     * 验证人
     */
    private String validatedBy;
    
    /**
     * 是否为最新版本
     */
    @JsonProperty("isLatest")
    private boolean isLatest;
    
    /**
     * 是否为当前启用版本
     */
    @JsonProperty("isCurrentlyEnabled")
    private boolean isCurrentlyEnabled;
    
    /**
     * 变更类型：MAJOR, MINOR, PATCH, INITIAL
     */
    private String changeType;
}
