-- =====================================================
-- 数据库初始化脚本
-- 执行顺序：删除表 -> 创建表 -> 创建索引 -> 创建序列 -> 插入数据 -> 添加约束
-- 
-- 最后更新: 2026-01-22
-- 更新内容:
--   1. sys_users 表 CHECK 约束更新为 4 值 (ACTIVE, DISABLED, LOCKED, PENDING)
--   2. 移除 sys_users.department_id 列和相关索引
--   3. 移除重复索引 (idx_user_username, idx_user_email, idx_user_status)
--   4. 添加 sys_users 缺失索引 (idx_sys_users_entity_manager, idx_sys_users_function_manager)
--   5. dw_form_table_bindings 添加 uk_form_table_binding 唯一约束
-- =====================================================

SET session_replication_role = 'replica';
CREATE EXTENSION IF NOT EXISTS pgcrypto WITH SCHEMA public;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp" WITH SCHEMA public;

-- =====================================================
-- 第一步：删除所有表（如果存在）
-- =====================================================
DROP TABLE IF EXISTS public.wf_saga_transactions CASCADE;
DROP TABLE IF EXISTS public.wf_saga_steps CASCADE;
DROP TABLE IF EXISTS public.wf_process_variables CASCADE;
DROP TABLE IF EXISTS public.wf_extended_task_info CASCADE;
DROP TABLE IF EXISTS public.wf_exception_records CASCADE;
DROP TABLE IF EXISTS public.wf_audit_logs CASCADE;
DROP TABLE IF EXISTS public.up_user_preference CASCADE;
DROP TABLE IF EXISTS public.up_process_instance CASCADE;
DROP TABLE IF EXISTS public.up_process_history CASCADE;
DROP TABLE IF EXISTS public.up_process_draft CASCADE;
DROP TABLE IF EXISTS public.up_permission_request CASCADE;
DROP TABLE IF EXISTS public.up_notification_preference CASCADE;
DROP TABLE IF EXISTS public.up_favorite_process CASCADE;
DROP TABLE IF EXISTS public.up_delegation_rule CASCADE;
DROP TABLE IF EXISTS public.up_delegation_audit CASCADE;
DROP TABLE IF EXISTS public.up_dashboard_layout CASCADE;
DROP TABLE IF EXISTS public.sys_virtual_groups CASCADE;
DROP TABLE IF EXISTS public.sys_virtual_group_task_history CASCADE;
DROP TABLE IF EXISTS public.sys_virtual_group_roles CASCADE;
DROP TABLE IF EXISTS public.sys_virtual_group_members CASCADE;
DROP TABLE IF EXISTS public.sys_users CASCADE;
DROP TABLE IF EXISTS public.sys_user_roles CASCADE;
DROP TABLE IF EXISTS public.sys_user_preferences CASCADE;
DROP TABLE IF EXISTS public.sys_user_business_units CASCADE;
DROP TABLE IF EXISTS public.sys_user_business_unit_roles CASCADE;
DROP TABLE IF EXISTS public.sys_roles CASCADE;
DROP TABLE IF EXISTS public.sys_role_permissions CASCADE;
DROP TABLE IF EXISTS public.sys_role_assignments CASCADE;
DROP TABLE IF EXISTS public.sys_permissions CASCADE;
DROP TABLE IF EXISTS public.sys_permission_requests CASCADE;
DROP TABLE IF EXISTS public.sys_member_change_logs CASCADE;
DROP TABLE IF EXISTS public.sys_login_audit CASCADE;
DROP TABLE IF EXISTS public.sys_function_units CASCADE;
DROP TABLE IF EXISTS public.sys_function_unit_deployments CASCADE;
DROP TABLE IF EXISTS public.sys_function_unit_dependencies CASCADE;
DROP TABLE IF EXISTS public.sys_function_unit_contents CASCADE;
DROP TABLE IF EXISTS public.sys_function_unit_approvals CASCADE;
DROP TABLE IF EXISTS public.sys_function_unit_access CASCADE;
DROP TABLE IF EXISTS public.sys_dictionary_versions CASCADE;
DROP TABLE IF EXISTS public.sys_dictionary_items CASCADE;
DROP TABLE IF EXISTS public.sys_dictionary_data_sources CASCADE;
DROP TABLE IF EXISTS public.sys_dictionaries CASCADE;
DROP TABLE IF EXISTS public.sys_developer_role_permissions CASCADE;
DROP TABLE IF EXISTS public.sys_business_units CASCADE;
DROP TABLE IF EXISTS public.sys_business_unit_roles CASCADE;
DROP TABLE IF EXISTS public.sys_approvers CASCADE;
DROP TABLE IF EXISTS public.flw_ru_batch_part CASCADE;
DROP TABLE IF EXISTS public.flw_ru_batch CASCADE;
DROP TABLE IF EXISTS public.flw_event_resource CASCADE;
DROP TABLE IF EXISTS public.flw_event_deployment CASCADE;
DROP TABLE IF EXISTS public.flw_event_definition CASCADE;
DROP TABLE IF EXISTS public.flw_ev_databasechangeloglock CASCADE;
DROP TABLE IF EXISTS public.flw_ev_databasechangelog CASCADE;
DROP TABLE IF EXISTS public.flw_channel_definition CASCADE;
DROP TABLE IF EXISTS public.dw_versions CASCADE;
DROP TABLE IF EXISTS public.dw_table_definitions CASCADE;
DROP TABLE IF EXISTS public.dw_process_definitions CASCADE;
DROP TABLE IF EXISTS public.dw_operation_logs CASCADE;
DROP TABLE IF EXISTS public.dw_icons CASCADE;
DROP TABLE IF EXISTS public.dw_function_units CASCADE;
DROP TABLE IF EXISTS public.dw_form_table_bindings CASCADE;
DROP TABLE IF EXISTS public.dw_form_definitions CASCADE;
DROP TABLE IF EXISTS public.dw_foreign_keys CASCADE;
DROP TABLE IF EXISTS public.dw_field_definitions CASCADE;
DROP TABLE IF EXISTS public.dw_action_definitions CASCADE;
DROP TABLE IF EXISTS public.admin_system_logs CASCADE;
DROP TABLE IF EXISTS public.admin_system_configs CASCADE;
DROP TABLE IF EXISTS public.admin_security_policies CASCADE;
DROP TABLE IF EXISTS public.admin_permission_delegations CASCADE;
DROP TABLE IF EXISTS public.admin_permission_conflicts CASCADE;
DROP TABLE IF EXISTS public.admin_permission_change_history CASCADE;
DROP TABLE IF EXISTS public.admin_password_history CASCADE;
DROP TABLE IF EXISTS public.admin_log_retention_policies CASCADE;
DROP TABLE IF EXISTS public.admin_data_permission_rules CASCADE;
DROP TABLE IF EXISTS public.admin_config_history CASCADE;
DROP TABLE IF EXISTS public.admin_column_permissions CASCADE;
DROP TABLE IF EXISTS public.admin_audit_logs CASCADE;
DROP TABLE IF EXISTS public.admin_alerts CASCADE;
DROP TABLE IF EXISTS public.admin_alert_rules CASCADE;
DROP TABLE IF EXISTS public.act_ru_variable CASCADE;
DROP TABLE IF EXISTS public.act_ru_timer_job CASCADE;
DROP TABLE IF EXISTS public.act_ru_task CASCADE;
DROP TABLE IF EXISTS public.act_ru_suspended_job CASCADE;
DROP TABLE IF EXISTS public.act_ru_job CASCADE;
DROP TABLE IF EXISTS public.act_ru_identitylink CASCADE;
DROP TABLE IF EXISTS public.act_ru_history_job CASCADE;
DROP TABLE IF EXISTS public.act_ru_external_job CASCADE;
DROP TABLE IF EXISTS public.act_ru_execution CASCADE;
DROP TABLE IF EXISTS public.act_ru_event_subscr CASCADE;
DROP TABLE IF EXISTS public.act_ru_entitylink CASCADE;
DROP TABLE IF EXISTS public.act_ru_deadletter_job CASCADE;
DROP TABLE IF EXISTS public.act_ru_actinst CASCADE;
DROP TABLE IF EXISTS public.act_re_procdef CASCADE;
DROP TABLE IF EXISTS public.act_re_model CASCADE;
DROP TABLE IF EXISTS public.act_re_deployment CASCADE;
DROP TABLE IF EXISTS public.act_procdef_info CASCADE;
DROP TABLE IF EXISTS public.act_id_user CASCADE;
DROP TABLE IF EXISTS public.act_id_token CASCADE;
DROP TABLE IF EXISTS public.act_id_property CASCADE;
DROP TABLE IF EXISTS public.act_id_priv_mapping CASCADE;
DROP TABLE IF EXISTS public.act_id_priv CASCADE;
DROP TABLE IF EXISTS public.act_id_membership CASCADE;
DROP TABLE IF EXISTS public.act_id_info CASCADE;
DROP TABLE IF EXISTS public.act_id_group CASCADE;
DROP TABLE IF EXISTS public.act_id_bytearray CASCADE;
DROP TABLE IF EXISTS public.act_hi_varinst CASCADE;
DROP TABLE IF EXISTS public.act_hi_tsk_log CASCADE;
DROP TABLE IF EXISTS public.act_hi_taskinst CASCADE;
DROP TABLE IF EXISTS public.act_hi_procinst CASCADE;
DROP TABLE IF EXISTS public.act_hi_identitylink CASCADE;
DROP TABLE IF EXISTS public.act_hi_entitylink CASCADE;
DROP TABLE IF EXISTS public.act_hi_detail CASCADE;
DROP TABLE IF EXISTS public.act_hi_comment CASCADE;
DROP TABLE IF EXISTS public.act_hi_attachment CASCADE;
DROP TABLE IF EXISTS public.act_hi_actinst CASCADE;
DROP TABLE IF EXISTS public.act_ge_property CASCADE;
DROP TABLE IF EXISTS public.act_ge_bytearray CASCADE;
DROP TABLE IF EXISTS public.act_evt_log CASCADE;
DROP TABLE IF EXISTS public.act_dmn_hi_decision_execution CASCADE;
DROP TABLE IF EXISTS public.act_dmn_deployment_resource CASCADE;
DROP TABLE IF EXISTS public.act_dmn_deployment CASCADE;
DROP TABLE IF EXISTS public.act_dmn_decision CASCADE;
DROP TABLE IF EXISTS public.act_dmn_databasechangeloglock CASCADE;
DROP TABLE IF EXISTS public.act_dmn_databasechangelog CASCADE;
DROP TABLE IF EXISTS public.act_cmmn_ru_sentry_part_inst CASCADE;
DROP TABLE IF EXISTS public.act_cmmn_ru_plan_item_inst CASCADE;
DROP TABLE IF EXISTS public.act_cmmn_ru_mil_inst CASCADE;
DROP TABLE IF EXISTS public.act_cmmn_ru_case_inst CASCADE;
DROP TABLE IF EXISTS public.act_cmmn_hi_plan_item_inst CASCADE;
DROP TABLE IF EXISTS public.act_cmmn_hi_mil_inst CASCADE;
DROP TABLE IF EXISTS public.act_cmmn_hi_case_inst CASCADE;
DROP TABLE IF EXISTS public.act_cmmn_deployment_resource CASCADE;
DROP TABLE IF EXISTS public.act_cmmn_deployment CASCADE;
DROP TABLE IF EXISTS public.act_cmmn_databasechangeloglock CASCADE;
DROP TABLE IF EXISTS public.act_cmmn_databasechangelog CASCADE;
DROP TABLE IF EXISTS public.act_cmmn_casedef CASCADE;
DROP TABLE IF EXISTS public.act_app_deployment_resource CASCADE;
DROP TABLE IF EXISTS public.act_app_deployment CASCADE;
DROP TABLE IF EXISTS public.act_app_databasechangeloglock CASCADE;
DROP TABLE IF EXISTS public.act_app_databasechangelog CASCADE;
DROP TABLE IF EXISTS public.act_app_appdef CASCADE;

