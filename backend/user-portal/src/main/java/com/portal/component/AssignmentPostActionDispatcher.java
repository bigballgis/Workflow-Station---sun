package com.portal.component;

import com.platform.common.assignment.*;
import com.platform.common.dto.ApiResponse;
import com.platform.common.util.JsonUtils;
import com.portal.client.AssignmentEngineTransport;
import com.portal.repository.AssignmentPostActionRepository;
import com.portal.util.ActionEmailRuleConditionEvaluator;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Optional mail is recovered independently of the case's business synchronization watermark. */
@Component
public class AssignmentPostActionDispatcher {
    private final AssignmentPostActionRepository repository;
    private final ActionEmailRuleConditionEvaluator conditions;
    private final AssignmentEngineTransport engine;
    private static final org.slf4j.Logger LOG=org.slf4j.LoggerFactory.getLogger(AssignmentPostActionDispatcher.class);
    public AssignmentPostActionDispatcher(AssignmentPostActionRepository repository,ActionEmailRuleConditionEvaluator conditions,AssignmentEngineTransport engine) {
        this.repository=repository; this.conditions=conditions; this.engine=engine;
    }
    @Scheduled(fixedDelay=1000,initialDelay=10000)
    public void dispatch() {
        long start=System.nanoTime();
        for (var due:repository.due(10)) {
            try { dispatch(due); repository.dispatched(due.operationId()); }
            catch (RuntimeException unavailable) {
                repository.retry(due.operationId());
                LOG.warn("Completion post-action delivery deferred: operationId={}",due.operationId());
            }
            if (System.nanoTime()-start>=1_500_000_000L) break;
        }
    }
    void dispatch(AssignmentPostActionRepository.Due due) {
        var plan=JsonUtils.getObjectMapper().convertValue(due.context().get("plan"),AssignmentPostAction.Plan.class);
        var filters=new ArrayList<Map<String,Object>>();
        for (var rule:plan.rules()) {
            Object configured=rule.get("condition");
            filters.add(configured instanceof Map<?,?>?JsonUtils.getObjectMapper().convertValue(configured,
                    new com.fasterxml.jackson.core.type.TypeReference<>() { }):Map.of());
        }
        var matches=conditions.evaluateSnapshot(due.context().path("row").toString(),filters);
        if (matches.length!=plan.rules().size()) throw new IllegalStateException("Invalid frozen mail condition result");
        for (int index=0;index<matches.length;index++) if (matches[index]) {
            var queued=engine.post(List.of("post-actions"),new AssignmentPostAction.Enqueue(due.operationId(),index),
                    new ParameterizedTypeReference<ApiResponse<Boolean>>() { },false);
            if (!Boolean.TRUE.equals(queued)) throw new AssignmentFailure(AssignmentFailure.Reason.ASSIGNMENT_DEPENDENCY_UNAVAILABLE);
        }
    }
}
