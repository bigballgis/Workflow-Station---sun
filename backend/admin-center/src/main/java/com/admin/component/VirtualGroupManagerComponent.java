package com.admin.component;

import com.admin.dto.request.VirtualGroupCreateRequest;
import com.admin.dto.request.VirtualGroupMemberRequest;
import com.admin.dto.response.VirtualGroupInfo;
import com.admin.dto.response.VirtualGroupMemberInfo;
import com.admin.dto.response.VirtualGroupResult;
import com.platform.security.entity.Role;
import com.platform.security.entity.User;
import com.platform.security.entity.VirtualGroup;
import com.platform.security.entity.VirtualGroupMember;
import com.platform.security.entity.VirtualGroupRole;
import com.admin.enums.VirtualGroupType;
import com.admin.exception.AdminBusinessException;
import com.admin.util.EntityTypeConverter;
import com.admin.exception.VirtualGroupNotFoundException;
import com.admin.repository.FunctionUnitRepository;
import com.admin.repository.RoleRepository;
import com.admin.repository.UserRepository;
import com.admin.repository.VirtualGroupMemberRepository;
import com.admin.repository.VirtualGroupRepository;
import com.admin.repository.VirtualGroupRoleRepository;
import com.platform.common.i18n.I18nService;
import com.platform.security.service.UserRoleService;
import com.platform.security.util.SecurityContextUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 虚拟组管理组件
 * 负责虚拟组的创建、配置、成员管理和生命周期管理
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VirtualGroupManagerComponent {

    /** Length of {@code sys_virtual_groups.code}. */
    private static final int CODE_MAX_LENGTH = 50;

    private final VirtualGroupRepository virtualGroupRepository;
    private final VirtualGroupMemberRepository virtualGroupMemberRepository;
    private final VirtualGroupRoleRepository virtualGroupRoleRepository;
    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final JdbcTemplate jdbcTemplate;
    private final com.admin.repository.UserBusinessUnitRepository userBusinessUnitRepository;
    private final I18nService i18nService;
    private final UserRoleService userRoleService;


    /**
     * 创建虚拟组。团队（DEVELOPER）的 code 一律由名称生成、不收手填值，保证各环境同名团队 code 相同（§5.4，C-7）；
     * 名称生成不出 code（如纯中文）时拒绝。只有团队可以配 Technical Lead 的 AD 组。
     */
    @Transactional
    public VirtualGroupResult createVirtualGroup(VirtualGroupCreateRequest request) {
        log.info("Creating virtual group: {}", request.getName());
        assertUserCreatableType(request.getType());
        boolean team = request.getType() == VirtualGroupType.DEVELOPER;

        // 验证名称唯一性
        if (virtualGroupRepository.existsByName(request.getName())) {
            throw new AdminBusinessException("NAME_EXISTS", "Virtual group name already exists: " + request.getName());
        }
        if (team && hasText(request.getCode())) {
            throw new AdminBusinessException("TEAM_CODE_FROM_NAME", i18nService.getMessage("admin.vg.team_code_from_name"));
        }
        if (!team && hasText(request.getTechLeadAdGroup())) {
            throw new AdminBusinessException("TECH_LEAD_AD_GROUP_TEAM_ONLY",
                    i18nService.getMessage("admin.vg.tech_lead_ad_group_team_only"));
        }

        String groupId = UUID.randomUUID().toString();

        // 生成代码（团队总是生成；CUSTOM 未提供时生成）
        String code = request.getCode();
        if (code == null || code.isBlank()) {
            code = generateCode(request.getName());
        }
        if (code.isEmpty()) {
            throw new AdminBusinessException("VIRTUAL_GROUP_CODE_EMPTY", i18nService.getMessage("admin.vg.code_empty"));
        }
        if (code.length() > CODE_MAX_LENGTH) {
            throw new AdminBusinessException("VIRTUAL_GROUP_CODE_TOO_LONG",
                    i18nService.getMessage("admin.vg.code_too_long", CODE_MAX_LENGTH));
        }

        // 验证代码唯一性
        if (virtualGroupRepository.existsByCode(code)) {
            throw new AdminBusinessException("CODE_EXISTS", "Virtual group code already exists: " + code);
        }

        VirtualGroup group = VirtualGroup.builder()
                .id(groupId)
                .name(request.getName())
                .code(code)
                .type(EntityTypeConverter.fromVirtualGroupType(request.getType()))
                .displayName(request.getDescription())
                .adGroup(request.getAdGroup())
                .techLeadAdGroup(team ? request.getTechLeadAdGroup() : null)
                .status("ACTIVE")
                .build();

        virtualGroupRepository.save(group);

        log.info("Virtual group created successfully: {}", groupId);
        return VirtualGroupResult.success(group);
    }
    
    /**
     * 根据名称生成代码
     */
    private String generateCode(String name) {
        return name.toUpperCase()
                .replaceAll("[^A-Z0-9]", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_|_$", "");
    }

    /** API create/update may only set CUSTOM or DEVELOPER; SYSTEM is seed-only. */
    private void assertUserCreatableType(VirtualGroupType type) {
        if (type != VirtualGroupType.CUSTOM && type != VirtualGroupType.DEVELOPER) {
            throw new AdminBusinessException(
                    "INVALID_VIRTUAL_GROUP_TYPE",
                    "Virtual group type must be CUSTOM or DEVELOPER");
        }
    }
    
    /**
     * 更新虚拟组。类型不能改；SYSTEM 组只能改 AD 组；团队的名称、code 建好后不能改（§5.4），可以改描述与两个
     * AD 组；Public 没有名单，不能配 AD 组。
     */
    @Transactional
    public VirtualGroupResult updateVirtualGroup(String groupId, VirtualGroupCreateRequest request) {
        log.info("Updating virtual group: {}", groupId);

        VirtualGroup group = virtualGroupRepository.findById(groupId)
                .orElseThrow(() -> new VirtualGroupNotFoundException(groupId));
        VirtualGroupType current = EntityTypeConverter.toVirtualGroupType(group.getType());
        if (request.getType() != null && request.getType() != current) {
            throw new AdminBusinessException("VIRTUAL_GROUP_TYPE_IMMUTABLE", i18nService.getMessage("admin.vg.type_immutable"));
        }

        // 系统内置虚拟组只能修改 AD Group；改了别的字段明确拒绝，不静默丢弃
        if (current == VirtualGroupType.SYSTEM) {
            if ((request.getName() != null && !request.getName().equals(group.getName()))
                    || !Objects.equals(blankToNull(request.getDescription()), blankToNull(group.getDisplayName()))
                    || hasText(request.getTechLeadAdGroup())) {
                throw new AdminBusinessException("SYSTEM_GROUP_AD_GROUP_ONLY",
                        i18nService.getMessage("admin.vg.system_group_ad_group_only"));
            }
            group.setAdGroup(request.getAdGroup());
            virtualGroupRepository.save(group);
            log.info("System virtual group AD group updated: {}", groupId);
            return VirtualGroupResult.success(group);
        }
        if (current == VirtualGroupType.DEVELOPER) {
            if (!group.getName().equals(request.getName())) {
                throw new AdminBusinessException("TEAM_NAME_IMMUTABLE", i18nService.getMessage("admin.vg.team_name_immutable"));
            }
            if (group.isPublicTeam() && (hasText(request.getAdGroup()) || hasText(request.getTechLeadAdGroup()))) {
                throw new AdminBusinessException("PUBLIC_TEAM_HAS_NO_LISTS", i18nService.getMessage("admin.vg.public_has_no_lists"));
            }
            group.setDisplayName(request.getDescription());
            group.setAdGroup(request.getAdGroup());
            group.setTechLeadAdGroup(request.getTechLeadAdGroup());
            virtualGroupRepository.save(group);
            log.info("Team updated successfully: {}", groupId);
            return VirtualGroupResult.success(group);
        }
        if (hasText(request.getTechLeadAdGroup())) {
            throw new AdminBusinessException("TECH_LEAD_AD_GROUP_TEAM_ONLY",
                    i18nService.getMessage("admin.vg.tech_lead_ad_group_team_only"));
        }

        // 验证名称唯一性（排除自身）
        if (!group.getName().equals(request.getName()) &&
            virtualGroupRepository.existsByName(request.getName())) {
            throw new AdminBusinessException("NAME_EXISTS", "Virtual group name already exists: " + request.getName());
        }

        group.setName(request.getName());
        group.setDisplayName(request.getDescription());
        group.setAdGroup(request.getAdGroup());

        virtualGroupRepository.save(group);

        log.info("Virtual group updated successfully: {}", groupId);
        return VirtualGroupResult.success(group);
    }
    
    /**
     * 删除虚拟组。SYSTEM 组与 Public 不可删除；团队在 Admin FU 目录里还有所属 FU 时拒绝，须先迁移（D-33：
     * Admin 不读 DW，DW 里挂在已删除团队上的 FU 由 DW 按团队不可用处理）。
     */
    @Transactional
    public void deleteVirtualGroup(String groupId) {
        log.info("Deleting virtual group: {}", groupId);

        VirtualGroup group = virtualGroupRepository.findById(groupId)
                .orElseThrow(() -> new VirtualGroupNotFoundException(groupId));

        // 系统内置虚拟组不可删除
        if (EntityTypeConverter.toVirtualGroupType(group.getType()) == VirtualGroupType.SYSTEM) {
            throw new AdminBusinessException("SYSTEM_GROUP_CANNOT_DELETE",
                    "系统内置虚拟组不可删除: " + group.getName());
        }
        if (group.isPublicTeam()) {
            throw new AdminBusinessException("PUBLIC_TEAM_CANNOT_DELETE", i18nService.getMessage("admin.vg.public_cannot_delete"));
        }
        if (EntityTypeConverter.toVirtualGroupType(group.getType()) == VirtualGroupType.DEVELOPER) {
            // A unit belongs to the team of its latest imported version row (§8.1): older rows of a unit that moved
            // to another team do not hold this one back.
            Long functionUnits = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM (SELECT DISTINCT ON (code) code, team_code FROM sys_function_units"
                            + " ORDER BY code, " + FunctionUnitRepository.LATEST_IMPORT_ORDER + ") latest"
                            + " WHERE team_code = ?", Long.class, group.getCode());
            if (functionUnits != null && functionUnits > 0) {
                throw new AdminBusinessException("TEAM_HAS_FUNCTION_UNITS",
                        i18nService.getMessage("admin.vg.team_has_function_units", functionUnits));
            }
        }

        // 删除所有成员关系
        virtualGroupMemberRepository.deleteByGroupId(groupId);

        // 删除虚拟组
        virtualGroupRepository.delete(group);

        log.info("Virtual group deleted successfully: {}", groupId);
    }
    
    /**
     * 获取虚拟组详情
     */
    public VirtualGroupInfo getVirtualGroup(String groupId) {
        VirtualGroup group = virtualGroupRepository.findById(groupId)
                .orElseThrow(() -> new VirtualGroupNotFoundException(groupId));
        return VirtualGroupInfo.fromEntity(group);
    }
    
    /**
     * 获取虚拟组列表（带过滤条件）
     */
    public List<VirtualGroupInfo> listVirtualGroups(String type, String status) {
        List<VirtualGroup> groups;
        if (type != null && status != null) {
            // type is already a String from platform-security entity, no conversion needed
            groups = virtualGroupRepository.findByTypeAndStatus(type, status);
        } else if (type != null) {
            // type is already a String from platform-security entity, no conversion needed
            groups = virtualGroupRepository.findByType(type);
        } else if (status != null) {
            groups = virtualGroupRepository.findByStatus(status);
        } else {
            groups = virtualGroupRepository.findAll();
        }
        
        // 获取所有虚拟组的角色绑定
        List<String> groupIds = groups.stream().map(VirtualGroup::getId).collect(Collectors.toList());
        Map<String, Role> roleBindings = getRoleBindingsForGroups(groupIds);
        
        return groups.stream()
                .map(group -> {
                    VirtualGroupInfo info = VirtualGroupInfo.fromEntity(group);
                    
                    // 设置成员数量
                    long memberCount = virtualGroupMemberRepository.countDistinctUsersByGroupId(group.getId());
                    info.setMemberCount((int) memberCount);
                    
                    // 设置角色绑定信息
                    Role boundRole = roleBindings.get(group.getId());
                    if (boundRole != null) {
                        info.setBoundRoleId(boundRole.getId());
                        info.setBoundRoleName(boundRole.getName());
                        info.setBoundRoleCode(boundRole.getCode());
                        info.setBoundRoleType(boundRole.getType());
                    }
                    return info;
                })
                .collect(Collectors.toList());
    }
    
    /**
     * 获取多个虚拟组的角色绑定
     */
    private Map<String, Role> getRoleBindingsForGroups(List<String> groupIds) {
        if (groupIds.isEmpty()) {
            return Map.of();
        }
        
        List<VirtualGroupRole> bindings = virtualGroupRoleRepository.findByVirtualGroupIdIn(groupIds);
        List<String> roleIds = bindings.stream()
                .map(VirtualGroupRole::getRoleId)
                .distinct()
                .collect(Collectors.toList());
        
        Map<String, Role> roleMap = roleRepository.findAllById(roleIds).stream()
                .collect(Collectors.toMap(Role::getId, r -> r));
        
        return bindings.stream()
                .collect(Collectors.toMap(
                        VirtualGroupRole::getVirtualGroupId,
                        binding -> roleMap.get(binding.getRoleId()),
                        (a, b) -> a
                ));
    }
    
    /**
     * 分页查询虚拟组
     */
    public Page<VirtualGroupInfo> listVirtualGroups(String type, String status, 
                                                     String keyword, Pageable pageable) {
        return virtualGroupRepository.findByConditions(type, status, keyword, pageable)
                .map(VirtualGroupInfo::fromEntity);
    }
    
    /**
     * 获取有效的虚拟组列表
     */
    public List<VirtualGroupInfo> getValidGroups() {
        return virtualGroupRepository.findValidGroups().stream()
                .map(VirtualGroupInfo::fromEntity)
                .collect(Collectors.toList());
    }

    
    // ==================== 成员管理 ====================
    
    /**
     * 人工加入一份名单（§5.5）。SYSTEM、CUSTOM 组只有成员；团队须指定 Developer 或 Technical Lead 名单；Public 没有
     * 名单。已是 AD 成员的人可以再加为人工成员，只有这份名单里已有人工行才算重复。
     */
    @Transactional
    public VirtualGroupResult addMember(String groupId, VirtualGroupMemberRequest request) {
        log.info("Adding member {} to virtual group {}", request.getUserId(), groupId);

        VirtualGroup group = virtualGroupRepository.findById(groupId)
                .orElseThrow(() -> new VirtualGroupNotFoundException(groupId));
        String listType = memberListType(group, request.getListType());
        assertMayMaintainMembers(group);

        if (manualRow(groupId, request.getUserId(), listType).isPresent()) {
            throw new AdminBusinessException("MEMBER_EXISTS", i18nService.getMessage("admin.vg.manual_member_exists"));
        }

        VirtualGroupMember member = VirtualGroupMember.builder()
                .id(UUID.randomUUID().toString())
                .groupId(groupId)
                .userId(request.getUserId())
                .listType(listType)
                .source(VirtualGroupMember.SOURCE_MANUAL)
                .build();

        virtualGroupMemberRepository.save(member);

        log.info("Member added successfully: {} to group {} ({})", request.getUserId(), groupId, listType);
        return VirtualGroupResult.success(group);
    }
    
    /**
     * 批量添加成员
     */
    @Transactional
    public List<VirtualGroupResult> addMembers(String groupId, List<VirtualGroupMemberRequest> requests) {
        log.info("Adding {} members to virtual group {}", requests.size(), groupId);
        
        return requests.stream()
                .map(request -> {
                    try {
                        return addMember(groupId, request);
                    } catch (AdminBusinessException e) {
                        log.warn("Failed to add member {}: {}", request.getUserId(), e.getMessage());
                        return null;
                    }
                })
                .filter(result -> result != null)
                .collect(Collectors.toList());
    }
    
    /**
     * 撤销人工成员（§5.5）：只删这份名单里的人工行，AD 来源不受影响，只要还剩一个来源这个人就仍是成员。
     * System Administrators 撤销后若没有有效成员（账号 ACTIVE、未删除，AD 来源也算）即拒绝（AC-12）。
     */
    @Transactional
    public VirtualGroupResult removeMember(String groupId, String userId, String requestedListType) {
        log.info("Revoking manual member {} from virtual group {}", userId, groupId);

        VirtualGroup group = virtualGroupRepository.findById(groupId)
                .orElseThrow(() -> new VirtualGroupNotFoundException(groupId));
        String listType = memberListType(group, requestedListType);
        assertMayMaintainMembers(group);

        VirtualGroupMember member = manualRow(groupId, userId, listType)
                .orElseThrow(() -> new AdminBusinessException("MANUAL_MEMBER_NOT_FOUND",
                        i18nService.getMessage("admin.vg.manual_member_not_found")));

        if (group.isSystemAdministrators() && !anyActiveMemberWithout(groupId, member)) {
            throw new AdminBusinessException("LAST_ADMIN_CANNOT_REMOVE", i18nService.getMessage("admin.vg.last_admin"));
        }

        virtualGroupMemberRepository.delete(member);

        log.info("Manual member revoked: {} from group {} ({})", userId, groupId, listType);
        return VirtualGroupResult.success(group);
    }

    /**
     * Only System Administrators keep the members of SYSTEM groups and team lists (§5.2, §5.5). A browser call reaches
     * here as SYS_ADMIN already; a service call acts for the user in X-User-Id — a Portal approver approving a join —
     * who may only change a CUSTOM group, the Portal access groups (§5.3).
     */
    private void assertMayMaintainMembers(VirtualGroup group) {
        if ("CUSTOM".equals(group.getType())) {
            return;
        }
        String operator = SecurityContextUtils.getCurrentUserId().orElse(null);
        if (operator == null || !userRoleService.hasRole(operator, "SYS_ADMIN")) {
            throw new AdminBusinessException("MEMBERS_SYS_ADMIN_ONLY",
                    i18nService.getMessage("admin.vg.members_sys_admin_only"));
        }
    }

    /** The list a manual member goes on: the only list of a SYSTEM / CUSTOM group, the requested list of a team. */
    private String memberListType(VirtualGroup group, String requested) {
        if (group.isPublicTeam()) {
            throw new AdminBusinessException("PUBLIC_TEAM_HAS_NO_LISTS", i18nService.getMessage("admin.vg.public_has_no_lists"));
        }
        if (EntityTypeConverter.toVirtualGroupType(group.getType()) == VirtualGroupType.DEVELOPER) {
            if (VirtualGroupMember.LIST_DEVELOPER.equals(requested) || VirtualGroupMember.LIST_TECH_LEAD.equals(requested)) {
                return requested;
            }
            throw new AdminBusinessException("TEAM_LIST_REQUIRED", i18nService.getMessage("admin.vg.team_list_required"));
        }
        if (!hasText(requested) || VirtualGroupMember.LIST_MEMBER.equals(requested)) {
            return VirtualGroupMember.LIST_MEMBER;
        }
        throw new AdminBusinessException("INVALID_MEMBER_LIST", i18nService.getMessage("admin.vg.member_list_invalid"));
    }

    private static String blankToNull(String value) {
        return hasText(value) ? value : null;
    }

    private Optional<VirtualGroupMember> manualRow(String groupId, String userId, String listType) {
        return virtualGroupMemberRepository.findByGroupIdAndSource(groupId, VirtualGroupMember.SOURCE_MANUAL).stream()
                .filter(m -> m.getUserId().equals(userId) && m.getListType().equals(listType))
                .findFirst();
    }

    /** Whether the group keeps an active, not deleted member once {@code removed} is gone. */
    private boolean anyActiveMemberWithout(String groupId, VirtualGroupMember removed) {
        Set<String> userIds = virtualGroupMemberRepository.findByGroupId(groupId).stream()
                .filter(m -> !m.getId().equals(removed.getId()))
                .map(VirtualGroupMember::getUserId)
                .collect(Collectors.toSet());
        return userRepository.findAllById(userIds).stream()
                .anyMatch(user -> user.isActive() && !Boolean.TRUE.equals(user.getDeleted()));
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
    
    /**
     * 获取虚拟组成员列表
     */
    public List<VirtualGroupMemberInfo> getGroupMembers(String groupId) {
        if (!virtualGroupRepository.existsById(groupId)) {
            throw new VirtualGroupNotFoundException(groupId);
        }
        
        // 获取虚拟组
        VirtualGroup group = virtualGroupRepository.findById(groupId)
                .orElseThrow(() -> new VirtualGroupNotFoundException(groupId));
        
        // 获取业务单元名称映射
        Map<String, String> businessUnitNameMap = getBusinessUnitNameMap();
        
        // 一人一份名单一项，带来源（AD / 人工，可并存）；最早加入时间为准
        Map<List<String>, List<VirtualGroupMember>> rowsByMember = virtualGroupMemberRepository.findByGroupId(groupId).stream()
                .collect(Collectors.groupingBy(m -> List.of(m.getUserId(), m.getListType()), LinkedHashMap::new,
                        Collectors.toList()));

        // 批量获取用户
        List<String> userIds = rowsByMember.keySet().stream()
                .map(key -> key.get(0))
                .distinct()
                .collect(Collectors.toList());
        Map<String, User> userMap = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        return rowsByMember.values().stream()
                .map(rows -> {
                    VirtualGroupMember member = rows.stream()
                            .min(Comparator.comparing(VirtualGroupMember::getJoinedAt,
                                    Comparator.nullsLast(Comparator.<LocalDateTime>naturalOrder())))
                            .orElseThrow();
                    User user = userMap.get(member.getUserId());
                    VirtualGroupMemberInfo info = VirtualGroupMemberInfo.fromEntity(member, group, user);
                    info.setSources(rows.stream().map(VirtualGroupMember::getSource).filter(Objects::nonNull)
                            .distinct().sorted().toList());
                    // 通过关联表获取用户的业务单元
                    if (member.getUserId() != null) {
                        List<com.platform.security.entity.UserBusinessUnit> userBusinessUnits = 
                                userBusinessUnitRepository.findByUserId(member.getUserId());
                        if (!userBusinessUnits.isEmpty()) {
                            String businessUnitId = userBusinessUnits.get(0).getBusinessUnitId();
                            info.setBusinessUnitId(businessUnitId);
                            info.setBusinessUnitName(businessUnitNameMap.get(businessUnitId));
                        }
                    }
                    return info;
                })
                .collect(Collectors.toList());
    }
    
    /**
     * 获取业务单元ID到名称的映射
     */
    private Map<String, String> getBusinessUnitNameMap() {
        try {
            return jdbcTemplate.query(
                "SELECT id, name FROM sys_business_units",
                (rs, rowNum) -> Map.entry(rs.getString("id"), rs.getString("name"))
            ).stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a));
        } catch (Exception e) {
            log.warn("Failed to get business unit names", e);
            return Map.of();
        }
    }
    
    /**
     * 分页获取虚拟组成员
     */
    public Page<VirtualGroupMemberInfo> getGroupMembers(String groupId, Pageable pageable) {
        if (!virtualGroupRepository.existsById(groupId)) {
            throw new VirtualGroupNotFoundException(groupId);
        }
        
        // 获取虚拟组
        VirtualGroup group = virtualGroupRepository.findById(groupId)
                .orElseThrow(() -> new VirtualGroupNotFoundException(groupId));
        
        Page<VirtualGroupMember> membersPage = virtualGroupMemberRepository.findByGroupId(groupId, pageable);
        
        // 批量获取用户
        List<String> userIds = membersPage.getContent().stream()
                .map(VirtualGroupMember::getUserId)
                .distinct()
                .collect(Collectors.toList());
        Map<String, User> userMap = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));
        
        return membersPage.map(member -> {
            User user = userMap.get(member.getUserId());
            return VirtualGroupMemberInfo.fromEntity(member, group, user);
        });
    }
    
    /**
     * 检查用户是否是虚拟组成员
     */
    public boolean isGroupMember(String groupId, String userId) {
        return virtualGroupMemberRepository.existsByGroupIdAndUserId(groupId, userId);
    }

    
    // ==================== 生命周期管理 ====================
    
    /**
     * 激活虚拟组
     */
    @Transactional
    public VirtualGroupResult activateGroup(String groupId) {
        log.info("Activating virtual group: {}", groupId);
        
        VirtualGroup group = virtualGroupRepository.findById(groupId)
                .orElseThrow(() -> new VirtualGroupNotFoundException(groupId));
        
        group.setStatus("ACTIVE");
        virtualGroupRepository.save(group);
        
        log.info("Virtual group activated: {}", groupId);
        return VirtualGroupResult.success(group);
    }
    
    /**
     * 停用虚拟组
     */
    @Transactional
    public VirtualGroupResult deactivateGroup(String groupId) {
        log.info("Deactivating virtual group: {}", groupId);
        
        VirtualGroup group = virtualGroupRepository.findById(groupId)
                .orElseThrow(() -> new VirtualGroupNotFoundException(groupId));
        // System Administrators, Auditors and Public cannot be disabled (§5.2, §6.4)
        if (EntityTypeConverter.toVirtualGroupType(group.getType()) == VirtualGroupType.SYSTEM || group.isPublicTeam()) {
            throw new AdminBusinessException("GROUP_CANNOT_BE_DEACTIVATED", i18nService.getMessage("admin.vg.cannot_deactivate"));
        }

        group.setStatus("INACTIVE");
        virtualGroupRepository.save(group);
        
        log.info("Virtual group deactivated: {}", groupId);
        return VirtualGroupResult.success(group);
    }
    
    /**
     * 解散虚拟组
     */
    @Transactional
    public void dissolveGroup(String groupId) {
        log.info("Dissolving virtual group: {}", groupId);
        
        VirtualGroup group = virtualGroupRepository.findById(groupId)
                .orElseThrow(() -> new VirtualGroupNotFoundException(groupId));
        
        group.setStatus("DISSOLVED");
        virtualGroupRepository.save(group);
        
        log.info("Virtual group dissolved: {}", groupId);
    }
    
    /**
     * 获取用户所属的虚拟组
     */
    public List<VirtualGroupInfo> getUserGroups(String userId) {
        return virtualGroupRepository.findByUserId(userId).stream()
                .map(VirtualGroupInfo::fromEntity)
                .collect(Collectors.toList());
    }
}
