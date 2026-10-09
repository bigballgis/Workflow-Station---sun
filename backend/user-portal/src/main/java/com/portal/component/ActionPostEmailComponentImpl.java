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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

@Slf4j
@Component
@RequiredArgsConstructor
public class ActionPostEmailComponentImpl implements ActionPostEmailComponent {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final TaskActionService taskActionService;
    private final TaskQueryComponent taskQueryComponent;
    private final ActionDefinitionRepository actionDefinitionRepository;
    private final ProcessInstanceRepository processInstanceRepository;
    private final EventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    @Override
    public <T> T runWithPostEmail(ActionPostEmailContext context, Supplier<T> action) {
        ResolvedAction resolved = resolveIfNeeded(context);
        T result = action.get();
        if (resolved != null) {
            publish(context, resolved, result);
        }
        return result;
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
        Map<String, Object> postEmail = readPostEmail(context.getActionId(), bound.getConfigJson());
        if (!isEnabled(postEmail)) {
            return null;
        }
        return new ResolvedAction(bound, postEmail);
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
            // Withdraw cancels the process before this lookup, so the runtime task is often gone.
        }
        String functionUnitKey = resolveFunctionUnitKey(context);
        if (!StringUtils.hasText(functionUnitKey)) {
            return null;
        }
        return actionDefinitionRepository
                .findCatalogActionForFunctionUnit(context.getActionId(), functionUnitKey)
                .or(() -> actionDefinitionRepository.findDwActionForFunctionUnit(
                        context.getActionId(), functionUnitKey))
                .map(this::toInfo)
                .orElse(null);
    }

    /**
     * A client-supplied task id is trusted only when this call has no process yet (the task id is
     * the authorized path id) or the loaded task belongs to that process. A task that still exists
     * on another process is rejected. A missing task is not a foreign task: withdraw has already
     * cancelled it.
     */
    private boolean taskBelongsToAnotherProcess(ActionPostEmailContext context) {
        if (!StringUtils.hasText(context.getProcessInstanceId())) {
            return false;
        }
        return taskQueryComponent.getTaskById(context.getTaskId())
                .map(task -> !context.getProcessInstanceId().equals(task.getProcessInstanceId()))
                .orElse(false);
    }

    /** Start passes the path key. Withdraw reads the code stored on the process instance. */
    private String resolveFunctionUnitKey(ActionPostEmailContext context) {
        if (StringUtils.hasText(context.getFunctionUnitCode())) {
            return context.getFunctionUnitCode();
        }
        if (!StringUtils.hasText(context.getProcessInstanceId())) {
            return null;
        }
        return processInstanceRepository.findById(context.getProcessInstanceId())
                .map(ProcessInstance::getFunctionUnitCode)
                .orElse(null);
    }

    private TaskActionInfo toInfo(ActionDefinition action) {
        return TaskActionInfo.builder()
                .actionId(action.getId())
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

    private Map<String, Object> readPostEmail(String actionId, String configJson) {
        if (!StringUtils.hasText(configJson)) {
            return Map.of();
        }
        try {
            Map<String, Object> config = objectMapper.readValue(configJson, MAP_TYPE);
            Object raw = config.get("postEmail");
            if (raw instanceof Map<?, ?> map) {
                return objectMapper.convertValue(map, MAP_TYPE);
            }
        } catch (Exception e) {
            // FALLBACK(external): mail is a side path. A broken config must not fail the Action.
            log.error("[ACTION-EMAIL] configJson parse failed actionId={}: {}", actionId, e.getMessage());
        }
        return Map.of();
    }

    private static boolean isEnabled(Map<String, Object> postEmail) {
        Object enabled = postEmail.get("enabled");
        return Boolean.TRUE.equals(enabled) || "true".equalsIgnoreCase(String.valueOf(enabled));
    }

    private void publish(ActionPostEmailContext context, ResolvedAction resolved, Object result) {
        String processInstanceId = resolveProcessInstanceId(context, result);
        if (!StringUtils.hasText(processInstanceId)) {
            log.warn("[ACTION-EMAIL] skip missing processInstanceId actionId={}", context.getActionId());
            return;
        }
        ActionEmailRequestedEvent event = ActionEmailRequestedEvent.builder()
                .eventType("ACTION_EMAIL_REQUESTED")
                .sourceService("user-portal")
                .processInstanceId(processInstanceId)
                .taskId(context.getTaskId())
                .actionId(resolved.info().getActionId())
                .actionName(resolved.info().getActionName())
                .actionType(resolved.info().getActionType())
                .operatorId(context.getOperatorId())
                .comment(context.getComment())
                .postEmail(resolved.postEmail())
                .build();
        try {
            // FALLBACK(external): notification side-path — Kafka publish must not fail the Action.
            eventPublisher.publish(event).exceptionally(ex -> {
                log.error("[ACTION-EMAIL] publish failed actionId={} process={}: {}",
                        context.getActionId(), processInstanceId, ex.getMessage(), ex);
                return null;
            });
        } catch (RuntimeException e) {
            // FALLBACK(external): same as above when KafkaTemplate.send throws synchronously.
            log.error("[ACTION-EMAIL] publish failed actionId={} process={}: {}",
                    context.getActionId(), processInstanceId, e.getMessage(), e);
        }
    }

    private String resolveProcessInstanceId(ActionPostEmailContext context, Object result) {
        if (StringUtils.hasText(context.getProcessInstanceId())) {
            return context.getProcessInstanceId();
        }
        if (result instanceof com.portal.dto.ProcessInstanceInfo info && StringUtils.hasText(info.getId())) {
            return info.getId();
        }
        if (StringUtils.hasText(context.getTaskId())) {
            return taskQueryComponent.getTaskById(context.getTaskId())
                    .map(TaskInfo::getProcessInstanceId)
                    .orElse(null);
        }
        return null;
    }

    private record ResolvedAction(TaskActionInfo info, Map<String, Object> postEmail) {}
}
