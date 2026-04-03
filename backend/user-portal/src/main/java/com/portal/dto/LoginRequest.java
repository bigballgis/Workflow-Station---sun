package com.portal.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginRequest {
    @NotBlank(message = "{validation.username_required}")
    private String username;
    
    @NotBlank(message = "{validation.password_required}")
    private String password;

    /**
     * 当用户存在多条 UBR 时必填：选择进入工作台的业务单元与角色（role 主键）。
     */
    private String workspaceBusinessUnitId;

    private String workspaceRoleId;
}
