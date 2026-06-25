// Runtime configuration, injected at container start by docker-entrypoint.sh (envsubst).
// Build-time bundling can't differ per environment (one image is promoted to uat/sit/prod),
// so per-environment values come from here at runtime via the deployment's env vars.
// Outside Docker (e.g. `vite dev`) the ${...} placeholders stay literal; the app detects
// the un-substituted form and falls back to its dev defaults.
window.__APP_CONFIG__ = window.__APP_CONFIG__ || {};
// Activepieces login-bridge URL. Empty -> the admin "Activepieces" launcher stays hidden
// (prod: AP is runtime-only, no UI). Non-prod sets it to the env's AP gateway bridge URL.
window.__APP_CONFIG__.AP_BRIDGE_URL = "${AP_BRIDGE_URL}";
