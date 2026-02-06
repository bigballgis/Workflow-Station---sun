-- =====================================================
-- Update Digital Lending BPMN with new action IDs
-- Only updates Task_DocumentVerification for now (the task we're testing)
-- =====================================================

-- Task_DocumentVerification already has the correct actionIds from previous update
-- Verify it's correct
SELECT 
    substring(
        convert_from(bytes_, 'UTF8'),
        position('Task_DocumentVerification' in convert_from(bytes_, 'UTF8')),
        500
    ) as document_verification_task
FROM act_ge_bytearray
WHERE name_ = 'DIGITAL_LENDING.bpmn'
ORDER BY id_ DESC
LIMIT 1;
