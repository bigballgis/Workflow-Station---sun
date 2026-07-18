package com.developer.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 可选「开发组（团队）」选项：用于进入工作区时的团队选择弹窗与顶部切换器。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DevGroupOptionDTO {
    private String id;
    private String name;
}
