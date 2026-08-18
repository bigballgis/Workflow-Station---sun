package com.developer.dto;

import com.developer.enums.FunctionUnitStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * 功能单元响应DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FunctionUnitResponse {
    
    private Long id;
    private String code;
    private String name;
    private String description;
    private Long iconId;
    private List<String> tags;
    private IconInfo icon;
    private FunctionUnitStatus status;
    private String currentVersion;
    private String createdBy;
    private Instant createdAt;
    private String updatedBy;
    private Instant updatedAt;
    private int tableCount;
    private int formCount;
    private int actionCount;
    private int decisionCount;
    private boolean hasProcess;

    /** 已分配的虚拟开发组 ID（sys_virtual_groups.id） */
    private List<String> assignedVirtualGroupIds;

    /**
     * Whether the current user may modify this function unit.
     * Missing/false must be treated as read-only by clients (fail-closed).
     */
    private boolean canModify;
    
    /**
     * 图标信息内部类
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class IconInfo {
        private Long id;
        private String name;
        private String svgContent;
    }
}
