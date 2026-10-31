<template>
  <el-dialog class="batch-edit-dialog" top="24px" :model-value="visible" :title="t(mode === 'add' ? 'subTable.batchAdd' : 'subTable.batchEdit')"
    width="min(960px, calc(100vw - 32px))" append-to-body :close-on-click-modal="false" :before-close="beforeClose">
    <template #header><div class="batch-edit-dialog__title"><span class="el-dialog__title">{{ t(mode === 'add' ? 'subTable.batchAdd' : 'subTable.batchEdit') }}</span><slot name="help" /></div></template>
    <el-alert v-if="unsupportedControl" :title="t('subTable.batchUnsupportedControl', { type: unsupportedControl })" type="error" :closable="false" />
    <template v-else>
    <el-form v-if="mode === 'add'" label-width="auto">
      <template v-if="mode === 'add'">
        <el-form-item v-if="parentSelection && parentSelection.options.length > 1" :label="t('subTable.relatedParent', { tableName: parentSelection.parentTableName })" required>
          <el-select v-model="selectedParentValue"><el-option v-for="parent in parentSelection.options" :key="parent.value" :value="parent.value" :label="parent.label" /></el-select>
        </el-form-item>
        <el-form-item :label="t('subTable.batchAddCount')"><el-input-number v-model="addCount" :min="1" :max="200" :precision="0" /></el-form-item>
      </template>
    </el-form>
    <BatchDesignedForm v-if="visible" :rules="rules" :options="options" :readonly-field="readonly"
      :initial-values="mode === 'add' ? initialValues : undefined" @change="setValue">
      <template #assignment="{ rule }"><slot v-if="showAssign" name="assignment" :rule="rule" :rows="assignmentRows" :disabled="options?.form?.disabled === true || rule.props?.disabled === true || rule.props?.readonly === true" /></template>
    </BatchDesignedForm>
    </template>
    <template #footer><el-button @click="close">{{ t('common.cancel') }}</el-button><el-button type="primary" :loading="applying" :disabled="!!unsupportedControl || (mode !== 'add' && !targetIds.length)" @click="apply">{{ t('subTable.batchApply') }}</el-button></template>
  </el-dialog>
</template>
<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { BindingParentSelection } from '../tableFkRuntime'
import type { PreparedBatchRows, BatchWorkspaceField, BatchAssignmentCollection, SubTableBatchChange } from './workspaceTypes'
import { firstBatchFieldError } from './validation'
import type { BatchEditableField } from './fields'
import { applyBatchDraft, type BatchDraftChangeSet, type BatchFieldIntent } from './draft'
import { batchRuleChildren, unsupportedBatchControl } from './batchFormRules'
import BatchDesignedForm from './BatchDesignedForm.vue'
import type { BatchRowEventRunner } from './rowEvents'
const props = withDefaults(defineProps<{
  visible: boolean; rows: Array<Record<string, unknown>>; fields: BatchEditableField[]
  rules: Array<Record<string, any>>; options?: Record<string, any>; selectedRowIds?: string[]; mode?: 'edit' | 'add'
  fieldWriteModes?: Record<string, 'EDITABLE' | 'READONLY'>
  defaultValueForField: (field: BatchEditableField) => unknown; formatError: (error: unknown) => string
  rowIdentity: (row: Record<string, unknown>) => string | null
  showAssign?: boolean; collectAssignment?: (rows: Array<Record<string, unknown>>) => BatchAssignmentCollection
  assignmentHasChanges?: () => boolean; allowEdit?: boolean; fieldDefinitions?: BatchWorkspaceField[]
  parentSelection?: BindingParentSelection | null
  prepareRows?: (rows: Array<Record<string, unknown>>, parentValue?: string) => Promise<PreparedBatchRows>
  runRowEvents?: BatchRowEventRunner
}>(), { allowEdit: true, mode: 'edit' })
const emit = defineEmits<{
  (event: 'update:visible', visible: boolean): void
  (event: 'apply', rows: Array<Record<string, unknown>>, changes: BatchDraftChangeSet): void
  (event: 'apply-workspace', rows: Array<Record<string, unknown>>, changes: BatchDraftChangeSet, prepared?: PreparedBatchRows, assignment?: SubTableBatchChange): void
}>()
const { t } = useI18n()
const values = reactive<Record<string, any>>({})
const initialValues = ref<Record<string, unknown>>({})
const intents = reactive<Record<string, BatchFieldIntent>>({})
const baselineRows = ref<Array<Record<string, unknown>>>([])
const targetIds = ref<string[]>([])
const selectedParentValue = ref('')
const addCount = ref(1)
const applying = ref(false)
const fieldMap = computed(() => new Map(props.fields.map(field => [field.field, field])))
const unsupportedControl = computed(() => unsupportedBatchControl(props.rules, new Set(fieldMap.value.keys())))
const ruleMap = computed(() => {
  const map = new Map<string, Record<string, any>>()
  function visit(rules: Array<Record<string, any>>, inheritedReadonly = false) {
    for (const rule of rules) {
      const locked = inheritedReadonly || rule.props?.disabled === true || rule.props?.readonly === true
      if (rule.field) map.set(rule.field, { ...rule, props: { ...rule.props, disabled: locked } })
      visit(batchRuleChildren(rule), locked)
      for (const control of Array.isArray(rule.control) ? rule.control : []) {
        if (Array.isArray(control.rule)) visit(control.rule, locked)
      }
    }
  }
  visit(props.rules)
  return map
})
const definitions = computed(() => new Map((props.fieldDefinitions ?? []).map(field => [field.fieldName, field])))
const assignmentRows = computed(() => baselineRows.value.filter(row => targetIds.value.includes(props.rowIdentity(row)!)))

