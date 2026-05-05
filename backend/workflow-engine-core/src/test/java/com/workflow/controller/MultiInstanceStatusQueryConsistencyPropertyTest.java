package com.workflow.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.dto.response.MultiInstanceStatusResponse;
import com.workflow.entity.ExtendedTaskInfo;
import com.workflow.enums.AssignmentType;
import com.workflow.repository.ExtendedTaskInfoRepository;
import net.jqwik.api.*;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.runtime.Execution;
import org.flowable.engine.runtime.ExecutionQuery;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * MultiInstanceStatusController 状态查询一致性属性测试
 * 
 * 使用 jqwik 进行基于属性的测试，验证状态查询的一致性属性
 * 
 * Feature: multi-instance-task-dispatch
 * 
 * @author Workflow Engine
 * @version 1.0
 */
class MultiInstanceStatusQueryConsistencyPropertyTest {
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    /**
     * Property 9: 状态查询一致性
     * 
     * For any 正在执行的多实例子流程，状态查询返回的 totalInstances、completedInstances、activeInstances 之和应满足
     * `completedInstances + activeInstances + cancelledInstances == totalInstances`，
     * 且各子任务的状态与 ExtendedTaskInfo 中的记录一致。
     * 
     * **Validates: Requirements 5.4**
     */
    @Property(tries = 100)
    @Label("Property 9: 状态查询一致性 - completedInstances + activeInstances + cancelledInstances == totalInstances")
    void property9_statusQueryConsistency(
        @ForAll("multiInstanceScenarios") MultiInstanceScenario scenario
    ) {
        // Setup mocks
        RuntimeService runtimeService = mock(RuntimeService.class);
        ExtendedTaskInfoRepository extendedTaskInfoRepository = mock(ExtendedTaskInfoRepository.class);
        ExecutionQuery executionQuery = mock(ExecutionQuery.class);
        
        MultiInstanceStatusController controller = new MultiInstanceStatusController(
            runtimeService,
            null, // taskService not needed for this test
            extendedTaskInfoRepository,
            objectMapper,
            null,  // jdbcTemplate not needed for this test
            mock(com.workflow.component.BpmnActionParser.class)
        );
        
        // Given: 准备多实例执行场景
        String processInstanceId = scenario.processInstanceId;
        
        // 模拟 Flowable 执行查询
        when(runtimeService.createExecutionQuery()).thenReturn(executionQuery);
        when(executionQuery.processInstanceId(processInstanceId)).thenReturn(executionQuery);
        when(executionQuery.list()).thenReturn(scenario.executions);
        
        // 模拟多实例父执行的变量
        String multiInstanceExecutionId = scenario.multiInstanceExecution.getId();
        Map<String, Object> miVariables = new HashMap<>();
        miVariables.put("nrOfInstances", scenario.totalInstances);
        miVariables.put("nrOfCompletedInstances", scenario.completedInstances);
        miVariables.put("nrOfActiveInstances", scenario.activeInstances);
        
        when(runtimeService.getVariables(multiInstanceExecutionId)).thenReturn(miVariables);
        
        // 模拟 ExtendedTaskInfo 查询
        when(extendedTaskInfoRepository.findByProcessInstanceIdAndIsDeletedFalse(processInstanceId))
            .thenReturn(scenario.tasks);
        
        // When: 查询多实例执行状态
        var responseEntity = controller.getStatus(processInstanceId);
        
        // Then: 验证响应成功
        assertThat(responseEntity.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(responseEntity.getBody()).isNotNull();
        assertThat(responseEntity.getBody().isSuccess()).isTrue();
        
        MultiInstanceStatusResponse response = responseEntity.getBody().getData();
        assertThat(response).isNotNull();
        
        // 核心属性验证：completedInstances + activeInstances + cancelledInstances == totalInstances
        int sumOfInstances = response.getCompletedInstances() + 
                            response.getActiveInstances() + 
                            response.getCancelledInstances();
        
        assertThat(sumOfInstances)
            .as("completedInstances(%d) + activeInstances(%d) + cancelledInstances(%d) 应该等于 totalInstances(%d)",
                response.getCompletedInstances(),
                response.getActiveInstances(),
                response.getCancelledInstances(),
                response.getTotalInstances())
            .isEqualTo(response.getTotalInstances());
        
        // 验证返回的总实例数与场景一致
        assertThat(response.getTotalInstances())
            .as("返回的总实例数应该与场景中的总实例数一致")
            .isEqualTo(scenario.totalInstances);
        
        // 验证返回的已完成实例数与场景一致
        assertThat(response.getCompletedInstances())
            .as("返回的已完成实例数应该与场景中的已完成实例数一致")
            .isEqualTo(scenario.completedInstances);
        
        // 验证返回的进行中实例数与场景一致
        assertThat(response.getActiveInstances())
            .as("返回的进行中实例数应该与场景中的进行中实例数一致")
            .isEqualTo(scenario.activeInstances);
        
        // 验证返回的已取消实例数与实际的多实例子任务中 CANCELLED 状态的数量一致
        long expectedCancelledCount = scenario.tasks.stream()
            .filter(this::isMultiInstanceTask)
            .filter(task -> "CANCELLED".equals(task.getStatus()))
            .count();
        
        assertThat(response.getCancelledInstances())
            .as("返回的已取消实例数应该与实际的 CANCELLED 状态任务数量一致")
            .isEqualTo((int) expectedCancelledCount);
        
        // 验证子任务详情列表
        assertThat(response.getTasks())
            .as("子任务详情列表不应为空")
            .isNotNull();
        
        // 验证子任务详情数量等于多实例子任务数量
        long multiInstanceTaskCount = scenario.tasks.stream()
            .filter(this::isMultiInstanceTask)
            .count();
        
        assertThat(response.getTasks())
            .as("子任务详情数量应该等于多实例子任务数量")
            .hasSize((int) multiInstanceTaskCount);
        
        // 验证每个子任务的状态与 ExtendedTaskInfo 中的记录一致
        Map<String, ExtendedTaskInfo> taskMap = scenario.tasks.stream()
            .filter(this::isMultiInstanceTask)
            .collect(Collectors.toMap(ExtendedTaskInfo::getTaskId, task -> task));
        
        for (MultiInstanceStatusResponse.SubTaskDetail detail : response.getTasks()) {
            ExtendedTaskInfo taskInfo = taskMap.get(detail.getTaskId());
            
            assertThat(taskInfo)
                .as("子任务详情中的任务ID应该在 ExtendedTaskInfo 中存在")
                .isNotNull();
            
            assertThat(detail.getStatus())
                .as("子任务详情中的状态应该与 ExtendedTaskInfo 中的状态一致")
                .isEqualTo(taskInfo.getStatus());
            
            assertThat(detail.getAssignee())
                .as("子任务详情中的处理人应该与 ExtendedTaskInfo 中的处理人一致")
                .isEqualTo(taskInfo.getAssignmentTarget());
            
            assertThat(detail.getTaskName())
                .as("子任务详情中的任务名称应该与 ExtendedTaskInfo 中的任务名称一致")
                .isEqualTo(taskInfo.getTaskName());
            
            // 验证 subTableRowId
            Map<String, Object> extProps = parseExtendedProperties(taskInfo.getExtendedProperties());
            Long expectedRowId = extProps.containsKey("subTableRowId") 
                ? ((Number) extProps.get("subTableRowId")).longValue() 
                : null;
            
            assertThat(detail.getSubTableRowId())
                .as("子任务详情中的 subTableRowId 应该与 ExtendedTaskInfo 中的 subTableRowId 一致")
                .isEqualTo(expectedRowId);
        }
        
        // 验证状态统计的一致性：按状态分组统计
        Map<String, Long> statusCounts = response.getTasks().stream()
            .collect(Collectors.groupingBy(
                MultiInstanceStatusResponse.SubTaskDetail::getStatus,
                Collectors.counting()
            ));
        
        long completedCount = statusCounts.getOrDefault("COMPLETED", 0L);
        long cancelledCount = statusCounts.getOrDefault("CANCELLED", 0L);
        long activeCount = statusCounts.getOrDefault("ASSIGNED", 0L) + 
                          statusCounts.getOrDefault("CREATED", 0L);
        
        assertThat(completedCount)
            .as("子任务详情中 COMPLETED 状态的数量应该等于 completedInstances")
            .isEqualTo(response.getCompletedInstances().longValue());
        
        assertThat(cancelledCount)
            .as("子任务详情中 CANCELLED 状态的数量应该等于 cancelledInstances")
            .isEqualTo(response.getCancelledInstances().longValue());
        
        // activeInstances 可能包含 ASSIGNED 和 CREATED 状态
        // 注意：这里的验证取决于实际实现，可能需要调整
    }
    
    /**
     * Property: 空多实例场景处理
     * 
     * For any 流程实例，如果没有多实例执行，应该返回错误信息。
     */
    @Property(tries = 50)
    @Label("空多实例场景应该返回错误")
    void shouldReturnErrorWhenNoMultiInstanceExecution(
        @ForAll("processInstanceIds") String processInstanceId
    ) {
        // Setup mocks
        RuntimeService runtimeService = mock(RuntimeService.class);
        ExtendedTaskInfoRepository extendedTaskInfoRepository = mock(ExtendedTaskInfoRepository.class);
        ExecutionQuery executionQuery = mock(ExecutionQuery.class);
        
        MultiInstanceStatusController controller = new MultiInstanceStatusController(
            runtimeService,
            null,
            extendedTaskInfoRepository,
            objectMapper,
            null,
            mock(com.workflow.component.BpmnActionParser.class)
        );
        
        // Given: 没有多实例执行
        when(runtimeService.createExecutionQuery()).thenReturn(executionQuery);
        when(executionQuery.processInstanceId(processInstanceId)).thenReturn(executionQuery);
        when(executionQuery.list()).thenReturn(Collections.emptyList());
        
        // When: 查询多实例执行状态
        var responseEntity = controller.getStatus(processInstanceId);
        
        // Then: 应该返回错误
        assertThat(responseEntity.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(responseEntity.getBody()).isNotNull();
        assertThat(responseEntity.getBody().isSuccess()).isFalse();
        assertThat(responseEntity.getBody().getCode()).isEqualTo("MULTI_INSTANCE_NOT_FOUND");
    }
    
    /**
     * Property: 非负数约束
     * 
     * For any 多实例执行状态，totalInstances、completedInstances、activeInstances、cancelledInstances
     * 都应该是非负数。
     */
    @Property(tries = 100)
    @Label("所有实例计数应该是非负数")
    void allInstanceCountsShouldBeNonNegative(
        @ForAll("multiInstanceScenarios") MultiInstanceScenario scenario
    ) {
        // Setup mocks
        RuntimeService runtimeService = mock(RuntimeService.class);
        ExtendedTaskInfoRepository extendedTaskInfoRepository = mock(ExtendedTaskInfoRepository.class);
        ExecutionQuery executionQuery = mock(ExecutionQuery.class);
        
        MultiInstanceStatusController controller = new MultiInstanceStatusController(
            runtimeService,
            null,
            extendedTaskInfoRepository,
            objectMapper,
            null,
            mock(com.workflow.component.BpmnActionParser.class)
        );
        
        // Given: 准备多实例执行场景
        String processInstanceId = scenario.processInstanceId;
        
        when(runtimeService.createExecutionQuery()).thenReturn(executionQuery);
        when(executionQuery.processInstanceId(processInstanceId)).thenReturn(executionQuery);
        when(executionQuery.list()).thenReturn(scenario.executions);
        
        String multiInstanceExecutionId = scenario.multiInstanceExecution.getId();
        Map<String, Object> miVariables = new HashMap<>();
        miVariables.put("nrOfInstances", scenario.totalInstances);
        miVariables.put("nrOfCompletedInstances", scenario.completedInstances);
        miVariables.put("nrOfActiveInstances", scenario.activeInstances);
        
        when(runtimeService.getVariables(multiInstanceExecutionId)).thenReturn(miVariables);
        when(extendedTaskInfoRepository.findByProcessInstanceIdAndIsDeletedFalse(processInstanceId))
            .thenReturn(scenario.tasks);
        
        // When: 查询多实例执行状态
        var responseEntity = controller.getStatus(processInstanceId);
        
        // Then: 验证所有计数都是非负数
        assertThat(responseEntity.getBody().isSuccess()).isTrue();
        MultiInstanceStatusResponse response = responseEntity.getBody().getData();
        
        assertThat(response.getTotalInstances())
            .as("totalInstances 应该是非负数")
            .isGreaterThanOrEqualTo(0);
        
        assertThat(response.getCompletedInstances())
            .as("completedInstances 应该是非负数")
            .isGreaterThanOrEqualTo(0);
        
        assertThat(response.getActiveInstances())
            .as("activeInstances 应该是非负数")
            .isGreaterThanOrEqualTo(0);
        
        assertThat(response.getCancelledInstances())
            .as("cancelledInstances 应该是非负数")
            .isGreaterThanOrEqualTo(0);
    }
    
    // ==================== 辅助方法和数据生成器 ====================
    
    /**
     * 多实例场景数据结构
     */
    private static class MultiInstanceScenario {
        final String processInstanceId;
        final List<Execution> executions;
        final Execution multiInstanceExecution;
        final int totalInstances;
        final int completedInstances;
        final int activeInstances;
        final int cancelledInstances;
        final List<ExtendedTaskInfo> tasks;
        
        MultiInstanceScenario(
            String processInstanceId,
            List<Execution> executions,
            Execution multiInstanceExecution,
            int totalInstances,
            int completedInstances,
            int activeInstances,
            int cancelledInstances,
            List<ExtendedTaskInfo> tasks
        ) {
            this.processInstanceId = processInstanceId;
            this.executions = executions;
            this.multiInstanceExecution = multiInstanceExecution;
            this.totalInstances = totalInstances;
            this.completedInstances = completedInstances;
            this.activeInstances = activeInstances;
            this.cancelledInstances = cancelledInstances;
            this.tasks = tasks;
        }
    }
    
    @Provide
    Arbitrary<MultiInstanceScenario> multiInstanceScenarios() {
        return Combinators.combine(
            processInstanceIds(),
            Arbitraries.integers().between(1, 20),  // totalInstances (至少1个)
            Arbitraries.integers().between(0, 100)  // 随机种子用于分配状态
        ).as((processInstanceId, totalInstances, seed) -> {
            Random random = new Random(seed);
            
            // 随机分配已完成、进行中、已取消的实例数
            // 确保 completedInstances + activeInstances + cancelledInstances == totalInstances
            int completedInstances = random.nextInt(totalInstances + 1);
            int remainingInstances = totalInstances - completedInstances;
            int cancelledInstances = remainingInstances > 0 ? random.nextInt(remainingInstances + 1) : 0;
            int activeInstances = totalInstances - completedInstances - cancelledInstances;
            
            // 创建多实例父执行
            Execution multiInstanceExecution = mock(Execution.class);
            when(multiInstanceExecution.getId()).thenReturn("mi-exec-" + processInstanceId);
            when(multiInstanceExecution.getActivityId()).thenReturn("MultiInstance_SubTable_45");
            
            List<Execution> executions = new ArrayList<>();
            executions.add(multiInstanceExecution);
            
            // 创建子任务
            List<ExtendedTaskInfo> tasks = new ArrayList<>();
            
            // 创建已完成的子任务
            for (int i = 0; i < completedInstances; i++) {
                tasks.add(createMultiInstanceTask(
                    processInstanceId,
                    "task-completed-" + i,
                    "user-" + i,
                    100L + i,
                    "COMPLETED",
                    LocalDateTime.now().minusHours(i + 1),
                    LocalDateTime.now().minusMinutes(i * 10)
                ));
            }
            
            // 创建进行中的子任务
            for (int i = 0; i < activeInstances; i++) {
                tasks.add(createMultiInstanceTask(
                    processInstanceId,
                    "task-active-" + i,
                    "user-" + (completedInstances + i),
                    200L + i,
                    "ASSIGNED",
                    LocalDateTime.now().minusHours(i + 1),
                    null
                ));
            }
            
            // 创建已取消的子任务
            for (int i = 0; i < cancelledInstances; i++) {
                tasks.add(createMultiInstanceTask(
                    processInstanceId,
                    "task-cancelled-" + i,
                    "user-" + (completedInstances + activeInstances + i),
                    300L + i,
                    "CANCELLED",
                    LocalDateTime.now().minusHours(i + 1),
                    null
                ));
            }
            
            return new MultiInstanceScenario(
                processInstanceId,
                executions,
                multiInstanceExecution,
                totalInstances,
                completedInstances,
                activeInstances,
                cancelledInstances,
                tasks
            );
        });
    }
    
    @Provide
    Arbitrary<String> processInstanceIds() {
        return Arbitraries.strings()
            .withCharRange('a', 'z')
            .numeric()
            .ofMinLength(5)
            .ofMaxLength(20)
            .map(s -> "proc-" + s);
    }
    
    private static ExtendedTaskInfo createMultiInstanceTask(
        String processInstanceId,
        String taskId,
        String assigneeId,
        Long rowId,
        String status,
        LocalDateTime createdTime,
        LocalDateTime completedTime
    ) {
        String extendedProperties = String.format(
            "{\"multiInstance\":true,\"subTableRowId\":%d,\"subTableRowVersion\":1," +
            "\"subTableId\":\"45\",\"subTableName\":\"fu_participants\"}",
            rowId
        );
        
        return ExtendedTaskInfo.builder()
            .id((long) taskId.hashCode())
            .taskId(taskId)
            .processInstanceId(processInstanceId)
            .processDefinitionId("proc-def-001")
            .taskDefinitionKey("fillInfo")
            .taskName("填写参会信息")
            .assignmentType(AssignmentType.USER)
            .assignmentTarget(assigneeId)
            .status(status)
            .createdTime(createdTime)
            .completedTime(completedTime)
            .completedBy("COMPLETED".equals(status) ? assigneeId : null)
            .extendedProperties(extendedProperties)
            .isDeleted(false)
            .build();
    }
    
    private boolean isMultiInstanceTask(ExtendedTaskInfo task) {
        String extendedProperties = task.getExtendedProperties();
        if (extendedProperties == null || extendedProperties.trim().isEmpty()) {
            return false;
        }
        
        try {
            Map<String, Object> properties = objectMapper.readValue(
                extendedProperties,
                new TypeReference<Map<String, Object>>() {}
            );
            
            Object multiInstance = properties.get("multiInstance");
            return multiInstance != null && Boolean.TRUE.equals(multiInstance);
        } catch (Exception e) {
            return false;
        }
    }
    
    private Map<String, Object> parseExtendedProperties(String extendedProperties) {
        if (extendedProperties == null || extendedProperties.trim().isEmpty()) {
            return new HashMap<>();
        }
        
        try {
            return objectMapper.readValue(extendedProperties, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return new HashMap<>();
        }
    }
}
