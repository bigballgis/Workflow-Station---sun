package com.portal.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SwitchWorkspaceRequest {

    @NotBlank
    private String businessUnitId;

    @NotBlank
    private String roleId;
}
