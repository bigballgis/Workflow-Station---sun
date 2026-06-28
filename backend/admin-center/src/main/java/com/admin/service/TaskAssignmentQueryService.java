package com.admin.service;

import com.platform.security.entity.BusinessUnit;
import com.platform.security.entity.Role;
import com.platform.security.entity.VirtualGroup;
import com.platform.security.entity.VirtualGroupMember;
import com.platform.security.entity.UserBusinessUnitRole;
import com.admin.enums.RoleType;
import com.admin.util.EntityTypeConverter;
import com.admin.exception.BusinessUnitNotFoundException;
import com.admin.exception.RoleNotFoundException;
import com.admin.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 任务分配查询服务
 * 提供工作流任务分配所需的用户查询功能
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TaskAssignmentQueryService {
    
    private final UserRepository userRepository;
    private final BusinessUnitRepository businessUnitRepository;
    private final RoleRepository roleRepository;
    private final UserBusinessUnitRoleRepository userBusinessUnitRoleRepository;
    private final VirtualGroupRoleRepository virtualGroupRoleRepository;
    private final VirtualGroupMemberRepository virtualGroupMemberRepository;
    private final VirtualGroupRepository virtualGroupRepository;
    private final BusinessUnitRoleRepository businessUnitRoleRepository;
    
    /**
     * 获取用户的业务单元上下文（UBR）。
     * <p>若用户在多个 BU 下有 UBR，须通过 {@code preferredBusinessUnitId}（流程变量 {@code activeBusinessUnitId} 或请求参数）指定，
     * 否则返回 null，避免误用「首条」启发式。</p>
     *
     * @param preferredBusinessUnitId 须在用户 UBR 中存在；可为 null
     */
    /**
     * 任务分配链路统一使用业务单元 <strong>code</strong>（跨环境稳定），故本方法：
     * <ul>
     *   <li>入参 {@code preferredBusinessUnitCode} 为 BU code（流程变量 {@code activeBusinessUnitId} 已 code 化）；</li>
     *   <li>返回值为用户当前 BU 的 <strong>code</strong>（内部 UBR 存的是 id，需转 code 后返回）。</li>
     * </ul>
     */
    public String getUserBusinessUnitId(String userId, String preferredBusinessUnitCode) {
        log.debug("Getting business unit code for user: {}, preferred: {}", userId, preferredBusinessUnitCode);

        List<UserBusinessUnitRole> assignments = userBusinessUnitRoleRepository.findByUserId(userId);
        if (assignments.isEmpty()) {
            log.debug("User {} has no business unit assignment", userId);
            return null;
        }

        if (preferredBusinessUnitCode != null && !preferredBusinessUnitCode.isBlank()) {
            // preferred is a code; resolve to id to match UBR rows (which store BU id)
            BusinessUnit preferred = businessUnitRepository.findByCode(preferredBusinessUnitCode).orElse(null);
            if (preferred == null) {
                log.warn("Preferred business unit code {} not found", preferredBusinessUnitCode);
                return null;
            }
            boolean ok = assignments.stream()
                    .anyMatch(a -> preferred.getId().equals(a.getBusinessUnitId()));
            if (ok) {
                return preferred.getCode();
            }
            log.warn("User {} has no UBR for preferred business unit code {}", userId, preferredBusinessUnitCode);
            return null;
        }

        Set<String> distinct = assignments.stream()
                .map(UserBusinessUnitRole::getBusinessUnitId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (distinct.size() == 1) {
            String buId = distinct.iterator().next();
            String code = businessUnitRepository.findById(buId).map(BusinessUnit::getCode).orElse(null);
            log.debug("User {} has single UBR business unit: id={}, code={}", userId, buId, code);
            return code;
        }
        log.warn("User {} has UBR in {} business units; pass activeBusinessUnitId — returning null", userId, distinct.size());
        return null;
    }

    /**
     * @see #getUserBusinessUnitId(String, String)
     */
    public String getUserBusinessUnitId(String userId) {
        return getUserBusinessUnitId(userId, null);
    }
    
    /**
     * 获取业务单元的父业务单元ID
     * @param businessUnitId 业务单元ID
     * @return 父业务单元ID，如果没有父级则返回null
     */
    /**
     * 业务单元 id → code 转换。供运行时把工作台上下文变量 {@code activeBusinessUnitId}（仍为 id）
     * 在进入任务分配 code 链路前转成 code。未找到返回 null。
     */
    public String getBusinessUnitCodeById(String businessUnitId) {
        if (businessUnitId == null || businessUnitId.isBlank()) {
            return null;
        }
        return businessUnitRepository.findById(businessUnitId.trim())
                .map(BusinessUnit::getCode)
                .orElse(null);
    }

    /**
     * 入参为 BU code，返回父 BU 的 <strong>code</strong>（hierarchy 沿父链全程 code）。
     * 无父级时返回 null。
     */
    public String getParentBusinessUnitId(String businessUnitCode) {
        log.debug("Getting parent business unit code for: {}", businessUnitCode);

        BusinessUnit businessUnit = businessUnitRepository.findByCode(businessUnitCode)
                .orElseThrow(() -> new BusinessUnitNotFoundException(businessUnitCode));

        String parentId = businessUnit.getParentId();
        if (parentId == null || parentId.isBlank()) {
            return null;
        }
        String parentCode = businessUnitRepository.findById(parentId)
                .map(BusinessUnit::getCode).orElse(null);
        log.debug("Business unit {} has parent code: {}", businessUnitCode, parentCode);
        return parentCode;
    }
    
    /**
     * 获取业务单元中拥有指定角色的用户ID列表
     * @param businessUnitId 业务单元ID
     * @param roleId 角色ID（BU_BOUNDED类型）
     * @return 用户ID列表
     */
    /**
     * 入参为 BU code 与 role code（任务分配链路统一 code）。内部解析为 id 后查询 UBR。
     */
    public List<String> getUsersByBusinessUnitAndRole(String businessUnitCode, String roleCode) {
        log.debug("Getting users by business unit code {} and role code {}", businessUnitCode, roleCode);

        // 解析 BU code → 实体
        BusinessUnit businessUnit = businessUnitRepository.findByCode(businessUnitCode)
                .orElseThrow(() -> new BusinessUnitNotFoundException(businessUnitCode));

        // 解析 role code → 实体，校验为 BU_BOUNDED 类型
        Role role = roleRepository.findByCode(roleCode)
                .orElseThrow(() -> new RoleNotFoundException(roleCode));

        if (EntityTypeConverter.toRoleType(role.getType()) != RoleType.BU_BOUNDED) {
            log.warn("Role {} is not BU_BOUNDED type, actual type: {}", roleCode, role.getType());
            return Collections.emptyList();
        }

        List<String> userIds = userBusinessUnitRoleRepository
                .findUserIdsByBusinessUnitIdAndRoleId(businessUnit.getId(), role.getId());
        log.debug("Found {} users with role {} in business unit {}", userIds.size(), roleCode, businessUnitCode);
        return userIds;
    }
    
    /**
     * 获取拥有指定BU无关型角色的用户ID列表
     * 通过查询绑定了该角色的虚拟组的所有成员
     * @param roleId 角色ID（BU_UNBOUNDED类型）
     * @return 用户ID列表
     */
    /**
     * 入参为 role code（任务分配链路统一 code）。内部解析为 id 后查询虚拟组成员。
     */
    public List<String> getUsersByUnboundedRole(String roleCode) {
        log.debug("Getting users by unbounded role code: {}", roleCode);

        // 解析 role code → 实体，校验为 BU_UNBOUNDED 类型
        Role role = roleRepository.findByCode(roleCode)
                .orElseThrow(() -> new RoleNotFoundException(roleCode));

        if (EntityTypeConverter.toRoleType(role.getType()) != RoleType.BU_UNBOUNDED) {
            log.warn("Role {} is not BU_UNBOUNDED type, actual type: {}", roleCode, role.getType());
            return Collections.emptyList();
        }

        // 查找绑定了该角色的所有虚拟组
        List<String> virtualGroupIds = virtualGroupRoleRepository.findVirtualGroupIdsByRoleId(role.getId());
        if (virtualGroupIds.isEmpty()) {
            log.debug("No virtual groups bound to role {}", roleCode);
            return Collections.emptyList();
        }

        // 查找这些虚拟组的所有成员
        List<String> userIds = virtualGroupMemberRepository.findUserIdsByVirtualGroupIds(virtualGroupIds);
        log.debug("Found {} users with unbounded role {} through {} virtual groups",
                userIds.size(), roleCode, virtualGroupIds.size());
        return userIds;
    }

    /**
     * 按虚拟组 <strong>业务编码</strong>（如 BPMN 中 VIRTUAL_GROUP 的 assigneeValue：DOCUMENT_VERIFIERS）解析成员用户 ID。
     * 与 {@link #getUsersByUnboundedRole(String)} 不同：后者参数为角色主键 ID。
     */
    public List<String> getUsersByVirtualGroupCode(String code) {
        if (code == null || code.isBlank()) {
            return Collections.emptyList();
        }
        String trimmed = code.trim();
        log.debug("Getting users by virtual group code: {}", trimmed);

        VirtualGroup group = virtualGroupRepository.findByCode(trimmed).orElse(null);
        if (group == null) {
            log.debug("No virtual group found for code {}", trimmed);
            return Collections.emptyList();
        }

        List<VirtualGroupMember> members = virtualGroupMemberRepository.findByGroupId(group.getId());
        List<String> userIds = members.stream()
                .map(VirtualGroupMember::getUserId)
                .distinct()
                .collect(Collectors.toList());
        log.debug("Found {} users in virtual group {} (code={})", userIds.size(), group.getId(), trimmed);
        return userIds;
    }
    
    /**
     * 获取业务单元的准入角色ID列表
     * @param businessUnitId 业务单元ID
     * @return 角色ID列表
     */
    /**
     * 入参为 BU code，返回该 BU 的准入角色 <strong>code</strong> 列表（任务分配链路统一 code）。
     */
    public List<String> getEligibleRoleIds(String businessUnitCode) {
        log.debug("Getting eligible role codes for business unit code: {}", businessUnitCode);

        BusinessUnit businessUnit = businessUnitRepository.findByCode(businessUnitCode)
                .orElseThrow(() -> new BusinessUnitNotFoundException(businessUnitCode));

        List<String> roleIds = businessUnitRoleRepository.findByBusinessUnitId(businessUnit.getId())
                .stream()
                .map(bur -> bur.getRoleId())
                .collect(Collectors.toList());

        // 转 code 返回
        List<String> roleCodes = roleRepository.findByIdIn(roleIds).stream()
                .map(Role::getCode)
                .collect(Collectors.toList());

        log.debug("Business unit {} has {} eligible roles", businessUnitCode, roleCodes.size());
        return roleCodes;
    }
    
    /**
     * 检查角色是否是业务单元的准入角色
     * @param businessUnitId 业务单元ID
     * @param roleId 角色ID
     * @return 是否是准入角色
     */
    /**
     * 入参为 BU code 与 role code（任务分配链路统一 code）。内部解析为 id 后判定准入。
     * 任一 code 不存在视为不准入（false）。
     */
    public boolean isEligibleRole(String businessUnitCode, String roleCode) {
        BusinessUnit businessUnit = businessUnitRepository.findByCode(businessUnitCode).orElse(null);
        Role role = roleRepository.findByCode(roleCode).orElse(null);
        if (businessUnit == null || role == null) {
            return false;
        }
        return businessUnitRoleRepository.existsByBusinessUnitIdAndRoleId(businessUnit.getId(), role.getId());
    }
    
    /**
     * 获取所有BU绑定型角色
     * @return 角色列表
     */
    public List<Role> getBuBoundedRoles() {
        return roleRepository.findByType("BU_BOUNDED");
    }
    
    /**
     * 获取所有BU无关型角色
     * @return 角色列表
     */
    public List<Role> getBuUnboundedRoles() {
        return roleRepository.findByType("BU_UNBOUNDED");
    }
}
