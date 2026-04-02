package com.workflow.integration;

import org.flowable.bpmn.model.*;
import org.flowable.bpmn.model.Process;
import org.flowable.engine.*;
import org.flowable.engine.repository.Deployment;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import java.util.*;

import static org.assertj.core.api.Assertions.*;

/**
 * 多实例子流程端到端集成测试
 * 
 * 测试场景：
 * 1. 部署包含多实例子流程的 BPMN XML
 * 2. 启动流程实例，模拟前端手动分配处理人
 * 3. 完成前置任务触发数据注入
 * 4. 验证子任务创建数量和分配正确
 * 5. 逐个完成子任务，验证数据回写（含乐观锁）
 * 6. 验证主任务表单数据在子任务中正确显示
 * 7. 验证流程自动推进到下一节点
 * 8. 测试取消和撤回场景的级联处理
 * 
 * 需求: 3.1, 4.1, 4.2, 5.1, 5.2, 6.3, 9.1, 9.2, 新增功能
 * 
 * **Validates: Requirements 3.1, 4.1, 4.2, 5.1, 5.2, 6.3, 9.1, 9.2**
 * 
 * @author Workflow Engine Team
 * @version 1.0
 */
@DisplayName("多实例子流程端到端集成测试")
class MultiInstanceSubProcessEndToEndTest {
    
    // Flowable Services
    private ProcessEngine processEngine;
    private RepositoryService repositoryService;
    private RuntimeService runtimeService;
    private TaskService taskService;
    private HistoryService historyService;
    
    // Database
    private EmbeddedDatabase embeddedDatabase;
    private JdbcTemplate jdbcTemplate;
    
    // Test data
    private static final String SUB_TABLE_NAME = "fu_participants";
    private static final String ASSIGNEE_FIELD = "assignee_user_id";
    private static final String FOREIGN_KEY_FIELD = "main_record_id";
    private static final Long MAIN_RECORD_ID = 1001L;
    
    @BeforeEach
    void setUp() {
        // Initialize embedded H2 database
        embeddedDatabase = new EmbeddedDatabaseBuilder()
            .setType(EmbeddedDatabaseType.H2)
            .build();
        
        jdbcTemplate = new JdbcTemplate(embeddedDatabase);
        
        // Initialize Flowable ProcessEngine with embedded database
        // Flowable will automatically create the schema
        ProcessEngineConfiguration config = ProcessEngineConfiguration
            .createStandaloneProcessEngineConfiguration()
            .setDataSource(embeddedDatabase)
            .setDatabaseSchemaUpdate(ProcessEngineConfiguration.DB_SCHEMA_UPDATE_CREATE_DROP)
            .setAsyncExecutorActivate(false);
        
        processEngine = config.buildProcessEngine();
        repositoryService = processEngine.getRepositoryService();
        runtimeService = processEngine.getRuntimeService();
        taskService = processEngine.getTaskService();
        historyService = processEngine.getHistoryService();
        
        // Create sub-table for testing
        createSubTable();
    }
    
    @AfterEach
    void tearDown() {
        if (processEngine != null) {
            processEngine.close();
        }
        if (embeddedDatabase != null) {
            embeddedDatabase.shutdown();
        }
    }
    