-- =====================================================
-- 第二步：创建所有表
-- =====================================================
CREATE TABLE IF NOT EXISTS public.act_app_appdef (
    id_ character varying(255) NOT NULL,
    rev_ integer NOT NULL,
    name_ character varying(255),
    key_ character varying(255) NOT NULL,
    version_ integer NOT NULL,
    category_ character varying(255),
    deployment_id_ character varying(255),
    resource_name_ character varying(4000),
    description_ character varying(4000),
    tenant_id_ character varying(255) DEFAULT ''::character varying
);
CREATE TABLE IF NOT EXISTS public.act_app_databasechangelog (
    id character varying(255) NOT NULL,
    author character varying(255) NOT NULL,
    filename character varying(255) NOT NULL,
    dateexecuted timestamp without time zone NOT NULL,
    orderexecuted integer NOT NULL,
    exectype character varying(10) NOT NULL,
    md5sum character varying(35),
    description character varying(255),
    comments character varying(255),
    tag character varying(255),
    liquibase character varying(20),
    contexts character varying(255),
    labels character varying(255),
    deployment_id character varying(10)
);
CREATE TABLE IF NOT EXISTS public.act_app_databasechangeloglock (
    id integer NOT NULL,
    locked boolean NOT NULL,
    lockgranted timestamp without time zone,
    lockedby character varying(255)
);
CREATE TABLE IF NOT EXISTS public.act_app_deployment (
    id_ character varying(255) NOT NULL,
    name_ character varying(255),
    category_ character varying(255),
    key_ character varying(255),
    deploy_time_ timestamp without time zone,
    tenant_id_ character varying(255) DEFAULT ''::character varying
);
CREATE TABLE IF NOT EXISTS public.act_app_deployment_resource (
    id_ character varying(255) NOT NULL,
    name_ character varying(255),
    deployment_id_ character varying(255),
    resource_bytes_ bytea
);
CREATE TABLE IF NOT EXISTS public.act_cmmn_casedef (
    id_ character varying(255) NOT NULL,
    rev_ integer NOT NULL,
    name_ character varying(255),
    key_ character varying(255) NOT NULL,
    version_ integer NOT NULL,
    category_ character varying(255),
    deployment_id_ character varying(255),
    resource_name_ character varying(4000),
    description_ character varying(4000),
    has_graphical_notation_ boolean,
    tenant_id_ character varying(255) DEFAULT ''::character varying,
    dgrm_resource_name_ character varying(4000),
    has_start_form_key_ boolean
);
CREATE TABLE IF NOT EXISTS public.act_cmmn_databasechangelog (
    id character varying(255) NOT NULL,
    author character varying(255) NOT NULL,
    filename character varying(255) NOT NULL,
    dateexecuted timestamp without time zone NOT NULL,
    orderexecuted integer NOT NULL,
    exectype character varying(10) NOT NULL,
    md5sum character varying(35),
    description character varying(255),
    comments character varying(255),
    tag character varying(255),
    liquibase character varying(20),
    contexts character varying(255),
    labels character varying(255),
    deployment_id character varying(10)
);
CREATE TABLE IF NOT EXISTS public.act_cmmn_databasechangeloglock (
    id integer NOT NULL,
    locked boolean NOT NULL,
    lockgranted timestamp without time zone,
    lockedby character varying(255)
);
CREATE TABLE IF NOT EXISTS public.act_cmmn_deployment (
    id_ character varying(255) NOT NULL,
    name_ character varying(255),
    category_ character varying(255),
    key_ character varying(255),
    deploy_time_ timestamp without time zone,
    parent_deployment_id_ character varying(255),
    tenant_id_ character varying(255) DEFAULT ''::character varying
);
CREATE TABLE IF NOT EXISTS public.act_cmmn_deployment_resource (
    id_ character varying(255) NOT NULL,
    name_ character varying(255),
    deployment_id_ character varying(255),
    resource_bytes_ bytea,
    generated_ boolean
);
CREATE TABLE IF NOT EXISTS public.act_cmmn_hi_case_inst (
    id_ character varying(255) NOT NULL,
    rev_ integer NOT NULL,
    business_key_ character varying(255),
    name_ character varying(255),
    parent_id_ character varying(255),
    case_def_id_ character varying(255),
    state_ character varying(255),
    start_time_ timestamp without time zone,
    end_time_ timestamp without time zone,
    start_user_id_ character varying(255),
    callback_id_ character varying(255),
    callback_type_ character varying(255),
    tenant_id_ character varying(255) DEFAULT ''::character varying,
    reference_id_ character varying(255),
    reference_type_ character varying(255),
    last_reactivation_time_ timestamp(3) without time zone,
    last_reactivation_user_id_ character varying(255),
    business_status_ character varying(255)
);
CREATE TABLE IF NOT EXISTS public.act_cmmn_hi_mil_inst (
    id_ character varying(255) NOT NULL,
    rev_ integer NOT NULL,
    name_ character varying(255) NOT NULL,
    time_stamp_ timestamp without time zone NOT NULL,
    case_inst_id_ character varying(255) NOT NULL,
    case_def_id_ character varying(255) NOT NULL,
    element_id_ character varying(255) NOT NULL,
    tenant_id_ character varying(255) DEFAULT ''::character varying
);
CREATE TABLE IF NOT EXISTS public.act_cmmn_hi_plan_item_inst (
    id_ character varying(255) NOT NULL,
    rev_ integer NOT NULL,
    name_ character varying(255),
    state_ character varying(255),
    case_def_id_ character varying(255),
    case_inst_id_ character varying(255),
    stage_inst_id_ character varying(255),
    is_stage_ boolean,
    element_id_ character varying(255),
    item_definition_id_ character varying(255),
    item_definition_type_ character varying(255),
    create_time_ timestamp without time zone,
    last_available_time_ timestamp without time zone,
    last_enabled_time_ timestamp without time zone,
    last_disabled_time_ timestamp without time zone,
    last_started_time_ timestamp without time zone,
    last_suspended_time_ timestamp without time zone,
    completed_time_ timestamp without time zone,
    occurred_time_ timestamp without time zone,
    terminated_time_ timestamp without time zone,
    exit_time_ timestamp without time zone,
    ended_time_ timestamp without time zone,
    last_updated_time_ timestamp without time zone,
    start_user_id_ character varying(255),
    reference_id_ character varying(255),
    reference_type_ character varying(255),
    tenant_id_ character varying(255) DEFAULT ''::character varying,
    entry_criterion_id_ character varying(255),
    exit_criterion_id_ character varying(255),
    show_in_overview_ boolean,
    extra_value_ character varying(255),
    derived_case_def_id_ character varying(255),
    last_unavailable_time_ timestamp(3) without time zone
);
CREATE TABLE IF NOT EXISTS public.act_cmmn_ru_case_inst (
    id_ character varying(255) NOT NULL,
    rev_ integer NOT NULL,
    business_key_ character varying(255),
    name_ character varying(255),
    parent_id_ character varying(255),
    case_def_id_ character varying(255),
    state_ character varying(255),
    start_time_ timestamp without time zone,
    start_user_id_ character varying(255),
    callback_id_ character varying(255),
    callback_type_ character varying(255),
    tenant_id_ character varying(255) DEFAULT ''::character varying,
    lock_time_ timestamp without time zone,
    is_completeable_ boolean,
    reference_id_ character varying(255),
    reference_type_ character varying(255),
    lock_owner_ character varying(255),
    last_reactivation_time_ timestamp(3) without time zone,
    last_reactivation_user_id_ character varying(255),
    business_status_ character varying(255)
);
CREATE TABLE IF NOT EXISTS public.act_cmmn_ru_mil_inst (
    id_ character varying(255) NOT NULL,
    name_ character varying(255) NOT NULL,
    time_stamp_ timestamp without time zone NOT NULL,
    case_inst_id_ character varying(255) NOT NULL,
    case_def_id_ character varying(255) NOT NULL,
    element_id_ character varying(255) NOT NULL,
    tenant_id_ character varying(255) DEFAULT ''::character varying
);
CREATE TABLE IF NOT EXISTS public.act_cmmn_ru_plan_item_inst (
    id_ character varying(255) NOT NULL,
    rev_ integer NOT NULL,
    case_def_id_ character varying(255),
    case_inst_id_ character varying(255),
    stage_inst_id_ character varying(255),
    is_stage_ boolean,
    element_id_ character varying(255),
    name_ character varying(255),
    state_ character varying(255),
    create_time_ timestamp without time zone,
    start_user_id_ character varying(255),
    reference_id_ character varying(255),
    reference_type_ character varying(255),
    tenant_id_ character varying(255) DEFAULT ''::character varying,
    item_definition_id_ character varying(255),
    item_definition_type_ character varying(255),
    is_completeable_ boolean,
    is_count_enabled_ boolean,
    var_count_ integer,
    sentry_part_inst_count_ integer,
    last_available_time_ timestamp(3) without time zone,
    last_enabled_time_ timestamp(3) without time zone,
    last_disabled_time_ timestamp(3) without time zone,
    last_started_time_ timestamp(3) without time zone,
    last_suspended_time_ timestamp(3) without time zone,
    completed_time_ timestamp(3) without time zone,
    occurred_time_ timestamp(3) without time zone,
    terminated_time_ timestamp(3) without time zone,
    exit_time_ timestamp(3) without time zone,
    ended_time_ timestamp(3) without time zone,
    entry_criterion_id_ character varying(255),
    exit_criterion_id_ character varying(255),
    extra_value_ character varying(255),
    derived_case_def_id_ character varying(255),
    last_unavailable_time_ timestamp(3) without time zone
);
CREATE TABLE IF NOT EXISTS public.act_cmmn_ru_sentry_part_inst (
    id_ character varying(255) NOT NULL,
    rev_ integer NOT NULL,
    case_def_id_ character varying(255),
    case_inst_id_ character varying(255),
    plan_item_inst_id_ character varying(255),
    on_part_id_ character varying(255),
    if_part_id_ character varying(255),
    time_stamp_ timestamp without time zone
);
CREATE TABLE IF NOT EXISTS public.act_dmn_databasechangelog (
    id character varying(255) NOT NULL,
    author character varying(255) NOT NULL,
    filename character varying(255) NOT NULL,
    dateexecuted timestamp without time zone NOT NULL,
    orderexecuted integer NOT NULL,
    exectype character varying(10) NOT NULL,
    md5sum character varying(35),
    description character varying(255),
    comments character varying(255),
    tag character varying(255),
    liquibase character varying(20),
    contexts character varying(255),
    labels character varying(255),
    deployment_id character varying(10)
);
CREATE TABLE IF NOT EXISTS public.act_dmn_databasechangeloglock (
    id integer NOT NULL,
    locked boolean NOT NULL,
    lockgranted timestamp without time zone,
    lockedby character varying(255)
);
CREATE TABLE IF NOT EXISTS public.act_dmn_decision (
    id_ character varying(255) NOT NULL,
    name_ character varying(255),
    version_ integer,
    key_ character varying(255),
    category_ character varying(255),
    deployment_id_ character varying(255),
    tenant_id_ character varying(255),
    resource_name_ character varying(255),
    description_ character varying(255),
    decision_type_ character varying(255)
);
CREATE TABLE IF NOT EXISTS public.act_dmn_deployment (
    id_ character varying(255) NOT NULL,
    name_ character varying(255),
    category_ character varying(255),
    deploy_time_ timestamp without time zone,
    tenant_id_ character varying(255),
    parent_deployment_id_ character varying(255)
);
CREATE TABLE IF NOT EXISTS public.act_dmn_deployment_resource (
    id_ character varying(255) NOT NULL,
    name_ character varying(255),
    deployment_id_ character varying(255),
    resource_bytes_ bytea
);
CREATE TABLE IF NOT EXISTS public.act_dmn_hi_decision_execution (
    id_ character varying(255) NOT NULL,
    decision_definition_id_ character varying(255),
    deployment_id_ character varying(255),
    start_time_ timestamp without time zone,
    end_time_ timestamp without time zone,
    instance_id_ character varying(255),
    execution_id_ character varying(255),
    activity_id_ character varying(255),
    failed_ boolean DEFAULT false,
    tenant_id_ character varying(255),
    execution_json_ text,
    scope_type_ character varying(255)
);
CREATE TABLE IF NOT EXISTS public.act_evt_log (
    log_nr_ integer NOT NULL,
    type_ character varying(64),
    proc_def_id_ character varying(64),
    proc_inst_id_ character varying(64),
    execution_id_ character varying(64),
    task_id_ character varying(64),
    time_stamp_ timestamp without time zone NOT NULL,
    user_id_ character varying(255),
    data_ bytea,
    lock_owner_ character varying(255),
    lock_time_ timestamp without time zone,
    is_processed_ smallint DEFAULT 0
);
CREATE TABLE IF NOT EXISTS public.act_ge_bytearray (
    id_ character varying(64) NOT NULL,
    rev_ integer,
    name_ character varying(255),
    deployment_id_ character varying(64),
    bytes_ bytea,
    generated_ boolean
);
CREATE TABLE IF NOT EXISTS public.act_ge_property (
    name_ character varying(64) NOT NULL,
    value_ character varying(300),
    rev_ integer
);
CREATE TABLE IF NOT EXISTS public.act_hi_actinst (
    id_ character varying(64) NOT NULL,
    rev_ integer DEFAULT 1,
    proc_def_id_ character varying(64) NOT NULL,
    proc_inst_id_ character varying(64) NOT NULL,
    execution_id_ character varying(64) NOT NULL,
    act_id_ character varying(255) NOT NULL,
    task_id_ character varying(64),
    call_proc_inst_id_ character varying(64),
    act_name_ character varying(255),
    act_type_ character varying(255) NOT NULL,
    assignee_ character varying(255),
    start_time_ timestamp without time zone NOT NULL,
    end_time_ timestamp without time zone,
    transaction_order_ integer,
    duration_ bigint,
    delete_reason_ character varying(4000),
    tenant_id_ character varying(255) DEFAULT ''::character varying
);
CREATE TABLE IF NOT EXISTS public.act_hi_attachment (
    id_ character varying(64) NOT NULL,
    rev_ integer,
    user_id_ character varying(255),
    name_ character varying(255),
    description_ character varying(4000),
    type_ character varying(255),
    task_id_ character varying(64),
    proc_inst_id_ character varying(64),
    url_ character varying(4000),
    content_id_ character varying(64),
    time_ timestamp without time zone
);
CREATE TABLE IF NOT EXISTS public.act_hi_comment (
    id_ character varying(64) NOT NULL,
    type_ character varying(255),
    time_ timestamp without time zone NOT NULL,
    user_id_ character varying(255),
    task_id_ character varying(64),
    proc_inst_id_ character varying(64),
    action_ character varying(255),
    message_ character varying(4000),
    full_msg_ bytea
);
CREATE TABLE IF NOT EXISTS public.act_hi_detail (
    id_ character varying(64) NOT NULL,
    type_ character varying(255) NOT NULL,
    proc_inst_id_ character varying(64),
    execution_id_ character varying(64),
    task_id_ character varying(64),
    act_inst_id_ character varying(64),
    name_ character varying(255) NOT NULL,
    var_type_ character varying(64),
    rev_ integer,
    time_ timestamp without time zone NOT NULL,
    bytearray_id_ character varying(64),
    double_ double precision,
    long_ bigint,
    text_ character varying(4000),
    text2_ character varying(4000)
);
CREATE TABLE IF NOT EXISTS public.act_hi_entitylink (
    id_ character varying(64) NOT NULL,
    link_type_ character varying(255),
    create_time_ timestamp without time zone,
    scope_id_ character varying(255),
    sub_scope_id_ character varying(255),
    scope_type_ character varying(255),
    scope_definition_id_ character varying(255),
    parent_element_id_ character varying(255),
    ref_scope_id_ character varying(255),
    ref_scope_type_ character varying(255),
    ref_scope_definition_id_ character varying(255),
    root_scope_id_ character varying(255),
    root_scope_type_ character varying(255),
    hierarchy_type_ character varying(255)
);
CREATE TABLE IF NOT EXISTS public.act_hi_identitylink (
    id_ character varying(64) NOT NULL,
    group_id_ character varying(255),
    type_ character varying(255),
    user_id_ character varying(255),
    task_id_ character varying(64),
    create_time_ timestamp without time zone,
    proc_inst_id_ character varying(64),
    scope_id_ character varying(255),
    sub_scope_id_ character varying(255),
    scope_type_ character varying(255),
    scope_definition_id_ character varying(255)
);
CREATE TABLE IF NOT EXISTS public.act_hi_procinst (
    id_ character varying(64) NOT NULL,
    rev_ integer DEFAULT 1,
    proc_inst_id_ character varying(64) NOT NULL,
    business_key_ character varying(255),
    proc_def_id_ character varying(64) NOT NULL,
    start_time_ timestamp without time zone NOT NULL,
    end_time_ timestamp without time zone,
    duration_ bigint,
    start_user_id_ character varying(255),
    start_act_id_ character varying(255),
    end_act_id_ character varying(255),
    super_process_instance_id_ character varying(64),
    delete_reason_ character varying(4000),
    tenant_id_ character varying(255) DEFAULT ''::character varying,
    name_ character varying(255),
    callback_id_ character varying(255),
    callback_type_ character varying(255),
    reference_id_ character varying(255),
    reference_type_ character varying(255),
    propagated_stage_inst_id_ character varying(255),
    business_status_ character varying(255)
);
CREATE TABLE IF NOT EXISTS public.act_hi_taskinst (
    id_ character varying(64) NOT NULL,
    rev_ integer DEFAULT 1,
    proc_def_id_ character varying(64),
    task_def_id_ character varying(64),
    task_def_key_ character varying(255),
    proc_inst_id_ character varying(64),
    execution_id_ character varying(64),
    scope_id_ character varying(255),
    sub_scope_id_ character varying(255),
    scope_type_ character varying(255),
    scope_definition_id_ character varying(255),
    propagated_stage_inst_id_ character varying(255),
    name_ character varying(255),
    parent_task_id_ character varying(64),
    description_ character varying(4000),
    owner_ character varying(255),
    assignee_ character varying(255),
    start_time_ timestamp without time zone NOT NULL,
    claim_time_ timestamp without time zone,
    end_time_ timestamp without time zone,
    duration_ bigint,
    delete_reason_ character varying(4000),
    priority_ integer,
    due_date_ timestamp without time zone,
    form_key_ character varying(255),
    category_ character varying(255),
    tenant_id_ character varying(255) DEFAULT ''::character varying,
    last_updated_time_ timestamp without time zone
);
CREATE TABLE IF NOT EXISTS public.act_hi_tsk_log (
    id_ integer NOT NULL,
    type_ character varying(64),
    task_id_ character varying(64) NOT NULL,
    time_stamp_ timestamp without time zone NOT NULL,
    user_id_ character varying(255),
    data_ character varying(4000),
    execution_id_ character varying(64),
    proc_inst_id_ character varying(64),
    proc_def_id_ character varying(64),
    scope_id_ character varying(255),
    scope_definition_id_ character varying(255),
    sub_scope_id_ character varying(255),
    scope_type_ character varying(255),
    tenant_id_ character varying(255) DEFAULT ''::character varying
);
CREATE TABLE IF NOT EXISTS public.act_hi_varinst (
    id_ character varying(64) NOT NULL,
    rev_ integer DEFAULT 1,
    proc_inst_id_ character varying(64),
    execution_id_ character varying(64),
    task_id_ character varying(64),
    name_ character varying(255) NOT NULL,
    var_type_ character varying(100),
    scope_id_ character varying(255),
    sub_scope_id_ character varying(255),
    scope_type_ character varying(255),
    bytearray_id_ character varying(64),
    double_ double precision,
    long_ bigint,
    text_ character varying(4000),
    text2_ character varying(4000),
    meta_info_ character varying(4000),
    create_time_ timestamp without time zone,
    last_updated_time_ timestamp without time zone
);
CREATE TABLE IF NOT EXISTS public.act_id_bytearray (
    id_ character varying(64) NOT NULL,
    rev_ integer,
    name_ character varying(255),
    bytes_ bytea
);
CREATE TABLE IF NOT EXISTS public.act_id_group (
    id_ character varying(64) NOT NULL,
    rev_ integer,
    name_ character varying(255),
    type_ character varying(255)
);
CREATE TABLE IF NOT EXISTS public.act_id_info (
    id_ character varying(64) NOT NULL,
    rev_ integer,
    user_id_ character varying(64),
    type_ character varying(64),
    key_ character varying(255),
    value_ character varying(255),
    password_ bytea,
    parent_id_ character varying(255)
);
CREATE TABLE IF NOT EXISTS public.act_id_membership (
    user_id_ character varying(64) NOT NULL,
    group_id_ character varying(64) NOT NULL
);
CREATE TABLE IF NOT EXISTS public.act_id_priv (
    id_ character varying(64) NOT NULL,
    name_ character varying(255) NOT NULL
);
CREATE TABLE IF NOT EXISTS public.act_id_priv_mapping (
    id_ character varying(64) NOT NULL,
    priv_id_ character varying(64) NOT NULL,
    user_id_ character varying(255),
    group_id_ character varying(255)
);
CREATE TABLE IF NOT EXISTS public.act_id_property (
    name_ character varying(64) NOT NULL,
    value_ character varying(300),
    rev_ integer
);
CREATE TABLE IF NOT EXISTS public.act_id_token (
    id_ character varying(64) NOT NULL,
    rev_ integer,
    token_value_ character varying(255),
    token_date_ timestamp without time zone,
    ip_address_ character varying(255),
    user_agent_ character varying(255),
    user_id_ character varying(255),
    token_data_ character varying(2000)
);
CREATE TABLE IF NOT EXISTS public.act_id_user (
    id_ character varying(64) NOT NULL,
    rev_ integer,
    first_ character varying(255),
    last_ character varying(255),
    display_name_ character varying(255),
    email_ character varying(255),
    pwd_ character varying(255),
    picture_id_ character varying(64),
    tenant_id_ character varying(255) DEFAULT ''::character varying
);
CREATE TABLE IF NOT EXISTS public.act_procdef_info (
    id_ character varying(64) NOT NULL,
    proc_def_id_ character varying(64) NOT NULL,
    rev_ integer,
    info_json_id_ character varying(64)
);
CREATE TABLE IF NOT EXISTS public.act_re_deployment (
    id_ character varying(64) NOT NULL,
    name_ character varying(255),
    category_ character varying(255),
    key_ character varying(255),
    tenant_id_ character varying(255) DEFAULT ''::character varying,
    deploy_time_ timestamp without time zone,
    derived_from_ character varying(64),
    derived_from_root_ character varying(64),
    parent_deployment_id_ character varying(255),
    engine_version_ character varying(255)
);
CREATE TABLE IF NOT EXISTS public.act_re_model (
    id_ character varying(64) NOT NULL,
    rev_ integer,
    name_ character varying(255),
    key_ character varying(255),
    category_ character varying(255),
    create_time_ timestamp without time zone,
    last_update_time_ timestamp without time zone,
    version_ integer,
    meta_info_ character varying(4000),
    deployment_id_ character varying(64),
    editor_source_value_id_ character varying(64),
    editor_source_extra_value_id_ character varying(64),
    tenant_id_ character varying(255) DEFAULT ''::character varying
);
CREATE TABLE IF NOT EXISTS public.act_re_procdef (
    id_ character varying(64) NOT NULL,
    rev_ integer,
    category_ character varying(255),
    name_ character varying(255),
    key_ character varying(255) NOT NULL,
    version_ integer NOT NULL,
    deployment_id_ character varying(64),
    resource_name_ character varying(4000),
    dgrm_resource_name_ character varying(4000),
    description_ character varying(4000),
    has_start_form_key_ boolean,
    has_graphical_notation_ boolean,
    suspension_state_ integer,
    tenant_id_ character varying(255) DEFAULT ''::character varying,
    derived_from_ character varying(64),
    derived_from_root_ character varying(64),
    derived_version_ integer DEFAULT 0 NOT NULL,
    engine_version_ character varying(255)
);
CREATE TABLE IF NOT EXISTS public.act_ru_actinst (
    id_ character varying(64) NOT NULL,
    rev_ integer DEFAULT 1,
    proc_def_id_ character varying(64) NOT NULL,
    proc_inst_id_ character varying(64) NOT NULL,
    execution_id_ character varying(64) NOT NULL,
    act_id_ character varying(255) NOT NULL,
    task_id_ character varying(64),
    call_proc_inst_id_ character varying(64),
    act_name_ character varying(255),
    act_type_ character varying(255) NOT NULL,
    assignee_ character varying(255),
    start_time_ timestamp without time zone NOT NULL,
    end_time_ timestamp without time zone,
    duration_ bigint,
    transaction_order_ integer,
    delete_reason_ character varying(4000),
    tenant_id_ character varying(255) DEFAULT ''::character varying
);
CREATE TABLE IF NOT EXISTS public.act_ru_deadletter_job (
    id_ character varying(64) NOT NULL,
    rev_ integer,
    category_ character varying(255),
    type_ character varying(255) NOT NULL,
    exclusive_ boolean,
    execution_id_ character varying(64),
    process_instance_id_ character varying(64),
    proc_def_id_ character varying(64),
    element_id_ character varying(255),
    element_name_ character varying(255),
    scope_id_ character varying(255),
    sub_scope_id_ character varying(255),
    scope_type_ character varying(255),
    scope_definition_id_ character varying(255),
    correlation_id_ character varying(255),
    exception_stack_id_ character varying(64),
    exception_msg_ character varying(4000),
    duedate_ timestamp without time zone,
    repeat_ character varying(255),
    handler_type_ character varying(255),
    handler_cfg_ character varying(4000),
    custom_values_id_ character varying(64),
    create_time_ timestamp without time zone,
    tenant_id_ character varying(255) DEFAULT ''::character varying
);
CREATE TABLE IF NOT EXISTS public.act_ru_entitylink (
    id_ character varying(64) NOT NULL,
    rev_ integer,
    create_time_ timestamp without time zone,
    link_type_ character varying(255),
    scope_id_ character varying(255),
    sub_scope_id_ character varying(255),
    scope_type_ character varying(255),
    scope_definition_id_ character varying(255),
    parent_element_id_ character varying(255),
    ref_scope_id_ character varying(255),
    ref_scope_type_ character varying(255),
    ref_scope_definition_id_ character varying(255),
    root_scope_id_ character varying(255),
    root_scope_type_ character varying(255),
    hierarchy_type_ character varying(255)
);
CREATE TABLE IF NOT EXISTS public.act_ru_event_subscr (
    id_ character varying(64) NOT NULL,
    rev_ integer,
    event_type_ character varying(255) NOT NULL,
    event_name_ character varying(255),
    execution_id_ character varying(64),
    proc_inst_id_ character varying(64),
    activity_id_ character varying(64),
    configuration_ character varying(255),
    created_ timestamp without time zone NOT NULL,
    proc_def_id_ character varying(64),
    sub_scope_id_ character varying(64),
    scope_id_ character varying(64),
    scope_definition_id_ character varying(64),
    scope_type_ character varying(64),
    lock_time_ timestamp without time zone,
    lock_owner_ character varying(255),
    tenant_id_ character varying(255) DEFAULT ''::character varying
);
CREATE TABLE IF NOT EXISTS public.act_ru_execution (
    id_ character varying(64) NOT NULL,
    rev_ integer,
    proc_inst_id_ character varying(64),
    business_key_ character varying(255),
    parent_id_ character varying(64),
    proc_def_id_ character varying(64),
    super_exec_ character varying(64),
    root_proc_inst_id_ character varying(64),
    act_id_ character varying(255),
    is_active_ boolean,
    is_concurrent_ boolean,
    is_scope_ boolean,
    is_event_scope_ boolean,
    is_mi_root_ boolean,
    suspension_state_ integer,
    cached_ent_state_ integer,
    tenant_id_ character varying(255) DEFAULT ''::character varying,
    name_ character varying(255),
    start_act_id_ character varying(255),
    start_time_ timestamp without time zone,
    start_user_id_ character varying(255),
    lock_time_ timestamp without time zone,
    lock_owner_ character varying(255),
    is_count_enabled_ boolean,
    evt_subscr_count_ integer,
    task_count_ integer,
    job_count_ integer,
    timer_job_count_ integer,
    susp_job_count_ integer,
    deadletter_job_count_ integer,
    external_worker_job_count_ integer,
    var_count_ integer,
    id_link_count_ integer,
    callback_id_ character varying(255),
    callback_type_ character varying(255),
    reference_id_ character varying(255),
    reference_type_ character varying(255),
    propagated_stage_inst_id_ character varying(255),
    business_status_ character varying(255)
);
CREATE TABLE IF NOT EXISTS public.act_ru_external_job (
    id_ character varying(64) NOT NULL,
    rev_ integer,
    category_ character varying(255),
    type_ character varying(255) NOT NULL,
    lock_exp_time_ timestamp without time zone,
    lock_owner_ character varying(255),
    exclusive_ boolean,
    execution_id_ character varying(64),
    process_instance_id_ character varying(64),
    proc_def_id_ character varying(64),
    element_id_ character varying(255),
    element_name_ character varying(255),
    scope_id_ character varying(255),
    sub_scope_id_ character varying(255),
    scope_type_ character varying(255),
    scope_definition_id_ character varying(255),
    correlation_id_ character varying(255),
    retries_ integer,
    exception_stack_id_ character varying(64),
    exception_msg_ character varying(4000),
    duedate_ timestamp without time zone,
    repeat_ character varying(255),
    handler_type_ character varying(255),
    handler_cfg_ character varying(4000),
    custom_values_id_ character varying(64),
    create_time_ timestamp without time zone,
    tenant_id_ character varying(255) DEFAULT ''::character varying
);
CREATE TABLE IF NOT EXISTS public.act_ru_history_job (
    id_ character varying(64) NOT NULL,
    rev_ integer,
    lock_exp_time_ timestamp without time zone,
    lock_owner_ character varying(255),
    retries_ integer,
    exception_stack_id_ character varying(64),
    exception_msg_ character varying(4000),
    handler_type_ character varying(255),
    handler_cfg_ character varying(4000),
    custom_values_id_ character varying(64),
    adv_handler_cfg_id_ character varying(64),
    create_time_ timestamp without time zone,
    scope_type_ character varying(255),
    tenant_id_ character varying(255) DEFAULT ''::character varying
);
CREATE TABLE IF NOT EXISTS public.act_ru_identitylink (
    id_ character varying(64) NOT NULL,
    rev_ integer,
    group_id_ character varying(255),
    type_ character varying(255),
    user_id_ character varying(255),
    task_id_ character varying(64),
    proc_inst_id_ character varying(64),
    proc_def_id_ character varying(64),
    scope_id_ character varying(255),
    sub_scope_id_ character varying(255),
    scope_type_ character varying(255),
    scope_definition_id_ character varying(255)
);
CREATE TABLE IF NOT EXISTS public.act_ru_job (
    id_ character varying(64) NOT NULL,
    rev_ integer,
    category_ character varying(255),
    type_ character varying(255) NOT NULL,
    lock_exp_time_ timestamp without time zone,
    lock_owner_ character varying(255),
    exclusive_ boolean,
    execution_id_ character varying(64),
    process_instance_id_ character varying(64),
    proc_def_id_ character varying(64),
    element_id_ character varying(255),
    element_name_ character varying(255),
    scope_id_ character varying(255),
    sub_scope_id_ character varying(255),
    scope_type_ character varying(255),
    scope_definition_id_ character varying(255),
    correlation_id_ character varying(255),
    retries_ integer,
    exception_stack_id_ character varying(64),
    exception_msg_ character varying(4000),
    duedate_ timestamp without time zone,
    repeat_ character varying(255),
    handler_type_ character varying(255),
    handler_cfg_ character varying(4000),
    custom_values_id_ character varying(64),
    create_time_ timestamp without time zone,
    tenant_id_ character varying(255) DEFAULT ''::character varying
);
CREATE TABLE IF NOT EXISTS public.act_ru_suspended_job (
    id_ character varying(64) NOT NULL,
    rev_ integer,
    category_ character varying(255),
    type_ character varying(255) NOT NULL,
    exclusive_ boolean,
    execution_id_ character varying(64),
    process_instance_id_ character varying(64),
    proc_def_id_ character varying(64),
    element_id_ character varying(255),
    element_name_ character varying(255),
    scope_id_ character varying(255),
    sub_scope_id_ character varying(255),
    scope_type_ character varying(255),
    scope_definition_id_ character varying(255),
    correlation_id_ character varying(255),
    retries_ integer,
    exception_stack_id_ character varying(64),
    exception_msg_ character varying(4000),
    duedate_ timestamp without time zone,
    repeat_ character varying(255),
    handler_type_ character varying(255),
    handler_cfg_ character varying(4000),
    custom_values_id_ character varying(64),
    create_time_ timestamp without time zone,
    tenant_id_ character varying(255) DEFAULT ''::character varying
);
CREATE TABLE IF NOT EXISTS public.act_ru_task (
    id_ character varying(64) NOT NULL,
    rev_ integer,
    execution_id_ character varying(64),
    proc_inst_id_ character varying(64),
    proc_def_id_ character varying(64),
    task_def_id_ character varying(64),
    scope_id_ character varying(255),
    sub_scope_id_ character varying(255),
    scope_type_ character varying(255),
    scope_definition_id_ character varying(255),
    propagated_stage_inst_id_ character varying(255),
    name_ character varying(255),
    parent_task_id_ character varying(64),
    description_ character varying(4000),
    task_def_key_ character varying(255),
    owner_ character varying(255),
    assignee_ character varying(255),
    delegation_ character varying(64),
    priority_ integer,
    create_time_ timestamp without time zone,
    due_date_ timestamp without time zone,
    category_ character varying(255),
    suspension_state_ integer,
    tenant_id_ character varying(255) DEFAULT ''::character varying,
    form_key_ character varying(255),
    claim_time_ timestamp without time zone,
    is_count_enabled_ boolean,
    var_count_ integer,
    id_link_count_ integer,
    sub_task_count_ integer
);
CREATE TABLE IF NOT EXISTS public.act_ru_timer_job (
    id_ character varying(64) NOT NULL,
    rev_ integer,
    category_ character varying(255),
    type_ character varying(255) NOT NULL,
    lock_exp_time_ timestamp without time zone,
    lock_owner_ character varying(255),
    exclusive_ boolean,
    execution_id_ character varying(64),
    process_instance_id_ character varying(64),
    proc_def_id_ character varying(64),
    element_id_ character varying(255),
    element_name_ character varying(255),
    scope_id_ character varying(255),
    sub_scope_id_ character varying(255),
    scope_type_ character varying(255),
    scope_definition_id_ character varying(255),
    correlation_id_ character varying(255),
    retries_ integer,
    exception_stack_id_ character varying(64),
    exception_msg_ character varying(4000),
    duedate_ timestamp without time zone,
    repeat_ character varying(255),
    handler_type_ character varying(255),
    handler_cfg_ character varying(4000),
    custom_values_id_ character varying(64),
    create_time_ timestamp without time zone,
    tenant_id_ character varying(255) DEFAULT ''::character varying
);
CREATE TABLE IF NOT EXISTS public.act_ru_variable (
    id_ character varying(64) NOT NULL,
    rev_ integer,
    type_ character varying(255) NOT NULL,
    name_ character varying(255) NOT NULL,
    execution_id_ character varying(64),
    proc_inst_id_ character varying(64),
    task_id_ character varying(64),
    scope_id_ character varying(255),
    sub_scope_id_ character varying(255),
    scope_type_ character varying(255),
    bytearray_id_ character varying(64),
    double_ double precision,
    long_ bigint,
    text_ character varying(4000),
    text2_ character varying(4000),
    meta_info_ character varying(4000)
);
CREATE TABLE IF NOT EXISTS public.admin_alert_rules (
    id character varying(36) NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    duration integer,
    enabled boolean,
    metric_name character varying(50),
    name character varying(100) NOT NULL,
    notify_channels character varying(500),
    operator character varying(20),
    severity character varying(20),
    threshold double precision,
    CONSTRAINT admin_alert_rules_severity_check CHECK (((severity)::text = ANY ((ARRAY['INFO'::character varying, 'WARNING'::character varying, 'ERROR'::character varying, 'CRITICAL'::character varying])::text[])))
);
CREATE TABLE IF NOT EXISTS public.admin_alerts (
    id character varying(36) NOT NULL,
    acknowledged_at timestamp(6) with time zone,
    acknowledged_by character varying(36),
    created_at timestamp(6) with time zone NOT NULL,
    message text,
    metric_value double precision,
    resolved_at timestamp(6) with time zone,
    resolved_by character varying(36),
    rule_id character varying(36),
    severity character varying(20) NOT NULL,
    status character varying(20) NOT NULL,
    title character varying(200) NOT NULL,
    CONSTRAINT admin_alerts_severity_check CHECK (((severity)::text = ANY ((ARRAY['INFO'::character varying, 'WARNING'::character varying, 'ERROR'::character varying, 'CRITICAL'::character varying])::text[]))),
    CONSTRAINT admin_alerts_status_check CHECK (((status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'ACKNOWLEDGED'::character varying, 'RESOLVED'::character varying, 'ESCALATED'::character varying])::text[])))
);
CREATE TABLE IF NOT EXISTS public.admin_audit_logs (
    id character varying(255) NOT NULL,
    action character varying(255) NOT NULL,
    change_details text,
    failure_reason character varying(255),
    ip_address character varying(255),
    new_value text,
    old_value text,
    resource_id character varying(255),
    resource_name character varying(255),
    resource_type character varying(255) NOT NULL,
    success boolean,
    "timestamp" timestamp(6) with time zone,
    user_agent character varying(255),
    user_id character varying(255) NOT NULL,
    user_name character varying(255),
    CONSTRAINT admin_audit_logs_action_check CHECK (((action)::text = ANY ((ARRAY['USER_LOGIN'::character varying, 'USER_LOGOUT'::character varying, 'USER_LOGIN_FAILED'::character varying, 'USER_CREATED'::character varying, 'USER_UPDATED'::character varying, 'USER_DELETED'::character varying, 'USER_LOCKED'::character varying, 'USER_UNLOCKED'::character varying, 'PASSWORD_CHANGED'::character varying, 'PASSWORD_RESET'::character varying, 'ROLE_CREATED'::character varying, 'ROLE_UPDATED'::character varying, 'ROLE_DELETED'::character varying, 'PERMISSION_GRANTED'::character varying, 'PERMISSION_REVOKED'::character varying, 'ROLE_ASSIGNED'::character varying, 'ROLE_UNASSIGNED'::character varying, 'DATA_CREATED'::character varying, 'DATA_UPDATED'::character varying, 'DATA_DELETED'::character varying, 'DATA_EXPORTED'::character varying, 'DATA_IMPORTED'::character varying, 'CONFIG_CREATED'::character varying, 'CONFIG_UPDATED'::character varying, 'CONFIG_DELETED'::character varying, 'SYSTEM_STARTUP'::character varying, 'SYSTEM_SHUTDOWN'::character varying, 'BACKUP_CREATED'::character varying, 'BACKUP_RESTORED'::character varying])::text[])))
);
CREATE TABLE IF NOT EXISTS public.admin_column_permissions (
    id character varying(36) NOT NULL,
    column_name character varying(100) NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    mask_expression character varying(200),
    mask_type character varying(50),
    masked boolean,
    rule_id character varying(36) NOT NULL,
    visible boolean
);
CREATE TABLE IF NOT EXISTS public.admin_config_history (
    id character varying(255) NOT NULL,
    change_reason character varying(255),
    changed_at timestamp(6) with time zone,
    changed_by character varying(255),
    config_id character varying(255) NOT NULL,
    config_key character varying(255) NOT NULL,
    new_value text,
    new_version integer,
    old_value text,
    old_version integer
);
CREATE TABLE IF NOT EXISTS public.admin_data_permission_rules (
    id character varying(36) NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    custom_filter text,
    data_scope character varying(30) NOT NULL,
    enabled boolean,
    name character varying(100) NOT NULL,
    permission_type character varying(20) NOT NULL,
    priority integer,
    resource_type character varying(100) NOT NULL,
    target_id character varying(36) NOT NULL,
    target_type character varying(20) NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    CONSTRAINT admin_data_permission_rules_data_scope_check CHECK (((data_scope)::text = ANY ((ARRAY['ALL'::character varying, 'DEPARTMENT'::character varying, 'DEPARTMENT_AND_CHILDREN'::character varying, 'SELF'::character varying, 'CUSTOM'::character varying])::text[]))),
    CONSTRAINT admin_data_permission_rules_permission_type_check CHECK (((permission_type)::text = ANY ((ARRAY['ROLE'::character varying, 'DEPARTMENT'::character varying, 'USER'::character varying, 'CUSTOM'::character varying])::text[])))
);
CREATE TABLE IF NOT EXISTS public.admin_log_retention_policies (
    id character varying(255) NOT NULL,
    archive_after_days integer,
    archive_location character varying(255),
    compression_enabled boolean,
    created_at timestamp(6) with time zone,
    enabled boolean,
    log_type character varying(255) NOT NULL,
    retention_days integer NOT NULL,
    updated_at timestamp(6) with time zone,
    updated_by character varying(255),
    CONSTRAINT admin_log_retention_policies_log_type_check CHECK (((log_type)::text = ANY ((ARRAY['SYSTEM'::character varying, 'BUSINESS'::character varying, 'ACCESS'::character varying, 'ERROR'::character varying, 'SECURITY'::character varying])::text[])))
);
CREATE TABLE IF NOT EXISTS public.admin_password_history (
    id character varying(64) NOT NULL,
    created_at timestamp(6) with time zone,
    password_hash character varying(255) NOT NULL,
    user_id character varying(64) NOT NULL
);
CREATE TABLE IF NOT EXISTS public.admin_permission_change_history (
    id character varying(36) NOT NULL,
    change_type character varying(50) NOT NULL,
    changed_at timestamp(6) with time zone NOT NULL,
    changed_by character varying(36) NOT NULL,
    ip_address character varying(50),
    new_value character varying(500),
    old_value character varying(500),
    reason character varying(500),
    target_permission_id character varying(36),
    target_role_id character varying(36),
    target_user_id character varying(36),
    user_agent character varying(500)
);
CREATE TABLE IF NOT EXISTS public.admin_permission_conflicts (
    id character varying(64) NOT NULL,
    conflict_description text,
    conflict_source1 character varying(100) NOT NULL,
    conflict_source2 character varying(100) NOT NULL,
    detected_at timestamp(6) with time zone,
    resolution_result text,
    resolution_strategy character varying(30),
    resolved_at timestamp(6) with time zone,
    resolved_by character varying(64),
    status character varying(20) NOT NULL,
    user_id character varying(64) NOT NULL,
    permission_id character varying(64) NOT NULL,
    CONSTRAINT admin_permission_conflicts_resolution_strategy_check CHECK (((resolution_strategy)::text = ANY ((ARRAY['DENY'::character varying, 'ALLOW'::character varying, 'HIGHEST_PRIVILEGE'::character varying, 'LOWEST_PRIVILEGE'::character varying, 'LATEST'::character varying, 'MANUAL'::character varying])::text[])))
);
CREATE TABLE IF NOT EXISTS public.admin_permission_delegations (
    id character varying(64) NOT NULL,
    conditions jsonb,
    created_at timestamp(6) with time zone,
    created_by character varying(64),
    delegatee_id character varying(64) NOT NULL,
    delegation_type character varying(20) NOT NULL,
    delegator_id character varying(64) NOT NULL,
    reason text,
    revoke_reason text,
    revoked_at timestamp(6) with time zone,
    revoked_by character varying(64),
    status character varying(20) NOT NULL,
    valid_from timestamp(6) with time zone NOT NULL,
    valid_to timestamp(6) with time zone,
    permission_id character varying(64) NOT NULL,
    CONSTRAINT admin_permission_delegations_delegation_type_check CHECK (((delegation_type)::text = ANY ((ARRAY['TEMPORARY'::character varying, 'PROXY'::character varying, 'TRANSFER'::character varying])::text[])))
);
CREATE TABLE IF NOT EXISTS public.admin_security_policies (
    id character varying(255) NOT NULL,
    created_at timestamp(6) with time zone,
    enabled boolean,
    policy_config text,
    policy_name character varying(255) NOT NULL,
    policy_type character varying(255) NOT NULL,
    updated_at timestamp(6) with time zone,
    updated_by character varying(255)
);
CREATE TABLE IF NOT EXISTS public.admin_system_configs (
    id character varying(255) NOT NULL,
    category character varying(255) NOT NULL,
    config_key character varying(255) NOT NULL,
    config_name character varying(255) NOT NULL,
    config_value text,
    created_at timestamp(6) with time zone,
    default_value character varying(255),
    description character varying(255),
    editable boolean,
    encrypted boolean,
    environment character varying(255),
    updated_at timestamp(6) with time zone,
    updated_by character varying(255),
    value_type character varying(255),
    version integer
);
CREATE TABLE IF NOT EXISTS public.admin_system_logs (
    id character varying(255) NOT NULL,
    action character varying(255),
    extra_data text,
    ip_address character varying(255),
    log_level character varying(255) NOT NULL,
    log_type character varying(255) NOT NULL,
    message text,
    module character varying(255),
    request_body text,
    request_method character varying(255),
    request_url character varying(255),
    response_body text,
    response_status integer,
    response_time bigint,
    stack_trace text,
    "timestamp" timestamp(6) with time zone,
    user_agent character varying(255),
    user_id character varying(255),
    user_name character varying(255),
    CONSTRAINT admin_system_logs_log_level_check CHECK (((log_level)::text = ANY ((ARRAY['TRACE'::character varying, 'DEBUG'::character varying, 'INFO'::character varying, 'WARN'::character varying, 'ERROR'::character varying, 'FATAL'::character varying])::text[]))),
    CONSTRAINT admin_system_logs_log_type_check CHECK (((log_type)::text = ANY ((ARRAY['SYSTEM'::character varying, 'BUSINESS'::character varying, 'ACCESS'::character varying, 'ERROR'::character varying, 'SECURITY'::character varying])::text[])))
);
CREATE TABLE IF NOT EXISTS public.dw_action_definitions (
    id bigint NOT NULL,
    function_unit_id bigint NOT NULL,
    action_name character varying(100) NOT NULL,
    action_type character varying(20) NOT NULL,
    config_json jsonb DEFAULT '{}'::jsonb NOT NULL,
    icon character varying(50),
    button_color character varying(20),
    description text,
    is_default boolean DEFAULT false,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS public.dw_field_definitions (
    id bigint NOT NULL,
    table_id bigint NOT NULL,
    field_name character varying(100) NOT NULL,
    data_type character varying(50) NOT NULL,
    length integer,
    precision_value integer,
    scale integer,
    nullable boolean DEFAULT true,
    default_value character varying(500),
    is_primary_key boolean DEFAULT false,
    is_unique boolean DEFAULT false,
    description text,
    sort_order integer DEFAULT 0 NOT NULL
);
CREATE TABLE IF NOT EXISTS public.dw_foreign_keys (
    id bigint NOT NULL,
    table_id bigint NOT NULL,
    field_id bigint NOT NULL,
    ref_table_id bigint NOT NULL,
    ref_field_id bigint NOT NULL,
    on_delete character varying(20) DEFAULT 'NO ACTION'::character varying,
    on_update character varying(20) DEFAULT 'NO ACTION'::character varying
);
CREATE TABLE IF NOT EXISTS public.dw_form_definitions (
    id bigint NOT NULL,
    function_unit_id bigint NOT NULL,
    form_name character varying(100) NOT NULL,
    form_type character varying(20) NOT NULL,
    config_json jsonb DEFAULT '{}'::jsonb NOT NULL,
    description text,
    bound_table_id bigint,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_form_type CHECK (((form_type)::text = ANY ((ARRAY['MAIN'::character varying, 'SUB'::character varying, 'ACTION'::character varying, 'POPUP'::character varying])::text[])))
);
CREATE TABLE IF NOT EXISTS public.dw_form_table_bindings (
    id bigint NOT NULL,
    form_id bigint NOT NULL,
    table_id bigint NOT NULL,
    binding_type character varying(20) NOT NULL,
    binding_mode character varying(20) DEFAULT 'READONLY'::character varying NOT NULL,
    foreign_key_field character varying(100),
    sort_order integer DEFAULT 0,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp without time zone,
    CONSTRAINT chk_binding_mode CHECK (((binding_mode)::text = ANY ((ARRAY['EDITABLE'::character varying, 'READONLY'::character varying])::text[]))),
    CONSTRAINT chk_binding_type CHECK (((binding_type)::text = ANY ((ARRAY['PRIMARY'::character varying, 'SUB'::character varying, 'RELATED'::character varying])::text[]))),
    CONSTRAINT uk_form_table_binding UNIQUE (form_id, table_id)
);
CREATE TABLE IF NOT EXISTS public.dw_function_units (
    id bigint NOT NULL,
    name character varying(100) NOT NULL,
    description text,
    icon_id bigint,
    status character varying(20) DEFAULT 'DRAFT'::character varying NOT NULL,
    current_version character varying(20),
    created_by character varying(50) NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_by character varying(50),
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    code character varying(50) NOT NULL,
    CONSTRAINT chk_function_unit_status CHECK (((status)::text = ANY ((ARRAY['DRAFT'::character varying, 'PUBLISHED'::character varying, 'ARCHIVED'::character varying])::text[])))
);
CREATE TABLE IF NOT EXISTS public.dw_icons (
    id bigint NOT NULL,
    name character varying(100) NOT NULL,
    category character varying(30) NOT NULL,
    svg_content text NOT NULL,
    file_size integer,
    description character varying(500),
    created_by character varying(50),
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    updated_by character varying(50),
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS public.dw_operation_logs (
    id bigint NOT NULL,
    operator character varying(50) NOT NULL,
    operation_type character varying(50) NOT NULL,
    target_type character varying(50) NOT NULL,
    target_id bigint,
    description character varying(500),
    details text,
    ip_address character varying(50),
    operation_time timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);
CREATE TABLE IF NOT EXISTS public.dw_process_definitions (
    id bigint NOT NULL,
    function_unit_id bigint NOT NULL,
    bpmn_xml text NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS public.dw_table_definitions (
    id bigint NOT NULL,
    function_unit_id bigint NOT NULL,
    table_name character varying(100) NOT NULL,
    table_type character varying(20) NOT NULL,
    description text,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_table_type CHECK (((table_type)::text = ANY ((ARRAY['MAIN'::character varying, 'SUB'::character varying, 'ACTION'::character varying, 'RELATION'::character varying])::text[])))
);
CREATE TABLE IF NOT EXISTS public.dw_versions (
    id bigint NOT NULL,
    function_unit_id bigint NOT NULL,
    version_number character varying(20) NOT NULL,
    change_log text,
    snapshot_data bytea NOT NULL,
    published_by character varying(50) NOT NULL,
    published_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);
CREATE TABLE IF NOT EXISTS public.flw_channel_definition (
    id_ character varying(255) NOT NULL,
    name_ character varying(255),
    version_ integer,
    key_ character varying(255),
    category_ character varying(255),
    deployment_id_ character varying(255),
    create_time_ timestamp(3) without time zone,
    tenant_id_ character varying(255),
    resource_name_ character varying(255),
    description_ character varying(255),
    type_ character varying(255),
    implementation_ character varying(255)
);
CREATE TABLE IF NOT EXISTS public.flw_ev_databasechangelog (
    id character varying(255) NOT NULL,
    author character varying(255) NOT NULL,
    filename character varying(255) NOT NULL,
    dateexecuted timestamp without time zone NOT NULL,
    orderexecuted integer NOT NULL,
    exectype character varying(10) NOT NULL,
    md5sum character varying(35),
    description character varying(255),
    comments character varying(255),
    tag character varying(255),
    liquibase character varying(20),
    contexts character varying(255),
    labels character varying(255),
    deployment_id character varying(10)
);
CREATE TABLE IF NOT EXISTS public.flw_ev_databasechangeloglock (
    id integer NOT NULL,
    locked boolean NOT NULL,
    lockgranted timestamp without time zone,
    lockedby character varying(255)
);
CREATE TABLE IF NOT EXISTS public.flw_event_definition (
    id_ character varying(255) NOT NULL,
    name_ character varying(255),
    version_ integer,
    key_ character varying(255),
    category_ character varying(255),
    deployment_id_ character varying(255),
    tenant_id_ character varying(255),
    resource_name_ character varying(255),
    description_ character varying(255)
);
CREATE TABLE IF NOT EXISTS public.flw_event_deployment (
    id_ character varying(255) NOT NULL,
    name_ character varying(255),
    category_ character varying(255),
    deploy_time_ timestamp(3) without time zone,
    tenant_id_ character varying(255),
    parent_deployment_id_ character varying(255)
);
CREATE TABLE IF NOT EXISTS public.flw_event_resource (
    id_ character varying(255) NOT NULL,
    name_ character varying(255),
    deployment_id_ character varying(255),
    resource_bytes_ bytea
);
CREATE TABLE IF NOT EXISTS public.flw_ru_batch (
    id_ character varying(64) NOT NULL,
    rev_ integer,
    type_ character varying(64) NOT NULL,
    search_key_ character varying(255),
    search_key2_ character varying(255),
    create_time_ timestamp without time zone NOT NULL,
    complete_time_ timestamp without time zone,
    status_ character varying(255),
    batch_doc_id_ character varying(64),
    tenant_id_ character varying(255) DEFAULT ''::character varying
);
CREATE TABLE IF NOT EXISTS public.flw_ru_batch_part (
    id_ character varying(64) NOT NULL,
    rev_ integer,
    batch_id_ character varying(64),
    type_ character varying(64) NOT NULL,
    scope_id_ character varying(64),
    sub_scope_id_ character varying(64),
    scope_type_ character varying(64),
    search_key_ character varying(255),
    search_key2_ character varying(255),
    create_time_ timestamp without time zone NOT NULL,
    complete_time_ timestamp without time zone,
    status_ character varying(255),
    result_doc_id_ character varying(64),
    tenant_id_ character varying(255) DEFAULT ''::character varying
);
CREATE TABLE IF NOT EXISTS public.sys_approvers (
    id character varying(64) NOT NULL,
    created_at timestamp(6) with time zone,
    created_by character varying(64),
    target_id character varying(64) NOT NULL,
    target_type character varying(20) NOT NULL,
    user_id character varying(64) NOT NULL,
    CONSTRAINT sys_approvers_target_type_check CHECK (((target_type)::text = ANY ((ARRAY['VIRTUAL_GROUP'::character varying, 'BUSINESS_UNIT'::character varying])::text[])))
);
CREATE TABLE IF NOT EXISTS public.sys_business_unit_roles (
    id character varying(64) NOT NULL,
    business_unit_id character varying(64) NOT NULL,
    created_at timestamp(6) with time zone,
    created_by character varying(64),
    role_id character varying(64) NOT NULL
);
CREATE TABLE IF NOT EXISTS public.sys_business_units (
    id character varying(64) NOT NULL,
    code character varying(50) NOT NULL,
    cost_center character varying(50),
    created_at timestamp(6) with time zone,
    created_by character varying(64),
    description text,
    level integer NOT NULL,
    location character varying(200),
    name character varying(100) NOT NULL,
    parent_id character varying(64),
    path character varying(500),
    phone character varying(50),
    sort_order integer,
    status character varying(20) NOT NULL,
    updated_at timestamp(6) with time zone,
    updated_by character varying(64),
    CONSTRAINT sys_business_units_status_check CHECK (((status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'DISABLED'::character varying])::text[])))
);
CREATE TABLE IF NOT EXISTS public.sys_developer_role_permissions (
    id character varying(64) NOT NULL,
    role_id character varying(64) NOT NULL,
    permission character varying(50) NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    created_by character varying(64)
);
CREATE TABLE IF NOT EXISTS public.sys_dictionaries (
    id character varying(64) NOT NULL,
    code character varying(50) NOT NULL,
    name character varying(100) NOT NULL,
    type character varying(50),
    description text,
    status character varying(20) DEFAULT 'ACTIVE'::character varying,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    cache_ttl integer,
    created_by character varying(36),
    data_source_config text,
    data_source_type character varying(20),
    sort_order integer,
    updated_by character varying(36),
    version integer DEFAULT 0,
    CONSTRAINT chk_dict_data_source_type CHECK (((data_source_type)::text = ANY ((ARRAY['DATABASE'::character varying, 'API'::character varying, 'FILE'::character varying, 'STATIC'::character varying])::text[])))
);
CREATE TABLE IF NOT EXISTS public.sys_dictionary_data_sources (
    id character varying(36) NOT NULL,
    dictionary_id character varying(36) NOT NULL,
    source_type character varying(20) NOT NULL,
    connection_string character varying(500),
    table_name character varying(200),
    code_field character varying(100),
    name_field character varying(100),
    value_field character varying(100),
    filter_condition character varying(500),
    order_by_field character varying(100),
    cache_ttl integer DEFAULT 300,
    enabled boolean DEFAULT true,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);
CREATE TABLE IF NOT EXISTS public.sys_dictionary_items (
    id character varying(64) NOT NULL,
    dictionary_id character varying(64) NOT NULL,
    parent_id character varying(64),
    item_code character varying(50) NOT NULL,
    name character varying(200) NOT NULL,
    name_en character varying(200),
    name_zh_cn character varying(200),
    name_zh_tw character varying(200),
    value character varying(500),
    description character varying(500),
    status character varying(20) NOT NULL,
    sort_order integer DEFAULT 0,
    valid_from timestamp without time zone,
    valid_to timestamp without time zone,
    ext_attributes text,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_by character varying(36),
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_by character varying(36)
);
CREATE TABLE IF NOT EXISTS public.sys_dictionary_versions (
    id character varying(36) NOT NULL,
    dictionary_id character varying(36) NOT NULL,
    version integer NOT NULL,
    snapshot_data text NOT NULL,
    change_description character varying(500),
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    created_by character varying(36)
);
CREATE TABLE IF NOT EXISTS public.sys_function_unit_access (
    id character varying(64) NOT NULL,
    function_unit_id character varying(64) NOT NULL,
    access_type character varying(20) NOT NULL,
    target_type character varying(20) NOT NULL,
    target_id character varying(64) NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    created_by character varying(64),
    role_id character varying(64) NOT NULL,
    role_name character varying(100)
);
CREATE TABLE IF NOT EXISTS public.sys_function_unit_approvals (
    id character varying(64) NOT NULL,
    deployment_id character varying(64) NOT NULL,
    approval_type character varying(20) NOT NULL,
    approver_id character varying(64) NOT NULL,
    approver_name character varying(100),
    status character varying(20) DEFAULT 'PENDING'::character varying NOT NULL,
    comment text,
    approved_at timestamp without time zone,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    approval_order integer
);
CREATE TABLE IF NOT EXISTS public.sys_function_unit_contents (
    id character varying(64) NOT NULL,
    function_unit_id character varying(64) NOT NULL,
    content_type character varying(20) NOT NULL,
    content_name character varying(200) NOT NULL,
    content_path character varying(500),
    content_data text,
    checksum character varying(64),
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    flowable_deployment_id character varying(64),
    flowable_process_definition_id character varying(64),
    source_id character varying(64)
);
CREATE TABLE IF NOT EXISTS public.sys_function_unit_dependencies (
    id character varying(64) NOT NULL,
    function_unit_id character varying(64) NOT NULL,
    dependency_code character varying(50) NOT NULL,
    dependency_version character varying(20) NOT NULL,
    dependency_type character varying(20) DEFAULT 'REQUIRED'::character varying NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS public.sys_function_unit_deployments (
    id character varying(64) NOT NULL,
    function_unit_id character varying(64) NOT NULL,
    environment character varying(20) NOT NULL,
    strategy character varying(20) NOT NULL,
    status character varying(20) DEFAULT 'PENDING'::character varying NOT NULL,
    deployed_at timestamp without time zone,
    deployed_by character varying(64),
    completed_at timestamp without time zone,
    rollback_to_id character varying(64),
    error_message text,
    deployment_log text,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    created_by character varying(64),
    rollback_at timestamp(6) with time zone,
    rollback_by character varying(64),
    rollback_reason text,
    started_at timestamp(6) with time zone
);
CREATE TABLE IF NOT EXISTS public.sys_function_units (
    id character varying(64) NOT NULL,
    checksum character varying(64),
    code character varying(50) NOT NULL,
    created_at timestamp(6) with time zone,
    created_by character varying(64),
    description text,
    digital_signature text,
    enabled boolean NOT NULL,
    imported_at timestamp(6) with time zone,
    imported_by character varying(64),
    name character varying(100) NOT NULL,
    package_path character varying(500),
    package_size bigint,
    status character varying(20) NOT NULL,
    updated_at timestamp(6) with time zone,
    updated_by character varying(64),
    validated_at timestamp(6) with time zone,
    validated_by character varying(64),
    version character varying(20) NOT NULL,
    process_deployed boolean,
    process_deployment_count integer,
    CONSTRAINT chk_func_unit_status CHECK (((status)::text = ANY ((ARRAY['DRAFT'::character varying, 'VALIDATED'::character varying, 'DEPLOYED'::character varying, 'DEPRECATED'::character varying])::text[])))
);
CREATE TABLE IF NOT EXISTS public.sys_login_audit (
    id uuid DEFAULT public.uuid_generate_v4() NOT NULL,
    user_id character varying(64),
    username character varying(50) NOT NULL,
    action character varying(20) NOT NULL,
    ip_address character varying(45),
    user_agent text,
    success boolean DEFAULT true,
    failure_reason character varying(255),
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS public.sys_member_change_logs (
    id character varying(64) NOT NULL,
    change_type character varying(20) NOT NULL,
    created_at timestamp(6) with time zone,
    operator_id character varying(64),
    reason text,
    role_ids text,
    target_id character varying(64) NOT NULL,
    target_type character varying(20) NOT NULL,
    user_id character varying(64) NOT NULL,
    CONSTRAINT sys_member_change_logs_change_type_check CHECK (((change_type)::text = ANY ((ARRAY['JOIN'::character varying, 'EXIT'::character varying, 'REMOVED'::character varying])::text[]))),
    CONSTRAINT sys_member_change_logs_target_type_check CHECK (((target_type)::text = ANY ((ARRAY['VIRTUAL_GROUP'::character varying, 'BUSINESS_UNIT'::character varying])::text[])))
);
CREATE TABLE IF NOT EXISTS public.sys_permission_requests (
    id character varying(64) NOT NULL,
    applicant_id character varying(64) NOT NULL,
    approved_at timestamp(6) with time zone,
    approver_comment text,
    approver_id character varying(64),
    created_at timestamp(6) with time zone,
    reason text,
    request_type character varying(20) NOT NULL,
    role_ids text,
    status character varying(20) NOT NULL,
    target_id character varying(64) NOT NULL,
    updated_at timestamp(6) with time zone,
    CONSTRAINT sys_permission_requests_request_type_check CHECK (((request_type)::text = ANY ((ARRAY['VIRTUAL_GROUP'::character varying, 'BUSINESS_UNIT'::character varying, 'BUSINESS_UNIT_ROLE'::character varying])::text[]))),
    CONSTRAINT sys_permission_requests_status_check CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'APPROVED'::character varying, 'REJECTED'::character varying, 'CANCELLED'::character varying])::text[])))
);
CREATE TABLE IF NOT EXISTS public.sys_permissions (
    id character varying(64) NOT NULL,
    code character varying(100) NOT NULL,
    name character varying(100) NOT NULL,
    type character varying(50),
    resource character varying(100),
    action character varying(50),
    description text,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    parent_id character varying(64),
    sort_order integer
);
CREATE TABLE IF NOT EXISTS public.sys_role_assignments (
    id character varying(64) NOT NULL,
    role_id character varying(64) NOT NULL,
    target_type character varying(20) NOT NULL,
    target_id character varying(64) NOT NULL,
    assigned_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    assigned_by character varying(64),
    valid_from timestamp without time zone,
    valid_to timestamp without time zone,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    created_by character varying(64),
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS public.sys_role_permissions (
    id character varying(64) NOT NULL,
    role_id character varying(64) NOT NULL,
    permission_id character varying(64) NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    condition_type character varying(50),
    condition_value jsonb,
    granted_at timestamp(6) with time zone,
    granted_by character varying(64)
);
CREATE TABLE IF NOT EXISTS public.sys_roles (
    id character varying(64) NOT NULL,
    code character varying(50) NOT NULL,
    name character varying(100) NOT NULL,
    type character varying(20) DEFAULT 'BUSINESS'::character varying NOT NULL,
    description text,
    status character varying(20) DEFAULT 'ACTIVE'::character varying,
    is_system boolean DEFAULT false,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    created_by character varying(64),
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    updated_by character varying(64),
    CONSTRAINT chk_role_type CHECK (((type)::text = ANY ((ARRAY['ADMIN'::character varying, 'DEVELOPER'::character varying, 'BU_BOUNDED'::character varying, 'BU_UNBOUNDED'::character varying])::text[])))
);
CREATE TABLE IF NOT EXISTS public.sys_user_business_unit_roles (
    id character varying(64) NOT NULL,
    business_unit_id character varying(64) NOT NULL,
    created_at timestamp(6) with time zone,
    created_by character varying(64),
    role_id character varying(64) NOT NULL,
    user_id character varying(64) NOT NULL
);
CREATE TABLE IF NOT EXISTS public.sys_user_business_units (
    id character varying(64) NOT NULL,
    business_unit_id character varying(64) NOT NULL,
    created_at timestamp(6) with time zone,
    created_by character varying(64),
    user_id character varying(64) NOT NULL
);
CREATE TABLE IF NOT EXISTS public.sys_user_preferences (
    id character varying(64) NOT NULL,
    created_at timestamp(6) with time zone,
    preference_key character varying(100) NOT NULL,
    preference_value character varying(500),
    updated_at timestamp(6) with time zone,
    user_id character varying(64) NOT NULL
);
CREATE TABLE IF NOT EXISTS public.sys_user_roles (
    id character varying(64) NOT NULL,
    user_id character varying(64) NOT NULL,
    role_id character varying(64) NOT NULL,
    assigned_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    assigned_by character varying(64),
    valid_from timestamp without time zone,
    valid_to timestamp without time zone
);
CREATE TABLE IF NOT EXISTS public.sys_users (
    id character varying(64) NOT NULL,
    username character varying(100) NOT NULL,
    password_hash character varying(255) NOT NULL,
    email character varying(255),
    display_name character varying(50),
    full_name character varying(100),
    phone character varying(50),
    employee_id character varying(50),
    "position" character varying(100),
    entity_manager_id character varying(64),
    function_manager_id character varying(64),
    status character varying(20) DEFAULT 'ACTIVE'::character varying NOT NULL,
    language character varying(10) DEFAULT 'zh_CN'::character varying,
    must_change_password boolean DEFAULT false,
    password_expired_at timestamp without time zone,
    last_login_at timestamp without time zone,
    last_login_ip character varying(50),
    failed_login_count integer DEFAULT 0,
    locked_until timestamp without time zone,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    created_by character varying(64),
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    updated_by character varying(64),
    deleted boolean DEFAULT false NOT NULL,
    deleted_at timestamp without time zone,
    deleted_by character varying(64),
    CONSTRAINT chk_sys_user_status CHECK (((status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'DISABLED'::character varying, 'LOCKED'::character varying, 'PENDING'::character varying])::text[])))
);
CREATE TABLE IF NOT EXISTS public.sys_virtual_group_members (
    id character varying(64) NOT NULL,
    group_id character varying(64) NOT NULL,
    user_id character varying(64) NOT NULL,
    joined_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    added_by character varying(64)
);
CREATE TABLE IF NOT EXISTS public.sys_virtual_group_roles (
    id character varying(64) NOT NULL,
    created_at timestamp(6) with time zone,
    created_by character varying(64),
    role_id character varying(64) NOT NULL,
    virtual_group_id character varying(64) NOT NULL
);
CREATE TABLE IF NOT EXISTS public.sys_virtual_group_task_history (
    id character varying(64) NOT NULL,
    group_id character varying(64) NOT NULL,
    task_id character varying(64) NOT NULL,
    action_type character varying(20) NOT NULL,
    from_user_id character varying(64),
    to_user_id character varying(64),
    assigned_user_id character varying(64),
    assigned_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    completed_at timestamp without time zone,
    status character varying(20),
    reason text,
    comment text,
    created_at timestamp with time zone DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_vg_task_action_type CHECK (((action_type)::text = ANY ((ARRAY['CREATED'::character varying, 'ASSIGNED'::character varying, 'CLAIMED'::character varying, 'DELEGATED'::character varying, 'COMPLETED'::character varying, 'CANCELLED'::character varying, 'RETURNED'::character varying])::text[])))
);
CREATE TABLE IF NOT EXISTS public.sys_virtual_groups (
    id character varying(64) NOT NULL,
    name character varying(100) NOT NULL,
    code character varying(50) NOT NULL,
    description text,
    type character varying(50) DEFAULT 'CUSTOM'::character varying,
    rule_expression text,
    status character varying(20) DEFAULT 'ACTIVE'::character varying,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    created_by character varying(64),
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    updated_by character varying(64),
    ad_group character varying(100),
    CONSTRAINT chk_virtual_group_type CHECK (((type)::text = ANY ((ARRAY['SYSTEM'::character varying, 'CUSTOM'::character varying])::text[])))
);
CREATE TABLE IF NOT EXISTS public.up_dashboard_layout (
    id bigint NOT NULL,
    component_id character varying(50) NOT NULL,
    component_type character varying(50) NOT NULL,
    config jsonb,
    created_at timestamp(6) without time zone,
    grid_h integer NOT NULL,
    grid_w integer NOT NULL,
    grid_x integer NOT NULL,
    grid_y integer NOT NULL,
    is_visible boolean,
    updated_at timestamp(6) without time zone,
    user_id character varying(64) NOT NULL
);
CREATE TABLE IF NOT EXISTS public.up_delegation_audit (
    id bigint NOT NULL,
    created_at timestamp(6) without time zone,
    delegate_id character varying(64) NOT NULL,
    delegator_id character varying(64) NOT NULL,
    ip_address character varying(50),
    operation_detail text,
    operation_result character varying(50),
    operation_type character varying(50) NOT NULL,
    task_id character varying(64),
    user_agent character varying(500)
);
CREATE TABLE IF NOT EXISTS public.up_delegation_rule (
    id bigint NOT NULL,
    created_at timestamp(6) without time zone,
    delegate_id character varying(64) NOT NULL,
    delegation_type character varying(20) NOT NULL,
    delegator_id character varying(64) NOT NULL,
    end_time timestamp(6) without time zone,
    priority_filter jsonb,
    process_types jsonb,
    reason text,
    start_time timestamp(6) without time zone,
    status character varying(20),
    updated_at timestamp(6) without time zone,
    CONSTRAINT up_delegation_rule_delegation_type_check CHECK (((delegation_type)::text = ANY ((ARRAY['ALL'::character varying, 'PARTIAL'::character varying, 'TEMPORARY'::character varying, 'URGENT'::character varying])::text[]))),
    CONSTRAINT up_delegation_rule_status_check CHECK (((status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'INACTIVE'::character varying, 'EXPIRED'::character varying, 'SUSPENDED'::character varying])::text[])))
);
CREATE TABLE IF NOT EXISTS public.up_favorite_process (
    id bigint NOT NULL,
    created_at timestamp(6) without time zone,
    display_order integer,
    process_definition_key character varying(255) NOT NULL,
    user_id character varying(64) NOT NULL
);
CREATE TABLE IF NOT EXISTS public.up_notification_preference (
    id bigint NOT NULL,
    browser_enabled boolean,
    created_at timestamp(6) without time zone,
    email_enabled boolean,
    in_app_enabled boolean,
    notification_type character varying(50) NOT NULL,
    quiet_end_time time(6) without time zone,
    quiet_start_time time(6) without time zone,
    updated_at timestamp(6) without time zone,
    user_id character varying(64) NOT NULL
);
CREATE TABLE IF NOT EXISTS public.up_permission_request (
    id bigint NOT NULL,
    applicant_id character varying(64) NOT NULL,
    approve_comment text,
    approve_time timestamp(6) without time zone,
    approver_id character varying(64),
    created_at timestamp(6) without time zone,
    organization_unit_id character varying(64),
    organization_unit_name character varying(200),
    permissions jsonb,
    reason text NOT NULL,
    request_type character varying(30) NOT NULL,
    role_id character varying(64),
    role_name character varying(100),
    status character varying(20),
    updated_at timestamp(6) without time zone,
    valid_from timestamp(6) without time zone,
    valid_to timestamp(6) without time zone,
    virtual_group_id character varying(64),
    virtual_group_name character varying(200),
    business_unit_id character varying(64),
    business_unit_name character varying(200),
    CONSTRAINT up_permission_request_request_type_check CHECK (((request_type)::text = ANY ((ARRAY['ROLE_ASSIGNMENT'::character varying, 'VIRTUAL_GROUP_JOIN'::character varying, 'FUNCTION'::character varying, 'DATA'::character varying, 'TEMPORARY'::character varying])::text[]))),
    CONSTRAINT up_permission_request_status_check CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'APPROVED'::character varying, 'REJECTED'::character varying])::text[])))
);
CREATE TABLE IF NOT EXISTS public.up_process_draft (
    id bigint NOT NULL,
    attachments jsonb,
    created_at timestamp(6) without time zone,
    form_data jsonb NOT NULL,
    process_definition_key character varying(255) NOT NULL,
    updated_at timestamp(6) without time zone,
    user_id character varying(64) NOT NULL
);
CREATE TABLE IF NOT EXISTS public.up_process_history (
    id bigint NOT NULL,
    activity_id character varying(100),
    activity_name character varying(255),
    activity_type character varying(50),
    comment text,
    duration bigint,
    operation_time timestamp(6) without time zone,
    operation_type character varying(50) NOT NULL,
    operator_id character varying(64) NOT NULL,
    operator_name character varying(100),
    process_instance_id character varying(64) NOT NULL,
    task_id character varying(64)
);
CREATE TABLE IF NOT EXISTS public.up_process_instance (
    id character varying(64) NOT NULL,
    business_key character varying(255),
    candidate_users character varying(500),
    current_assignee character varying(64),
    current_node character varying(255),
    end_time timestamp(6) without time zone,
    priority character varying(32),
    process_definition_id character varying(64),
    process_definition_key character varying(255) NOT NULL,
    process_definition_name character varying(255),
    start_time timestamp(6) without time zone,
    start_user_id character varying(64) NOT NULL,
    start_user_name character varying(100),
    status character varying(32) NOT NULL,
    updated_at timestamp(6) without time zone,
    variables jsonb
);
CREATE TABLE IF NOT EXISTS public.up_user_preference (
    id bigint NOT NULL,
    created_at timestamp(6) without time zone,
    date_format character varying(20),
    font_size character varying(10),
    language character varying(10),
    layout_density character varying(10),
    page_size integer,
    theme character varying(20),
    theme_color character varying(20),
    timezone character varying(50),
    updated_at timestamp(6) without time zone,
    user_id character varying(64) NOT NULL
);
CREATE TABLE IF NOT EXISTS public.wf_audit_logs (
    id character varying(64) NOT NULL,
    process_instance_id character varying(64),
    task_id character varying(64),
    user_id character varying(64),
    operation_type character varying(50) NOT NULL,
    operation_detail text,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    after_data jsonb,
    before_data jsonb,
    context_data jsonb,
    duration_ms bigint,
    error_message text,
    ip_address character varying(45),
    is_sensitive boolean NOT NULL,
    operation_description text,
    operation_result character varying(20) NOT NULL,
    request_id character varying(64),
    resource_id character varying(64) NOT NULL,
    resource_name character varying(255),
    resource_type character varying(50) NOT NULL,
    risk_level character varying(20),
    session_id character varying(128),
    tenant_id character varying(64),
    "timestamp" timestamp(6) without time zone NOT NULL,
    user_agent character varying(500)
);
CREATE TABLE IF NOT EXISTS public.wf_exception_records (
    id character varying(64) NOT NULL,
    process_instance_id character varying(64),
    task_id character varying(64),
    exception_type character varying(100),
    exception_message text,
    stack_trace text,
    handled boolean DEFAULT false,
    handled_by character varying(64),
    handled_at timestamp without time zone,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    activity_id character varying(255),
    activity_name character varying(255),
    alert_sent boolean NOT NULL,
    alert_sent_time timestamp(6) without time zone,
    context_data text,
    created_time timestamp(6) without time zone NOT NULL,
    exception_class character varying(500),
    last_retry_time timestamp(6) without time zone,
    max_retry_count integer NOT NULL,
    next_retry_time timestamp(6) without time zone,
    occurred_time timestamp(6) without time zone NOT NULL,
    parent_exception_id character varying(64),
    process_definition_id character varying(64),
    process_definition_key character varying(255),
    resolution_method character varying(50),
    resolution_note text,
    resolved boolean NOT NULL,
    resolved_by character varying(64),
    resolved_time timestamp(6) without time zone,
    retry_count integer NOT NULL,
    root_cause text,
    severity character varying(20) NOT NULL,
    status character varying(20) NOT NULL,
    task_name character varying(255),
    tenant_id character varying(64),
    updated_time timestamp(6) without time zone,
    variables_snapshot text,
    CONSTRAINT wf_exception_records_severity_check CHECK (((severity)::text = ANY ((ARRAY['CRITICAL'::character varying, 'HIGH'::character varying, 'MEDIUM'::character varying, 'LOW'::character varying])::text[]))),
    CONSTRAINT wf_exception_records_status_check CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'PROCESSING'::character varying, 'RESOLVED'::character varying, 'IGNORED'::character varying])::text[])))
);
CREATE TABLE IF NOT EXISTS public.wf_extended_task_info (
    task_id character varying(64) NOT NULL,
    process_instance_id character varying(64),
    assignment_type character varying(20),
    original_assignee character varying(64),
    delegated_from character varying(64),
    priority integer DEFAULT 50,
    due_date timestamp without time zone,
    reminder_sent boolean DEFAULT false,
    custom_data text,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    assignment_target character varying(255) NOT NULL,
    business_key character varying(255),
    claimed_by character varying(64),
    claimed_time timestamp(6) without time zone,
    completed_by character varying(64),
    completed_time timestamp(6) without time zone,
    created_by character varying(64),
    created_time timestamp(6) without time zone NOT NULL,
    delegated_by character varying(64),
    delegated_time timestamp(6) without time zone,
    delegated_to character varying(64),
    delegation_reason character varying(500),
    extended_properties text,
    form_key character varying(255),
    is_deleted boolean NOT NULL,
    process_definition_id character varying(64) NOT NULL,
    status character varying(20) NOT NULL,
    task_definition_key character varying(255),
    task_description character varying(4000),
    task_name character varying(255),
    tenant_id character varying(64),
    updated_by character varying(64),
    updated_time timestamp(6) without time zone,
    version bigint,
    id bigint NOT NULL
);
CREATE TABLE IF NOT EXISTS public.wf_process_variables (
    id character varying(64) NOT NULL,
    process_instance_id character varying(64) NOT NULL,
    name character varying(100) NOT NULL,
    type character varying(50),
    value text,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP,
    activity_instance_id character varying(64),
    binary_value oid,
    case_execution_id character varying(64),
    case_instance_id character varying(64),
    change_reason character varying(500),
    created_by character varying(64),
    created_time timestamp(6) without time zone NOT NULL,
    date_value timestamp(6) without time zone,
    double_value double precision,
    execution_id character varying(64),
    is_concurrent_local boolean,
    json_value jsonb,
    long_value bigint,
    operation_type character varying(20),
    sequence_counter bigint,
    task_id character varying(64),
    tenant_id character varying(255),
    text_value text,
    text_value2 text,
    updated_by character varying(64),
    updated_time timestamp(6) without time zone NOT NULL
);
CREATE TABLE IF NOT EXISTS public.wf_saga_steps (
    id uuid DEFAULT public.uuid_generate_v4() NOT NULL,
    saga_id uuid NOT NULL,
    step_name character varying(100) NOT NULL,
    step_order integer NOT NULL,
    status character varying(20) NOT NULL,
    input_data jsonb,
    output_data jsonb,
    error_message text,
    started_at timestamp without time zone,
    completed_at timestamp without time zone
);
CREATE TABLE IF NOT EXISTS public.wf_saga_transactions (
    id uuid DEFAULT public.uuid_generate_v4() NOT NULL,
    saga_type character varying(100) NOT NULL,
    status character varying(20) NOT NULL,
    context jsonb,
    started_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    completed_at timestamp without time zone,
    error_message text,
    version bigint DEFAULT 0
);

