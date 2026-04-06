package com.portal.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 子表行分配处理人请求（当 UI 行缺少 rowId 时，按业务字段反查）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubTableRowAssignByIdentityRequest {

    @NotBlank(message = "assigneeId is required")
    private String assigneeId;

    private String email;
    private String name;
    private String department;
    private String topic;
    private String location;
    private String organizerName;
}
