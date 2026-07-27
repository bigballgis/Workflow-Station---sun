<template>
  <div
    class="link-form-placeholder-widget"
    :class="`is-${state}`"
    :data-fc-designer-link-form-id="effectiveComponentId ?? undefined"
    @click.stop="onWidgetClick"
  >
    <el-icon><Link /></el-icon>
    <span
      v-if="state === 'valid'"
      class="component-name"
    >{{ displayName }}</span>
    <span
      v-else-if="state === 'unconfigured'"
      class="hint-text"
    >{{ t('form.linkFormPlaceholderUnconfigured') }}</span>
    <el-tag
      v-else
      type="warning"
      size="small"
    >
      {{ t('form.linkFormPlaceholderStale') }}
    </el-tag>
  </div>
</template>

<script setup lang="ts">
import { computed, inject } from 'vue'
import { useI18n } from 'vue-i18n'

interface LinkFormComponentInfo {
  id: number
  componentName: string
  linkedFormName?: string
}

const props = defineProps<{
  _componentId?: number | null
  componentId?: number | null
  linkedFormId?: number | null
  components?: LinkFormComponentInfo[]
}>()

const { t } = useI18n()
const injectedComponents = inject<() => LinkFormComponentInfo[]>('linkFormComponents', () => [])

function onWidgetClick(e: MouseEvent) {
  e.stopPropagation()
  const el = e.currentTarget as HTMLElement
  const dragTool = el.closest('._fd-drag-tool') as HTMLElement | null
  if (dragTool) {
    const clickEvent = new MouseEvent('click', { bubbles: false, cancelable: true })
    dragTool.dispatchEvent(clickEvent)
  }
}

const componentId = computed(() => props._componentId ?? props.componentId ?? null)
const effectiveComponentId = componentId

type PlaceholderState = 'unconfigured' | 'valid' | 'stale'

const state = computed((): PlaceholderState => {
  if (!componentId.value) return 'unconfigured'
  const found = linkFormComponents.value.find(c => c.id === componentId.value)
  return found ? 'valid' : 'stale'
})

const linkFormComponents = computed((): LinkFormComponentInfo[] => {
  return props.components?.length ? props.components : injectedComponents()
})

const displayName = computed(() => {
  if (state.value !== 'valid') return null
  const comp = linkFormComponents.value.find(c => c.id === componentId.value)!
  return comp.linkedFormName
    ? `${comp.componentName} → ${comp.linkedFormName}`
    : comp.componentName
})
</script>

<style scoped>
.link-form-placeholder-widget {
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

.link-form-placeholder-widget.is-valid {
  border-color: #67c23a;
  background: #f0f9eb;
  color: #67c23a;
}

.link-form-placeholder-widget.is-stale {
  border-color: #e6a23c;
  background: #fdf6ec;
  color: #e6a23c;
}

.link-form-placeholder-widget.is-unconfigured {
  border-color: #dcdfe6;
  background: #f5f7fa;
  color: #909399;
}

.component-name {
  font-weight: 500;
}

.hint-text {
  color: #909399;
  font-size: 12px;
}
</style>
