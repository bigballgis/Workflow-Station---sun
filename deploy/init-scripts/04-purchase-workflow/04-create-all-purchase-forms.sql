-- =====================================================
-- 采购工作流 - 完整表单定义（form-create格式）
-- =====================================================
-- 此脚本为采购工作流创建所有4个表单定义
-- 使用 form-create 库的标准格式

-- 先删除旧的表单定义
DELETE FROM dw_form_definitions WHERE function_unit_id = (SELECT id FROM dw_function_units WHERE code = 'PURCHASE');

-- =====================================================
-- 1. 采购申请主表单 (Purchase Request Form)
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
                'type', 'input',
                'field', 'request_no',
                'title', '申请编号',
                'props', jsonb_build_object('readonly', true, 'placeholder', '系统自动生成'),
                'validate', jsonb_build_array(jsonb_build_object('required', true, 'message', '申请编号不能为空'))
            ),
            jsonb_build_object(
                'type', 'input',
                'field', 'title',
                'title', '申请标题',
                'props', jsonb_build_object('placeholder', '请输入采购申请标题'),
                'validate', jsonb_build_array(jsonb_build_object('required', true, 'message', '申请标题不能为空'))
            ),
            jsonb_build_object(
                'type', 'select',
                'field', 'department',
                'title', '申请部门',
                'options', jsonb_build_array(
                    jsonb_build_object('label', 'IT部门', 'value', 'IT'),
                    jsonb_build_object('label', '人力资源部', 'value', 'HR'),
                    jsonb_build_object('label', '财务部', 'value', 'FINANCE'),
                    jsonb_build_object('label', '行政部', 'value', 'ADMIN')
                ),
                'validate', jsonb_build_array(jsonb_build_object('required', true, 'message', '请选择申请部门'))
            ),
            jsonb_build_object(
                'type', 'input',
                'field', 'applicant',
                'title', '申请人',
                'props', jsonb_build_object('placeholder', '请输入申请人姓名'),
                'validate', jsonb_build_array(jsonb_build_object('required', true, 'message', '申请人不能为空'))
            ),
            jsonb_build_object(
                'type', 'DatePicker',
                'field', 'apply_date',
                'title', '申请日期',
                'validate', jsonb_build_array(jsonb_build_object('required', true, 'message', '请选择申请日期'))
            ),
            jsonb_build_object(
                'type', 'InputNumber',
                'field', 'total_amount',
                'title', '总金额',
                'props', jsonb_build_object('readonly', true, 'precision', 2, 'controls', false),
                'validate', jsonb_build_array(jsonb_build_object('required', true, 'message', '总金额不能为空'))
            ),
            jsonb_build_object(
                'type', 'select',
                'field', 'status',
                'title', '状态',
                'value', 'DRAFT',
                'options', jsonb_build_array(
                    jsonb_build_object('label', '草稿', 'value', 'DRAFT'),
                    jsonb_build_object('label', '待审批', 'value', 'PENDING'),
                    jsonb_build_object('label', '已批准', 'value', 'APPROVED'),
                    jsonb_build_object('label', '已拒绝', 'value', 'REJECTED')
                ),
                'validate', jsonb_build_array(jsonb_build_object('required', true, 'message', '请选择状态'))
            ),
            jsonb_build_object(
                'type', 'input',
                'field', 'remarks',
                'title', '备注',
                'props', jsonb_build_object('type', 'textarea', 'rows', 4, 'placeholder', '请输入备注信息')
            )
        ),
        'option', jsonb_build_object(
            'form', jsonb_build_object('labelWidth', '120px', 'size', 'default'),
            'submitBtn', false,
            'resetBtn', false
        )
    ),
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
FROM dw_function_units fu
JOIN dw_table_definitions td ON td.function_unit_id = fu.id
WHERE fu.code = 'PURCHASE' AND td.table_name = 'purchase_request';

