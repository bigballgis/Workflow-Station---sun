package com.developer.exception;

import com.developer.dto.LockInfoResponse;
import lombok.Getter;

/**
 * AI 锁冲突异常
 */
@Getter
public class AiLockConflictException extends AiGenerationException {

    private final LockInfoResponse lockInfo;

    public AiLockConflictException(LockInfoResponse lockInfo) {
        super("AI_LOCK_CONFLICT", "Function unit is locked by another user");
        this.lockInfo = lockInfo;
    }
}
