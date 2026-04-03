package com.developer.security;

/**
 * 工作区隔离拒绝访问（由全局异常处理映射为 403）
 */
public class FunctionUnitWorkspaceAccessDeniedException extends RuntimeException {

    public FunctionUnitWorkspaceAccessDeniedException(String message) {
        super(message);
    }
}