-- =====================================================
-- 第三步：创建索引
-- =====================================================
CREATE INDEX IF NOT EXISTS act_idx_app_def_dply ON public.act_app_appdef USING btree (deployment_id_);
CREATE INDEX IF NOT EXISTS act_idx_app_rsrc_dpl ON public.act_app_deployment_resource USING btree (deployment_id_);
CREATE INDEX IF NOT EXISTS act_idx_athrz_procedef ON public.act_ru_identitylink USING btree (proc_def_id_);
CREATE INDEX IF NOT EXISTS act_idx_bytear_depl ON public.act_ge_bytearray USING btree (deployment_id_);
CREATE INDEX IF NOT EXISTS act_idx_case_def_dply ON public.act_cmmn_casedef USING btree (deployment_id_);
CREATE INDEX IF NOT EXISTS act_idx_case_inst_case_def ON public.act_cmmn_ru_case_inst USING btree (case_def_id_);
CREATE INDEX IF NOT EXISTS act_idx_case_inst_parent ON public.act_cmmn_ru_case_inst USING btree (parent_id_);
CREATE INDEX IF NOT EXISTS act_idx_case_inst_ref_id_ ON public.act_cmmn_ru_case_inst USING btree (reference_id_);
CREATE INDEX IF NOT EXISTS act_idx_cmmn_rsrc_dpl ON public.act_cmmn_deployment_resource USING btree (deployment_id_);
CREATE INDEX IF NOT EXISTS act_idx_deadletter_job_correlation_id ON public.act_ru_deadletter_job USING btree (correlation_id_);
CREATE INDEX IF NOT EXISTS act_idx_deadletter_job_custom_values_id ON public.act_ru_deadletter_job USING btree (custom_values_id_);
CREATE INDEX IF NOT EXISTS act_idx_deadletter_job_exception_stack_id ON public.act_ru_deadletter_job USING btree (exception_stack_id_);
CREATE INDEX IF NOT EXISTS act_idx_deadletter_job_execution_id ON public.act_ru_deadletter_job USING btree (execution_id_);
CREATE INDEX IF NOT EXISTS act_idx_deadletter_job_proc_def_id ON public.act_ru_deadletter_job USING btree (proc_def_id_);
CREATE INDEX IF NOT EXISTS act_idx_deadletter_job_process_instance_id ON public.act_ru_deadletter_job USING btree (process_instance_id_);
CREATE INDEX IF NOT EXISTS act_idx_djob_scope ON public.act_ru_deadletter_job USING btree (scope_id_, scope_type_);
CREATE INDEX IF NOT EXISTS act_idx_djob_scope_def ON public.act_ru_deadletter_job USING btree (scope_definition_id_, scope_type_);
CREATE INDEX IF NOT EXISTS act_idx_djob_sub_scope ON public.act_ru_deadletter_job USING btree (sub_scope_id_, scope_type_);
CREATE INDEX IF NOT EXISTS act_idx_dmn_instance_id ON public.act_dmn_hi_decision_execution USING btree (instance_id_);
CREATE INDEX IF NOT EXISTS act_idx_ejob_scope ON public.act_ru_external_job USING btree (scope_id_, scope_type_);
CREATE INDEX IF NOT EXISTS act_idx_ejob_scope_def ON public.act_ru_external_job USING btree (scope_definition_id_, scope_type_);
CREATE INDEX IF NOT EXISTS act_idx_ejob_sub_scope ON public.act_ru_external_job USING btree (sub_scope_id_, scope_type_);
CREATE INDEX IF NOT EXISTS act_idx_ent_lnk_ref_scope ON public.act_ru_entitylink USING btree (ref_scope_id_, ref_scope_type_, link_type_);
CREATE INDEX IF NOT EXISTS act_idx_ent_lnk_root_scope ON public.act_ru_entitylink USING btree (root_scope_id_, root_scope_type_, link_type_);
CREATE INDEX IF NOT EXISTS act_idx_ent_lnk_scope ON public.act_ru_entitylink USING btree (scope_id_, scope_type_, link_type_);
CREATE INDEX IF NOT EXISTS act_idx_ent_lnk_scope_def ON public.act_ru_entitylink USING btree (scope_definition_id_, scope_type_, link_type_);
CREATE INDEX IF NOT EXISTS act_idx_event_subscr ON public.act_ru_event_subscr USING btree (execution_id_);
CREATE INDEX IF NOT EXISTS act_idx_event_subscr_config_ ON public.act_ru_event_subscr USING btree (configuration_);
CREATE INDEX IF NOT EXISTS act_idx_event_subscr_scoperef_ ON public.act_ru_event_subscr USING btree (scope_id_, scope_type_);
CREATE INDEX IF NOT EXISTS act_idx_exe_parent ON public.act_ru_execution USING btree (parent_id_);
CREATE INDEX IF NOT EXISTS act_idx_exe_procdef ON public.act_ru_execution USING btree (proc_def_id_);
CREATE INDEX IF NOT EXISTS act_idx_exe_procinst ON public.act_ru_execution USING btree (proc_inst_id_);
CREATE INDEX IF NOT EXISTS act_idx_exe_root ON public.act_ru_execution USING btree (root_proc_inst_id_);
CREATE INDEX IF NOT EXISTS act_idx_exe_super ON public.act_ru_execution USING btree (super_exec_);
CREATE INDEX IF NOT EXISTS act_idx_exec_buskey ON public.act_ru_execution USING btree (business_key_);
CREATE INDEX IF NOT EXISTS act_idx_exec_ref_id_ ON public.act_ru_execution USING btree (reference_id_);
CREATE INDEX IF NOT EXISTS act_idx_external_job_correlation_id ON public.act_ru_external_job USING btree (correlation_id_);
CREATE INDEX IF NOT EXISTS act_idx_external_job_custom_values_id ON public.act_ru_external_job USING btree (custom_values_id_);
CREATE INDEX IF NOT EXISTS act_idx_external_job_exception_stack_id ON public.act_ru_external_job USING btree (exception_stack_id_);
CREATE INDEX IF NOT EXISTS act_idx_hi_act_inst_end ON public.act_hi_actinst USING btree (end_time_);
CREATE INDEX IF NOT EXISTS act_idx_hi_act_inst_exec ON public.act_hi_actinst USING btree (execution_id_, act_id_);
CREATE INDEX IF NOT EXISTS act_idx_hi_act_inst_procinst ON public.act_hi_actinst USING btree (proc_inst_id_, act_id_);
CREATE INDEX IF NOT EXISTS act_idx_hi_act_inst_start ON public.act_hi_actinst USING btree (start_time_);
CREATE INDEX IF NOT EXISTS act_idx_hi_case_inst_end ON public.act_cmmn_hi_case_inst USING btree (end_time_);
CREATE INDEX IF NOT EXISTS act_idx_hi_detail_act_inst ON public.act_hi_detail USING btree (act_inst_id_);
CREATE INDEX IF NOT EXISTS act_idx_hi_detail_name ON public.act_hi_detail USING btree (name_);
CREATE INDEX IF NOT EXISTS act_idx_hi_detail_proc_inst ON public.act_hi_detail USING btree (proc_inst_id_);
CREATE INDEX IF NOT EXISTS act_idx_hi_detail_task_id ON public.act_hi_detail USING btree (task_id_);
CREATE INDEX IF NOT EXISTS act_idx_hi_detail_time ON public.act_hi_detail USING btree (time_);
CREATE INDEX IF NOT EXISTS act_idx_hi_ent_lnk_ref_scope ON public.act_hi_entitylink USING btree (ref_scope_id_, ref_scope_type_, link_type_);
CREATE INDEX IF NOT EXISTS act_idx_hi_ent_lnk_root_scope ON public.act_hi_entitylink USING btree (root_scope_id_, root_scope_type_, link_type_);
CREATE INDEX IF NOT EXISTS act_idx_hi_ent_lnk_scope ON public.act_hi_entitylink USING btree (scope_id_, scope_type_, link_type_);
CREATE INDEX IF NOT EXISTS act_idx_hi_ent_lnk_scope_def ON public.act_hi_entitylink USING btree (scope_definition_id_, scope_type_, link_type_);
CREATE INDEX IF NOT EXISTS act_idx_hi_ident_lnk_procinst ON public.act_hi_identitylink USING btree (proc_inst_id_);
CREATE INDEX IF NOT EXISTS act_idx_hi_ident_lnk_scope ON public.act_hi_identitylink USING btree (scope_id_, scope_type_);
CREATE INDEX IF NOT EXISTS act_idx_hi_ident_lnk_scope_def ON public.act_hi_identitylink USING btree (scope_definition_id_, scope_type_);
CREATE INDEX IF NOT EXISTS act_idx_hi_ident_lnk_sub_scope ON public.act_hi_identitylink USING btree (sub_scope_id_, scope_type_);
CREATE INDEX IF NOT EXISTS act_idx_hi_ident_lnk_task ON public.act_hi_identitylink USING btree (task_id_);
CREATE INDEX IF NOT EXISTS act_idx_hi_ident_lnk_user ON public.act_hi_identitylink USING btree (user_id_);
CREATE INDEX IF NOT EXISTS act_idx_hi_pro_i_buskey ON public.act_hi_procinst USING btree (business_key_);
CREATE INDEX IF NOT EXISTS act_idx_hi_pro_inst_end ON public.act_hi_procinst USING btree (end_time_);
CREATE INDEX IF NOT EXISTS act_idx_hi_pro_super_procinst ON public.act_hi_procinst USING btree (super_process_instance_id_);
CREATE INDEX IF NOT EXISTS act_idx_hi_procvar_exe ON public.act_hi_varinst USING btree (execution_id_);
CREATE INDEX IF NOT EXISTS act_idx_hi_procvar_name_type ON public.act_hi_varinst USING btree (name_, var_type_);
CREATE INDEX IF NOT EXISTS act_idx_hi_procvar_proc_inst ON public.act_hi_varinst USING btree (proc_inst_id_);
CREATE INDEX IF NOT EXISTS act_idx_hi_procvar_task_id ON public.act_hi_varinst USING btree (task_id_);
CREATE INDEX IF NOT EXISTS act_idx_hi_task_inst_procinst ON public.act_hi_taskinst USING btree (proc_inst_id_);
CREATE INDEX IF NOT EXISTS act_idx_hi_task_scope ON public.act_hi_taskinst USING btree (scope_id_, scope_type_);
CREATE INDEX IF NOT EXISTS act_idx_hi_task_scope_def ON public.act_hi_taskinst USING btree (scope_definition_id_, scope_type_);
CREATE INDEX IF NOT EXISTS act_idx_hi_task_sub_scope ON public.act_hi_taskinst USING btree (sub_scope_id_, scope_type_);
CREATE INDEX IF NOT EXISTS act_idx_hi_var_scope_id_type ON public.act_hi_varinst USING btree (scope_id_, scope_type_);
CREATE INDEX IF NOT EXISTS act_idx_hi_var_sub_id_type ON public.act_hi_varinst USING btree (sub_scope_id_, scope_type_);
CREATE INDEX IF NOT EXISTS act_idx_ident_lnk_group ON public.act_ru_identitylink USING btree (group_id_);
CREATE INDEX IF NOT EXISTS act_idx_ident_lnk_scope ON public.act_ru_identitylink USING btree (scope_id_, scope_type_);
CREATE INDEX IF NOT EXISTS act_idx_ident_lnk_scope_def ON public.act_ru_identitylink USING btree (scope_definition_id_, scope_type_);
CREATE INDEX IF NOT EXISTS act_idx_ident_lnk_sub_scope ON public.act_ru_identitylink USING btree (sub_scope_id_, scope_type_);
CREATE INDEX IF NOT EXISTS act_idx_ident_lnk_user ON public.act_ru_identitylink USING btree (user_id_);
CREATE INDEX IF NOT EXISTS act_idx_idl_procinst ON public.act_ru_identitylink USING btree (proc_inst_id_);
CREATE INDEX IF NOT EXISTS act_idx_job_correlation_id ON public.act_ru_job USING btree (correlation_id_);
CREATE INDEX IF NOT EXISTS act_idx_job_custom_values_id ON public.act_ru_job USING btree (custom_values_id_);
CREATE INDEX IF NOT EXISTS act_idx_job_exception_stack_id ON public.act_ru_job USING btree (exception_stack_id_);
CREATE INDEX IF NOT EXISTS act_idx_job_execution_id ON public.act_ru_job USING btree (execution_id_);
CREATE INDEX IF NOT EXISTS act_idx_job_proc_def_id ON public.act_ru_job USING btree (proc_def_id_);
CREATE INDEX IF NOT EXISTS act_idx_job_process_instance_id ON public.act_ru_job USING btree (process_instance_id_);
CREATE INDEX IF NOT EXISTS act_idx_job_scope ON public.act_ru_job USING btree (scope_id_, scope_type_);
CREATE INDEX IF NOT EXISTS act_idx_job_scope_def ON public.act_ru_job USING btree (scope_definition_id_, scope_type_);
CREATE INDEX IF NOT EXISTS act_idx_job_sub_scope ON public.act_ru_job USING btree (sub_scope_id_, scope_type_);
CREATE INDEX IF NOT EXISTS act_idx_memb_group ON public.act_id_membership USING btree (group_id_);
CREATE INDEX IF NOT EXISTS act_idx_memb_user ON public.act_id_membership USING btree (user_id_);
CREATE INDEX IF NOT EXISTS act_idx_mil_case_def ON public.act_cmmn_ru_mil_inst USING btree (case_def_id_);
CREATE INDEX IF NOT EXISTS act_idx_mil_case_inst ON public.act_cmmn_ru_mil_inst USING btree (case_inst_id_);
CREATE INDEX IF NOT EXISTS act_idx_model_deployment ON public.act_re_model USING btree (deployment_id_);
CREATE INDEX IF NOT EXISTS act_idx_model_source ON public.act_re_model USING btree (editor_source_value_id_);
CREATE INDEX IF NOT EXISTS act_idx_model_source_extra ON public.act_re_model USING btree (editor_source_extra_value_id_);
CREATE INDEX IF NOT EXISTS act_idx_plan_item_case_def ON public.act_cmmn_ru_plan_item_inst USING btree (case_def_id_);
CREATE INDEX IF NOT EXISTS act_idx_plan_item_case_inst ON public.act_cmmn_ru_plan_item_inst USING btree (case_inst_id_);
CREATE INDEX IF NOT EXISTS act_idx_plan_item_stage_inst ON public.act_cmmn_ru_plan_item_inst USING btree (stage_inst_id_);
CREATE INDEX IF NOT EXISTS act_idx_priv_group ON public.act_id_priv_mapping USING btree (group_id_);
CREATE INDEX IF NOT EXISTS act_idx_priv_mapping ON public.act_id_priv_mapping USING btree (priv_id_);
CREATE INDEX IF NOT EXISTS act_idx_priv_user ON public.act_id_priv_mapping USING btree (user_id_);
CREATE INDEX IF NOT EXISTS act_idx_procdef_info_json ON public.act_procdef_info USING btree (info_json_id_);
CREATE INDEX IF NOT EXISTS act_idx_procdef_info_proc ON public.act_procdef_info USING btree (proc_def_id_);
CREATE INDEX IF NOT EXISTS act_idx_ru_acti_end ON public.act_ru_actinst USING btree (end_time_);
CREATE INDEX IF NOT EXISTS act_idx_ru_acti_exec ON public.act_ru_actinst USING btree (execution_id_);
CREATE INDEX IF NOT EXISTS act_idx_ru_acti_exec_act ON public.act_ru_actinst USING btree (execution_id_, act_id_);
CREATE INDEX IF NOT EXISTS act_idx_ru_acti_proc ON public.act_ru_actinst USING btree (proc_inst_id_);
CREATE INDEX IF NOT EXISTS act_idx_ru_acti_proc_act ON public.act_ru_actinst USING btree (proc_inst_id_, act_id_);
CREATE INDEX IF NOT EXISTS act_idx_ru_acti_start ON public.act_ru_actinst USING btree (start_time_);
CREATE INDEX IF NOT EXISTS act_idx_ru_acti_task ON public.act_ru_actinst USING btree (task_id_);
CREATE INDEX IF NOT EXISTS act_idx_ru_var_scope_id_type ON public.act_ru_variable USING btree (scope_id_, scope_type_);
CREATE INDEX IF NOT EXISTS act_idx_ru_var_sub_id_type ON public.act_ru_variable USING btree (sub_scope_id_, scope_type_);
CREATE INDEX IF NOT EXISTS act_idx_sentry_case_def ON public.act_cmmn_ru_sentry_part_inst USING btree (case_def_id_);
CREATE INDEX IF NOT EXISTS act_idx_sentry_case_inst ON public.act_cmmn_ru_sentry_part_inst USING btree (case_inst_id_);
CREATE INDEX IF NOT EXISTS act_idx_sentry_plan_item ON public.act_cmmn_ru_sentry_part_inst USING btree (plan_item_inst_id_);
CREATE INDEX IF NOT EXISTS act_idx_sjob_scope ON public.act_ru_suspended_job USING btree (scope_id_, scope_type_);
CREATE INDEX IF NOT EXISTS act_idx_sjob_scope_def ON public.act_ru_suspended_job USING btree (scope_definition_id_, scope_type_);
CREATE INDEX IF NOT EXISTS act_idx_sjob_sub_scope ON public.act_ru_suspended_job USING btree (sub_scope_id_, scope_type_);
CREATE INDEX IF NOT EXISTS act_idx_suspended_job_correlation_id ON public.act_ru_suspended_job USING btree (correlation_id_);
CREATE INDEX IF NOT EXISTS act_idx_suspended_job_custom_values_id ON public.act_ru_suspended_job USING btree (custom_values_id_);
CREATE INDEX IF NOT EXISTS act_idx_suspended_job_exception_stack_id ON public.act_ru_suspended_job USING btree (exception_stack_id_);
CREATE INDEX IF NOT EXISTS act_idx_suspended_job_execution_id ON public.act_ru_suspended_job USING btree (execution_id_);
CREATE INDEX IF NOT EXISTS act_idx_suspended_job_proc_def_id ON public.act_ru_suspended_job USING btree (proc_def_id_);
CREATE INDEX IF NOT EXISTS act_idx_suspended_job_process_instance_id ON public.act_ru_suspended_job USING btree (process_instance_id_);
CREATE INDEX IF NOT EXISTS act_idx_task_create ON public.act_ru_task USING btree (create_time_);
CREATE INDEX IF NOT EXISTS act_idx_task_exec ON public.act_ru_task USING btree (execution_id_);
CREATE INDEX IF NOT EXISTS act_idx_task_procdef ON public.act_ru_task USING btree (proc_def_id_);
CREATE INDEX IF NOT EXISTS act_idx_task_procinst ON public.act_ru_task USING btree (proc_inst_id_);
CREATE INDEX IF NOT EXISTS act_idx_task_scope ON public.act_ru_task USING btree (scope_id_, scope_type_);
CREATE INDEX IF NOT EXISTS act_idx_task_scope_def ON public.act_ru_task USING btree (scope_definition_id_, scope_type_);
CREATE INDEX IF NOT EXISTS act_idx_task_sub_scope ON public.act_ru_task USING btree (sub_scope_id_, scope_type_);
CREATE INDEX IF NOT EXISTS act_idx_timer_job_correlation_id ON public.act_ru_timer_job USING btree (correlation_id_);
CREATE INDEX IF NOT EXISTS act_idx_timer_job_custom_values_id ON public.act_ru_timer_job USING btree (custom_values_id_);
CREATE INDEX IF NOT EXISTS act_idx_timer_job_duedate ON public.act_ru_timer_job USING btree (duedate_);
CREATE INDEX IF NOT EXISTS act_idx_timer_job_exception_stack_id ON public.act_ru_timer_job USING btree (exception_stack_id_);
CREATE INDEX IF NOT EXISTS act_idx_timer_job_execution_id ON public.act_ru_timer_job USING btree (execution_id_);
CREATE INDEX IF NOT EXISTS act_idx_timer_job_proc_def_id ON public.act_ru_timer_job USING btree (proc_def_id_);
CREATE INDEX IF NOT EXISTS act_idx_timer_job_process_instance_id ON public.act_ru_timer_job USING btree (process_instance_id_);
CREATE INDEX IF NOT EXISTS act_idx_tjob_scope ON public.act_ru_timer_job USING btree (scope_id_, scope_type_);
CREATE INDEX IF NOT EXISTS act_idx_tjob_scope_def ON public.act_ru_timer_job USING btree (scope_definition_id_, scope_type_);
CREATE INDEX IF NOT EXISTS act_idx_tjob_sub_scope ON public.act_ru_timer_job USING btree (sub_scope_id_, scope_type_);
CREATE INDEX IF NOT EXISTS act_idx_tskass_task ON public.act_ru_identitylink USING btree (task_id_);
CREATE INDEX IF NOT EXISTS act_idx_var_bytearray ON public.act_ru_variable USING btree (bytearray_id_);
CREATE INDEX IF NOT EXISTS act_idx_var_exe ON public.act_ru_variable USING btree (execution_id_);
CREATE INDEX IF NOT EXISTS act_idx_var_procinst ON public.act_ru_variable USING btree (proc_inst_id_);
CREATE INDEX IF NOT EXISTS act_idx_variable_task_id ON public.act_ru_variable USING btree (task_id_);
CREATE INDEX IF NOT EXISTS flw_idx_batch_part ON public.flw_ru_batch_part USING btree (batch_id_);
CREATE INDEX IF NOT EXISTS idx_alert_severity ON public.admin_alerts USING btree (severity);
CREATE INDEX IF NOT EXISTS idx_alert_status ON public.admin_alerts USING btree (status);
CREATE INDEX IF NOT EXISTS idx_assignment_target ON public.wf_extended_task_info USING btree (assignment_target);
CREATE INDEX IF NOT EXISTS idx_assignment_type ON public.wf_extended_task_info USING btree (assignment_type);
CREATE INDEX IF NOT EXISTS idx_audit_action ON public.admin_audit_logs USING btree (action);
CREATE INDEX IF NOT EXISTS idx_audit_composite ON public.wf_audit_logs USING btree (user_id, operation_type, "timestamp");
CREATE INDEX IF NOT EXISTS idx_audit_ip_address ON public.wf_audit_logs USING btree (ip_address);
CREATE INDEX IF NOT EXISTS idx_audit_operation_type ON public.wf_audit_logs USING btree (operation_type);
CREATE INDEX IF NOT EXISTS idx_audit_resource ON public.admin_audit_logs USING btree (resource_type, resource_id);
CREATE INDEX IF NOT EXISTS idx_audit_resource_id ON public.wf_audit_logs USING btree (resource_id);
CREATE INDEX IF NOT EXISTS idx_audit_resource_type ON public.wf_audit_logs USING btree (resource_type);
CREATE INDEX IF NOT EXISTS idx_audit_session_id ON public.wf_audit_logs USING btree (session_id);
CREATE INDEX IF NOT EXISTS idx_audit_tenant_id ON public.wf_audit_logs USING btree (tenant_id);
CREATE INDEX IF NOT EXISTS idx_audit_timestamp ON public.admin_audit_logs USING btree ("timestamp");
CREATE INDEX IF NOT EXISTS idx_audit_user ON public.admin_audit_logs USING btree (user_id);
CREATE INDEX IF NOT EXISTS idx_audit_user_id ON public.wf_audit_logs USING btree (user_id);
CREATE INDEX IF NOT EXISTS idx_claimed_by ON public.wf_extended_task_info USING btree (claimed_by);
CREATE INDEX IF NOT EXISTS idx_col_perm_column ON public.admin_column_permissions USING btree (column_name);
CREATE INDEX IF NOT EXISTS idx_col_perm_rule ON public.admin_column_permissions USING btree (rule_id);
CREATE INDEX IF NOT EXISTS idx_config_category ON public.admin_system_configs USING btree (category);
CREATE INDEX IF NOT EXISTS idx_config_history_key ON public.admin_config_history USING btree (config_key);
CREATE INDEX IF NOT EXISTS idx_config_history_time ON public.admin_config_history USING btree (changed_at);
CREATE INDEX IF NOT EXISTS idx_config_key ON public.admin_system_configs USING btree (config_key);
CREATE INDEX IF NOT EXISTS idx_created_time ON public.wf_extended_task_info USING btree (created_time);
CREATE INDEX IF NOT EXISTS idx_delegated_to ON public.wf_extended_task_info USING btree (delegated_to);
CREATE INDEX IF NOT EXISTS idx_dev_role_perm_role ON public.sys_developer_role_permissions USING btree (role_id);
CREATE INDEX IF NOT EXISTS idx_dict_code ON public.sys_dictionaries USING btree (code);
CREATE INDEX IF NOT EXISTS idx_dict_ds_dict_id ON public.sys_dictionary_data_sources USING btree (dictionary_id);
CREATE INDEX IF NOT EXISTS idx_dict_item_code ON public.sys_dictionary_items USING btree (dictionary_id, item_code);
CREATE INDEX IF NOT EXISTS idx_dict_item_dict_id ON public.sys_dictionary_items USING btree (dictionary_id);
CREATE INDEX IF NOT EXISTS idx_dict_item_parent ON public.sys_dictionary_items USING btree (parent_id);
CREATE INDEX IF NOT EXISTS idx_dict_item_status ON public.sys_dictionary_items USING btree (status);
CREATE INDEX IF NOT EXISTS idx_dict_status ON public.sys_dictionaries USING btree (status);
CREATE INDEX IF NOT EXISTS idx_dict_type ON public.sys_dictionaries USING btree (type);
CREATE INDEX IF NOT EXISTS idx_dict_ver_dict_id ON public.sys_dictionary_versions USING btree (dictionary_id);
CREATE INDEX IF NOT EXISTS idx_dict_ver_version ON public.sys_dictionary_versions USING btree (dictionary_id, version);
CREATE INDEX IF NOT EXISTS idx_dp_rule_target ON public.admin_data_permission_rules USING btree (target_type, target_id);
CREATE INDEX IF NOT EXISTS idx_dp_rule_type ON public.admin_data_permission_rules USING btree (permission_type);
CREATE INDEX IF NOT EXISTS idx_due_date ON public.wf_extended_task_info USING btree (due_date);
CREATE INDEX IF NOT EXISTS idx_dw_action_definitions_fu ON public.dw_action_definitions USING btree (function_unit_id);
CREATE INDEX IF NOT EXISTS idx_dw_field_definitions_table ON public.dw_field_definitions USING btree (table_id);
CREATE INDEX IF NOT EXISTS idx_dw_foreign_keys_table ON public.dw_foreign_keys USING btree (table_id);
CREATE INDEX IF NOT EXISTS idx_dw_form_definitions_fu ON public.dw_form_definitions USING btree (function_unit_id);
CREATE INDEX IF NOT EXISTS idx_dw_form_table_bindings_form ON public.dw_form_table_bindings USING btree (form_id);
CREATE INDEX IF NOT EXISTS idx_dw_form_table_bindings_table ON public.dw_form_table_bindings USING btree (table_id);
CREATE INDEX IF NOT EXISTS idx_dw_function_units_name ON public.dw_function_units USING btree (name);
CREATE INDEX IF NOT EXISTS idx_dw_function_units_status ON public.dw_function_units USING btree (status);
CREATE INDEX IF NOT EXISTS idx_dw_icons_category ON public.dw_icons USING btree (category);
CREATE INDEX IF NOT EXISTS idx_dw_icons_name ON public.dw_icons USING btree (name);
CREATE INDEX IF NOT EXISTS idx_dw_operation_logs_operator ON public.dw_operation_logs USING btree (operator);
CREATE INDEX IF NOT EXISTS idx_dw_operation_logs_target ON public.dw_operation_logs USING btree (target_type, target_id);
CREATE INDEX IF NOT EXISTS idx_dw_operation_logs_time ON public.dw_operation_logs USING btree (operation_time);
CREATE INDEX IF NOT EXISTS idx_dw_process_definitions_fu ON public.dw_process_definitions USING btree (function_unit_id);
CREATE INDEX IF NOT EXISTS idx_dw_table_definitions_fu ON public.dw_table_definitions USING btree (function_unit_id);
CREATE INDEX IF NOT EXISTS idx_dw_versions_fu ON public.dw_versions USING btree (function_unit_id);
CREATE INDEX IF NOT EXISTS idx_exception_occurred_time ON public.wf_exception_records USING btree (occurred_time);
CREATE INDEX IF NOT EXISTS idx_exception_process_instance ON public.wf_exception_records USING btree (process_instance_id);
CREATE INDEX IF NOT EXISTS idx_exception_resolved ON public.wf_exception_records USING btree (resolved);
CREATE INDEX IF NOT EXISTS idx_exception_severity ON public.wf_exception_records USING btree (severity);
CREATE INDEX IF NOT EXISTS idx_exception_status ON public.wf_exception_records USING btree (status);
CREATE INDEX IF NOT EXISTS idx_exception_task_id ON public.wf_exception_records USING btree (task_id);
CREATE INDEX IF NOT EXISTS idx_exception_type ON public.wf_exception_records USING btree (exception_type);
CREATE INDEX IF NOT EXISTS idx_fu_access_func_unit ON public.sys_function_unit_access USING btree (function_unit_id);
CREATE INDEX IF NOT EXISTS idx_fu_approval_deployment ON public.sys_function_unit_approvals USING btree (deployment_id);
CREATE INDEX IF NOT EXISTS idx_fu_content_func_unit ON public.sys_function_unit_contents USING btree (function_unit_id);
CREATE INDEX IF NOT EXISTS idx_fu_dependency_func_unit ON public.sys_function_unit_dependencies USING btree (function_unit_id);
CREATE INDEX IF NOT EXISTS idx_fu_deployment_func_unit ON public.sys_function_unit_deployments USING btree (function_unit_id);
CREATE INDEX IF NOT EXISTS idx_fu_deployment_status ON public.sys_function_unit_deployments USING btree (status);
CREATE INDEX IF NOT EXISTS idx_function_unit_code ON public.dw_function_units USING btree (code);
CREATE INDEX IF NOT EXISTS idx_log_level ON public.admin_system_logs USING btree (log_level);
CREATE INDEX IF NOT EXISTS idx_log_module ON public.admin_system_logs USING btree (module);
CREATE INDEX IF NOT EXISTS idx_log_timestamp ON public.admin_system_logs USING btree ("timestamp");
CREATE INDEX IF NOT EXISTS idx_log_type ON public.admin_system_logs USING btree (log_type);
CREATE INDEX IF NOT EXISTS idx_log_user ON public.admin_system_logs USING btree (user_id);
CREATE INDEX IF NOT EXISTS idx_login_audit_action ON public.sys_login_audit USING btree (action);
CREATE INDEX IF NOT EXISTS idx_login_audit_created ON public.sys_login_audit USING btree (created_at);
CREATE INDEX IF NOT EXISTS idx_login_audit_user ON public.sys_login_audit USING btree (user_id);
CREATE INDEX IF NOT EXISTS idx_login_audit_username ON public.sys_login_audit USING btree (username);
CREATE INDEX IF NOT EXISTS idx_priority ON public.wf_extended_task_info USING btree (priority);
CREATE INDEX IF NOT EXISTS idx_process_instance ON public.wf_extended_task_info USING btree (process_instance_id);
CREATE INDEX IF NOT EXISTS idx_role_assignments_role ON public.sys_role_assignments USING btree (role_id);
CREATE INDEX IF NOT EXISTS idx_role_assignments_target ON public.sys_role_assignments USING btree (target_type, target_id);
CREATE INDEX IF NOT EXISTS idx_role_assignments_valid ON public.sys_role_assignments USING btree (valid_from, valid_to);
CREATE INDEX IF NOT EXISTS idx_status ON public.wf_extended_task_info USING btree (status);
CREATE INDEX IF NOT EXISTS idx_sys_permissions_parent ON public.sys_permissions USING btree (parent_id);
CREATE INDEX IF NOT EXISTS idx_sys_roles_code ON public.sys_roles USING btree (code);
CREATE INDEX IF NOT EXISTS idx_sys_roles_type ON public.sys_roles USING btree (type);
CREATE INDEX IF NOT EXISTS idx_sys_user_roles_role ON public.sys_user_roles USING btree (role_id);
CREATE INDEX IF NOT EXISTS idx_sys_user_roles_user ON public.sys_user_roles USING btree (user_id);
CREATE INDEX IF NOT EXISTS idx_sys_users_deleted ON public.sys_users USING btree (deleted);
CREATE INDEX IF NOT EXISTS idx_sys_users_email ON public.sys_users USING btree (email);
CREATE INDEX IF NOT EXISTS idx_sys_users_employee_id ON public.sys_users USING btree (employee_id);
CREATE INDEX IF NOT EXISTS idx_sys_users_entity_manager ON public.sys_users USING btree (entity_manager_id);
CREATE INDEX IF NOT EXISTS idx_sys_users_function_manager ON public.sys_users USING btree (function_manager_id);
CREATE INDEX IF NOT EXISTS idx_sys_users_status ON public.sys_users USING btree (status);
CREATE INDEX IF NOT EXISTS idx_sys_users_username ON public.sys_users USING btree (username);
CREATE INDEX IF NOT EXISTS idx_variable_created_time ON public.wf_process_variables USING btree (created_time);
CREATE INDEX IF NOT EXISTS idx_variable_name ON public.wf_process_variables USING btree (name);
CREATE INDEX IF NOT EXISTS idx_variable_proc_inst ON public.wf_process_variables USING btree (process_instance_id);
CREATE INDEX IF NOT EXISTS idx_variable_task ON public.wf_process_variables USING btree (task_id);
CREATE INDEX IF NOT EXISTS idx_vg_task_history_action ON public.sys_virtual_group_task_history USING btree (action_type);
CREATE INDEX IF NOT EXISTS idx_vg_task_history_created ON public.sys_virtual_group_task_history USING btree (created_at);
CREATE INDEX IF NOT EXISTS idx_vg_task_history_group ON public.sys_virtual_group_task_history USING btree (group_id);
CREATE INDEX IF NOT EXISTS idx_vg_task_history_task ON public.sys_virtual_group_task_history USING btree (task_id);
CREATE INDEX IF NOT EXISTS idx_wf_audit_process ON public.wf_audit_logs USING btree (process_instance_id);
CREATE INDEX IF NOT EXISTS idx_wf_audit_user ON public.wf_audit_logs USING btree (user_id);
CREATE INDEX IF NOT EXISTS idx_wf_exception_handled ON public.wf_exception_records USING btree (handled);
CREATE INDEX IF NOT EXISTS idx_wf_exception_process ON public.wf_exception_records USING btree (process_instance_id);
CREATE INDEX IF NOT EXISTS idx_wf_saga_status ON public.wf_saga_transactions USING btree (status);
CREATE INDEX IF NOT EXISTS idx_wf_saga_steps_saga ON public.wf_saga_steps USING btree (saga_id);
CREATE INDEX IF NOT EXISTS idx_wf_saga_type ON public.wf_saga_transactions USING btree (saga_type);
CREATE INDEX IF NOT EXISTS idx_wf_task_id ON public.wf_extended_task_info USING btree (task_id);
CREATE INDEX IF NOT EXISTS idx_wf_task_process ON public.wf_extended_task_info USING btree (process_instance_id);
CREATE INDEX IF NOT EXISTS idx_wf_var_name ON public.wf_process_variables USING btree (name);
CREATE INDEX IF NOT EXISTS idx_wf_var_process ON public.wf_process_variables USING btree (process_instance_id);

