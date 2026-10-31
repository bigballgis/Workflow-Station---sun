package com.platform.common.assignment;

import com.platform.common.enums.ErrorCode;
import com.platform.common.exception.BusinessException;

/** Explicit service authentication for assignment snapshots and durable recovery, shared by their owning services. */
public final class AssignmentServiceAuthentication {
    private AssignmentServiceAuthentication() { }
    public static void require(String configured, String presented) {
        if (configured == null || configured.isBlank()) throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE);
        if (presented == null || !java.security.MessageDigest.isEqual(configured.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                presented.getBytes(java.nio.charset.StandardCharsets.UTF_8))) throw new BusinessException(ErrorCode.FORBIDDEN);
    }
}
