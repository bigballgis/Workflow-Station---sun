<template>
  <el-dialog class="batch-edit-dialog" top="24px" :model-value="visible" :title="t(mode === 'add' ? 'subTable.batchAdd' : 'subTable.batchEdit')"
    width="min(960px, calc(100vw - 32px))" append-to-body :close-on-click-modal="false" :before-close="beforeClose">
    <template #header><div class="batch-edit-dialog__title"><span class="el-dialog__title">{{ t(mode === 'add' ? 'subTable.batchAdd' : 'subTable.batchEdit') }}</span><slot name="help" /></div></template>
    <el-alert v-if="unsupportedControl" :title="t('subTable.batchUnsupportedControl', { type: unsupportedControl })" type="error" :closable="false" />
    <el-alert v-if="eventError || initializationError" :title="initializationError || eventError" type="error" :closable="false" />
    <el-alert v-if="eventPending || queuedPending" :title="t('subTable.batchEventPending')" type="info" :closable="false" />
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
      :initial-values="mode === 'add' ? initialValues : undefined">
      <template #assignment="{ rule }"><slot v-if="showAssign" name="assignment" :rule="rule" :rows="assignmentRows" :event-context="sharedEventContext" :disabled="options?.form?.disabled === true || rule.props?.disabled === true || rule.props?.readonly === true" /></template>
    </BatchDesignedForm>
    </template>
    <template #footer><el-button @click="close">{{ t('common.cancel') }}</el-button><el-button type="primary" :loading="applying" :disabled="eventPending || queuedPending || !!unsupportedControl || (mode !== 'add' && !targetIds.length)" @click="apply">{{ t('subTable.batchApply') }}</el-button></template>
  </el-dialog>
