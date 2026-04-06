package com.workflow.util;

import java.util.Locale;

/**
 * 判断「发起人孤儿任务修复」是否允许把未指派的任务幂等写回给流程发起人。
 * <p>
 * 仅当 BPMN 上本节点为发起人办理语义时才允许；BU_ROLE、HIERARCHY 等非发起人节点禁止修复，
 * 否则查询待办时会把任务误派给发起人（见 {@code TaskManagerComponent.appendUnassignedInitiatorTasks}）。
 * </p>
 */
public final class InitiatorOrphanRepairEligibility {

    private InitiatorOrphanRepairEligibility() {
    }

    /**
     * @param assigneeTypeExtension custom:property assigneeType，可为 null/空白
     * @param flowableUserTaskAssignee Flowable UserTask 标准 assignee 表达式（仅当扩展未写 assigneeType 时使用）
     * @return 是否允许将未指派任务修复为当前发起人用户
     */
    public static boolean shouldRepair(String assigneeTypeExtension, String flowableUserTaskAssignee) {
        if (assigneeTypeExtension != null && !assigneeTypeExtension.isBlank()) {
            return isInitiatorAssigneeType(assigneeTypeExtension);
        }
        if (flowableUserTaskAssignee != null && !flowableUserTaskAssignee.isBlank()) {
            return isInitiatorExpression(flowableUserTaskAssignee.trim());
        }
        return false;
    }

    private static boolean isInitiatorAssigneeType(String raw) {
        if (raw == null) {
            return false;
        }
        String u = raw.trim().toUpperCase(Locale.ROOT);
        return "INITIATOR".equals(u) || "PROCESS_INITIATOR".equals(u);
    }

    /**
     * 与 {@link com.workflow.listener.TaskAssignmentListener#isInitiatorExpression(String)} 对齐。
     */
    private static boolean isInitiatorExpression(String expr) {
        if (expr == null || expr.isEmpty()) {
            return false;
        }
        String e = expr.trim();
        if ("${initiator}".equals(e) || "${initiatorId}".equalsIgnoreCase(e)) {
            return true;
        }
        return e.matches("(?i)^\\$\\{\\s*initiator\\s*}$") || e.matches("(?i)^\\$\\{\\s*initiatorId\\s*}$");
    }
}
