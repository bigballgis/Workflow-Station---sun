package com.workflow.listener;

import com.platform.common.util.SafeUrlInput;
import lombok.extern.slf4j.Slf4j;
import com.workflow.component.ProcessCallCascade;
import org.flowable.common.engine.api.delegate.event.FlowableEngineEventType;
import org.flowable.common.engine.api.delegate.event.FlowableEvent;
import org.flowable.common.engine.api.delegate.event.FlowableEventListener;
import org.flowable.engine.HistoryService;
import org.flowable.engine.delegate.event.FlowableProcessEngineEvent;
import org.flowable.engine.history.HistoricActivityInstance;
import org.flowable.variable.api.history.HistoricVariableInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Process completion event listener.
 * Listens for process completion events and notifies user-portal to update process instance status.
 */
@Slf4j
@Component
public class ProcessCompletionListener implements FlowableEventListener {

    @Autowired
    private RestTemplate restTemplate;

    /**
     * Terminates the caller when a called Function Unit ends in rejection.
     *
     * <p>{@code @Lazy} for the same reason as {@link #historyService} below: this listener is
     * wired into the Flowable engine's own configuration, while the cascade needs that engine's
     * RuntimeService — injecting it eagerly closes a bean cycle and the context refuses to start.
     */
    @Autowired(required = false)
    @Lazy
    private ProcessCallCascade processCallCascade;
    
    @Autowired
    @Lazy
    private HistoryService historyService;
    
    @Value("${user-portal.url:http://user-portal:8080}")
    private String userPortalUrl;

    /**
     * Internal service token for the user-portal completion callback. The portal's
     * {@code /api/portal/processes/{id}/complete} endpoint requires a non-blank
     * X-Internal-Service-Token header — without it the callback is rejected (403) and the
     * application stays RUNNING. This matters for processes with no user task (e.g. pure
     * automation: Start → AP service task → End), whose only completion path is this callback.
     */
    @Value("${platform.internal.service-token:wf-internal-service-call}")
    private String internalServiceToken;

    @Override
    public void onEvent(FlowableEvent event) {
        if (event.getType() == FlowableEngineEventType.PROCESS_COMPLETED) {
            FlowableProcessEngineEvent processEvent = (FlowableProcessEngineEvent) event;
            String processInstanceId = processEvent.getProcessInstanceId();
            
            log.info("Process completed event received for process instance: {}", processInstanceId);
            
            try {
                // Get last activity node name in current thread since HistoryService needs to be within a transaction
                String lastActivityName = getLastActivityName(processInstanceId);

                // A rejected Function Unit sub-process must take its caller down with it. Flowable
                // considers a rejection a normal completion — the token simply reached a different
                // end event — so it would otherwise leave the parent parked on its call activity.
                // Read the outcome here, while the instance's variables are still queryable.
                if (processCallCascade != null && endedInRejection(processInstanceId)) {
                    try {
                        String parent = processCallCascade.cascadeToParent(
                                processInstanceId, "called Function Unit was rejected");
                        if (parent != null) {
                            log.info("Process {} was rejected; terminated its calling instance {}",
                                    processInstanceId, parent);
                        }
                    } catch (Exception e) {
                        log.error("Failed to fail the caller of rejected process {}: {}",
                                processInstanceId, e.getMessage(), e);
                    }
                }
                
                // Async notify user-portal to update process instance status.
                // Must be async to avoid deadlock: completeTask(@Transactional) holds ProcessInstance row lock
                // -> sync call to workflow-engine -> listener sync callback to user-portal markProcessAsCompleted
                // -> waits for same row lock -> deadlock
                CompletableFuture.runAsync(() -> {
                    try {
                        // Short delay to ensure completeTask transaction has committed
                        Thread.sleep(500);
                        
                        String url = userPortalUrl + "/api/portal/processes/" + SafeUrlInput.requirePathToken(processInstanceId) + "/complete";
                        log.info("Async notifying user-portal about process completion: {}", url);
                        
                        Map<String, Object> request = new HashMap<>();
                        request.put("processInstanceId", processInstanceId);
                        request.put("endTime", System.currentTimeMillis());
                        request.put("lastActivityName", lastActivityName);

                        HttpHeaders headers = new HttpHeaders();
                        headers.set("X-Internal-Service-Token", internalServiceToken);
                        restTemplate.postForObject(url, new HttpEntity<>(request, headers), Map.class);
                        log.info("Successfully notified user-portal about process completion: {} with lastActivity: {}", 
                                processInstanceId, lastActivityName);
                    } catch (Exception e) {
                        log.error("Failed to async notify user-portal about process completion for {}: {}", 
                                processInstanceId, e.getMessage(), e);
                    }
                });
                
            } catch (Exception e) {
                log.error("Failed to notify user-portal about process completion for {}: {}", 
                        processInstanceId, e.getMessage(), e);
            }
        }
    }
    
