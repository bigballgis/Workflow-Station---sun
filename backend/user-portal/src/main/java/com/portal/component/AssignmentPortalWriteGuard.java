package com.portal.component;

import com.fasterxml.jackson.databind.JsonNode;
import com.platform.common.assignment.AssignmentWriteDecision;

/** Exact local write binding; callers keep the packet on the server and validate it inside the real write transaction. */
public interface AssignmentPortalWriteGuard {
    AssignmentWriteDecision prepare(Request request);
    void enter(AssignmentWriteDecision decision, Request request);
    void written(AssignmentWriteDecision decision, boolean changesCandidates);

    record Request(String taskId, String actor, String operation, JsonNode payload, String representedUser, String instanceId) {
        public static Request of(String task, String actor, String operation, Object payload, String instanceId) {
            return new Request(task, actor, operation, com.platform.common.util.JsonUtils.getObjectMapper().valueToTree(payload), null, instanceId);
        }
        public String digest() { return com.platform.common.assignment.AssignmentRequestDigest.of(this); }
    }
}
