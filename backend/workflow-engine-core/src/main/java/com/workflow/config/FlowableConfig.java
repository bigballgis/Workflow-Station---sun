package com.workflow.config;

import com.workflow.listener.ProcessCompletionListener;
import com.workflow.listener.TaskAssignmentListener;
import org.flowable.common.engine.api.delegate.event.FlowableEngineEventType;
import org.flowable.spring.SpringProcessEngineConfiguration;
import org.flowable.spring.boot.EngineConfigurationConfigurer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Flowable 引擎配置
 * 注册自定义事件监听器
 */
@Configuration
public class FlowableConfig {

    @Autowired
    private TaskAssignmentListener taskAssignmentListener;
    
    @Autowired
    private ProcessCompletionListener processCompletionListener;

    @Autowired
    private com.workflow.recordnote.RecordNoteLifecycleListener recordNoteLifecycleListener;

    @Autowired
    @org.springframework.context.annotation.Lazy
    private com.workflow.assignment.AssignmentCreationLifecycle assignmentCreationLifecycle;

    @Autowired
    private com.workflow.assignment.AssignmentMutationLifecycle assignmentMutationLifecycle;
    @Autowired private com.workflow.assignment.AssignmentCallRowOutputLifecycle assignmentCallRowOutputLifecycle;
    @Autowired @org.springframework.context.annotation.Lazy
    private com.workflow.assignment.AssignmentNativeSignalBoundary assignmentNativeSignalBoundary;
    @Autowired private com.workflow.assignment.AssignmentDefinitionRetention assignmentDefinitionRetention;

    @Bean
    public EngineConfigurationConfigurer<SpringProcessEngineConfiguration> customProcessEngineConfigurer() {
        return processEngineConfiguration -> {
            processEngineConfiguration.addEventSubscriptionServiceConfigurator(assignmentNativeSignalBoundary);
            processEngineConfiguration.setRepositoryService(new com.workflow.assignment.AssignmentRepositoryService(assignmentDefinitionRetention));
            var jobs=new java.util.ArrayList<org.flowable.job.service.JobHandler>();
            if (processEngineConfiguration.getCustomJobHandlers()!=null) jobs.addAll(processEngineConfiguration.getCustomJobHandlers());
            jobs.addAll(com.workflow.assignment.AssignmentDefinitionActionJobs.handlers(assignmentDefinitionRetention));
            processEngineConfiguration.setCustomJobHandlers(jobs);
            var parseHandlers=new java.util.ArrayList<org.flowable.engine.parse.BpmnParseHandler>();
            if (processEngineConfiguration.getPreBpmnParseHandlers()!=null) parseHandlers.addAll(processEngineConfiguration.getPreBpmnParseHandlers());
            parseHandlers.add(new com.workflow.assignment.AssignmentDeploymentParseGuard(assignmentDefinitionRetention));
            processEngineConfiguration.setPreBpmnParseHandlers(parseHandlers);
            // 注册事件监听器
            Map<String, List<org.flowable.common.engine.api.delegate.event.FlowableEventListener>> typedListeners = 
                    new HashMap<>();
            
            // 任务创建事件监听器
            typedListeners.put(FlowableEngineEventType.TASK_CREATED.name(), 
                    List.of(assignmentCreationLifecycle.listener(true), taskAssignmentListener,
                            assignmentCreationLifecycle.listener(false), recordNoteLifecycleListener));
            typedListeners.put(FlowableEngineEventType.PROCESS_STARTED.name(),List.of(assignmentMutationLifecycle));
            
            // 流程完成事件监听器
            typedListeners.put(FlowableEngineEventType.PROCESS_COMPLETED.name(), 
                    List.of(assignmentCallRowOutputLifecycle, assignmentMutationLifecycle, processCompletionListener));
            
            for (FlowableEngineEventType type : List.of(FlowableEngineEventType.TASK_ASSIGNED,
                    FlowableEngineEventType.TASK_COMPLETED, FlowableEngineEventType.VARIABLE_UPDATED,
                    FlowableEngineEventType.MULTI_INSTANCE_ACTIVITY_CANCELLED, FlowableEngineEventType.MULTI_INSTANCE_ACTIVITY_STARTED,
                    FlowableEngineEventType.ACTIVITY_CANCELLED, FlowableEngineEventType.PROCESS_CANCELLED)) {
                boolean mutation = type == FlowableEngineEventType.TASK_ASSIGNED || type == FlowableEngineEventType.TASK_COMPLETED
                        || type == FlowableEngineEventType.PROCESS_CANCELLED;
                typedListeners.put(type.name(), mutation ? List.of(assignmentMutationLifecycle, recordNoteLifecycleListener)
                        : List.of(recordNoteLifecycleListener));
            }
            processEngineConfiguration.setTypedEventListeners(typedListeners);
        };
    }
}
