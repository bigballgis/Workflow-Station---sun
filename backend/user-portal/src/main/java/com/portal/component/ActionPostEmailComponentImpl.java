package com.portal.component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.messaging.event.ActionEmailRequestedEvent;
import com.platform.messaging.service.EventPublisher;
import com.portal.dto.TaskActionInfo;
import com.portal.dto.TaskInfo;
import com.portal.entity.ActionDefinition;
import com.portal.entity.ProcessInstance;
import com.portal.repository.ActionDefinitionRepository;
import com.portal.repository.ProcessInstanceRepository;
import com.portal.service.TaskActionService;
import com.portal.util.ActionEmailRuleConditionEvaluator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

@Slf4j
@Component
@RequiredArgsConstructor
public class ActionPostEmailComponentImpl implements ActionPostEmailComponent {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
    private static final TypeReference<List<Map<String, Object>>> RULE_LIST_TYPE = new TypeReference<>() {};

    private final TaskActionService taskActionService;
    private final TaskQueryComponent taskQueryComponent;
    private final ActionDefinitionRepository actionDefinitionRepository;
    private final ProcessInstanceRepository processInstanceRepository;
    private final EventPublisher eventPublisher;
    private final ObjectMapper objectMapper;
    private final ActionEmailRuleConditionEvaluator ruleConditionEvaluator;

    @Override
    public <T> T runWithPostEmail(ActionPostEmailContext context, Supplier<T> action) {
        boolean starting = shouldResolveAfterAction(context);
        ResolvedAction resolved = starting ? null : resolveIfNeeded(context);
        T result = action.get();
        if (starting) {
            context = ActionPostEmailContext.builder().actionId(context.getActionId())
                    .operation(context.getOperation()).functionUnitCode(context.getFunctionUnitCode())
                    .processInstanceId(resolveProcessInstanceId(context, result))
                    .taskId(context.getTaskId())
                    .operatorId(context.getOperatorId()).comment(context.getComment()).build();
            resolved = resolveIfNeeded(context);
        }
        if (resolved != null) {
            publishMatchingRules(context, resolved, result);
        }
        return result;
    }

    /** START always creates the instance; New Request FORM_POPUP does the same when no id is supplied. */
    private static boolean shouldResolveAfterAction(ActionPostEmailContext context) {
        if (context == null) {
            return false;
        }
        if (ActionPostEmailContext.OP_START.equals(context.getOperation())) {
            return true;
        }
        return ActionPostEmailContext.OP_FORM_POPUP.equals(context.getOperation())
                && !StringUtils.hasText(context.getProcessInstanceId())
                && !StringUtils.hasText(context.getTaskId());
    }

    @Override
    public void runWithPostEmail(ActionPostEmailContext context, Runnable action) {
        runWithPostEmail(context, () -> {
            action.run();
            return null;
        });
    }

    private ResolvedAction resolveIfNeeded(ActionPostEmailContext context) {
        if (context == null || !StringUtils.hasText(context.getActionId())) {
            return null;
        }
        TaskActionInfo bound = findBoundAction(context);
        if (bound == null) {
            log.warn("[ACTION-EMAIL] skip unbound actionId={} operation={} taskId={}",
                    context.getActionId(), context.getOperation(), context.getTaskId());
            return null;
        }
        if (!typeMatches(context.getOperation(), bound.getActionType())) {
            log.warn("[ACTION-EMAIL] skip type mismatch actionId={} operation={} actionType={}",
                    context.getActionId(), context.getOperation(), bound.getActionType());
            return null;
        }
        List<Map<String, Object>> rules = readPostEmailRules(context.getActionId(), bound.getConfigJson());
        if (rules.isEmpty()) {
            return null;
        }
        return new ResolvedAction(bound, rules);
    }

