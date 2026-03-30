package com.admin.dto.request;

import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 批量删除功能单元的请求 DTO
 *
 * <p><b>Validates: Requirements 20.1, 20.3</b>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BatchDeleteRequest {

    @NotEmpty(message = "{functionUnit.batch.ids.notEmpty}")
    private List<String> ids;
}
