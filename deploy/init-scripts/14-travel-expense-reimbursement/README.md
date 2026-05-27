# 14-travel-expense-reimbursement

差旅报销功能单元初始化脚本。包含 AI 发票识别（N8N + 豆包大模型）集成。状态为 PUBLISHED，版本从 1.0.0 开始。

## 执行顺序

```bash
docker exec -it platform-postgres-dev psql -U platform_dev -d workflow_platform_dev << 'EOF'
\i /docker-entrypoint-initdb.d/14-travel-expense-reimbursement/00-create-function-unit.sql
\i /docker-entrypoint-initdb.d/14-travel-expense-reimbursement/01-create-tables.sql
\i /docker-entrypoint-initdb.d/14-travel-expense-reimbursement/02-create-bpmn-process.sql
\i /docker-entrypoint-initdb.d/14-travel-expense-reimbursement/03-form-table-bindings.sql
EOF
```

## 数据结构

### Function Unit
- Code: `fu-20260403-a1b2c3`
- Name: `Travel Expense Reimbursement`
- Status: `PUBLISHED`
- Version: `1.0.0`

### Forms (2个)
| form_name | form_type | 说明 |
|-----------|-----------|------|
| Reimbursement Form | MAIN | 报销申请表单（9个字段：reimbursement_number, apply_date, applicant_name, department, travel_destination, travel_start_date, travel_end_date, travel_purpose, total_amount） |
| Approval Form | MAIN | 审批表单（1个字段：approval_comment） |

### Actions (4个)
| action_name | action_type | icon | button_color | 说明 |
|-------------|-------------|------|--------------|------|
| 提交报销 | PROCESS_SUBMIT | - | - | 提交报销申请启动审批流程 |
| AI 识别发票 | N8N_ACTION | - | - | 调用 N8N 工作流识别发票 |
| 审批通过 | APPROVE | Check | success | 审批通过 |
| 审批驳回 | REJECT | Close | danger | 审批驳回 |

### Tables (4个)
| table_name | table_type | fields | 说明 |
|------------|------------|--------|------|
| Reimbursement | MAIN | 15 | 报销主表（含 reimbursement_number, total_amount, status 等） |
| ExpenseItems | SUB | 7 | 费用明细子表（含 expense_type, amount, expense_date） |
| Invoices | SUB | 15 | 发票附件子表（含 file, invoice_type, invoice_amount, recognition_status） |
| ApprovalActions | ACTION | 8 | 审批操作记录表 |

### Form Table Bindings
| form | table | binding_type | binding_mode | fk | sort |
|------|-------|--------------|--------------|-----|------|
| Reimbursement Form | Reimbursement | PRIMARY | EDITABLE | - | 1 |
| Reimbursement Form | ExpenseItems | SUB | EDITABLE | reimbursement_id | 2 |
| Reimbursement Form | Invoices | SUB | EDITABLE | reimbursement_id | 3 |
| Approval Form | Reimbursement | PRIMARY | READONLY | - | 1 |

**注意**: Reimbursement Form 的 `config_json.subForms` 的 key 是 `dw_form_table_bindings.id`（即 binding ID），
不是 `dw_table_definitions.id`（table ID）。这在 `03-form-table-bindings.sql` 中通过
`INSERT ... RETURNING id` 动态获取。

### BPMN 流程
```
Start → 填写报销申请 → 主管审批 → 审批结果?
                                    → decision == 'yes' → 已通过 (end)
                                    → decision != 'yes' → 已驳回 (end)
```
- Process ID: `TravelExpenseReimbursementProcess`
- 填写报销申请: formId=Reimbursement Form, actions=[提交报销, AI 识别发票]
- 主管审批: formId=Approval Form, actions=[审批通过, 审批驳回]

## N8N 工作流配置指南

工作流模板文件：`deploy/n8n-workflows/travel-expense-invoice-recognition.json`

### 工作流节点链路

```
Webhook (POST /webhook/invoice-recognition)
  → Parse Input Files (提取 invoiceFiles 数组)
  → Download Invoice File (下载发票文件)
  → Convert to Base64 (转换为 base64)
  → Call Doubao LLM API (调用豆包视觉大模型)
  → Parse LLM Response (解析识别结果)
  → Aggregate Results (汇总所有发票数据)
  → Respond to Webhook (返回结构化 JSON)
```

### 预期输入格式

通过 Webhook 接收 POST 请求，请求体为包含发票文件 URL 列表的 JSON：

