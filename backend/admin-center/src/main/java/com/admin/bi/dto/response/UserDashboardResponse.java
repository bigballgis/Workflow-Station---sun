package com.admin.bi.dto.response;

import com.admin.bi.enums.LayoutMode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * 用户有效 Dashboard 响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserDashboardResponse {

    private String dashboardId;
    private String dashboardTitle;
    private String description;
    private UUID embedId;
    private LayoutMode layoutMode;
    private Integer displayOrder;
    private Boolean isDefault;
}
