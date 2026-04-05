package com.portal.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 子表行分配处理人请求（门户转发至 workflow-engine）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubTableRowAssignRequest {

    @NotBlank(message = "assigneeId is required")
    private String assigneeId;
}
