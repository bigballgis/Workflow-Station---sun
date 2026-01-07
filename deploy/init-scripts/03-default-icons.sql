-- =====================================================
-- Default Icons Initialization Script
-- Banking Workflow Application Icon Library (PostgreSQL)
-- =====================================================

-- Clear existing icons (optional, comment out in production)
-- DELETE FROM dw_icons;

-- =====================================================
-- APPROVAL - Leave, Expense, Purchase approvals
-- =====================================================

INSERT INTO dw_icons (name, category, file_type, file_data, file_size, width, height, tags, created_by, created_at) VALUES
('leave-request', 'APPROVAL', 'svg', 
'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="#DB0011" stroke-width="2"><rect x="3" y="4" width="18" height="18" rx="2"/><line x1="16" y1="2" x2="16" y2="6"/><line x1="8" y1="2" x2="8" y2="6"/><line x1="3" y1="10" x2="21" y2="10"/><path d="M9 16l2 2 4-4"/></svg>'::bytea,
280, 24, 24, 'leave,vacation,holiday', 'system', NOW());

INSERT INTO dw_icons (name, category, file_type, file_data, file_size, width, height, tags, created_by, created_at) VALUES
('expense-claim', 'APPROVAL', 'svg',
'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="#DB0011" stroke-width="2"><rect x="2" y="3" width="20" height="18" rx="2"/><line x1="2" y1="9" x2="22" y2="9"/><circle cx="12" cy="15" r="3"/><path d="M12 13v4M10 15h4"/></svg>'::bytea,
290, 24, 24, 'expense,claim,finance', 'system', NOW());

INSERT INTO dw_icons (name, category, file_type, file_data, file_size, width, height, tags, created_by, created_at) VALUES
('purchase-request', 'APPROVAL', 'svg',
'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="#DB0011" stroke-width="2"><circle cx="9" cy="21" r="1"/><circle cx="20" cy="21" r="1"/><path d="M1 1h4l2.68 13.39a2 2 0 0 0 2 1.61h9.72a2 2 0 0 0 2-1.61L23 6H6"/></svg>'::bytea,
260, 24, 24, 'purchase,buy,order', 'system', NOW());

INSERT INTO dw_icons (name, category, file_type, file_data, file_size, width, height, tags, created_by, created_at) VALUES
('approval-pass', 'APPROVAL', 'svg',
'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="#00A651" stroke-width="2"><circle cx="12" cy="12" r="10"/><path d="M9 12l2 2 4-4"/></svg>'::bytea,
180, 24, 24, 'pass,approve,accept', 'system', NOW());

INSERT INTO dw_icons (name, category, file_type, file_data, file_size, width, height, tags, created_by, created_at) VALUES
('approval-reject', 'APPROVAL', 'svg',
'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="#DB0011" stroke-width="2"><circle cx="12" cy="12" r="10"/><line x1="15" y1="9" x2="9" y2="15"/><line x1="9" y1="9" x2="15" y2="15"/></svg>'::bytea,
200, 24, 24, 'reject,deny,decline', 'system', NOW());

INSERT INTO dw_icons (name, category, file_type, file_data, file_size, width, height, tags, created_by, created_at) VALUES
('contract-approval', 'APPROVAL', 'svg',
'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="#DB0011" stroke-width="2"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/><line x1="16" y1="13" x2="8" y2="13"/><line x1="16" y1="17" x2="8" y2="17"/></svg>'::bytea,
320, 24, 24, 'contract,agreement,sign', 'system', NOW());


-- =====================================================
-- CREDIT - Loan, Credit, Risk
-- =====================================================

INSERT INTO dw_icons (name, category, file_type, file_data, file_size, width, height, tags, created_by, created_at) VALUES
('loan-application', 'CREDIT', 'svg',
'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="#DB0011" stroke-width="2"><rect x="2" y="4" width="20" height="16" rx="2"/><line x1="2" y1="10" x2="22" y2="10"/><path d="M12 14v4"/><path d="M8 14l4 4 4-4"/></svg>'::bytea,
250, 24, 24, 'loan,borrow,finance', 'system', NOW());

