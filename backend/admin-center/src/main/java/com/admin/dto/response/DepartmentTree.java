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
 * Note: 保留 DepartmentTree 名称以保持向后兼容
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DepartmentTree {
    
    private String id;
    private String name;
    private String code;
    private String parentId;
    private String parentName;
    private Integer level;
    private String path;
    private BusinessUnitStatus status;
    private Integer sortOrder;
    private Long memberCount;
    
    @Builder.Default
    private List<DepartmentTree> children = new ArrayList<>();
    
    public static DepartmentTree fromEntity(BusinessUnit bu) {
        return DepartmentTree.builder()
                .id(bu.getId())
                .name(bu.getName())
                .code(bu.getCode())
                .parentId(bu.getParentId())
                .level(bu.getLevel())
                .path(bu.getPath())
                .status(bu.getStatus())
                .sortOrder(bu.getSortOrder())
                .memberCount(bu.getMemberCount())
                .children(new ArrayList<>())
                .build();
    }
}
