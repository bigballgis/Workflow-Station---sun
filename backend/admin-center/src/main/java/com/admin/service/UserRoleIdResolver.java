package com.admin.service;

import com.admin.repository.UserBusinessUnitRoleRepository;
import com.platform.security.dto.UserEffectiveRole;
import com.platform.security.service.UserRoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Role ids of a user for Admin's role-keyed checks (BI audiences, function unit access, developer permissions,
 * profile role lists). Platform roles come from the resolution every service shares
 * ({@link UserRoleService}); business roles come from the user's BU memberships.
 */
@Component
@RequiredArgsConstructor
public class UserRoleIdResolver {

    private final UserRoleService userRoleService;
    private final UserBusinessUnitRoleRepository userBusinessUnitRoleRepository;

    /** SYS_ADMIN, AUDITOR, TECH_LEAD and DEVELOPER, as far as the user holds them. */
    public List<String> platformRoleIds(String userId) {
        return userRoleService.getEffectiveRolesForUser(userId).stream()
                .map(UserEffectiveRole::getRoleId)
                .toList();
    }

    /** Platform roles plus the roles of every BU membership of the user. */
    public List<String> allRoleIds(String userId) {
        Set<String> ids = new LinkedHashSet<>(platformRoleIds(userId));
        ids.addAll(userBusinessUnitRoleRepository.findRoleIdsByUserId(userId));
        return List.copyOf(ids);
    }
}
