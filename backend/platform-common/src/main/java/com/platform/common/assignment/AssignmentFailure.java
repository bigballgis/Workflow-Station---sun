package com.platform.common.assignment;

import com.platform.common.enums.ErrorCode;
import com.platform.common.exception.PlatformException;
import java.util.Map;

/** Typed failure reason in the platform's existing error envelope; no user lists or form data are returned. */
public final class AssignmentFailure extends PlatformException {
    public AssignmentFailure(Reason reason) {
        super(reason.error, reason.name(), Map.of("reason", reason.name()));
    }
    public AssignmentFailure(Reason reason,Throwable cause) {
        super(reason.error,reason.name(),Map.of("reason",reason.name()),cause);
    }

    public enum Reason {
        ASSIGNMENT_EXCLUDED(ErrorCode.PERMISSION_DENIED),
        ASSIGNMENT_STATE_CHANGED(ErrorCode.VERSION_CONFLICT),
        ASSIGNMENT_NO_ELIGIBLE_USERS(ErrorCode.VERSION_CONFLICT),
        ASSIGNMENT_POLICY_INVALID(ErrorCode.VALIDATION_ERROR),
        ASSIGNMENT_CONTEXT_UNAVAILABLE(ErrorCode.SERVICE_UNAVAILABLE),
        ASSIGNMENT_DEPENDENCY_UNAVAILABLE(ErrorCode.SERVICE_UNAVAILABLE),
        ASSIGNMENT_ENGINE_UNSUPPORTED(ErrorCode.VERSION_CONFLICT),
        ASSIGNMENT_OPERATION_CONFLICT(ErrorCode.VERSION_CONFLICT),
        ASSIGNMENT_SYNC_PENDING(ErrorCode.VERSION_CONFLICT),
        ASSIGNMENT_SYNC_CONFLICT(ErrorCode.VERSION_CONFLICT);

        private final ErrorCode error;
        Reason(ErrorCode error) { this.error = error; }
    }
}