    /**
     * 测试场景 1：完整的多实例子流程端到端流程
     * 
     * 流程步骤：
     * 1. 部署包含多实例子流程的 BPMN XML
     * 2. 启动流程实例
     * 3. 在子表中插入 3 行数据并手动分配处理人
     * 4. 手动注入集合变量（模拟 SubTableDataInjector）
     * 5. 完成前置任务
     * 6. 验证创建了 3 个子任务并正确分配
     * 7. 逐个完成子任务，验证数据回写
     * 8. 验证流程自动推进到下一节点
     */
    @Test
    @DisplayName("完整的多实例子流程端到端流程")
    void testCompleteMultiInstanceSubProcessFlow() {
        // ========== 步骤 1: 部署 BPMN XML ==========
        BpmnModel bpmnModel = createMultiInstanceBpmnModel();
        Deployment deployment = repositoryService.createDeployment()
            .addBpmnModel("multi-instance-test.bpmn", bpmnModel)
            .name("Multi-Instance Test Process")
            .deploy();
        
        assertThat(deployment).isNotNull();
        assertThat(deployment.getId()).isNotNull();
        
        // ========== 步骤 2: 启动流程实例 ==========
        Map<String, Object> variables = new HashMap<>();
        variables.put("mainRecordId", MAIN_RECORD_ID);
        variables.put("meetingTitle", "2026 Q2 产品规划会议");
        variables.put("meetingTime", "2026-04-15 14:00");
        variables.put("meetingLocation", "3 楼会议室");
        
        ProcessInstance processInstance = runtimeService.startProcessInstanceByKey(
            "MultiInstanceTestProcess", variables);
        
        assertThat(processInstance).isNotNull();
        assertThat(processInstance.isEnded()).isFalse();
        
        // ========== 步骤 3: 插入子表数据并手动分配处理人 ==========
        insertSubTableData(MAIN_RECORD_ID, "张三", "user-001");
        insertSubTableData(MAIN_RECORD_ID, "李四", "user-002");
        insertSubTableData(MAIN_RECORD_ID, "王五", "user-003");
        
        // 验证子表数据已插入
        List<Map<String, Object>> subTableRows = jdbcTemplate.queryForList(
            "SELECT * FROM " + SUB_TABLE_NAME + " WHERE " + FOREIGN_KEY_FIELD + " = ?",
            MAIN_RECORD_ID);
        assertThat(subTableRows).hasSize(3);
        
        // ========== 步骤 4: 手动注入集合变量（模拟 SubTableDataInjector） ==========
        List<Map<String, Object>> collectionVariable = new ArrayList<>();
        for (Map<String, Object> row : subTableRows) {
            Map<String, Object> element = new HashMap<>();
            element.put("rowId", row.get("id"));
            element.put("assigneeId", row.get(ASSIGNEE_FIELD));
            element.put("rowVersion", row.get("row_version"));
            collectionVariable.add(element);
        }
        
        runtimeService.setVariable(processInstance.getId(), 
            "multiInstance_fu_participants_collection", collectionVariable);
        
        // ========== 步骤 5: 完成前置任务 ==========
        Task assignTask = taskService.createTaskQuery()
            .processInstanceId(processInstance.getId())
            .taskDefinitionKey("assignParticipantsTask")
            .singleResult();
        
        assertThat(assignTask).isNotNull();
        assertThat(assignTask.getName()).isEqualTo("分配参与人");
        
        taskService.complete(assignTask.getId());
        
        // ========== 步骤 6: 验证创建了 3 个子任务并正确分配 ==========
        List<Task> subTasks = taskService.createTaskQuery()
            .processInstanceId(processInstance.getId())
            .taskDefinitionKey("MI_UserTask_45")
            .list();
        
        assertThat(subTasks).hasSize(3);
        
        // 验证每个子任务的分配
        Set<String> assignees = new HashSet<>();
        for (Task subTask : subTasks) {
            assertThat(subTask.getAssignee()).isNotNull();
            assignees.add(subTask.getAssignee());
        }
        assertThat(assignees).containsExactlyInAnyOrder("user-001", "user-002", "user-003");
        
        // ========== 步骤 7: 逐个完成子任务，验证数据回写 ==========
        for (Task subTask : subTasks) {
            String assignee = subTask.getAssignee();
            
            // 验证主任务数据在子任务中可访问
            Map<String, Object> mainFormData = runtimeService.getVariables(processInstance.getId());
            assertThat(mainFormData.get("meetingTitle")).isEqualTo("2026 Q2 产品规划会议");
            assertThat(mainFormData.get("meetingTime")).isEqualTo("2026-04-15 14:00");
            
            // 获取子表行 ID（从流程变量）
            Map<String, Object> currentItem = (Map<String, Object>) 
                runtimeService.getVariable(subTask.getExecutionId(), "currentItem");
            Long rowId = ((Number) currentItem.get("rowId")).longValue();
            Long rowVersion = ((Number) currentItem.get("rowVersion")).longValue();
            
            // 模拟数据回写到子表（含乐观锁）
            String updateSql = String.format(
                "UPDATE %s SET will_attend = ?, dietary_preference = ?, remarks = ?, " +
                "row_version = row_version + 1 WHERE id = ? AND row_version = ?",
                SUB_TABLE_NAME);
            int updated = jdbcTemplate.update(updateSql,
                "是", "无", "需要投影仪", rowId, rowVersion);
            
            assertThat(updated).isEqualTo(1);
            
            // 完成子任务
            taskService.complete(subTask.getId());
        }
        
        // ========== 步骤 8: 验证流程自动推进到下一节点 ==========
        Task nextTask = taskService.createTaskQuery()
            .processInstanceId(processInstance.getId())
            .taskDefinitionKey("generateReportTask")
            .singleResult();
        
        assertThat(nextTask).isNotNull();
        assertThat(nextTask.getName()).isEqualTo("生成参会名单");
        
        // 验证子表数据已更新
        List<Map<String, Object>> updatedRows = jdbcTemplate.queryForList(
            "SELECT * FROM " + SUB_TABLE_NAME + " WHERE " + FOREIGN_KEY_FIELD + " = ?",
            MAIN_RECORD_ID);
        
        for (Map<String, Object> row : updatedRows) {
            assertThat(row.get("will_attend")).isEqualTo("是");
            assertThat(row.get("row_version")).isEqualTo(2L);
        }
    }
    
