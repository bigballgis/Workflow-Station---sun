<template>
  <el-dialog class="batch-edit-dialog" top="24px" :model-value="visible" :title="t(mode === 'add' ? 'subTable.batchAdd' : 'subTable.batchEdit')"
    width="min(960px, calc(100vw - 32px))" append-to-body :close-on-click-modal="false" :before-close="beforeClose">
    <template #header><div class="batch-edit-dialog__title"><span class="el-dialog__title">{{ t(mode === 'add' ? 'subTable.batchAdd' : 'subTable.batchEdit') }}</span><slot name="help" /></div></template>
    <el-alert v-if="unsupportedControl" :title="t('subTable.batchUnsupportedControl', { type: unsupportedControl })" type="error" :closable="false" />
    <el-alert v-if="eventError || initializationError" :title="initializationError || eventError" type="error" :closable="false" />
    <template v-if="!unsupportedControl">
    <el-form v-if="mode === 'add'" label-width="auto">
      <template v-if="mode === 'add'">
        <el-form-item v-if="parentSelection && parentSelection.options.length > 1" :label="t('subTable.relatedParent', { tableName: parentSelection.parentTableName })" required>
          <el-select v-model="selectedParentValue"><el-option v-for="parent in parentSelection.options" :key="parent.value" :value="parent.value" :label="parent.label" /></el-select>
        </el-form-item>
        <el-form-item :label="t('subTable.batchAddCount')"><el-input-number v-model="addCount" :min="1" :max="200" :precision="0" /></el-form-item>
      </template>
    </el-form>
    <el-select v-if="eventSessions.length > 1" v-model="previewIndex" :aria-label="t('subTable.batchPreviewRow')" class="batch-preview-row">
      <el-option v-for="(_session, index) in eventSessions" :key="index" :value="index" :label="t('subTable.batchPreviewRowLabel', { row: index + 1 })" />
    </el-select>
    <el-alert v-for="notice in currentNotices" :key="notice.uniqueId" :title="notice.message" :type="notice.level === 'ERROR' ? 'error' : notice.level === 'WARNING' ? 'warning' : 'info'" :closable="false" />
    <BatchDesignedForm v-if="visible" :rules="rules" :options="options" :readonly-field="readonly" :event-context="sharedEventContext"
      :initial-values="mode === 'add' ? initialValues : undefined" @change="setValue">
      <template #assignment="{ rule }"><slot v-if="showAssign" name="assignment" :rule="rule" :rows="assignmentRows" :event-context="sharedEventContext" :disabled="options?.form?.disabled === true || rule.props?.disabled === true || rule.props?.readonly === true" /></template>
    </BatchDesignedForm>
    </template>
    <template #footer><el-button @click="close">{{ t('common.cancel') }}</el-button><el-button type="primary" :loading="applying" :disabled="!!unsupportedControl || (mode !== 'add' && !targetIds.length)" @click="apply">{{ t('subTable.batchApply') }}</el-button></template>
  </el-dialog>
