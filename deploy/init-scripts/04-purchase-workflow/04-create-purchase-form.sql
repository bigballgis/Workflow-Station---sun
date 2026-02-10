-- =====================================================
-- 采购工作流 - 表单定义
-- =====================================================
-- 此脚本为采购工作流创建表单定义
-- 前置条件：需要先创建表定义和字段定义

-- =====================================================
-- 采购申请表单 (Purchase Request Form)
-- =====================================================
INSERT INTO dw_form_definitions (
    function_unit_id,
    form_name,
    form_type,
    description,
    bound_table_id,
    config_json,
    created_at,
    updated_at
)
SELECT 
    fu.id,
    'purchase_request_form',
    'MAIN',
    '采购申请主表单',
    td.id,
    jsonb_build_object(
        'rule', jsonb_build_array(
            jsonb_build_object(
                'field', 'request_no',
                'label', '申请编号',
                'type', 'input',
                'required', true,
                'readonly', true,
                'placeholder', '系统自动生成'
            ),
            jsonb_build_object(
                'field', 'title',
                'label', '申请标题',
                'type', 'input',
                'required', true,
                'placeholder', '请输入采购申请标题'
            ),
            jsonb_build_object(
                'field', 'department',
                'label', '申请部门',
                'type', 'select',
                'required', true,
                'options', jsonb_build_array(
                    jsonb_build_object('label', 'IT部门', 'value', 'IT'),
                    jsonb_build_object('label', '人力资源部', 'value', 'HR'),
                    jsonb_build_object('label', '财务部', 'value', 'FINANCE'),
                    jsonb_build_object('label', '行政部', 'value', 'ADMIN')
                )
            ),
            jsonb_build_object(
                'field', 'applicant',
                'label', '申请人',
                'type', 'input',
                'required', true,
                'placeholder', '请输入申请人姓名'
            ),
            jsonb_build_object(
                'field', 'apply_date',
                'label', '申请日期',
                'type', 'date',
                'required', true,
                'defaultValue', 'today'
            ),
            jsonb_build_object(
                'field', 'total_amount',
                'label', '总金额',
                'type', 'number',
                'required', true,
                'readonly', true,
                'precision', 2,
                'suffix', '元'
            ),
            jsonb_build_object(
                'field', 'status',
                'label', '状态',
                'type', 'select',
                'required', true,
                'defaultValue', 'DRAFT',
                'options', jsonb_build_array(
                    jsonb_build_object('label', '草稿', 'value', 'DRAFT'),
                    jsonb_build_object('label', '待审批', 'value', 'PENDING'),
                    jsonb_build_object('label', '已批准', 'value', 'APPROVED'),
                    jsonb_build_object('label', '已拒绝', 'value', 'REJECTED')
                )
            ),
            jsonb_build_object(
                'field', 'remarks',
                'label', '备注',
                'type', 'textarea',
                'rows', 4,
                'placeholder', '请输入备注信息'
            )
        )
    ),
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
FROM dw_function_units fu
JOIN dw_table_definitions td ON td.function_unit_id = fu.id
WHERE fu.code = 'PURCHASE' AND td.table_name = 'purchase_request'
ON CONFLICT (function_unit_id, form_name) DO UPDATE SET
    description = EXCLUDED.description,
    config_json = EXCLUDED.config_json,
    updated_at = CURRENT_TIMESTAMP;

-- =====================================================
-- 采购明细子表单 (Purchase Items Sub-Form)
-- =====================================================
INSERT INTO dw_form_definitions (
    function_unit_id,
    form_name,
    form_type,
    description,
    bound_table_id,
    config_json,
    created_at,
    updated_at
)
SELECT 
    fu.id,
    'purchase_items_form',
    'SUB',
    '采购明细子表单',
    td.id,
    jsonb_build_object(
        'rule', jsonb_build_array(
            jsonb_build_object(
                'field', 'item_name',
                'label', '物品名称',
                'type', 'input',
                'required', true,
                'placeholder', '请输入物品名称'
            ),
            jsonb_build_object(
                'field', 'specification',
                'label', '规格型号',
                'type', 'input',
                'placeholder', '请输入规格型号'
            ),
            jsonb_build_object(
                'field', 'quantity',
                'label', '数量',
                'type', 'number',
                'required', true,
                'min', 1,
                'defaultValue', 1
            ),
            jsonb_build_object(
                'field', 'unit',
                'label', '单位',
                'type', 'select',
                'required', true,
                'options', jsonb_build_array(
                    jsonb_build_object('label', '个', 'value', '个'),
                    jsonb_build_object('label', '台', 'value', '台'),
                    jsonb_build_object('label', '套', 'value', '套'),
                    jsonb_build_object('label', '箱', 'value', '箱'),
                    jsonb_build_object('label', '件', 'value', '件')
                )
            ),
            jsonb_build_object(
                'field', 'unit_price',
                'label', '单价',
                'type', 'number',
                'required', true,
                'precision', 2,
                'min', 0,
                'placeholder', '请输入单价'
            ),
            jsonb_build_object(
                'field', 'subtotal',
                'label', '小计',
                'type', 'number',
                'readonly', true,
                'precision', 2
            ),
            jsonb_build_object(
                'field', 'remarks',
                'label', '备注',
                'type', 'input',
                'placeholder', '请输入备注'
            )
        )
    ),
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
FROM dw_function_units fu
JOIN dw_table_definitions td ON td.function_unit_id = fu.id
WHERE fu.code = 'PURCHASE' AND td.table_name = 'purchase_items'
ON CONFLICT (function_unit_id, form_name) DO UPDATE SET
    description = EXCLUDED.description,
    config_json = EXCLUDED.config_json,
    updated_at = CURRENT_TIMESTAMP;

