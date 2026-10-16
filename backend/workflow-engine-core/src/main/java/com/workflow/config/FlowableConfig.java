package com.workflow.config;

import com.workflow.listener.ProcessCompletionListener;
import com.workflow.listener.TaskAssignmentListener;
import org.flowable.common.engine.api.delegate.event.FlowableEngineEventType;
import org.flowable.spring.SpringProcessEngineConfiguration;
import org.flowable.spring.boot.EngineConfigurationConfigurer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Collections;
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

    @Bean
    public EngineConfigurationConfigurer<SpringProcessEngineConfiguration> customProcessEngineConfigurer() {
        return processEngineConfiguration -> {
            // 注册事件监听器
            Map<String, List<org.flowable.common.engine.api.delegate.event.FlowableEventListener>> typedListeners = 
                    new HashMap<>();
            
            // 任务创建事件监听器
            typedListeners.put(FlowableEngineEventType.TASK_CREATED.name(), 
                    List.of(taskAssignmentListener, recordNoteLifecycleListener));
            
            // 流程完成事件监听器
            typedListeners.put(FlowableEngineEventType.PROCESS_COMPLETED.name(), 
                    Collections.singletonList(processCompletionListener));
            
            for (FlowableEngineEventType type : List.of(FlowableEngineEventType.TASK_ASSIGNED,
                    FlowableEngineEventType.TASK_COMPLETED, FlowableEngineEventType.VARIABLE_UPDATED,
                    FlowableEngineEventType.MULTI_INSTANCE_ACTIVITY_CANCELLED, FlowableEngineEventType.MULTI_INSTANCE_ACTIVITY_STARTED,
                    FlowableEngineEventType.ACTIVITY_CANCELLED, FlowableEngineEventType.PROCESS_CANCELLED)) {
                typedListeners.put(type.name(), List.of(recordNoteLifecycleListener));
            }
            processEngineConfiguration.setTypedEventListeners(typedListeners);
        };
    }
}
