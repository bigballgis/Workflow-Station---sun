-- =============================================================================
-- 15-platform-showcase: 公司推广 / 全栈能力演示功能单元（原「平台能力全功能演示」升级版）
-- code: fu-20260403-a1b2c4（fu-{yyyyMMdd}-{6位hex}）
-- 依赖: 无（首脚本）；开发组依赖 01-admin 已创建 sys_virtual_groups（vg-tech-leads）
-- 顺序: 00 → 01 → 02 → 03 → 04 → 05
-- 互补示例: fu-20260403-a1b2c5 会议多实例子流程、fu-20260403-a1b2c6 信贷长流程与阶段表单
-- =============================================================================

DO $main$
DECLARE
    v_fu_id            BIGINT;
    v_request_form_id  BIGINT;
    v_approval_form_id BIGINT;
    v_task_form_id     BIGINT;
    v_popup_form_id    BIGINT;
    v_submit_id        BIGINT;
    v_save_id          BIGINT;
BEGIN
    INSERT INTO dw_function_units (
        code, name, description, status,
        current_version, version, is_active, enabled,
        deployed_at, lock_version, created_by, created_at, updated_by, updated_at
    ) VALUES (
        'fu-20260403-a1b2c4',
        'Platform Showcase (Company Demo)',
        'Company demo: ' ||
        '1) Data: MAIN/SUB/RELATION/ACTION table types + dw_table_relations; ' ||
        '2) Forms: PROCESS/TASK/ACTION, subForms editable sub-tables, and stage bindings (05) aligned with BPMN userTask id; ' ||
        '3) Process: BPMN + Flowable DMN service task; ' ||
        '4) Actions: approve/transfer/delegate/composite/decision-table/N8N (frontendOutputMapping placeholder) action matrix; ' ||
        '5) Collaboration: assigned to TECH_LEADS (vg-tech-leads) for workspace permission demo. ' ||
        'Complementary demos: fu-20260403-a1b2c5 (multi-instance), fu-20260403-a1b2c6 (lending).',
        'PUBLISHED',
        '1.1.0', '1.0.0',
        true, true,
        CURRENT_TIMESTAMP, 0,
        'system', CURRENT_TIMESTAMP, 'system', CURRENT_TIMESTAMP
    )
    ON CONFLICT (code) DO UPDATE SET
        name            = EXCLUDED.name,
        description     = EXCLUDED.description,
        status          = EXCLUDED.status,
        current_version = EXCLUDED.current_version,
        version         = EXCLUDED.version,
        is_active       = EXCLUDED.is_active,
        enabled         = EXCLUDED.enabled,
        deployed_at     = COALESCE(dw_function_units.deployed_at, EXCLUDED.deployed_at),
        updated_by      = EXCLUDED.updated_by,
        updated_at      = CURRENT_TIMESTAMP
    RETURNING id INTO v_fu_id;

    RAISE NOTICE 'Function unit fu-20260403-a1b2c4 id=%', v_fu_id;

    -- 流程表单（每功能单元唯一）：仅发起人/Requestor 使用 PROCESS
    INSERT INTO dw_form_definitions (
        function_unit_id, form_name, form_type, description, config_json, created_at, updated_at
    ) VALUES (
        v_fu_id,
        'Showcase Request Form',
        'PROCESS',
        '发起：申请编号、标题、金额（供决策表输入）',
        '{"rule":[{"name":"ref_ps_app_no","type":"input","field":"app_no","props":{"maxlength":50,"placeholder":"申请编号"},"title":"申请编号","_fc_id":"id_ps_app_no","hidden":false,"display":true,"validate":[{"message":"必填","trigger":"blur","required":true}],"_fc_drag_tag":"input"},{"name":"ref_ps_title","type":"input","field":"title","props":{"maxlength":200,"placeholder":"标题"},"title":"标题","_fc_id":"id_ps_title","hidden":false,"display":true,"validate":[{"message":"必填","trigger":"blur","required":true}],"_fc_drag_tag":"input"},{"name":"ref_ps_amount","type":"inputNumber","field":"amount","props":{"precision":2,"placeholder":"金额"},"title":"金额","_fc_id":"id_ps_amount","hidden":false,"display":true,"validate":[{"message":"必填","trigger":"blur","required":true}],"_fc_drag_tag":"inputNumber"},{"name":"ref_ps_tier","type":"input","field":"tier","props":{"maxlength":20,"placeholder":"风险分层(决策输出)"},"title":"分层结果","_fc_id":"id_ps_tier","hidden":false,"display":true,"_fc_drag_tag":"input"}],"options":{"form":{"size":"default","inline":false,"labelWidth":"120px","labelPosition":"right","hideRequiredAsterisk":false},"resetBtn":{"show":false,"innerText":"Reset"},"submitBtn":{"show":true,"innerText":"Submit"}},"subForms":{}}'::jsonb,
        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
    )
    ON CONFLICT (function_unit_id, form_name) DO UPDATE SET
        form_type   = EXCLUDED.form_type,
        config_json = EXCLUDED.config_json,
        description = EXCLUDED.description,
        updated_at  = CURRENT_TIMESTAMP
    RETURNING id INTO v_request_form_id;

    -- 任务节点表单：审批等 UserTask 使用 TASK（非 PROCESS）
    INSERT INTO dw_form_definitions (
        function_unit_id, form_name, form_type, description, config_json, created_at, updated_at
    ) VALUES (
        v_fu_id,
        'Showcase Approval Form',
        'TASK',
        '审批任务表单：意见与结论',
        '{"rule":[{"name":"ref_ps_comment","type":"input","field":"approval_comment","props":{"rows":3,"type":"textarea","placeholder":"审批意见"},"title":"审批意见","_fc_id":"id_ps_comment","hidden":false,"display":true,"_fc_drag_tag":"input"},{"name":"ref_ps_dec","type":"input","field":"decision","props":{"maxlength":10,"placeholder":"yes/no"},"title":"结论","_fc_id":"id_ps_dec","hidden":false,"display":true,"_fc_drag_tag":"input"}],"options":{"form":{"size":"default","inline":false,"labelWidth":"120px","labelPosition":"right","hideRequiredAsterisk":false},"resetBtn":{"show":false,"innerText":"Reset"},"submitBtn":{"show":true,"innerText":"Submit"}},"subForms":{}}'::jsonb,
        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
    )
    ON CONFLICT (function_unit_id, form_name) DO UPDATE SET
        form_type   = EXCLUDED.form_type,
        config_json = EXCLUDED.config_json,
        description = EXCLUDED.description,
        updated_at  = CURRENT_TIMESTAMP
    RETURNING id INTO v_approval_form_id;

    INSERT INTO dw_form_definitions (
        function_unit_id, form_name, form_type, description, config_json, created_at, updated_at
    ) VALUES (
        v_fu_id,
        'Showcase Task Form',
        'TASK',
        '通用任务节点示例表单',
        '{"rule":[{"name":"ref_ps_task_note","type":"input","field":"task_note","props":{"maxlength":500,"placeholder":"任务备注"},"title":"任务备注","_fc_id":"id_ps_task_note","hidden":false,"display":true,"_fc_drag_tag":"input"}],"options":{"form":{"size":"default","inline":false,"labelWidth":"120px","labelPosition":"right","hideRequiredAsterisk":false},"resetBtn":{"show":false,"innerText":"Reset"},"submitBtn":{"show":true,"innerText":"Submit"}},"subForms":{}}'::jsonb,
        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
    )
    ON CONFLICT (function_unit_id, form_name) DO UPDATE SET
        form_type   = EXCLUDED.form_type,
        config_json = EXCLUDED.config_json,
        description = EXCLUDED.description,
        updated_at  = CURRENT_TIMESTAMP
    RETURNING id INTO v_task_form_id;

    INSERT INTO dw_form_definitions (
        function_unit_id, form_name, form_type, description, config_json, created_at, updated_at
    ) VALUES (
        v_fu_id,
        'Showcase Popup Form',
        'ACTION',
        '弹窗动作绑定的简短表单',
        '{"rule":[{"name":"ref_ps_popup","type":"input","field":"popup_note","props":{"rows":2,"type":"textarea","placeholder":"备注"},"title":"弹窗备注","_fc_id":"id_ps_popup","hidden":false,"display":true,"_fc_drag_tag":"input"}],"options":{"form":{"size":"default","inline":false,"labelWidth":"100px","labelPosition":"right","hideRequiredAsterisk":false},"resetBtn":{"show":false,"innerText":"Reset"},"submitBtn":{"show":true,"innerText":"OK"}},"subForms":{}}'::jsonb,
        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
    )
    ON CONFLICT (function_unit_id, form_name) DO UPDATE SET
        form_type   = EXCLUDED.form_type,
        config_json = EXCLUDED.config_json,
        description = EXCLUDED.description,
        updated_at  = CURRENT_TIMESTAMP
    RETURNING id INTO v_popup_form_id;

    -- DMN：金额分层，decision id = showcase_amount_tier（与 BPMN / DECISION_TABLE 一致）
    DELETE FROM dw_decision_definitions WHERE function_unit_id = v_fu_id;

    INSERT INTO dw_decision_definitions (
        function_unit_id, decision_key, decision_name, dmn_xml, hit_policy, description, created_at, updated_at
    ) VALUES (
        v_fu_id,
        'showcase_amount_tier',
        'Showcase Amount Tier',
        $dmn$<?xml version="1.0" encoding="UTF-8"?>
<definitions xmlns="https://www.omg.org/spec/DMN/20191111/MODEL/" xmlns:dmndi="https://www.omg.org/spec/DMN/20191111/DMNDI/" xmlns:dc="http://www.omg.org/spec/DMN/20180521/DC/" id="Definitions_Showcase" name="Showcase" namespace="http://workflow.platform/showcase" exporter="WorkflowStation" exporterVersion="1.0">
  <decision id="showcase_amount_tier" name="Amount Tier">
    <decisionTable id="DecisionTable_Showcase" hitPolicy="FIRST">
      <input id="Input_Amount" label="Amount">
        <inputExpression id="InputExpr_Amount" typeRef="number">
          <text>amount</text>
        </inputExpression>
      </input>
      <output id="Output_Tier" label="Tier" name="tier" typeRef="string" />
      <rule id="Rule_Low">
        <inputEntry id="UE_Low"><text>&lt; 1000</text></inputEntry>
        <outputEntry id="LE_Low"><text>"LOW"</text></outputEntry>
      </rule>
      <rule id="Rule_High">
        <inputEntry id="UE_High"><text>&gt;= 1000</text></inputEntry>
        <outputEntry id="LE_High"><text>"HIGH"</text></outputEntry>
      </rule>
    </decisionTable>
  </decision>
</definitions>$dmn$,
        'FIRST',
        '演示用决策表：按 amount 输出 tier',
        CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP
    );

    -- ---------- 动作（覆盖 ActionType 主要枚举）----------
    INSERT INTO dw_action_definitions (
        function_unit_id, action_name, action_type, config_json,
        icon, button_color, description, is_default, created_at, updated_at
    ) VALUES (
        v_fu_id, '提交申请', 'PROCESS_SUBMIT',
        '{"confirmMessage":"确认提交？","requireComment":false,"successMessage":"已提交"}'::jsonb,
        NULL, NULL, '流程提交', false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
    )
    ON CONFLICT (function_unit_id, action_name) DO UPDATE SET
        action_type = EXCLUDED.action_type, config_json = EXCLUDED.config_json,
        description = EXCLUDED.description, updated_at = CURRENT_TIMESTAMP
    RETURNING id INTO v_submit_id;

    INSERT INTO dw_action_definitions (
        function_unit_id, action_name, action_type, config_json,
        icon, button_color, description, is_default, created_at, updated_at
    ) VALUES (
        v_fu_id, '保存草稿', 'SAVE',
        '{}'::jsonb,
        'Document', NULL, '保存草稿', false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
    )
    ON CONFLICT (function_unit_id, action_name) DO UPDATE SET
        action_type = EXCLUDED.action_type, config_json = EXCLUDED.config_json,
        description = EXCLUDED.description, updated_at = CURRENT_TIMESTAMP
    RETURNING id INTO v_save_id;

    INSERT INTO dw_action_definitions (
        function_unit_id, action_name, action_type, config_json,
        icon, button_color, description, is_default, created_at, updated_at
    ) VALUES (
        v_fu_id, '流程驳回', 'PROCESS_REJECT',
        '{"confirmMessage":"确认驳回到上一环节？","requireComment":true}'::jsonb,
        NULL, NULL, '流程驳回', false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
    ) ON CONFLICT (function_unit_id, action_name) DO UPDATE SET
        action_type = EXCLUDED.action_type, config_json = EXCLUDED.config_json,
        description = EXCLUDED.description, updated_at = CURRENT_TIMESTAMP;

    INSERT INTO dw_action_definitions (
        function_unit_id, action_name, action_type, config_json,
        icon, button_color, description, is_default, created_at, updated_at
    ) VALUES (
        v_fu_id, '审批通过', 'APPROVE',
        '{"targetStatus":"APPROVED","confirmMessage":"确认通过？","requireComment":true,"successMessage":"已通过"}'::jsonb,
        'Check', 'success', '同意', false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
    ) ON CONFLICT (function_unit_id, action_name) DO UPDATE SET
        action_type = EXCLUDED.action_type, config_json = EXCLUDED.config_json,
        icon = EXCLUDED.icon, button_color = EXCLUDED.button_color,
        description = EXCLUDED.description, updated_at = CURRENT_TIMESTAMP;

    INSERT INTO dw_action_definitions (
        function_unit_id, action_name, action_type, config_json,
        icon, button_color, description, is_default, created_at, updated_at
    ) VALUES (
        v_fu_id, '审批拒绝', 'REJECT',
        '{"targetStatus":"REJECTED","requireReason":true,"confirmMessage":"确认拒绝？","requireComment":true}'::jsonb,
        'Close', 'danger', '拒绝', false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
    ) ON CONFLICT (function_unit_id, action_name) DO UPDATE SET
        action_type = EXCLUDED.action_type, config_json = EXCLUDED.config_json,
        icon = EXCLUDED.icon, button_color = EXCLUDED.button_color,
        description = EXCLUDED.description, updated_at = CURRENT_TIMESTAMP;

    INSERT INTO dw_action_definitions (
        function_unit_id, action_name, action_type, config_json,
        icon, button_color, description, is_default, created_at, updated_at
    ) VALUES (
        v_fu_id, '转办', 'TRANSFER',
        '{"confirmMessage":"确认转办？","requireComment":false,"successMessage":"已转办"}'::jsonb,
        'Switch', NULL, '转办', false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
    ) ON CONFLICT (function_unit_id, action_name) DO UPDATE SET
        action_type = EXCLUDED.action_type, config_json = EXCLUDED.config_json,
        description = EXCLUDED.description, updated_at = CURRENT_TIMESTAMP;

    INSERT INTO dw_action_definitions (
        function_unit_id, action_name, action_type, config_json,
        icon, button_color, description, is_default, created_at, updated_at
    ) VALUES (
        v_fu_id, '委派', 'DELEGATE',
        '{"confirmMessage":"确认委派？","requireComment":false,"successMessage":"已委派"}'::jsonb,
        'User', NULL, '委派', false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
    ) ON CONFLICT (function_unit_id, action_name) DO UPDATE SET
        action_type = EXCLUDED.action_type, config_json = EXCLUDED.config_json,
        description = EXCLUDED.description, updated_at = CURRENT_TIMESTAMP;

    INSERT INTO dw_action_definitions (
        function_unit_id, action_name, action_type, config_json,
        icon, button_color, description, is_default, created_at, updated_at
    ) VALUES (
        v_fu_id, '回退', 'ROLLBACK',
        '{"confirmMessage":"确认回退？","requireComment":true}'::jsonb,
        NULL, NULL, '回退', false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
    ) ON CONFLICT (function_unit_id, action_name) DO UPDATE SET
        action_type = EXCLUDED.action_type, config_json = EXCLUDED.config_json,
        description = EXCLUDED.description, updated_at = CURRENT_TIMESTAMP;

    INSERT INTO dw_action_definitions (
        function_unit_id, action_name, action_type, config_json,
        icon, button_color, description, is_default, created_at, updated_at
    ) VALUES (
        v_fu_id, '撤回', 'WITHDRAW',
        '{"confirmMessage":"确认撤回？","requireComment":false}'::jsonb,
        NULL, NULL, '撤回', false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
    ) ON CONFLICT (function_unit_id, action_name) DO UPDATE SET
        action_type = EXCLUDED.action_type, config_json = EXCLUDED.config_json,
        description = EXCLUDED.description, updated_at = CURRENT_TIMESTAMP;

    INSERT INTO dw_action_definitions (
        function_unit_id, action_name, action_type, config_json,
        icon, button_color, description, is_default, created_at, updated_at
    ) VALUES (
        v_fu_id, '取消', 'CANCEL',
        '{"confirmMessage":"确认取消？","requireComment":false}'::jsonb,
        NULL, NULL, '取消', false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
    ) ON CONFLICT (function_unit_id, action_name) DO UPDATE SET
        action_type = EXCLUDED.action_type, config_json = EXCLUDED.config_json,
        description = EXCLUDED.description, updated_at = CURRENT_TIMESTAMP;

    INSERT INTO dw_action_definitions (
        function_unit_id, action_name, action_type, config_json,
        icon, button_color, description, is_default, created_at, updated_at
    ) VALUES (
        v_fu_id, '导出', 'EXPORT',
        '{}'::jsonb,
        'Download', NULL, '导出数据', false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
    ) ON CONFLICT (function_unit_id, action_name) DO UPDATE SET
        action_type = EXCLUDED.action_type, config_json = EXCLUDED.config_json,
        description = EXCLUDED.description, updated_at = CURRENT_TIMESTAMP;

    INSERT INTO dw_action_definitions (
        function_unit_id, action_name, action_type, config_json,
        icon, button_color, description, is_default, created_at, updated_at
    ) VALUES (
        v_fu_id, 'HTTP调用', 'API_CALL',
        '{"url":"/api/v1/actuator/health","method":"GET","headers":"","body":""}'::jsonb,
        NULL, NULL, '示例 GET（需按环境调整）', false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
    ) ON CONFLICT (function_unit_id, action_name) DO UPDATE SET
        action_type = EXCLUDED.action_type, config_json = EXCLUDED.config_json,
        description = EXCLUDED.description, updated_at = CURRENT_TIMESTAMP;

    INSERT INTO dw_action_definitions (
        function_unit_id, action_name, action_type, config_json,
        icon, button_color, description, is_default, created_at, updated_at
    ) VALUES (
        v_fu_id, '打开弹窗表单', 'FORM_POPUP',
        jsonb_build_object(
            'formId', v_popup_form_id,
            'dialogTitle', '快速备注',
            'dialogWidth', '520px'
        ),
        NULL, NULL, '弹窗表单动作', false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
    ) ON CONFLICT (function_unit_id, action_name) DO UPDATE SET
        action_type = EXCLUDED.action_type, config_json = EXCLUDED.config_json,
        description = EXCLUDED.description, updated_at = CURRENT_TIMESTAMP;

    INSERT INTO dw_action_definitions (
        function_unit_id, action_name, action_type, config_json,
        icon, button_color, description, is_default, created_at, updated_at
    ) VALUES (
        v_fu_id, '演示N8N', 'N8N_ACTION',
        '{"n8nConfigId":"showcase-demo","n8nWorkflowId":"showcase-demo","webhookUrl":"","timeoutSeconds":60,"inputMapping":[],"outputMapping":[],"frontendOutputMapping":[]}'::jsonb,
        NULL, NULL, '与差旅报销脚本一致的扩展位：outputMapping 供后端，frontendOutputMapping 供前端自动回填（演示为空数组）', false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
    ) ON CONFLICT (function_unit_id, action_name) DO UPDATE SET
        action_type = EXCLUDED.action_type, config_json = EXCLUDED.config_json,
        description = EXCLUDED.description, updated_at = CURRENT_TIMESTAMP;

    INSERT INTO dw_action_definitions (
        function_unit_id, action_name, action_type, config_json,
        icon, button_color, description, is_default, created_at, updated_at
    ) VALUES (
        v_fu_id, '决策表评估', 'DECISION_TABLE',
        jsonb_build_object(
            'decisionKey', 'showcase_amount_tier',
            'inputMappings', jsonb_build_object('amount', 'form.amount'),
            'outputMappings', jsonb_build_object('tier', 'form.tier')
        ),
        NULL, NULL, '绑定本单元 DMN showcase_amount_tier', false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
    ) ON CONFLICT (function_unit_id, action_name) DO UPDATE SET
        action_type = EXCLUDED.action_type, config_json = EXCLUDED.config_json,
        description = EXCLUDED.description, updated_at = CURRENT_TIMESTAMP;

    INSERT INTO dw_action_definitions (
        function_unit_id, action_name, action_type, config_json,
        icon, button_color, description, is_default, created_at, updated_at
    ) VALUES (
        v_fu_id, '脚本动作', 'SCRIPT',
        '{"script":"// 演示占位，勿在生产使用"}'::jsonb,
        NULL, NULL, '脚本类动作占位', false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
    ) ON CONFLICT (function_unit_id, action_name) DO UPDATE SET
        action_type = EXCLUDED.action_type, config_json = EXCLUDED.config_json,
        description = EXCLUDED.description, updated_at = CURRENT_TIMESTAMP;

    INSERT INTO dw_action_definitions (
        function_unit_id, action_name, action_type, config_json,
        icon, button_color, description, is_default, created_at, updated_at
    ) VALUES (
        v_fu_id, '自定义脚本', 'CUSTOM_SCRIPT',
        '{"script":"return true;"}'::jsonb,
        NULL, NULL, '自定义脚本占位', false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
    ) ON CONFLICT (function_unit_id, action_name) DO UPDATE SET
        action_type = EXCLUDED.action_type, config_json = EXCLUDED.config_json,
        description = EXCLUDED.description, updated_at = CURRENT_TIMESTAMP;

    INSERT INTO dw_action_definitions (
        function_unit_id, action_name, action_type, config_json,
        icon, button_color, description, is_default, created_at, updated_at
    ) VALUES (
        v_fu_id, '组合动作', 'COMPOSITE',
        jsonb_build_object(
            'subActions', jsonb_build_array(v_submit_id, v_save_id),
            'executionOrder', 'sequential'
        ),
        NULL, NULL, '顺序执行：提交 + 保存草稿（演示配置）', false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
    ) ON CONFLICT (function_unit_id, action_name) DO UPDATE SET
        action_type = EXCLUDED.action_type, config_json = EXCLUDED.config_json,
        description = EXCLUDED.description, updated_at = CURRENT_TIMESTAMP;

    UPDATE dw_action_definitions ad
    SET config_json   = jsonb_set(
            COALESCE(ad.config_json, '{}'::jsonb),
            '{formId}',
            to_jsonb(v_popup_form_id),
            true
        ),
        updated_at      = CURRENT_TIMESTAMP
    WHERE ad.function_unit_id = v_fu_id
      AND ad.action_name = '打开弹窗表单';

    UPDATE dw_action_definitions
    SET config_json = jsonb_build_object(
            'subActions', jsonb_build_array(v_submit_id, v_save_id),
            'executionOrder', 'sequential'
        ),
        updated_at    = CURRENT_TIMESTAMP
    WHERE function_unit_id = v_fu_id
      AND action_name = '组合动作';

    -- 开发组：技术负责人虚拟组（与 FunctionUnitDevGroupAssignment.virtualGroupId = sys_virtual_groups.id 一致）
    DELETE FROM dw_function_unit_dev_groups WHERE function_unit_id = v_fu_id;
    INSERT INTO dw_function_unit_dev_groups (function_unit_id, virtual_group_id, created_at, created_by)
    VALUES (v_fu_id, 'vg-tech-leads', CURRENT_TIMESTAMP, 'system')
    ON CONFLICT (function_unit_id, virtual_group_id) DO NOTHING;

    RAISE NOTICE 'fu-20260403-a1b2c4 forms: request=%, approval=%, task=%, popup=%',
        v_request_form_id, v_approval_form_id, v_task_form_id, v_popup_form_id;
    RAISE NOTICE 'Next: 01-create-tables.sql';

END $main$;
