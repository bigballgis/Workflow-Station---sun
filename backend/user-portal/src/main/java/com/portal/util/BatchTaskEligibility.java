package com.portal.util;

import com.portal.dto.TaskActionInfo;
import com.portal.dto.TaskInfo;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

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
     * 节点开了 allowBatch，且当前用户能办这条任务：指派给我 / 我已认领；我可认领的 BU Role 认领池任务
     * （批量提交时先替我认领）；或单条提交就能办的其他任务——候选人池（如多人的 HIERARCHY_ROLE）由
     * 单条完成路径自动认领，委托同理。多实例子任务与普通任务同等对待。
     *
     * @param mayProcess 列表/分组传 {@link #isOpenCandidateTask}（任务已在我的 To Do 里，纯内存判断）；
     *                   批量提交传 {@code TaskPermissionEvaluator#canProcessTask}（逐条权威复核）。
     *                   放在最后求值。claimable 须先经 TaskPermissionEvaluator#annotateClaimState 填好。
     */
    public static boolean isEligible(TaskInfo task, String userId, Predicate<TaskInfo> mayProcess) {
        return task != null
                && userId != null
                && task.isAllowBatch()
                && task.getProcessDefinitionId() != null
                && task.getTaskDefinitionKey() != null
                && (userId.equals(task.getAssignee()) || task.isClaimable() || mayProcess.test(task));
    }

    /**
     * 列表侧的「我能办」：To Do 里一条无人处理、有候选人、又不是 BU Role 认领池的任务——引擎正是因为我是
     * 候选人/组成员才把它放进我的 To Do。不在列表上逐行跑 canProcessTask：它的虚拟组判断是一次未缓存的
     * 远程调用（TaskPermissionEvaluator 也注明不要用它再过滤整张列表）。
     */
    public static boolean isOpenCandidateTask(TaskInfo task) {
        boolean unassigned = task.getAssignee() == null || task.getAssignee().isBlank();
        boolean hasCandidates = (task.getCandidateUserIds() != null && !task.getCandidateUserIds().isEmpty())
                || (task.getCandidateGroupIds() != null && !task.getCandidateGroupIds().isEmpty());
        return unassigned && hasCandidates && !BuRolePoolTasks.isClaimPoolTask(task);
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
