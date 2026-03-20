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
        super("AI_LOCK_CONFLICT", "功能单元已被其他用户锁定");
        this.lockInfo = lockInfo;
    }
}
