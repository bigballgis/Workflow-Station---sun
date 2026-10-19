package com.portal.util;

import com.portal.dto.TaskActionInfo;
import com.portal.dto.TaskInfo;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Objects;
import java.util.Set;

/**
 * To Do「可批量操作」的唯一判据：列表、分组、批量提交三处共用，避免各写一份后漂移。
 */
public final class BatchTaskEligibility {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 批量只提供通过/驳回类动作（详情页 useCustomActions 同样把这四类映射成 APPROVE/REJECT） */
    private static final Set<String> APPROVE_TYPES = Set.of("APPROVE", "PROCESS_SUBMIT");
    private static final Set<String> REJECT_TYPES = Set.of("REJECT", "PROCESS_REJECT");

    private BatchTaskEligibility() {
    }

    /**
     * 节点开了 allowBatch，且任务由当前用户本人办理（指派给我 / 我已认领）；
     * 未认领的认领池任务、多实例子任务不参与。
     */
    public static boolean isEligible(TaskInfo task, String userId) {
        return task != null
                && userId != null
                && task.isAllowBatch()
                && !task.isMultiInstanceSubTask()
                && task.getProcessDefinitionId() != null
                && task.getTaskDefinitionKey() != null
                && userId.equals(task.getAssignee());
    }

    /**
     * 任务组 = 发起时钉死的功能单元发布版本(目录行,即 DW Version Management 的 1.0.x)+ 节点。
     * 不按 Flowable 流程定义版本分:同一发布可对应多个引擎部署(重复部署会递增引擎版本号),
     * 按它分会把同一发布拆成多组,且引擎版本号与 DW 版本对不上。没有钉死发布的流程实例才按引擎定义分。
     * 需先经 RequestIdEnricher 填好 functionUnitCatalogId。
     */
    public static String groupKeyOf(TaskInfo task) {
        String catalogId = task.getFunctionUnitCatalogId();
        return catalogId != null && !catalogId.isBlank()
                ? "fu:" + catalogId.trim()
                : "pd:" + task.getProcessDefinitionId();
    }

    public static boolean inGroup(TaskInfo task, String groupKey, String taskDefinitionKey) {
        return Objects.equals(groupKeyOf(task), groupKey)
                && Objects.equals(task.getTaskDefinitionKey(), taskDefinitionKey);
    }

    /** 动作对应的 complete action；非批量类动作返回 null */
    public static String completeActionOf(TaskActionInfo action) {
        String type = action.getActionType() == null ? "" : action.getActionType().trim().toUpperCase();
        if (APPROVE_TYPES.contains(type)) {
            return "APPROVE";
        }
        if (REJECT_TYPES.contains(type)) {
            return "REJECT";
        }
        return null;
    }

    /**
     * 解析动作 configJson；非对象 JSON 抛 IllegalArgumentException（配置坏了要报出来，不当作空配置）。
     */
    public static JsonNode parseConfig(TaskActionInfo action) {
        String raw = action.getConfigJson();
        if (raw == null || raw.isBlank()) {
            return MAPPER.createObjectNode();
        }
        try {
            JsonNode node = MAPPER.readTree(raw);
            if (node != null && node.isObject()) {
                return node;
            }
        } catch (Exception ignored) {
            // fall through
        }
        throw new IllegalArgumentException("Invalid action configJson: " + action.getActionId());
    }

    public static boolean requiresComment(JsonNode config) {
        return config.path("requireComment").asBoolean(false);
    }

    public static String confirmMessage(JsonNode config) {
        JsonNode node = config.get("confirmMessage");
        if (node == null || !node.isTextual() || node.asText().isBlank()) {
            return null;
        }
        return node.asText().trim();
    }
}
