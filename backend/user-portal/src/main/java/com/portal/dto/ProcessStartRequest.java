package com.portal.dto;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import jakarta.validation.constraints.NotBlank;
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

    /** 流程定义Key */
    @NotBlank(message = "{validation.process_definition_key_required}")
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
