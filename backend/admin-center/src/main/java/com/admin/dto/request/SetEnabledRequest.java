package com.admin.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 切换功能单元启用/禁用状态的请求 DTO
 *
 * <p><b>Validates: Requirements 33.1, 33.2</b>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SetEnabledRequest {

    @NotNull(message = "{functionUnit.enabled.notNull}")
    private Boolean enabled;
}
