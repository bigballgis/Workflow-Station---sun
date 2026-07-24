import { get } from './request'

/**
 * ServiceTask 跨域登录入口（方案 B：跨域 SSO 握手）。
 *
 * <p>在 admin 域调用（cookie 在自己域有效）：后端验平台 JWT、用共享账号换 AP 会话、签发一次性 nonce，
 * 返回 {@code bridgeUrl}（AP 桥页地址 + {@code #nonce=<票>}）。前端拿到后整页跳转进 AP 域，
 * AP 域凭 nonce 换 token，<b>无需平台 cookie 跨子域</b>。
 */
export async function launchServiceTask(): Promise<string> {
  // 拦截器返回 HTTP body；/launch 用 ApiResponse 包了一层，故解 data.bridgeUrl（兼容裸返回）。
  const body = await get<unknown>('/internal/ap/launch')
  const b = body as { bridgeUrl?: string; data?: { bridgeUrl?: string } } | null
  return b?.data?.bridgeUrl || b?.bridgeUrl || ''
}
