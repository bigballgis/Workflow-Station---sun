package com.workflow.listener;

import lombok.extern.slf4j.Slf4j;
import org.flowable.common.engine.api.delegate.event.FlowableEngineEventType;
import org.flowable.common.engine.api.delegate.event.FlowableEvent;
import org.flowable.common.engine.api.delegate.event.FlowableEventListener;
import org.flowable.engine.HistoryService;
import org.flowable.engine.delegate.event.FlowableProcessEngineEvent;
import org.flowable.engine.history.HistoricActivityInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
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
    
    @Autowired
    @Lazy
    private HistoryService historyService;
    
    @Value("${user-portal.url:http://user-portal:8080}")
    private String userPortalUrl;

    @Override
    public void onEvent(FlowableEvent event) {
        if (event.getType() == FlowableEngineEventType.PROCESS_COMPLETED) {
            FlowableProcessEngineEvent processEvent = (FlowableProcessEngineEvent) event;
            String processInstanceId = processEvent.getProcessInstanceId();
            
            log.info("Process completed event received for process instance: {}", processInstanceId);
            
            try {
                // Get last activity node name in current thread since HistoryService needs to be within a transaction
                String lastActivityName = getLastActivityName(processInstanceId);
                
                // Async notify user-portal to update process instance status.
                // Must be async to avoid deadlock: completeTask(@Transactional) holds ProcessInstance row lock
                // -> sync call to workflow-engine -> listener sync callback to user-portal markProcessAsCompleted
                // -> waits for same row lock -> deadlock
                CompletableFuture.runAsync(() -> {
                    try {
                        // Short delay to ensure completeTask transaction has committed
                        Thread.sleep(500);
                        
                        String url = userPortalUrl + "/api/portal/processes/" + processInstanceId + "/complete";
                        log.info("Async notifying user-portal about process completion: {}", url);
                        
                        Map<String, Object> request = new HashMap<>();
                        request.put("processInstanceId", processInstanceId);
                        request.put("endTime", System.currentTimeMillis());
                        request.put("lastActivityName", lastActivityName);
                        
                        restTemplate.postForObject(url, request, Map.class);
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
