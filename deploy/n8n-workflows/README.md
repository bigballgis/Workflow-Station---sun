# N8N Workflow Templates

## Travel Expense Invoice Recognition

File: `travel-expense-invoice-recognition.json`

### Overview

This workflow receives invoice file URLs via webhook, downloads each file, sends it to the Doubao (豆包) Vision LLM for recognition, and returns structured invoice data with totals.

### Flow

```
Webhook (POST) → Parse Input → Download Files → Base64 Convert → Doubao LLM API → Parse Response → Aggregate → Respond
```

### Import Steps

1. Open N8N UI (default: http://localhost:5678)
2. Click "Add workflow" → "Import from file"
3. Select `travel-expense-invoice-recognition.json`
4. Configure credentials (see below)
5. Activate the workflow

### Credential Setup

Create an "Header Auth" credential in N8N:
- Name: `Doubao API Key`
- Header Name: `Authorization`
- Header Value: `Bearer YOUR_DOUBAO_API_KEY`

Then update the "Call Doubao LLM API" node to use this credential.

### Environment Variable

Set in N8N Settings → Variables:
- `DOUBAO_MODEL_ID`: Your Doubao vision model endpoint ID (e.g., `ep-xxxxxxxxxx-xxxxx`)

### Webhook URL

After activation, the webhook URL will be:
```
http://localhost:5678/webhook/invoice-recognition
```

Use this URL in the platform's N8N_ACTION config (`webhookUrl` field).

### Request Format

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

### Response Format

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

The `expenseItems` array can be directly mapped to the Expense_Items_Table sub-form, and `summary.totalAmount` maps to the `total_amount` field on the reimbursement form.
