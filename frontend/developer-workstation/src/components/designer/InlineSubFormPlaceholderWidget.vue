<template>
  <div
    class="inline-sub-form-placeholder-widget"
    :class="`is-${state}`"
    :data-fc-designer-binding-id="effectiveBindingId ?? undefined"
    @click.stop="onWidgetClick"
  >
    <!-- 图标 + 状态文字 -->
    <el-icon><Document /></el-icon>
    <span
      v-if="state === 'valid'"
      class="binding-name"
    >{{ displayName }}</span>
    <span
      v-else-if="state === 'unconfigured'"
      class="hint-text"
    >{{ t('form.inlineSubFormUnconfigured') }}</span>
    <el-tag
      v-else
      type="warning"
      size="small"
    >
      {{ t('form.inlineSubFormStale') }}
    </el-tag>

    <!-- 跳转按钮（仅 valid 状态显示） -->
    <el-button
      v-if="state === 'valid'"
      link
      type="primary"
      size="small"
      class="navigate-btn"
      @click.stop="emit('navigate', effectiveBindingId!)"
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
import { normalizeBindingId } from '@/utils/bindingDisplayHelpers'

/**
 * Canvas chip for the `inlineSubForm` widget: it marks the spot where the bound SUB
 * table's designed form gets laid out inline at runtime. Unlike `subTable`, no grid is
 * rendered above it — the form itself IS the component.
 *
 * The rule declares `input: false`, so form-create forwards neither `rule.props` nor
 * `rule.on`; `_bindingId` reaches us only because the drag rule's loadRule copies the
 * top-level value into props. See docs/design/inline-sub-form-component.md.
 */

interface DesignerSubBinding {
  id: number
  tableName: string
  tableDisplayName?: string
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

// Prefer injected subBindings from FormDesigner (via provide/inject),
// fall back to prop, then empty array. The injection is a FUNCTION, not a ref —
// fc-designer registers panel components in its own app context.
const injectedSubBindings = inject<() => DesignerSubBinding[]>('designerSubBindings', () => [])

const subBindings = computed(() => props.subBindings ?? injectedSubBindings())
const effectiveBindingId = computed(() => normalizeBindingId(props._bindingId ?? props.bindingId ?? null))

type PlaceholderState = 'unconfigured' | 'valid' | 'stale'

const state = computed((): PlaceholderState => {
  if (effectiveBindingId.value == null) return 'unconfigured'
  const found = subBindings.value.find(b => b.id === effectiveBindingId.value)
  return found ? 'valid' : 'stale'
})

const displayName = computed(() => {
  if (state.value !== 'valid' || effectiveBindingId.value == null) return null
  const binding = subBindings.value.find(b => b.id === effectiveBindingId.value)!
  const label = binding.tableDisplayName || binding.tableName
  return binding.tableDescription
    ? `${label}（${binding.tableDescription}）`
    : label
})
</script>

<style scoped>
.inline-sub-form-placeholder-widget {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 12px;
  border-radius: 4px;
  border: 1px dashed #dcdfe6;
  background: #f5f7fa;
  font-size: 13px;
  cursor: default;
  user-select: none;
}

.inline-sub-form-placeholder-widget.is-valid {
  border-color: #67c23a;
  background: #f0f9eb;
  color: #67c23a;
}

.inline-sub-form-placeholder-widget.is-stale {
  border-color: #e6a23c;
  background: #fdf6ec;
  color: #e6a23c;
}

.inline-sub-form-placeholder-widget.is-unconfigured {
  border-color: #dcdfe6;
  background: #f5f7fa;
  color: #909399;
}

.navigate-btn {
  margin-left: auto;
}
</style>
