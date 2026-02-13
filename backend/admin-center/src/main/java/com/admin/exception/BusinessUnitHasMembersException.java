package com.admin.exception;

/**
 * 业务单元存在成员异常
 */
public class BusinessUnitHasMembersException extends RuntimeException {
    
    private final String unitId;
    private static final String MESSAGE_KEY = "admin.bu_has_members";
    
    public BusinessUnitHasMembersException(String unitId) {
        super("Business unit has members, cannot delete: " + unitId);
        this.unitId = unitId;
    }
    
    public String getUnitId() {
        return unitId;
    }
    
    public String getMessageKey() {
        return MESSAGE_KEY;
    }
}
