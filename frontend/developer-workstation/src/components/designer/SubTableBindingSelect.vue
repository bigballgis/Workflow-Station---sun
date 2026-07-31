<script setup lang="ts">
import { inject, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { CopyDocument } from '@element-plus/icons-vue'
import { normalizeBindingId } from '@/utils/bindingDisplayHelpers'
import { lookupStore } from './lookupStore'

interface DesignerSubBinding {
  id: number
  tableName: string
  tableDisplayName?: string
  tableDescription: string
  bindingType: string
}

interface SubTableBindingSelectProps {
  modelValue: number | null
  subBindings?: DesignerSubBinding[]
}

interface SubTableBindingSelectEmits {
  'update:modelValue': [val: number | null]
}

const props = defineProps<SubTableBindingSelectProps>()
const emit = defineEmits<SubTableBindingSelectEmits>()
const { t } = useI18n()

// Get subBindings from inject (provided by FormDesigner) or fall back to prop
const injectedSubBindings = inject<() => DesignerSubBinding[]>('designerSubBindings', () => [])
const allSubBindings = computed(() => props.subBindings?.length ? props.subBindings : injectedSubBindings())

// Only show SUB type bindings for sub-table widget binding selection
const subBindings = computed(() => allSubBindings.value.filter(b => b.bindingType === 'SUB'))
const normalizedModelValue = computed(() => normalizeBindingId(props.modelValue))
const scriptHideKey = computed(() => {
  const id = normalizedModelValue.value
  return id == null ? null : `__subTable_${id}`
})
const isStaleSelection = computed(() => {
  const id = normalizedModelValue.value
  if (id == null) return false
  return !subBindings.value.some((b) => b.id === id)
})

// Use module-level store instead of inject — fc-designer registers this component in its own
// Vue app context, so provide/inject from FormDesigner doesn't reach here.
function goToDesigner() {
  if (normalizedModelValue.value != null) lookupStore.switchToBinding?.(normalizedModelValue.value)
}

function handleChange(val: number | null) {
  emit('update:modelValue', val ?? null)
}

async function copyText(text: string): Promise<void> {
  try {
    await navigator.clipboard.writeText(text)
    ElMessage.success(t('form.subTableBindingIdCopied'))
  } catch {
    ElMessage.error(t('form.subTableBindingIdCopyFailed'))
  }
}
</script>

<template>
  <div class="sub-table-binding-select">
    <el-select
      :model-value="normalizedModelValue"
      clearable
      :placeholder="t('form.subTableSelectPlaceholder')"
      @change="handleChange($event ?? null)"
    >
      <el-option
        v-for="b in subBindings"
        :key="b.id"
        :value="b.id"
        :label="b.tableDescription
          ? `${b.tableDisplayName || b.tableName}（${b.tableDescription}）`
          : (b.tableDisplayName || b.tableName)"
      />
      <template
        v-if="subBindings.length === 0"
        #empty
      >
        <span class="el-select-dropdown__empty">{{ t('form.subTableSelectEmpty') }}</span>
      </template>
    </el-select>
    <el-tag
      v-if="isStaleSelection"
      type="warning"
      size="small"
      class="stale-binding-tag"
    >
      {{ t('form.subTablePlaceholderStale') }}
    </el-tag>
    <a
      v-if="normalizedModelValue && lookupStore.switchToBinding && !isStaleSelection"
      class="binding-nav-link"
      href="#"
      @click.prevent="goToDesigner"
    >{{ t('form.subTableGoToDesigner') }}</a>
    <div
      v-if="normalizedModelValue != null && scriptHideKey"
      class="binding-id-panel"
    >
      <div class="binding-id-row">
        <span class="binding-id-label">{{ t('form.subTableBindingIdLabel') }}</span>
        <code class="binding-id-value">{{ normalizedModelValue }}</code>
        <el-button
          link
          type="primary"
          size="small"
          :aria-label="t('form.subTableBindingIdCopy')"
          @click="copyText(String(normalizedModelValue))"
        >
          <el-icon><CopyDocument /></el-icon>
        </el-button>
      </div>
      <div class="binding-id-row">
        <span class="binding-id-label">{{ t('form.subTableScriptHideKeyLabel') }}</span>
        <code class="binding-id-value">{{ scriptHideKey }}</code>
        <el-button
          link
          type="primary"
          size="small"
          :aria-label="t('form.subTableBindingIdCopy')"
          @click="copyText(scriptHideKey)"
        >
          <el-icon><CopyDocument /></el-icon>
        </el-button>
      </div>
      <p class="binding-id-hint">{{ t('form.subTableScriptHideKeyHint') }}</p>
    </div>
  </div>
</template>

<style scoped>
.sub-table-binding-select {
  width: 100%;
}
.stale-binding-tag {
  display: inline-block;
  margin-top: 4px;
  margin-right: 8px;
}
.binding-nav-link {
  display: inline-block;
  margin-top: 4px;
  font-size: 12px;
  color: var(--el-color-primary);
  text-decoration: none;
}
.binding-nav-link:hover {
  text-decoration: underline;
}
.binding-id-panel {
  margin-top: 8px;
  padding: 8px 10px;
  border-radius: 4px;
  background: var(--el-fill-color-light);
  border: 1px solid var(--el-border-color-lighter);
}
.binding-id-row {
  display: flex;
  align-items: center;
  gap: 6px;
  min-height: 24px;
  font-size: 12px;
  line-height: 1.4;
}
.binding-id-row + .binding-id-row {
  margin-top: 4px;
}
.binding-id-label {
  flex: 0 0 auto;
  color: var(--el-text-color-secondary);
  white-space: nowrap;
}
.binding-id-value {
  flex: 1 1 auto;
  min-width: 0;
  padding: 1px 6px;
  border-radius: 3px;
  background: var(--el-bg-color);
  color: var(--el-text-color-primary);
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 12px;
  word-break: break-all;
}
.binding-id-hint {
  margin: 6px 0 0;
  font-size: 11px;
  line-height: 1.4;
  color: var(--el-text-color-secondary);
}
</style>
