package com.admin.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 虚拟组成员请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VirtualGroupMemberRequest {
    
    @NotBlank(message = "用户ID不能为空")
    private String userId;
}