INSERT INTO dw_icons (name, category, file_type, file_data, file_size, width, height, tags, created_by, created_at) VALUES
('credit-approval', 'CREDIT', 'svg',
'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="#DB0011" stroke-width="2"><path d="M12 2L2 7l10 5 10-5-10-5z"/><path d="M2 17l10 5 10-5"/><path d="M2 12l10 5 10-5"/></svg>'::bytea,
220, 24, 24, 'credit,limit,approval', 'system', NOW());

INSERT INTO dw_icons (name, category, file_type, file_data, file_size, width, height, tags, created_by, created_at) VALUES
('risk-assessment', 'CREDIT', 'svg',
'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="#DB0011" stroke-width="2"><path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"/><line x1="12" y1="9" x2="12" y2="13"/><line x1="12" y1="17" x2="12.01" y2="17"/></svg>'::bytea,
300, 24, 24, 'risk,assessment,warning', 'system', NOW());

INSERT INTO dw_icons (name, category, file_type, file_data, file_size, width, height, tags, created_by, created_at) VALUES
('repayment', 'CREDIT', 'svg',
'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="#DB0011" stroke-width="2"><circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/></svg>'::bytea,
180, 24, 24, 'repay,return,payback', 'system', NOW());

INSERT INTO dw_icons (name, category, file_type, file_data, file_size, width, height, tags, created_by, created_at) VALUES
('guarantee', 'CREDIT', 'svg',
'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="#DB0011" stroke-width="2"><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/></svg>'::bytea,
160, 24, 24, 'guarantee,collateral,security', 'system', NOW());


-- =====================================================
-- ACCOUNT - Account services
-- =====================================================

INSERT INTO dw_icons (name, category, file_type, file_data, file_size, width, height, tags, created_by, created_at) VALUES
('account-open', 'ACCOUNT', 'svg',
'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="#DB0011" stroke-width="2"><path d="M16 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/><circle cx="8.5" cy="7" r="4"/><line x1="20" y1="8" x2="20" y2="14"/><line x1="23" y1="11" x2="17" y2="11"/></svg>'::bytea,
260, 24, 24, 'open,create,register', 'system', NOW());

INSERT INTO dw_icons (name, category, file_type, file_data, file_size, width, height, tags, created_by, created_at) VALUES
('account-close', 'ACCOUNT', 'svg',
'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="#DB0011" stroke-width="2"><path d="M16 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/><circle cx="8.5" cy="7" r="4"/><line x1="18" y1="8" x2="23" y2="13"/><line x1="23" y1="8" x2="18" y2="13"/></svg>'::bytea,
260, 24, 24, 'close,cancel,terminate', 'system', NOW());

INSERT INTO dw_icons (name, category, file_type, file_data, file_size, width, height, tags, created_by, created_at) VALUES
('account-modify', 'ACCOUNT', 'svg',
'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="#DB0011" stroke-width="2"><path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/><path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/></svg>'::bytea,
250, 24, 24, 'modify,edit,change', 'system', NOW());

INSERT INTO dw_icons (name, category, file_type, file_data, file_size, width, height, tags, created_by, created_at) VALUES
('account-query', 'ACCOUNT', 'svg',
'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="#DB0011" stroke-width="2"><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/></svg>'::bytea,
170, 24, 24, 'query,search,find', 'system', NOW());

INSERT INTO dw_icons (name, category, file_type, file_data, file_size, width, height, tags, created_by, created_at) VALUES
('account-freeze', 'ACCOUNT', 'svg',
'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="#DB0011" stroke-width="2"><rect x="3" y="11" width="18" height="11" rx="2" ry="2"/><path d="M7 11V7a5 5 0 0 1 10 0v4"/></svg>'::bytea,
200, 24, 24, 'freeze,lock,suspend', 'system', NOW());


-- =====================================================
-- PAYMENT - Payment and settlement
-- =====================================================

INSERT INTO dw_icons (name, category, file_type, file_data, file_size, width, height, tags, created_by, created_at) VALUES
('transfer', 'PAYMENT', 'svg',
'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="#DB0011" stroke-width="2"><line x1="17" y1="17" x2="7" y2="7"/><polyline points="7 17 7 7 17 7"/></svg>'::bytea,
170, 24, 24, 'transfer,remit,send', 'system', NOW());

