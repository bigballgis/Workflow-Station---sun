package com.admin.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * N8N 连接测试结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class N8nConnectionTestResult {

    private boolean success;

    private String message;

    private Integer workflowCount;
}