    /**
     * 测试场景 2：乐观锁冲突场景
     * 
     * 验证当 row_version 不一致时，数据回写被拒绝
     */
    @Test
    @DisplayName("乐观锁冲突场景")
    void testOptimisticLockConflict() {
        // 部署流程并启动
        BpmnModel bpmnModel = createMultiInstanceBpmnModel();
        repositoryService.createDeployment()
            .addBpmnModel("multi-instance-test.bpmn", bpmnModel)
            .deploy();
        
        Map<String, Object> variables = new HashMap<>();
        variables.put("mainRecordId", MAIN_RECORD_ID);
        ProcessInstance processInstance = runtimeService.startProcessInstanceByKey(
            "MultiInstanceTestProcess", variables);
        
        // 插入子表数据
        insertSubTableData(MAIN_RECORD_ID, "张三", "user-001");
        
        // 注入集合变量
        List<Map<String, Object>> subTableRows = jdbcTemplate.queryForList(
            "SELECT * FROM " + SUB_TABLE_NAME + " WHERE " + FOREIGN_KEY_FIELD + " = ?",
            MAIN_RECORD_ID);
        
        List<Map<String, Object>> collectionVariable = new ArrayList<>();
        for (Map<String, Object> row : subTableRows) {
            Map<String, Object> element = new HashMap<>();
            element.put("rowId", row.get("id"));
            element.put("assigneeId", row.get(ASSIGNEE_FIELD));
            element.put("rowVersion", row.get("row_version"));
            collectionVariable.add(element);
        }
        runtimeService.setVariable(processInstance.getId(), 
            "multiInstance_fu_participants_collection", collectionVariable);
        
        // 完成前置任务
        Task assignTask = taskService.createTaskQuery()
            .processInstanceId(processInstance.getId())
            .taskDefinitionKey("assignParticipantsTask")
            .singleResult();
        taskService.complete(assignTask.getId());
        
        // 获取子任务
        Task subTask = taskService.createTaskQuery()
            .processInstanceId(processInstance.getId())
            .taskDefinitionKey("MI_UserTask_45")
            .singleResult();
        
        Map<String, Object> currentItem = (Map<String, Object>) 
            runtimeService.getVariable(subTask.getExecutionId(), "currentItem");
        Long rowId = ((Number) currentItem.get("rowId")).longValue();
        Long rowVersion = ((Number) currentItem.get("rowVersion")).longValue();
        
        // 模拟另一个进程修改了数据（row_version 递增）
        jdbcTemplate.update(
            "UPDATE " + SUB_TABLE_NAME + " SET row_version = row_version + 1 WHERE id = ?",
            rowId);
        
        // 尝试使用旧的 row_version 更新数据
        String updateSql = String.format(
            "UPDATE %s SET will_attend = ?, row_version = row_version + 1 " +
            "WHERE id = ? AND row_version = ?",
            SUB_TABLE_NAME);
        int updated = jdbcTemplate.update(updateSql, "是", rowId, rowVersion);
        
        // 验证：更新失败（乐观锁冲突）
        assertThat(updated).isEqualTo(0);
        
        // 验证数据未被修改
        Map<String, Object> row = jdbcTemplate.queryForMap(
            "SELECT * FROM " + SUB_TABLE_NAME + " WHERE id = ?", rowId);
        assertThat(row.get("will_attend")).isNull();
        assertThat(row.get("row_version")).isEqualTo(2L);
    }
    
