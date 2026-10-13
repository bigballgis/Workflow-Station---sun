<template>
  <el-dialog
    :model-value="visible"
    :title="t('subTable.batchEdit')"
    width="min(1120px, calc(100vw - 32px))"
    append-to-body
    :close-on-click-modal="false"
    @update:model-value="close"
  >
    <p class="batch-edit-dialog__hint">{{ t('subTable.batchEditDraftHint') }}</p>
    <div class="batch-edit-dialog__table">
      <el-table :data="draftRows" border size="small" max-height="420">
        <el-table-column type="selection" width="48" @selection-change="onSelectionChange" />
        <el-table-column type="index" width="58" />
        <el-table-column
          v-for="field in fields"
          :key="field.field"
          :label="field.label"
          :min-width="180"
        >
          <template #default="scope">
            <BatchFieldInput v-model="scope.row[field.field]" :field="field" />
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-divider />
    <el-form class="batch-edit-dialog__uniform" label-position="left" label-width="140px">
      <div class="batch-edit-dialog__uniform-title">{{ t('subTable.batchSetSelected') }}</div>
      <el-form-item v-for="field in fields" :key="field.field" :label="field.label">
        <el-select v-model="uniformMode[field.field]" class="batch-edit-dialog__mode">
          <el-option :label="t('subTable.batchNoChange')" value="none" />
          <el-option :label="t('subTable.batchSetValue')" value="set" />
          <el-option :label="t('subTable.batchClearValue')" value="clear" />
        </el-select>
        <BatchFieldInput
          v-if="uniformMode[field.field] === 'set'"
          v-model="uniformValues[field.field]"
          :field="field"
          class="batch-edit-dialog__uniform-value"
        />
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="close">{{ t('common.cancel') }}</el-button>
      <el-button type="primary" @click="apply">{{ t('subTable.batchApply') }}</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, defineComponent, h, reactive, ref, resolveComponent, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { extractBatchEditableFields, type SubTableBatchCapability } from '@/utils/subTableBatchCapability'
import type { BatchDraftChangeSet } from '@/utils/subTableBatchDraft'
import { applyBatchDraft } from '@/utils/subTableBatchDraft'

type BatchField = NonNullable<ReturnType<typeof extractBatchEditableFields>>[number]

const BatchFieldInput = defineComponent({
  name: 'BatchFieldInput',
  props: {
    modelValue: { type: null, default: undefined },
    field: { type: Object as () => BatchField, required: true },
  },
  emits: ['update:modelValue'],
  setup(props, { emit }) {
    const update = (value: unknown) => emit('update:modelValue', value)
    return () => {
      if (props.field.type === 'switch') return h(resolveComponent('el-switch'), { modelValue: props.modelValue, 'onUpdate:modelValue': update })
      if (props.field.type === 'inputNumber' || props.field.type === 'number') return h(resolveComponent('el-input-number'), { modelValue: props.modelValue, 'onUpdate:modelValue': update, style: 'width: 100%' })
      if (props.field.type === 'select') return h(resolveComponent('el-select'), { modelValue: props.modelValue, 'onUpdate:modelValue': update, clearable: true, style: 'width: 100%' }, () =>
        props.field.options.map(option => h(resolveComponent('el-option'), { key: String(option.value), label: option.label, value: option.value })))
      if (props.field.type === 'date' || props.field.type === 'datePicker' || props.field.type === 'datetime') return h(resolveComponent('el-date-picker'), {
        modelValue: props.modelValue,
        'onUpdate:modelValue': update,
        type: props.field.type === 'datetime' ? 'datetime' : 'date',
        valueFormat: props.field.type === 'datetime' ? 'YYYY-MM-DD HH:mm:ss' : 'YYYY-MM-DD',
        style: 'width: 100%',
      })
      return h(resolveComponent('el-input'), { modelValue: props.modelValue, 'onUpdate:modelValue': update, clearable: true })
    }
  },
})

const props = defineProps<{
  visible: boolean
  rows: Array<Record<string, unknown>>
  capability: SubTableBatchCapability
  rowIdentity: (row: Record<string, unknown>) => string | null
}>()

const emit = defineEmits<{
  (event: 'update:visible', visible: boolean): void
  (event: 'apply', rows: Array<Record<string, unknown>>, changes: BatchDraftChangeSet): void
}>()

const { t } = useI18n()
const draftRows = ref<Array<Record<string, unknown>>>([])
const uniformMode = reactive<Record<string, 'none' | 'set' | 'clear'>>({})
const uniformValues = reactive<Record<string, unknown>>({})
const selectedRowIds = ref<Set<string>>(new Set())

const fields = computed<BatchField[]>(() => extractBatchEditableFields(props.capability) ?? [])

function resetDraft() {
  draftRows.value = props.rows.map(row => ({ ...row }))
  selectedRowIds.value = new Set()
  for (const key of Object.keys(uniformMode)) delete uniformMode[key]
  for (const key of Object.keys(uniformValues)) delete uniformValues[key]
  for (const field of fields.value) uniformMode[field.field] = 'none'
}

watch(() => [props.visible, props.rows, fields.value] as const, ([visible]) => {
  if (visible) resetDraft()
}, { immediate: true, deep: true })

function close() {
  emit('update:visible', false)
}

function onSelectionChange(selected: Array<Record<string, unknown>>) {
  selectedRowIds.value = new Set(selected
    .map(row => props.rowIdentity(row))
    .filter((value): value is string => !!value))
}

function apply() {
  const rowPatches = draftRows.value.flatMap((row, index) => {
    const baseline = props.rows[index]
    const rowId = props.rowIdentity(baseline)
    if (!baseline || !rowId) return []
    const changed = Object.fromEntries(fields.value
      .filter(field => !Object.is(row[field.field], baseline[field.field]))
      .map(field => [field.field, { kind: 'set' as const, value: row[field.field] }]))
    return Object.keys(changed).length ? [{ rowId, fields: changed }] : []
  })
  const rowIds = [...selectedRowIds.value]
  const uniformFields = Object.fromEntries(fields.value.flatMap((field) => {
    const mode = uniformMode[field.field] ?? 'none'
    if (mode === 'none') return []
    return [[field.field, mode === 'clear' ? { kind: 'clear' as const } : { kind: 'set' as const, value: uniformValues[field.field] }]]
  }))
  const changes: BatchDraftChangeSet = {
    ...(rowPatches.length ? { rowPatches } : {}),
    ...(rowIds.length && Object.keys(uniformFields).length ? { uniformPatches: [{ rowIds, fields: uniformFields }] } : {}),
  }
  const result = applyBatchDraft(props.rows, (row) => props.rowIdentity(row) ?? '', changes)
  emit('apply', result, changes)
  close()
}
</script>

<style scoped>
.batch-edit-dialog__hint { margin-top: 0; color: var(--el-text-color-secondary); }
.batch-edit-dialog__table { overflow-x: auto; }
.batch-edit-dialog__uniform { max-width: 760px; }
.batch-edit-dialog__uniform-title { margin-bottom: 12px; font-weight: 600; }
.batch-edit-dialog__mode { width: 145px; margin-right: 12px; }
.batch-edit-dialog__uniform-value { width: min(420px, 100%); vertical-align: middle; }
@media (max-width: 640px) {
  .batch-edit-dialog__mode { width: 100%; margin: 0 0 8px; }
  .batch-edit-dialog__uniform-value { width: 100%; }
}
</style>
