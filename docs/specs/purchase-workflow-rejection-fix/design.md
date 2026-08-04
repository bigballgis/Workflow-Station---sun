# Design Document: Purchase Workflow Rejection Fix

## Overview

本设计文档描述了修复采购审批流程中两个关键缺陷的技术方案：

1. **流程图高亮错误**：当部门经理拒绝采购申请时，前端流程图错误地将所有节点标记为已完成（绿色），而不是仅高亮实际执行的路径
2. **子表单数据丢失**：采购明细子表单（purchase_items_form）的数据在流程执行过程中被意外清空

### Root Cause Analysis

**问题1 - 流程图高亮错误**：
- 前端代码在`parseBpmnXml`函数中使用了错误的逻辑来确定已完成节点
- 当前实现使用`!foundCurrentNode`标志，将当前节点之前的所有节点标记为已完成
- 这种顺序逻辑无法处理分支流程（如审批拒绝走不同路径的情况）
- 正确的做法应该是从后端历史服务查询实际执行过的活动实例

**问题2 - 子表单数据丢失**：
- 需要进一步调查表单数据的保存和加载机制
- 可能的原因包括：流程变量序列化问题、子表单数据未正确传递、或数据库记录被删除

### Solution Approach

1. **后端增强**：在HistoryController中添加新的API端点，返回实际执行的活动ID列表
2. **前端修复**：修改ProcessDiagram组件和任务详情页面，使用后端返回的实际执行活动列表
3. **数据持久化**：确保子表单数据在整个流程生命周期中正确保存和传递

## Architecture

### Component Interaction

```
┌─────────────────┐
│  Task Detail    │
│     View        │
└────────┬────────┘
         │ 1. Load task
         │ 2. Get process history
         ▼
┌─────────────────┐      ┌──────────────────┐
│   History       │◄─────┤  History Service │
│   Controller    │      │   (Flowable)     │
└────────┬────────┘      └──────────────────┘
         │ 3. Return executed activity IDs
         ▼
┌─────────────────┐
│   Process       │
│   Diagram       │
│   Component     │
└─────────────────┘
         │ 4. Highlight only executed nodes
         ▼
    [User sees accurate diagram]
```

### Data Flow

1. 用户打开任务详情页面
2. 前端调用`/api/v1/history/activities?processInstanceId={id}`
3. 后端查询Flowable HistoryService获取已完成的活动实例
4. 后端过滤出`endTime != null`的活动，提取activityId列表
5. 前端接收activityId列表，传递给ProcessDiagram组件
6. ProcessDiagram仅高亮列表中的节点

## Components and Interfaces

### Backend Components

#### 1. HistoryController Enhancement

**新增方法**：
```java
@GetMapping("/executed-activities")
public ResponseEntity<ApiResponse<List<String>>> getExecutedActivityIds(
    @RequestParam("processInstanceId") String processInstanceId)
```

**职责**：
- 查询指定流程实例的历史活动
- 过滤出已完成的活动（endTime != null）
- 返回activityId列表

**实现逻辑**：
```java
List<HistoricActivityInstance> activities = historyService
    .createHistoricActivityInstanceQuery()
    .processInstanceId(processInstanceId)
    .finished()  // 只查询已完成的活动
    .orderByHistoricActivityInstanceStartTime()
    .asc()
    .list();

List<String> executedActivityIds = activities.stream()
    .map(HistoricActivityInstance::getActivityId)
    .distinct()
    .collect(Collectors.toList());
```

#### 2. Form Data Persistence Enhancement

**修改点**：TaskManagerComponent.completeTask方法

**确保**：
- 子表单数据包含在流程变量中
- 使用正确的序列化格式（JSON数组）
- 验证数据完整性

### Frontend Components

#### 1. Task Detail View Enhancement

**文件**：`frontend/user-portal/src/views/tasks/detail.vue`

**修改点**：
- 移除`parseBpmnXml`中的顺序逻辑
- 添加新的API调用获取实际执行的活动ID
- 将执行的活动ID传递给ProcessDiagram组件

