/**
 * el-switch 切换 composable
 * 
 * 统一处理 switch 的 loading 状态、乐观更新、失败回滚。
 * 消除各组件中重复的 _enabledLoading / _portalLoading 等手动状态管理。
 * 
 * @example
 * const { toggleState, toggle } = useToggleSwitch(
 *   (id, val) => functionUnitApi.setEnabled(id, val),
 *   { successMessage: '操作成功' }
 * )
 * // 在模板中: <el-switch v-model="toggleState[row.id]" @change="(val) => toggle(row.id, val)" />
 */

import { reactive } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'

export interface ToggleState {
  value: boolean
  loading: boolean
}

export interface UseToggleSwitchOptions {
  /** 切换成功消息 */
  successMessage?: string
  /** 失败消息 */
  errorMessage?: string
  /** 是否需要确认弹窗（disable 时） */
  confirmOnDisable?: {
    message: string
    title: string
  }
}

export function useToggleSwitch<TId extends string = string>(
  toggleFn: (id: TId, value: boolean) => Promise<any>,
  options: UseToggleSwitchOptions = {}
) {
  const { successMessage = '操作成功', errorMessage = '操作失败', confirmOnDisable } = options

  /** key = id, value = { value, loading } */
  const states = reactive<Record<string, ToggleState>>({})

  const getState = (id: TId): ToggleState => {
    if (!states[id as string]) {
      states[id as string] = { value: false, loading: false }
    }
    return states[id as string]
  }

  /**
   * 设置初始状态
   */
  const initState = (id: TId, value: boolean) => {
    states[id as string] = { value, loading: false }
  }

  /**
   * 切换处理。el-switch 的 @change 调用此方法。
   * @returns 原 value（如果用户取消确认），或切换后的 value
   */
  const toggle = async (id: TId, newValue: boolean): Promise<void> => {
    const state = getState(id)

    // 如果是 disable，可能需要确认
    if (!newValue && confirmOnDisable) {
      try {
        await ElMessageBox.confirm(confirmOnDisable.message, confirmOnDisable.title, { type: 'warning' })
      } catch {
        // 用户取消 —— 恢复原值 (true)
        state.value = true
        return
      }
    }

    state.loading = true
    try {
      await toggleFn(id, newValue)
      state.value = newValue
      ElMessage.success(successMessage)
    } catch (e) {
      // 恢复原值
      state.value = !newValue
      ElMessage.error(errorMessage)
    } finally {
      state.loading = false
    }
  }

  return { states, getState, initState, toggle }
}
