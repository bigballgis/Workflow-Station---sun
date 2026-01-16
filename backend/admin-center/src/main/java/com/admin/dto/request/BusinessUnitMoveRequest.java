package com.admin.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 业务单元移动请求DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BusinessUnitMoveRequest {
    
    private String newParentId;
}
