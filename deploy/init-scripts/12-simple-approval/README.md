# 12-simple-approval

基于数据库实际数据生成的初始化脚本。状态为 DRAFT，版本从 1.0.0 开始。

## 执行顺序

```bash
docker exec -it platform-postgres-dev psql -U platform_dev -d workflow_platform_dev << 'EOF'
\i /docker-entrypoint-initdb.d/12-simple-approval/00-create-function-unit.sql
\i /docker-entrypoint-initdb.d/12-simple-approval/01-create-tables.sql
\i /docker-entrypoint-initdb.d/12-simple-approval/02-create-bpmn-process.sql
\i /docker-entrypoint-initdb.d/12-simple-approval/03-form-table-bindings.sql
EOF
```

## 数据结构

### Function Unit
- Code: `fu-20260403-a1b2c1`
- Name: `Simple Approval Workflow 12`
- Status: `DRAFT`
- Version: `1.0.0`

### Forms (3个)
| form_name | form_type | bound_table | 说明 |
|-----------|-----------|-------------|------|
| Request Form | MAIN | - | 申请人填写（4个字段：request_number, request_date, title, description） |
| Approval Form | MAIN | Request | 审批人操作（2个字段：approval_comments, status） |
| sub form | SUB | - | 子表单 |

### Actions (3个)
| action_name | action_type | icon | button_color |
|-------------|-------------|------|--------------|
| Submit Request | PROCESS_SUBMIT | - | - |
| Approve | APPROVE | Check | success |
| Reject | REJECT | Close | danger |

### Tables (5个)
| table_name | table_type | fields |
|------------|------------|--------|
| Request | MAIN | 10 |
| RequestItems | SUB | 8 |
| ApprovalActions | ACTION | 9 |
| RequestAttachments | RELATION | 9 |
| test | SUB | 2 |

### Form Table Bindings
| form | table | binding_type | binding_mode | fk | sort |
|------|-------|--------------|--------------|-----|------|
| Request Form | Request | PRIMARY | EDITABLE | - | 1 |
| Request Form | RequestItems | SUB | EDITABLE | request_id | 2 |
| Request Form | RequestAttachments | RELATED | EDITABLE | request_id | 3 |
| Approval Form | Request | PRIMARY | READONLY | - | 1 |

### BPMN 流程
```
Start → Submit Request → Manager Approval → Approved? → Yes → Approved (end)
                                                      → No  → Rejected (end)
```
- Process ID: `SimpleApprovalProcess12`
- Submit Request: formId=Request Form, actions=[Submit Request]
- Manager Approval: formId=Request Form (readOnly), actions=[Approve, Reject]
