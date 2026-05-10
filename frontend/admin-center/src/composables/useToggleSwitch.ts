/**
 * el-switch 切换 composable (纯逻辑，零 UI)
 *
 * 管理 switch 的 loading 状态、乐观更新、失败回滚。
 * 不调用任何 notify / confirm —— 由调用方决定 UI 展示。
 *
 * @example
 * const { states, initState, toggle } = useToggleSwitch(
 *   (id, val) => functionUnitApi.setEnabled(id, val),
 * )
 * // 模板中: <el-switch v-model="states[row.id].value" :loading="states[row.id].loading"
 * //                   @change="(val) => handleToggle(row.id, val)" />
 * // handleToggle: const r = await toggle(id, val); r.ok ? notifySuccess(...) : notifyError(...)
 */

import { reactive } from 'vue'

export interface ToggleState {
  value: boolean
  loading: boolean
}

export interface ToggleResult {
  ok: boolean
  /** 失败时的错误码（来自 toggleFn 抛出的 AppError.code 或 error.message） */
  code?: string
}

export function useToggleSwitch<TId extends string = string>(
  toggleFn: (id: TId, value: boolean) => Promise<unknown>,
) {
  /** key = id, value = { value, loading } */
  const states = reactive<Record<string, ToggleState>>({})

  const getState = (id: TId): ToggleState => {
    if (!states[id as string]) {
      states[id as string] = { value: false, loading: false }
    }
    return states[id as string]
  }

  /** 设置初始状态（列表加载后调用） */
  const initState = (id: TId, value: boolean) => {
    states[id as string] = { value, loading: false }
  }

  /**
   * 切换处理。el-switch 的 @change 调用此方法。
   * 调用方应在调用前自行处理确认弹窗。
   *
   * @returns ToggleResult — ok 表示成功，否则 code 为错误码
   */
  const toggle = async (id: TId, newValue: boolean): Promise<ToggleResult> => {
    const state = getState(id)
    state.loading = true
    try {
      await toggleFn(id, newValue)
      state.value = newValue
      return { ok: true }
    } catch (e) {
      // 恢复原值
      state.value = !newValue
      const code = extractErrorCode(e)
      return { ok: false, code }
    } finally {
      state.loading = false
    }
  }

  return { states, getState, initState, toggle }
}

/** 从错误对象提取 code */
function extractErrorCode(e: unknown): string | undefined {
  if (e && typeof e === 'object') {
    const err = e as Record<string, unknown>
    if (typeof err.code === 'string') return err.code
    if (typeof err.message === 'string') return err.message
  }
  return undefined
}
