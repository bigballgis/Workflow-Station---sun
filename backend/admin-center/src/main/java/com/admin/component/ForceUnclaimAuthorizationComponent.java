package com.admin.component;

import com.admin.repository.BusinessUnitRepository;
import com.admin.repository.RoleRepository;
import com.admin.repository.UserBusinessUnitRoleRepository;
import com.admin.repository.UserRepository;
import com.admin.service.ApproverService;
import com.admin.service.UserPermissionService;
import com.platform.security.entity.BusinessUnit;
import com.platform.security.entity.Role;
import com.platform.security.entity.User;
import com.platform.security.entity.UserBusinessUnitRole;
import com.platform.security.ubr.UbrMembershipType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Authoritative Claim Hold force-unclaim check: SYS_ADMIN, BU Approver, or UBR Leader.
 */
@Component
@RequiredArgsConstructor
public class ForceUnclaimAuthorizationComponent {

    private static final String SYS_ADMIN = "SYS_ADMIN";

    private final UserPermissionService userPermissionService;
    private final ApproverService approverService;
    private final UserBusinessUnitRoleRepository userBusinessUnitRoleRepository;
    private final BusinessUnitRepository businessUnitRepository;
    private final RoleRepository roleRepository;
    private final UserRepository userRepository;

    public Map<String, Boolean> evaluate(String userId, List<ForceUnclaimItem> items) {
        Capability capability = loadCapability(userId);
        Map<String, Boolean> flags = new LinkedHashMap<>();
        if (items == null) {
            return flags;
        }
        for (ForceUnclaimItem item : items) {
            if (item == null || !StringUtils.hasText(item.taskId())) {
                continue;
            }
            flags.put(item.taskId(), capability.allows(item.businessUnitId(), item.roleIds()));
        }
        return flags;
    }

    public List<com.admin.dto.response.RoleLeaderGroup> listLeaders(String businessUnitId) {
        List<UserBusinessUnitRole> rows = userBusinessUnitRoleRepository
                .findByBusinessUnitIdAndMembershipType(businessUnitId, UbrMembershipType.LEADER);
        return groupLeaders(rows);
    }

    private Capability loadCapability(String userId) {
        boolean sysAdmin = userPermissionService.getUserRolesForProfile(userId, "ADMIN").stream()
                .anyMatch(role -> SYS_ADMIN.equals(role.getCode()));
        Set<String> approverBuIds = new LinkedHashSet<>(approverService.getApproverBusinessUnitIds(userId));
        Set<String> approverBuCodes = new LinkedHashSet<>();
        if (!approverBuIds.isEmpty()) {
            for (BusinessUnit bu : businessUnitRepository.findAllById(approverBuIds)) {
                if (bu.getCode() != null && !bu.getCode().isBlank()) {
                    approverBuCodes.add(bu.getCode());
                }
            }
        }
        List<LeaderUbr> leaders = loadLeaderUbrs(userId);
        return new Capability(sysAdmin, approverBuIds, approverBuCodes, leaders);
    }

    private List<LeaderUbr> loadLeaderUbrs(String userId) {
        List<UserBusinessUnitRole> rows = userBusinessUnitRoleRepository
                .findByUserIdAndMembershipType(userId, UbrMembershipType.LEADER);
        if (rows.isEmpty()) {
            return List.of();
        }
        Set<String> buIds = new LinkedHashSet<>();
        Set<String> roleIds = new LinkedHashSet<>();
        for (UserBusinessUnitRole row : rows) {
            buIds.add(row.getBusinessUnitId());
            roleIds.add(row.getRoleId());
        }
        Map<String, BusinessUnit> buMap = businessUnitRepository.findAllById(buIds).stream()
                .collect(java.util.stream.Collectors.toMap(BusinessUnit::getId, bu -> bu));
        Map<String, Role> roleMap = roleRepository.findAllById(roleIds).stream()
                .collect(java.util.stream.Collectors.toMap(Role::getId, r -> r));
        List<LeaderUbr> out = new ArrayList<>();
        for (UserBusinessUnitRole row : rows) {
            BusinessUnit bu = buMap.get(row.getBusinessUnitId());
            Role role = roleMap.get(row.getRoleId());
            out.add(new LeaderUbr(
                    row.getBusinessUnitId(),
                    bu != null ? bu.getCode() : null,
                    row.getRoleId(),
                    role != null ? role.getCode() : null));
        }
        return out;
    }

