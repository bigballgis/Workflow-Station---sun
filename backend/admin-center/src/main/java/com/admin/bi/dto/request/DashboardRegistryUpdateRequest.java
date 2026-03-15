package com.admin.bi.dto.request;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Dashboard 注册表本地扩展字段更新请求
 */
@Data
@NoArgsConstructor
public class DashboardRegistryUpdateRequest {

    /** 标签（逗号分隔） */
    private String tags;

    /** 是否为默认 Landing Dashboard */
    private Boolean isDefaultLanding;
}
