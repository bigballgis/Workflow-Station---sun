package com.admin.exception;

/**
 * 业务单元存在成员异常
 */
public class BusinessUnitHasMembersException extends RuntimeException {
    
    private final String unitId;
    
    public BusinessUnitHasMembersException(String unitId) {
        super("业务单元存在成员，无法删除: " + unitId);
        this.unitId = unitId;
    }
    
    public String getUnitId() {
        return unitId;
    }
}
