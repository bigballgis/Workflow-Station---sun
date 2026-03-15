package com.admin.bi.dto.response;

import com.admin.bi.enums.AssignmentTargetType;
import com.admin.bi.enums.LayoutMode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Dashboard 分配记录响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardAssignmentResponse {

    private String id;
    private String dashboardId;
    private String dashboardTitle;
    private AssignmentTargetType targetType;
    private String targetId;
    private String targetName;
    private LayoutMode layoutMode;
    private Integer displayOrder;
    private Boolean isDefault;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
