package com.admin.exception;

/**
 * 功能单元未找到异常
 */
public class FunctionUnitNotFoundException extends AdminBusinessException {
    
    public FunctionUnitNotFoundException(String functionUnitId) {
        super("FUNCTION_UNIT_NOT_FOUND", "功能单元不存在: " + functionUnitId);
    }
    
    public FunctionUnitNotFoundException(String code, String version) {
        super("FUNCTION_UNIT_NOT_FOUND", "功能单元不存在: " + code + ":" + version);
    }
}
