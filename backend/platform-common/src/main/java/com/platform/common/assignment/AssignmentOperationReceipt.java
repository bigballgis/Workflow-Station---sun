package com.platform.common.assignment;

import java.util.Map;
import java.util.UUID;

/** Server-to-server immutable accepted effect; user-facing status APIs must omit the effect payload. */
public record AssignmentOperationReceipt(UUID operationId, UUID caseId, String taskId, String actorUserId,
        String representedUserId, String operation, String requestDigest, long operationSeq,
        long portalBaseRevision, Map<String,Object> acceptedEffect, String state) { }
