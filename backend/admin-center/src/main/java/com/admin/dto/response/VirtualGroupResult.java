package com.admin.dto.response;

import com.platform.security.entity.VirtualGroup;
import com.admin.enums.VirtualGroupType;
import com.admin.util.EntityTypeConverter;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 虚拟组操作结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VirtualGroupResult {
    
    private boolean success;
    private String groupId;
    private String name;
    private VirtualGroupType type;
    private String message;
    
    public static VirtualGroupResult success(VirtualGroup group) {
        return VirtualGroupResult.builder()
                .success(true)
                .groupId(group.getId())
                .name(group.getName())
                .type(EntityTypeConverter.toVirtualGroupType(group.getType()))
                .message("操作成功")
                .build();
    }
    
    public static VirtualGroupResult success(String groupId, String name) {
        return VirtualGroupResult.builder()
                .success(true)
                .groupId(groupId)
                .name(name)
                .message("操作成功")
                .build();
    }
    
    public static VirtualGroupResult failure(String message) {
        return VirtualGroupResult.builder()
                .success(false)
                .message(message)
                .build();
    }
}
