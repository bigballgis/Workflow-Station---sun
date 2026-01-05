package com.admin.dto.request;

import com.admin.enums.VirtualGroupType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 虚拟组创建请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VirtualGroupCreateRequest {
    
    @NotBlank(message = "虚拟组名称不能为空")
    @Size(max = 100, message = "虚拟组名称长度不能超过100个字符")
    private String name;
    
    @NotNull(message = "虚拟组类型不能为空")
    private VirtualGroupType type;
    
    @Size(max = 500, message = "描述长度不能超过500个字符")
    private String description;
    
    private Instant validFrom;
    
    private Instant validTo;
}