```json
POST /webhook/invoice-recognition
{
  "invoiceFiles": [
    {
      "fileUrl": "http://your-server/api/v1/upload/files/invoice1.jpg",
      "fileName": "taxi_receipt.jpg"
    },
    {
      "fileUrl": "http://your-server/api/v1/upload/files/invoice2.png",
      "fileName": "hotel_bill.png"
    }
  ]
}
```

### 预期输出格式

返回包含每张发票识别结果的 JSON，包含 invoice_type、invoice_amount、invoice_date、vendor_name 等字段：

```json
{
  "success": true,
  "summary": {
    "totalAmount": 1580.50,
    "totalTax": 94.83,
    "totalInvoices": 2,
    "successCount": 2,
    "failedCount": 0,
    "expenseByType": {
      "交通": { "count": 1, "amount": 280.00 },
      "住宿": { "count": 1, "amount": 1300.50 }
    }
  },
  "invoices": [
    {
      "fileIndex": 0,
      "fileName": "taxi_receipt.jpg",
      "recognition_status": "SUCCESS",
      "invoice_type": "交通",
      "invoice_amount": 280.00,
      "invoice_date": "2026-03-10",
      "vendor_name": "XX出租车公司",
      "invoice_number": "12345678",
      "tax_amount": 16.80,
      "description": "出租车费"
    }
  ],
  "expenseItems": [
    {
      "expense_type": "交通",
      "expense_date": "2026-03-10",
      "amount": 280.00,
      "description": "出租车费",
      "sort_order": 1
    }
  ]
}
```

其中 `expenseItems` 数组可直接映射到 ExpenseItems 子表单，`summary.totalAmount` 映射到主表的 `total_amount` 字段。

### 推荐节点配置

| 节点 | 类型 | 说明 |
|------|------|------|
| Webhook | n8n-nodes-base.webhook | POST 触发器，path=`invoice-recognition`，responseMode=`responseNode` |
| Parse Input Files | n8n-nodes-base.code | 提取 invoiceFiles 数组，每个文件输出为独立 item |
| Download Invoice File | n8n-nodes-base.httpRequest | GET 请求下载文件，responseFormat=`file` |
| Convert to Base64 | n8n-nodes-base.code | 将二进制文件转换为 base64 编码 |
| Call Doubao LLM API | n8n-nodes-base.httpRequest | POST 调用豆包视觉大模型（见下方） |
| Parse LLM Response | n8n-nodes-base.code | 解析 LLM JSON 响应，处理 markdown 代码块包裹 |
| Aggregate Results | n8n-nodes-base.code | 汇总所有发票数据，计算总金额和分类统计 |
| Respond to Webhook | n8n-nodes-base.respondToWebhook | 返回 JSON 结果，HTTP 200 |

### 豆包大模型 API 调用方式

通过 HTTP POST 请求调用豆包视觉大模型 API，发送 base64 编码的发票图片和识别提示词：

**API 地址**: `https://ark.cn-beijing.volces.com/api/v3/chat/completions`

**认证方式**: Header Auth，`Authorization: Bearer YOUR_DOUBAO_API_KEY`

**请求体结构**:
```json
{
  "model": "{{DOUBAO_MODEL_ID}}",
  "messages": [
    {
      "role": "system",
      "content": "你是一个专业的发票识别助手。请从发票图片中提取以下信息并以JSON格式返回：invoice_type, invoice_amount, invoice_date, vendor_name, invoice_number, tax_amount, description"
    },
    {
      "role": "user",
      "content": [
        {
          "type": "image_url",
          "image_url": {
            "url": "data:{mimeType};base64,{base64Image}"
          }
        },
        {
          "type": "text",
          "text": "请识别这张发票的内容，提取发票类型、金额、日期、商家名称、发票号码、税额和内容摘要。"
        }
      ]
    }
  ],
  "max_tokens": 1024,
  "temperature": 0.1
}
```

**环境变量**: 在 N8N Settings → Variables 中设置 `DOUBAO_MODEL_ID`（豆包视觉模型 endpoint ID，如 `ep-xxxxxxxxxx-xxxxx`）

**凭证配置**: 在 N8N 中创建 "Header Auth" 凭证，Header Name 为 `Authorization`，Header Value 为 `Bearer YOUR_DOUBAO_API_KEY`，然后在 "Call Doubao LLM API" 节点中引用该凭证。
