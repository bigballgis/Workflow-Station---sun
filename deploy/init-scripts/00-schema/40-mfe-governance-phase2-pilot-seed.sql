-- MFE Governance Phase 2 — Pilot MFE Module Registry Entries
-- Registers notification-mfe and delegation-mfe for user-portal host
--
-- DEV mode (Vite dev servers):
--   psql -d n8n_dev -f <this-file>
--   Uses localhost:3100 / localhost:3101 for direct Vite access
--
-- DOCKER mode (nginx edge proxy):
--   Replace remote_entry_url with:
--     notification-mfe: /mfe-notification/assets/remoteEntry.js
--     delegation-mfe:   /mfe-delegation/assets/remoteEntry.js
--   The edge nginx proxies /mfe-notification/ → notification-mfe container

BEGIN;

-- ============ DEV mode (Vite dev servers) ============
-- Switch to DOCKER blocks below for Docker Compose deployment

-- Seed notification-mfe for user-portal (DEV environment)
INSERT INTO ac_frontend_module_registry (
  tenant_id, host_app, module_code, display_name, route_path, icon, order_no,
  remote_entry_url, exposed_module, enabled, required_permissions, tenant_scope, env, version
) VALUES (
  'DEFAULT', 'user-portal', 'notification-mfe', 'Notifications (MFE)', '/mfe/notifications', 'Bell', 40,
  'http://localhost:3100/assets/remoteEntry.js', './App', true,
  '[]'::jsonb, '[]'::jsonb, 'DEV', '1.0.0'
) ON CONFLICT (tenant_id, host_app, env, module_code) DO NOTHING;

-- Seed notification-mfe initial version record
INSERT INTO ac_frontend_module_version (module_registry_id, version, remote_entry_url, is_active, release_note)
SELECT id, '1.0.0', 'http://localhost:3100/assets/remoteEntry.js', true, 'Phase 2 pilot — initial release'
FROM ac_frontend_module_registry
WHERE module_code = 'notification-mfe' AND env = 'DEV'
ON CONFLICT (module_registry_id, version) DO NOTHING;

-- Seed delegation-mfe for user-portal (DEV environment)
INSERT INTO ac_frontend_module_registry (
  tenant_id, host_app, module_code, display_name, route_path, icon, order_no,
  remote_entry_url, exposed_module, enabled, required_permissions, tenant_scope, env, version
) VALUES (
  'DEFAULT', 'user-portal', 'delegation-mfe', 'Delegation (MFE)', '/mfe/delegations', 'Share', 45,
  'http://localhost:3101/assets/remoteEntry.js', './App', true,
  '[]'::jsonb, '[]'::jsonb, 'DEV', '1.0.0'
) ON CONFLICT (tenant_id, host_app, env, module_code) DO NOTHING;

-- Seed delegation-mfe initial version record
INSERT INTO ac_frontend_module_version (module_registry_id, version, remote_entry_url, is_active, release_note)
SELECT id, '1.0.0', 'http://localhost:3101/assets/remoteEntry.js', true, 'Phase 2 pilot — initial release'
FROM ac_frontend_module_registry
WHERE module_code = 'delegation-mfe' AND env = 'DEV'
ON CONFLICT (module_registry_id, version) DO NOTHING;

-- ============ DOCKER mode (uncomment for Docker Compose) ============
-- When using Docker Compose, the edge nginx proxies MFE requests.
-- Replace the remote_entry_url values above with these:

-- UPDATE ac_frontend_module_registry
-- SET remote_entry_url = '/mfe-notification/assets/remoteEntry.js'
-- WHERE module_code = 'notification-mfe' AND env = 'DEV';

-- UPDATE ac_frontend_module_version
-- SET remote_entry_url = '/mfe-notification/assets/remoteEntry.js'
-- WHERE module_registry_id IN (SELECT id FROM ac_frontend_module_registry WHERE module_code = 'notification-mfe');

-- UPDATE ac_frontend_module_registry
-- SET remote_entry_url = '/mfe-delegation/assets/remoteEntry.js'
-- WHERE module_code = 'delegation-mfe' AND env = 'DEV';

-- UPDATE ac_frontend_module_version
-- SET remote_entry_url = '/mfe-delegation/assets/remoteEntry.js'
-- WHERE module_registry_id IN (SELECT id FROM ac_frontend_module_registry WHERE module_code = 'delegation-mfe');

COMMIT;

\echo '✓ Pilot MFE modules registered'
