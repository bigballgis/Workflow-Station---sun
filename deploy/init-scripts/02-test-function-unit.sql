-- =====================================================
-- Test Function Unit Data
-- 创建一个完整的测试功能单元，覆盖所有模块
-- =====================================================

-- 1. 创建测试功能单元
INSERT INTO dw_function_units (id, name, description, status, current_version, created_by, created_at, updated_at)
VALUES (
    1,
    '请假申请',
    '员工请假申请功能单元，包含请假申请表单、审批流程、数据表定义等完整功能',
    'DRAFT',
    NULL,
    'admin',
    NOW(),
    NOW()
) ON CONFLICT (id) DO UPDATE SET
    name = EXCLUDED.name,
    description = EXCLUDED.description,
    updated_at = NOW();

-- 2. 创建表定义 - 请假申请主表
INSERT INTO dw_table_definitions (id, function_unit_id, table_name, table_type, description, created_at, updated_at)
VALUES (
    1,
    1,
    'leave_request',
    'MAIN',
    '请假申请主表，存储请假申请的基本信息',
    NOW(),
    NOW()
) ON CONFLICT (id) DO UPDATE SET
    table_name = EXCLUDED.table_name,
    updated_at = NOW();

-- 3. 创建字段定义 (字段名是 table_id)
INSERT INTO dw_field_definitions (id, table_id, field_name, data_type, length, nullable, is_primary_key, is_unique, description, sort_order)
VALUES
    (1, 1, 'id', 'BIGINT', NULL, false, true, true, '主键ID', 1),
    (2, 1, 'employee_id', 'VARCHAR', 50, false, false, false, '员工ID', 2),
    (3, 1, 'employee_name', 'VARCHAR', 100, false, false, false, '员工姓名', 3),
    (4, 1, 'department', 'VARCHAR', 100, false, false, false, '所属部门', 4),
    (5, 1, 'leave_type', 'VARCHAR', 20, false, false, false, '请假类型', 5),
    (6, 1, 'start_date', 'DATE', NULL, false, false, false, '开始日期', 6),
    (7, 1, 'end_date', 'DATE', NULL, false, false, false, '结束日期', 7),
    (8, 1, 'duration', 'DECIMAL', NULL, false, false, false, '请假天数', 8),
    (9, 1, 'reason', 'TEXT', NULL, true, false, false, '请假原因', 9),
    (10, 1, 'status', 'VARCHAR', 20, false, false, false, '状态', 10),
    (11, 1, 'approver_id', 'VARCHAR', 50, true, false, false, '审批人ID', 11),
    (12, 1, 'approver_name', 'VARCHAR', 100, true, false, false, '审批人姓名', 12),
    (13, 1, 'approve_time', 'TIMESTAMP', NULL, true, false, false, '审批时间', 13),
    (14, 1, 'approve_comment', 'TEXT', NULL, true, false, false, '审批意见', 14),
    (15, 1, 'created_at', 'TIMESTAMP', NULL, false, false, false, '创建时间', 15),
    (16, 1, 'updated_at', 'TIMESTAMP', NULL, true, false, false, '更新时间', 16)
ON CONFLICT (id) DO UPDATE SET
    field_name = EXCLUDED.field_name,
    sort_order = EXCLUDED.sort_order;

-- 4. 创建表定义 - 请假审批记录表
INSERT INTO dw_table_definitions (id, function_unit_id, table_name, table_type, description, created_at, updated_at)
VALUES (
    2,
    1,
    'leave_approval_history',
    'SUB',
    '请假审批历史记录表，记录每次审批的详细信息',
    NOW(),
    NOW()
) ON CONFLICT (id) DO UPDATE SET
    table_name = EXCLUDED.table_name,
    updated_at = NOW();

