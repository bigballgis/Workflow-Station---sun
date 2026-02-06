-- 更新BPMN中所有任务的actionIds为字符串格式
-- 日期: 2026-02-06

-- 获取当前的BPMN内容并更新
UPDATE sys_processes
SET bpmn_xml = REPLACE(
    REPLACE(
        REPLACE(
            REPLACE(
                REPLACE(
                    bpmn_xml,
                    -- Task_CreditCheck: 更新actionIds
                    '<custom:property name="actionIds" value="[13,14,15,16,18]" />',
                    '<custom:property name="actionIds" value="[action-dl-credit-check,action-dl-approve-loan,action-dl-reject-loan,action-dl-request-info]" />'
                ),
                -- Task_RiskAssessment: 更新actionIds
                '<custom:property name="actionIds" value="[19,16,17]" />',
                '<custom:property name="actionIds" value="[action-dl-assess-risk,action-dl-mark-low-risk,action-dl-mark-high-risk]" />'
            ),
            -- Task_ManagerApproval: 更新actionIds
            '<custom:property name="actionIds" value="[16,17,18]" />',
            '<custom:property name="actionIds" value="[action-dl-manager-approve,action-dl-manager-reject,action-dl-request-revision]" />'
        ),
        -- Task_SeniorManagerApproval: 更新actionIds
        '<custom:property name="actionIds" value="[16,17,18]" />',
        '<custom:property name="actionIds" value="[action-dl-senior-approve,action-dl-senior-reject,action-dl-escalate]" />'
    ),
    -- Task_Disbursement: 更新actionIds
    '<custom:property name="actionIds" value="[21,22,23]" />',
    '<custom:property name="actionIds" value="[action-dl-disburse,action-dl-hold-disbursement,action-dl-verify-account]" />'
)
WHERE process_key = 'DigitalLendingProcess';

-- 验证更新
SELECT process_key, process_name, 
       CASE 
           WHEN bpmn_xml LIKE '%action-dl-credit-check%' THEN 'Updated'
           ELSE 'Not Updated'
       END as status
FROM sys_processes
WHERE process_key = 'DigitalLendingProcess';
