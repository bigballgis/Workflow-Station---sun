package com.admin.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 流程内容 DTO
 * 包含 BPMN XML 数据（已 Base64 解码）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProcessContentDTO {
    private String id;
    private String name;
    private String sourceId;
    private String data;       // BPMN XML 字符串（已 Base64 解码）
    private String type;       // "PROCESS"
}