**新增方法**：
```typescript
const loadExecutedActivities = async (processInstanceId: string) => {
  try {
    const response = await historyApi.getExecutedActivityIds(processInstanceId)
    return response.data || []
  } catch (error) {
    console.error('Failed to load executed activities:', error)
    return []
  }
}
```

**修改parseBpmnXml**：
```typescript
const parseBpmnXml = async (xml: string, processInstanceId: string) => {
  // ... 解析BPMN结构 ...
  
  // 从后端获取实际执行的活动ID
  const executedIds = await loadExecutedActivities(processInstanceId)
  
  // 标记已完成的节点
  nodes.forEach(node => {
    if (executedIds.includes(node.id)) {
      node.status = 'completed'
      completed.push(node.id)
    } else if (node.id === currentNodeId.value) {
      node.status = 'current'
    } else {
      node.status = 'pending'
    }
  })
  
  // ...
}
```

#### 2. ProcessDiagram Component

**文件**：`frontend/user-portal/src/components/ProcessDiagram.vue`

**无需修改**：组件已经正确使用`completedNodeIds` prop来高亮节点

#### 3. History API Client

**文件**：`frontend/user-portal/src/api/history.ts`（新建）

```typescript
export const historyApi = {
  getExecutedActivityIds(processInstanceId: string) {
    return request.get(`/history/executed-activities`, {
      params: { processInstanceId }
    })
  }
}
```

## Data Models

### Backend DTOs

#### ExecutedActivitiesResponse
```java
public class ExecutedActivitiesResponse {
    private List<String> activityIds;
    private String processInstanceId;
    private int totalCount;
    
    // getters and setters
}
```

### Frontend Interfaces

#### ExecutedActivitiesData
```typescript
interface ExecutedActivitiesData {
  activityIds: string[]
  processInstanceId: string
  totalCount: number
}
```

## Correctness Properties

*属性是一个特征或行为，应该在系统的所有有效执行中保持为真——本质上是关于系统应该做什么的形式化陈述。属性作为人类可读规范和机器可验证正确性保证之间的桥梁。*

### Property 1: 已完成节点集合准确性

*For any* 流程实例，从历史服务查询到的已完成活动ID集合，应该与实际执行过的BPMN活动节点ID集合完全一致，且只包含具有非空结束时间的活动

**Validates: Requirements 1.1, 1.3, 1.4**

### Property 2: 拒绝路径高亮正确性

*For any* 在部门经理审批节点被拒绝的流程实例，流程图应该只高亮：开始节点、部门经理审批节点、部门审批网关、以及拒绝结束节点

**Validates: Requirements 1.2**

### Property 3: 网关分支路径唯一性

*For any* 排他网关（exclusiveGateway），在一次流程执行中，只有一条出口路径应该被标记为已执行，且流程图应该只高亮该路径

**Validates: Requirements 1.5, 3.3**

### Property 4: 历史活动时间顺序性

*For any* 流程实例的历史活动列表，每个活动的开始时间应该小于或等于其结束时间，且活动按开始时间升序排列后应该反映实际执行顺序

**Validates: Requirements 3.1, 3.2, 3.5**

### Property 5: 子表单数据完整持久化

*For any* 包含子表单数据的任务，在提交、完成、或流程结束后，子表单数据应该在流程变量和数据库中完整保留，且可以通过历史查询完整检索

**Validates: Requirements 2.1, 2.3, 2.4, 2.5**

### Property 6: 子表单数据加载完整性

*For any* 任务详情加载操作，如果流程实例包含N条子表单记录，则加载的表单数据应该包含完整的N条记录

**Validates: Requirements 2.2, 5.1**

### Property 7: 流程变量序列化往返一致性

*For any* 包含子表单数组的表单数据，序列化为流程变量后再反序列化，应该得到与原始数据结构等价的对象，且子表单数组的长度和内容保持不变

**Validates: Requirements 6.1, 6.2, 6.3**