-- 审批记录表字段
INSERT INTO dw_field_definitions (id, table_id, field_name, data_type, length, nullable, is_primary_key, is_unique, description, sort_order)
VALUES
    (17, 2, 'id', 'BIGINT', NULL, false, true, true, '主键ID', 1),
    (18, 2, 'leave_request_id', 'BIGINT', NULL, false, false, false, '关联的请假申请ID', 2),
    (19, 2, 'approver_id', 'VARCHAR', 50, false, false, false, '审批人ID', 3),
    (20, 2, 'approver_name', 'VARCHAR', 100, false, false, false, '审批人姓名', 4),
    (21, 2, 'approval_action', 'VARCHAR', 20, false, false, false, '审批动作', 5),
    (22, 2, 'comment', 'TEXT', NULL, true, false, false, '审批意见', 6),
    (23, 2, 'created_at', 'TIMESTAMP', NULL, false, false, false, '审批时间', 7)
ON CONFLICT (id) DO UPDATE SET
    field_name = EXCLUDED.field_name,
    sort_order = EXCLUDED.sort_order;

-- 5. 创建表单定义 - 请假申请表单 (使用 form-create 格式)
INSERT INTO dw_form_definitions (id, function_unit_id, form_name, form_type, config_json, description, created_at, updated_at)
VALUES (
    1,
    1,
    '请假申请表单',
    'MAIN',
    '{
        "rule": [
            {
                "type": "input",
                "field": "employeeName",
                "title": "员工姓名",
                "props": {"placeholder": "请输入员工姓名"},
                "validate": [{"required": true, "message": "请输入员工姓名"}]
            },
            {
                "type": "input",
                "field": "department",
                "title": "所属部门",
                "props": {"placeholder": "请输入所属部门"},
                "validate": [{"required": true, "message": "请输入所属部门"}]
            },
            {
                "type": "select",
                "field": "leaveType",
                "title": "请假类型",
                "props": {"placeholder": "请选择请假类型"},
                "options": [
                    {"value": "ANNUAL", "label": "年假"},
                    {"value": "SICK", "label": "病假"},
                    {"value": "PERSONAL", "label": "事假"},
                    {"value": "MARRIAGE", "label": "婚假"},
                    {"value": "MATERNITY", "label": "产假"}
                ],
                "validate": [{"required": true, "message": "请选择请假类型"}]
            },
            {
                "type": "DatePicker",
                "field": "startDate",
                "title": "开始日期",
                "props": {"type": "date", "placeholder": "请选择开始日期"},
                "validate": [{"required": true, "message": "请选择开始日期"}]
            },
            {
                "type": "DatePicker",
                "field": "endDate",
                "title": "结束日期",
                "props": {"type": "date", "placeholder": "请选择结束日期"},
                "validate": [{"required": true, "message": "请选择结束日期"}]
            },
            {
                "type": "InputNumber",
                "field": "duration",
                "title": "请假天数",
                "props": {"min": 0.5, "step": 0.5, "precision": 1},
                "validate": [{"required": true, "message": "请输入请假天数"}]
            },
            {
                "type": "input",
                "field": "reason",
                "title": "请假原因",
                "props": {"type": "textarea", "rows": 4, "placeholder": "请输入请假原因"}
            }
        ],
        "options": {
            "submitBtn": {"show": true, "innerText": "提交申请"},
            "resetBtn": {"show": true, "innerText": "重置"}
        }
    }'::jsonb,
    '员工填写请假申请的主表单',
    NOW(),
    NOW()
) ON CONFLICT (id) DO UPDATE SET
    form_name = EXCLUDED.form_name,
    config_json = EXCLUDED.config_json,
    updated_at = NOW();

-- 6. 创建表单定义 - 审批表单
INSERT INTO dw_form_definitions (id, function_unit_id, form_name, form_type, config_json, description, created_at, updated_at)
VALUES (
    2,
    1,
    '请假审批表单',
    'SUB',
    '{
        "rule": [
            {
                "type": "radio",
                "field": "approveAction",
                "title": "审批结果",
                "options": [
                    {"value": "APPROVE", "label": "批准"},
                    {"value": "REJECT", "label": "拒绝"}
                ],
                "validate": [{"required": true, "message": "请选择审批结果"}]
            },
            {
                "type": "input",
                "field": "comment",
                "title": "审批意见",
                "props": {"type": "textarea", "rows": 3, "placeholder": "请输入审批意见"}
            }
        ],
        "options": {
            "submitBtn": {"show": true, "innerText": "提交审批"},
            "resetBtn": {"show": false}
        }
    }'::jsonb,
    '审批人审批请假申请的表单',
    NOW(),
    NOW()
) ON CONFLICT (id) DO UPDATE SET
    form_name = EXCLUDED.form_name,
    config_json = EXCLUDED.config_json,
    updated_at = NOW();