-- =====================================================
-- 2. 采购明细子表单 (Purchase Items Sub-Form)
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
                'type', 'input',
                'field', 'item_name',
                'title', '物品名称',
                'props', jsonb_build_object('placeholder', '请输入物品名称'),
                'validate', jsonb_build_array(jsonb_build_object('required', true, 'message', '物品名称不能为空'))
            ),
            jsonb_build_object(
                'type', 'input',
                'field', 'specification',
                'title', '规格型号',
                'props', jsonb_build_object('placeholder', '请输入规格型号')
            ),
            jsonb_build_object(
                'type', 'InputNumber',
                'field', 'quantity',
                'title', '数量',
                'value', 1,
                'props', jsonb_build_object('min', 1, 'controls', true),
                'validate', jsonb_build_array(jsonb_build_object('required', true, 'message', '数量不能为空'))
            ),
            jsonb_build_object(
                'type', 'select',
                'field', 'unit',
                'title', '单位',
                'options', jsonb_build_array(
                    jsonb_build_object('label', '个', 'value', '个'),
                    jsonb_build_object('label', '台', 'value', '台'),
                    jsonb_build_object('label', '套', 'value', '套'),
                    jsonb_build_object('label', '箱', 'value', '箱'),
                    jsonb_build_object('label', '件', 'value', '件')
                ),
                'validate', jsonb_build_array(jsonb_build_object('required', true, 'message', '请选择单位'))
            ),
            jsonb_build_object(
                'type', 'InputNumber',
                'field', 'unit_price',
                'title', '单价',
                'props', jsonb_build_object('min', 0, 'precision', 2, 'controls', false),
                'validate', jsonb_build_array(jsonb_build_object('required', true, 'message', '单价不能为空'))
            ),
            jsonb_build_object(
                'type', 'InputNumber',
                'field', 'subtotal',
                'title', '小计',
                'props', jsonb_build_object('readonly', true, 'precision', 2, 'controls', false)
            ),
            jsonb_build_object(
                'type', 'input',
                'field', 'remarks',
                'title', '备注',
                'props', jsonb_build_object('placeholder', '请输入备注')
            )
        ),
        'option', jsonb_build_object(
            'form', jsonb_build_object('labelWidth', '100px', 'size', 'default'),
            'submitBtn', false,
            'resetBtn', false
        )
    ),
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
FROM dw_function_units fu
JOIN dw_table_definitions td ON td.function_unit_id = fu.id
WHERE fu.code = 'PURCHASE' AND td.table_name = 'purchase_items';

-- =====================================================
-- 3. 供应商选择弹窗表单 (Supplier Selector Form)
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
                'type', 'input',
                'field', 'supplier_code',
                'title', '供应商编号',
                'props', jsonb_build_object('readonly', true)
            ),
            jsonb_build_object(
                'type', 'input',
                'field', 'supplier_name',
                'title', '供应商名称',
                'props', jsonb_build_object('placeholder', '请输入供应商名称'),
                'validate', jsonb_build_array(jsonb_build_object('required', true, 'message', '供应商名称不能为空'))
            ),
            jsonb_build_object(
                'type', 'input',
                'field', 'contact_person',
                'title', '联系人',
                'props', jsonb_build_object('placeholder', '请输入联系人')
            ),
            jsonb_build_object(
                'type', 'input',
                'field', 'contact_phone',
                'title', '联系电话',
                'props', jsonb_build_object('placeholder', '请输入联系电话')
            ),
            jsonb_build_object(
                'type', 'input',
                'field', 'address',
                'title', '地址',
                'props', jsonb_build_object('placeholder', '请输入地址')
            ),
            jsonb_build_object(
                'type', 'select',
                'field', 'status',
                'title', '状态',
                'value', 'ACTIVE',
                'options', jsonb_build_array(
                    jsonb_build_object('label', '活跃', 'value', 'ACTIVE'),
                    jsonb_build_object('label', '停用', 'value', 'INACTIVE')
                )
            )
        ),
        'option', jsonb_build_object(
            'form', jsonb_build_object('labelWidth', '100px', 'size', 'default'),
            'submitBtn', false,
            'resetBtn', false
        )
    ),
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
FROM dw_function_units fu
JOIN dw_table_definitions td ON td.function_unit_id = fu.id
WHERE fu.code = 'PURCHASE' AND td.table_name = 'suppliers';

-- =====================================================
-- 4. 审批操作表单 (Approval Action Form)
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
                'type', 'input',
                'field', 'approver',
                'title', '审批人',
                'props', jsonb_build_object('readonly', true),
                'validate', jsonb_build_array(jsonb_build_object('required', true, 'message', '审批人不能为空'))
            ),
            jsonb_build_object(
                'type', 'radio',
                'field', 'result',
                'title', '审批结果',
                'options', jsonb_build_array(
                    jsonb_build_object('label', '批准', 'value', 'APPROVED'),
                    jsonb_build_object('label', '拒绝', 'value', 'REJECTED')
                ),
                'validate', jsonb_build_array(jsonb_build_object('required', true, 'message', '请选择审批结果'))
            ),
            jsonb_build_object(
                'type', 'input',
                'field', 'comments',
                'title', '审批意见',
                'props', jsonb_build_object('type', 'textarea', 'rows', 4, 'placeholder', '请输入审批意见')
            )
        ),
        'option', jsonb_build_object(
            'form', jsonb_build_object('labelWidth', '100px', 'size', 'default'),
            'submitBtn', false,
            'resetBtn', false
        )
    ),
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
FROM dw_function_units fu
JOIN dw_table_definitions td ON td.function_unit_id = fu.id
WHERE fu.code = 'PURCHASE' AND td.table_name = 'purchase_approvals';

-- =====================================================
-- 验证插入结果
-- =====================================================
SELECT 
    fd.id,
    fd.form_name,
    fd.form_type,
    fd.description,
    td.table_name as bound_table,
    LENGTH(fd.config_json::text) as config_size
FROM dw_form_definitions fd
JOIN dw_function_units fu ON fd.function_unit_id = fu.id
LEFT JOIN dw_table_definitions td ON fd.bound_table_id = td.id
WHERE fu.code = 'PURCHASE'
ORDER BY fd.form_type, fd.form_name;
