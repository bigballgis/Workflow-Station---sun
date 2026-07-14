package com.developer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 功能单元请求DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FunctionUnitRequest {
    
    @NotBlank(message = "{validation.name_required}")
    @Size(max = 100, message = "{validation.name_max_length}")
    private String name;
    
    private String description;
    
    private Long iconId;

    /** 用户自定义标签（最多 20 个，每项最长 50 字符）。 */
    private List<String> tags;

    /**
     * 创建时所属团队（虚拟组）id 列表 —— 决定 FU 的可见范围（团队 scope）。
     * 仅在 create 时使用；update 不通过本字段变更团队分配（改用 /dev-groups 接口）。
     */
    private List<String> virtualGroupIds;
}
