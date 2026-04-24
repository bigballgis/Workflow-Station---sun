package com.developer.security;

import com.developer.entity.FunctionUnit;
import com.developer.repository.FunctionUnitDevGroupAssignmentRepository;
import com.developer.repository.FunctionUnitRepository;
import com.developer.repository.RoleRepository;
import com.developer.repository.VirtualGroupMembershipDao;
import com.platform.security.util.SecurityContextUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Technical Lead / Team Lead / Developer 工作区隔离：创建者 + 虚拟组分配
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FunctionUnitWorkspaceAccessService {

    private static final String ROLE_TECH_LEAD = "TECH_LEAD";
    private static final String ROLE_TEAM_LEAD = "TEAM_LEAD";
    private static final String ROLE_DEVELOPER = "DEVELOPER";

    private final RoleRepository roleRepository;
    private final FunctionUnitRepository functionUnitRepository;
    private final FunctionUnitDevGroupAssignmentRepository devGroupAssignmentRepository;
    private final VirtualGroupMembershipDao virtualGroupMembershipDao;

    public void assertCanAccess(Long functionUnitId, WorkspaceAccessAction action) {
        if (!canAccess(functionUnitId, action)) {
            log.warn("Workspace denied: functionUnitId={}, action={}, userId={}",
                    functionUnitId, action, SecurityContextUtils.getCurrentUserId().orElse("?"));
            throw new FunctionUnitWorkspaceAccessDeniedException("Not authorized to perform this operation on this function unit");
        }
    }

    public boolean canAccess(Long functionUnitId, WorkspaceAccessAction action) {
        Optional<String> userIdOpt = SecurityContextUtils.getCurrentUserId();
        Optional<String> usernameOpt = SecurityContextUtils.getCurrentUsername();
        if (userIdOpt.isEmpty()) {
            return false;
        }
        String userId = userIdOpt.get();
        String username = usernameOpt.orElse("");

        Optional<FunctionUnit> fuOpt = functionUnitRepository.findById(functionUnitId);
        if (fuOpt.isEmpty()) {
            return false;
        }
        FunctionUnit fu = fuOpt.get();

        if (roleRepository.userHasActiveAdminTypeRole(userId)) {
            return true;
        }
        if (roleRepository.hasRoleByUserId(userId, ROLE_TECH_LEAD)) {
            return true;
        }

        boolean teamLead = roleRepository.hasRoleByUserId(userId, ROLE_TEAM_LEAD);
        boolean developer = roleRepository.hasRoleByUserId(userId, ROLE_DEVELOPER);

        if (action == WorkspaceAccessAction.DELETE) {
            return teamLead && username.equals(fu.getCreatedBy());
        }
        if (action == WorkspaceAccessAction.ASSIGN_DEV_GROUPS) {
            return teamLead && username.equals(fu.getCreatedBy());
        }

        if (teamLead && username.equals(fu.getCreatedBy())) {
            return true;
        }
        if (developer && isAssignedViaVirtualGroup(userId, functionUnitId)) {
            return true;
        }
        return false;
    }

    private boolean isAssignedViaVirtualGroup(String userId, Long functionUnitId) {
        List<String> groups = virtualGroupMembershipDao.findVirtualGroupIdsByUserId(userId);
        if (groups.isEmpty()) {
            return false;
        }
        List<Long> unitIds = devGroupAssignmentRepository.findDistinctFunctionUnitIdsByVirtualGroupIdIn(groups);
        return unitIds.contains(functionUnitId);
    }

    /**
     * 列表/可见范围：Technical Lead / 平台 ADMIN = 全部；否则为「我创建的 ∪ 组分配到的」
     */
    public Set<Long> visibleFunctionUnitIds() {
        Optional<String> userIdOpt = SecurityContextUtils.getCurrentUserId();
        Optional<String> usernameOpt = SecurityContextUtils.getCurrentUsername();
        if (userIdOpt.isEmpty()) {
            return Collections.emptySet();
        }
        String userId = userIdOpt.get();
        String username = usernameOpt.orElse("");

        if (roleRepository.userHasActiveAdminTypeRole(userId)
                || roleRepository.hasRoleByUserId(userId, ROLE_TECH_LEAD)) {
            return null;
        }

        Set<Long> ids = new HashSet<>();
        if (roleRepository.hasRoleByUserId(userId, ROLE_TEAM_LEAD)) {
            ids.addAll(functionUnitRepository.findIdsByCreatedBy(username));
        }
        if (roleRepository.hasRoleByUserId(userId, ROLE_DEVELOPER)) {
            List<String> groups = virtualGroupMembershipDao.findVirtualGroupIdsByUserId(userId);
            if (!groups.isEmpty()) {
                ids.addAll(devGroupAssignmentRepository.findDistinctFunctionUnitIdsByVirtualGroupIdIn(groups));
            }
        }
        return ids;
    }
}
