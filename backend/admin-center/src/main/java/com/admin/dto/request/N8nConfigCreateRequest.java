package com.admin.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * N8N 连接配置创建请求 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class N8nConfigCreateRequest {

    @NotBlank(message = "配置名称不能为空")
    @Size(max = 100, message = "配置名称长度不能超过100")
    private String name;

    @NotBlank(message = "N8N 服务地址不能为空")
    @Size(max = 500, message = "N8N 服务地址长度不能超过500")
    private String baseUrl;

    @NotBlank(message = "API 密钥不能为空")
    private String apiKey;

    @Builder.Default
    private Boolean isActive = true;
}
