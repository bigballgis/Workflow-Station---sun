# 功能单元SQL生成指南

## 概述
本指南用于在developer-workstation模块中生成功能单元的测试数据SQL。

## 关键规则

### 1. BPMN流程图必须包含图形信息
- BPMN XML必须包含`bpmndi:BPMNDiagram`元素，否则前端无法渲染流程图
- 必须包含的命名空间：
  - `xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI"`
  - `xmlns:dc="http://www.omg.org/spec/DD/20100524/DC"`
  - `xmlns:di="http://www.omg.org/spec/DD/20100524/DI"`
- 每个流程元素都需要对应的`bpmndi:BPMNShape`（节点）或`bpmndi:BPMNEdge`（连线）

### 2. BPMN XML必须Base64编码存储
- 代码使用`XmlEncodingUtil.encode()`进行编码
- 读取时使用`XmlEncodingUtil.smartDecode()`自动解码
- 这是为了避免数据库存储时的特殊字符转义问题

### 3. SQL文件分模块组织
由于Base64字符串很长，建议分模块生成SQL文件：
```
03-01-function-unit.sql  # 功能单元基本信息
03-02-tables.sql         # 表定义
03-03-fields-main.sql    # 主表字段
03-04-fields-sub.sql     # 子表/关联表/动作表字段
03-05-foreign-keys.sql   # 外键关系
03-06-forms.sql          # 表单定义和绑定
03-07-actions.sql        # 动作定义
03-08-process.sql        # 流程定义、版本、日志
```

### 4. 使用PowerShell生成长Base64字符串
由于AI工具对单行长字符串有限制，使用PowerShell生成包含Base64的SQL文件：
```powershell
$bpmnXml = @'
<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions ...>
  <!-- 完整的BPMN XML包含bpmndi:BPMNDiagram -->
</bpmn:definitions>
'@
$base64 = [Convert]::ToBase64String([System.Text.Encoding]::UTF8.GetBytes($bpmnXml))
$sql = "INSERT INTO dw_process_definitions ... '$base64' ..."
$sql | Out-File -FilePath "xxx.sql" -Encoding UTF8
```

### 5. 图标引用
- 图标存储在`dw_icons`表中
- 引用方式：`(SELECT id FROM dw_icons WHERE name = 'icon-name')`
- 可用图标查看：`deploy/init-scripts/03-test-workflow/02-sample-icons.sql`

### 6. 表类型枚举
- `MAIN` - 主表
- `SUB` - 子表
- `RELATION` - 关联表
- `ACTION` - 动作表

### 7. 表单类型枚举
- `MAIN` - 主表单
- `SUB` - 子表单
- `POPUP` - 弹出表单
- `ACTION` - 动作表单

### 8. 动作类型枚举
- `PROCESS_SUBMIT` - 提交流程
- `APPROVE` - 同意
- `REJECT` - 拒绝
- `TRANSFER` - 转办
- `ROLLBACK` - 回退
- `WITHDRAW` - 撤回
- `API_CALL` - API调用
- `SCRIPT` - 脚本执行

### 9. 节点绑定（表单和动作）
表单和动作与流程节点的绑定存储在BPMN XML的扩展属性中：

#### 命名空间（必须）
```xml
xmlns:custom="http://custom.bpmn.io/schema"
```

#### 表单绑定属性
在`userTask`节点的`extensionElements`中添加：
- `formId` - 表单ID（数字）
- `formName` - 表单名称（字符串）
- `formReadOnly` - 是否只读（"true"/"false"）

#### 动作绑定属性
在`userTask`节点的`extensionElements`中添加：
- `actionIds` - 动作ID数组（JSON格式，如`"[1,2,3]"`）

#### 处理人分配方式
在`userTask`节点的`extensionElements`中添加：
- `assigneeType` - 分配方式类型
- `assigneeLabel` - 分配方式显示标签
- `assigneeValue` - 分配值（仅user/group/expression类型需要）
- `candidateUsers` - 候选用户（逗号分隔）
- `candidateGroups` - 候选组（逗号分隔）

**分配方式类型枚举：**
- `initiator` - 流程发起人
- `manager` - 发起人的直属上级
- `entityManager` - 实体管理者
- `functionManager` - 职能管理者
- `eitherManager` - 实体或职能管理者（或签）
- `bothManagers` - 实体+职能管理者（会签）
- `departmentManager` - 部门主经理
- `departmentSecondaryManager` - 部门副经理
- `user` - 指定用户（需要assigneeValue）
- `group` - 指定部门/组（需要assigneeValue）
- `expression` - 表达式（需要assigneeValue）

#### 全局动作绑定
在`process`节点的`extensionElements`中添加：
- `globalActionIds` - 全局动作ID数组（JSON格式）

