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
    
    /** 目标父业务单元 ID；null / 空串表示移动到根级 */
    private String newParentId;

    /** 在新父级下的位置（0 起、只数 ACTIVE 同级）；null 表示保持原 sortOrder、不重排同级 */
    private Integer sortOrder;
}
