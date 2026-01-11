package com.admin.component;

import com.admin.dto.request.DepartmentCreateRequest;
import com.admin.dto.response.DepartmentResult;
import com.admin.dto.response.DepartmentTree;
import com.admin.entity.Department;
import com.admin.enums.DepartmentStatus;
import com.admin.exception.*;
import com.admin.repository.DepartmentRepository;
import com.admin.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 组织架构管理组件
 * 负责部门的创建、编辑、层级调整、删除等核心功能
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrganizationManagerComponent {
    
    private final DepartmentRepository departmentRepository;
    private final UserRepository userRepository;
    
    private static final Pattern CODE_PATTERN = Pattern.compile("^[A-Za-z0-9_-]+$");
    
    /**
     * 创建部门
     */
    @Transactional
    public DepartmentResult createDepartment(DepartmentCreateRequest request) {
        log.info("Creating department: {}", request.getCode());
        
        // 验证部门编码格式
        validateDepartmentCode(request.getCode());
        
        // 验证部门编码唯一性
        if (departmentRepository.existsByCode(request.getCode())) {
            throw new AdminBusinessException("CODE_EXISTS", "部门编码已存在: " + request.getCode());
        }
        
        // 验证同级部门名称唯一性
        validateDepartmentNameUnique(request.getName(), request.getParentId(), null);
        
        // 计算层级和路径
        int level = 1;
        String path;
        String departmentId = UUID.randomUUID().toString();
        
        if (request.getParentId() != null && !request.getParentId().isEmpty()) {
            Department parent = departmentRepository.findById(request.getParentId())
                    .orElseThrow(() -> new DepartmentNotFoundException(request.getParentId()));
            level = parent.getLevel() + 1;
            path = parent.getPath() + "/" + departmentId;
        } else {
            path = "/" + departmentId;
        }
        
        Department department = Department.builder()
                .id(departmentId)
                .name(request.getName())
                .code(request.getCode())
                .parentId(request.getParentId())
                .level(level)
                .path(path)
                .managerId(request.getManagerId())
                .secondaryManagerId(request.getSecondaryManagerId())
                .phone(request.getPhone())
                .description(request.getDescription())
                .costCenter(request.getCostCenter())
                .location(request.getLocation())
                .sortOrder(request.getSortOrder() != null ? request.getSortOrder() : 0)
                .status(DepartmentStatus.ACTIVE)
                .build();
        
        departmentRepository.save(department);
        
        log.info("Department created successfully: {}", departmentId);
        return DepartmentResult.success(departmentId, request.getCode());
    }
    
    /**
     * 更新部门
     */
    @Transactional
    public void updateDepartment(String deptId, DepartmentCreateRequest request) {
        log.info("Updating department: {}", deptId);
        
        Department department = departmentRepository.findById(deptId)
                .orElseThrow(() -> new DepartmentNotFoundException(deptId));
        
        // 验证同级部门名称唯一性
        if (!department.getName().equals(request.getName())) {
            validateDepartmentNameUnique(request.getName(), department.getParentId(), deptId);
        }
        
        // 验证副经理存在
        if (request.getSecondaryManagerId() != null && !request.getSecondaryManagerId().isEmpty()) {
            if (!userRepository.existsById(request.getSecondaryManagerId())) {
                throw new AdminBusinessException("SECONDARY_MANAGER_NOT_FOUND", "副经理不存在");
            }
        }
        
        department.setName(request.getName());
        department.setManagerId(request.getManagerId());
        department.setSecondaryManagerId(request.getSecondaryManagerId() != null && request.getSecondaryManagerId().isEmpty() 
                ? null : request.getSecondaryManagerId());
        department.setPhone(request.getPhone());
        department.setDescription(request.getDescription());
        department.setCostCenter(request.getCostCenter());
        department.setLocation(request.getLocation());
        if (request.getSortOrder() != null) {
            department.setSortOrder(request.getSortOrder());
        }
        
        departmentRepository.save(department);
        
        log.info("Department updated successfully: {}", deptId);
    }
    
    /**
     * 调整部门层级 - 检测循环依赖
     */
    @Transactional
    public void moveDepartment(String deptId, String newParentId) {
        log.info("Moving department {} to parent {}", deptId, newParentId);
        
        Department department = departmentRepository.findById(deptId)
                .orElseThrow(() -> new DepartmentNotFoundException(deptId));
        
        // 不能移动到自己
        if (deptId.equals(newParentId)) {
            throw new CircularDependencyException(deptId, newParentId);
        }
        
        // 检测循环依赖
        if (wouldCreateCycle(deptId, newParentId)) {
            throw new CircularDependencyException(deptId, newParentId);
        }
        
        String oldPath = department.getPath();
        int newLevel;
        String newPath;
        
        if (newParentId == null || newParentId.isEmpty()) {
            // 移动到根级别
            newLevel = 1;
            newPath = "/" + deptId;
        } else {
            Department newParent = departmentRepository.findById(newParentId)
                    .orElseThrow(() -> new DepartmentNotFoundException(newParentId));
            newLevel = newParent.getLevel() + 1;
            newPath = newParent.getPath() + "/" + deptId;
        }
        
        // 更新当前部门
        department.setParentId(newParentId);
        department.setLevel(newLevel);
        department.setPath(newPath);
        departmentRepository.save(department);
        
        // 更新所有子部门的路径和层级
        updateDescendantPaths(deptId, oldPath, newPath, newLevel);
        
        log.info("Department moved successfully: {} to {}", deptId, newParentId);
    }
    
    /**
     * 删除部门 - 检查子部门和成员
     */
    @Transactional
    public void deleteDepartment(String deptId) {
        log.info("Deleting department: {}", deptId);
        
        Department department = departmentRepository.findById(deptId)
                .orElseThrow(() -> new DepartmentNotFoundException(deptId));
        
        // 检查是否有子部门
        if (hasChildDepartments(deptId)) {
            throw new DepartmentHasChildrenException(deptId);
        }
        
        // 检查是否有成员
        if (hasDepartmentMembers(deptId)) {
            throw new DepartmentHasMembersException(deptId);
        }
        
        departmentRepository.delete(department);
        
        log.info("Department deleted successfully: {}", deptId);
    }
    
    /**
     * 获取部门详情（包含父部门名称和管理者名称）
     */
    public DepartmentTree getDepartmentDetail(String deptId) {
        log.info("Getting department detail for: {}", deptId);
        
        Department dept = departmentRepository.findById(deptId)
                .orElseThrow(() -> new DepartmentNotFoundException(deptId));
        
        log.info("Department found: {}, parentId: {}", dept.getName(), dept.getParentId());
        
        DepartmentTree tree = DepartmentTree.fromEntity(dept);
        tree.setMemberCount(userRepository.countByDepartmentId(deptId));
        
        // 获取父部门名称
        if (dept.getParentId() != null && !dept.getParentId().isEmpty()) {
            log.info("Looking up parent department: {}", dept.getParentId());
            departmentRepository.findById(dept.getParentId())
                    .ifPresent(parent -> {
                        log.info("Parent department found: {}", parent.getName());
                        tree.setParentName(parent.getName());
                    });
        }
        
        // 获取管理者名称
        if (dept.getManagerId() != null) {
            userRepository.findById(dept.getManagerId())
                    .ifPresent(user -> {
                        tree.setManagerName(user.getFullName());
                        tree.setLeaderName(user.getFullName());
                    });
        }
        
        // 获取副经理名称
        if (dept.getSecondaryManagerId() != null) {
            userRepository.findById(dept.getSecondaryManagerId())
                    .ifPresent(user -> tree.setSecondaryManagerName(user.getFullName()));
        }
        
        log.info("Returning department tree with parentName: {}", tree.getParentName());
        return tree;
    }
    
    /**
     * 获取部门详情
     */
    public Department getDepartment(String deptId) {
        return departmentRepository.findById(deptId)
                .orElseThrow(() -> new DepartmentNotFoundException(deptId));
    }
    
    /**
     * 获取组织架构树
     */
    public List<DepartmentTree> getOrganizationTree() {
        List<Department> allDepts = departmentRepository.findAllActive();
        
        // 获取每个部门的成员数量
        Map<String, Long> memberCounts = new HashMap<>();
        for (Department dept : allDepts) {
            memberCounts.put(dept.getId(), userRepository.countByDepartmentId(dept.getId()));
        }
        
        // 收集所有管理者ID
        Set<String> managerIds = new HashSet<>();
        for (Department dept : allDepts) {
            if (dept.getManagerId() != null) {
                managerIds.add(dept.getManagerId());
            }
            if (dept.getSecondaryManagerId() != null) {
                managerIds.add(dept.getSecondaryManagerId());
            }
        }
        
        // 批量获取管理者名称
        Map<String, String> managerNames = new HashMap<>();
        if (!managerIds.isEmpty()) {
            userRepository.findAllById(managerIds).forEach(user -> 
                managerNames.put(user.getId(), user.getFullName()));
        }
        
        // 构建树形结构
        return buildDepartmentTree(allDepts, memberCounts, managerNames);
    }
    
    /**
     * 搜索部门
     */
    public List<DepartmentTree> searchDepartments(String keyword) {
        List<Department> departments = departmentRepository.searchDepartments(keyword);
        return departments.stream()
                .map(DepartmentTree::fromEntity)
                .collect(Collectors.toList());
    }
    
    /**
     * 获取子部门
     */
    public List<DepartmentTree> getChildDepartments(String parentId) {
        List<Department> children = departmentRepository.findByParentIdOrderBySortOrder(parentId);
        return children.stream()
                .map(DepartmentTree::fromEntity)
                .collect(Collectors.toList());
    }
    
    /**
     * 检测是否会造成循环依赖
     */
    public boolean wouldCreateCycle(String deptId, String newParentId) {
        if (newParentId == null || newParentId.isEmpty()) {
            return false;
        }
        
        // 检查新父部门是否是当前部门的后代
        Department newParent = departmentRepository.findById(newParentId).orElse(null);
        if (newParent == null) {
            return false;
        }
        
        Department current = departmentRepository.findById(deptId).orElse(null);
        if (current == null) {
            return false;
        }
        
        // 如果新父部门的路径包含当前部门的ID，则会造成循环
        return newParent.getPath() != null && newParent.getPath().contains("/" + deptId);
    }
    
    /**
     * 检查是否有子部门
     */
    public boolean hasChildDepartments(String deptId) {
        return departmentRepository.existsByParentId(deptId);
    }
    
    /**
     * 检查是否有部门成员
     */
    public boolean hasDepartmentMembers(String deptId) {
        return userRepository.countByDepartmentId(deptId) > 0;
    }
    
    /**
     * 验证部门编码格式
     */
    public void validateDepartmentCode(String code) {
        if (code == null || !CODE_PATTERN.matcher(code).matches()) {
            throw new AdminBusinessException("INVALID_CODE", 
                    "部门编码格式无效，只能包含字母、数字、下划线和连字符");
        }
    }
    
    /**
     * 验证同级部门名称唯一性
     */
    private void validateDepartmentNameUnique(String name, String parentId, String excludeId) {
        if (departmentRepository.existsByNameAndParentIdExcluding(name, parentId, 
                excludeId != null ? excludeId : "")) {
            throw new AdminBusinessException("NAME_EXISTS", 
                    "同级部门下已存在相同名称的部门: " + name);
        }
    }
    
    /**
     * 更新后代部门的路径和层级
     */
    private void updateDescendantPaths(String deptId, String oldPath, String newPath, int parentLevel) {
        List<Department> descendants = departmentRepository.findByPathStartingWith(oldPath + "/");
        
        for (Department descendant : descendants) {
            String updatedPath = descendant.getPath().replace(oldPath, newPath);
            int levelDiff = parentLevel - (descendant.getLevel() - 1);
            
            descendant.setPath(updatedPath);
            descendant.setLevel(descendant.getLevel() + levelDiff);
            departmentRepository.save(descendant);
        }
    }
    
    /**
     * 构建部门树形结构
     */
    private List<DepartmentTree> buildDepartmentTree(List<Department> departments, 
                                                      Map<String, Long> memberCounts,
                                                      Map<String, String> managerNames) {
        Map<String, DepartmentTree> treeMap = new HashMap<>();
        Map<String, String> deptNames = new HashMap<>();
        List<DepartmentTree> roots = new ArrayList<>();
        
        // 先收集所有部门名称用于查找父部门名称
        for (Department dept : departments) {
            deptNames.put(dept.getId(), dept.getName());
        }
        
        // 创建所有节点
        for (Department dept : departments) {
            DepartmentTree tree = DepartmentTree.fromEntity(dept);
            tree.setMemberCount(memberCounts.getOrDefault(dept.getId(), 0L));
            
            // 设置父部门名称
            if (dept.getParentId() != null && !dept.getParentId().isEmpty()) {
                tree.setParentName(deptNames.get(dept.getParentId()));
            }
            
            // 设置管理者名称 (leaderName 和 managerName 都指向同一个人)
            if (dept.getManagerId() != null) {
                String leaderName = managerNames.get(dept.getManagerId());
                tree.setManagerName(leaderName);
                tree.setLeaderName(leaderName);
            }
            if (dept.getSecondaryManagerId() != null) {
                tree.setSecondaryManagerName(managerNames.get(dept.getSecondaryManagerId()));
            }
            treeMap.put(dept.getId(), tree);
        }
        
        // 构建树形关系
        for (Department dept : departments) {
            DepartmentTree tree = treeMap.get(dept.getId());
            if (dept.getParentId() == null || dept.getParentId().isEmpty()) {
                roots.add(tree);
            } else {
                DepartmentTree parent = treeMap.get(dept.getParentId());
                if (parent != null) {
                    parent.getChildren().add(tree);
                } else {
                    roots.add(tree);
                }
            }
        }
        
        // 排序
        roots.sort(Comparator.comparingInt(DepartmentTree::getSortOrder));
        for (DepartmentTree tree : treeMap.values()) {
            tree.getChildren().sort(Comparator.comparingInt(DepartmentTree::getSortOrder));
        }
        
        return roots;
    }
}