    private TaskActionInfo findBoundAction(ActionPostEmailContext context) {
        if (StringUtils.hasText(context.getTaskId())) {
            if (taskBelongsToAnotherProcess(context)) {
                log.warn("[ACTION-EMAIL] skip taskId={} not on process={}",
                        context.getTaskId(), context.getProcessInstanceId());
                return null;
            }
            TaskActionInfo onTask = taskActionService.getTaskActions(context.getTaskId()).stream()
                    .filter(a -> context.getActionId().equals(a.getActionId()))
                    .findFirst()
                    .orElse(null);
            if (onTask != null || !StringUtils.hasText(context.getProcessInstanceId())) {
                return onTask;
            }
        }
        if (!StringUtils.hasText(context.getProcessInstanceId())) return null;
        var instance = processInstanceRepository.findById(context.getProcessInstanceId())
                .orElseThrow(() -> new com.platform.common.functionunit.FunctionUnitVersionException(
                        "VERSION_RECOVERY_REQUIRED", context.getProcessInstanceId()));
        String catalogId = ProcessVersionContext.of(instance).catalogId();
        var matches = actionDefinitionRepository.findPinnedActions(catalogId, List.of(context.getActionId()));
        if (matches.size() != 1) throw new com.platform.common.functionunit.FunctionUnitVersionException(
                "VERSION_SNAPSHOT_INCOMPLETE", catalogId);
        return toInfo(matches.get(0));
    }

    private boolean taskBelongsToAnotherProcess(ActionPostEmailContext context) {
        if (!StringUtils.hasText(context.getProcessInstanceId())) {
            return false;
        }
        return taskQueryComponent.getTaskById(context.getTaskId())
                .map(task -> !context.getProcessInstanceId().equals(task.getProcessInstanceId()))
                .orElse(false);
    }

    private TaskActionInfo toInfo(ActionDefinition action) {
        return TaskActionInfo.builder()
                .actionId(action.getSourceId() == null ? action.getId() : action.getSourceId())
                .actionName(action.getActionName())
                .actionType(action.getActionType())
                .configJson(action.getConfigJson())
                .build();
    }

    static boolean typeMatches(String operation, String actionType) {
        if (!StringUtils.hasText(operation) || !StringUtils.hasText(actionType)) {
            return false;
        }
        String type = actionType.trim().toUpperCase();
        return switch (operation) {
            case ActionPostEmailContext.OP_SAVE -> "SAVE".equals(type);
            case ActionPostEmailContext.OP_APPROVE -> Set.of("APPROVE", "PROCESS_SUBMIT").contains(type);
            case ActionPostEmailContext.OP_REJECT -> Set.of("REJECT", "PROCESS_REJECT").contains(type);
            case ActionPostEmailContext.OP_RETURN -> "ROLLBACK".equals(type);
            case ActionPostEmailContext.OP_DRAFT -> "DRAFT".equals(type);
            case ActionPostEmailContext.OP_DELEGATE -> "DELEGATE".equals(type);
            case ActionPostEmailContext.OP_TRANSFER -> "TRANSFER".equals(type);
            case ActionPostEmailContext.OP_URGE -> "URGE".equals(type);
            case ActionPostEmailContext.OP_WITHDRAW -> "WITHDRAW".equals(type);
            case ActionPostEmailContext.OP_START -> "PROCESS_SUBMIT".equals(type);
            case ActionPostEmailContext.OP_FORM_POPUP -> "FORM_POPUP".equals(type);
            default -> false;
        };
    }

