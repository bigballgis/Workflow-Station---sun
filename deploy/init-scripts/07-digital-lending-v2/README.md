# 数字贷款系统 V2 - 基于 AI 生成框架

## 概述

这是一个基于《AI 功能单元生成指导框架》创建的完整数字贷款系统示例。

## 功能特性

### 1. 完整的数据模型（7个表）

**主表（MAIN）：**
- Loan Application - 贷款申请主表

**子表（SUB）：**
- Applicant Information - 申请人信息
- Financial Information - 财务信息
- Collateral Details - 抵押物信息

**关联表（RELATION）：**
- Credit Check Results - 信用检查结果
- Approval History - 审批历史
- Documents - 文档附件

### 2. 多种表单类型（5个表单）

- **主表单：** 贷款申请表单、审批表单、放款表单
- **弹窗表单：** 信用检查表单、风险评估表单

### 3. 复杂的审批流程（8个节点）

1. 提交申请（发起人）
2. 文档验证（文档验证组）
3. 信用检查（信用审查组）
4. 风险评估（风险评估组）
5. 经理审批（实体管理者）
6. 高级经理审批（功能管理者）
7. 财务放款（财务组）
8. 流程结束


### 4. 丰富的业务动作（15个动作）

**流程控制：**
- Submit Application - 提交申请（PROCESS_SUBMIT）
- Withdraw Application - 撤回申请（WITHDRAW）

**审批操作：**
- Approve - 批准（APPROVE）
- Reject - 拒绝（REJECT）
- Request Additional Info - 请求补充信息（FORM_POPUP）

**专业操作：**
- Perform Credit Check - 执行信用检查（FORM_POPUP）
- View Credit Report - 查看信用报告（FORM_POPUP，只读）
- Assess Risk - 风险评估（FORM_POPUP）
- Verify Documents - 验证文档（APPROVE）

**计算操作：**
- Calculate EMI - 计算月供（API_CALL）

**查询操作：**
- Query Applications - 查询申请（API_CALL）

**放款操作：**
- Process Disbursement - 处理放款（APPROVE）
- Verify Account - 验证账户（API_CALL）

### 5. 多种任务分配方式

- **INITIATOR** - 流程发起人（提交申请）
- **VIRTUAL_GROUP** - 虚拟组（文档验证、信用检查、风险评估、财务）
- **ENTITY_MANAGER** - 实体管理者（部门经理审批）
- **FUNCTION_MANAGER** - 功能管理者（高级经理审批）


## 使用说明

### 前置条件

1. 数据库已初始化（运行了 00-schema 中的脚本）
2. 已创建必要的虚拟组：
   - DOCUMENT_VERIFIERS - 文档验证组
   - CREDIT_OFFICERS - 信用审查组
   - RISK_OFFICERS - 风险评估组
   - FINANCE_TEAM - 财务组

### 安装步骤

#### 1. 创建虚拟组（如果不存在）

```bash
# 使用 PowerShell
Get-Content 00-create-virtual-groups.sql | docker exec -i platform-postgres-dev psql -U platform_dev -d workflow_platform_dev
```

#### 2. 创建功能单元

```bash
# 使用 PowerShell
Get-Content 01-create-digital-lending-complete.sql | docker exec -i platform-postgres-dev psql -U platform_dev -d workflow_platform_dev
```

#### 3. 插入 BPMN 流程

```powershell
# 使用 PowerShell 脚本
.\02-insert-bpmn-process.ps1
```

#### 4. 绑定动作到流程节点

```bash
# 使用 PowerShell
Get-Content 03-bind-actions.sql | docker exec -i platform-postgres-dev psql -U platform_dev -d workflow_platform_dev
```

#### 5. 部署到开发者工作台

1. 登录开发者工作台：http://localhost:3002
2. 找到"数字贷款系统 V2"
3. 点击"部署"按钮
4. 等待部署完成


### 测试流程

#### 1. 提交贷款申请

1. 登录用户门户：http://localhost:3001
2. 选择"数字贷款系统 V2"
3. 填写贷款申请表单：
   - 贷款类型、金额、期限
   - 申请人信息
   - 财务信息
   - 抵押物信息（如适用）
4. 点击"Submit Application"提交

#### 2. 文档验证

1. 文档验证组成员登录
2. 在待办任务中找到申请
3. 点击"Verify Documents"验证文档
4. 选择"Approve"或"Reject"