    private List<com.admin.dto.response.RoleLeaderGroup> groupLeaders(List<UserBusinessUnitRole> rows) {
        Map<String, com.admin.dto.response.RoleLeaderGroup> byRole = new LinkedHashMap<>();
        if (rows.isEmpty()) {
            return List.of();
        }
        Set<String> roleIds = new LinkedHashSet<>();
        Set<String> userIds = new LinkedHashSet<>();
        for (UserBusinessUnitRole row : rows) {
            roleIds.add(row.getRoleId());
            userIds.add(row.getUserId());
        }
        Map<String, Role> roleMap = roleRepository.findAllById(roleIds).stream()
                .collect(java.util.stream.Collectors.toMap(Role::getId, r -> r));
        Map<String, User> userMap = userRepository.findAllById(userIds).stream()
                .collect(java.util.stream.Collectors.toMap(User::getId, u -> u));
        for (UserBusinessUnitRole row : rows) {
            Role role = roleMap.get(row.getRoleId());
            com.admin.dto.response.RoleLeaderGroup group = byRole.computeIfAbsent(row.getRoleId(), id ->
                    com.admin.dto.response.RoleLeaderGroup.builder()
                            .roleId(id)
                            .roleName(role != null ? role.getName() : id)
                            .roleCode(role != null ? role.getCode() : null)
                            .leaders(new ArrayList<>())
                            .build());
            User user = userMap.get(row.getUserId());
            group.getLeaders().add(com.admin.dto.response.RoleLeaderGroup.RoleLeaderUser.builder()
                    .userId(row.getUserId())
                    .userName(user != null ? user.getUsername() : null)
                    .userFullName(user != null ? user.getFullName() : null)
                    .build());
        }
        return new ArrayList<>(byRole.values());
    }

    public record ForceUnclaimItem(String taskId, String businessUnitId, List<String> roleIds) {
    }

    private record Capability(boolean sysAdmin, Set<String> approverBuIds, Set<String> approverBuCodes,
                              List<LeaderUbr> leaders) {
        boolean allows(String taskBu, Collection<String> taskRoleIds) {
            if (sysAdmin) {
                return true;
            }
            if (!StringUtils.hasText(taskBu)) {
                return false;
            }
            String bu = taskBu.trim();
            if (approverBuIds.contains(bu) || containsIgnoreCase(approverBuCodes, bu)) {
                return true;
            }
            if (taskRoleIds == null || taskRoleIds.isEmpty()) {
                return false;
            }
            Set<String> roles = new LinkedHashSet<>();
            for (String roleId : taskRoleIds) {
                if (StringUtils.hasText(roleId)) {
                    roles.add(roleId.trim());
                }
            }
            for (LeaderUbr leader : leaders) {
                if (leader.matchesBu(bu) && leader.matchesAnyRole(roles)) {
                    return true;
                }
            }
            return false;
        }

        private static boolean containsIgnoreCase(Set<String> values, String candidate) {
            for (String value : values) {
                if (value != null && value.equalsIgnoreCase(candidate)) {
                    return true;
                }
            }
            return false;
        }
    }

    private record LeaderUbr(String businessUnitId, String businessUnitCode, String roleId, String roleCode) {
        boolean matchesBu(String taskBu) {
            return taskBu.equals(businessUnitId)
                    || (businessUnitCode != null && businessUnitCode.equalsIgnoreCase(taskBu));
        }

        boolean matchesAnyRole(Set<String> taskRoles) {
            for (String taskRole : taskRoles) {
                if (taskRole.equals(roleId) || (roleCode != null && roleCode.equalsIgnoreCase(taskRole))) {
                    return true;
                }
            }
            return false;
        }
    }
}
