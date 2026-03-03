# 13-procurement-workflow

基于数据库实际数据生成的初始化脚本。状态为 PUBLISHED，版本从 1.0.0 开始。

## 执行顺序

```bash
docker exec -it platform-postgres-dev psql -U platform_dev -d workflow_platform_dev << 'EOF'
\i /docker-entrypoint-initdb.d/13-procurement-workflow/00-create-function-unit.sql
\i /docker-entrypoint-initdb.d/13-procurement-workflow/01-create-tables.sql
\i /docker-entrypoint-initdb.d/13-procurement-workflow/02-create-bpmn-process.sql
\i /docker-entrypoint-initdb.d/13-procurement-workflow/03-form-table-bindings.sql
EOF
```

## 数据结构

### Function Unit
- Code: `PROCUREMENT_WORKFLOW`
- Name: `Procurement Workflow`
- Status: `PUBLISHED`
- Version: `1.0.0`

### Forms (3个)
| form_name | form_type | 说明 |
|-----------|-----------|------|
| Request Form | MAIN | 申请人填写（5个字段：request_number, request_date, title, description, budget） |
| Approval Form | MAIN | 审批人操作（1个字段：additional_information） |
| sub form | SUB | 子表单 |

### Actions (3个)
| action_name | action_type | icon | button_color |
|-------------|-------------|------|--------------|
| Submit Request | PROCESS_SUBMIT | - | - |
| Approve | APPROVE | Check | success |
| Reject | REJECT | Close | danger |

### Tables (4个)
| table_name | table_type | fields |
|------------|------------|--------|
| Request | MAIN | 11 (含 budget) |
| RequestItems | SUB | 9 (含 count) |
| ApprovalActions | ACTION | 9 |
| RequestAttachments | SUB | 9 |

### Form Table Bindings
| form | table | binding_type | binding_mode | fk | sort |
|------|-------|--------------|--------------|-----|------|
| Request Form | Request | PRIMARY | EDITABLE | - | 1 |
| Request Form | RequestItems | SUB | EDITABLE | request_id | 2 |
| Request Form | RequestAttachments | SUB | EDITABLE | request_id | 3 |
| Approval Form | Request | PRIMARY | READONLY | - | 1 |

**注意**: Request Form 的 `config_json.subForms` 的 key 是 `dw_form_table_bindings.id`（即 binding ID），
不是 `dw_table_definitions.id`（table ID）。这在 `03-form-table-bindings.sql` 中通过
`INSERT ... RETURNING id` 动态获取。

### BPMN 流程
```
Start → Submit Request → Total price > 10000?
                           → Yes → Manager Review → Approved? → Yes → Approved (end)
                           |                                   → No  → Rejected (end)
                           → No  → Auto Approved (end)
```
- Process ID: `ProcurementWorkflowProcess`
- Submit Request: formId=Request Form, actions=[Submit Request]
- Manager Review: formId=Approval Form, actions=[Approve, Reject]
