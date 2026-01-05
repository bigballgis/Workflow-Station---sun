package com.admin.exception;

/**
 * 部门存在子部门异常
 */
public class DepartmentHasChildrenException extends AdminBusinessException {
    
    public DepartmentHasChildrenException(String deptId) {
        super("DEPARTMENT_HAS_CHILDREN", "部门存在子部门，无法删除: " + deptId);
    }
}