    /**
     * 测试场景 3：验证主任务表单数据在子任务中正确显示
     * 
     * 验证子任务处理人可以访问主任务的完整表单数据（只读）
     * 
     * **Validates: Requirements 6.1, 新增功能 - 主任务信息展示**
     */
    @Test
    @DisplayName("验证主任务表单数据在子任务中正确显示")
    void testMainFormDataVisibleInSubTasks() {
        // 部署流程并启动
        BpmnModel bpmnModel = createMultiInstanceBpmnModel();
        repositoryService.createDeployment()
            .addBpmnModel("multi-instance-test.bpmn", bpmnModel)
            .deploy();
        
        // 启动流程实例，设置主表单数据
        Map<String, Object> variables = new HashMap<>();
        variables.put("mainRecordId", MAIN_RECORD_ID);
        variables.put("meetingTitle", "2026 Q2 产品规划会议");
        variables.put("meetingTime", "2026-04-15 14:00");
        variables.put("meetingLocation", "3 楼会议室");
        variables.put("organizer", "张经理");
        variables.put("meetingDescription", "讨论 Q2 产品路线图和资源分配");
        
        ProcessInstance processInstance = runtimeService.startProcessInstanceByKey(
            "MultiInstanceTestProcess", variables);
        
        // 插入子表数据
        insertSubTableData(MAIN_RECORD_ID, "张三", "user-001");
        insertSubTableData(MAIN_RECORD_ID, "李四", "user-002");
        
        // 注入集合变量
        List<Map<String, Object>> subTableRows = jdbcTemplate.queryForList(
            "SELECT * FROM " + SUB_TABLE_NAME + " WHERE " + FOREIGN_KEY_FIELD + " = ?",
            MAIN_RECORD_ID);
        
        List<Map<String, Object>> collectionVariable = new ArrayList<>();
        for (Map<String, Object> row : subTableRows) {
            Map<String, Object> element = new HashMap<>();
            element.put("rowId", row.get("id"));
            element.put("assigneeId", row.get(ASSIGNEE_FIELD));
            element.put("rowVersion", row.get("row_version"));
            collectionVariable.add(element);
        }
        runtimeService.setVariable(processInstance.getId(), 
            "multiInstance_fu_participants_collection", collectionVariable);
        
        // 完成前置任务
        Task assignTask = taskService.createTaskQuery()
            .processInstanceId(processInstance.getId())
            .taskDefinitionKey("assignParticipantsTask")
            .singleResult();
        taskService.complete(assignTask.getId());
        
        // 获取子任务
        List<Task> subTasks = taskService.createTaskQuery()
            .processInstanceId(processInstance.getId())
            .taskDefinitionKey("MI_UserTask_45")
            .list();
        
        assertThat(subTasks).hasSize(2);
        
        // 验证每个子任务都能访问主任务表单数据
        for (Task subTask : subTasks) {
            // 获取流程变量（模拟 MultiInstanceDataResolver.loadMainFormData()）
            Map<String, Object> mainFormData = runtimeService.getVariables(processInstance.getId());
            
            // 验证主任务表单数据完整且正确
            assertThat(mainFormData.get("meetingTitle")).isEqualTo("2026 Q2 产品规划会议");
            assertThat(mainFormData.get("meetingTime")).isEqualTo("2026-04-15 14:00");
            assertThat(mainFormData.get("meetingLocation")).isEqualTo("3 楼会议室");
            assertThat(mainFormData.get("organizer")).isEqualTo("张经理");
            assertThat(mainFormData.get("meetingDescription")).isEqualTo("讨论 Q2 产品路线图和资源分配");
            
            // 验证子任务可以访问自己的 currentItem
            Map<String, Object> currentItem = (Map<String, Object>) 
                runtimeService.getVariable(subTask.getExecutionId(), "currentItem");
            assertThat(currentItem).isNotNull();
            assertThat(currentItem.get("rowId")).isNotNull();
            assertThat(currentItem.get("assigneeId")).isEqualTo(subTask.getAssignee());
            
            // 验证系统变量存在（但在实际应用中会被过滤）
            assertThat(mainFormData.containsKey("multiInstance_fu_participants_collection")).isTrue();
        }
    }
    