#### 3. 信用检查

1. 信用审查组成员登录
2. 点击"Perform Credit Check"打开弹窗
3. 填写信用检查结果
4. 保存并继续

#### 4. 风险评估

1. 风险评估组成员登录
2. 点击"Assess Risk"打开弹窗
3. 评估风险等级
4. 选择继续或拒绝

#### 5. 经理审批

1. 部门经理登录
2. 查看完整申请信息
3. 点击"Approve"或"Reject"

#### 6. 高级经理审批

1. 高级经理登录
2. 最终审批
3. 点击"Final Approve"或"Final Reject"

#### 7. 财务放款

1. 财务组成员登录
2. 验证账户信息
3. 点击"Process Disbursement"处理放款


## 设计亮点

### 1. 表单弹窗操作

使用 FORM_POPUP 动作类型实现专业操作：

```sql
-- 信用检查弹窗
{
    "formId": 7,
    "formName": "Credit Check Form",
    "popupWidth": "800px",
    "popupTitle": "信用局检查",
    "requireComment": false,
    "allowedRoles": ["CREDIT_OFFICER"],
    "successMessage": "信用检查完成"
}
```

### 2. 多级审批流程

- 低风险贷款：经理审批即可
- 高风险贷款：需要高级经理审批
- 条件分支：根据风险评级自动路由

### 3. 权限控制

每个动作都可以配置允许的角色：

```sql
"allowedRoles": ["CREDIT_OFFICER", "RISK_MANAGER"]
```

### 4. API 集成

支持调用后端 API 进行计算和查询：

```sql
-- 计算月供
{
    "url": "/api/lending/calculate-emi",
    "method": "POST",
    "parameters": {
        "loanAmount": "{{loan_amount}}",
        "tenureMonths": "{{loan_tenure_months}}",
        "interestRate": "{{interest_rate}}"
    },
    "updateFields": {
        "emi_amount": "{{response.emiAmount}}"
    }
}
```


## 数据库表结构

### 主表：Loan Application

| 字段 | 类型 | 说明 |
|-----|------|------|
| id | BIGINT | 主键 |
| application_number | VARCHAR(50) | 申请编号 |
| application_date | TIMESTAMP | 申请日期 |
| loan_type | VARCHAR(50) | 贷款类型 |
| loan_amount | DECIMAL(15,2) | 申请金额 |
| loan_tenure_months | INTEGER | 贷款期限（月） |
| interest_rate | DECIMAL(5,2) | 年利率 |
| emi_amount | DECIMAL(15,2) | 月供金额 |
| loan_purpose | TEXT | 贷款用途 |
| status | VARCHAR(30) | 申请状态 |
| risk_rating | VARCHAR(20) | 风险评级 |
| credit_score | INTEGER | 信用评分 |

### 子表：Applicant Information

| 字段 | 类型 | 说明 |
|-----|------|------|
| id | BIGINT | 主键 |
| loan_application_id | BIGINT | 外键 |
| applicant_type | VARCHAR(20) | 主申请人/共同申请人 |
| full_name | VARCHAR(200) | 全名 |
| date_of_birth | DATE | 出生日期 |
| id_number | VARCHAR(50) | 身份证号 |
| mobile_number | VARCHAR(20) | 手机号 |
| email | VARCHAR(100) | 邮箱 |
| current_address | TEXT | 当前地址 |

### 子表：Financial Information

| 字段 | 类型 | 说明 |
|-----|------|------|
| id | BIGINT | 主键 |
| loan_application_id | BIGINT | 外键 |
| employment_type | VARCHAR(50) | 就业类型 |
| monthly_income | DECIMAL(15,2) | 月收入 |
| monthly_expenses | DECIMAL(15,2) | 月支出 |
| existing_loans | DECIMAL(15,2) | 现有贷款 |
| bank_name | VARCHAR(100) | 银行名称 |
| account_number | VARCHAR(50) | 账号 |


### 关联表：Credit Check Results

| 字段 | 类型 | 说明 |
|-----|------|------|
| id | BIGINT | 主键 |
| loan_application_id | BIGINT | 外键 |
| bureau_name | VARCHAR(100) | 信用局名称 |
| check_date | TIMESTAMP | 检查日期 |
| credit_score | INTEGER | 信用评分 |
| total_accounts | INTEGER | 总账户数 |
| delinquent_accounts | INTEGER | 逾期账户数 |
| payment_history | VARCHAR(20) | 还款历史评级 |