### Property 8: 流程图节点状态互斥性

*For any* 流程图中的节点，其状态应该是completed、current、pending三者之一，且同一节点不能同时具有多个状态

**Validates: Requirements 4.1**

### Property 9: 序列流高亮一致性

*For any* 两个已完成节点之间的序列流，该序列流应该被高亮显示；反之，如果源节点或目标节点未完成，则序列流不应该被高亮

**Validates: Requirements 3.4, 4.5**

### Property 10: 数据丢失检测和日志记录

*For any* 预期包含子表单数据但实际数据缺失的情况，系统应该记录包含流程实例ID、任务ID和表单名称的警告日志

**Validates: Requirements 5.2, 7.1**

### Property 11: 流程变量大小限制处理

*For any* 超过大小限制的流程变量，系统应该将数据存储到外部存储，并在流程变量中保留有效的引用，且通过引用能够完整检索原始数据

**Validates: Requirements 6.5**

## Error Handling

### Backend Error Scenarios

1. **流程实例不存在**
   - HTTP 404
   - 错误消息："Process instance not found: {processInstanceId}"

2. **历史服务查询失败**
   - HTTP 500
   - 错误消息："Failed to query process history"
   - 记录完整异常堆栈

3. **子表单数据缺失**
   - HTTP 200（不阻塞流程）
   - 警告日志：包含processInstanceId、taskId、formName
   - 返回空数组

### Frontend Error Scenarios

1. **API调用失败**
   - 显示用户友好的错误消息
   - 降级处理：使用旧的顺序逻辑（临时方案）
   - 记录错误到控制台

2. **BPMN解析失败**
   - 显示"流程图加载失败"消息
   - 提供重试按钮

3. **表单数据不完整**
   - 显示警告提示
   - 允许用户继续但标记数据可能不完整

## Testing Strategy

### Unit Tests

**Backend**：
1. 测试`getExecutedActivityIds`方法返回正确的活动ID列表
2. 测试当流程实例不存在时返回404
3. 测试子表单数据序列化和反序列化
4. 测试历史活动查询的过滤逻辑（finished()）

**Frontend**：
1. 测试`loadExecutedActivities`方法正确调用API
2. 测试`parseBpmnXml`正确标记节点状态
3. 测试ProcessDiagram组件根据completedNodeIds正确渲染
4. 测试错误处理和降级逻辑

### Property-Based Tests

每个属性测试应该运行至少100次迭代，使用随机生成的测试数据。

**Property 1测试**：
```java
@RepeatedTest(100)
void executedActivityIdsMatchActualExecution() {
    // 生成随机流程实例
    String processInstanceId = generateRandomProcessInstance();
    
    // 获取实际执行的活动（从Flowable）
    List<String> actualExecuted = getActualExecutedActivities(processInstanceId);
    
    // 调用API获取执行的活动ID
    List<String> returnedIds = historyController
        .getExecutedActivityIds(processInstanceId)
        .getBody()
        .getData();
    
    // 验证集合相等
    assertEquals(new HashSet<>(actualExecuted), new HashSet<>(returnedIds));
}
```

**Property 2测试**：
```java
@RepeatedTest(100)
void rejectionPathHighlightingIsCorrect() {
    // 生成随机采购流程并在部门经理节点拒绝
    String processInstanceId = createPurchaseProcessAndRejectAtDeptManager();
    
    // 获取执行的活动ID
    List<String> executedIds = historyController
        .getExecutedActivityIds(processInstanceId)
        .getBody()
        .getData();
    
    // 验证只包含预期的节点
    List<String> expectedIds = Arrays.asList(
        "startEvent",
        "deptManagerApproval",
        "deptApprovalGateway",
        "rejectedEndEvent"
    );
    
    assertEquals(new HashSet<>(expectedIds), new HashSet<>(executedIds));
}
```

