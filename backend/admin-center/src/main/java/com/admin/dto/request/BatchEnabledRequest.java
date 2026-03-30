package com.admin.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 批量启用/禁用功能单元的请求 DTO
 *
 * <p><b>Validates: Requirements 20.1, 20.2</b>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BatchEnabledRequest {

    @NotEmpty(message = "{functionUnit.batch.ids.notEmpty}")
    private List<String> ids;

    @NotNull(message = "{functionUnit.enabled.notNull}")
    private Boolean enabled;
}
