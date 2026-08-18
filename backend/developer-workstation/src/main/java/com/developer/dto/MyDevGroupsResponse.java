package com.developer.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

/**
 * 当前用户可用的「开发组（团队）」信息，驱动 DW 进入时的团队选择弹窗与顶部切换器。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MyDevGroupsResponse {
    /** 可选择的团队（ADMIN 为全部活跃 CUSTOM 团队，其他用户为其所属团队；均排除内置 Public）。 */
    private List<DevGroupOptionDTO> groups;

    /** 是否可查看全部功能单元（SYS_ADMIN / AUDITOR 为 true，可选择「全部团队」）。 */
    private boolean canSeeAllGroups;
    /** 内置 Public 组 id（用于在团队切换器中单独查看公共功能单元）。 */
    private String publicGroupId;
}
