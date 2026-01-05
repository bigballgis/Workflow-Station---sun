package com.admin.dto.response;

import com.admin.entity.Department;
import com.admin.enums.DepartmentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 部门树形结构DTO
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
    private Integer level;
    private String path;
    private String managerId;
    private String managerName;
    private DepartmentStatus status;
    private Integer sortOrder;
    private Long memberCount;
    
    @Builder.Default
    private List<DepartmentTree> children = new ArrayList<>();
    
    public static DepartmentTree fromEntity(Department dept) {
        return DepartmentTree.builder()
                .id(dept.getId())
                .name(dept.getName())
                .code(dept.getCode())
                .parentId(dept.getParentId())
                .level(dept.getLevel())
                .path(dept.getPath())
                .managerId(dept.getManagerId())
                .status(dept.getStatus())
                .sortOrder(dept.getSortOrder())
                .memberCount(dept.getMemberCount())
                .children(new ArrayList<>())
                .build();
    }
}