-- 7. 创建动作定义 (使用允许的 action_type 值)
INSERT INTO dw_action_definitions (id, function_unit_id, action_name, action_type, config_json, button_color, description, is_default, created_at, updated_at)
VALUES
    (1, 1, '批准', 'APPROVE', 
     '{"targetStatus": "APPROVED", "notification": {"enabled": true, "template": "leave_approved"}}'::jsonb,
     'success', '批准请假申请', true, NOW(), NOW()),
    
    (2, 1, '拒绝', 'REJECT',
     '{"targetStatus": "REJECTED", "notification": {"enabled": true, "template": "leave_rejected"}, "requireComment": true}'::jsonb,
     'danger', '拒绝请假申请', false, NOW(), NOW()),
    
    (3, 1, '转交', 'TRANSFER',
     '{"requireAssignee": true, "notification": {"enabled": true, "template": "leave_transferred"}}'::jsonb,
     'info', '转交给其他审批人', false, NOW(), NOW()),
    
    (4, 1, '委托', 'DELEGATE',
     '{"requireAssignee": true}'::jsonb,
     'warning', '委托他人审批', false, NOW(), NOW()),
    
    (5, 1, '撤回', 'WITHDRAW',
     '{"targetStatus": "CANCELLED", "allowedFromStatus": ["PENDING"]}'::jsonb,
     'warning', '撤回请假申请', false, NOW(), NOW()),
    
    (6, 1, '回退', 'ROLLBACK',
     '{"targetStep": "previous"}'::jsonb,
     NULL, '回退到上一步', false, NOW(), NOW()),
    
    (7, 1, '调用API', 'API_CALL',
     '{"url": "/api/leave/export", "method": "GET", "format": "xlsx"}'::jsonb,
     NULL, '导出请假记录', false, NOW(), NOW())
ON CONFLICT (id) DO UPDATE SET
    action_name = EXCLUDED.action_name,
    config_json = EXCLUDED.config_json,
    updated_at = NOW();