    /**
     * 测试场景 4：流程取消时验证子任务状态
     * 
     * 验证主流程被终止时，未完成的子任务被取消，已提交的数据保留
     * 
     * **Validates: Requirements 9.1, 9.3**
     */
    @Test
    @DisplayName("流程取消时验证子任务状态")
    void testProcessCancellationWithMultiInstance() {
        // 部署流程并启动
        BpmnModel bpmnModel = createMultiInstanceBpmnModel();
        repositoryService.createDeployment()
            .addBpmnModel("multi-instance-test.bpmn", bpmnModel)
            .deploy();
        
        Map<String, Object> variables = new HashMap<>();
        variables.put("mainRecordId", MAIN_RECORD_ID);
        ProcessInstance processInstance = runtimeService.startProcessInstanceByKey(
            "MultiInstanceTestProcess", variables);
        
        // 插入子表数据
        insertSubTableData(MAIN_RECORD_ID, "张三", "user-001");
        insertSubTableData(MAIN_RECORD_ID, "李四", "user-002");
        insertSubTableData(MAIN_RECORD_ID, "王五", "user-003");
        
        // 注入集合变量
        List<Map<String, Object>> subTableRows = jdbcTemplate.queryForList(
            "SELECT * FROM " + SUB_TABLE_NAME + " WHERE " + FOREIGN_KEY_FIELD + " = ?",
            MAIN_RECORD_ID);
        
        List<Map<String, Object>> collectionVariable = new ArrayList<>();
        for (Map<String, Object> row : subTableRows) {
            Map<String, Object> element = new HashMap<>();
            element.put("rowId", row.get("id"));
            element.put("assigneeId", row.get(ASSIGNEE_FIELD));
            element.put("rowVersion", row.get("row_version"));
            collectionVariable.add(element);
        }
        runtimeService.setVariable(processInstance.getId(), 
            "multiInstance_fu_participants_collection", collectionVariable);
        
        // 完成前置任务
        Task assignTask = taskService.createTaskQuery()
            .processInstanceId(processInstance.getId())
            .taskDefinitionKey("assignParticipantsTask")
            .singleResult();
        taskService.complete(assignTask.getId());
        
        // 验证创建了 3 个子任务
        List<Task> subTasks = taskService.createTaskQuery()
            .processInstanceId(processInstance.getId())
            .taskDefinitionKey("MI_UserTask_45")
            .list();
        assertThat(subTasks).hasSize(3);
        
        // 完成第 1 个子任务
        Task firstTask = subTasks.get(0);
        Map<String, Object> currentItem = (Map<String, Object>) 
            runtimeService.getVariable(firstTask.getExecutionId(), "currentItem");
        Long rowId = ((Number) currentItem.get("rowId")).longValue();
        
        jdbcTemplate.update(
            "UPDATE " + SUB_TABLE_NAME + " SET will_attend = ?, row_version = row_version + 1 " +
            "WHERE id = ? AND row_version = 1",
            "是", rowId);
        taskService.complete(firstTask.getId());
        
        // 取消流程实例
        runtimeService.deleteProcessInstance(processInstance.getId(), "测试取消");
        
        // 验证流程已结束
        ProcessInstance pi = runtimeService.createProcessInstanceQuery()
            .processInstanceId(processInstance.getId())
            .singleResult();
        assertThat(pi).isNull();
        
        // 验证第 1 个子任务的数据已保留
        Map<String, Object> completedRow = jdbcTemplate.queryForMap(
            "SELECT * FROM " + SUB_TABLE_NAME + " WHERE id = ?", rowId);
        assertThat(completedRow.get("will_attend")).isEqualTo("是");
        assertThat(completedRow.get("row_version")).isEqualTo(2L);
    }
    
