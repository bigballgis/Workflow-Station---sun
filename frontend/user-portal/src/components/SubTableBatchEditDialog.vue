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
    <el-form v-if="showAdd" label-width="auto" class="batch-edit-dialog__add">
      <el-form-item v-if="parentSelection && parentSelection.options.length > 1" :label="t('subTable.relatedParent', { tableName: parentSelection.parentTableName })" required>
        <el-select v-model="selectedParentValue">
          <el-option v-for="parent in parentSelection.options" :key="parent.value" :value="parent.value" :label="parent.label" />
        </el-select>
      </el-form-item>
      <el-form-item :label="t('subTable.batchAddCount')">
        <el-input-number v-model="addCount" :min="1" :max="200" :precision="0" />
        <el-button :disabled="applying || freshCount + addCount > 200" @click="addDraftRows">{{ t('subTable.add') }}</el-button>
      </el-form-item>
    </el-form>
    <div class="batch-edit-dialog__table">
      <el-table :data="draftRows.slice((page - 1) * 20, page * 20)" border size="small" max-height="420" @selection-change="onSelectionChange">
        <el-table-column type="selection" width="48" />
        <el-table-column type="index" width="58" />
        <el-table-column
          v-for="field in fields"
          :key="field.field"
          :label="field.label"
          :min-width="180"
        >
          <template #default="scope">
            <BatchFieldInput v-model="scope.row[field.field]" :field="field" :disabled="!editableRow(scope.row, field.field)" />
          </template>
        </el-table-column>
      </el-table>
      <el-pagination v-model:current-page="page" :page-size="20" :total="draftRows.length" layout="prev, pager, next" />
    </div>

    <el-divider />
    <el-form class="batch-edit-dialog__uniform" label-position="left" label-width="auto">
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
      <el-button v-if="showAssign" @click="openAssign">{{ t('subTable.batchAssign') }}</el-button>
      <el-button v-if="showDelete" type="danger" plain :disabled="!selectedRowIds.size" @click="deleteSelected">{{ t('subTable.delete') }}</el-button>
      <el-button type="primary" :loading="applying" :disabled="!draftRows.length && !deletedRowIds.length" @click="apply">{{ t('subTable.batchApply') }}</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, defineComponent, h, reactive, ref, resolveComponent, toRaw, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { BindingFieldDefinition, BindingParentSelection } from '@/utils/subTableRowRuntime'
import { pkNeedsAllocation } from '@/utils/subTableRowRuntime/pkPredicates'
import type { PreparedBatchRows } from '@/utils/subTableBatchAdd'
import { resolveUserFacingHttpMessage } from '@/utils/httpErrorMessage'
import { firstBatchFieldError } from '@/utils/subTableBatchValidation'
import { extractBatchEditableFields, type SubTableBatchCapability } from '@/utils/subTableBatchCapability'
import type { BatchDraftChangeSet } from '@/utils/subTableBatchDraft'
import { applyBatchDraft } from '@/utils/subTableBatchDraft'

type BatchField = NonNullable<ReturnType<typeof extractBatchEditableFields>>[number]