-- 8. 创建流程定义 (BPMN XML)
INSERT INTO dw_process_definitions (id, function_unit_id, bpmn_xml, created_at, updated_at)
VALUES (
    1,
    1,
    '<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" 
                  xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI" 
                  xmlns:dc="http://www.omg.org/spec/DD/20100524/DC" 
                  xmlns:di="http://www.omg.org/spec/DD/20100524/DI"
                  xmlns:camunda="http://camunda.org/schema/1.0/bpmn"
                  id="Definitions_LeaveRequest" 
                  targetNamespace="http://bpmn.io/schema/bpmn">
  <bpmn:process id="Process_LeaveRequest" name="请假申请流程" isExecutable="true">
    
    <bpmn:startEvent id="StartEvent_1" name="提交申请">
      <bpmn:outgoing>Flow_1</bpmn:outgoing>
    </bpmn:startEvent>
    
    <bpmn:userTask id="Task_FillApplication" name="填写请假申请" camunda:formKey="请假申请表单">
      <bpmn:incoming>Flow_1</bpmn:incoming>
      <bpmn:outgoing>Flow_2</bpmn:outgoing>
    </bpmn:userTask>
    
    <bpmn:exclusiveGateway id="Gateway_Duration" name="请假天数判断">
      <bpmn:incoming>Flow_2</bpmn:incoming>
      <bpmn:outgoing>Flow_3</bpmn:outgoing>
      <bpmn:outgoing>Flow_4</bpmn:outgoing>
    </bpmn:exclusiveGateway>
    
    <bpmn:userTask id="Task_ManagerApproval" name="直属领导审批" camunda:formKey="请假审批表单" camunda:assignee="${manager}">
      <bpmn:incoming>Flow_3</bpmn:incoming>
      <bpmn:incoming>Flow_4</bpmn:incoming>
      <bpmn:outgoing>Flow_5</bpmn:outgoing>
    </bpmn:userTask>
    
    <bpmn:exclusiveGateway id="Gateway_ManagerResult" name="领导审批结果">
      <bpmn:incoming>Flow_5</bpmn:incoming>
      <bpmn:outgoing>Flow_6</bpmn:outgoing>
      <bpmn:outgoing>Flow_7</bpmn:outgoing>
      <bpmn:outgoing>Flow_8</bpmn:outgoing>
    </bpmn:exclusiveGateway>
    
    <bpmn:userTask id="Task_HRApproval" name="HR审批" camunda:formKey="请假审批表单" camunda:candidateGroups="hr">
      <bpmn:incoming>Flow_6</bpmn:incoming>
      <bpmn:outgoing>Flow_9</bpmn:outgoing>
    </bpmn:userTask>
    
    <bpmn:exclusiveGateway id="Gateway_HRResult" name="HR审批结果">
      <bpmn:incoming>Flow_9</bpmn:incoming>
      <bpmn:outgoing>Flow_10</bpmn:outgoing>
      <bpmn:outgoing>Flow_11</bpmn:outgoing>
    </bpmn:exclusiveGateway>
    
    <bpmn:serviceTask id="Task_SendNotification" name="发送审批结果通知" camunda:delegateExpression="${notificationService}">
      <bpmn:incoming>Flow_7</bpmn:incoming>
      <bpmn:incoming>Flow_10</bpmn:incoming>
      <bpmn:outgoing>Flow_12</bpmn:outgoing>
    </bpmn:serviceTask>
    
    <bpmn:serviceTask id="Task_UpdateAttendance" name="更新考勤系统" camunda:delegateExpression="${attendanceService}">
      <bpmn:incoming>Flow_12</bpmn:incoming>
      <bpmn:outgoing>Flow_13</bpmn:outgoing>
    </bpmn:serviceTask>
    
    <bpmn:endEvent id="EndEvent_Approved" name="审批通过">
      <bpmn:incoming>Flow_13</bpmn:incoming>
    </bpmn:endEvent>
    
    <bpmn:endEvent id="EndEvent_Rejected" name="审批拒绝">
      <bpmn:incoming>Flow_8</bpmn:incoming>
      <bpmn:incoming>Flow_11</bpmn:incoming>
    </bpmn:endEvent>
    
    <bpmn:sequenceFlow id="Flow_1" sourceRef="StartEvent_1" targetRef="Task_FillApplication" />
    <bpmn:sequenceFlow id="Flow_2" sourceRef="Task_FillApplication" targetRef="Gateway_Duration" />
    <bpmn:sequenceFlow id="Flow_3" name="≤3天" sourceRef="Gateway_Duration" targetRef="Task_ManagerApproval" />
    <bpmn:sequenceFlow id="Flow_4" name=">3天" sourceRef="Gateway_Duration" targetRef="Task_ManagerApproval" />
    <bpmn:sequenceFlow id="Flow_5" sourceRef="Task_ManagerApproval" targetRef="Gateway_ManagerResult" />
    <bpmn:sequenceFlow id="Flow_6" name="批准且>3天" sourceRef="Gateway_ManagerResult" targetRef="Task_HRApproval" />
    <bpmn:sequenceFlow id="Flow_7" name="批准且≤3天" sourceRef="Gateway_ManagerResult" targetRef="Task_SendNotification" />
    <bpmn:sequenceFlow id="Flow_8" name="拒绝" sourceRef="Gateway_ManagerResult" targetRef="EndEvent_Rejected" />
    <bpmn:sequenceFlow id="Flow_9" sourceRef="Task_HRApproval" targetRef="Gateway_HRResult" />
    <bpmn:sequenceFlow id="Flow_10" name="批准" sourceRef="Gateway_HRResult" targetRef="Task_SendNotification" />
    <bpmn:sequenceFlow id="Flow_11" name="拒绝" sourceRef="Gateway_HRResult" targetRef="EndEvent_Rejected" />
    <bpmn:sequenceFlow id="Flow_12" sourceRef="Task_SendNotification" targetRef="Task_UpdateAttendance" />
    <bpmn:sequenceFlow id="Flow_13" sourceRef="Task_UpdateAttendance" targetRef="EndEvent_Approved" />
    
  </bpmn:process>
  
  <bpmndi:BPMNDiagram id="BPMNDiagram_1">
    <bpmndi:BPMNPlane id="BPMNPlane_1" bpmnElement="Process_LeaveRequest">
      <bpmndi:BPMNShape id="StartEvent_1_di" bpmnElement="StartEvent_1">
        <dc:Bounds x="152" y="202" width="36" height="36" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Task_FillApplication_di" bpmnElement="Task_FillApplication">
        <dc:Bounds x="240" y="180" width="100" height="80" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Gateway_Duration_di" bpmnElement="Gateway_Duration" isMarkerVisible="true">
        <dc:Bounds x="395" y="195" width="50" height="50" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Task_ManagerApproval_di" bpmnElement="Task_ManagerApproval">
        <dc:Bounds x="500" y="180" width="100" height="80" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Gateway_ManagerResult_di" bpmnElement="Gateway_ManagerResult" isMarkerVisible="true">
        <dc:Bounds x="655" y="195" width="50" height="50" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Task_HRApproval_di" bpmnElement="Task_HRApproval">
        <dc:Bounds x="760" y="80" width="100" height="80" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Gateway_HRResult_di" bpmnElement="Gateway_HRResult" isMarkerVisible="true">
        <dc:Bounds x="915" y="95" width="50" height="50" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Task_SendNotification_di" bpmnElement="Task_SendNotification">
        <dc:Bounds x="890" y="180" width="100" height="80" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="Task_UpdateAttendance_di" bpmnElement="Task_UpdateAttendance">
        <dc:Bounds x="1040" y="180" width="100" height="80" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="EndEvent_Approved_di" bpmnElement="EndEvent_Approved">
        <dc:Bounds x="1192" y="202" width="36" height="36" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="EndEvent_Rejected_di" bpmnElement="EndEvent_Rejected">
        <dc:Bounds x="792" y="302" width="36" height="36" />
      </bpmndi:BPMNShape>
    </bpmndi:BPMNPlane>
  </bpmndi:BPMNDiagram>
</bpmn:definitions>',
    NOW(),
    NOW()
) ON CONFLICT (id) DO UPDATE SET
    bpmn_xml = EXCLUDED.bpmn_xml,
    updated_at = NOW();