</template>
<script setup lang="ts">
import { computed, reactive, ref, shallowRef, watch, onBeforeUnmount } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { BindingParentSelection } from '../tableFkRuntime'
import type { PreparedBatchRows, BatchWorkspaceField, BatchAssignmentCollection, SubTableBatchChange } from './workspaceTypes'
import { firstBatchFieldError } from './validation'
import type { BatchEditableField } from './fields'
import { applyBatchDraft, type BatchDraftChangeSet, type BatchFieldIntent } from './draft'
import { batchRuleChildren, unsupportedBatchControl } from './batchFormRules'
import BatchDesignedForm from './BatchDesignedForm.vue'
import { BatchRowEventError, type BatchEventSession, type BatchRowEventRunner } from './rowEvents'
import { assignmentControlRules } from './assignmentControlRules'
import type { BatchAssignmentConfig } from './workspaceTypes'
import { afterEvent, isEventPromise } from '../formEvents/async'
import { FormEventOperation } from '../formEvents/operation'
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
let acceptedSessions: BatchEventSession[] = []
let eventOperation: FormEventOperation | undefined
let eventCompletion: Promise<boolean> | undefined
let queuedEvents: Promise<boolean> | undefined
let inputGeneration = 0
const queuedPending = ref(false)
const eventPending = ref(false)
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
const currentNotices = computed(() => { void eventTick.value; return [...(acceptedSessions[previewIndex.value]?.notifications.values() ?? [])] })
watch(previewIndex, () => { cancelEvents(); return executeEvents(session => { if (session === activeSession.value) return session.reload() }) })
function eventReadonly(field: string) {
  return assignmentFields.value.includes(field) ? props.options?.form?.disabled === true || props.fieldWriteModes?.[field] === 'READONLY'
    || ruleMap.value.get(field)?.props?.disabled === true || ruleMap.value.get(field)?.props?.readonly === true : readonly(field)
}
function cancelEvents(invalidate = true) {
  if (invalidate) { inputGeneration++; queuedEvents = undefined; queuedPending.value = false }
  eventOperation?.cancel()
  eventOperation?.finish()
  eventOperation = undefined
  eventCompletion = undefined
  eventPending.value = false
  eventSessions.value = acceptedSessions
  eventTick.value++
}
onBeforeUnmount(cancelEvents)
function executeEvents(action: (session: BatchEventSession) => unknown): boolean | Promise<boolean> {
  cancelEvents(false)
  const operation = new FormEventOperation()
  eventOperation = operation
  const working = acceptedSessions.map(session => session.fork(operation))
  working.forEach(session => { session.notify = () => { if (eventOperation === operation) eventTick.value++ } })
  eventSessions.value = working
  const finish = (error?: unknown) => {
    if (eventOperation !== operation) return false
    if (error == null) {
      operation.assertActive()
      try { operation.finish() } catch (failure) { return finish(failure) }
      acceptedSessions = working
      eventError.value = ''
    } else {
      operation.cancel(error)
      if (error instanceof BatchRowEventError && error.message === 'subTable.batchEventValidation') {
        working.forEach((session, index) => {
          const errors = acceptedSessions[index]!.errors
          for (const [field, message] of session.errors) errors.set(field, message)
        })
      }
      eventError.value = props.formatError(error)
    }
    eventSessions.value = acceptedSessions
    eventOperation = undefined
    eventCompletion = undefined
    eventPending.value = false
    operation.finish()
    eventTick.value++
    return error == null
  }
  try {
    const pending: PromiseLike<unknown>[] = []
    for (const session of working) {
      const result = action(session)
      if (isEventPromise(result)) {
        const promise = Promise.resolve(result)
        // A later row can fail synchronously; the transaction still reports that error.
        void promise.catch(() => {})
        pending.push(promise)
      }
    }
    if (pending.length) {
      eventPending.value = true
      eventCompletion = operation.wait(Promise.all(pending)).then(() => finish(), error => finish(error))
      return eventCompletion
    }
    return finish()
  } catch (error) {
    return finish(error)
  }
}
function enqueueEvents(action: (session: BatchEventSession) => unknown): boolean | Promise<boolean> {
  const previous = queuedEvents ?? eventCompletion
  if (!previous) return executeEvents(action)
  const generation = inputGeneration
  const next = previous.then(accepted => accepted && inputGeneration === generation && props.visible ? executeEvents(action) : false)
  queuedEvents = next
  queuedPending.value = true
  void next.finally(() => { if (queuedEvents === next) { queuedEvents = undefined; queuedPending.value = false } }).catch(() => {})
  return next
}
const sharedEventContext = {
  version: () => eventTick.value,
  session: () => activeSession.value,
  pending: () => eventPending.value || queuedPending.value,
  change: setValue,
  clearAssignment: () => {
    if (applying.value || initializationError.value) return false
    cancelEvents()
    const fields = assignmentFields.value.filter(field => ruleMap.value.has(field))
    return executeEvents(session => session.changeValues(Object.fromEntries(fields.filter(field => session.data[field] != null && session.data[field] !== '').map(field => [field, null]))))
  },
  beforeFetch: (config: unknown, data: unknown) => {
    let result: unknown
    const run = () => afterEvent(enqueueEvents(session => {
      if (session !== activeSession.value) return
      return afterEvent(session.beforeFetch(config, data), value => { result = value })
    }), accepted => {
      if (!accepted) throw new Error(eventError.value)
      return result
    })
    return run()
  },
  event: (rule: Record<string, any>, name: string, args: unknown[], kind?: 'on' | 'hook') => {
    if (!props.visible || applying.value) return
    if (name === 'input' && kind !== 'hook' && typeof rule.field === 'string') {
      if (eventSessions.value.every(session => JSON.stringify(session.data[rule.field]) === JSON.stringify(args[0]))) {
        return enqueueEvents(session => session.ruleEvent(rule, name, args, kind))
      }
      cancelEvents()
      return afterEvent(executeEvents(session => afterEvent(session.change(rule.field, args[0]), () => session.ruleEvent(rule, name, args, kind))), accepted => {
        if (accepted) recordFieldIntent(rule.field, args[0])
        return accepted
      })
    }
    return enqueueEvents(session => session.ruleEvent(rule, name, args, kind))
  },
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
  if (applying.value || eventReadonly(name) || initializationError.value) return false
  cancelEvents()
  return afterEvent(executeEvents(session => session.change(name, value)), accepted => {
  if (!accepted) return false
  recordFieldIntent(name, value)
  return true
  })
}
function recordFieldIntent(name: string, value: unknown) {
  values[name] = value
  intents[name] = value == null || value === '' ? { kind: 'clear' } : { kind: 'set', value }
}
watch(() => props.visible, visible => {
  cancelEvents()
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
  cancelEvents()
  initializationError.value = ''
  if (!props.runRowEvents) return
  const drafts = props.mode === 'add' ? Array.from({ length: addCount.value }, () => ({ ...initialValues.value })) : assignmentRows.value
  eventSessions.value = drafts.map(row => props.runRowEvents!.createSession(row, eventRules.value, eventReadonly, props.options))
  acceptedSessions = eventSessions.value
  const result = executeEvents(session => afterEvent(session.bootstrap(), () => {
    if (props.mode === 'add') return session.changeValues(Object.fromEntries(Object.entries(intents).map(([field, intent]) => [field, intent.kind === 'clear' ? null : intent.value])))
  }))
  void afterEvent(result, accepted => { if (!accepted) initializationError.value = eventError.value })
}
watch(addCount, () => { if (props.visible && props.mode === 'add') initializeSessions() })
watch(() => [props.rows, props.selectedRowIds], () => {
  if (!props.visible) return
  const selected = [...new Set(props.selectedRowIds ?? [])]
  const current = new Map(props.rows.map(row => [props.rowIdentity(row), row]))
  if (JSON.stringify(selected) === JSON.stringify(targetIds.value)
    && assignmentRows.value.every(row => JSON.stringify(current.get(props.rowIdentity(row))) === JSON.stringify(row))) return
  cancelEvents()
  eventError.value = t('subTable.batchStaleDraft')
}, { flush: 'sync' })
async function close() {
  if (applying.value) return false
  const wasPending = eventPending.value || queuedPending.value
  cancelEvents()
  if (wasPending || Object.keys(intents).length || props.assignmentHasChanges?.()) {
    try { await ElMessageBox.confirm(t('subTable.batchDiscardConfirm'), t('common.cancel'), { type: 'warning' }) } catch { return false }
  }
  if (!await executeEvents(session => afterEvent(session.reset(), result => {
    if (result === false) throw new BatchRowEventError('subTable.batchEventSubmitRejected', 'onReset')
  }))) return false
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
  if (applying.value || eventPending.value || queuedPending.value) return
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
      if (!await executeEvents(session => session.submit(fields))) throw new Error(eventError.value)
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
  const requiredFields = fields.map(field => ({ ...field, required: field.required || definitions.value.get(field.field)?.nullable === false }))
  if (eventSessions.value.length) eventSessions.value.forEach(session => session.validate(requiredFields))
  else {
    const error = firstBatchFieldError(fresh, requiredFields)
    if (error) throw new Error(t('subTable.batchInvalidField', { ...error }))
  }
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