const BatchFieldInput = defineComponent({
  name: 'BatchFieldInput',
  props: {
    modelValue: { type: null, default: undefined },
    field: { type: Object as () => BatchField, required: true },
    disabled: Boolean,
  },
  emits: ['update:modelValue'],
  setup(props, { emit }) {
    const update = (value: unknown) => emit('update:modelValue', value)
    return () => {
      if (props.disabled) return h('span', String(props.modelValue ?? ''))
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

const props = withDefaults(defineProps<{
  visible: boolean
  rows: Array<Record<string, unknown>>
  capability: SubTableBatchCapability
  rowIdentity: (row: Record<string, unknown>) => string | null
  showAssign?: boolean
  showDelete?: boolean
  showAdd?: boolean
  allowEdit?: boolean
  fieldDefinitions?: BindingFieldDefinition[]
  parentSelection?: BindingParentSelection | null
  prepareRows?: (rows: Array<Record<string, unknown>>, parentValue?: string) => Promise<PreparedBatchRows>
}>(), { allowEdit: true })

const emit = defineEmits<{
  (event: 'update:visible', visible: boolean): void
  (event: 'apply', rows: Array<Record<string, unknown>>, changes: BatchDraftChangeSet): void
  (event: 'open-assign'): void
  (event: 'open-delete'): void
  (event: 'apply-workspace', rows: Array<Record<string, unknown>>, changes: BatchDraftChangeSet, prepared?: PreparedBatchRows): void
}>()

const { t } = useI18n()
const draftRows = ref<Array<Record<string, unknown>>>([])
const uniformMode = reactive<Record<string, 'none' | 'set' | 'clear'>>({})
const uniformValues = reactive<Record<string, unknown>>({})
const selectedRowIds = ref<Set<string>>(new Set())
const selectedParentValue = ref('')
const addCount = ref(1)
const page = ref(1)
const applying = ref(false)
const baselineRows = ref<Array<Record<string, unknown>>>([])
let temporaryKeys = new WeakMap<Record<string, unknown>, string>()
const deletedRowIds = ref<string[]>([])
const freshCount = computed(() => draftRows.value.filter(row => temporaryKeys.has(toRaw(row))).length)

const fields = computed<BatchField[]>(() => extractBatchEditableFields(props.capability) ?? [])

function resetDraft() {
  baselineRows.value = JSON.parse(JSON.stringify(props.rows))
  draftRows.value = JSON.parse(JSON.stringify(props.rows))
  selectedParentValue.value = ''
  page.value = 1
  temporaryKeys = new WeakMap()
  deletedRowIds.value = []
  selectedRowIds.value = new Set()
  for (const key of Object.keys(uniformMode)) delete uniformMode[key]
  for (const key of Object.keys(uniformValues)) delete uniformValues[key]
  for (const field of fields.value) uniformMode[field.field] = 'none'
}

watch(() => props.visible, visible => {
  if (visible) resetDraft()
}, { immediate: true })

async function close() {
  if (applying.value) return false
  if (JSON.stringify(draftRows.value) !== JSON.stringify(baselineRows.value)
    || Object.values(uniformMode).some(mode => mode !== 'none')) {
    try { await ElMessageBox.confirm(t('subTable.batchDiscardConfirm'), t('common.cancel'), { type: 'warning' }) } catch { return false }
  }
  emit('update:visible', false)
  return true
}

async function openAssign(): Promise<void> {
  if (await close()) emit('open-assign')
}
function draftIdentity(row: Record<string, unknown>): string | null {
  return temporaryKeys.get(toRaw(row)) ?? props.rowIdentity(row)
}

function editableRow(row: Record<string, unknown>, fieldName: string): boolean {
  const fresh = temporaryKeys.has(toRaw(row))
  if (!fresh && props.allowEdit === false) return false
  const definition = props.fieldDefinitions?.find(field => field.fieldName === fieldName)
  return !definition?.isComputed && !definition?.isForeignKey && !(definition?.isPrimaryKey && (!fresh || pkNeedsAllocation(definition)))
}

async function deleteSelected(): Promise<void> {
  try { await ElMessageBox.confirm(t('subTable.batchDeleteConfirm', { count: selectedRowIds.value.size }), t('subTable.deleteConfirm'), { type: 'warning' }) }
  catch { return }
  const next: Array<Record<string, unknown>> = []
  for (const row of draftRows.value) {
    const key = draftIdentity(row)
    if (!key || !selectedRowIds.value.has(key)) { next.push(row); continue }
    if (!temporaryKeys.has(toRaw(row))) deletedRowIds.value.push(key)
  }
  draftRows.value = next
  selectedRowIds.value = new Set()
}

function addDraftRows() {
  if (freshCount.value + addCount.value > 200) { ElMessage.error(t('subTable.batchLimit')); return }
  for (let index = 0; index < addCount.value; index++) {
    const row = Object.fromEntries(fields.value.map(field => {
      const metadata = props.capability.rowDesign?.fields.find(item => item.fieldName === field.field)
      return [field.field, field.defaultValue ?? metadata?.defaultValue ?? null]
    }))
    temporaryKeys.set(row, `draft:${crypto.randomUUID()}`)
    draftRows.value.push(row)
  }
  page.value = Math.ceil(draftRows.value.length / 20)
}

function onSelectionChange(selected: Array<Record<string, unknown>>) {
  selectedRowIds.value = new Set(selected
    .map(row => draftIdentity(row))
    .filter((value): value is string => !!value))
}

function collectChanges(): BatchDraftChangeSet {
  const baselineById = new Map(baselineRows.value.map(row => [props.rowIdentity(row), row]))
  const rowPatches = draftRows.value.flatMap(row => {
    if (temporaryKeys.has(toRaw(row))) return []
    const rowId = props.rowIdentity(row)
    const baseline = rowId ? baselineById.get(rowId) : null
    if (!baseline || !rowId) return []
    const changed = Object.fromEntries(fields.value
      .filter(field => editableRow(row, field.field) && !Object.is(row[field.field], baseline[field.field]))
      .map(field => [field.field, { kind: 'set' as const, value: row[field.field] }]))
    return Object.keys(changed).length ? [{ rowId, fields: changed }] : []
  })
  const rowIds = [...selectedRowIds.value].filter(id => baselineById.has(id))
  const uniformFields = Object.fromEntries(fields.value.flatMap((field) => {
    if (props.allowEdit === false || props.fieldDefinitions?.some(definition => definition.fieldName === field.field && (definition.isPrimaryKey || definition.isForeignKey || definition.isComputed))) return []
    const mode = uniformMode[field.field] ?? 'none'
    if (mode === 'none') return []
    return [[field.field, mode === 'clear' ? { kind: 'clear' as const } : { kind: 'set' as const, value: uniformValues[field.field] }]]
  }))
  return {
    ...(rowPatches.length ? { rowPatches } : {}),
    ...(rowIds.length && Object.keys(uniformFields).length ? { uniformPatches: [{ rowIds, fields: uniformFields }] } : {}),
    ...(deletedRowIds.value.length ? { deletedRowIds: [...deletedRowIds.value] } : {}),
  }
}

async function apply() {
  if (applying.value) return
  if (JSON.stringify(props.rows) !== JSON.stringify(baselineRows.value)) { ElMessage.error(t('subTable.batchStaleDraft')); return }
  const fresh = freshRows()
  const changes = collectChanges()
  const existing = applyBatchDraft(props.rows, row => props.rowIdentity(row) ?? '', changes)
  const changedIds = new Set([...(changes.rowPatches ?? []).map(patch => patch.rowId), ...(changes.uniformPatches ?? []).flatMap(patch => patch.rowIds)])
  const affectedIds = new Set([...changedIds, ...(changes.deletedRowIds ?? [])])
  if (affectedIds.size + fresh.length > 200) { ElMessage.error(t('subTable.batchLimit')); return }
  const affectedExisting = existing.filter(row => changedIds.has(props.rowIdentity(row) ?? ''))
  const editableFields = fields.value.filter(field => !props.fieldDefinitions?.some(definition => definition.fieldName === field.field && (definition.isPrimaryKey || definition.isForeignKey || definition.isComputed)))
  const addedFields = fields.value.filter(field => !props.fieldDefinitions?.some(definition => definition.fieldName === field.field && (definition.isForeignKey || definition.isComputed || (definition.isPrimaryKey && pkNeedsAllocation(definition)))))
  const requiredAddedFields = addedFields.map(field => ({ ...field, required: field.required
    || props.capability.rowDesign?.fields.some(item => item.fieldName === field.field && item.nullable === false) === true }))
  const error = firstBatchFieldError(affectedExisting, editableFields) ?? firstBatchFieldError(fresh, requiredAddedFields)
  if (error) { page.value = Math.ceil(error.row / 20); ElMessage.error(t('subTable.batchInvalidField', { row: error.row, label: error.label })); return }
  applying.value = true
  try {
    const prepared = fresh.length ? await props.prepareRows?.(fresh, selectedParentValue.value) : undefined
    if (fresh.length && !prepared) throw new Error(t('common.operationFailed'))
    if (JSON.stringify(props.rows) !== JSON.stringify(baselineRows.value)) throw new Error(t('subTable.batchStaleDraft'))
    if (prepared) validatePreparedIdentities(prepared)
    if (prepared || deletedRowIds.value.length) emit('apply-workspace', [...existing, ...(prepared?.rows ?? [])], changes, prepared)
    else emit('apply', existing, changes)
    emit('update:visible', false)
  } catch (error) { ElMessage.error(resolveUserFacingHttpMessage(error, t)) }
  finally { applying.value = false }
}

function validatePreparedIdentities(prepared: PreparedBatchRows): void {
  const known = new Set(props.rows.map(props.rowIdentity))
  for (const row of prepared.rows) {
    const key = props.rowIdentity(row)
    if (!key || known.has(key)) throw new Error(t('subTable.batchDuplicateIdentity'))
    known.add(key)
  }
}

function freshRows(): Array<Record<string, unknown>> {
  return draftRows.value.filter(row => temporaryKeys.has(toRaw(row))).map(row => {
    const result = { ...row }
    if (!selectedRowIds.value.has(draftIdentity(row) ?? '')) return result
    for (const field of fields.value) {
      if (!editableRow(row, field.field) || props.fieldDefinitions?.some(definition => definition.fieldName === field.field && definition.isPrimaryKey)) continue
      const mode = uniformMode[field.field]
      if (mode === 'set') result[field.field] = uniformValues[field.field]
      else if (mode === 'clear') result[field.field] = null
    }
    return result
  })
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