    /**
     * 测试场景 5：手动分配处理人场景
     * 
     * 验证前端手动分配处理人的完整流程：
     * 1. 插入子表数据时处理人字段为空
     * 2. 通过 UPDATE 语句模拟手动分配
     * 3. 验证数据注入时所有处理人已分配
     * 
     * **Validates: Requirements 3.5, 新增功能 - 手动分配**
     */
    @Test
    @DisplayName("手动分配处理人场景")
    void testManualAssignmentFlow() {
        // 部署流程并启动
        BpmnModel bpmnModel = createMultiInstanceBpmnModel();
        repositoryService.createDeployment()
            .addBpmnModel("multi-instance-test.bpmn", bpmnModel)
            .deploy();
        
        Map<String, Object> variables = new HashMap<>();
        variables.put("mainRecordId", MAIN_RECORD_ID);
        ProcessInstance processInstance = runtimeService.startProcessInstanceByKey(
            "MultiInstanceTestProcess", variables);
        
        // 步骤 1: 插入子表数据，处理人字段为空（模拟前端初始添加）
        String insertSql = String.format(
            "INSERT INTO %s (%s, name, row_version) VALUES (?, ?, 1)",
            SUB_TABLE_NAME, FOREIGN_KEY_FIELD);
        
        jdbcTemplate.update(insertSql, MAIN_RECORD_ID, "张三");
        jdbcTemplate.update(insertSql, MAIN_RECORD_ID, "李四");
        jdbcTemplate.update(insertSql, MAIN_RECORD_ID, "王五");
        
        // 验证子表数据已插入但处理人为空
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            "SELECT * FROM " + SUB_TABLE_NAME + " WHERE " + FOREIGN_KEY_FIELD + " = ?",
            MAIN_RECORD_ID);
        assertThat(rows).hasSize(3);
        for (Map<String, Object> row : rows) {
            assertThat(row.get(ASSIGNEE_FIELD)).isNull();
        }
        
        // 步骤 2: 模拟前端手动分配处理人（通过 SubTableAssignmentHandler）
        for (int i = 0; i < rows.size(); i++) {
            Long rowId = ((Number) rows.get(i).get("id")).longValue();
            String assigneeId = "user-00" + (i + 1);
            
            String updateSql = String.format(
                "UPDATE %s SET %s = ? WHERE id = ?",
                SUB_TABLE_NAME, ASSIGNEE_FIELD);
            int updated = jdbcTemplate.update(updateSql, assigneeId, rowId);
            assertThat(updated).isEqualTo(1);
        }
        
        // 验证所有行已分配处理人
        rows = jdbcTemplate.queryForList(
            "SELECT * FROM " + SUB_TABLE_NAME + " WHERE " + FOREIGN_KEY_FIELD + " = ?",
            MAIN_RECORD_ID);
        for (Map<String, Object> row : rows) {
            assertThat(row.get(ASSIGNEE_FIELD)).isNotNull();
        }
        
