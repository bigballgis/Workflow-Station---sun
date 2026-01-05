package com.admin.exception;

/**
 * 部门未找到异常
 */
public class DepartmentNotFoundException extends AdminBusinessException {
    
    public DepartmentNotFoundException(String deptId) {
        super("DEPARTMENT_NOT_FOUND", "部门不存在: " + deptId);
    }
}
