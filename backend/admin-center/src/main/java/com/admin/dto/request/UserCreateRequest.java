package com.admin.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户创建请求DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserCreateRequest {
    
    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 100, message = "用户名长度必须在3-100之间")
    private String username;
    
    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    private String email;
    
    @NotBlank(message = "姓名不能为空")
    @Size(max = 100, message = "姓名长度不能超过100")
    private String fullName;
    
    @Size(max = 50, message = "工号长度不能超过50")
    private String employeeId;
    
    private String businessUnitId;
    
    @Size(max = 100, message = "职位长度不能超过100")
    private String position;
    
    @NotBlank(message = "初始密码不能为空")
    @Size(min = 8, max = 128, message = "密码长度必须在8-128之间")
    private String initialPassword;
}
