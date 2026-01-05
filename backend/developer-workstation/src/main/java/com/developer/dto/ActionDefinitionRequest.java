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
    
    @NotBlank(message = "动作名不能为空")
    @Size(max = 100, message = "动作名长度不能超过100个字符")
    private String actionName;
    
    @NotNull(message = "动作类型不能为空")
    private ActionType actionType;
    
    @NotNull(message = "动作配置不能为空")
    private Map<String, Object> configJson;
    
    private String icon;
    private String buttonColor;
    private String description;
}
