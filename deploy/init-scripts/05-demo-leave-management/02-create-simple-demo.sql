-- Simple Demo: Employee Leave Management System
-- This creates just the function unit, you can design tables/forms/process in the UI

INSERT INTO public.dw_function_units (
    code,
    name,
    description,
    status,
    created_by,
    updated_by
) VALUES (
    'LEAVE_MGMT',
    'Employee Leave Management',
    'Complete employee leave request and approval system with multi-level approval workflow. Demo includes: Main table (Leave Request), Sub table (Leave Details), Related table (Approval Records), Application form, Approval form, Multi-step approval process, and various actions (Submit, Approve, Reject, Withdraw, Query).',
    'DRAFT',
    'admin',
    'admin'
) ON CONFLICT (code) DO UPDATE SET
    description = EXCLUDED.description,
    updated_by = EXCLUDED.updated_by,
    updated_at = CURRENT_TIMESTAMP;

SELECT 'Demo function unit created successfully! ID: ' || id || ', Code: ' || code || ', Name: ' || name
FROM public.dw_function_units
WHERE code = 'LEAVE_MGMT';