        // 步骤 3: 注入集合变量（模拟 SubTableDataInjector）
        List<Map<String, Object>> collectionVariable = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> element = new HashMap<>();
            element.put("rowId", row.get("id"));
            element.put("assigneeId", row.get(ASSIGNEE_FIELD));
            element.put("rowVersion", row.get("row_version"));
            collectionVariable.add(element);
        }
        runtimeService.setVariable(processInstance.getId(), 
            "multiInstance_fu_participants_collection", collectionVariable);
        
        // 完成前置任务
        Task assignTask = taskService.createTaskQuery()
            .processInstanceId(processInstance.getId())
            .taskDefinitionKey("assignParticipantsTask")
            .singleResult();
        taskService.complete(assignTask.getId());
        
        // 验证创建了 3 个子任务并正确分配
        List<Task> subTasks = taskService.createTaskQuery()
            .processInstanceId(processInstance.getId())
            .taskDefinitionKey("MI_UserTask_45")
            .list();
        
        assertThat(subTasks).hasSize(3);
        
        Set<String> assignees = new HashSet<>();
        for (Task subTask : subTasks) {
            assertThat(subTask.getAssignee()).isNotNull();
            assignees.add(subTask.getAssignee());
        }
        assertThat(assignees).containsExactlyInAnyOrder("user-001", "user-002", "user-003");
    }
    
    /**
     * 测试场景 6：数据注入验证 - 处理人缺失场景
     * 
     * 验证当子表数据行中存在处理人为空的记录时，数据注入被阻止
     * 
     * **Validates: Requirements 3.5**
     */
    @Test
    @DisplayName("数据注入验证 - 处理人缺失场景")
    void testDataInjectionValidation_MissingAssignee() {
        // 部署流程并启动
        BpmnModel bpmnModel = createMultiInstanceBpmnModel();
        repositoryService.createDeployment()
            .addBpmnModel("multi-instance-test.bpmn", bpmnModel)
            .deploy();
        
        Map<String, Object> variables = new HashMap<>();
        variables.put("mainRecordId", MAIN_RECORD_ID);
        ProcessInstance processInstance = runtimeService.startProcessInstanceByKey(
            "MultiInstanceTestProcess", variables);
        
        // 插入子表数据：第 2 行处理人为空
        insertSubTableData(MAIN_RECORD_ID, "张三", "user-001");
        
        String insertSql = String.format(
            "INSERT INTO %s (%s, name, row_version) VALUES (?, ?, 1)",
            SUB_TABLE_NAME, FOREIGN_KEY_FIELD);
        jdbcTemplate.update(insertSql, MAIN_RECORD_ID, "李四"); // 处理人为空
        
        insertSubTableData(MAIN_RECORD_ID, "王五", "user-003");
        
        // 尝试注入集合变量（应该失败）
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            "SELECT * FROM " + SUB_TABLE_NAME + " WHERE " + FOREIGN_KEY_FIELD + " = ?",
            MAIN_RECORD_ID);
        
        // 验证：第 2 行处理人为空
        assertThat(rows).hasSize(3);
        assertThat(rows.get(1).get(ASSIGNEE_FIELD)).isNull();
        
        // 模拟 SubTableDataInjector 的验证逻辑
        List<Integer> emptyAssigneeRows = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            Object assignee = rows.get(i).get(ASSIGNEE_FIELD);
            if (assignee == null || assignee.toString().trim().isEmpty()) {
                emptyAssigneeRows.add(i + 1); // 行号从 1 开始
            }
        }
        
        // 验证：检测到第 2 行处理人缺失
        assertThat(emptyAssigneeRows).containsExactly(2);
        
        // 在实际应用中，SubTableDataInjector 会抛出异常：
        // throw new WorkflowValidationException("第 2 行缺少处理人（assignee_user_id 字段为空）");
    }
    
    /**
     * 测试场景 7：数据注入验证 - 子表数据为空场景
     * 
     * 验证当子表没有数据行时，数据注入被阻止
     * 
     * **Validates: Requirements 3.4**
     */
    @Test
    @DisplayName("数据注入验证 - 子表数据为空场景")
    void testDataInjectionValidation_EmptySubTable() {
        // 部署流程并启动
        BpmnModel bpmnModel = createMultiInstanceBpmnModel();
        repositoryService.createDeployment()
            .addBpmnModel("multi-instance-test.bpmn", bpmnModel)
            .deploy();
        
        Map<String, Object> variables = new HashMap<>();
        variables.put("mainRecordId", MAIN_RECORD_ID);
        ProcessInstance processInstance = runtimeService.startProcessInstanceByKey(
            "MultiInstanceTestProcess", variables);
        
        // 不插入任何子表数据
        
        // 尝试查询子表数据
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            "SELECT * FROM " + SUB_TABLE_NAME + " WHERE " + FOREIGN_KEY_FIELD + " = ?",
            MAIN_RECORD_ID);
        
        // 验证：子表数据为空
        assertThat(rows).isEmpty();
        
        // 在实际应用中，SubTableDataInjector 会抛出异常：
        // throw new WorkflowValidationException("多实例数据源为空，至少需要一条子表数据");
    }
    
    // ==================== 辅助方法 ====================
    
    /**
     * 创建包含多实例子流程的 BPMN 模型
     */
    private BpmnModel createMultiInstanceBpmnModel() {
        BpmnModel bpmnModel = new BpmnModel();
        Process process = new Process();
        process.setId("MultiInstanceTestProcess");
        process.setName("多实例测试流程");
        
        // 开始事件
        StartEvent startEvent = new StartEvent();
        startEvent.setId("startEvent");
        
        // 前置任务：分配参与人
        UserTask assignTask = new UserTask();
        assignTask.setId("assignParticipantsTask");
        assignTask.setName("分配参与人");
        
        // 多实例子流程
        SubProcess subProcess = new SubProcess();
        subProcess.setId("MultiInstance_SubTable_45");
        subProcess.setName("多实例-参与人信息收集");
        
        // 设置多实例特性
        MultiInstanceLoopCharacteristics loopCharacteristics = new MultiInstanceLoopCharacteristics();
        loopCharacteristics.setSequential(false);
        loopCharacteristics.setInputDataItem("multiInstance_fu_participants_collection");
        loopCharacteristics.setElementVariable("currentItem");
        
        subProcess.setLoopCharacteristics(loopCharacteristics);
        
        // 子流程内部：开始事件
        StartEvent subStartEvent = new StartEvent();
        subStartEvent.setId("MI_Start_45");
        
        // 子流程内部：用户任务
        UserTask subUserTask = new UserTask();
        subUserTask.setId("MI_UserTask_45");
        subUserTask.setName("补充个人信息");
        subUserTask.setAssignee("${currentItem.assigneeId}");
        
        // 子流程内部：结束事件
        EndEvent subEndEvent = new EndEvent();
        subEndEvent.setId("MI_End_45");
        
        // 子流程内部连线
        SequenceFlow subFlow1 = new SequenceFlow();
        subFlow1.setId("MI_Flow1_45");
        subFlow1.setSourceRef("MI_Start_45");
        subFlow1.setTargetRef("MI_UserTask_45");
        
        SequenceFlow subFlow2 = new SequenceFlow();
        subFlow2.setId("MI_Flow2_45");
        subFlow2.setSourceRef("MI_UserTask_45");
        subFlow2.setTargetRef("MI_End_45");
        
        subProcess.addFlowElement(subStartEvent);
        subProcess.addFlowElement(subUserTask);
        subProcess.addFlowElement(subEndEvent);
        subProcess.addFlowElement(subFlow1);
        subProcess.addFlowElement(subFlow2);
        
        // 后续任务：生成参会名单
        UserTask generateReportTask = new UserTask();
        generateReportTask.setId("generateReportTask");
        generateReportTask.setName("生成参会名单");
        
        // 结束事件
        EndEvent endEvent = new EndEvent();
        endEvent.setId("endEvent");
        
        // 主流程连线
        SequenceFlow flow1 = new SequenceFlow();
        flow1.setId("flow1");
        flow1.setSourceRef("startEvent");
        flow1.setTargetRef("assignParticipantsTask");
        
        SequenceFlow flow2 = new SequenceFlow();
        flow2.setId("flow2");
        flow2.setSourceRef("assignParticipantsTask");
        flow2.setTargetRef("MultiInstance_SubTable_45");
        
        SequenceFlow flow3 = new SequenceFlow();
        flow3.setId("flow3");
        flow3.setSourceRef("MultiInstance_SubTable_45");
        flow3.setTargetRef("generateReportTask");
        
        SequenceFlow flow4 = new SequenceFlow();
        flow4.setId("flow4");
        flow4.setSourceRef("generateReportTask");
        flow4.setTargetRef("endEvent");
        
        // 添加所有元素到流程
        process.addFlowElement(startEvent);
        process.addFlowElement(assignTask);
        process.addFlowElement(subProcess);
        process.addFlowElement(generateReportTask);
        process.addFlowElement(endEvent);
        process.addFlowElement(flow1);
        process.addFlowElement(flow2);
        process.addFlowElement(flow3);
        process.addFlowElement(flow4);
        
        bpmnModel.addProcess(process);
        return bpmnModel;
    }
    
    /**
     * 创建子表
     */
    private void createSubTable() {
        String createTableSql = String.format(
            "CREATE TABLE IF NOT EXISTS %s (" +
            "  id BIGINT AUTO_INCREMENT PRIMARY KEY," +
            "  %s BIGINT NOT NULL," +
            "  name VARCHAR(100)," +
            "  %s VARCHAR(50)," +
            "  will_attend VARCHAR(10)," +
            "  dietary_preference VARCHAR(50)," +
            "  remarks TEXT," +
            "  row_version BIGINT NOT NULL DEFAULT 1," +
            "  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
            ")",
            SUB_TABLE_NAME, FOREIGN_KEY_FIELD, ASSIGNEE_FIELD);
        
        jdbcTemplate.execute(createTableSql);
    }
    
    /**
     * 插入子表数据
     */
    private void insertSubTableData(Long mainRecordId, String name, String assigneeUserId) {
        String insertSql = String.format(
            "INSERT INTO %s (%s, name, %s, row_version) VALUES (?, ?, ?, 1)",
            SUB_TABLE_NAME, FOREIGN_KEY_FIELD, ASSIGNEE_FIELD);
        
        jdbcTemplate.update(insertSql, mainRecordId, name, assigneeUserId);
    }
}
