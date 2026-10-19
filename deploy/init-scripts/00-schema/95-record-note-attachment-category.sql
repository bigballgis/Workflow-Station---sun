-- Run with the new Portal binary; old binaries cannot decode ATTACHMENT_UPDATE.
-- Identify historical attachment notifications by the authoritative delivery ledger.
UPDATE up_notification n SET type='ATTACHMENT_UPDATE'
WHERE n.type='PROCESS' AND EXISTS (
    SELECT 1 FROM up_record_note_deliveries d
    WHERE d.notification_id=n.id AND d.user_id=n.user_id AND d.state='DELIVERED'
);

-- Inherit prior Process preferences once; subsequent changes are independent.
INSERT INTO up_notification_preference
    (user_id,notification_type,email_enabled,browser_enabled,in_app_enabled,
     quiet_start_time,quiet_end_time,created_at,updated_at)
SELECT u.id,'ATTACHMENT_UPDATE',COALESCE(p.email_enabled,false),
       COALESCE(p.browser_enabled,true),COALESCE(p.in_app_enabled,true),
       p.quiet_start_time,p.quiet_end_time,now(),now()
FROM sys_users u LEFT JOIN up_notification_preference p
    ON p.user_id=u.id AND p.notification_type='PROCESS'
ON CONFLICT (user_id,notification_type) DO NOTHING;
