package com.portal.dto;

import java.util.List;

/**
 * To Do「可批量操作」任务组：同一功能单元发布版本的同一节点。
 *
 * @param groupKey 组标识(BatchTaskEligibility.groupKeyOf),下钻查询原样带回
 * @param version  功能单元发布版本(如 1.0.23,与 DW Version Management 一致);未钉死发布时为 null
 */
public record BatchTaskGroup(
        String groupKey,
        String processDefinitionKey,
        String functionUnitName,
        String version,
        String taskDefinitionKey,
        String nodeName,
        int count,
        List<BatchTaskAction> actions) {

    public BatchTaskGroup {
        actions = actions == null ? List.of() : List.copyOf(actions);
    }

    /**
     * 可批量执行的动作，按钮呈现（名称/图标/颜色）与待办详情页同一套 DW 配置；
     * requireComment=true 时前端弹一次统一意见；configJson 原样带出，前端按其 allowedRoles
     * 决定当前会话是否可见（与详情页 TaskActionBar 同一判据）
     */
    public record BatchTaskAction(
            String actionId,
            String actionName,
            String actionType,
            String icon,
            String buttonColor,
            boolean requireComment,
            String confirmMessage,
            String configJson) {
    }
}
