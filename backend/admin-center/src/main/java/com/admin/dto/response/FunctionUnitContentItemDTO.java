package com.admin.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 功能单元内容项 DTO — 用于合并后的 GET /{id}/contents 端点。
 *
 * <p><b>Validates: Requirements 35.1, 35.2, 35.3</b>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FunctionUnitContentItemDTO {
    private String id;
    private String contentType;
    private String contentName;
    private String contentData;
    private String sourceId;
}
