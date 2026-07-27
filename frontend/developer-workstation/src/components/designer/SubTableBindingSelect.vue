<script setup lang="ts">
import { inject, computed } from 'vue'
import { useI18n } from 'vue-i18n'
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
</script>

<template>
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
</template>

<style scoped>
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
</style>
