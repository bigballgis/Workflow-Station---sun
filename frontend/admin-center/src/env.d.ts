/// <reference types="vite/client" />

declare module '*.vue' {
  import type { DefineComponent } from 'vue'
  const component: DefineComponent<{}, {}, any>
  export default component
}

declare module 'element-plus/dist/locale/zh-cn.mjs'
declare module 'element-plus/dist/locale/zh-tw.mjs'
declare module 'element-plus/dist/locale/en.mjs'

// Runtime config injected per-environment by docker-entrypoint.sh into public/config.js.
// (Currently no keys: the AP_BRIDGE_URL launcher gate was removed with the Admin Center
// automation entries — FR-E01; automation management lives in Developer Workstation now.)
interface Window {
  __APP_CONFIG__?: Record<string, string | undefined>
}
