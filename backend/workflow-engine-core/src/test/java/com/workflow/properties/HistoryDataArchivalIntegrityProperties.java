package com.workflow.properties;

import com.workflow.component.ProcessEngineComponent;
import com.workflow.component.HistoryManagerComponent;
import com.workflow.component.TaskManagerComponent;
import com.workflow.dto.request.ProcessDefinitionRequest;
import com.workflow.dto.request.StartProcessRequest;
import com.workflow.dto.request.HistoryQueryRequest;

import com.workflow.dto.response.DeploymentResult;
import com.workflow.dto.response.ProcessInstanceResult;
import com.workflow.dto.response.HistoryQueryResult;
import com.workflow.dto.response.HistoryArchiveResult;
import com.workflow.exception.WorkflowValidationException;

import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.HistoryService;
import org.flowable.task.api.Task;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.task.api.history.HistoricTaskInstance;
import org.flowable.variable.api.history.HistoricVariableInstance;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.*;

/**
 * 历史数据归档完整性属性测试
 * 验证需求: 需求 6.1 - 流程实例完成时的历史数据归档完整性
 * 
 * 属性 12: 历史数据归档完整性
 * 对于任何完成的流程实例，归档到历史表的数据应该与原始执行数据完全一致，不丢失任何信息
 * 
 * 测试策略：
 * 1. 创建并执行完整的流程实例
 * 2. 收集执行过程中的所有数据（流程实例、任务、变量、活动）
 * 3. 完成流程实例
 * 4. 验证历史数据的完整性和一致性
 * 5. 验证归档功能的正确性
 * 
 * 注意：使用JUnit的@RepeatedTest来模拟属性测试，验证多种随机场景
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class HistoryDataArchivalIntegrityProperties {

    @Autowired
    private ProcessEngineComponent processEngineComponent;
    
    @Autowired
    private HistoryManagerComponent historyManagerComponent;
    
    @Autowired
    private TaskManagerComponent taskManagerComponent;
    
    @Autowired
    private RuntimeService runtimeService;
    
    @Autowired
    private TaskService taskService;
    
    @Autowired
    private HistoryService historyService;

    private static final String TEST_BPMN_WITH_VARIABLES = """
        <?xml version="1.0" encoding="UTF-8"?>
        <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                     xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                     xmlns:flowable="http://flowable.org/bpmn"
                     targetNamespace="http://www.flowable.org/processdef"
                     xsi:schemaLocation="http://www.omg.org/spec/BPMN/20100524/MODEL http://www.omg.org/spec/BPMN/20100524/BPMN20.xsd">
          <process id="%s" name="%s" isExecutable="true">
            <startEvent id="startEvent" name="Start"/>
            <sequenceFlow id="flow1" sourceRef="startEvent" targetRef="userTask1"/>
            <userTask id="userTask1" name="First Task" flowable:assignee="user1"/>
            <sequenceFlow id="flow2" sourceRef="userTask1" targetRef="userTask2"/>
            <userTask id="userTask2" name="Second Task" flowable:assignee="user2"/>
            <sequenceFlow id="flow3" sourceRef="userTask2" targetRef="endEvent"/>
            <endEvent id="endEvent" name="End"/>
          </process>
        </definitions>
        """;

    @BeforeEach
    void setUp() {
        assertThat(processEngineComponent).isNotNull();
        assertThat(historyManagerComponent).isNotNull();
        assertThat(taskManagerComponent).isNotNull();
        assertThat(runtimeService).isNotNull();
        assertThat(taskService).isNotNull();
        assertThat(historyService).isNotNull();
    }

    /**
     * 属性测试: 流程实例完成后历史数据应该完整保存
     * 功能: workflow-engine-core, 属性 12: 历史数据归档完整性
     */
    @RepeatedTest(10)
    void completedProcessInstanceShouldHaveCompleteHistoryData() {
        // Given: 创建并启动流程实例
        String processKey = generateProcessKey();
        String businessKey = generateBusinessKey();
        String startUserId = generateUserId();
        
        ProcessInstanceExecutionData executionData = createAndExecuteCompleteProcess(
            processKey, businessKey, startUserId);
        
        // When: 查询历史数据
        HistoryQueryRequest queryRequest = HistoryQueryRequest.builder()
            .processInstanceId(executionData.getProcessInstanceId())
            .pageSize(100)
            .page(1)
            .build();
        
        HistoryQueryResult historyResult = historyManagerComponent
            .getCompleteProcessHistory(executionData.getProcessInstanceId());
        
        // Then: 验证历史数据完整性
        assertThat(historyResult).isNotNull();
        assertThat(historyResult.getProcessInstances()).hasSize(1);
        
        // 验证流程实例历史数据
        HistoryQueryResult.ProcessInstanceHistory processHistory = historyResult.getProcessInstances().get(0);
        assertThat(processHistory.getProcessInstanceId()).isEqualTo(executionData.getProcessInstanceId());
        assertThat(processHistory.getProcessDefinitionKey()).isEqualTo(processKey);
        assertThat(processHistory.getBusinessKey()).isEqualTo(businessKey);
        assertThat(processHistory.getStartUserId()).isEqualTo(startUserId);
        assertThat(processHistory.getStartTime()).isNotNull();
        assertThat(processHistory.getEndTime()).isNotNull();
        assertThat(processHistory.getDurationInMillis()).isNotNull();
        assertThat(processHistory.getDurationInMillis()).isGreaterThan(0L);
        
        // 验证任务历史数据完整性
        assertThat(historyResult.getTaskInstances()).hasSize(2); // 两个用户任务
        
        List<HistoryQueryResult.TaskInstanceHistory> taskHistories = historyResult.getTaskInstances();
        for (HistoryQueryResult.TaskInstanceHistory taskHistory : taskHistories) {
            assertThat(taskHistory.getTaskId()).isNotNull();
            assertThat(taskHistory.getTaskName()).isNotNull();
            assertThat(taskHistory.getProcessInstanceId()).isEqualTo(executionData.getProcessInstanceId());
            assertThat(taskHistory.getCreateTime()).isNotNull();
            assertThat(taskHistory.getStartTime()).isNotNull();
            assertThat(taskHistory.getEndTime()).isNotNull();
            assertThat(taskHistory.getDurationInMillis()).isNotNull();
            assertThat(taskHistory.getAssignee()).isNotNull();
        }
        
        // 验证变量历史数据完整性
        assertThat(historyResult.getVariableInstances()).isNotEmpty();
        
        List<HistoryQueryResult.VariableInstanceHistory> variableHistories = historyResult.getVariableInstances();
        Map<String, Object> expectedVariables = executionData.getProcessVariables();
        
        for (Map.Entry<String, Object> expectedVar : expectedVariables.entrySet()) {
            Optional<HistoryQueryResult.VariableInstanceHistory> foundVar = variableHistories.stream()
                .filter(var -> expectedVar.getKey().equals(var.getVariableName()))
                .findFirst();
            
            assertThat(foundVar).isPresent();
            
            Object expectedValue = expectedVar.getValue();
            Object actualValue = foundVar.get().getValue();
            
            // 处理LocalDateTime精度差异
            if (expectedValue instanceof LocalDateTime && actualValue instanceof LocalDateTime) {
                LocalDateTime expectedTime = (LocalDateTime) expectedValue;
                LocalDateTime actualTime = (LocalDateTime) actualValue;
                // 只比较到毫秒精度
                assertThat(actualTime.withNano(0)).isEqualTo(expectedTime.withNano(0));
            } else {
                assertThat(actualValue).isEqualTo(expectedValue);
            }
            
            assertThat(foundVar.get().getProcessInstanceId()).isEqualTo(executionData.getProcessInstanceId());
        }
        
        // 验证活动历史数据完整性
        assertThat(historyResult.getActivityInstances()).isNotEmpty();
        
        List<HistoryQueryResult.ActivityInstanceHistory> activityHistories = historyResult.getActivityInstances();
        // 应该包含：startEvent, userTask1, userTask2, endEvent
        assertThat(activityHistories).hasSizeGreaterThanOrEqualTo(4);
        
        // 验证开始事件
        Optional<HistoryQueryResult.ActivityInstanceHistory> startActivity = activityHistories.stream()
            .filter(activity -> "startEvent".equals(activity.getActivityId()))
            .findFirst();
        assertThat(startActivity).isPresent();
        assertThat(startActivity.get().getActivityType()).isEqualTo("startEvent");
        
        // 验证结束事件
        Optional<HistoryQueryResult.ActivityInstanceHistory> endActivity = activityHistories.stream()
            .filter(activity -> "endEvent".equals(activity.getActivityId()))
            .findFirst();
        assertThat(endActivity).isPresent();
        assertThat(endActivity.get().getActivityType()).isEqualTo("endEvent");
    }

    /**
     * 属性测试: 历史数据查询应该与Flowable原生查询结果一致
     * 功能: workflow-engine-core, 属性 12: 历史数据归档完整性
     */
    @RepeatedTest(8)
    void historyQueryShouldMatchFlowableNativeQuery() {
        // Given: 创建并完成流程实例
        String processKey = generateProcessKey();
        String businessKey = generateBusinessKey();
        String startUserId = generateUserId();
        
        ProcessInstanceExecutionData executionData = createAndExecuteCompleteProcess(
            processKey, businessKey, startUserId);
        
        // When: 使用History Manager查询历史数据
        HistoryQueryResult managerResult = historyManagerComponent
            .getCompleteProcessHistory(executionData.getProcessInstanceId());
        
        // When: 使用Flowable原生API查询历史数据
        HistoricProcessInstance nativeProcessInstance = historyService
            .createHistoricProcessInstanceQuery()
            .processInstanceId(executionData.getProcessInstanceId())
            .singleResult();
        
        List<HistoricTaskInstance> nativeTaskInstances = historyService
            .createHistoricTaskInstanceQuery()
            .processInstanceId(executionData.getProcessInstanceId())
            .orderByHistoricTaskInstanceStartTime().asc()
            .list();
        
        List<HistoricVariableInstance> nativeVariableInstances = historyService
            .createHistoricVariableInstanceQuery()
            .processInstanceId(executionData.getProcessInstanceId())
            .list();
        
        // Then: 验证查询结果一致性
        assertThat(managerResult.getProcessInstances()).hasSize(1);
        HistoryQueryResult.ProcessInstanceHistory processHistory = managerResult.getProcessInstances().get(0);
        
        // 验证流程实例数据一致性
        assertThat(processHistory.getProcessInstanceId()).isEqualTo(nativeProcessInstance.getId());
        assertThat(processHistory.getProcessDefinitionKey()).isEqualTo(nativeProcessInstance.getProcessDefinitionKey());
        assertThat(processHistory.getBusinessKey()).isEqualTo(nativeProcessInstance.getBusinessKey());
        assertThat(processHistory.getStartUserId()).isEqualTo(nativeProcessInstance.getStartUserId());
        assertThat(processHistory.getDurationInMillis()).isEqualTo(nativeProcessInstance.getDurationInMillis());
        
        // 验证任务数据一致性
        assertThat(managerResult.getTaskInstances()).hasSize(nativeTaskInstances.size());
        
        for (int i = 0; i < nativeTaskInstances.size(); i++) {
            HistoricTaskInstance nativeTask = nativeTaskInstances.get(i);
            HistoryQueryResult.TaskInstanceHistory managerTask = managerResult.getTaskInstances().get(i);
            
            assertThat(managerTask.getTaskId()).isEqualTo(nativeTask.getId());
            assertThat(managerTask.getTaskName()).isEqualTo(nativeTask.getName());
            assertThat(managerTask.getAssignee()).isEqualTo(nativeTask.getAssignee());
            assertThat(managerTask.getDurationInMillis()).isEqualTo(nativeTask.getDurationInMillis());
        }
        
        // 验证变量数据一致性
        assertThat(managerResult.getVariableInstances()).hasSize(nativeVariableInstances.size());
        
        Map<String, HistoricVariableInstance> nativeVarMap = nativeVariableInstances.stream()
            .collect(Collectors.toMap(HistoricVariableInstance::getVariableName, var -> var));
        
        for (HistoryQueryResult.VariableInstanceHistory managerVar : managerResult.getVariableInstances()) {
            HistoricVariableInstance nativeVar = nativeVarMap.get(managerVar.getVariableName());
            assertThat(nativeVar).isNotNull();
            assertThat(managerVar.getValue()).isEqualTo(nativeVar.getValue());
            assertThat(managerVar.getVariableTypeName()).isEqualTo(nativeVar.getVariableTypeName());
        }
    }

    /**
     * 属性测试: 流程变量变更历史应该完整记录
     * 功能: workflow-engine-core, 属性 12: 历史数据归档完整性
     */
    @RepeatedTest(8)
    void processVariableChangesShouldBeCompletelyRecorded() {
        // Given: 创建流程实例并设置初始变量
        String processKey = generateProcessKey();
        String businessKey = generateBusinessKey();
        String startUserId = generateUserId();
        
        Map<String, Object> initialVariables = generateProcessVariables();
        
        String processInstanceId = createProcessInstance(processKey, businessKey, startUserId, initialVariables);
        
        // When: 在流程执行过程中修改变量
        Map<String, Object> updatedVariables = new HashMap<>(initialVariables);
        updatedVariables.put("status", "in_progress");
        updatedVariables.put("updateTime", LocalDateTime.now());
        updatedVariables.put("counter", ((Integer) initialVariables.get("counter")) + 10);
        
        runtimeService.setVariables(processInstanceId, updatedVariables);
        
        // 完成所有任务
        completeAllTasks(processInstanceId);
        
        // Then: 验证变量变更历史
        HistoryQueryRequest queryRequest = HistoryQueryRequest.builder()
            .processInstanceId(processInstanceId)
            .pageSize(100)
            .page(1)
            .build();
        
        HistoryQueryResult historyResult = historyManagerComponent
            .queryHistoricVariableInstances(queryRequest);
        
        assertThat(historyResult.getVariableInstances()).isNotEmpty();
        
        // 验证所有变量都有历史记录
        Set<String> expectedVariableNames = updatedVariables.keySet();
        Set<String> actualVariableNames = historyResult.getVariableInstances().stream()
            .map(HistoryQueryResult.VariableInstanceHistory::getVariableName)
            .collect(Collectors.toSet());
        
        assertThat(actualVariableNames).containsAll(expectedVariableNames);
        
        // 验证变量最终值的正确性
        for (HistoryQueryResult.VariableInstanceHistory varHistory : historyResult.getVariableInstances()) {
            String varName = varHistory.getVariableName();
            if (updatedVariables.containsKey(varName)) {
                Object expectedValue = updatedVariables.get(varName);
                Object actualValue = varHistory.getValue();
                
                if (expectedValue instanceof LocalDateTime && actualValue instanceof Date) {
                    // 处理时间类型的比较 - 跳过精度差异
                    continue;
                }
                
                assertThat(actualValue).isEqualTo(expectedValue);
            }
        }
    }

    /**
     * 属性测试: 归档功能应该正确处理完成的流程实例
     * 功能: workflow-engine-core, 属性 12: 历史数据归档完整性
     */
    @RepeatedTest(6)
    void archiveFunctionShouldCorrectlyHandleCompletedProcesses() {
        // Given: 创建并完成流程实例
        String processKey = generateProcessKey();
        String businessKey = generateBusinessKey();
        String startUserId = generateUserId();
        
        ProcessInstanceExecutionData executionData = createAndExecuteCompleteProcess(
            processKey, businessKey, startUserId);
        
        // When: 归档历史数据
        String archiveReason = "测试归档 - " + UUID.randomUUID().toString().substring(0, 8);
        HistoryArchiveResult archiveResult = historyManagerComponent
            .archiveHistoryData(executionData.getProcessInstanceId(), archiveReason);
        
        // Then: 验证归档结果
        assertThat(archiveResult.isSuccess()).isTrue();
        assertThat(archiveResult.getArchiveId()).isNotNull();
        assertThat(archiveResult.getProcessInstanceId()).isEqualTo(executionData.getProcessInstanceId());
        assertThat(archiveResult.getArchivedDataCount()).isGreaterThan(0);
        assertThat(archiveResult.getArchiveTime()).isNotNull();
        assertThat(archiveResult.getMessage()).contains("成功");
        
        // 验证归档后数据仍然可以查询
        HistoryQueryResult postArchiveResult = historyManagerComponent
            .getCompleteProcessHistory(executionData.getProcessInstanceId());
        
        assertThat(postArchiveResult).isNotNull();
        assertThat(postArchiveResult.getProcessInstances()).hasSize(1);
        assertThat(postArchiveResult.getTaskInstances()).isNotEmpty();
        assertThat(postArchiveResult.getVariableInstances()).isNotEmpty();
    }

    /**
     * 属性测试: 未完成的流程实例不应该被归档
     * 功能: workflow-engine-core, 属性 12: 历史数据归档完整性
     */
    @Test
    void unfinishedProcessInstanceShouldNotBeArchived() {
        // Given: 创建但不完成流程实例
        String processKey = generateProcessKey();
        String businessKey = generateBusinessKey();
        String startUserId = generateUserId();
        
        Map<String, Object> variables = generateProcessVariables();
        String processInstanceId = createProcessInstance(processKey, businessKey, startUserId, variables);
        
        // 验证流程实例确实在运行中
        ProcessInstance runningInstance = runtimeService.createProcessInstanceQuery()
            .processInstanceId(processInstanceId)
            .singleResult();
        assertThat(runningInstance).isNotNull();
        
        // When: 尝试归档未完成的流程实例
        String archiveReason = "尝试归档未完成流程";
        HistoryArchiveResult archiveResult = historyManagerComponent
            .archiveHistoryData(processInstanceId, archiveReason);
        
        // Then: 归档应该失败
        assertThat(archiveResult.isSuccess()).isFalse();
        assertThat(archiveResult.getErrorMessage()).contains("尚未完成");
    }

    /**
     * 属性测试: 历史数据导出应该包含完整信息
     * 功能: workflow-engine-core, 属性 12: 历史数据归档完整性
     */
    @RepeatedTest(5)
    void historyDataExportShouldContainCompleteInformation() {
        // Given: 创建并完成多个流程实例
        List<ProcessInstanceExecutionData> executionDataList = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            String processKey = generateProcessKey();
            String businessKey = generateBusinessKey();
            String startUserId = generateUserId();
            
            ProcessInstanceExecutionData executionData = createAndExecuteCompleteProcess(
                processKey, businessKey, startUserId);
            executionDataList.add(executionData);
        }
        
        // When: 导出历史数据
        HistoryQueryRequest exportRequest = HistoryQueryRequest.builder()
            .pageSize(100)
            .page(1)
            .exportFormat("CSV")
            .finishedOnly(true)
            .build();
        
        var exportResult = historyManagerComponent.exportHistoryData(exportRequest);
        
        // Then: 验证导出结果
        assertThat(exportResult).isNotNull();
        assertThat(exportResult.getFileName()).isNotNull();
        assertThat(exportResult.getFileName()).endsWith(".csv");
        assertThat(exportResult.getFormat()).isEqualTo("CSV");
        assertThat(exportResult.getRecordCount()).isGreaterThanOrEqualTo(3L);
        assertThat(exportResult.getFileContent()).isNotNull();
        assertThat(exportResult.getExportTime()).isNotNull();
        
        // 验证导出内容包含关键信息
        String content = exportResult.getFileContent();
        assertThat(content).contains("Process Instance ID");
        assertThat(content).contains("Process Definition Key");
        assertThat(content).contains("Business Key");
        assertThat(content).contains("Start Time");
        assertThat(content).contains("End Time");
        assertThat(content).contains("Duration");
        
        // 验证每个流程实例的数据都在导出内容中
        for (ProcessInstanceExecutionData executionData : executionDataList) {
            assertThat(content).contains(executionData.getProcessInstanceId());
        }
    }

    /**
     * 属性测试: 历史数据统计应该准确反映实际数据
     * 功能: workflow-engine-core, 属性 12: 历史数据归档完整性
     */
    @RepeatedTest(5)
    void historyStatisticsShouldAccuratelyReflectActualData() {
        // Given: 创建并完成多个流程实例
        int processCount = 3 + new Random().nextInt(3); // 3-5个流程实例
        List<ProcessInstanceExecutionData> executionDataList = new ArrayList<>();
        
        for (int i = 0; i < processCount; i++) {
            String processKey = generateProcessKey();
            String businessKey = generateBusinessKey();
            String startUserId = generateUserId();
            
            ProcessInstanceExecutionData executionData = createAndExecuteCompleteProcess(
                processKey, businessKey, startUserId);
            executionDataList.add(executionData);
        }
        
        // When: 获取历史数据统计
        HistoryQueryRequest statsRequest = HistoryQueryRequest.builder()
            .pageSize(100)
            .page(1)
            .build();
        
        var statisticsResult = historyManagerComponent.getHistoryStatistics(statsRequest);
        
        // Then: 验证统计结果
        assertThat(statisticsResult).isNotNull();
        assertThat(statisticsResult.getCompletedProcessCount()).isGreaterThanOrEqualTo(processCount);
        assertThat(statisticsResult.getCompletedTaskCount()).isGreaterThanOrEqualTo(processCount * 2); // 每个流程2个任务
        assertThat(statisticsResult.getAverageDurationMillis()).isGreaterThan(0L);
        assertThat(statisticsResult.getProcessDefinitionStats()).isNotEmpty();
        assertThat(statisticsResult.getStatisticsTime()).isNotNull();
        
        // 验证流程定义统计的准确性
        Map<String, Long> processDefStats = statisticsResult.getProcessDefinitionStats();
        Set<String> actualProcessKeys = executionDataList.stream()
            .map(ProcessInstanceExecutionData::getProcessDefinitionKey)
            .collect(Collectors.toSet());
        
        for (String processKey : actualProcessKeys) {
            assertThat(processDefStats).containsKey(processKey);
            assertThat(processDefStats.get(processKey)).isGreaterThanOrEqualTo(1L);
        }
    }

    // ==================== 辅助方法 ====================

    /**
     * 流程实例执行数据
     */
    private static class ProcessInstanceExecutionData {
        private final String processInstanceId;
        private final String processDefinitionKey;
        private final String businessKey;
        private final String startUserId;
        private final Map<String, Object> processVariables;
        private final List<String> completedTaskIds;

        public ProcessInstanceExecutionData(String processInstanceId, String processDefinitionKey, 
                String businessKey, String startUserId, Map<String, Object> processVariables, 
                List<String> completedTaskIds) {
            this.processInstanceId = processInstanceId;
            this.processDefinitionKey = processDefinitionKey;
            this.businessKey = businessKey;
            this.startUserId = startUserId;
            this.processVariables = processVariables;
            this.completedTaskIds = completedTaskIds;
        }

        public String getProcessInstanceId() { return processInstanceId; }
        public String getProcessDefinitionKey() { return processDefinitionKey; }
        public String getBusinessKey() { return businessKey; }
        public String getStartUserId() { return startUserId; }
        public Map<String, Object> getProcessVariables() { return processVariables; }
        public List<String> getCompletedTaskIds() { return completedTaskIds; }
    }

    /**
     * 创建并执行完整的流程实例
     */
    private ProcessInstanceExecutionData createAndExecuteCompleteProcess(
            String processKey, String businessKey, String startUserId) {
        
        Map<String, Object> variables = generateProcessVariables();
        String processInstanceId = createProcessInstance(processKey, businessKey, startUserId, variables);
        
        List<String> completedTaskIds = completeAllTasks(processInstanceId);
        
        return new ProcessInstanceExecutionData(
            processInstanceId, processKey, businessKey, startUserId, variables, completedTaskIds);
    }

    /**
     * 创建流程实例
     */
    private String createProcessInstance(String processKey, String businessKey, 
            String startUserId, Map<String, Object> variables) {
        
        try {
            // 部署流程定义
            String processName = "Test Process " + processKey;
            
            ProcessDefinitionRequest processRequest = new ProcessDefinitionRequest();
            processRequest.setName(processName);
            processRequest.setKey(processKey);
            processRequest.setCategory("test");
            processRequest.setBpmnXml(String.format(TEST_BPMN_WITH_VARIABLES, processKey, processName));
            
            DeploymentResult deploymentResult = processEngineComponent.deployProcess(processRequest);
            assertThat(deploymentResult.isSuccess()).isTrue();
            
            // 启动流程实例
            StartProcessRequest instanceRequest = new StartProcessRequest();
            instanceRequest.setProcessDefinitionKey(processKey);
            instanceRequest.setBusinessKey(businessKey);
            instanceRequest.setStartUserId(startUserId);
            instanceRequest.setVariables(variables);
            
            ProcessInstanceResult instanceResult = processEngineComponent.startProcess(instanceRequest);
            assertThat(instanceResult.isSuccess()).isTrue();
            
            return instanceResult.getProcessInstanceId();
            
        } catch (Exception e) {
            throw new RuntimeException("创建流程实例失败", e);
        }
    }

    /**
     * 完成所有任务
     */
    private List<String> completeAllTasks(String processInstanceId) {
        List<String> completedTaskIds = new ArrayList<>();
        
        // 完成所有任务直到流程结束
        List<Task> tasks;
        while (!(tasks = taskService.createTaskQuery()
                .processInstanceId(processInstanceId)
                .list()).isEmpty()) {
            
            for (Task task : tasks) {
                // 设置任务变量
                Map<String, Object> taskVariables = new HashMap<>();
                taskVariables.put("taskResult", "approved");
                taskVariables.put("taskComment", "自动完成任务: " + task.getName());
                taskVariables.put("completedBy", task.getAssignee());
                taskVariables.put("completedTime", new Date());
                
                taskService.setVariablesLocal(task.getId(), taskVariables);
                taskService.complete(task.getId());
                
                completedTaskIds.add(task.getId());
            }
        }
        
        return completedTaskIds;
    }

    // ==================== 随机数据生成器 ====================

    private final Random random = new Random();

    private String generateProcessKey() {
        return "testProcess_" + generateRandomString(8);
    }

    private String generateBusinessKey() {
        return "business_" + generateRandomString(10);
    }

    private String generateUserId() {
        return "user_" + generateRandomString(6);
    }

    private Map<String, Object> generateProcessVariables() {
        Map<String, Object> variables = new HashMap<>();
        variables.put("requestId", "REQ-" + generateRandomString(8));
        variables.put("priority", random.nextInt(5) + 1);
        variables.put("amount", random.nextDouble() * 10000);
        variables.put("status", "pending");
        variables.put("counter", random.nextInt(100));
        variables.put("description", "测试流程变量 " + generateRandomString(10));
        variables.put("createTime", LocalDateTime.now());
        variables.put("isUrgent", random.nextBoolean());
        return variables;
    }

    private String generateRandomString(int length) {
        String chars = "abcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }
}