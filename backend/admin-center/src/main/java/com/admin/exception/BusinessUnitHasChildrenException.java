package com.admin.exception;

/**
 * 业务单元存在子业务单元异常
 */
public class BusinessUnitHasChildrenException extends RuntimeException {
    
    private final String unitId;
    private static final String MESSAGE_KEY = "admin.bu_has_children";
    
    public BusinessUnitHasChildrenException(String unitId) {
        super("Business unit has child units, cannot delete: " + unitId);
        this.unitId = unitId;
    }
    
    public String getUnitId() {
        return unitId;
    }
    
    public String getMessageKey() {
        return MESSAGE_KEY;
    }
}
