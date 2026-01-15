-- Purchase Request - Form Definitions

-- Main Form: Purchase Request Form
INSERT INTO dw_form_definitions (function_unit_id, form_name, form_type, config_json, description, created_at)
SELECT f.id, 'Purchase Request Main Form', 'MAIN', '{}'::jsonb, 'Main form for purchase request with basic information', NOW()
FROM dw_function_units f WHERE f.code = 'fu-purchase-request';

-- Sub Form: Purchase Items Form
INSERT INTO dw_form_definitions (function_unit_id, form_name, form_type, config_json, description, created_at)
SELECT f.id, 'Purchase Items Form', 'SUB', '{}'::jsonb, 'Sub form for purchase item details', NOW()
FROM dw_function_units f WHERE f.code = 'fu-purchase-request';

-- Action Form: Approval Form
INSERT INTO dw_form_definitions (function_unit_id, form_name, form_type, config_json, description, created_at)
SELECT f.id, 'Approval Form', 'ACTION', '{}'::jsonb, 'Action form for approval operations', NOW()
FROM dw_function_units f WHERE f.code = 'fu-purchase-request';

-- Popup Form: Supplier Selection
INSERT INTO dw_form_definitions (function_unit_id, form_name, form_type, config_json, description, created_at)
SELECT f.id, 'Supplier Selection Form', 'POPUP', '{}'::jsonb, 'Popup form for supplier selection', NOW()
FROM dw_function_units f WHERE f.code = 'fu-purchase-request';

-- Action Form: Budget Query Form
INSERT INTO dw_form_definitions (function_unit_id, form_name, form_type, config_json, description, created_at)
SELECT f.id, 'Budget Query Form', 'ACTION', '{}'::jsonb, 'Action form for budget query', NOW()
FROM dw_function_units f WHERE f.code = 'fu-purchase-request';

-- Action Form: Countersign Form
INSERT INTO dw_form_definitions (function_unit_id, form_name, form_type, config_json, description, created_at)
SELECT f.id, 'Countersign Form', 'ACTION', '{}'::jsonb, 'Action form for countersign operations', NOW()
FROM dw_function_units f WHERE f.code = 'fu-purchase-request';