    /**
     * Whether this instance finished because it was rejected.
     *
     * <p>Reads the {@code approvalStatus} variable the platform writes on every approve/reject,
     * rather than matching the end event's name against words like "Rejected" — a designer is
     * free to name that node anything, and a wrong answer here would terminate a caller that
     * should have carried on.
     */
    private boolean endedInRejection(String processInstanceId) {
        try {
            HistoricVariableInstance approvalStatus = historyService
                    .createHistoricVariableInstanceQuery()
                    .processInstanceId(processInstanceId)
                    .variableName("approvalStatus")
                    .singleResult();
            return approvalStatus != null
                    && approvalStatus.getValue() instanceof String status
                    && "REJECTED".equalsIgnoreCase(status.trim());
        } catch (Exception e) {
            // Unknown outcome: leave the caller running rather than terminating it on a guess.
            log.warn("Could not read approval outcome of process {}: {}", processInstanceId, e.getMessage());
            return false;
        }
    }

    /**
     * Get last activity node name of the process.
     * Prioritizes returning end event name (e.g. "Approved"); falls back to last user task.
     */
    private String getLastActivityName(String processInstanceId) {
        try {
            // Query end events first (endEvent) - these are the actual last nodes of the process.
            // Note: not using .finished() because at PROCESS_COMPLETED event time, endEvent may not yet be marked as finished
            List<HistoricActivityInstance> endEvents = historyService
                    .createHistoricActivityInstanceQuery()
                    .processInstanceId(processInstanceId)
                    .activityType("endEvent")
                    .orderByHistoricActivityInstanceStartTime()
                    .desc()
                    .list();
            
            log.info("Found {} endEvents for process {}", endEvents.size(), processInstanceId);
            
            // If there is an end event with a name, prioritize returning the end event name
            if (!endEvents.isEmpty()) {
                HistoricActivityInstance endEvent = endEvents.get(0);
                String activityName = endEvent.getActivityName();
                log.info("EndEvent details: name={}, startTime={}, endTime={}", 
                        activityName, endEvent.getStartTime(), endEvent.getEndTime());
                
                if (activityName != null && !activityName.isEmpty() && 
                    !activityName.equalsIgnoreCase("End")) {
                    log.info("Using endEvent for process {}: {}", processInstanceId, activityName);
                    return activityName;
                }
            }
            
            // If end event has no meaningful name, query the last user task
            List<HistoricActivityInstance> userTasks = historyService
                    .createHistoricActivityInstanceQuery()
                    .processInstanceId(processInstanceId)
                    .activityType("userTask")
                    .finished()
                    .orderByHistoricActivityInstanceEndTime()
                    .desc()
                    .list();
            
            if (!userTasks.isEmpty()) {
                HistoricActivityInstance lastUserTask = userTasks.get(0);
                String activityName = lastUserTask.getActivityName();
                log.info("Found last userTask for process {}: {} (end_time: {})", 
                        processInstanceId, activityName, lastUserTask.getEndTime());
                return activityName != null ? activityName : "Completed";
            }
            
            // If no user tasks, query service tasks
            List<HistoricActivityInstance> serviceTasks = historyService
                    .createHistoricActivityInstanceQuery()
                    .processInstanceId(processInstanceId)
                    .activityType("serviceTask")
                    .finished()
                    .orderByHistoricActivityInstanceEndTime()
                    .desc()
                    .list();
            
            if (!serviceTasks.isEmpty()) {
                HistoricActivityInstance lastServiceTask = serviceTasks.get(0);
                String activityName = lastServiceTask.getActivityName();
                log.info("Found last serviceTask for process {}: {} (end_time: {})", 
                        processInstanceId, activityName, lastServiceTask.getEndTime());
                return activityName != null ? activityName : "Completed";
            }
            
            // If nothing found, return default value
            log.warn("No endEvent, userTask or serviceTask found for process {}", processInstanceId);
            return "Completed";
            
        } catch (Exception e) {
            log.error("Failed to get last activity name for process {}: {}", 
                    processInstanceId, e.getMessage());
            return "Completed";
        }
    }

    @Override
    public boolean isFailOnException() {
        // Do not fail on exception to avoid affecting process execution
        return false;
    }

    @Override
    public boolean isFireOnTransactionLifecycleEvent() {
        // Fire after transaction commit to ensure all history data (including endEvent) has been persisted
        return true;
    }

    @Override
    public String getOnTransaction() {
        // Fire after transaction commit
        return "COMMITTED";
    }
}
