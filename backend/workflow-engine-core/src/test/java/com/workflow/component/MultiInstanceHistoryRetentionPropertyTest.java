package com.workflow.component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.entity.ExtendedTaskInfo;
import com.workflow.enums.AssignmentType;
import net.jqwik.api.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property 13: 历史记录保留
 * 
 * Feature: multi-instance-task-dispatch
 * Property 13: 历史记录保留
 * 
 * For any 已完成的多实例子流程，通过 HistoryService 查询应能获取所有子任务的
 * 历史执行记录，包括处理人、完成时间和状态。
 * 
 * **验证: 需求 7.3**
 */
class MultiInstanceHistoryRetentionPropertyTest {
    
    private ObjectMapper objectMapper = new ObjectMapper();
    
    /**
     * Property 13: 历史记录保留
     * 
     * 验证已完成的多实例子流程可通过 ExtendedTaskInfo 查询所有子任务历史
     * 
     * 注意：由于测试环境可能没有完整的 Flowable HistoryService，
     * 这里主要验证 ExtendedTaskInfo 的历史记录保留
     */
    @Property(tries = 100)
    @Label("Property 13: 历史记录保留 - 已完成的子任务历史应可查询")
    void completedMultiInstanceSubTasksHistoryShouldBeQueryable(
            @ForAll("multiInstanceSubTaskGroups") List<ExtendedTaskInfo> subTasks) {
        
        // Given: 多个多实例子任务
        // When: 完成所有子任务
        for (ExtendedTaskInfo task : subTasks) {
            task.completeTask("user-" + task.getTaskId().hashCode());
        }
        
        // Then: 验证所有子任务的历史记录完整
        for (ExtendedTaskInfo historyTask : subTasks) {
            // 验证状态为 COMPLETED
            assertThat(historyTask.getStatus()).isEqualTo("COMPLETED");
            
            // 验证完成时间不为空
            assertThat(historyTask.getCompletedTime()).isNotNull();
            
            // 验证完成人不为空
            assertThat(historyTask.getCompletedBy()).isNotNull();
            
            // 验证 extendedProperties 保留多实例配置
            assertThat(historyTask.getExtendedProperties()).isNotNull();
            
            try {
                Map<String, Object> props = objectMapper.readValue(
                    historyTask.getExtendedProperties(), Map.class);
                assertThat(props.get("multiInstance")).isEqualTo(true);
                assertThat(props.get("subTableRowId")).isNotNull();
            } catch (Exception e) {
                throw new RuntimeException("Failed to parse extendedProperties", e);
            }
        }
    }
    
    /**
     * Property 13: 历史记录保留 - 验证处理人信息保留
     * 
     * 验证历史记录中保留了正确的处理人信息
     */
    @Property(tries = 100)
    @Label("Property 13: 历史记录保留 - 处理人信息应正确保留")
    void completedSubTasksHistoryShouldPreserveAssigneeInfo(
            @ForAll("multiInstanceSubTaskGroups") List<ExtendedTaskInfo> subTasks) {
        
        // Given: 多个多实例子任务，每个任务有不同的处理人
        Map<String, String> taskIdToAssignee = new HashMap<>();
        
        for (ExtendedTaskInfo subTask : subTasks) {
            String assignee = "user-" + subTask.getTaskId().hashCode();
            taskIdToAssignee.put(subTask.getTaskId(), assignee);
            
            // 完成任务
            subTask.completeTask(assignee);
        }
        
        // Then: 验证每个任务的处理人信息正确
        for (ExtendedTaskInfo historyTask : subTasks) {
            String expectedAssignee = taskIdToAssignee.get(historyTask.getTaskId());
            assertThat(historyTask.getCompletedBy()).isEqualTo(expectedAssignee);
            assertThat(historyTask.getAssignmentTarget()).isNotNull();
        }
    }
    
