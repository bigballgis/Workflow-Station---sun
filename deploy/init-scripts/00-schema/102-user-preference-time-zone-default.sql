-- A new user's preferred time zone defaults to the platform's, Hong Kong time (WP19; was Asia/Shanghai, also UTC+8).
-- Existing preference rows keep the zone they hold. Idempotent.
ALTER TABLE up_user_preference ALTER COLUMN timezone SET DEFAULT 'Asia/Hong_Kong';
