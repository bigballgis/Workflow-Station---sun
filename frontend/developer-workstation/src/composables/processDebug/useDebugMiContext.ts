import { computed } from 'vue'
import { ElMessage } from 'element-plus'
import type { DebugState, GeneratedCollectionPreview, MiContext } from './useDebugState'

interface UseDebugMiContextOptions {
  state: DebugState
  t: (key: string, params?: Record<string, unknown>) => string
  /** Wrapper closure to break the cycle with step application (set node form tab). */
  hasCurrentNodeFormBinding: () => boolean
}

/**
 * 多实例（MI）/并行实例上下文：派生有效 MI 上下文、MI 状态文案、
 * 并行实例切换与生成集合预览复制。行为与原 SFC 完全一致。
 */
export function useDebugMiContext(options: UseDebugMiContextOptions) {
  const { state, t, hasCurrentNodeFormBinding } = options
  const {
    currentMiContext,
    activeParallelMi,
    currentVariables,
    parallelInstancePicker,
    activeTab,
  } = state

  const effectiveMiContext = computed(() => {
    const ctx = currentMiContext.value
    const scope = activeParallelMi.value
    if (!ctx || !scope?.collectionVariable) return ctx

    const collection = currentVariables.value[scope.collectionVariable]
    if (!Array.isArray(collection) || collection.length === 0) return ctx

    const index = Math.min(
      Math.max(parallelInstancePicker.value, 1),
      collection.length,
    ) - 1
    const currentItem = collection[index]
    if (!currentItem || typeof currentItem !== 'object') return ctx

    return {
      ...ctx,
      instanceIndex: index + 1,
      totalInstances: scope.totalInstances,
      currentItem: currentItem as Record<string, any>,
      parallelMode: true,
    }
  })

  const miExecutionModeText = computed(() => {
    if (activeParallelMi.value) return t('debug.miParallelMode')
    const ctx = currentMiContext.value
    if (ctx?.sequential === true && ctx.phase) return t('debug.miSequentialMode')
    return ''
  })

  const miInstanceText = computed(() => {
    const ctx = effectiveMiContext.value
    if (!ctx?.instanceIndex || !ctx.totalInstances || ctx.phase !== 'instance') return ''
    return t('debug.miInstanceProgress', {
      current: ctx.instanceIndex,
      total: ctx.totalInstances
    })
  })

  const miAssigneeText = computed(() => {
    const item = effectiveMiContext.value?.currentItem
    if (!item || typeof item !== 'object') return ''
    const assignee = item.assignee_id ?? item.assignee ?? item.user_id
    if (assignee == null || assignee === '') return ''
    return t('debug.miAssigneeValue', { assignee: String(assignee) })
  })

  const miCompletionConditionText = computed(() => {
    const expr = currentMiContext.value?.completionCondition
    return expr ? String(expr) : ''
  })

  function syncParallelMiScope(miContext: MiContext | null) {
    if (!miContext) {
      activeParallelMi.value = null
      return
    }
    if (!miContext.parallelMode || !miContext.collectionVariable) {
      return
    }
    activeParallelMi.value = {
      collectionVariable: miContext.collectionVariable,
      elementVariable: miContext.elementVariable || 'currentItem',
      totalInstances: miContext.totalInstances ?? 0,
      subProcessId: miContext.subProcessId,
    }
    if (miContext.instanceIndex && miContext.instanceIndex > 0) {
      parallelInstancePicker.value = miContext.instanceIndex
    }
  }

  function handleParallelInstanceChange() {
    if (!activeParallelMi.value) return
    const scope = activeParallelMi.value
    const collection = currentVariables.value[scope.collectionVariable]
    if (!Array.isArray(collection)) return
    const index = parallelInstancePicker.value - 1
    const item = collection[index]
    if (!item || typeof item !== 'object') return
    currentVariables.value = {
      ...currentVariables.value,
      [scope.elementVariable]: item,
    }
    if (hasCurrentNodeFormBinding()) {
      activeTab.value = 'nodeForm'
    }
  }

  async function copyCollectionJson(collection: GeneratedCollectionPreview) {
    try {
      await navigator.clipboard.writeText(JSON.stringify(collection.rows, null, 2))
      ElMessage.success(t('debug.copyCollectionSuccess'))
    } catch {
      ElMessage.error(t('debug.copyCollectionFailed'))
    }
  }

  return {
    effectiveMiContext,
    miExecutionModeText,
    miInstanceText,
    miAssigneeText,
    miCompletionConditionText,
    syncParallelMiScope,
    handleParallelInstanceChange,
    copyCollectionJson,
  }
}
