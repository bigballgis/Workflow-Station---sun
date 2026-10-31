package com.portal.security;

import com.platform.common.assignment.AssignmentFailure;
import com.platform.security.util.SecurityContextUtils;

/** Protected operations require the authenticated principal; the legacy X-User-Id compatibility header is insufficient. */
public final class AssignmentPortalActor {
    private AssignmentPortalActor() { }
    public static void require(String actor) {
        if (actor==null || actor.isBlank() || SecurityContextUtils.getCurrentUserId().filter(actor::equals).isEmpty()) {
            throw new AssignmentFailure(AssignmentFailure.Reason.ASSIGNMENT_EXCLUDED);
        }
    }
}
