package com.admin.exception;

/**
 * Activepieces API 调用异常。
 */
public class ServiceTaskApiException extends AdminBusinessException {

    public ServiceTaskApiException(String message) {
        super("ACTIVEPIECES_API_ERROR", message);
    }

    public ServiceTaskApiException(String message, Throwable cause) {
        super("ACTIVEPIECES_API_ERROR", message, cause);
    }
}