    List<Map<String, Object>> readPostEmailRules(String actionId, String configJson) {
        if (!StringUtils.hasText(configJson)) {
            return List.of();
        }
        try {
            Map<String, Object> config = objectMapper.readValue(configJson, MAP_TYPE);
            if (config.get("postEmailRules") instanceof List<?> rawRules) {
                List<Map<String, Object>> parsed = objectMapper.convertValue(rawRules, RULE_LIST_TYPE);
                return parsed.stream().filter(this::isRuleEnabled).toList();
            }
            Map<String, Object> legacy = readLegacyPostEmail(config);
            if (legacy.isEmpty()) {
                return List.of();
            }
            return List.of(legacy);
        } catch (Exception e) {
            // FALLBACK(external): mail is a side path. A broken config must not fail the Action.
            log.error("[ACTION-EMAIL] configJson parse failed actionId={}: {}", actionId, e.getMessage());
            return List.of();
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readLegacyPostEmail(Map<String, Object> config) {
        Object raw = config.get("postEmail");
        if (!(raw instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, Object> postEmail = objectMapper.convertValue(map, MAP_TYPE);
        if (!isRuleEnabled(postEmail)) {
            return Map.of();
        }
        return postEmail;
    }

    private boolean isRuleEnabled(Map<String, Object> rule) {
        Object enabled = rule.get("enabled");
        return Boolean.TRUE.equals(enabled) || "true".equalsIgnoreCase(String.valueOf(enabled));
    }

    private void publishMatchingRules(ActionPostEmailContext context, ResolvedAction resolved, Object result) {
        String processInstanceId = resolveProcessInstanceId(context, result);
        if (!StringUtils.hasText(processInstanceId)) {
            log.warn("[ACTION-EMAIL] skip missing processInstanceId actionId={}", context.getActionId());
            return;
        }
        List<Map<String, Object>> rules = resolved.rules();
        List<Map<String, Object>> conditions = new ArrayList<>(rules.size());
        for (Map<String, Object> rule : rules) {
            Object condition = rule.get("condition");
            if (condition instanceof Map<?, ?> map) {
                conditions.add(objectMapper.convertValue(map, MAP_TYPE));
            } else {
                conditions.add(Map.of());
            }
        }
        boolean[] matches = ruleConditionEvaluator.evaluate(processInstanceId, conditions, actionRowOf(result));
        for (int i = 0; i < rules.size(); i++) {
            if (!matches[i]) {
                log.info("[ACTION-EMAIL] skip rule not matched actionId={} ruleId={} process={}",
                        context.getActionId(), rules.get(i).get("id"), processInstanceId);
                continue;
            }
            publishOne(context, resolved.info(), processInstanceId, rules.get(i));
        }
    }

    private void publishOne(
            ActionPostEmailContext context,
            TaskActionInfo info,
            String processInstanceId,
            Map<String, Object> rule) {
        Map<String, Object> postEmail = buildPostEmailPayload(rule);
        ActionEmailRequestedEvent event = ActionEmailRequestedEvent.builder()
                .eventType("ACTION_EMAIL_REQUESTED")
                .sourceService("user-portal")
                .processInstanceId(processInstanceId)
                .taskId(context.getTaskId())
                .actionId(info.getActionId())
                .actionName(info.getActionName())
                .actionType(info.getActionType())
                .operatorId(context.getOperatorId())
                .comment(context.getComment())
                .postEmail(postEmail)
                .build();
        try {
            // FALLBACK(external): notification side-path — Kafka publish must not fail the Action.
            eventPublisher.publish(event).exceptionally(ex -> {
                log.error("[ACTION-EMAIL] publish failed actionId={} ruleId={} process={}: {}",
                        context.getActionId(), rule.get("id"), processInstanceId, ex.getMessage(), ex);
                return null;
            });
        } catch (RuntimeException e) {
            // FALLBACK(external): same as above when KafkaTemplate.send throws synchronously.
            log.error("[ACTION-EMAIL] publish failed actionId={} ruleId={} process={}: {}",
                    context.getActionId(), rule.get("id"), processInstanceId, e.getMessage(), e);
        }
    }

    static Map<String, Object> buildPostEmailPayload(Map<String, Object> rule) {
        Map<String, Object> postEmail = new HashMap<>();
        for (String key : List.of(
                "connectionId", "emailTo", "emailCc", "emailBcc", "emailFrom",
                "emailTemplateId", "emailAttachments")) {
            Object value = rule.get(key);
            if (value != null) {
                postEmail.put(key, value);
            }
        }
        postEmail.put("enabled", true);
        if (rule.get("id") != null) {
            postEmail.put("ruleId", rule.get("id").toString());
        }
        if (rule.get("name") != null && StringUtils.hasText(rule.get("name").toString())) {
            postEmail.put("ruleName", rule.get("name").toString());
        }
        return postEmail;
    }

    private String resolveProcessInstanceId(ActionPostEmailContext context, Object result) {
        if (StringUtils.hasText(context.getProcessInstanceId())) {
            return context.getProcessInstanceId();
        }
        com.portal.dto.ProcessInstanceInfo instance = result instanceof ProcessStartFormPopupOutcome outcome
                ? outcome.instance()
                : result instanceof com.portal.dto.ProcessInstanceInfo info ? info : null;
        if (instance != null && StringUtils.hasText(instance.getId())) {
            return instance.getId();
        }
        if (StringUtils.hasText(context.getTaskId())) {
            return taskQueryComponent.getTaskById(context.getTaskId())
                    .map(TaskInfo::getProcessInstanceId)
                    .orElse(null);
        }
        return null;
    }

    private static ActionTableRowRef actionRowOf(Object result) {
        if (result instanceof ActionTableRowRef row) {
            return row;
        }
        if (result instanceof ProcessStartFormPopupOutcome outcome) {
            return outcome.actionRow();
        }
        return null;
    }

    private record ResolvedAction(TaskActionInfo info, List<Map<String, Object>> rules) {}
}
