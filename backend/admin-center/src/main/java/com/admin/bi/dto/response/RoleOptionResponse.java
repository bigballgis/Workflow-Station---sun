package com.admin.bi.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 角色选项响应 DTO，用于未映射角色下拉列表
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoleOptionResponse {

    private String id;
    private String name;
    private String code;
    private String type;
}
