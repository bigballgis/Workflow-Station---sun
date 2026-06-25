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
interface AppRuntimeConfig {
  AP_BRIDGE_URL?: string
}
interface Window {
  __APP_CONFIG__?: AppRuntimeConfig
}