INSERT INTO dw_icons (name, category, file_type, file_data, file_size, width, height, tags, created_by, created_at) VALUES
('receive-payment', 'PAYMENT', 'svg',
'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="#00A651" stroke-width="2"><line x1="12" y1="19" x2="12" y2="5"/><polyline points="5 12 12 5 19 12"/></svg>'::bytea,
170, 24, 24, 'receive,income,credit', 'system', NOW());

INSERT INTO dw_icons (name, category, file_type, file_data, file_size, width, height, tags, created_by, created_at) VALUES
('make-payment', 'PAYMENT', 'svg',
'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="#DB0011" stroke-width="2"><line x1="12" y1="5" x2="12" y2="19"/><polyline points="19 12 12 19 5 12"/></svg>'::bytea,
170, 24, 24, 'pay,payment,debit', 'system', NOW());

INSERT INTO dw_icons (name, category, file_type, file_data, file_size, width, height, tags, created_by, created_at) VALUES
('batch-payment', 'PAYMENT', 'svg',
'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="#DB0011" stroke-width="2"><rect x="3" y="3" width="7" height="7"/><rect x="14" y="3" width="7" height="7"/><rect x="14" y="14" width="7" height="7"/><rect x="3" y="14" width="7" height="7"/></svg>'::bytea,
220, 24, 24, 'batch,bulk,mass', 'system', NOW());

INSERT INTO dw_icons (name, category, file_type, file_data, file_size, width, height, tags, created_by, created_at) VALUES
('reconciliation', 'PAYMENT', 'svg',
'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="#DB0011" stroke-width="2"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/><line x1="9" y1="15" x2="15" y2="15"/></svg>'::bytea,
250, 24, 24, 'reconcile,settle,clear', 'system', NOW());


-- =====================================================
-- CUSTOMER - Customer management
-- =====================================================

INSERT INTO dw_icons (name, category, file_type, file_data, file_size, width, height, tags, created_by, created_at) VALUES
('customer-info', 'CUSTOMER', 'svg',
'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="#DB0011" stroke-width="2"><path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/><circle cx="12" cy="7" r="4"/></svg>'::bytea,
180, 24, 24, 'customer,user,person', 'system', NOW());

INSERT INTO dw_icons (name, category, file_type, file_data, file_size, width, height, tags, created_by, created_at) VALUES
('corporate-customer', 'CUSTOMER', 'svg',
'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="#DB0011" stroke-width="2"><path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M23 21v-2a4 4 0 0 0-3-3.87"/><path d="M16 3.13a4 4 0 0 1 0 7.75"/></svg>'::bytea,
280, 24, 24, 'corporate,company,enterprise', 'system', NOW());

INSERT INTO dw_icons (name, category, file_type, file_data, file_size, width, height, tags, created_by, created_at) VALUES
('kyc-verify', 'CUSTOMER', 'svg',
'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="#DB0011" stroke-width="2"><rect x="3" y="4" width="18" height="16" rx="2"/><circle cx="9" cy="10" r="2"/><path d="M15 8h2"/><path d="M15 12h2"/><path d="M7 16h10"/></svg>'::bytea,
240, 24, 24, 'KYC,verify,identity', 'system', NOW());

INSERT INTO dw_icons (name, category, file_type, file_data, file_size, width, height, tags, created_by, created_at) VALUES
('due-diligence', 'CUSTOMER', 'svg',
'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="#DB0011" stroke-width="2"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/><path d="M9 15l2 2 4-4"/></svg>'::bytea,
260, 24, 24, 'diligence,investigate,audit', 'system', NOW());

INSERT INTO dw_icons (name, category, file_type, file_data, file_size, width, height, tags, created_by, created_at) VALUES
('customer-rating', 'CUSTOMER', 'svg',
'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="#DB0011" stroke-width="2"><polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2"/></svg>'::bytea,
200, 24, 24, 'rating,grade,star', 'system', NOW());


-- =====================================================
-- COMPLIANCE - AML, Risk control
-- =====================================================

