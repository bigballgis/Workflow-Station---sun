-- 更新表单配置为 form-create 格式
-- 请假申请表单
UPDATE dw_form_definitions 
SET config_json = '{
  "rule": [
    {
      "type": "input",
      "field": "applicant_name",
      "title": "申请人",
      "props": {"placeholder": "请输入申请人姓名", "disabled": true},
      "col": {"span": 12},
      "validate": [{"required": true, "message": "申请人不能为空"}]
    },
    {
      "type": "input",
      "field": "department_name",
      "title": "部门",
      "props": {"placeholder": "所属部门", "disabled": true},
      "col": {"span": 12}
    },
    {
      "type": "select",
      "field": "leave_type",
      "title": "请假类型",
      "props": {"placeholder": "请选择请假类型"},
      "col": {"span": 12},
      "options": [
        {"label": "年假", "value": "ANNUAL"},
        {"label": "事假", "value": "PERSONAL"},
        {"label": "病假", "value": "SICK"},
        {"label": "婚假", "value": "MARRIAGE"},
        {"label": "产假", "value": "MATERNITY"},
        {"label": "陪产假", "value": "PATERNITY"},
        {"label": "丧假", "value": "BEREAVEMENT"}
      ],
      "validate": [{"required": true, "message": "请选择请假类型"}]
    },
    {
      "type": "inputNumber",
      "field": "total_days",
      "title": "请假天数",
      "props": {"placeholder": "请假天数", "min": 0.5, "max": 365, "step": 0.5},
      "col": {"span": 12},
      "validate": [{"required": true, "message": "请输入请假天数"}]
    },
    {
      "type": "datePicker",
      "field": "start_date",
      "title": "开始日期",
      "props": {"placeholder": "请选择开始日期", "type": "date", "valueFormat": "YYYY-MM-DD"},
      "col": {"span": 12},
      "validate": [{"required": true, "message": "请选择开始日期"}]
    },
    {
      "type": "datePicker",
      "field": "end_date",
      "title": "结束日期",
      "props": {"placeholder": "请选择结束日期", "type": "date", "valueFormat": "YYYY-MM-DD"},
      "col": {"span": 12},
      "validate": [{"required": true, "message": "请选择结束日期"}]
    },
    {
      "type": "input",
      "field": "reason",
      "title": "请假原因",
      "props": {"placeholder": "请输入请假原因", "type": "textarea", "rows": 4},
      "col": {"span": 24},
      "validate": [{"required": true, "message": "请输入请假原因"}, {"min": 10, "message": "请假原因至少10个字符"}]
    }
  ],
  "options": {
    "labelWidth": "100px",
    "labelPosition": "right"
  }
}'::jsonb
WHERE form_name = '请假申请表单';

-- 审批表单
UPDATE dw_form_definitions 
SET config_json = '{
  "rule": [
    {
      "type": "radio",
      "field": "action",
      "title": "审批意见",
      "props": {},
      "col": {"span": 24},
      "options": [
        {"label": "同意", "value": "APPROVE"},
        {"label": "拒绝", "value": "REJECT"}
      ],
      "validate": [{"required": true, "message": "请选择审批意见"}]
    },
    {
      "type": "input",
      "field": "comment",
      "title": "审批备注",
      "props": {"placeholder": "请输入审批备注", "type": "textarea", "rows": 4},
      "col": {"span": 24}
    }
  ],
  "options": {
    "labelWidth": "100px",
    "labelPosition": "top"
  }
}'::jsonb
WHERE form_name = '审批表单';

-- 转办表单
UPDATE dw_form_definitions 
SET config_json = '{
  "rule": [
    {
      "type": "input",
      "field": "target_user",
      "title": "转办给",
      "props": {"placeholder": "请输入转办目标用户ID"},
      "col": {"span": 24},
      "validate": [{"required": true, "message": "请输入转办目标用户"}]
    },
    {
      "type": "input",
      "field": "reason",
      "title": "转办原因",
      "props": {"placeholder": "请输入转办原因", "type": "textarea", "rows": 3},
      "col": {"span": 24},
      "validate": [{"required": true, "message": "请输入转办原因"}]
    }
  ],
  "options": {
    "labelWidth": "100px",
    "labelPosition": "top"
  }
}'::jsonb
WHERE form_name = '转办表单';

-- 请假明细表单（子表单）
UPDATE dw_form_definitions 
SET config_json = '{
  "rule": [
    {
      "type": "datePicker",
      "field": "leave_date",
      "title": "日期",
      "props": {"placeholder": "请选择日期", "type": "date", "valueFormat": "YYYY-MM-DD"},
      "col": {"span": 8},
      "validate": [{"required": true, "message": "请选择日期"}]
    },
    {
      "type": "select",
      "field": "time_period",
      "title": "时段",
      "props": {"placeholder": "请选择时段"},
      "col": {"span": 8},
      "options": [
        {"label": "全天", "value": "FULL_DAY"},
        {"label": "上午", "value": "MORNING"},
        {"label": "下午", "value": "AFTERNOON"}
      ],
      "validate": [{"required": true, "message": "请选择时段"}]
    },
    {
      "type": "inputNumber",
      "field": "hours",
      "title": "小时",
      "props": {"placeholder": "小时数", "min": 0.5, "max": 8, "step": 0.5},
      "col": {"span": 8},
      "validate": [{"required": true, "message": "请输入小时数"}]
    }
  ],
  "options": {
    "labelWidth": "80px",
    "labelPosition": "top"
  }
}'::jsonb
WHERE form_name = '请假明细表单';
