package com.platform.common.functionunit;

import com.platform.common.enums.ErrorCode;
import com.platform.common.exception.PlatformException;
import java.util.Map;

/** A version routing failure is explicit and never permits a live-design fallback. */
public final class FunctionUnitVersionException extends PlatformException {
    public FunctionUnitVersionException(String reason, String reference) {
        super(ErrorCode.VERSION_CONFLICT, reason,
                Map.of("reason", reason, "reference", reference == null ? "" : reference));
    }
}