-- 重置序列
SELECT setval('dw_function_units_id_seq', GREATEST((SELECT MAX(id) FROM dw_function_units), 1));
SELECT setval('dw_table_definitions_id_seq', GREATEST((SELECT MAX(id) FROM dw_table_definitions), 1));
SELECT setval('dw_field_definitions_id_seq', GREATEST((SELECT MAX(id) FROM dw_field_definitions), 1));
SELECT setval('dw_form_definitions_id_seq', GREATEST((SELECT MAX(id) FROM dw_form_definitions), 1));
SELECT setval('dw_action_definitions_id_seq', GREATEST((SELECT MAX(id) FROM dw_action_definitions), 1));
SELECT setval('dw_process_definitions_id_seq', GREATEST((SELECT MAX(id) FROM dw_process_definitions), 1));

-- 输出创建结果
DO $$
BEGIN
    RAISE NOTICE '测试功能单元创建完成！';
    RAISE NOTICE '功能单元: 请假申请';
    RAISE NOTICE '- 表定义: 2个 (leave_request, leave_approval_history)';
    RAISE NOTICE '- 字段定义: 23个';
    RAISE NOTICE '- 表单定义: 2个 (请假申请表单, 请假审批表单)';
    RAISE NOTICE '- 动作定义: 7个';
    RAISE NOTICE '- 流程定义: 1个';
END $$;