-- =====================================================
-- 供应商选择弹窗表单 (Supplier Popup Form)
-- =====================================================
INSERT INTO dw_form_definitions (
    function_unit_id,
    form_name,
    form_type,
    description,
    bound_table_id,
    config_json,
    created_at,
    updated_at
)
SELECT 
    fu.id,
    'supplier_selector_form',
    'POPUP',
    '供应商选择弹窗表单',
    td.id,
    jsonb_build_object(
        'rule', jsonb_build_array(
            jsonb_build_object(
                'field', 'supplier_code',
                'label', '供应商编号',
                'type', 'input',
                'readonly', true
            ),
            jsonb_build_object(
                'field', 'supplier_name',
                'label', '供应商名称',
                'type', 'input',
                'required', true,
                'placeholder', '请输入供应商名称'
            ),
            jsonb_build_object(
                'field', 'contact_person',
                'label', '联系人',
                'type', 'input',
                'placeholder', '请输入联系人'
            ),
            jsonb_build_object(
                'field', 'contact_phone',
                'label', '联系电话',
                'type', 'input',
                'placeholder', '请输入联系电话'
            ),
            jsonb_build_object(
                'field', 'status',
                'label', '状态',
                'type', 'select',
                'required', true,
                'defaultValue', 'ACTIVE',
                'options', jsonb_build_array(
                    jsonb_build_object('label', '活跃', 'value', 'ACTIVE'),
                    jsonb_build_object('label', '停用', 'value', 'INACTIVE')
                )
            )
        )
    ),
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
FROM dw_function_units fu
JOIN dw_table_definitions td ON td.function_unit_id = fu.id
WHERE fu.code = 'PURCHASE' AND td.table_name = 'suppliers'
ON CONFLICT (function_unit_id, form_name) DO UPDATE SET
    description = EXCLUDED.description,
    config_json = EXCLUDED.config_json,
    updated_at = CURRENT_TIMESTAMP;

-- =====================================================
-- 审批操作表单 (Approval Action Form)
-- =====================================================
INSERT INTO dw_form_definitions (
    function_unit_id,
    form_name,
    form_type,
    description,
    bound_table_id,
    config_json,
    created_at,
    updated_at
)
SELECT 
    fu.id,
    'approval_action_form',
    'ACTION',
    '审批操作表单',
    td.id,
    jsonb_build_object(
        'rule', jsonb_build_array(
            jsonb_build_object(
                'field', 'approver',
                'label', '审批人',
                'type', 'input',
                'required', true,
                'readonly', true
            ),
            jsonb_build_object(
                'field', 'result',
                'label', '审批结果',
                'type', 'radio',
                'required', true,
                'options', jsonb_build_array(
                    jsonb_build_object('label', '批准', 'value', 'APPROVED'),
                    jsonb_build_object('label', '拒绝', 'value', 'REJECTED')
                )
            ),
            jsonb_build_object(
                'field', 'comments',
                'label', '审批意见',
                'type', 'textarea',
                'rows', 4,
                'placeholder', '请输入审批意见'
            )
        )
    ),
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
FROM dw_function_units fu
JOIN dw_table_definitions td ON td.function_unit_id = fu.id
WHERE fu.code = 'PURCHASE' AND td.table_name = 'purchase_approvals'
ON CONFLICT (function_unit_id, form_name) DO UPDATE SET
    description = EXCLUDED.description,
    config_json = EXCLUDED.config_json,
    updated_at = CURRENT_TIMESTAMP;

-- =====================================================
-- 验证插入结果
-- =====================================================
-- 查询所有表单定义
-- SELECT 
--     fd.id,
--     fd.form_name,
--     fd.form_type,
--     fd.description,
--     td.table_name as bound_table,
--     fu.name as function_unit_name
-- FROM dw_form_definitions fd
-- JOIN dw_function_units fu ON fd.function_unit_id = fu.id
-- LEFT JOIN dw_table_definitions td ON fd.bound_table_id = td.id
-- WHERE fu.code = 'PURCHASE'
-- ORDER BY fd.form_type, fd.form_name;
