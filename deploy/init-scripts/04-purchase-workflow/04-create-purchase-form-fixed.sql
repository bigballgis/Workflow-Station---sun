-- =====================================================
-- 采购工作流 - 表单定义（form-create格式）
-- =====================================================
-- 此脚本为采购工作流创建表单定义
-- 使用 form-create 库的标准格式

-- 先删除旧的表单定义
DELETE FROM dw_form_definitions WHERE function_unit_id = (SELECT id FROM dw_function_units WHERE code = 'PURCHASE');

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

-- 验证
SELECT fd.id, fd.form_name, fd.form_type, LENGTH(fd.config_json::text) as config_size
FROM dw_form_definitions fd
JOIN dw_function_units fu ON fd.function_unit_id = fu.id
WHERE fu.code = 'PURCHASE';
