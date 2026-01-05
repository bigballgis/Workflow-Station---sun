package com.admin.exception;

/**
 * 部门存在成员异常
 */
public class DepartmentHasMembersException extends AdminBusinessException {
    
    public DepartmentHasMembersException(String deptId) {
        super("DEPARTMENT_HAS_MEMBERS", "部门存在成员，无法删除: " + deptId);
    }
}
