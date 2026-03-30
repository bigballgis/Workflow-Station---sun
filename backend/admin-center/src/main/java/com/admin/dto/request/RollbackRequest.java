package com.admin.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 回滚 Relation Table 请求 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RollbackRequest {

    @NotNull(message = "目标版本 ID 不能为空")
    private Long targetVersionId;
}
