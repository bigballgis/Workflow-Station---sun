-- =============================================================================
-- Insert Sample Data for Simple Approval Workflow
-- Created on 2026-02-20
-- Inserts actual business data that can be viewed in the UI
-- =============================================================================

DO $sample_data$
DECLARE
    v_function_unit_id BIGINT;
    v_main_table_id BIGINT;
    v_sub_table_id BIGINT;
    v_action_table_id BIGINT;
    v_relation_table_id BIGINT;
BEGIN
    -- =========================================================================
    -- Get Function Unit and Table IDs
    -- =========================================================================
    SELECT id INTO v_function_unit_id
    FROM dw_function_units
    WHERE code = 'SIMPLE_APPROVAL';

    IF v_function_unit_id IS NULL THEN
        RAISE EXCEPTION 'Function unit SIMPLE_APPROVAL not found. Run previous scripts first.';
    END IF;

    SELECT id INTO v_main_table_id
    FROM dw_table_definitions
    WHERE function_unit_id = v_function_unit_id AND table_name = 'Request';

    SELECT id INTO v_sub_table_id
    FROM dw_table_definitions
    WHERE function_unit_id = v_function_unit_id AND table_name = 'RequestItems';

    SELECT id INTO v_action_table_id
    FROM dw_table_definitions
    WHERE function_unit_id = v_function_unit_id AND table_name = 'ApprovalActions';

    SELECT id INTO v_relation_table_id
    FROM dw_table_definitions
    WHERE function_unit_id = v_function_unit_id AND table_name = 'RequestAttachments';

    RAISE NOTICE 'Found table IDs: Main=%, Sub=%, Action=%, Relation=%', 
        v_main_table_id, v_sub_table_id, v_action_table_id, v_relation_table_id;

    -- =========================================================================
    -- Create dynamic tables if they don't exist
    -- =========================================================================
    
    -- Create MAIN table
    EXECUTE format('
        CREATE TABLE IF NOT EXISTS dw_data_%s (
            id BIGSERIAL PRIMARY KEY,
            request_number VARCHAR(50) NOT NULL,
            request_date TIMESTAMP NOT NULL,
            title VARCHAR(200) NOT NULL,
            description TEXT NOT NULL,
            status VARCHAR(30) NOT NULL,
            approval_comments TEXT,
            created_by VARCHAR(100) NOT NULL,
            created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
            updated_at TIMESTAMP
        )', v_main_table_id);

    RAISE NOTICE 'Created/verified table: dw_data_%', v_main_table_id;

    -- Create SUB table
    IF v_sub_table_id IS NOT NULL THEN
        EXECUTE format('
            CREATE TABLE IF NOT EXISTS dw_data_%s (
                id BIGSERIAL PRIMARY KEY,
                request_id BIGINT NOT NULL,
                item_name VARCHAR(200) NOT NULL,
                quantity INTEGER NOT NULL,
                unit_price DECIMAL(10,2) NOT NULL,
                total_price DECIMAL(10,2) NOT NULL,
                remarks TEXT,
                sort_order INTEGER NOT NULL
            )', v_sub_table_id);
        
        RAISE NOTICE 'Created/verified table: dw_data_%', v_sub_table_id;
    END IF;

    -- Create ACTION table
    IF v_action_table_id IS NOT NULL THEN
        EXECUTE format('
            CREATE TABLE IF NOT EXISTS dw_data_%s (
                id BIGSERIAL PRIMARY KEY,
                request_id BIGINT NOT NULL,
                action_type VARCHAR(50) NOT NULL,
                action_by VARCHAR(100) NOT NULL,
                action_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                comments TEXT,
                previous_status VARCHAR(30),
                new_status VARCHAR(30) NOT NULL,
                ip_address VARCHAR(50)
            )', v_action_table_id);
        
        RAISE NOTICE 'Created/verified table: dw_data_%', v_action_table_id;
    END IF;

    -- Create RELATION table
    IF v_relation_table_id IS NOT NULL THEN
        EXECUTE format('
            CREATE TABLE IF NOT EXISTS dw_data_%s (
                id BIGSERIAL PRIMARY KEY,
                request_id BIGINT NOT NULL,
                file_name VARCHAR(255) NOT NULL,
                file_path VARCHAR(500) NOT NULL,
                file_size BIGINT NOT NULL,
                file_type VARCHAR(100) NOT NULL,
                uploaded_by VARCHAR(100) NOT NULL,
                uploaded_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                description TEXT
            )', v_relation_table_id);
        
        RAISE NOTICE 'Created/verified table: dw_data_%', v_relation_table_id;
    END IF;

    -- =========================================================================
    -- Insert Sample Data - Request 1: Office Supplies (Approved)
    -- =========================================================================
    
    -- Main Request 1
    EXECUTE format('
        INSERT INTO dw_data_%s (
            id, request_number, request_date, title, description, 
            status, approval_comments, created_by, created_at, updated_at
        ) VALUES (
            1, ''REQ-2026-001'', ''2026-02-15 10:00:00'', 
            ''办公用品采购申请'', ''需要采购一批办公用品，包括笔记本电脑、鼠标和键盘'',
            ''APPROVED'', ''同意采购，预算充足'', ''john.doe'',
            ''2026-02-15 10:00:00'', ''2026-02-15 14:30:00''
        ) ON CONFLICT (id) DO NOTHING', v_main_table_id);

    -- Sub Items for Request 1
    IF v_sub_table_id IS NOT NULL THEN
        EXECUTE format('
            INSERT INTO dw_data_%s (
                id, request_id, item_name, quantity, unit_price, total_price, remarks, sort_order
            ) VALUES 
            (1, 1, ''笔记本电脑'', 2, 5000.00, 10000.00, ''ThinkPad X1 Carbon'', 1),
            (2, 1, ''无线鼠标'', 5, 50.00, 250.00, ''罗技 MX Master 3'', 2),
            (3, 1, ''机械键盘'', 5, 150.00, 750.00, ''Cherry MX 青轴'', 3)
            ON CONFLICT (id) DO NOTHING', v_sub_table_id);
    END IF;

    -- Actions for Request 1
    IF v_action_table_id IS NOT NULL THEN
        EXECUTE format('
            INSERT INTO dw_data_%s (
                id, request_id, action_type, action_by, action_at, 
                comments, previous_status, new_status, ip_address
            ) VALUES 
            (1, 1, ''SUBMIT'', ''john.doe'', ''2026-02-15 10:00:00'',
             ''提交审批申请'', NULL, ''PENDING'', ''192.168.1.100''),
            (2, 1, ''APPROVE'', ''manager.smith'', ''2026-02-15 14:30:00'',
             ''同意采购，预算充足'', ''PENDING'', ''APPROVED'', ''192.168.1.50'')
            ON CONFLICT (id) DO NOTHING', v_action_table_id);
    END IF;

    -- Attachments for Request 1
    IF v_relation_table_id IS NOT NULL THEN
        EXECUTE format('
            INSERT INTO dw_data_%s (
                id, request_id, file_name, file_path, file_size, 
                file_type, uploaded_by, uploaded_at, description
            ) VALUES 
            (1, 1, ''供应商报价单.pdf'', ''/uploads/2026/02/quote-001.pdf'', 245678,
             ''application/pdf'', ''john.doe'', ''2026-02-15 10:00:00'', ''三家供应商的报价对比''),
            (2, 1, ''产品目录.xlsx'', ''/uploads/2026/02/catalog-001.xlsx'', 156789,
             ''application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'',
             ''john.doe'', ''2026-02-15 10:05:00'', ''产品详细规格和型号'')
            ON CONFLICT (id) DO NOTHING', v_relation_table_id);
    END IF;

    RAISE NOTICE 'Inserted Request 1: Office Supplies (APPROVED)';

    -- =========================================================================
    -- Insert Sample Data - Request 2: Training Budget (Pending)
    -- =========================================================================
    
    -- Main Request 2
    EXECUTE format('
        INSERT INTO dw_data_%s (
            id, request_number, request_date, title, description, 
            status, approval_comments, created_by, created_at, updated_at
        ) VALUES (
            2, ''REQ-2026-002'', ''2026-02-18 09:30:00'', 
            ''员工培训预算申请'', ''申请Q1季度员工技术培训预算'',
            ''PENDING'', NULL, ''jane.smith'',
            ''2026-02-18 09:30:00'', NULL
        ) ON CONFLICT (id) DO NOTHING', v_main_table_id);

    -- Sub Items for Request 2
    IF v_sub_table_id IS NOT NULL THEN
        EXECUTE format('
            INSERT INTO dw_data_%s (
                id, request_id, item_name, quantity, unit_price, total_price, remarks, sort_order
            ) VALUES 
            (4, 2, ''Java高级开发培训'', 10, 3000.00, 30000.00, ''为期5天的集中培训'', 1),
            (5, 2, ''云计算架构师认证'', 5, 5000.00, 25000.00, ''AWS认证培训'', 2),
            (6, 2, ''敏捷项目管理'', 8, 2000.00, 16000.00, ''Scrum Master认证'', 3)
            ON CONFLICT (id) DO NOTHING', v_sub_table_id);
    END IF;

    -- Actions for Request 2
    IF v_action_table_id IS NOT NULL THEN
        EXECUTE format('
            INSERT INTO dw_data_%s (
                id, request_id, action_type, action_by, action_at, 
                comments, previous_status, new_status, ip_address
            ) VALUES 
            (3, 2, ''SUBMIT'', ''jane.smith'', ''2026-02-18 09:30:00'',
             ''提交培训预算申请'', NULL, ''PENDING'', ''192.168.1.105'')
            ON CONFLICT (id) DO NOTHING', v_action_table_id);
    END IF;

    -- Attachments for Request 2
    IF v_relation_table_id IS NOT NULL THEN
        EXECUTE format('
            INSERT INTO dw_data_%s (
                id, request_id, file_name, file_path, file_size, 
                file_type, uploaded_by, uploaded_at, description
            ) VALUES 
            (3, 2, ''培训计划.docx'', ''/uploads/2026/02/training-plan-002.docx'', 89456,
             ''application/vnd.openxmlformats-officedocument.wordprocessingml.document'',
             ''jane.smith'', ''2026-02-18 09:30:00'', ''Q1培训详细计划'')
            ON CONFLICT (id) DO NOTHING', v_relation_table_id);
    END IF;

    RAISE NOTICE 'Inserted Request 2: Training Budget (PENDING)';

    -- =========================================================================
    -- Insert Sample Data - Request 3: Marketing Campaign (Rejected)
    -- =========================================================================
    
    -- Main Request 3
    EXECUTE format('
        INSERT INTO dw_data_%s (
            id, request_number, request_date, title, description, 
            status, approval_comments, created_by, created_at, updated_at
        ) VALUES (
            3, ''REQ-2026-003'', ''2026-02-19 14:00:00'', 
            ''市场推广活动预算'', ''申请春季产品推广活动预算'',
            ''REJECTED'', ''当前预算紧张，建议延期到Q2'', ''mike.chen'',
            ''2026-02-19 14:00:00'', ''2026-02-19 16:45:00''
        ) ON CONFLICT (id) DO NOTHING', v_main_table_id);

    -- Sub Items for Request 3
    IF v_sub_table_id IS NOT NULL THEN
        EXECUTE format('
            INSERT INTO dw_data_%s (
                id, request_id, item_name, quantity, unit_price, total_price, remarks, sort_order
            ) VALUES 
            (7, 3, ''线上广告投放'', 1, 50000.00, 50000.00, ''社交媒体和搜索引擎'', 1),
            (8, 3, ''线下活动场地'', 3, 8000.00, 24000.00, ''三个城市巡回活动'', 2),
            (9, 3, ''宣传物料制作'', 1, 15000.00, 15000.00, ''海报、手册、礼品'', 3)
            ON CONFLICT (id) DO NOTHING', v_sub_table_id);
    END IF;

    -- Actions for Request 3
    IF v_action_table_id IS NOT NULL THEN
        EXECUTE format('
            INSERT INTO dw_data_%s (
                id, request_id, action_type, action_by, action_at, 
                comments, previous_status, new_status, ip_address
            ) VALUES 
            (4, 3, ''SUBMIT'', ''mike.chen'', ''2026-02-19 14:00:00'',
             ''提交市场推广预算申请'', NULL, ''PENDING'', ''192.168.1.110''),
            (5, 3, ''REJECT'', ''director.wang'', ''2026-02-19 16:45:00'',
             ''当前预算紧张，建议延期到Q2再申请'', ''PENDING'', ''REJECTED'', ''192.168.1.60'')
            ON CONFLICT (id) DO NOTHING', v_action_table_id);
    END IF;

    -- Attachments for Request 3
    IF v_relation_table_id IS NOT NULL THEN
        EXECUTE format('
            INSERT INTO dw_data_%s (
                id, request_id, file_name, file_path, file_size, 
                file_type, uploaded_by, uploaded_at, description
            ) VALUES 
            (4, 3, ''推广方案.pptx'', ''/uploads/2026/02/campaign-003.pptx'', 3456789,
             ''application/vnd.openxmlformats-officedocument.presentationml.presentation'',
             ''mike.chen'', ''2026-02-19 14:00:00'', ''春季推广活动详细方案''),
            (5, 3, ''预算明细.xlsx'', ''/uploads/2026/02/budget-003.xlsx'', 234567,
             ''application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'',
             ''mike.chen'', ''2026-02-19 14:05:00'', ''各项费用预算明细'')
            ON CONFLICT (id) DO NOTHING', v_relation_table_id);
    END IF;

    RAISE NOTICE 'Inserted Request 3: Marketing Campaign (REJECTED)';

    -- =========================================================================
    -- Update sequences
    -- =========================================================================
    EXECUTE format('SELECT setval(''dw_data_%s_id_seq'', (SELECT MAX(id) FROM dw_data_%s))', 
        v_main_table_id, v_main_table_id);
    
    IF v_sub_table_id IS NOT NULL THEN
        EXECUTE format('SELECT setval(''dw_data_%s_id_seq'', (SELECT MAX(id) FROM dw_data_%s))', 
            v_sub_table_id, v_sub_table_id);
    END IF;
    
    IF v_action_table_id IS NOT NULL THEN
        EXECUTE format('SELECT setval(''dw_data_%s_id_seq'', (SELECT MAX(id) FROM dw_data_%s))', 
            v_action_table_id, v_action_table_id);
    END IF;
    
    IF v_relation_table_id IS NOT NULL THEN
        EXECUTE format('SELECT setval(''dw_data_%s_id_seq'', (SELECT MAX(id) FROM dw_data_%s))', 
            v_relation_table_id, v_relation_table_id);
    END IF;

    -- =========================================================================
    -- Summary
    -- =========================================================================
    RAISE NOTICE '========================================';
    RAISE NOTICE 'Sample Data Insertion Complete!';
    RAISE NOTICE '========================================';
    RAISE NOTICE 'Inserted 3 requests:';
    RAISE NOTICE '  1. REQ-2026-001: Office Supplies (APPROVED) - 3 items, 2 actions, 2 attachments';
    RAISE NOTICE '  2. REQ-2026-002: Training Budget (PENDING) - 3 items, 1 action, 1 attachment';
    RAISE NOTICE '  3. REQ-2026-003: Marketing Campaign (REJECTED) - 3 items, 2 actions, 2 attachments';
    RAISE NOTICE '========================================';
    RAISE NOTICE 'Total: 3 requests, 9 items, 5 actions, 5 attachments';
    RAISE NOTICE '========================================';

END $sample_data$;
