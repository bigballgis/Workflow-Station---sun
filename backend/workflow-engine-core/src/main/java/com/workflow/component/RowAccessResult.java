package com.workflow.component;

/**
 * 行访问检查结果（原 {@link DataAccessSecurityComponent} 内嵌类型）。
 */
public class RowAccessResult {
    private boolean allowed;
    private String policyId;
    private String message;

    public static RowAccessResult allowed() {
        RowAccessResult result = new RowAccessResult();
        result.allowed = true;
        result.message = "访问允许";
        return result;
    }

    public static RowAccessResult denied(String policyId, String message) {
        RowAccessResult result = new RowAccessResult();
        result.allowed = false;
        result.policyId = policyId;
        result.message = message;
        return result;
    }

    public boolean isAllowed() {
        return allowed;
    }

    public String getPolicyId() {
        return policyId;
    }

    public String getMessage() {
        return message;
    }
}
