package com.portal.dto;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 流程发起请求DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProcessStartRequest {

    /**
     * 流程定义 Key（与路径 {@code POST /processes/{processKey}/start} 通常一致）。
     * 可省略：缺省时由网关/控制器使用路径变量 {@code processKey} 填充。
     */
    private String processDefinitionKey;

    /** 业务Key */
    private String businessKey;

    /** 表单数据 */
    private Map<String, Object> formData;

    /** 附件列表 */
    private List<AttachmentInfo> attachments;

    /** 紧急程度：NORMAL, GENERAL, URGENT, CRITICAL */
    @Builder.Default
    private String priority = "NORMAL";

    /** 备注 */
    private String remark;

    /**
     * Transport metadata for table-keyed {@code __subTables__} — not a form field.
     * Must stay off {@link #formData} so engine start cannot persist it as a process variable.
     * Null/empty keeps the V1 table-keyed path.
     */
    private List<String> emptiedSubTableKeys;

    /** Per-binding write claims. Same shape as {@code TaskFormSubmitRequest}. */
    private List<SubTableBindingScope> subTableBindingScopes;

    /**
     * 已废弃：服务端不再读取。流程变量 {@code activeBusinessUnitId} 仅由当前访问令牌 JWT 中的工作台上下文写入，防止客户端伪造。
     */
    @Deprecated(forRemoval = false)
    private String activeBusinessUnitId;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AttachmentInfo {
        private String fileName;
        private String fileUrl;
        private Long fileSize;
        private String fileType;
    }
}