-- =====================================================
-- 第四步：创建序列
-- =====================================================
CREATE SEQUENCE IF NOT EXISTS public.act_evt_log_log_nr__seq AS integer START WITH 1 INCREMENT BY 1 NO MINVALUE NO MAXVALUE CACHE 1;
CREATE SEQUENCE IF NOT EXISTS public.act_hi_tsk_log_id__seq AS integer START WITH 1 INCREMENT BY 1 NO MINVALUE NO MAXVALUE CACHE 1;
CREATE SEQUENCE IF NOT EXISTS public.dw_action_definitions_id_seq AS integer START WITH 1 INCREMENT BY 1 NO MINVALUE NO MAXVALUE CACHE 1;
CREATE SEQUENCE IF NOT EXISTS public.dw_field_definitions_id_seq AS integer START WITH 1 INCREMENT BY 1 NO MINVALUE NO MAXVALUE CACHE 1;
CREATE SEQUENCE IF NOT EXISTS public.dw_foreign_keys_id_seq AS integer START WITH 1 INCREMENT BY 1 NO MINVALUE NO MAXVALUE CACHE 1;
CREATE SEQUENCE IF NOT EXISTS public.dw_form_definitions_id_seq AS integer START WITH 1 INCREMENT BY 1 NO MINVALUE NO MAXVALUE CACHE 1;
CREATE SEQUENCE IF NOT EXISTS public.dw_form_table_bindings_id_seq AS integer START WITH 1 INCREMENT BY 1 NO MINVALUE NO MAXVALUE CACHE 1;
CREATE SEQUENCE IF NOT EXISTS public.dw_function_units_id_seq AS integer START WITH 1 INCREMENT BY 1 NO MINVALUE NO MAXVALUE CACHE 1;
CREATE SEQUENCE IF NOT EXISTS public.dw_icons_id_seq AS integer START WITH 1 INCREMENT BY 1 NO MINVALUE NO MAXVALUE CACHE 1;
CREATE SEQUENCE IF NOT EXISTS public.dw_operation_logs_id_seq AS integer START WITH 1 INCREMENT BY 1 NO MINVALUE NO MAXVALUE CACHE 1;
CREATE SEQUENCE IF NOT EXISTS public.dw_process_definitions_id_seq AS integer START WITH 1 INCREMENT BY 1 NO MINVALUE NO MAXVALUE CACHE 1;
CREATE SEQUENCE IF NOT EXISTS public.dw_table_definitions_id_seq AS integer START WITH 1 INCREMENT BY 1 NO MINVALUE NO MAXVALUE CACHE 1;
CREATE SEQUENCE IF NOT EXISTS public.dw_versions_id_seq AS integer START WITH 1 INCREMENT BY 1 NO MINVALUE NO MAXVALUE CACHE 1;
CREATE SEQUENCE IF NOT EXISTS public.up_dashboard_layout_id_seq AS integer START WITH 1 INCREMENT BY 1 NO MINVALUE NO MAXVALUE CACHE 1;
CREATE SEQUENCE IF NOT EXISTS public.up_delegation_audit_id_seq AS integer START WITH 1 INCREMENT BY 1 NO MINVALUE NO MAXVALUE CACHE 1;
CREATE SEQUENCE IF NOT EXISTS public.up_delegation_rule_id_seq AS integer START WITH 1 INCREMENT BY 1 NO MINVALUE NO MAXVALUE CACHE 1;
CREATE SEQUENCE IF NOT EXISTS public.up_favorite_process_id_seq AS integer START WITH 1 INCREMENT BY 1 NO MINVALUE NO MAXVALUE CACHE 1;
CREATE SEQUENCE IF NOT EXISTS public.up_notification_preference_id_seq AS integer START WITH 1 INCREMENT BY 1 NO MINVALUE NO MAXVALUE CACHE 1;
CREATE SEQUENCE IF NOT EXISTS public.up_permission_request_id_seq AS integer START WITH 1 INCREMENT BY 1 NO MINVALUE NO MAXVALUE CACHE 1;
CREATE SEQUENCE IF NOT EXISTS public.up_process_draft_id_seq AS integer START WITH 1 INCREMENT BY 1 NO MINVALUE NO MAXVALUE CACHE 1;
CREATE SEQUENCE IF NOT EXISTS public.up_process_history_id_seq AS integer START WITH 1 INCREMENT BY 1 NO MINVALUE NO MAXVALUE CACHE 1;
CREATE SEQUENCE IF NOT EXISTS public.up_user_preference_id_seq AS integer START WITH 1 INCREMENT BY 1 NO MINVALUE NO MAXVALUE CACHE 1;
CREATE SEQUENCE IF NOT EXISTS public.wf_extended_task_info_id_seq AS integer START WITH 1 INCREMENT BY 1 NO MINVALUE NO MAXVALUE CACHE 1;

