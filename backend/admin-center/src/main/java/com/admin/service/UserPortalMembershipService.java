package com.admin.service;

import com.admin.repository.BusinessUnitRepository;
import com.admin.repository.RoleRepository;
import com.admin.repository.UserBusinessUnitRepository;
import com.admin.repository.UserBusinessUnitRoleRepository;
import com.admin.repository.VirtualGroupMemberRepository;
import com.admin.repository.VirtualGroupRepository;
import com.admin.repository.VirtualGroupRoleRepository;
import com.platform.security.entity.BusinessUnit;
import com.platform.security.entity.Role;
import com.platform.security.entity.UserBusinessUnit;
import com.platform.security.entity.UserBusinessUnitRole;
import com.platform.security.entity.VirtualGroup;
import com.platform.security.entity.VirtualGroupMember;
import com.platform.security.entity.VirtualGroupRole;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 聚合 user-portal 所需的「我的成员身份」视图（虚拟组 + 业务单元角色 + 业务单元成员行）。
 */
@Service
@RequiredArgsConstructor
public class UserPortalMembershipService {

    private final VirtualGroupMemberRepository virtualGroupMemberRepository;
    private final VirtualGroupRepository virtualGroupRepository;
    private final VirtualGroupRoleRepository virtualGroupRoleRepository;
    private final RoleRepository roleRepository;
    private final UserBusinessUnitRoleRepository userBusinessUnitRoleRepository;
    private final BusinessUnitRepository businessUnitRepository;
    private final UserBusinessUnitRepository userBusinessUnitRepository;

    public Map<String, Object> buildMembershipPayload(String userId) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("virtualGroups", buildVirtualGroups(userId));
        root.put("businessUnitRoles", buildBusinessUnitRoles(userId));
        root.put("businessUnits", buildBusinessUnits(userId));
        return root;
    }

    private List<Map<String, Object>> buildVirtualGroups(String userId) {
        List<VirtualGroupMember> memberships = virtualGroupMemberRepository.findByUserId(userId);
        if (memberships.isEmpty()) {
            return List.of();
        }
        List<String> groupIds = memberships.stream()
                .map(VirtualGroupMember::getGroupId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<String, VirtualGroup> groupMap = virtualGroupRepository.findAllById(groupIds).stream()
                .collect(Collectors.toMap(VirtualGroup::getId, g -> g));

        List<VirtualGroupRole> bindings = virtualGroupRoleRepository.findByVirtualGroupIdIn(groupIds);
        Map<String, List<String>> groupToRoleIds = bindings.stream()
                .collect(Collectors.groupingBy(
                        VirtualGroupRole::getVirtualGroupId,
                        Collectors.mapping(VirtualGroupRole::getRoleId, Collectors.toList())));

        Set<String> roleIds = bindings.stream().map(VirtualGroupRole::getRoleId).collect(Collectors.toSet());
        Map<String, Role> roleMap = roleIds.isEmpty() ? Map.of()
                : roleRepository.findAllById(roleIds).stream().collect(Collectors.toMap(Role::getId, r -> r));

        List<Map<String, Object>> out = new ArrayList<>();
        for (VirtualGroupMember m : memberships) {
            VirtualGroup group = groupMap.get(m.getGroupId());
            if (group == null) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("groupId", group.getId());
            row.put("groupName", group.getName());
            row.put("groupDescription", group.getDisplayName());
            row.put("joinedAt", m.getJoinedAt());
            List<Map<String, Object>> boundRoles = new ArrayList<>();
            for (String rid : groupToRoleIds.getOrDefault(group.getId(), List.of())) {
                Role r = roleMap.get(rid);
                if (r == null) {
                    continue;
                }
                if (!"BU_BOUNDED".equals(r.getType()) && !"BU_UNBOUNDED".equals(r.getType())) {
                    continue;
                }
                Map<String, Object> rm = new LinkedHashMap<>();
                rm.put("id", r.getId());
                rm.put("name", r.getName());
                rm.put("code", r.getCode());
                rm.put("type", r.getType());
                boundRoles.add(rm);
            }
            if (boundRoles.isEmpty()) {
                continue;
            }
            row.put("boundRoles", boundRoles);
            out.add(row);
        }
        return out;
    }

    private List<Map<String, Object>> buildBusinessUnitRoles(String userId) {
        List<UserBusinessUnitRole> roles = userBusinessUnitRoleRepository.findByUserId(userId);
        if (roles.isEmpty()) {
            return List.of();
        }
        List<String> buIds = roles.stream().map(UserBusinessUnitRole::getBusinessUnitId).distinct().toList();
        List<String> rIds = roles.stream().map(UserBusinessUnitRole::getRoleId).distinct().toList();
        Map<String, BusinessUnit> buMap = businessUnitRepository.findAllById(buIds).stream()
                .collect(Collectors.toMap(BusinessUnit::getId, bu -> bu));
        Map<String, Role> roleMap = roleRepository.findAllById(rIds).stream()
                .collect(Collectors.toMap(Role::getId, r -> r));

        List<Map<String, Object>> out = new ArrayList<>();
        for (UserBusinessUnitRole u : roles) {
            BusinessUnit bu = buMap.get(u.getBusinessUnitId());
            Role r = roleMap.get(u.getRoleId());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", u.getId());
            row.put("userId", u.getUserId());
            row.put("businessUnitId", u.getBusinessUnitId());
            row.put("businessUnitName", bu != null ? bu.getName() : null);
            row.put("roleId", u.getRoleId());
            row.put("roleName", r != null ? r.getName() : null);
            row.put("roleCode", r != null ? r.getCode() : null);
            row.put("membershipType", u.getMembershipType() != null ? u.getMembershipType() : "MEMBER");
            row.put("assignedAt", u.getCreatedAt());
            row.put("createdAt", u.getCreatedAt());
            out.add(row);
        }
        return out;
    }

    private List<Map<String, Object>> buildBusinessUnits(String userId) {
        List<UserBusinessUnit> rows = userBusinessUnitRepository.findByUserId(userId);
        if (rows.isEmpty()) {
            return List.of();
        }
        List<String> buIds = rows.stream().map(UserBusinessUnit::getBusinessUnitId).distinct().toList();
        Map<String, BusinessUnit> buMap = businessUnitRepository.findAllById(buIds).stream()
                .collect(Collectors.toMap(BusinessUnit::getId, bu -> bu));
        List<Map<String, Object>> out = new ArrayList<>();
        for (UserBusinessUnit ub : rows) {
            BusinessUnit bu = buMap.get(ub.getBusinessUnitId());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("businessUnitId", ub.getBusinessUnitId());
            row.put("businessUnitName", bu != null ? bu.getName() : null);
            row.put("joinedAt", ub.getCreatedAt());
            out.add(row);
        }
        return out;
    }
}
