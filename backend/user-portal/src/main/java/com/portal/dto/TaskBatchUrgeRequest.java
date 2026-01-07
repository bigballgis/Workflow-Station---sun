package com.portal.dto;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/**
 * 批量催办请求DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskBatchUrgeRequest {

    /** 任务ID列表 */
    @NotEmpty(message = "任务ID列表不能为空")
    private List<String> taskIds;

    /** 催办消息 */
    private String message;
}
