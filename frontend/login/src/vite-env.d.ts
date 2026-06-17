/// <reference types="vite/client" />

/**
 * 构建期注入的 DSP 相关环境变量类型声明。
 * 在 vite/client 已有的 ImportMetaEnv（含索引签名 [key]: any）基础上做声明合并，
 * 为这些键提供精确类型，避免在业务代码中出现 any。
 */
interface ImportMetaEnv {
  /** 免密入口显隐：仅当显式 'false' 时隐藏（其余值/未设均显示）。 */
  readonly VITE_DSP_ENABLED?: string
  /** 浏览器侧 DSP authenticate 端点（主动获取 AMToken）；为空则不主动获取，仅读已存在 token。 */
  readonly VITE_DSP_AUTHENTICATE_URL?: string
  /** DSP 客户端 ID（X-Client-Id 头）。 */
  readonly VITE_DSP_CLIENT_ID?: string
  /** DSP 客户端密钥（X-Client-Secret 头）。注意：浏览器侧会暴露，仅 public client 模式下配置。 */
  readonly VITE_DSP_CLIENT_SECRET?: string
  /** Accept-API-Version 头（默认 protocol=1.0,resource=2.1）。 */
  readonly VITE_DSP_ACCEPT_API_VERSION?: string
}