</template>
<script setup lang="ts">
import { computed, reactive, ref, shallowRef, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { BindingParentSelection } from '../tableFkRuntime'
import type { PreparedBatchRows, BatchWorkspaceField, BatchAssignmentCollection, SubTableBatchChange } from './workspaceTypes'
import { firstBatchFieldError } from './validation'
import type { BatchEditableField } from './fields'
import { applyBatchDraft, type BatchDraftChangeSet, type BatchFieldIntent } from './draft'
import { batchRuleChildren, unsupportedBatchControl } from './batchFormRules'
import BatchDesignedForm from './BatchDesignedForm.vue'
import type { BatchEventSession, BatchRowEventRunner } from './rowEvents'
import { assignmentControlRules } from './assignmentControlRules'
import type { BatchAssignmentConfig } from './workspaceTypes'
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
  runRowEvents?: BatchRowEventRunner; assignmentConfig?: BatchAssignmentConfig
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
const eventSessions = shallowRef<BatchEventSession[]>([])
const eventTick = ref(0)
const eventError = ref('')
const initializationError = ref('')
const previewIndex = ref(0)
const activeSession = computed(() => { void eventTick.value; return eventSessions.value[previewIndex.value] })
const assignmentFields = computed(() => [props.assignmentConfig?.assigneeField, props.assignmentConfig?.buField, props.assignmentConfig?.roleField].filter((field): field is string => !!field))
const eventRules = computed(() => {
  function visit(rules: Array<Record<string, any>>): Array<Record<string, any>> {
    return rules.map(rule => ({ ...rule, _batchAssignmentOwned: assignmentFields.value.includes(rule.field), children: rule.type === 'miAssignment' && props.assignmentConfig
      ? visit(assignmentControlRules(batchRuleChildren(rule), props.assignmentConfig, t)) : visit(batchRuleChildren(rule)),
      ...(Array.isArray(rule.control) ? { control: rule.control.map((control: Record<string, any>) => ({ ...control, ...(Array.isArray(control.rule) ? { rule: visit(control.rule) } : {}) })) } : {}),
    }))
  }
  return visit(props.rules)
})
const currentNotices = computed(() => { void eventTick.value; return [...(activeSession.value?.notifications.values() ?? [])] })
watch(previewIndex, () => executeEvents(session => { if (session === activeSession.value) session.reload() }))
function eventReadonly(field: string) {
  return assignmentFields.value.includes(field) ? props.options?.form?.disabled === true || props.fieldWriteModes?.[field] === 'READONLY'
    || ruleMap.value.get(field)?.props?.disabled === true || ruleMap.value.get(field)?.props?.readonly === true : readonly(field)
}
function executeEvents(action: (session: BatchEventSession) => void) {
  const snapshots = eventSessions.value.map(session => session.snapshot())
  let failed = false
  try {
    for (const session of eventSessions.value) action(session)
    eventError.value = ''
  } catch (error) {
    eventSessions.value.forEach((session, index) => session.restore(snapshots[index]!))
    failed = true
    eventError.value = props.formatError(error)
  }
  if (failed || eventSessions.value.some((session, index) => JSON.stringify(session.snapshot()) !== JSON.stringify(snapshots[index]))) eventTick.value++
}
const sharedEventContext = {
  version: () => eventTick.value,
  session: () => activeSession.value,
  change: setValue,
  clearAssignment: () => {
    if (initializationError.value) return false
    const fields = assignmentFields.value.filter(field => ruleMap.value.has(field))
    executeEvents(session => session.changeValues(Object.fromEntries(fields.filter(field => session.data[field] != null && session.data[field] !== '').map(field => [field, null]))))
    return !eventError.value
  },
  beforeFetch: (config: unknown, data: unknown) => {
    let result: unknown
    executeEvents(session => { if (session === activeSession.value) result = session.beforeFetch(config, data) })
    if (eventError.value) throw new Error(eventError.value)
    return result
  },
  event: (rule: Record<string, any>, name: string, args: unknown[], kind?: 'on' | 'hook') => executeEvents(session => session.ruleEvent(rule, name, args, kind)),
}
const fieldMap = computed(() => new Map(props.fields.map(field => [field.field, field])))
const unsupportedControl = computed(() => unsupportedBatchControl(props.rules, new Set(fieldMap.value.keys())))
const ruleMap = computed(() => {
  const map = new Map<string, Record<string, any>>()
  function visit(rules: Array<Record<string, any>>, inheritedReadonly = false) {
    for (const rule of rules) {
      const locked = inheritedReadonly || rule.disabled === true || rule.readonly === true || rule.props?.disabled === true || rule.props?.readonly === true
      if (rule.field) map.set(rule.field, { ...rule, props: { ...rule.props, disabled: locked } })
      visit(batchRuleChildren(rule), locked)
      for (const control of Array.isArray(rule.control) ? rule.control : []) {
        if (Array.isArray(control.rule)) visit(control.rule, locked)
      }
    }
  }
  visit(eventRules.value)
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
  if (eventReadonly(name) || initializationError.value) return false
  executeEvents(session => session.change(name, value))
  if (eventError.value) return false
  values[name] = value
  intents[name] = value == null || value === '' ? { kind: 'clear' } : { kind: 'set', value }
  return true
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
  previewIndex.value = 0
  initializeSessions()
}, { immediate: true })
function initializeSessions() {
  initializationError.value = ''
  if (!props.runRowEvents) return
  const drafts = props.mode === 'add' ? Array.from({ length: addCount.value }, () => ({ ...initialValues.value })) : assignmentRows.value
  eventSessions.value = drafts.map(row => props.runRowEvents!.createSession(row, eventRules.value, eventReadonly, props.options))
  executeEvents(session => {
    session.bootstrap()
    if (props.mode === 'add') for (const [field, intent] of Object.entries(intents)) session.change(field, intent.kind === 'clear' ? null : intent.value)
  })
  initializationError.value = eventError.value
}
watch(addCount, () => { if (props.visible && props.mode === 'add') initializeSessions() })
async function close() {
  if (applying.value) return false
  if (Object.keys(intents).length || props.assignmentHasChanges?.()) {
    try { await ElMessageBox.confirm(t('subTable.batchDiscardConfirm'), t('common.cancel'), { type: 'warning' }) } catch { return false }
  }
  executeEvents(session => session.reset())
  if (eventError.value) return false
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
    if (initializationError.value || eventError.value) throw new Error(initializationError.value || eventError.value)
    assertFresh()
    if ((props.mode === 'edit' && (!targetIds.value.length || targetIds.value.length > 200))
      || (props.mode === 'add' && (!Number.isInteger(addCount.value) || addCount.value < 1 || addCount.value > 200))) throw new Error(t('subTable.batchLimit'))
    const fields = props.fields.filter(field => !readonly(field.field))
    const patch = Object.fromEntries(fields.filter(field => intents[field.field]).map(field => [field.field, intents[field.field]!]))
    const changes: BatchDraftChangeSet = Object.keys(patch).length && props.mode === 'edit' ? { uniformPatches: [{ rowIds: [...targetIds.value], fields: patch }] } : {}
    let next = applyBatchDraft(props.rows, row => props.rowIdentity(row)!, changes)
    const touched = new Set(Object.keys(patch))
    if (eventSessions.value.length) {
      executeEvents(session => { session.submit(fields) })
      if (eventError.value) throw new Error(eventError.value)
      const drafts = new Map(eventSessions.value.map((session, index) => [targetIds.value[index], session.data]))
      if (props.mode === 'edit') next = next.map(row => {
        const draft = drafts.get(props.rowIdentity(row)!)
        if (!draft) return row
        // Only the typed Assign collector can commit ownership fields.
        return { ...draft, ...Object.fromEntries(assignmentFields.value.map(field => [field, row[field]])) }
      })
    }
    let assignment: SubTableBatchChange | undefined
    if (props.showAssign && props.assignmentHasChanges?.()) {
      if (!props.collectAssignment) throw new Error(t('common.operationFailed'))
      const collected = props.collectAssignment(next.filter(row => targetIds.value.includes(props.rowIdentity(row)!)))
      const byId = new Map(collected.rows.map(row => [props.rowIdentity(row), row]))
      next = next.map(row => byId.get(props.rowIdentity(row)) ?? row)
      assignment = collected.change
      collected.editedFields?.forEach(field => touched.add(field))
    }
    next = collectEventPatches(next, changes)
    let prepared: PreparedBatchRows | undefined
    if (props.mode === 'add') {
      prepared = await prepareAdded(fields)
      next = [...props.rows, ...prepared.rows]
    } else if (touched.size && !eventSessions.value.length) {
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
function collectEventPatches(rows: Array<Record<string, unknown>>, changes: BatchDraftChangeSet) {
  if (props.mode !== 'edit' || !eventSessions.value.length) return rows
  const baseline = new Map(baselineRows.value.map(row => [props.rowIdentity(row), row]))
  changes.rowPatches = rows.filter(row => targetIds.value.includes(props.rowIdentity(row)!)).flatMap(row => {
    const before = baseline.get(props.rowIdentity(row))!
    const fields = Object.fromEntries(props.fields.filter(field => !readonly(field.field)
      && JSON.stringify(before[field.field]) !== JSON.stringify(row[field.field]))
      .map(field => [field.field, { kind: 'set' as const, value: row[field.field] }]))
    return Object.keys(fields).length ? [{ rowId: props.rowIdentity(row)!, fields }] : []
  })
  delete changes.uniformPatches
  return rows
}
async function prepareAdded(fields: BatchEditableField[]): Promise<PreparedBatchRows> {
  const row = Object.fromEntries(fields.map(field => [field.field, intents[field.field]?.kind === 'clear' ? null : values[field.field]]))
  const fresh = eventSessions.value.length ? eventSessions.value.map(session => session.data) : Array.from({ length: addCount.value }, () => JSON.parse(JSON.stringify(row)))
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
.batch-preview-row { margin-bottom: 12px; width: 180px; }
.batch-edit-dialog__title { display:flex; align-items:center; gap:8px; }
</style>
