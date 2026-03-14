package com.admin.dto.request;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * N8N 连接配置更新请求 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class N8nConfigUpdateRequest {

    @Size(max = 100, message = "配置名称长度不能超过100")
    private String name;

    @Size(max = 500, message = "N8N 服务地址长度不能超过500")
    private String baseUrl;

    private String apiKey;

    private Boolean isActive;
}