**Property 4测试**：
```typescript
test.each(generateRandomFormData(100))(
  'sub-form data persists after task completion',
  async (formData) => {
    // 创建包含子表单数据的任务
    const taskId = await createTaskWithSubForm(formData)
    
    // 完成任务
    await completeTask(taskId, formData)
    
    // 查询流程变量
    const variables = await getProcessVariables(taskId)
    
    // 验证子表单数据存在且完整
    expect(variables.purchase_items_form).toBeDefined()
    expect(variables.purchase_items_form).toHaveLength(formData.purchase_items_form.length)
    expect(variables.purchase_items_form).toEqual(formData.purchase_items_form)
  }
)
```

**Property 6测试**：
```typescript
test.each(generateRandomProcessDiagrams(100))(
  'node status is mutually exclusive',
  (diagram) => {
    const nodes = diagram.nodes
    
    for (const node of nodes) {
      const statusCount = [
        node.status === 'completed',
        node.status === 'current',
        node.status === 'pending'
      ].filter(Boolean).length
      
      expect(statusCount).toBe(1)
    }
  }
)
```

**Property 8测试**：
```java
@RepeatedTest(100)
void processVariableSerializationRoundTrip() {
    // 生成随机表单数据（包含子表单数组）
    Map<String, Object> originalData = generateRandomFormDataWithSubForm();
    
    // 序列化为流程变量
    Map<String, Object> variables = serializeToProcessVariables(originalData);
    
    // 反序列化
    Map<String, Object> deserializedData = deserializeFromProcessVariables(variables);
    
    // 验证往返一致性
    assertEquals(originalData, deserializedData);
    
    // 特别验证子表单数组
    List<?> originalSubForm = (List<?>) originalData.get("purchase_items_form");
    List<?> deserializedSubForm = (List<?>) deserializedData.get("purchase_items_form");
    assertEquals(originalSubForm.size(), deserializedSubForm.size());
    assertEquals(originalSubForm, deserializedSubForm);
}
```

### Integration Tests

1. **端到端流程测试**：
   - 创建采购流程
   - 部门经理拒绝
   - 验证流程图只高亮拒绝路径
   - 验证子表单数据仍然存在

2. **API集成测试**：
   - 测试前端调用后端API的完整流程
   - 验证数据格式和错误处理

### Manual Testing Checklist

- [ ] 创建采购申请，填写采购明细
- [ ] 部门经理审批拒绝
- [ ] 验证流程图只有4个节点变绿（开始、部门经理审批、网关、拒绝结束）
- [ ] 验证采购明细数据仍然显示
- [ ] 刷新页面，验证数据持久化
- [ ] 查看历史记录，验证数据完整

## Implementation Notes

### Phase 1: Backend API Enhancement
1. 在HistoryController中添加`getExecutedActivityIds`方法
2. 添加单元测试
3. 部署到测试环境

### Phase 2: Frontend Integration
1. 创建history API客户端
2. 修改task detail view的`parseBpmnXml`方法
3. 添加错误处理和降级逻辑
4. 添加单元测试

### Phase 3: Sub-form Data Fix
1. 调查子表单数据丢失的根本原因
2. 修复数据持久化逻辑
3. 添加数据完整性验证
4. 添加相关测试

### Phase 4: Testing and Validation
1. 运行所有单元测试和属性测试
2. 执行集成测试
3. 进行手动测试
4. 性能测试（确保历史查询不影响性能）

### Rollback Plan

如果新实现出现问题：
1. 前端可以通过feature flag切换回旧的顺序逻辑
2. 后端API向后兼容，不影响现有功能
3. 数据库无schema变更，可以安全回滚

### Performance Considerations

1. **历史查询缓存**：考虑缓存已完成流程的执行活动列表
2. **批量查询**：如果需要显示多个流程图，使用批量API
3. **索引优化**：确保Flowable历史表有适当的索引

### Security Considerations

1. **权限验证**：确保用户只能查询有权限的流程实例历史
2. **数据脱敏**：历史数据中的敏感信息应该脱敏
3. **审计日志**：记录历史数据访问日志
