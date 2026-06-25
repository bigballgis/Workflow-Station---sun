package com.admin.exception;

/**
 * Activepieces API 调用异常。
 */
public class ActivepiecesApiException extends AdminBusinessException {

    public ActivepiecesApiException(String message) {
        super("ACTIVEPIECES_API_ERROR", message);
    }

    public ActivepiecesApiException(String message, Throwable cause) {
        super("ACTIVEPIECES_API_ERROR", message, cause);
    }
}
