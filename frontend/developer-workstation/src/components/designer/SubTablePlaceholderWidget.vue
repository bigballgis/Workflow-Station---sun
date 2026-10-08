<template>
  <div
    class="sub-table-placeholder-widget"
    :class="`is-${state}`"
    :data-fc-designer-binding-id="effectiveBindingId ?? undefined"
    @click.stop="onWidgetClick"
  >
    <!-- 图标 + 状态文字 -->
    <el-icon><Grid /></el-icon>
    <span
      v-if="state === 'valid'"
      class="binding-copy"
    >
      <span
        v-if="displayTitle"
        class="display-title"
      >{{ displayTitle }}</span>
      <span class="binding-name">{{ bindingDisplayName }}</span>
    </span>
    <span
      v-else-if="state === 'unconfigured'"
      class="hint-text"
    >{{ t('form.subTablePlaceholderUnconfigured') }}</span>
    <el-tag
      v-else
      type="warning"
      size="small"
    >
      {{ t('form.subTablePlaceholderStale') }}
    </el-tag>

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
import { computed, inject } from 'vue'
// Icons are globally registered in main.ts via ElementPlusIconsVue
// No need for local imports, which can cause circular dependency issues in production build
import { useI18n } from 'vue-i18n'
import { formatSubTableBindingOptionLabel, normalizeBindingId } from '@/utils/bindingDisplayHelpers'

interface DesignerSubBinding {
  id: number
  tableName: string
  tableDisplayName?: string
  tableDescription: string
  bindingType: string
  foreignKeyField?: string | null
  bindingLinkMode?: string | null
}

// form-create passes rule props directly — _bindingId comes from rule._bindingId
const props = defineProps<{
  _bindingId?: number | null
  // legacy prop name support
  bindingId?: number | null
  _displayTitle?: string | null
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

// Prefer injected subBindings from FormDesigner (via provide/inject),
// fall back to prop, then empty array
const injectedSubBindings = inject<() => DesignerSubBinding[]>('designerSubBindings', () => [])

const subBindings = computed(() => props.subBindings ?? injectedSubBindings())
const bindingId = computed(() => normalizeBindingId(props._bindingId ?? props.bindingId ?? null))
const effectiveBindingId = bindingId

type PlaceholderState = 'unconfigured' | 'valid' | 'stale'

const state = computed((): PlaceholderState => {
  if (bindingId.value == null) return 'unconfigured'
  const found = subBindings.value.find(b => b.id === bindingId.value)
  return found ? 'valid' : 'stale'
})

const displayTitle = computed(() => String(props._displayTitle ?? '').trim())

const bindingDisplayName = computed(() => {
  if (state.value !== 'valid' || bindingId.value == null) return null
  const binding = subBindings.value.find(b => b.id === bindingId.value)!
  return formatSubTableBindingOptionLabel(binding)
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

.binding-copy {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 2px;
}

.display-title {
  color: #303133;
  font-weight: 600;
}

.binding-name {
  font-size: 12px;
}
</style>