INSERT INTO dw_icons (name, category, file_type, file_data, file_size, width, height, tags, created_by, created_at) VALUES
('aml-check', 'COMPLIANCE', 'svg',
'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="#DB0011" stroke-width="2"><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/><path d="M9 12l2 2 4-4"/></svg>'::bytea,
200, 24, 24, 'AML,compliance,check', 'system', NOW());

INSERT INTO dw_icons (name, category, file_type, file_data, file_size, width, height, tags, created_by, created_at) VALUES
('compliance-review', 'COMPLIANCE', 'svg',
'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="#DB0011" stroke-width="2"><path d="M9 11l3 3L22 4"/><path d="M21 12v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11"/></svg>'::bytea,
220, 24, 24, 'compliance,review,inspect', 'system', NOW());

INSERT INTO dw_icons (name, category, file_type, file_data, file_size, width, height, tags, created_by, created_at) VALUES
('risk-alert', 'COMPLIANCE', 'svg',
'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="#DB0011" stroke-width="2"><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/></svg>'::bytea,
190, 24, 24, 'risk,alert,warning', 'system', NOW());

INSERT INTO dw_icons (name, category, file_type, file_data, file_size, width, height, tags, created_by, created_at) VALUES
('audit-trail', 'COMPLIANCE', 'svg',
'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="#DB0011" stroke-width="2"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/><line x1="16" y1="13" x2="8" y2="13"/><line x1="16" y1="17" x2="8" y2="17"/><line x1="10" y1="9" x2="8" y2="9"/></svg>'::bytea,
300, 24, 24, 'audit,trail,log', 'system', NOW());

INSERT INTO dw_icons (name, category, file_type, file_data, file_size, width, height, tags, created_by, created_at) VALUES
('blacklist', 'COMPLIANCE', 'svg',
'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="#DB0011" stroke-width="2"><circle cx="12" cy="12" r="10"/><line x1="4.93" y1="4.93" x2="19.07" y2="19.07"/></svg>'::bytea,
170, 24, 24, 'blacklist,block,restrict', 'system', NOW());


-- =====================================================
-- OPERATION - Operations management
-- =====================================================

INSERT INTO dw_icons (name, category, file_type, file_data, file_size, width, height, tags, created_by, created_at) VALUES
('report', 'OPERATION', 'svg',
'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="#DB0011" stroke-width="2"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/><line x1="12" y1="18" x2="12" y2="12"/><line x1="8" y1="18" x2="8" y2="15"/><line x1="16" y1="18" x2="16" y2="13"/></svg>'::bytea,
300, 24, 24, 'report,statistics,chart', 'system', NOW());

INSERT INTO dw_icons (name, category, file_type, file_data, file_size, width, height, tags, created_by, created_at) VALUES
('dashboard', 'OPERATION', 'svg',
'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="#DB0011" stroke-width="2"><rect x="3" y="3" width="7" height="9"/><rect x="14" y="3" width="7" height="5"/><rect x="14" y="12" width="7" height="9"/><rect x="3" y="16" width="7" height="5"/></svg>'::bytea,
240, 24, 24, 'dashboard,board,monitor', 'system', NOW());

INSERT INTO dw_icons (name, category, file_type, file_data, file_size, width, height, tags, created_by, created_at) VALUES
('notification', 'OPERATION', 'svg',
'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="#DB0011" stroke-width="2"><path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9"/><path d="M13.73 21a2 2 0 0 1-3.46 0"/></svg>'::bytea,
200, 24, 24, 'notification,alert,message', 'system', NOW());

INSERT INTO dw_icons (name, category, file_type, file_data, file_size, width, height, tags, created_by, created_at) VALUES
('schedule', 'OPERATION', 'svg',
'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="#DB0011" stroke-width="2"><rect x="3" y="4" width="18" height="18" rx="2" ry="2"/><line x1="16" y1="2" x2="16" y2="6"/><line x1="8" y1="2" x2="8" y2="6"/><line x1="3" y1="10" x2="21" y2="10"/></svg>'::bytea,
230, 24, 24, 'schedule,calendar,plan', 'system', NOW());

