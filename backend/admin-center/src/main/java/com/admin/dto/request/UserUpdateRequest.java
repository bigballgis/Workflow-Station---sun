package com.admin.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户更新请求DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserUpdateRequest {
    
    @Email(message = "邮箱格式不正确")
    private String email;
    
    @Size(max = 100, message = "姓名长度不能超过100")
    private String fullName;
    
    @Size(max = 50, message = "工号长度不能超过50")
    private String employeeId;
    
    private String businessUnitId;
    
    @Size(max = 100, message = "职位长度不能超过100")
    private String position;
    
    private String entityManagerId;
    
    private String functionManagerId;
}
