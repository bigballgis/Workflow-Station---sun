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

/**
 * 流程完成事件监听器
 * 监听流程完成事件，通知 user-portal 更新流程实例状态
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
                // 获取最后一个活动节点的名称
                String lastActivityName = getLastActivityName(processInstanceId);
                
                // 通知 user-portal 更新流程实例状态
                String url = userPortalUrl + "/api/portal/processes/" + processInstanceId + "/complete";
                log.info("Notifying user-portal about process completion: {}", url);
                
                Map<String, Object> request = new HashMap<>();
                request.put("processInstanceId", processInstanceId);
                request.put("endTime", System.currentTimeMillis());
                request.put("lastActivityName", lastActivityName);
                
                restTemplate.postForObject(url, request, Map.class);
                log.info("Successfully notified user-portal about process completion: {} with lastActivity: {}", 
                        processInstanceId, lastActivityName);
                
            } catch (Exception e) {
                log.error("Failed to notify user-portal about process completion for {}: {}", 
                        processInstanceId, e.getMessage(), e);
            }
        }
    }
    
    /**
     * 获取流程的最后一个活动节点名称
     * 优先返回结束事件的名称（如 "Approved"），如果没有则返回最后一个用户任务
     */
    private String getLastActivityName(String processInstanceId) {
        try {
            // 首先查询结束事件（endEvent），这是流程的真正最后节点
            // 注意：不使用 .finished() 因为在 PROCESS_COMPLETED 事件触发时，endEvent 可能还没有被标记为 finished
            List<HistoricActivityInstance> endEvents = historyService
                    .createHistoricActivityInstanceQuery()
                    .processInstanceId(processInstanceId)
                    .activityType("endEvent")
                    .orderByHistoricActivityInstanceStartTime()
                    .desc()
                    .list();
            
            log.info("Found {} endEvents for process {}", endEvents.size(), processInstanceId);
            
            // 如果有结束事件且有名称，优先返回结束事件的名称
            if (!endEvents.isEmpty()) {
                HistoricActivityInstance endEvent = endEvents.get(0);
                String activityName = endEvent.getActivityName();
                log.info("EndEvent details: name={}, startTime={}, endTime={}", 
                        activityName, endEvent.getStartTime(), endEvent.getEndTime());
                
                if (activityName != null && !activityName.isEmpty() && 
                    !activityName.equalsIgnoreCase("End") && 
                    !activityName.equalsIgnoreCase("结束")) {
                    log.info("Using endEvent for process {}: {}", processInstanceId, activityName);
                    return activityName;
                }
            }
            
            // 如果结束事件没有有意义的名称，查询最后一个用户任务
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
                return activityName != null ? activityName : "已完成";
            }
            
            // 如果没有用户任务，查询服务任务
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
                return activityName != null ? activityName : "已完成";
            }
            
            // 如果都没有找到，返回默认值
            log.warn("No endEvent, userTask or serviceTask found for process {}", processInstanceId);
            return "已完成";
            
        } catch (Exception e) {
            log.error("Failed to get last activity name for process {}: {}", 
                    processInstanceId, e.getMessage());
            return "已完成";
        }
    }

    @Override
    public boolean isFailOnException() {
        // 不因异常而失败，避免影响流程执行
        return false;
    }

    @Override
    public boolean isFireOnTransactionLifecycleEvent() {
        // 在事务提交后触发，确保所有历史数据（包括 endEvent）都已持久化
        return true;
    }

    @Override
    public String getOnTransaction() {
        // 在事务提交后触发
        return "COMMITTED";
    }
}
