# 13-procurement-workflow

基于数据库实际数据生成的初始化脚本。
状态为 PUBLISHED，current_version `1.0.6`。所有脚本均为幂等（ON CONFLICT / DELETE + INSERT）。

## 脚本说明

| 脚本 | 说明 |
|------|------|
| `00-create-function-unit.sql` | Function Unit + 4个 Forms + 8个 Actions |
| `01-create-tables.sql` | 5个 Table Definitions + 所有 Field Definitions |
| `02-create-bpmn-process.sql` | BPMN 流程定义（base64 XML） |
| `03-form-table-bindings.sql` | Form-Table 绑定 + subForms rule（30个控件） |
| `04-add-new-subtable-fields.sql` | ~~已废弃~~ 仅旧数据库迁移用，新环境无需执行 |

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
- Status: `PUBLISHED`, current_version: `1.0.6`, version: `1.0.0`

### Forms (4个)
| form_name | form_type | 说明 |
|-----------|-----------|------|
| Request Form | MAIN | 申请表（5个主表字段 + 2个 subTable） |
| Approval Form | MAIN | 审批表（1个字段：additional_information） |
| Review Form | MAIN | 审核表（4个字段） |
| sub form | SUB | 子表单 |

### Actions (8个)
| action_name | action_type | icon | button_color | is_default |
|-------------|-------------|------|--------------|------------|
| Submit Request | PROCESS_SUBMIT | - | - | false |
| Approve | APPROVE | Check | success | false |
| Reject | REJECT | Close | danger | false |
| Confirm | APPROVE | - | - | true |
| Transfer | TRANSFER | Switch | - | false |
| Delegate | DELEGATE | User | - | false |
| Approve First | APPROVE | - | - | true |
| Rejected First | REJECT | - | - | true |

### Tables (5个)
| table_name | table_type | fields |
|------------|------------|--------|
| Request | MAIN | 11 |
| RequestItems | SUB | 29（覆盖所有控件类型） |
| ApprovalActions | ACTION | 9 |
| RequestAttachments | SUB | 10 |
| Review Table | SUB | 3 |

### RequestItems 控件类型覆盖
input, inputNumber, textarea, select(单选/多选), switch, datePicker(date/datetime),
upload, timePicker(单/范围), radio, rate, colorPicker, elTreeSelect, tree, checkbox,
editor, signature, transfer, cascader, slider, password

### Form Table Bindings
| form | table | binding_type | binding_mode | fk | sort |
|------|-------|--------------|--------------|-----|------|
| Request Form | Request | PRIMARY | EDITABLE | - | 1 |
| Request Form | RequestItems | SUB | EDITABLE | request_id | 2 |
| Request Form | RequestAttachments | SUB | EDITABLE | request_id | 3 |
| Approval Form | Request | PRIMARY | READONLY | - | 1 |

> **注意**: `config_json.subForms` 的 key 是 `dw_form_table_bindings.id`（binding ID），
> 由 `03-form-table-bindings.sql` 通过 `INSERT ... RETURNING id` 动态获取。

### BPMN 流程
```
Start → Submit Request → First Review → Approve?
  → Yes → Second Review → Total price > 10000?
            → Yes → Manager Review → Approved? → Yes → Approved (end)
            |                                   → No  → Rejected (end)
            → No  → Auto Approved (end)
  → No  → Rejected (end)
```

| 节点 | formId | actions |
|------|--------|---------|
| Submit Request | Request Form | Submit Request |
| First Review | Request Form | Approve First, Rejected First |
| Second Review | Request Form | (无) |
| Manager Review | Approval Form | Approve, Reject, Transfer, Delegate |