### 关联表：Approval History

| 字段 | 类型 | 说明 |
|-----|------|------|
| id | BIGINT | 主键 |
| loan_application_id | BIGINT | 外键 |
| stage_name | VARCHAR(100) | 审批阶段 |
| approver_name | VARCHAR(100) | 审批人 |
| approver_role | VARCHAR(50) | 审批人角色 |
| action | VARCHAR(30) | 操作 |
| decision | VARCHAR(20) | 决定 |
| action_date | TIMESTAMP | 操作日期 |
| comments | TEXT | 评论 |

## 流程图

```
[开始] 
  ↓
[提交申请] (发起人)
  ↓
[文档验证] (文档验证组)
  ↓
[通过?] ─No→ [结束-拒绝]
  ↓ Yes
[信用检查] (信用审查组)
  ↓
[风险评估] (风险评估组)
  ↓
[风险可接受?] ─High→ [结束-拒绝]
  ↓ Low/Medium    ↓ Need Info
[经理审批]        [返回提交]
  ↓
[批准?] ─No→ [结束-拒绝]
  ↓ Yes
[高级经理审批]
  ↓
[批准?] ─No→ [结束-拒绝]
  ↓ Yes
[财务放款]
  ↓
[结束-已放款]
```


## 与 AI 生成框架的对应关系

本示例完整展示了框架的所有阶段：

### ✅ 第一阶段：需求收集与分析
- 明确业务场景：数字贷款申请和审批
- 识别用户角色：申请人、验证员、审查员、经理等
- 定义核心实体：贷款申请、申请人、财务信息等

### ✅ 第二阶段：数据模型设计
- 1个主表（MAIN）
- 3个子表（SUB）
- 3个关联表（RELATION）
- 遵循命名规范和数据类型标准

### ✅ 第三阶段：表单设计
- 3个主表单（申请、审批、放款）
- 2个弹窗表单（信用检查、风险评估）
- 正确的表单-表绑定关系

### ✅ 第四阶段：流程设计
- 8个流程节点
- 3个决策网关
- 5种任务分配方式
- 完整的 BPMN 2.0 XML

### ✅ 第五阶段：动作设计
- 15个业务动作
- 6种动作类型
- 合理的按钮颜色和图标
- 权限控制配置

### ✅ 第六阶段：SQL 脚本生成
- 结构清晰的 PL/pgSQL 脚本
- 使用 RETURNING 子句
- 适当的进度提示
- 完整的错误处理

### ✅ 第七阶段：验证与优化
- 完整性检查通过
- 业务逻辑合理
- 技术规范符合标准


## 扩展建议

### 1. 增加更多业务规则

- 根据信用评分自动设置利率
- 根据贷款金额自动确定审批级别
- 自动计算贷款风险评分

### 2. 集成外部系统

- 对接真实的信用局 API
- 集成银行账户验证系统
- 连接电子签名服务

### 3. 增强用户体验

- 添加进度跟踪功能
- 实时消息通知
- 移动端支持

### 4. 数据分析

- 贷款申请统计报表
- 审批效率分析
- 风险趋势分析

## 常见问题

### Q: 如何修改审批流程？

A: 修改 BPMN XML 文件，然后重新执行 02-insert-bpmn-process.ps1

### Q: 如何添加新的动作？

A: 在 01-create-digital-lending-complete.sql 中添加新的 INSERT 语句，然后重新执行脚本

### Q: 如何修改表单字段？

A: 修改 dw_field_definitions 的 INSERT 语句，添加或删除字段定义

### Q: 如何测试不同的审批路径？

A: 在风险评估阶段设置不同的风险等级，系统会自动路由到不同的审批路径

## 参考资料

- [AI 功能单元生成指导框架](../../docs/AI_FUNCTION_UNIT_GENERATION_FRAMEWORK.md)
- [BPMN 2.0 规范](https://www.omg.org/spec/BPMN/2.0/)
- [PostgreSQL PL/pgSQL 文档](https://www.postgresql.org/docs/current/plpgsql.html)

## 版本历史

- **v1.0.0** (2026-02-06): 初始版本，基于 AI 生成框架创建

