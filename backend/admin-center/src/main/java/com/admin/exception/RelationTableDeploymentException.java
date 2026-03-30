package com.admin.exception;

/**
 * Relation Table 部署 DDL 执行失败异常
 */
public class RelationTableDeploymentException extends AdminBusinessException {

    public RelationTableDeploymentException(String message) {
        super("RELATION_TABLE_DEPLOYMENT_FAILED", message);
    }

    public RelationTableDeploymentException(String message, Throwable cause) {
        super("RELATION_TABLE_DEPLOYMENT_FAILED", message, cause);
    }
}
