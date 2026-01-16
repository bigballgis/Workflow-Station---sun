package com.admin.dto.request;

import com.admin.enums.UserStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户查询请求DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserQueryRequest {
    
    private String keyword;
    private String businessUnitId;
    private UserStatus status;
    
    @Builder.Default
    private int page = 0;
    
    @Builder.Default
    private int size = 20;
}
