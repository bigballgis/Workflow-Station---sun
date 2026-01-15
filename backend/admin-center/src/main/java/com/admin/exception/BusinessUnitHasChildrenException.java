package com.admin.exception;

/**
 * 业务单元存在子业务单元异常
 */
public class BusinessUnitHasChildrenException extends RuntimeException {
    
    private final String unitId;
    
    public BusinessUnitHasChildrenException(String unitId) {
        super("业务单元存在子业务单元，无法删除: " + unitId);
        this.unitId = unitId;
    }
    
    public String getUnitId() {
        return unitId;
    }
}
