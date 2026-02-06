package com.portal.dto;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * 任务操作信息DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskActionInfo {
    
    /** 操作ID */
    private Long actionId;
    
    /** 操作名称 */
    private String actionName;
    
    /** 操作类型：APPROVE, REJECT, FORM_POPUP, API_CALL, etc. */
    private String actionType;
    
    /** 操作描述 */
    private String description;
    
    /** 表单ID（用于FORM_POPUP类型） */
    private Long formId;
    
    /** 表单名称 */
    private String formName;
}