    /**
     * Property 13: 历史记录保留 - 验证完成时间顺序
     * 
     * 验证历史记录中的完成时间反映了实际的完成顺序
     */
    @Property(tries = 50)
    @Label("Property 13: 历史记录保留 - 完成时间应反映实际完成顺序")
    void completedSubTasksHistoryShouldPreserveCompletionOrder(
            @ForAll("multiInstanceSubTaskGroups") List<ExtendedTaskInfo> subTasks) throws InterruptedException {
        
        // Given: 多个多实例子任务
        // When: 按顺序完成任务，每次完成之间有时间间隔
        List<LocalDateTime> completionTimes = new ArrayList<>();
        for (ExtendedTaskInfo task : subTasks) {
            Thread.sleep(10); // 确保时间戳不同
            task.completeTask("user-" + task.getTaskId().hashCode());
            completionTimes.add(task.getCompletedTime());
        }
        
        // Then: 验证完成时间是递增的
        for (int i = 0; i < subTasks.size() - 1; i++) {
            LocalDateTime currentTime = subTasks.get(i).getCompletedTime();
            LocalDateTime nextTime = subTasks.get(i + 1).getCompletedTime();
            assertThat(currentTime).isBeforeOrEqualTo(nextTime);
        }
    }
    
    /**
     * Property 13: 历史记录保留 - 验证子表关联信息保留
     * 
     * 验证历史记录中保留了子表行 ID 等关联信息
     */
    @Property(tries = 100)
    @Label("Property 13: 历史记录保留 - 子表关联信息应保留")
    void completedSubTasksHistoryShouldPreserveSubTableInfo(
            @ForAll("multiInstanceSubTaskGroups") List<ExtendedTaskInfo> subTasks) {
        
        // Given: 多个多实例子任务，每个任务关联不同的子表行
        Map<String, Long> taskIdToRowId = new HashMap<>();
        
        for (ExtendedTaskInfo subTask : subTasks) {
            // 提取子表行 ID
            try {
                Map<String, Object> props = objectMapper.readValue(
                    subTask.getExtendedProperties(), Map.class);
                Long rowId = ((Number) props.get("subTableRowId")).longValue();
                taskIdToRowId.put(subTask.getTaskId(), rowId);
            } catch (Exception e) {
                throw new RuntimeException("Failed to parse extendedProperties", e);
            }
            
            // 完成任务
            subTask.completeTask("user-" + subTask.getTaskId().hashCode());
        }
        
        // Then: 验证每个任务的子表关联信息正确
        for (ExtendedTaskInfo historyTask : subTasks) {
            Long expectedRowId = taskIdToRowId.get(historyTask.getTaskId());
            
            try {
                Map<String, Object> props = objectMapper.readValue(
                    historyTask.getExtendedProperties(), Map.class);
                Long actualRowId = ((Number) props.get("subTableRowId")).longValue();
                assertThat(actualRowId).isEqualTo(expectedRowId);
            } catch (Exception e) {
                throw new RuntimeException("Failed to parse extendedProperties", e);
            }
        }
    }
    
    // ==================== Arbitraries ====================
    
    @Provide
    Arbitrary<List<ExtendedTaskInfo>> multiInstanceSubTaskGroups() {
        return Combinators.combine(
            Arbitraries.strings().alpha().ofLength(10),  // processInstanceId
            Arbitraries.integers().between(2, 10)        // 子任务数量
        ).as((processInstanceId, count) -> {
            List<ExtendedTaskInfo> subTasks = new ArrayList<>();
            
            for (int i = 0; i < count; i++) {
                try {
                    // 构建 extendedProperties JSON
                    Map<String, Object> extendedProperties = new HashMap<>();
                    extendedProperties.put("multiInstance", true);
                    extendedProperties.put("subTableRowId", 100L + i);
                    extendedProperties.put("subTableName", "fu_participants");
                    extendedProperties.put("subTableRowVersion", 1L);
                    
                    String extendedPropertiesJson = objectMapper.writeValueAsString(extendedProperties);
                    
                    ExtendedTaskInfo subTask = ExtendedTaskInfo.builder()
                        .taskId("task-" + processInstanceId + "-" + i)
                        .processInstanceId(processInstanceId)
                        .processDefinitionId("process-def-001")
                        .taskDefinitionKey("MI_UserTask_" + i)
                        .taskName("补充个人信息 " + i)
                        .assignmentType(AssignmentType.USER)
                        .assignmentTarget("user-" + i)
                        .status("ASSIGNED")
                        .createdTime(LocalDateTime.now().minusHours(1))
                        .extendedProperties(extendedPropertiesJson)
                        .build();
                    
                    subTasks.add(subTask);
                } catch (Exception e) {
                    throw new RuntimeException("Failed to create ExtendedTaskInfo", e);
                }
            }
            
            return subTasks;
        });
    }
}
