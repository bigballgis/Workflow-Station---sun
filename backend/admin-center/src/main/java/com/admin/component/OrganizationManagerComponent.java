package com.admin.component;

import com.admin.dto.request.BusinessUnitCreateRequest;
import com.admin.dto.request.BusinessUnitUpdateRequest;
import com.admin.dto.response.BusinessUnitResult;
import com.admin.dto.response.BusinessUnitTree;
import com.platform.security.entity.BusinessUnit;
import com.admin.enums.BusinessUnitStatus;
import com.admin.util.EntityTypeConverter;
import com.admin.exception.*;
import com.admin.repository.BusinessUnitRepository;
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
 * 负责业务单元的创建、编辑、层级调整、删除等核心功能
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrganizationManagerComponent {
    
    private final BusinessUnitRepository businessUnitRepository;
    private final UserRepository userRepository;
    
    private static final Pattern CODE_PATTERN = Pattern.compile("^[A-Za-z0-9_-]+$");
    
    /**
     * 创建业务单元
     */
    @Transactional
    public BusinessUnitResult createBusinessUnit(BusinessUnitCreateRequest request) {
        log.info("Creating business unit: {}", request.getCode());
        
        // 验证业务单元编码格式
        validateBusinessUnitCode(request.getCode());
        
        // 验证业务单元编码唯一性
        if (businessUnitRepository.existsByCode(request.getCode())) {
            throw new AdminBusinessException("CODE_EXISTS", "业务单元编码已存在: " + request.getCode());
        }
        
        // 验证同级业务单元名称唯一性
        validateBusinessUnitNameUnique(request.getName(), request.getParentId(), null);
        
        // 计算层级和路径
        int level = 1;
        String path;
        String unitId = UUID.randomUUID().toString();
        
        if (request.getParentId() != null && !request.getParentId().isEmpty()) {
            BusinessUnit parent = businessUnitRepository.findById(request.getParentId())
                    .orElseThrow(() -> new BusinessUnitNotFoundException(request.getParentId()));
            level = parent.getLevel() + 1;
            path = parent.getPath() + "/" + unitId;
        } else {
            path = "/" + unitId;
        }
        
        BusinessUnit businessUnit = BusinessUnit.builder()
                .id(unitId)
                .name(request.getName())
                .code(request.getCode())
                .parentId(request.getParentId())
                .level(level)
                .path(path)
                .phone(request.getPhone())
                .description(request.getDescription())
                .costCenter(request.getCostCenter())
                .location(request.getLocation())
                .sortOrder(request.getSortOrder() != null ? request.getSortOrder() : 0)
                .status(EntityTypeConverter.fromBusinessUnitStatus(BusinessUnitStatus.ACTIVE))
                .build();
        
        businessUnitRepository.save(businessUnit);
        
        log.info("Business unit created successfully: {}", unitId);
        return BusinessUnitResult.success(unitId, request.getCode());
    }
    
    /**
     * 更新业务单元
     */
    @Transactional
    public void updateBusinessUnit(String unitId, BusinessUnitUpdateRequest request) {
        log.info("Updating business unit: {}", unitId);
        
        BusinessUnit businessUnit = businessUnitRepository.findById(unitId)
                .orElseThrow(() -> new BusinessUnitNotFoundException(unitId));
        
        // 验证同级业务单元名称唯一性
        if (!businessUnit.getName().equals(request.getName())) {
            validateBusinessUnitNameUnique(request.getName(), businessUnit.getParentId(), unitId);
        }
        
        businessUnit.setName(request.getName());
        businessUnit.setPhone(request.getPhone());
        businessUnit.setDescription(request.getDescription());
        businessUnit.setCostCenter(request.getCostCenter());
        businessUnit.setLocation(request.getLocation());
        if (request.getSortOrder() != null) {
            businessUnit.setSortOrder(request.getSortOrder());
        }
        
        businessUnitRepository.save(businessUnit);
        
        log.info("Business unit updated successfully: {}", unitId);
    }
    
    /**
     * 调整业务单元层级 - 检测循环依赖
     */
    @Transactional
    public void moveBusinessUnit(String unitId, String newParentId) {
        log.info("Moving business unit {} to parent {}", unitId, newParentId);
        
        BusinessUnit businessUnit = businessUnitRepository.findById(unitId)
                .orElseThrow(() -> new BusinessUnitNotFoundException(unitId));
        
        // 不能移动到自己
        if (unitId.equals(newParentId)) {
            throw new CircularDependencyException(unitId, newParentId);
        }
        
        // 检测循环依赖
        if (wouldCreateCycle(unitId, newParentId)) {
            throw new CircularDependencyException(unitId, newParentId);
        }
        
        String oldPath = businessUnit.getPath();
        int newLevel;
        String newPath;
        
        if (newParentId == null || newParentId.isEmpty()) {
            // 移动到根级别
            newLevel = 1;
            newPath = "/" + unitId;
        } else {
            BusinessUnit newParent = businessUnitRepository.findById(newParentId)
                    .orElseThrow(() -> new BusinessUnitNotFoundException(newParentId));
            newLevel = newParent.getLevel() + 1;
            newPath = newParent.getPath() + "/" + unitId;
        }
        
        // 更新当前业务单元
        businessUnit.setParentId(newParentId);
        businessUnit.setLevel(newLevel);
        businessUnit.setPath(newPath);
        businessUnitRepository.save(businessUnit);
        
        // 更新所有子业务单元的路径和层级
        updateDescendantPaths(unitId, oldPath, newPath, newLevel);
        
        log.info("Business unit moved successfully: {} to {}", unitId, newParentId);
    }
    
    /**
     * 删除业务单元 - 检查子业务单元和成员
     */
    @Transactional
    public void deleteBusinessUnit(String unitId) {
        log.info("Deleting business unit: {}", unitId);
        
        BusinessUnit businessUnit = businessUnitRepository.findById(unitId)
                .orElseThrow(() -> new BusinessUnitNotFoundException(unitId));
        
        // 检查是否有子业务单元
        if (hasChildBusinessUnits(unitId)) {
            throw new BusinessUnitHasChildrenException(unitId);
        }
        
        // 检查是否有成员
        if (hasBusinessUnitMembers(unitId)) {
            throw new BusinessUnitHasMembersException(unitId);
        }
        
        businessUnitRepository.delete(businessUnit);
        
        log.info("Business unit deleted successfully: {}", unitId);
    }
    
    /**
     * 获取业务单元详情（包含父业务单元名称和管理者名称）
     */
    public BusinessUnitTree getBusinessUnitDetail(String unitId) {
        log.info("Getting business unit detail for: {}", unitId);
        
        BusinessUnit unit = businessUnitRepository.findById(unitId)
                .orElseThrow(() -> new BusinessUnitNotFoundException(unitId));
        
        log.info("Business unit found: {}, parentId: {}", unit.getName(), unit.getParentId());
        
        BusinessUnitTree tree = BusinessUnitTree.fromEntity(unit);
        tree.setMemberCount(userRepository.countMembersByBusinessUnitId(unitId));
        
        // 获取父业务单元名称
        if (unit.getParentId() != null && !unit.getParentId().isEmpty()) {
            log.info("Looking up parent business unit: {}", unit.getParentId());
            businessUnitRepository.findById(unit.getParentId())
                    .ifPresent(parent -> {
                        log.info("Parent business unit found: {}", parent.getName());
                        tree.setParentName(parent.getName());
                    });
        }
        
        log.info("Returning business unit tree with parentName: {}", tree.getParentName());
        return tree;
    }
    
    /**
     * 获取业务单元详情
     */
    public BusinessUnit getBusinessUnit(String unitId) {
        return businessUnitRepository.findById(unitId)
                .orElseThrow(() -> new BusinessUnitNotFoundException(unitId));
    }
    
    /**
     * 获取业务单元树
     */
    public List<BusinessUnitTree> getBusinessUnitTree() {
        List<BusinessUnit> allUnits = businessUnitRepository.findAllActive();
        
        // 获取每个业务单元的成员数量（通过关联表）
        Map<String, Long> memberCounts = new HashMap<>();
        for (BusinessUnit unit : allUnits) {
            memberCounts.put(unit.getId(), userRepository.countMembersByBusinessUnitId(unit.getId()));
        }
        
        // 构建树形结构
        return buildBusinessUnitTree(allUnits, memberCounts);
    }
    
    /**
     * 搜索业务单元
     */
    public List<BusinessUnitTree> searchBusinessUnits(String keyword) {
        List<BusinessUnit> units = businessUnitRepository.searchBusinessUnits(keyword);
        return units.stream()
                .map(BusinessUnitTree::fromEntity)
                .collect(Collectors.toList());
    }
    
    /**
     * 获取子业务单元
     */
    public List<BusinessUnitTree> getChildBusinessUnits(String parentId) {
        List<BusinessUnit> children = businessUnitRepository.findByParentIdOrderBySortOrder(parentId);
        return children.stream()
                .map(BusinessUnitTree::fromEntity)
                .collect(Collectors.toList());
    }
    
    /**
     * 检测是否会造成循环依赖
     */
    public boolean wouldCreateCycle(String unitId, String newParentId) {
        if (newParentId == null || newParentId.isEmpty()) {
            return false;
        }
        
        // 检查新父业务单元是否是当前业务单元的后代
        BusinessUnit newParent = businessUnitRepository.findById(newParentId).orElse(null);
        if (newParent == null) {
            return false;
        }
        
        BusinessUnit current = businessUnitRepository.findById(unitId).orElse(null);
        if (current == null) {
            return false;
        }
        
        // 如果新父业务单元的路径包含当前业务单元的ID，则会造成循环
        return newParent.getPath() != null && newParent.getPath().contains("/" + unitId);
    }
    
    /**
     * 检查是否有子业务单元
     */
    public boolean hasChildBusinessUnits(String unitId) {
        return businessUnitRepository.existsByParentId(unitId);
    }
    
    /**
     * 检查是否有业务单元成员（通过关联表）
     */
    public boolean hasBusinessUnitMembers(String unitId) {
        return userRepository.countMembersByBusinessUnitId(unitId) > 0;
    }
    
    /**
     * 验证业务单元编码格式
     */
    public void validateBusinessUnitCode(String code) {
        if (code == null || !CODE_PATTERN.matcher(code).matches()) {
            throw new AdminBusinessException("INVALID_CODE", 
                    "业务单元编码格式无效，只能包含字母、数字、下划线和连字符");
        }
    }
    
    /**
     * 验证同级业务单元名称唯一性
     */
    private void validateBusinessUnitNameUnique(String name, String parentId, String excludeId) {
        if (businessUnitRepository.existsByNameAndParentIdExcluding(name, parentId, 
                excludeId != null ? excludeId : "")) {
            throw new AdminBusinessException("NAME_EXISTS", 
                    "同级业务单元下已存在相同名称的业务单元: " + name);
        }
    }
    
    /**
     * 更新后代业务单元的路径和层级
     */
    private void updateDescendantPaths(String unitId, String oldPath, String newPath, int parentLevel) {
        List<BusinessUnit> descendants = businessUnitRepository.findByPathStartingWith(oldPath + "/");
        
        for (BusinessUnit descendant : descendants) {
            String updatedPath = descendant.getPath().replace(oldPath, newPath);
            int levelDiff = parentLevel - (descendant.getLevel() - 1);
            
            descendant.setPath(updatedPath);
            descendant.setLevel(descendant.getLevel() + levelDiff);
            businessUnitRepository.save(descendant);
        }
    }
    
    /**
     * 构建业务单元树形结构
     */
    private List<BusinessUnitTree> buildBusinessUnitTree(List<BusinessUnit> units, 
                                                          Map<String, Long> memberCounts) {
        Map<String, BusinessUnitTree> treeMap = new HashMap<>();
        Map<String, String> unitNames = new HashMap<>();
        List<BusinessUnitTree> roots = new ArrayList<>();
        
        // 先收集所有业务单元名称用于查找父业务单元名称
        for (BusinessUnit unit : units) {
            unitNames.put(unit.getId(), unit.getName());
        }
        
        // 创建所有节点
        for (BusinessUnit unit : units) {
            BusinessUnitTree tree = BusinessUnitTree.fromEntity(unit);
            tree.setMemberCount(memberCounts.getOrDefault(unit.getId(), 0L));
            
            // 设置父业务单元名称
            if (unit.getParentId() != null && !unit.getParentId().isEmpty()) {
                tree.setParentName(unitNames.get(unit.getParentId()));
            }
            
            treeMap.put(unit.getId(), tree);
        }
        
        // 构建树形关系
        for (BusinessUnit unit : units) {
            BusinessUnitTree tree = treeMap.get(unit.getId());
            if (unit.getParentId() == null || unit.getParentId().isEmpty()) {
                roots.add(tree);
            } else {
                BusinessUnitTree parent = treeMap.get(unit.getParentId());
                if (parent != null) {
                    parent.getChildren().add(tree);
                } else {
                    roots.add(tree);
                }
            }
        }
        
        // 排序
        roots.sort(Comparator.comparingInt(tree -> tree.getSortOrder() != null ? tree.getSortOrder() : 0));
        for (BusinessUnitTree tree : treeMap.values()) {
            tree.getChildren().sort(Comparator.comparingInt(child -> child.getSortOrder() != null ? child.getSortOrder() : 0));
        }
        
        return roots;
    }
}