## 完整BPMN模板（含节点绑定）

```xml
<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions 
    xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" 
    xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI"
    xmlns:dc="http://www.omg.org/spec/DD/20100524/DC"
    xmlns:di="http://www.omg.org/spec/DD/20100524/DI"
    xmlns:custom="http://custom.bpmn.io/schema"
    id="Definitions_xxx" 
    targetNamespace="http://workflow.example.com/xxx">
  <bpmn:process id="Process_xxx" name="流程名称" isExecutable="true">
    <!-- 全局动作绑定 -->
    <bpmn:extensionElements>
      <custom:properties>
        <custom:property name="globalActionIds" value="[7,8]"/>
      </custom:properties>
    </bpmn:extensionElements>
    
    <bpmn:startEvent id="StartEvent_1" name="开始">
      <bpmn:outgoing>Flow_1</bpmn:outgoing>
    </bpmn:startEvent>
    
    <!-- 带表单和动作绑定的用户任务 -->
    <bpmn:userTask id="Task_Submit" name="提交申请">
      <bpmn:extensionElements>
        <custom:properties>
          <custom:property name="formId" value="1"/>
          <custom:property name="formName" value="申请表单"/>
          <custom:property name="formReadOnly" value="false"/>
          <custom:property name="actionIds" value="[1,6]"/>
          <custom:property name="assigneeType" value="initiator"/>
          <custom:property name="assigneeLabel" value="流程发起人"/>
        </custom:properties>
      </bpmn:extensionElements>
      <bpmn:incoming>Flow_1</bpmn:incoming>
      <bpmn:outgoing>Flow_2</bpmn:outgoing>
    </bpmn:userTask>
    
    <!-- 审批节点示例 -->
    <bpmn:userTask id="Task_Approval" name="主管审批">
      <bpmn:extensionElements>
        <custom:properties>
          <custom:property name="formId" value="3"/>
          <custom:property name="formName" value="审批表单"/>
          <custom:property name="formReadOnly" value="false"/>
          <custom:property name="actionIds" value="[2,3,4,5]"/>
          <custom:property name="assigneeType" value="manager"/>
          <custom:property name="assigneeLabel" value="发起人的直属上级"/>
        </custom:properties>
      </bpmn:extensionElements>
      <bpmn:incoming>Flow_2</bpmn:incoming>
      <bpmn:outgoing>Flow_3</bpmn:outgoing>
    </bpmn:userTask>
    
    <!-- 其他流程元素... -->
  </bpmn:process>
  
  <bpmndi:BPMNDiagram id="BPMNDiagram_1">
    <bpmndi:BPMNPlane id="BPMNPlane_1" bpmnElement="Process_xxx">
      <!-- 每个元素的图形定义 -->
      <bpmndi:BPMNShape id="StartEvent_1_di" bpmnElement="StartEvent_1">
        <dc:Bounds x="100" y="100" width="36" height="36"/>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Task_Submit_di" bpmnElement="Task_Submit">
        <dc:Bounds x="200" y="80" width="100" height="80"/>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNEdge id="Flow_1_di" bpmnElement="Flow_1">
        <di:waypoint x="136" y="118"/>
        <di:waypoint x="200" y="118"/>
      </bpmndi:BPMNEdge>
    </bpmndi:BPMNPlane>
  </bpmndi:BPMNDiagram>
</bpmn:definitions>
```

### 10. SQL文件执行顺序
SQL文件需要按顺序执行，因为存在外键依赖：
```
03-01-function-unit.sql  # 先创建功能单元
03-02-tables.sql         # 创建表定义（依赖功能单元）
03-03-fields-main.sql    # 创建主表字段（依赖表定义）
03-04-fields-sub.sql     # 创建子表字段（依赖表定义）
03-05-foreign-keys.sql   # 创建外键（依赖字段定义）
03-06-forms.sql          # 创建表单（依赖表定义）
03-07-actions.sql        # 创建动作（依赖功能单元）
03-08-process.sql        # 创建流程定义（依赖功能单元）
03-09-process-bindings.sql # 更新流程绑定（依赖表单和动作ID）
```

**注意**：`03-09-process-bindings.sql`必须在表单和动作创建后执行，因为绑定中引用的formId和actionIds需要是数据库中实际存在的ID。

## 数据库表归属
- `dw_*` 表属于 developer-workstation 模块
- `sys_*` 表属于 platform-security 模块
- `admin_*` 表属于 admin-center 模块

## 参考文件
- 实体定义：`backend/developer-workstation/src/main/java/com/developer/entity/`
- XML工具类：`backend/developer-workstation/src/main/java/com/developer/util/XmlEncodingUtil.java`
- 示例SQL：`deploy/init-scripts/03-test-workflow/`
