package com.admin.bi.dto.request;

import com.admin.bi.enums.AssignmentTargetType;
import com.admin.bi.enums.LayoutMode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Dashboard 分配创建请求
 */
@Data
@NoArgsConstructor
public class DashboardAssignmentCreateRequest {

    /** Dashboard ID */
    @NotBlank(message = "Dashboard ID is required")
    private String dashboardId;

    /** 分配目标类型 */
    @NotNull(message = "Target type is required")
    private AssignmentTargetType targetType;

    /** 目标 ID */
    @NotBlank(message = "Target ID is required")
    private String targetId;

    /** 布局模式 */
    private LayoutMode layoutMode;

    /** 显示顺序 */
    private Integer displayOrder;

    /** 是否为默认 Dashboard */
    private Boolean isDefault;
}
