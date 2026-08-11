package com.developer.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 可选「开发组（团队）」选项：用于进入工作区时的团队选择弹窗与顶部切换器。
 * {@code status} 为 {@code ACTIVE} 时可切换；{@code INACTIVE} 仍列出但前端禁用。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DevGroupOptionDTO {
    private String id;
    private String name;
    /** ACTIVE / INACTIVE（及其他生命周期状态） */
    private String status;

    public DevGroupOptionDTO(String id, String name) {
        this(id, name, "ACTIVE");
    }

    public boolean isSelectable() {
        return "ACTIVE".equalsIgnoreCase(status);
    }
}
