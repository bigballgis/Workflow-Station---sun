package com.portal.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * To Do 批量通过/驳回：同一动作作用于多条任务，不带表单数据。
 */
public record BatchCompleteRequest(
        @NotEmpty @Size(max = 200) List<@NotBlank String> taskIds,
        @NotBlank String actionId,
        String comment) {
}
