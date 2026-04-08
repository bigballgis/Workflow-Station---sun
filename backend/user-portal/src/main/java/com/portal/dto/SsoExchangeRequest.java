package com.portal.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SsoExchangeRequest {

    @NotBlank
    private String code;

    private String state;

    private String workspaceBusinessUnitId;

    private String workspaceRoleId;
}
