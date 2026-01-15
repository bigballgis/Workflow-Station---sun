package com.admin.dto.response;

import com.admin.entity.BusinessUnit;
import com.admin.enums.BusinessUnitStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 业务单元树形结构DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BusinessUnitTree {
    
    private String id;
    private String name;
    private String code;
    private String parentId;
    private String parentName;
    private Integer level;
    private String path;
    private String managerId;
    private String managerName;
    private String leaderName;
    private String secondaryManagerId;
    private String secondaryManagerName;
    private BusinessUnitStatus status;
    private Integer sortOrder;
    private Long memberCount;
    
    @Builder.Default
    private List<BusinessUnitTree> children = new ArrayList<>();
    
    public static BusinessUnitTree fromEntity(BusinessUnit unit) {
        return BusinessUnitTree.builder()
                .id(unit.getId())
                .name(unit.getName())
                .code(unit.getCode())
                .parentId(unit.getParentId())
                .level(unit.getLevel())
                .path(unit.getPath())
                .managerId(unit.getManagerId())
                .secondaryManagerId(unit.getSecondaryManagerId())
                .status(unit.getStatus())
                .sortOrder(unit.getSortOrder())
                .memberCount(unit.getMemberCount())
                .children(new ArrayList<>())
                .build();
    }
}
