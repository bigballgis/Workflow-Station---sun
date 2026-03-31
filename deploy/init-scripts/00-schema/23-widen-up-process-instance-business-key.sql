-- =====================================================
-- up_process_instance.business_key: align with JPA
-- user-portal & developer-workstation ProcessInstance
-- use @Column(length = 255); base 03 script used VARCHAR(100).
-- =====================================================

ALTER TABLE up_process_instance
    ALTER COLUMN business_key TYPE VARCHAR(255);

COMMENT ON COLUMN up_process_instance.business_key IS 'Business key (aligned with entity length 255)';
