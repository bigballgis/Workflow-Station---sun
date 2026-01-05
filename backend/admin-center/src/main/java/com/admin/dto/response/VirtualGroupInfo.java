package com.admin.dto.response;

import com.admin.entity.VirtualGroup;
import com.admin.enums.VirtualGroupType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 虚拟组信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VirtualGroupInfo {
    
    private String id;
    private String name;
    private VirtualGroupType type;
    private String description;
    private Instant validFrom;
    private Instant validTo;
    private String status;
    private int memberCount;
    private boolean valid;
    private boolean expired;
    private Instant createdAt;
    private String createdBy;
    
    public static VirtualGroupInfo fromEntity(VirtualGroup group) {
        return VirtualGroupInfo.builder()
                .id(group.getId())
                .name(group.getName())
                .type(group.getType())
                .description(group.getDescription())
                .validFrom(group.getValidFrom())
                .validTo(group.getValidTo())
                .status(group.getStatus())
                .memberCount(group.getMemberCount())
                .valid(group.isValid())
                .expired(group.isExpired())
                .createdAt(group.getCreatedAt())
                .createdBy(group.getCreatedBy())
                .build();
    }
}
