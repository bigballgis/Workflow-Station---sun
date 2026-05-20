-- =============================================================================
-- 13-procurement-workflow: Backfill assigneeType=INITIATOR on Submit Request + First Review
--
-- 根因：旧 BPMN 无 assigneeType，TaskAssignmentListener 不写 assignee，发起人待办为空。
-- 新环境请用已更新的 02-create-bpmn-process.sql；本脚本用于已部署目录/实例修复。
-- =============================================================================

-- 1) 目录 BPMN（sys_function_unit_contents，明文 XML）
UPDATE sys_function_unit_contents fuc
SET content_data = regexp_replace(
    fuc.content_data,
    '(<bpmn:userTask id="Task_SubmitRequest"[^>]*>\s*<bpmn:extensionElements>\s*<custom(?:_1)?:properties>)',
    E'\\1\n          <custom:property name="assigneeType" value="INITIATOR" />\n          <custom:property name="assigneeLabel" value="Process Initiator" />',
    'g'
)
FROM sys_function_units fu
WHERE fuc.function_unit_id = fu.id
  AND fu.code = 'fu-20260403-a1b2c2'
  AND fuc.content_type = 'PROCESS'
  AND fuc.content_data NOT LIKE '%assigneeType%'
  AND fuc.content_data LIKE '%Task_SubmitRequest%';

UPDATE sys_function_unit_contents fuc
SET content_data = regexp_replace(
    fuc.content_data,
    '(<bpmn:userTask id="Activity_1wf0eko"[^>]*>\s*<bpmn:extensionElements>\s*<custom(?:_1)?:properties>)',
    E'\\1\n          <custom:property name="assigneeType" value="INITIATOR" />\n          <custom:property name="assigneeLabel" value="Process Initiator" />',
    'g'
)
FROM sys_function_units fu
WHERE fuc.function_unit_id = fu.id
  AND fu.code = 'fu-20260403-a1b2c2'
  AND fuc.content_type = 'PROCESS'
  AND fuc.content_data NOT LIKE '%Activity_1wf0eko%assigneeType%'
  AND fuc.content_data LIKE '%Activity_1wf0eko%';

-- custom_1:property 命名空间（init 脚本格式）
UPDATE sys_function_unit_contents fuc
SET content_data = regexp_replace(
    fuc.content_data,
    '(<bpmn:userTask id="Task_SubmitRequest"[^>]*>\s*<bpmn:extensionElements>\s*<custom_1:properties>)',
    E'\\1\n          <custom_1:property name="assigneeType" value="INITIATOR"/>\n          <custom_1:property name="assigneeLabel" value="Process Initiator"/>',
    'g'
)
FROM sys_function_units fu
WHERE fuc.function_unit_id = fu.id
  AND fu.code = 'fu-20260403-a1b2c2'
  AND fuc.content_type = 'PROCESS'
  AND fuc.content_data LIKE '%custom_1:properties%'
  AND fuc.content_data LIKE '%Task_SubmitRequest%'
  AND fuc.content_data NOT LIKE '%assigneeType%';

UPDATE sys_function_unit_contents fuc
SET content_data = regexp_replace(
    fuc.content_data,
    '(<bpmn:userTask id="Activity_1wf0eko"[^>]*>\s*<bpmn:extensionElements>\s*<custom_1:properties>)',
    E'\\1\n          <custom_1:property name="assigneeType" value="INITIATOR"/>\n          <custom_1:property name="assigneeLabel" value="Process Initiator"/>',
    'g'
)
FROM sys_function_units fu
WHERE fuc.function_unit_id = fu.id
  AND fu.code = 'fu-20260403-a1b2c2'
  AND fuc.content_type = 'PROCESS'
  AND fuc.content_data LIKE '%custom_1:properties%'
  AND fuc.content_data LIKE '%Activity_1wf0eko%'
  AND fuc.content_data NOT LIKE '%Activity_1wf0eko%assigneeType%';

-- 2) 设计器 dw_process_definitions（base64 BPMN）
UPDATE dw_process_definitions pd
SET bpmn_xml = encode(
    convert_to(
        regexp_replace(
            convert_from(decode(pd.bpmn_xml, 'base64'), 'UTF8'),
            '(<bpmn:userTask id="Task_SubmitRequest"[^>]*>\s*<bpmn:extensionElements>\s*<custom(?:_1)?:properties>)',
            E'\\1\n          <custom:property name="assigneeType" value="INITIATOR" />\n          <custom:property name="assigneeLabel" value="Process Initiator" />',
            'g'
        ),
        'UTF8'
    ),
    'base64'
)
FROM dw_function_units fu
WHERE pd.function_unit_id = fu.id
  AND fu.code = 'fu-20260403-a1b2c2'
  AND convert_from(decode(pd.bpmn_xml, 'base64'), 'UTF8') NOT LIKE '%assigneeType%';

UPDATE dw_process_definitions pd
SET bpmn_xml = encode(
    convert_to(
        regexp_replace(
            convert_from(decode(pd.bpmn_xml, 'base64'), 'UTF8'),
            '(<bpmn:userTask id="Activity_1wf0eko"[^>]*>\s*<bpmn:extensionElements>\s*<custom(?:_1)?:properties>)',
            E'\\1\n          <custom:property name="assigneeType" value="INITIATOR" />\n          <custom:property name="assigneeLabel" value="Process Initiator" />',
            'g'
        ),
        'UTF8'
    ),
    'base64'
)
FROM dw_function_units fu
WHERE pd.function_unit_id = fu.id
  AND fu.code = 'fu-20260403-a1b2c2'
  AND convert_from(decode(pd.bpmn_xml, 'base64'), 'UTF8') LIKE '%Activity_1wf0eko%'
  AND convert_from(decode(pd.bpmn_xml, 'base64'), 'UTF8') NOT LIKE '%Activity_1wf0eko%assigneeType%';

-- 3) 修复已卡住实例：First Review 无 assignee 时写回流程发起人
UPDATE act_ru_task t
SET assignee_ = COALESCE(NULLIF(trim(pi.start_user_id_), ''), NULLIF(trim(v.text_), ''))
FROM act_hi_procinst pi
LEFT JOIN act_ru_variable v ON v.proc_inst_id_ = pi.proc_inst_id_ AND v.name_ = 'initiator'
JOIN up_process_instance up ON up.id = pi.proc_inst_id_
WHERE t.proc_inst_id_ = pi.proc_inst_id_
  AND t.task_def_key_ = 'Activity_1wf0eko'
  AND (t.assignee_ IS NULL OR trim(t.assignee_) = '')
  AND up.function_unit_code = 'fu-20260403-a1b2c2'
  AND up.status = 'RUNNING'
  AND COALESCE(NULLIF(trim(pi.start_user_id_), ''), NULLIF(trim(v.text_), '')) IS NOT NULL;

UPDATE up_process_instance up
SET current_assignee = u.display_name
FROM act_ru_task t
JOIN act_hi_procinst pi ON pi.proc_inst_id_ = t.proc_inst_id_
LEFT JOIN sys_users u ON u.id = t.assignee_
WHERE up.id = pi.proc_inst_id_
  AND up.function_unit_code = 'fu-20260403-a1b2c2'
  AND up.status = 'RUNNING'
  AND t.task_def_key_ = 'Activity_1wf0eko'
  AND t.assignee_ IS NOT NULL
  AND (up.current_assignee IS NULL OR trim(up.current_assignee) = '');