INSERT INTO dw_icons (name, category, file_type, file_data, file_size, width, height, tags, created_by, created_at) VALUES
('settings', 'OPERATION', 'svg',
'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="#DB0011" stroke-width="2"><circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1 0 2.83 2 2 0 0 1-2.83 0l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-2 2 2 2 0 0 1-2-2v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83 0 2 2 0 0 1 0-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1-2-2 2 2 0 0 1 2-2h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 0-2.83 2 2 0 0 1 2.83 0l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 2-2 2 2 0 0 1 2 2v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 0 2 2 0 0 1 0 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 2 2 2 2 0 0 1-2 2h-.09a1.65 1.65 0 0 0-1.51 1z"/></svg>'::bytea,
650, 24, 24, 'settings,config,manage', 'system', NOW());


-- =====================================================
-- GENERAL - General purpose icons
-- =====================================================

INSERT INTO dw_icons (name, category, file_type, file_data, file_size, width, height, tags, created_by, created_at) VALUES
('start', 'GENERAL', 'svg',
'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="#00A651" stroke-width="2"><circle cx="12" cy="12" r="10"/><polygon points="10 8 16 12 10 16 10 8" fill="#00A651"/></svg>'::bytea,
200, 24, 24, 'start,begin,execute', 'system', NOW());

INSERT INTO dw_icons (name, category, file_type, file_data, file_size, width, height, tags, created_by, created_at) VALUES
('end', 'GENERAL', 'svg',
'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="#DB0011" stroke-width="2"><circle cx="12" cy="12" r="10"/><rect x="9" y="9" width="6" height="6" fill="#DB0011"/></svg>'::bytea,
200, 24, 24, 'end,stop,finish', 'system', NOW());

INSERT INTO dw_icons (name, category, file_type, file_data, file_size, width, height, tags, created_by, created_at) VALUES
('pending', 'GENERAL', 'svg',
'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="#F5A623" stroke-width="2"><circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/></svg>'::bytea,
180, 24, 24, 'pending,waiting,progress', 'system', NOW());

INSERT INTO dw_icons (name, category, file_type, file_data, file_size, width, height, tags, created_by, created_at) VALUES
('document', 'GENERAL', 'svg',
'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="#DB0011" stroke-width="2"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/></svg>'::bytea,
200, 24, 24, 'document,file,paper', 'system', NOW());

INSERT INTO dw_icons (name, category, file_type, file_data, file_size, width, height, tags, created_by, created_at) VALUES
('attachment', 'GENERAL', 'svg',
'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="#DB0011" stroke-width="2"><path d="M21.44 11.05l-9.19 9.19a6 6 0 0 1-8.49-8.49l9.19-9.19a4 4 0 0 1 5.66 5.66l-9.2 9.19a2 2 0 0 1-2.83-2.83l8.49-8.48"/></svg>'::bytea,
230, 24, 24, 'attachment,upload,file', 'system', NOW());

INSERT INTO dw_icons (name, category, file_type, file_data, file_size, width, height, tags, created_by, created_at) VALUES
('print', 'GENERAL', 'svg',
'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="#DB0011" stroke-width="2"><polyline points="6 9 6 2 18 2 18 9"/><path d="M6 18H4a2 2 0 0 1-2-2v-5a2 2 0 0 1 2-2h16a2 2 0 0 1 2 2v5a2 2 0 0 1-2 2h-2"/><rect x="6" y="14" width="12" height="8"/></svg>'::bytea,
280, 24, 24, 'print,output,export', 'system', NOW());

INSERT INTO dw_icons (name, category, file_type, file_data, file_size, width, height, tags, created_by, created_at) VALUES
('email', 'GENERAL', 'svg',
'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="#DB0011" stroke-width="2"><path d="M4 4h16c1.1 0 2 .9 2 2v12c0 1.1-.9 2-2 2H4c-1.1 0-2-.9-2-2V6c0-1.1.9-2 2-2z"/><polyline points="22,6 12,13 2,6"/></svg>'::bytea,
220, 24, 24, 'email,send,notify', 'system', NOW());


-- =====================================================
-- Summary
-- =====================================================
-- Total: 43 default icons
-- APPROVAL: 6
-- CREDIT: 5
-- ACCOUNT: 5
-- PAYMENT: 5
-- CUSTOMER: 5
-- COMPLIANCE: 5
-- OPERATION: 5
-- GENERAL: 7
