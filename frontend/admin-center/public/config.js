// Runtime configuration, injected at container start by docker-entrypoint.sh (envsubst).
// Build-time bundling can't differ per environment (one image is promoted to uat/sit/prod),
// so per-environment values come from here at runtime via the deployment's env vars.
// Outside Docker (e.g. `vite dev`) the ${...} placeholders stay literal; the app detects
// the un-substituted form and falls back to its dev defaults.
window.__APP_CONFIG__ = window.__APP_CONFIG__ || {};
// (No per-environment keys at present. The AP_BRIDGE_URL launcher gate was removed with
// the Admin Center automation entries — automation management lives in DW now.)
