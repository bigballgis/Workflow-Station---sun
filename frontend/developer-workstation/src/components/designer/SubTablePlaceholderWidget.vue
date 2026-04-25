<template>
  <div class="sub-table-placeholder-widget" :class="`is-${state}`" @click.stop="onWidgetClick">
    <!-- 图标 + 状态文字 -->
    <el-icon><Grid /></el-icon>
    <span v-if="state === 'valid'" class="binding-name">{{ displayName }}</span>
    <span v-else-if="state === 'unconfigured'" class="hint-text">{{ t('designer.subTablePlaceholderUnconfigured') }}</span>
    <el-tag v-else type="warning" size="small">{{ t('designer.subTablePlaceholderStale') }}</el-tag>

    <!-- 跳转按钮（仅 valid 状态显示） -->
    <el-button
      v-if="state === 'valid'"
      link
      type="primary"
      size="small"
      class="navigate-btn"
      @click.stop="emit('navigate', _bindingId!)"
    >
      <el-icon><ArrowRight /></el-icon>
    </el-button>
  </div>
</template>

<script setup lang="ts">
import { computed, inject, onMounted, getCurrentInstance } from 'vue'
// Icons are globally registered in main.ts via ElementPlusIconsVue
// No need for local imports, which can cause circular dependency issues in production build
import { useI18n } from 'vue-i18n'

interface DesignerSubBinding {
  id: number
  tableName: string
  tableDescription: string
  bindingType: string
}

// form-create passes rule props directly — _bindingId comes from rule._bindingId
const props = defineProps<{
  _bindingId?: number | null
  // legacy prop name support
  bindingId?: number | null
  subBindings?: DesignerSubBinding[]
  formCreateInject?: any
}>()

const emit = defineEmits<{
  'navigate': [bindingId: number]
}>()
const { t } = useI18n()

// When clicked in designer, manually trigger DragTool selection
// by finding the parent _fd-drag-tool and dispatching a click on it
function onWidgetClick(e: MouseEvent) {
  e.stopPropagation()
  const el = e.currentTarget as HTMLElement
  const dragTool = el.closest('._fd-drag-tool') as HTMLElement | null
  if (dragTool) {
    // Create a new click event that targets the drag tool directly
    const clickEvent = new MouseEvent('click', { bubbles: false, cancelable: true })
    dragTool.dispatchEvent(clickEvent)
  }
}

onMounted(() => {
  const instance = getCurrentInstance()
  const el = instance?.proxy?.$el as HTMLElement | null
  if (el) {
    console.log('[SubTable] mounted, parent classes:', el.parentElement?.className, 'grandparent:', el.parentElement?.parentElement?.className)
    const dragTool = el.closest('._fd-drag-tool')
    console.log('[SubTable] closest _fd-drag-tool:', dragTool)
    const dragMask = dragTool?.querySelector('._fd-drag-mask')
    console.log('[SubTable] _fd-drag-mask:', dragMask)
    // Add a click listener to the drag tool to see if it fires
    dragTool?.addEventListener('click', (e) => {
      console.log('[SubTable] _fd-drag-tool clicked! target:', (e.target as HTMLElement)?.className)
    })
  }
})

// Prefer injected subBindings from FormDesigner (via provide/inject),
// fall back to prop, then empty array
const injectedSubBindings = inject<() => DesignerSubBinding[]>('designerSubBindings', () => [])

const subBindings = computed(() => props.subBindings ?? injectedSubBindings())
const bindingId = computed(() => props._bindingId ?? props.bindingId ?? null)

type PlaceholderState = 'unconfigured' | 'valid' | 'stale'

const state = computed((): PlaceholderState => {
  if (!bindingId.value) return 'unconfigured'
  const found = subBindings.value.find(b => b.id === bindingId.value)
  return found ? 'valid' : 'stale'
})

const displayName = computed(() => {
  if (state.value !== 'valid') return null
  const binding = subBindings.value.find(b => b.id === bindingId.value)!
  return binding.tableDescription
    ? `${binding.tableName}（${binding.tableDescription}）`
    : binding.tableName
})
</script>

<style scoped>
.sub-table-placeholder-widget {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 12px;
  border-radius: 4px;
  border: 1px solid #dcdfe6;
  background: #f5f7fa;
  font-size: 13px;
  cursor: default;
  user-select: none;
}

.sub-table-placeholder-widget.is-valid {
  border-color: #409eff;
  background: #ecf5ff;
  color: #409eff;
}

.sub-table-placeholder-widget.is-stale {
  border-color: #e6a23c;
  background: #fdf6ec;
  color: #e6a23c;
}

.sub-table-placeholder-widget.is-unconfigured {
  border-color: #dcdfe6;
  background: #f5f7fa;
  color: #909399;
}

.navigate-btn {
  margin-left: auto;
}
</style>
