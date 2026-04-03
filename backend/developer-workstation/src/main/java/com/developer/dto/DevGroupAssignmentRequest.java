package com.developer.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 将设计站功能单元分配给虚拟开发组（sys_virtual_groups.id）
 */
@Data
public class DevGroupAssignmentRequest {

    @NotNull
    private List<String> virtualGroupIds = new ArrayList<>();
}
