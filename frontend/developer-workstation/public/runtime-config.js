// Per-environment runtime config. Overwritten at container start by docker-entrypoint.sh;
// this empty default keeps local builds working (no DSP endpoint = no silent AMToken refresh).
window.__DW_RUNTIME_CONFIG__ = {};
