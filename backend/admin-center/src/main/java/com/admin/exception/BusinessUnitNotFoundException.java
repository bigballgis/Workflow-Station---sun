package com.admin.exception;

/**
 * 业务单元未找到异常
 */
public class BusinessUnitNotFoundException extends RuntimeException {
    
    private final String unitId;
    
    public BusinessUnitNotFoundException(String unitId) {
        super("业务单元不存在: " + unitId);
        this.unitId = unitId;
    }
    
    public String getUnitId() {
        return unitId;
    }
}
