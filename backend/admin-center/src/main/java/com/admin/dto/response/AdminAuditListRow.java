package com.admin.dto.response;

/**
 * Slim Admin Center audit list row. TEXT snapshots (old/new/changeDetails)
 * stay off this payload and are loaded by id when the detail dialog opens.
 */
public record AdminAuditListRow(
        String id,
        String action,
        String resourceType,
        String resourceId,
        String username,
        String ipAddress,
        String result,
        Integer duration,
        String createdAt) {
}
