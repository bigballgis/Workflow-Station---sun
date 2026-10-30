package com.workflow.assignment;

import com.platform.common.assignment.*;
import com.platform.common.util.JsonUtils;
import com.platform.messaging.event.ActionEmailRequestedEvent;
import com.workflow.email.action.ActionEmailDeliveryRepository;
import org.springframework.stereotype.Component;

/** Reuses the existing durable mail delivery queue; retries use the same operation/rule event ID. */
@Component
public class AssignmentPostActionEnqueue {
    private final AssignmentOperationRepository operations;
    private final ActionEmailDeliveryRepository deliveries;
    public AssignmentPostActionEnqueue(AssignmentOperationRepository operations,ActionEmailDeliveryRepository deliveries) {
        this.operations=operations; this.deliveries=deliveries;
    }
    public void enqueue(AssignmentPostAction.Enqueue request) {
        var receipt=operations.find(request.operationId()).orElseThrow(this::invalid);
        var effect=JsonUtils.getObjectMapper().convertValue(receipt.acceptedEffect(),AssignmentCompletionRequest.Effect.class);
        var plan=JsonUtils.getObjectMapper().convertValue(effect.postAction(),AssignmentPostAction.Plan.class);
        if (request.ruleIndex()<0 || request.ruleIndex()>=plan.rules().size()) throw invalid();
        var event=ActionEmailRequestedEvent.builder().eventId(AssignmentPostAction.eventId(request.operationId(),request.ruleIndex()))
                .eventType("ACTION_EMAIL_REQUESTED").sourceService("user-portal").processInstanceId(effect.instanceId()).taskId(receipt.taskId())
                .actionId(plan.actionId()).actionName(plan.actionName()).actionType(plan.actionType()).operatorId(receipt.actorUserId()).comment(plan.comment())
                .postEmail(AssignmentPostAction.payload(plan.rules().get(request.ruleIndex())))
                .assignmentOperationId(request.operationId()).assignmentRuleIndex(request.ruleIndex()).build();
        event.initializeDefaults();
        deliveries.insertPending(event.getEventId(),JsonUtils.toJson(event),event.getProcessInstanceId(),event.getActionId());
    }
    private AssignmentFailure invalid() { return new AssignmentFailure(AssignmentFailure.Reason.ASSIGNMENT_OPERATION_CONFLICT); }
}
