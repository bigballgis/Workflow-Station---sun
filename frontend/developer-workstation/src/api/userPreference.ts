/**
 * 用户 UI 偏好（后端 dw_user_preferences）。
 * scope='user'（默认）按账号存取；scope='shared' 为全平台共享一份（如 Launchpad 布局）。
 * value 为前端自定义 JSON 字符串，后端只存取不解析。
 */
import api from './index'

export type PreferenceScope = 'user' | 'shared'

export const userPreferenceApi = {
  /** 读取偏好；后端无记录时返回 null */
  get: async (key: string, scope: PreferenceScope = 'user'): Promise<string | null> => {
    const res = await api.get<never, { data: string | null }>(`/user-preferences/${key}`, {
      params: { scope },
    })
    return res.data ?? null
  },

  /** 保存偏好（覆盖写；shared 作用域为 last-write-wins） */
  save: async (key: string, value: string, scope: PreferenceScope = 'user'): Promise<void> => {
    await api.put(`/user-preferences/${key}`, { value }, { params: { scope } })
  },
}