function readonly(name: string): boolean {
  const def = definitions.value.get(name)
  const rule = ruleMap.value.get(name)
  return props.options?.form?.disabled === true || rule?.props?.disabled === true || rule?.props?.readonly === true || (props.mode === 'edit' && !props.allowEdit) || props.fieldWriteModes?.[name] === 'READONLY'
    || !!def?.isComputed || !!def?.isForeignKey || !!(def?.isPrimaryKey && (props.mode === 'edit' || def.requiresAllocation))
}
function setValue(name: string, value: unknown) {
  if (readonly(name)) return
  values[name] = value
  intents[name] = value == null || value === '' ? { kind: 'clear' } : { kind: 'set', value }
}
watch(() => props.visible, visible => {
  if (!visible) return
  baselineRows.value = JSON.parse(JSON.stringify(props.rows))
  targetIds.value = [...new Set(props.selectedRowIds ?? [])]
  for (const name of Object.keys(values)) delete values[name]
  for (const name of Object.keys(intents)) delete intents[name]
  if (props.mode === 'add') for (const field of props.fields) if (!readonly(field.field)) values[field.field] = props.defaultValueForField(field)
  initialValues.value = { ...values }
  selectedParentValue.value = ''
  addCount.value = 1
}, { immediate: true })
async function close() {
  if (applying.value) return false
  if (Object.keys(intents).length || props.assignmentHasChanges?.()) {
    try { await ElMessageBox.confirm(t('subTable.batchDiscardConfirm'), t('common.cancel'), { type: 'warning' }) } catch { return false }
  }
  emit('update:visible', false)
  return true
}
async function beforeClose(done: () => void) { if (await close()) done() }
function assertFresh() {
  const current = new Map(props.rows.map(row => [props.rowIdentity(row), row]))
  for (const row of assignmentRows.value) if (JSON.stringify(current.get(props.rowIdentity(row))) !== JSON.stringify(row)) throw new Error(t('subTable.batchStaleDraft'))
  if (targetIds.value.length !== assignmentRows.value.length) throw new Error(t('subTable.batchStaleDraft'))
}
async function apply() {
  if (applying.value) return
  applying.value = true
  try {
    if (unsupportedControl.value) throw new Error(t('subTable.batchUnsupportedControl', { type: unsupportedControl.value }))
    assertFresh()
    if ((props.mode === 'edit' && (!targetIds.value.length || targetIds.value.length > 200))
      || (props.mode === 'add' && (!Number.isInteger(addCount.value) || addCount.value < 1 || addCount.value > 200))) throw new Error(t('subTable.batchLimit'))
    const fields = props.fields.filter(field => !readonly(field.field))
    const patch = Object.fromEntries(fields.filter(field => intents[field.field]).map(field => [field.field, intents[field.field]!]))
    const changes: BatchDraftChangeSet = Object.keys(patch).length && props.mode === 'edit' ? { uniformPatches: [{ rowIds: [...targetIds.value], fields: patch }] } : {}
    let next = applyBatchDraft(props.rows, row => props.rowIdentity(row)!, changes)
    const touched = new Set(Object.keys(patch))
    let assignment: SubTableBatchChange | undefined
    if (props.showAssign && props.assignmentHasChanges?.()) {
      if (!props.collectAssignment) throw new Error(t('common.operationFailed'))
      const collected = props.collectAssignment(next.filter(row => targetIds.value.includes(props.rowIdentity(row)!)))
      const byId = new Map(collected.rows.map(row => [props.rowIdentity(row), row]))
      next = next.map(row => byId.get(props.rowIdentity(row)) ?? row)
      assignment = collected.change
      collected.editedFields?.forEach(field => touched.add(field))
    }
    next = applyEvents(next, touched, changes)
    let prepared: PreparedBatchRows | undefined
    if (props.mode === 'add') {
      prepared = await prepareAdded(fields, touched)
      next = [...props.rows, ...prepared.rows]
    } else if (touched.size) {
      const error = firstBatchFieldError(next.filter(row => targetIds.value.includes(props.rowIdentity(row)!)), fields)
      if (error) throw new Error(t('subTable.batchInvalidField', { ...error }))
    }
    assertFresh()
    if (prepared || assignment) emit('apply-workspace', next, changes, prepared, assignment)
    else if (Object.keys(patch).length || changes.rowPatches?.length) emit('apply', next, changes)
    emit('update:visible', false)
  } catch (error) { ElMessage.error(props.formatError(error)) }
  finally { applying.value = false }
}
function applyEvents(rows: Array<Record<string, unknown>>, touched: Set<string>, changes: BatchDraftChangeSet) {
  if (props.mode !== 'edit' || !touched.size || !props.runRowEvents) return rows
  const baseline = new Map(baselineRows.value.map(row => [props.rowIdentity(row), row]))
  const next = rows.map(row => targetIds.value.includes(props.rowIdentity(row)!)
    ? props.runRowEvents!(row, props.rules, [...touched], readonly) : row)
  changes.rowPatches = next.filter(row => targetIds.value.includes(props.rowIdentity(row)!)).flatMap(row => {
    const before = baseline.get(props.rowIdentity(row))!
    const fields = Object.fromEntries(props.fields.filter(field => !readonly(field.field)
      && JSON.stringify(before[field.field]) !== JSON.stringify(row[field.field]))
      .map(field => [field.field, { kind: 'set' as const, value: row[field.field] }]))
    return Object.keys(fields).length ? [{ rowId: props.rowIdentity(row)!, fields }] : []
  })
  delete changes.uniformPatches
  return next
}
async function prepareAdded(fields: BatchEditableField[], touched: Set<string>): Promise<PreparedBatchRows> {
  const row = Object.fromEntries(fields.map(field => [field.field, intents[field.field]?.kind === 'clear' ? null : values[field.field]]))
  const fresh = Array.from({ length: addCount.value }, () => props.runRowEvents
    ? props.runRowEvents(row, props.rules, [...touched], readonly) : JSON.parse(JSON.stringify(row)))
  const error = firstBatchFieldError(fresh, fields.map(field => ({ ...field, required: field.required || definitions.value.get(field.field)?.nullable === false })))
  if (error) throw new Error(t('subTable.batchInvalidField', { ...error }))
  if (!props.prepareRows) throw new Error(t('common.operationFailed'))
  const prepared = await props.prepareRows(fresh, selectedParentValue.value)
  const known = new Set(props.rows.map(props.rowIdentity))
  for (const added of prepared.rows) {
    const key = props.rowIdentity(added)
    if (!key || known.has(key)) throw new Error(t('subTable.batchDuplicateIdentity'))
    known.add(key)
  }
  if (prepared.rows.length !== fresh.length) throw new Error(t('common.operationFailed'))
  return prepared
}
</script>
<style scoped>
:global(.el-dialog.batch-edit-dialog) { display:flex; flex-direction:column; max-height:calc(100dvh - 48px); margin-bottom:24px; }
:global(.el-dialog.batch-edit-dialog .el-dialog__body) { overflow:auto; min-height:0; }
.batch-edit-dialog__title { display:flex; align-items:center; gap:8px; }
</style>
