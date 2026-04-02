package com.workflow.component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.entity.ExtendedTaskInfo;
import com.workflow.enums.AssignmentType;
import net.jqwik.api.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property 7: 子任务完成状态更新
 * 
 * Feature: multi-instance-task-dispatch
 * Property 7: 子任务完成状态更新
 * 
 * For any 被完成的多实例子任务，对应的 ExtendedTaskInfo 记录状态应更新为 COMPLETED，
 * 且 completed_time 和 completed_by 字段被正确设置。
 * 
 * **验证: 需求 5.1**
 */
class TaskManagerComponentSubTaskCompletionPropertyTest {
    
    private ObjectMapper objectMapper = new ObjectMapper();
    
    /**
     * Property 7: 子任务完成状态更新
     * 
     * 验证完成的子任务 ExtendedTaskInfo 状态为 COMPLETED，
     * completed_time 和 completed_by 正确
     */
    @Property(tries = 100)
    @Label("Property 7: 子任务完成状态更新 - 完成的子任务状态应为 COMPLETED")
    void completedSubTaskShouldHaveCorrectStatus(
            @ForAll("multiInstanceSubTasks") ExtendedTaskInfo subTask,
            @ForAll("userIds") String completedBy) {
        
        // Given: 多实例子任务
        LocalDateTime beforeCompletion = LocalDateTime.now();
        
        // When: 完成任务
        subTask.completeTask(completedBy);
        LocalDateTime afterCompletion = LocalDateTime.now();
        
        // Then: 验证状态更新
        assertThat(subTask.getStatus()).isEqualTo("COMPLETED");
        assertThat(subTask.getCompletedBy()).isEqualTo(completedBy);
        assertThat(subTask.getCompletedTime())
            .isNotNull()
            .isAfterOrEqualTo(beforeCompletion)
            .isBeforeOrEqualTo(afterCompletion);
    }
    
    /**
     * Property 7: 子任务完成状态更新 - 验证 extendedProperties 保留
     * 
     * 验证完成任务后，extendedProperties 中的多实例配置信息仍然保留
     */
    @Property(tries = 100)
    @Label("Property 7: 子任务完成状态更新 - extendedProperties 应保留多实例配置")
    void completedSubTaskShouldPreserveExtendedProperties(
            @ForAll("multiInstanceSubTasks") ExtendedTaskInfo subTask,
            @ForAll("userIds") String completedBy) throws Exception {
        
        // Given: 多实例子任务
        // 提取原始的 extendedProperties
        String originalExtendedProperties = subTask.getExtendedProperties();
        Map<String, Object> originalProps = objectMapper.readValue(
            originalExtendedProperties, Map.class);
        
        // When: 完成任务
        subTask.completeTask(completedBy);
        
        // Then: 验证 extendedProperties 保留
        assertThat(subTask.getExtendedProperties()).isNotNull();
        
        Map<String, Object> updatedProps = objectMapper.readValue(
            subTask.getExtendedProperties(), Map.class);
        
        // 验证多实例标记仍然存在
        assertThat(updatedProps.get("multiInstance")).isEqualTo(true);
        assertThat(updatedProps.get("subTableRowId"))
            .isEqualTo(originalProps.get("subTableRowId"));
        assertThat(updatedProps.get("subTableName"))
            .isEqualTo(originalProps.get("subTableName"));
    }
    
    // ==================== Arbitraries ====================
    
    @Provide
    Arbitrary<ExtendedTaskInfo> multiInstanceSubTasks() {
        return Combinators.combine(
            Arbitraries.strings().alpha().ofLength(10),  // taskId
            Arbitraries.strings().alpha().ofLength(10),  // processInstanceId
            Arbitraries.longs().between(1L, 1000L),      // subTableRowId
            Arbitraries.strings().alpha().ofLength(10)   // subTableName
        ).as((taskId, processInstanceId, subTableRowId, subTableName) -> {
            try {
                // 构建 extendedProperties JSON
                Map<String, Object> extendedProperties = new HashMap<>();
                extendedProperties.put("multiInstance", true);
                extendedProperties.put("subTableRowId", subTableRowId);
                extendedProperties.put("subTableName", subTableName);
                extendedProperties.put("subTableRowVersion", 1L);
                
                String extendedPropertiesJson = objectMapper.writeValueAsString(extendedProperties);
                
                return ExtendedTaskInfo.builder()
                    .taskId(taskId)
                    .processInstanceId(processInstanceId)
                    .processDefinitionId("process-def-001")
                    .taskDefinitionKey("MI_UserTask_" + subTableRowId)
                    .taskName("补充个人信息")
                    .assignmentType(AssignmentType.USER)
                    .assignmentTarget("user-" + (subTableRowId % 10))
                    .status("ASSIGNED")
                    .createdTime(LocalDateTime.now().minusHours(1))
                    .extendedProperties(extendedPropertiesJson)
                    .build();
            } catch (Exception e) {
                throw new RuntimeException("Failed to create ExtendedTaskInfo", e);
            }
        });
    }
    
    @Provide
    Arbitrary<String> userIds() {
        return Arbitraries.integers().between(1, 100)
            .map(i -> "user-" + String.format("%03d", i));
    }
}
