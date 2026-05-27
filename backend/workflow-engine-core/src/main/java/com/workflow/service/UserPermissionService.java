package com.workflow.service;

import com.workflow.client.AdminCenterClient;
import com.workflow.enums.AssignmentType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

/**
 * User permission service.
 * Encapsulates user permission verification logic for task assignment and
 * operation permission checks.
 *
 * Note: this service uses the legacy AssignmentType enum (USER, VIRTUAL_GROUP).
 * The new task assignment mechanism uses the AssigneeType enum and
 * TaskAssigneeResolver service.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserPermissionService {

    private final AdminCenterClient adminCenterClient;

    /**
     * Verify whether a user has permission to operate on tasks of the specified
     * assignment type.
     *
     * @param userId           user ID
     * @param assignmentType   assignment type
     * @param assignmentTarget assignment target (user ID / virtual group ID)
     * @return whether the user has permission
     */
    public boolean hasTaskPermission(String userId, AssignmentType assignmentType, String assignmentTarget) {
        if (userId == null) {
            return false;
        }

        switch (assignmentType) {
            case USER:
                // Tasks directly assigned to a user — only that user has permission
                return assignmentTarget != null && userId.equals(assignmentTarget);

            case CANDIDATE_USERS:
                if (assignmentTarget == null || assignmentTarget.isBlank()) {
                    return false;
                }
                for (String uid : assignmentTarget.split(",")) {
                    if (userId.equals(uid.trim())) {
                        return true;
                    }
                }
                return false;

            case VIRTUAL_GROUP:
                // Tasks assigned to a virtual group — virtual group members have permission
                // (supports comma-separated multiple groups)
                if (assignmentTarget == null || assignmentTarget.isBlank()) {
                    return false;
                }
                for (String gid : assignmentTarget.split(",")) {
                    String g = gid.trim();
                    if (!g.isEmpty() && isUserInVirtualGroup(userId, g)) {
                        return true;
                    }
                }
                return false;

            default:
                log.warn("Unknown or unsupported assignment type: {}", assignmentType);
                return false;
        }
    }

    /**
     * Check whether the user is a member of a virtual group.
     *
     * @param userId  user ID
     * @param groupId virtual group ID
     * @return whether the user is a member
     */
    public boolean isUserInVirtualGroup(String userId, String groupId) {
        try {
            return adminCenterClient.isUserInVirtualGroup(userId, groupId);
        } catch (Exception e) {
            log.error("Failed to check virtual group membership: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Get all virtual group IDs the user belongs to.
     *
     * @param userId user ID
     * @return list of virtual group IDs
     */
    public List<String> getUserVirtualGroupIds(String userId) {
        return adminCenterClient.getUserVirtualGroupIds(userId);
    }

    /**
     * Get the user's role list.
     *
     * @param userId user ID
     * @return list of role codes
     */
    public List<String> getUserRoles(String userId) {
        return adminCenterClient.getUserRoles(userId);
    }
}
