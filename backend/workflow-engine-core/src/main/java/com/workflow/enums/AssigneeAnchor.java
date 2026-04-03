package com.workflow.enums;

/**
 * 组织类分配中，「相对谁」取 BU 链 / 经理：发起人，或物理上最近完成的用户任务办理人。
 * <p>详见 {@code .kiro/docs/assignee-type-convergence.md}。</p>
 */
public enum AssigneeAnchor {

    INITIATOR("INITIATOR"),

    LAST_TASK_ASSIGNEE("LAST_TASK_ASSIGNEE");

    private final String code;

    AssigneeAnchor(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    public static AssigneeAnchor fromCode(String raw) {
        if (raw == null || raw.isBlank()) {
            return INITIATOR;
        }
        String u = raw.trim().toUpperCase();
        if ("LAST_TASK_ASSIGNEE".equals(u) || "LAST".equals(u) || "CURRENT".equals(u)) {
            return LAST_TASK_ASSIGNEE;
        }
        return INITIATOR;
    }
}
