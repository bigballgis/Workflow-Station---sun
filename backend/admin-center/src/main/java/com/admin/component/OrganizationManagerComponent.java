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
            throw new AdminBusinessException("CODE_EXISTS", "Business unit code already exists: " + request.getCode());
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
     * 调整业务单元层级（不改同级顺序）
     */
    @Transactional
    public void moveBusinessUnit(String unitId, String newParentId) {
        moveBusinessUnit(unitId, newParentId, null);
    }

    /**
     * 调整业务单元层级 - 检测循环依赖，连带整棵子树重算 level/path，可选在新父级下重排同级。
     * <p>
     * 成员（sys_user_business_units / sys_user_business_unit_roles）、准入角色（sys_business_unit_roles）、
     * 审批人（sys_approvers）都按业务单元 ID 挂接，移动时无需改写，自然随节点一起迁移。
     *
     * @param sortOrder 在新父级 ACTIVE 同级中的位置（0 起）；null 表示不重排
     */
    @Transactional
    public void moveBusinessUnit(String unitId, String newParentId, Integer sortOrder) {
        String targetParentId = (newParentId == null || newParentId.isEmpty()) ? null : newParentId;
        log.info("Moving business unit {} to parent {} (sortOrder={})", unitId, targetParentId, sortOrder);

        BusinessUnit businessUnit = businessUnitRepository.findById(unitId)
                .orElseThrow(() -> new BusinessUnitNotFoundException(unitId));

        // 不能移动到自己 / 自己的后代
        if (unitId.equals(targetParentId) || wouldCreateCycle(unitId, targetParentId)) {
            throw new CircularDependencyException(unitId, targetParentId);
        }

        BusinessUnit newParent = null;
        if (targetParentId != null) {
            newParent = businessUnitRepository.findById(targetParentId)
                    .orElseThrow(() -> new BusinessUnitNotFoundException(newParentId));
        }

        boolean parentChanged = !Objects.equals(businessUnit.getParentId(), targetParentId);
        if (parentChanged) {
            validateBusinessUnitNameUnique(businessUnit.getName(), targetParentId, unitId);
        }

        businessUnit.setParentId(targetParentId);
        businessUnit.setLevel(newParent == null ? 1 : newParent.getLevel() + 1);
        businessUnit.setPath((newParent == null ? "" : newParent.getPath()) + "/" + unitId);
        businessUnitRepository.save(businessUnit);

        // 整棵子树按 parentId 递归重算，不依赖旧 path 的字符串形态（种子数据的 path 可能是 code 而非 id）
        relocateDescendants(businessUnit);

        if (sortOrder != null) {
            resequenceSiblings(businessUnit, targetParentId, sortOrder);
        }

        log.info("Business unit moved successfully: {} to {}", unitId, targetParentId);
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
     * 获取业务单元平铺列表（供下拉框等场景使用）
     */
    @Transactional(readOnly = true)
    public List<BusinessUnitTree> listBusinessUnits(String parentId, String status) {
        List<BusinessUnit> units;
        if (parentId != null && !parentId.isEmpty()) {
            units = businessUnitRepository.findByParentIdOrderBySortOrder(parentId);
        } else if (status != null && !status.isEmpty()) {
            units = businessUnitRepository.findByStatus(status);
        } else {
            units = businessUnitRepository.findAllActive();
        }
        return units.stream()
                .map(BusinessUnitTree::fromEntity)
                .collect(Collectors.toList());
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

        // 沿 parentId 链从新父级向上走：碰到自己即成环。
        // 不用 path 字符串判断——种子数据的 path 可能是 code 拼成的，含不了 id。
        Set<String> visited = new HashSet<>();
        String cursor = newParentId;
        while (cursor != null && !cursor.isEmpty() && visited.add(cursor)) {
            if (cursor.equals(unitId)) {
                return true;
            }
            BusinessUnit node = businessUnitRepository.findById(cursor).orElse(null);
            if (node == null) {
                return false;
            }
            cursor = node.getParentId();
        }
        return false;
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
     * 按 parentId 逐层重算整棵子树的 level / path（子级、孙级……全部跟随）
     */
    private void relocateDescendants(BusinessUnit root) {
        Deque<BusinessUnit> queue = new ArrayDeque<>();
        Set<String> visited = new HashSet<>();
        queue.add(root);
        visited.add(root.getId());

        while (!queue.isEmpty()) {
            BusinessUnit parent = queue.poll();
            for (BusinessUnit child : businessUnitRepository.findByParentIdOrderBySortOrder(parent.getId())) {
                if (!visited.add(child.getId())) {
                    continue; // 脏数据成环保护
                }
                child.setLevel(parent.getLevel() + 1);
                child.setPath(parent.getPath() + "/" + child.getId());
                businessUnitRepository.save(child);
                queue.add(child);
            }
        }
    }

    /**
     * 在新父级下重排同级：把 unit 插到 ACTIVE 同级的第 position 位，其余顺序不变；INACTIVE 同级排在最后。
     * 前端树只显示 ACTIVE 节点，拖拽给出的下标也只数 ACTIVE。
     */
    private void resequenceSiblings(BusinessUnit unit, String parentId, int position) {
        List<BusinessUnit> siblings = parentId == null
                ? businessUnitRepository.findRootBusinessUnits()
                : businessUnitRepository.findByParentIdOrderBySortOrder(parentId);

        String activeStatus = EntityTypeConverter.fromBusinessUnitStatus(BusinessUnitStatus.ACTIVE);
        List<BusinessUnit> active = new ArrayList<>();
        List<BusinessUnit> inactive = new ArrayList<>();
        for (BusinessUnit sibling : siblings) {
            if (unit.getId().equals(sibling.getId())) {
                continue;
            }
            (activeStatus.equals(sibling.getStatus()) ? active : inactive).add(sibling);
        }

        int index = Math.max(0, Math.min(position, active.size()));
        active.add(index, unit);
        active.addAll(inactive);

        List<BusinessUnit> changed = new ArrayList<>();
        for (int i = 0; i < active.size(); i++) {
            BusinessUnit sibling = active.get(i);
            if (sibling.getSortOrder() == null || sibling.getSortOrder() != i) {
                sibling.setSortOrder(i);
                changed.add(sibling);
            }
        }
        if (!changed.isEmpty()) {
            businessUnitRepository.saveAll(changed);
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
        
        // 排序：第一级固定按名称字母序（不受 sortOrder / 拖拽影响），其余层级按 sortOrder，同序再按名称
        roots.sort(ROOT_ORDER);
        for (BusinessUnitTree tree : treeMap.values()) {
            tree.getChildren().sort(CHILD_ORDER);
        }

        return roots;
    }

    private static final Comparator<BusinessUnitTree> BY_NAME = Comparator
            .comparing((BusinessUnitTree t) -> t.getName() == null ? "" : t.getName(), String.CASE_INSENSITIVE_ORDER)
            .thenComparing(t -> t.getCode() == null ? "" : t.getCode(), String.CASE_INSENSITIVE_ORDER);

    static final Comparator<BusinessUnitTree> ROOT_ORDER = BY_NAME;

    static final Comparator<BusinessUnitTree> CHILD_ORDER = Comparator
            .comparingInt((BusinessUnitTree t) -> t.getSortOrder() != null ? t.getSortOrder() : 0)
            .thenComparing(BY_NAME);
}
