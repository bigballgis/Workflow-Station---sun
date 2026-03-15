package com.admin.exception;

/**
 * Superset 同步异常
 */
public class SupersetSyncException extends AdminBusinessException {

    public SupersetSyncException(String message) {
        super("SUPERSET_SYNC_FAILED", message);
    }

    public SupersetSyncException(String message, Throwable cause) {
        super("SUPERSET_SYNC_FAILED", message, cause);
    }
}
