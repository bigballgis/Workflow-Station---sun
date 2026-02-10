-- =====================================================
-- Purchase Workflow - Form Definitions
-- =====================================================
-- This script creates form definitions for the purchase workflow
-- Prerequisites: Table definitions and field definitions must be created first

-- =====================================================
-- Purchase Request Form
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
    'Purchase Request Main Form',
    td.id,
    jsonb_build_object(
        'rule', jsonb_build_array(
            jsonb_build_object(
                'field', 'request_no',
                'label', 'Request No.',
                'title', 'Request No.',
                'type', 'input',
                'required', true,
                'readonly', true,
                'placeholder', 'Auto-generated'
            ),
            jsonb_build_object(
                'field', 'title',
                'label', 'Request Title',
                'title', 'Request Title',
                'type', 'input',
                'required', true,
                'placeholder', 'Enter purchase request title'
            ),
            jsonb_build_object(
                'field', 'department',
                'label', 'Department',
                'title', 'Department',
                'type', 'select',
                'required', true,
                'options', jsonb_build_array(
                    jsonb_build_object('label', 'IT Department', 'value', 'IT'),
                    jsonb_build_object('label', 'Human Resources', 'value', 'HR'),
                    jsonb_build_object('label', 'Finance', 'value', 'FINANCE'),
                    jsonb_build_object('label', 'Administration', 'value', 'ADMIN')
                )
            ),
            jsonb_build_object(
                'field', 'applicant',
                'label', 'Applicant',
                'title', 'Applicant',
                'type', 'input',
                'required', true,
                'placeholder', 'Enter applicant name'
            ),
            jsonb_build_object(
                'field', 'apply_date',
                'label', 'Application Date',
                'title', 'Application Date',
                'type', 'date',
                'required', true,
                'defaultValue', 'today'
            ),
            jsonb_build_object(
                'field', 'total_amount',
                'label', 'Total Amount',
                'title', 'Total Amount',
                'type', 'number',
                'required', true,
                'readonly', true,
                'precision', 2,
                'suffix', 'USD'
            ),
            jsonb_build_object(
                'field', 'status',
                'label', 'Status',
                'title', 'Status',
                'type', 'select',
                'required', true,
                'defaultValue', 'DRAFT',
                'options', jsonb_build_array(
                    jsonb_build_object('label', 'Draft', 'value', 'DRAFT'),
                    jsonb_build_object('label', 'Pending', 'value', 'PENDING'),
                    jsonb_build_object('label', 'Approved', 'value', 'APPROVED'),
                    jsonb_build_object('label', 'Rejected', 'value', 'REJECTED')
                )
            ),
            jsonb_build_object(
                'field', 'remarks',
                'label', 'Remarks',
                'title', 'Remarks',
                'type', 'textarea',
                'rows', 4,
                'placeholder', 'Enter remarks'
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
-- Purchase Items Sub-Form
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
    'Purchase Items Sub-Form',
    td.id,
    jsonb_build_object(
        'rule', jsonb_build_array(
            jsonb_build_object(
                'field', 'item_name',
                'label', 'Item Name',
                'title', 'Item Name',
                'type', 'input',
                'required', true,
                'placeholder', 'Enter item name'
            ),
            jsonb_build_object(
                'field', 'specification',
                'label', 'Specification',
                'title', 'Specification',
                'type', 'input',
                'placeholder', 'Enter specification'
            ),
            jsonb_build_object(
                'field', 'quantity',
                'label', 'Quantity',
                'title', 'Quantity',
                'type', 'number',
                'required', true,
                'min', 1,
                'defaultValue', 1
            ),
            jsonb_build_object(
                'field', 'unit',
                'label', 'Unit',
                'title', 'Unit',
                'type', 'select',
                'required', true,
                'options', jsonb_build_array(
                    jsonb_build_object('label', 'Piece', 'value', 'piece'),
                    jsonb_build_object('label', 'Set', 'value', 'set'),
                    jsonb_build_object('label', 'Box', 'value', 'box'),
                    jsonb_build_object('label', 'Unit', 'value', 'unit'),
                    jsonb_build_object('label', 'Item', 'value', 'item')
                )
            ),
            jsonb_build_object(
                'field', 'unit_price',
                'label', 'Unit Price',
                'title', 'Unit Price',
                'type', 'number',
                'required', true,
                'precision', 2,
                'min', 0,
                'placeholder', 'Enter unit price'
            ),
            jsonb_build_object(
                'field', 'subtotal',
                'label', 'Subtotal',
                'title', 'Subtotal',
                'type', 'number',
                'readonly', true,
                'precision', 2
            ),
            jsonb_build_object(
                'field', 'remarks',
                'label', 'Remarks',
                'title', 'Remarks',
                'type', 'input',
                'placeholder', 'Enter remarks'
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
-- Supplier Selector Popup Form
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
    'Supplier Selector Popup Form',
    td.id,
    jsonb_build_object(
        'rule', jsonb_build_array(
            jsonb_build_object(
                'field', 'supplier_code',
                'label', 'Supplier Code',
                'title', 'Supplier Code',
                'type', 'input',
                'readonly', true
            ),
            jsonb_build_object(
                'field', 'supplier_name',
                'label', 'Supplier Name',
                'title', 'Supplier Name',
                'type', 'input',
                'required', true,
                'placeholder', 'Enter supplier name'
            ),
            jsonb_build_object(
                'field', 'contact_person',
                'label', 'Contact Person',
                'title', 'Contact Person',
                'type', 'input',
                'placeholder', 'Enter contact person'
            ),
            jsonb_build_object(
                'field', 'contact_phone',
                'label', 'Contact Phone',
                'title', 'Contact Phone',
                'type', 'input',
                'placeholder', 'Enter contact phone'
            ),
            jsonb_build_object(
                'field', 'status',
                'label', 'Status',
                'title', 'Status',
                'type', 'select',
                'required', true,
                'defaultValue', 'ACTIVE',
                'options', jsonb_build_array(
                    jsonb_build_object('label', 'Active', 'value', 'ACTIVE'),
                    jsonb_build_object('label', 'Inactive', 'value', 'INACTIVE')
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
-- Approval Action Form
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
    'Approval Action Form',
    td.id,
    jsonb_build_object(
        'rule', jsonb_build_array(
            jsonb_build_object(
                'field', 'approver',
                'label', 'Approver',
                'title', 'Approver',
                'type', 'input',
                'required', true,
                'readonly', true
            ),
            jsonb_build_object(
                'field', 'result',
                'label', 'Approval Result',
                'title', 'Approval Result',
                'type', 'radio',
                'required', true,
                'options', jsonb_build_array(
                    jsonb_build_object('label', 'Approved', 'value', 'APPROVED'),
                    jsonb_build_object('label', 'Rejected', 'value', 'REJECTED')
                )
            ),
            jsonb_build_object(
                'field', 'comments',
                'label', 'Comments',
                'title', 'Comments',
                'type', 'textarea',
                'rows', 4,
                'placeholder', 'Enter approval comments'
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
-- Verify Insert Results
-- =====================================================
-- Query all form definitions
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
