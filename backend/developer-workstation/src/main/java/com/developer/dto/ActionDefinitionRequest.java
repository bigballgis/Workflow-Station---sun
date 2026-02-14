package com.developer.dto;

import com.developer.enums.ActionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 动作定义请求DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActionDefinitionRequest {
    
    @NotBlank(message = "{validation.action_name_required}")
    @Size(max = 100, message = "{validation.action_name_max_length}")
    private String actionName;
    
    @NotNull(message = "{validation.action_type_required}")
    private ActionType actionType;
    
    @NotNull(message = "{validation.action_config_required}")
    private Map<String, Object> configJson;
    
    private String icon;
    private String buttonColor;
    private String description;
}
