package com.portal.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 子表行分配处理人请求（门户转发至 workflow-engine）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubTableRowAssignRequest {

    private Map<String, Object> rowKey;

    @NotBlank(message = "assigneeId is required")
    private String assigneeId;
}