-- =====================================================
-- 第五步：插入数据
-- =====================================================
INSERT INTO public.act_app_databasechangelog (id, author, filename, dateexecuted, orderexecuted, exectype, md5sum, description, comments, tag, liquibase, contexts, labels, deployment_id) VALUES ('1', 'flowable', 'org/flowable/app/db/liquibase/flowable-app-db-changelog.xml', '2026-01-14 17:11:36.881182', 1, 'EXECUTED', '9:959783069c0c7ce80320a0617aa48969', 'createTable tableName=ACT_APP_DEPLOYMENT;

-- =====================================================
-- 第五步：插入数据
-- =====================================================
INSERT INTO public.act_app_databasechangelog (id, author, filename, dateexecuted, orderexecuted, exectype, md5sum, description, comments, tag, liquibase, contexts, labels, deployment_id) VALUES ('1', 'flowable', 'org/flowable/app/db/liquibase/flowable-app-db-changelog.xml', '2026-01-14 17:11:36.881182', 1, 'EXECUTED', '9:959783069c0c7ce80320a0617aa48969', 'createTable tableName=ACT_APP_DEPLOYMENT;
INSERT INTO public.act_app_databasechangelog (id, author, filename, dateexecuted, orderexecuted, exectype, md5sum, description, comments, tag, liquibase, contexts, labels, deployment_id) VALUES ('3', 'flowable', 'org/flowable/app/db/liquibase/flowable-app-db-changelog.xml', '2026-01-14 17:11:36.897756', 2, 'EXECUTED', '9:c05b79a3b00e95136533085718361208', 'createIndex indexName=ACT_IDX_APP_DEF_UNIQ, tableName=ACT_APP_APPDEF', '', NULL, '4.24.0', NULL, NULL, '8381896797');
INSERT INTO public.act_app_databasechangeloglock (id, locked, lockgranted, lockedby) VALUES (1, false, NULL, NULL);
INSERT INTO public.act_cmmn_databasechangelog (id, author, filename, dateexecuted, orderexecuted, exectype, md5sum, description, comments, tag, liquibase, contexts, labels, deployment_id) VALUES ('1', 'flowable', 'org/flowable/cmmn/db/liquibase/flowable-cmmn-db-changelog.xml', '2026-01-14 17:11:36.284078', 1, 'EXECUTED', '9:d0cc0aaadf0e4ef70c5b412cd05fadc4', 'createTable tableName=ACT_CMMN_DEPLOYMENT;
INSERT INTO public.act_cmmn_databasechangelog (id, author, filename, dateexecuted, orderexecuted, exectype, md5sum, description, comments, tag, liquibase, contexts, labels, deployment_id) VALUES ('2', 'flowable', 'org/flowable/cmmn/db/liquibase/flowable-cmmn-db-changelog.xml', '2026-01-14 17:11:36.316879', 2, 'EXECUTED', '9:8095a5a8a222a100c2d0310cacbda5e7', 'addColumn tableName=ACT_CMMN_CASEDEF;
INSERT INTO public.act_cmmn_databasechangelog (id, author, filename, dateexecuted, orderexecuted, exectype, md5sum, description, comments, tag, liquibase, contexts, labels, deployment_id) VALUES ('3', 'flowable', 'org/flowable/cmmn/db/liquibase/flowable-cmmn-db-changelog.xml', '2026-01-14 17:11:36.372838', 3, 'EXECUTED', '9:f031b4f0ae67bc5a640736b379049b12', 'addColumn tableName=ACT_CMMN_RU_PLAN_ITEM_INST;
INSERT INTO public.act_cmmn_databasechangelog (id, author, filename, dateexecuted, orderexecuted, exectype, md5sum, description, comments, tag, liquibase, contexts, labels, deployment_id) VALUES ('4', 'flowable', 'org/flowable/cmmn/db/liquibase/flowable-cmmn-db-changelog.xml', '2026-01-14 17:11:36.44873', 4, 'EXECUTED', '9:c484ecfb08719feccac2f80fc962dda9', 'createTable tableName=ACT_CMMN_HI_PLAN_ITEM_INST;
INSERT INTO public.act_cmmn_databasechangelog (id, author, filename, dateexecuted, orderexecuted, exectype, md5sum, description, comments, tag, liquibase, contexts, labels, deployment_id) VALUES ('6', 'flowable', 'org/flowable/cmmn/db/liquibase/flowable-cmmn-db-changelog.xml', '2026-01-14 17:11:36.469984', 5, 'EXECUTED', '9:7343ab247d959e5add9278b5386de833', 'createIndex indexName=ACT_IDX_CASE_DEF_UNIQ, tableName=ACT_CMMN_CASEDEF', '', NULL, '4.24.0', NULL, NULL, '8381896015');
INSERT INTO public.act_cmmn_databasechangelog (id, author, filename, dateexecuted, orderexecuted, exectype, md5sum, description, comments, tag, liquibase, contexts, labels, deployment_id) VALUES ('7', 'flowable', 'org/flowable/cmmn/db/liquibase/flowable-cmmn-db-changelog.xml', '2026-01-14 17:11:36.511524', 6, 'EXECUTED', '9:d73200db684b6cdb748cc03570d5d2e9', 'renameColumn newColumnName=CREATE_TIME_, oldColumnName=START_TIME_, tableName=ACT_CMMN_RU_PLAN_ITEM_INST;
INSERT INTO public.act_cmmn_databasechangelog (id, author, filename, dateexecuted, orderexecuted, exectype, md5sum, description, comments, tag, liquibase, contexts, labels, deployment_id) VALUES ('8', 'flowable', 'org/flowable/cmmn/db/liquibase/flowable-cmmn-db-changelog.xml', '2026-01-14 17:11:36.52187', 7, 'EXECUTED', '9:eda5e43816221f2d8554bfcc90f1c37e', 'addColumn tableName=ACT_CMMN_HI_PLAN_ITEM_INST', '', NULL, '4.24.0', NULL, NULL, '8381896015');
INSERT INTO public.act_cmmn_databasechangelog (id, author, filename, dateexecuted, orderexecuted, exectype, md5sum, description, comments, tag, liquibase, contexts, labels, deployment_id) VALUES ('9', 'flowable', 'org/flowable/cmmn/db/liquibase/flowable-cmmn-db-changelog.xml', '2026-01-14 17:11:36.539497', 8, 'EXECUTED', '9:c34685611779075a73caf8c380f078ea', 'addColumn tableName=ACT_CMMN_RU_PLAN_ITEM_INST;
INSERT INTO public.act_cmmn_databasechangelog (id, author, filename, dateexecuted, orderexecuted, exectype, md5sum, description, comments, tag, liquibase, contexts, labels, deployment_id) VALUES ('10', 'flowable', 'org/flowable/cmmn/db/liquibase/flowable-cmmn-db-changelog.xml', '2026-01-14 17:11:36.56556', 9, 'EXECUTED', '9:368e9472ad2348206205170d6c52d58e', 'addColumn tableName=ACT_CMMN_RU_CASE_INST;
INSERT INTO public.act_cmmn_databasechangelog (id, author, filename, dateexecuted, orderexecuted, exectype, md5sum, description, comments, tag, liquibase, contexts, labels, deployment_id) VALUES ('11', 'flowable', 'org/flowable/cmmn/db/liquibase/flowable-cmmn-db-changelog.xml', '2026-01-14 17:11:36.576827', 10, 'EXECUTED', '9:e54b50ceb2bcd5355ae4dfb56d9ff3ad', 'addColumn tableName=ACT_CMMN_RU_PLAN_ITEM_INST;
INSERT INTO public.act_cmmn_databasechangelog (id, author, filename, dateexecuted, orderexecuted, exectype, md5sum, description, comments, tag, liquibase, contexts, labels, deployment_id) VALUES ('12', 'flowable', 'org/flowable/cmmn/db/liquibase/flowable-cmmn-db-changelog.xml', '2026-01-14 17:11:36.587979', 11, 'EXECUTED', '9:f53f262768d04e74529f43fcd93429b0', 'addColumn tableName=ACT_CMMN_RU_CASE_INST', '', NULL, '4.24.0', NULL, NULL, '8381896015');
INSERT INTO public.act_cmmn_databasechangelog (id, author, filename, dateexecuted, orderexecuted, exectype, md5sum, description, comments, tag, liquibase, contexts, labels, deployment_id) VALUES ('13', 'flowable', 'org/flowable/cmmn/db/liquibase/flowable-cmmn-db-changelog.xml', '2026-01-14 17:11:36.602914', 12, 'EXECUTED', '9:64e7eafbe97997094654e83caea99895', 'addColumn tableName=ACT_CMMN_RU_PLAN_ITEM_INST;
INSERT INTO public.act_cmmn_databasechangelog (id, author, filename, dateexecuted, orderexecuted, exectype, md5sum, description, comments, tag, liquibase, contexts, labels, deployment_id) VALUES ('14', 'flowable', 'org/flowable/cmmn/db/liquibase/flowable-cmmn-db-changelog.xml', '2026-01-14 17:11:36.617188', 13, 'EXECUTED', '9:ab7d934abde497eac034701542e0a281', 'addColumn tableName=ACT_CMMN_RU_CASE_INST;
INSERT INTO public.act_cmmn_databasechangelog (id, author, filename, dateexecuted, orderexecuted, exectype, md5sum, description, comments, tag, liquibase, contexts, labels, deployment_id) VALUES ('16', 'flowable', 'org/flowable/cmmn/db/liquibase/flowable-cmmn-db-changelog.xml', '2026-01-14 17:11:36.631458', 14, 'EXECUTED', '9:03928d422e510959770e7a9daa5a993f', 'addColumn tableName=ACT_CMMN_RU_CASE_INST;
INSERT INTO public.act_cmmn_databasechangelog (id, author, filename, dateexecuted, orderexecuted, exectype, md5sum, description, comments, tag, liquibase, contexts, labels, deployment_id) VALUES ('17', 'flowable', 'org/flowable/cmmn/db/liquibase/flowable-cmmn-db-changelog.xml', '2026-01-14 17:11:36.643064', 15, 'EXECUTED', '9:f30304cf001d6eac78c793ea88cd5781', 'createIndex indexName=ACT_IDX_HI_CASE_INST_END, tableName=ACT_CMMN_HI_CASE_INST', '', NULL, '4.24.0', NULL, NULL, '8381896015');
INSERT INTO public.act_cmmn_databasechangeloglock (id, locked, lockgranted, lockedby) VALUES (1, false, NULL, NULL);
INSERT INTO public.act_dmn_databasechangelog (id, author, filename, dateexecuted, orderexecuted, exectype, md5sum, description, comments, tag, liquibase, contexts, labels, deployment_id) VALUES ('1', 'activiti', 'org/flowable/dmn/db/liquibase/flowable-dmn-db-changelog.xml', '2026-01-14 17:11:35.504255', 1, 'EXECUTED', '9:5b36e70aee5a2e42f6e7a62ea5fa681b', 'createTable tableName=ACT_DMN_DEPLOYMENT;
INSERT INTO public.act_dmn_databasechangelog (id, author, filename, dateexecuted, orderexecuted, exectype, md5sum, description, comments, tag, liquibase, contexts, labels, deployment_id) VALUES ('2', 'flowable', 'org/flowable/dmn/db/liquibase/flowable-dmn-db-changelog.xml', '2026-01-14 17:11:35.521245', 2, 'EXECUTED', '9:fd13fa3f7af55d2b72f763fc261da30d', 'createTable tableName=ACT_DMN_HI_DECISION_EXECUTION', '', NULL, '4.24.0', NULL, NULL, '8381895464');
INSERT INTO public.act_dmn_databasechangelog (id, author, filename, dateexecuted, orderexecuted, exectype, md5sum, description, comments, tag, liquibase, contexts, labels, deployment_id) VALUES ('3', 'flowable', 'org/flowable/dmn/db/liquibase/flowable-dmn-db-changelog.xml', '2026-01-14 17:11:35.53143', 3, 'EXECUTED', '9:9f30e6a3557d4b4c713dbb2dcc141782', 'addColumn tableName=ACT_DMN_HI_DECISION_EXECUTION', '', NULL, '4.24.0', NULL, NULL, '8381895464');
INSERT INTO public.act_dmn_databasechangelog (id, author, filename, dateexecuted, orderexecuted, exectype, md5sum, description, comments, tag, liquibase, contexts, labels, deployment_id) VALUES ('4', 'flowable', 'org/flowable/dmn/db/liquibase/flowable-dmn-db-changelog.xml', '2026-01-14 17:11:35.542009', 4, 'EXECUTED', '9:41085fbde807dba96104ee75a2fcc4cc', 'dropColumn columnName=PARENT_DEPLOYMENT_ID_, tableName=ACT_DMN_DECISION_TABLE', '', NULL, '4.24.0', NULL, NULL, '8381895464');
INSERT INTO public.act_dmn_databasechangelog (id, author, filename, dateexecuted, orderexecuted, exectype, md5sum, description, comments, tag, liquibase, contexts, labels, deployment_id) VALUES ('6', 'flowable', 'org/flowable/dmn/db/liquibase/flowable-dmn-db-changelog.xml', '2026-01-14 17:11:35.552966', 5, 'EXECUTED', '9:f00f92f3ef1af3fc1604f0323630f9b1', 'createIndex indexName=ACT_IDX_DEC_TBL_UNIQ, tableName=ACT_DMN_DECISION_TABLE', '', NULL, '4.24.0', NULL, NULL, '8381895464');
INSERT INTO public.act_dmn_databasechangelog (id, author, filename, dateexecuted, orderexecuted, exectype, md5sum, description, comments, tag, liquibase, contexts, labels, deployment_id) VALUES ('7', 'flowable', 'org/flowable/dmn/db/liquibase/flowable-dmn-db-changelog.xml', '2026-01-14 17:11:35.570229', 6, 'EXECUTED', '9:d24d4c5f44083b4edf1231a7a682a2cd', 'dropIndex indexName=ACT_IDX_DEC_TBL_UNIQ, tableName=ACT_DMN_DECISION_TABLE;
INSERT INTO public.act_dmn_databasechangelog (id, author, filename, dateexecuted, orderexecuted, exectype, md5sum, description, comments, tag, liquibase, contexts, labels, deployment_id) VALUES ('8', 'flowable', 'org/flowable/dmn/db/liquibase/flowable-dmn-db-changelog.xml', '2026-01-14 17:11:35.578836', 7, 'EXECUTED', '9:3998ef0958b46fe9c19458183952d2a0', 'addColumn tableName=ACT_DMN_DECISION', '', NULL, '4.24.0', NULL, NULL, '8381895464');
INSERT INTO public.act_dmn_databasechangelog (id, author, filename, dateexecuted, orderexecuted, exectype, md5sum, description, comments, tag, liquibase, contexts, labels, deployment_id) VALUES ('9', 'flowable', 'org/flowable/dmn/db/liquibase/flowable-dmn-db-changelog.xml', '2026-01-14 17:11:35.591222', 8, 'EXECUTED', '9:5c9dc65601456faa1aa12f8d3afe0e9e', 'createIndex indexName=ACT_IDX_DMN_INSTANCE_ID, tableName=ACT_DMN_HI_DECISION_EXECUTION', '', NULL, '4.24.0', NULL, NULL, '8381895464');
INSERT INTO public.act_dmn_databasechangeloglock (id, locked, lockgranted, lockedby) VALUES (1, false, NULL, NULL);
