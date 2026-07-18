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

    /** 用户所属的、可选择的团队（CUSTOM 类型虚拟组，排除内置 Public 组）。 */
    private List<DevGroupOptionDTO> groups;

    /** 是否可查看全部功能单元（仅 ADMIN 型角色为 true，可选择「全部团队」）。 */
    private boolean canSeeAllGroups;

    /** 内置 Public 组 id（其功能单元始终叠加可见）。 */
    private String publicGroupId;
}
