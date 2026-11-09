package com.developer.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 可选「开发组（团队）」选项：用于进入工作区时的团队选择弹窗与顶部切换器。
 * {@code status} 为 {@code ACTIVE} 时可切换；{@code INACTIVE} 只对能看全部团队的用户列出（标注已停用）。
 * {@code techLead} / {@code developer} 是当前用户在该团队所在的名单，{@code canCreate} 表示当前用户能否在该团队
 * 新建、导入、克隆、移入功能单元（平台访问模型 §6.2）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DevGroupOptionDTO {
    private String id;
    private String name;
    /** ACTIVE / INACTIVE（及其他生命周期状态） */
    private String status;
    private boolean techLead;
    private boolean developer;
    private boolean canCreate;

    public DevGroupOptionDTO(String id, String name, String status) {
        this(id, name, status, false, false, false);
    }

    public boolean isSelectable() {
        return "ACTIVE".equalsIgnoreCase(status);
    }
}
