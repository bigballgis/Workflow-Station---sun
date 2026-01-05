package com.admin.exception;

/**
 * 循环依赖异常
 */
public class CircularDependencyException extends AdminBusinessException {
    
    public CircularDependencyException(String deptId, String newParentId) {
        super("CIRCULAR_DEPENDENCY", 
              String.format("移动部门 %s 到 %s 会造成循环依赖", deptId, newParentId));
    }
}
