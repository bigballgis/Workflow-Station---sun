<template>
  <el-tooltip :disabled="!definition.invalid" :content="t('form.batchFormInvalid')">
    <el-button v-if="definition.flags.edit || definition.flags.assign" size="small" :disabled="definition.invalid || !selectedRowIds.length || selectedRowIds.length > 200" @click="open">{{ t('subTable.batchEdit') }}</el-button>
  </el-tooltip>
  <el-button v-if="businessRows && definition.flags.add" size="small" :disabled="definition.invalid" @click="openAdd">{{ t('subTable.batchAdd') }}</el-button>
  <el-button v-if="businessRows && definition.flags.delete" size="small" :disabled="definition.invalid || !selectedRowIds.length || selectedRowIds.length > 200" @click="deleteSelected">{{ t('subTable.batchDelete') }}</el-button>
  <BatchWorkspace
    v-if="visible" v-model:visible="visible" :rows="modelValue ?? []" :fields="fields" :row-identity="rowIdentity"
    :rules="definition.rule" :options="definition.options" :selected-row-ids="targetIds" :mode="mode"
    :field-definitions="workspaceFields" :default-value-for-field="defaultValueForField" :format-error="formatError"
    :field-write-modes="definition.fieldWriteModes"
    :allow-edit="definition.flags.edit" :show-assign="definition.flags.assign && mode !== 'add'"
    :prepare-rows="prepareRows" :collect-assignment="collectAssignment" :assignment-has-changes="assignmentHasChanges"
    @apply="rows => applyRows(rows)" @apply-workspace="(rows, _changes, prepared) => applyRows(rows, prepared)"
  >
    <template #help><DesignerHelpLink path="/form-ctl-sub-table#batch" :ariaLabel="t('subTable.batchGuideLinkAria')" /></template>
    <template #assignment="{ rows, disabled, rule }">
      <BatchAssignmentEditor v-if="definition.flags.assign" ref="assignmentEditor" :rule="rule" :options="definition.options" :disabled="disabled" :rows="rows" :capability="definition" :field-write-modes="definition.fieldWriteModes"
        :row-identity="rowIdentity" :row-scope-key="rowScopeKey" :create-directory="useBatchPreviewDirectory" />
    </template>
  </BatchWorkspace>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useI18n } from 'vue-i18n'
import BatchWorkspace from '@platform-shared/batch/BatchWorkspace.vue'
import BatchAssignmentEditor from '@platform-shared/batch/BatchAssignmentEditor.vue'
import { extractBatchFields, type BatchEditableField } from '@platform-shared/batch/fields'
import type { PreparedBatchRows } from '@platform-shared/batch/workspaceTypes'
import { buildRowAddContext } from '@/utils/tableFkRuntime'
import { prepareSubTableAddRow } from '@/utils/subTableRowRuntime'
import type { FieldDefinition } from '@/api/functionUnit'
import { normalizeFieldDefinitionForRuntime } from '@/utils/formFieldMeta'
import { coerceDefaultValueForRuleType } from '@/utils/formCreateRuleDefaults'
import { mergeFormRowWithSeed } from './subTableAddDialogHelpers'
import type { SubTableFieldProps } from '@/composables/designerSubTableField/types'
import type { BatchPreviewDefinition } from '@/utils/batchSubTablePreview'
import { createBatchPreviewKeyAllocator } from '@/utils/batchPreviewKeyAllocator'
import { resolveUserFacingHttpMessage } from '@/utils/httpErrorMessage'
import { useBatchPreviewDirectory } from '@/composables/designerSubTableField/useBatchPreviewDirectory'
import DesignerHelpLink from './DesignerHelpLink.vue'

const props = defineProps<SubTableFieldProps & { definition: BatchPreviewDefinition; selectedRowIds: string[]; rowIdentity: (row: Record<string, unknown>) => string | null }>()
const emit = defineEmits<{
  (event: 'update:modelValue', rows: Array<Record<string, unknown>>): void
  (event: 'update:primaryFormData', value: Record<string, unknown>): void
}>()
const { t } = useI18n()
const visible = ref(false)
const mode = ref<'edit' | 'add'>('edit')
const targetIds = ref<string[]>([])
const workingRows = ref<Array<Record<string, unknown>>>([])
const assignmentEditor = ref<InstanceType<typeof BatchAssignmentEditor>>()
const fields = computed(() => extractBatchFields(props.definition.rule) ?? [])
const workspaceFields = computed(() => (props.config.fieldDefinitions ?? []).map(field => {
  const normalized = normalizeFieldDefinitionForRuntime(field as FieldDefinition)
  return { ...normalized, requiresAllocation: normalized.isPrimaryKey === true
    && (normalized.pkGeneration?.strategy ?? normalized.pkGenerationJson?.strategy ?? 'uuid') !== 'manual' }
}))
const primaryKeys = computed(() => workspaceFields.value.filter(field => field.isPrimaryKey).map(field => field.fieldName))
const businessRows = computed(() => props.config.bindingType === 'SUB' && props.config.bindingMode === 'EDITABLE'
  && props.config.bindingLinkMode === 'structuralFk')

function rowScopeKey(row: Record<string, unknown>): Record<string, unknown> {
  return primaryKeys.value.length && primaryKeys.value.every(field => row[field] != null && row[field] !== '')
    ? Object.fromEntries(primaryKeys.value.map(field => [field, row[field]])) : { platformRowUuid: row.platformRowUuid }
}
function rowIdentity(row: Record<string, unknown>): string | null { return props.rowIdentity(row) }
function open() {
  if (!props.selectedRowIds.length || props.selectedRowIds.length > 200) return
  mode.value = 'edit'
  targetIds.value = [...props.selectedRowIds]
  workingRows.value = props.modelValue ?? []
  visible.value = true
}
function openAdd() {
  mode.value = 'add'; targetIds.value = []; workingRows.value = props.modelValue ?? []; visible.value = true
}
async function deleteSelected() {
  if (!businessRows.value || !props.definition.flags.delete) return
  const targets = JSON.parse(JSON.stringify((props.modelValue ?? []).filter(row => props.selectedRowIds.includes(rowIdentity(row)!)))) as Array<Record<string, unknown>>
  if (!targets.length || targets.length > 200) return
  try { await ElMessageBox.confirm(t('subTable.batchDeleteConfirm', { count: targets.length }), t('subTable.deleteConfirm'), { type: 'warning' }) } catch { return }
  const current = props.modelValue ?? []
  if (targets.some(row => JSON.stringify(current.find(item => rowIdentity(item) === rowIdentity(row))) !== JSON.stringify(row))) {
    ElMessage.error(t('subTable.batchStaleDraft')); return
  }
  const removed = new Set(targets.map(rowIdentity))
  emit('update:modelValue', current.filter(row => !removed.has(rowIdentity(row))))
}
function defaultValueForField(field: BatchEditableField): unknown {
  const metadata = workspaceFields.value.find(item => item.fieldName === field.field)
  return coerceDefaultValueForRuleType(field.type === 'number' ? 'inputNumber' : field.type,
    field.defaultValue ?? metadata?.defaultValue ?? null, { options: field.options })
}
function formatError(error: unknown): string {
  return error instanceof Error && /^(subTable|form)\./.test(error.message) ? t(error.message) : resolveUserFacingHttpMessage(error, t)
}
function collectAssignment(rows: Array<Record<string, unknown>>) {
  if (!assignmentEditor.value) throw new Error('subTable.batchAssignmentRequired')
  return assignmentEditor.value.collect(rows)
}
function assignmentHasChanges(): boolean { return assignmentEditor.value?.hasChanges() === true }
function applyRows(rows: Array<Record<string, unknown>>, prepared?: PreparedBatchRows) {
  emit('update:modelValue', rows)
  if (prepared?.primaryFormDataPatch) emit('update:primaryFormData', prepared.primaryFormDataPatch)
}
async function prepareRows(rows: Array<Record<string, unknown>>): Promise<PreparedBatchRows> {
  let parent = JSON.parse(JSON.stringify(props.primaryFormData ?? {})) as Record<string, unknown>
  const result: PreparedBatchRows = { rows: [] }
  if (props.config.tableId == null) throw new Error('form.batchFormInvalid')
  const allocatePrimaryKeys = createBatchPreviewKeyAllocator({
    ...Object.fromEntries(Object.entries(props.parentTablesById ?? {}).map(([id, table]) => [id, table.fieldDefinitions])),
    [props.config.tableId]: workspaceFields.value,
  }, { ...(props.primaryTableId != null ? { [props.primaryTableId]: [parent] } : {}), [props.config.tableId]: workingRows.value })
  for (const row of rows) {
    const prepared = await prepareSubTableAddRow({
      columns: [], fieldDefinitions: props.config.fieldDefinitions,
      rowAddContext: buildRowAddContext(parent, props.previewTableBindings, null, null,
        { bindingId: props.config.bindingId, tableId: props.config.tableId, filterFkRefTableId: props.config.filterFkRefTableId }),
      tableId: props.config.tableId, primaryTableId: props.primaryTableId, parentTablesById: props.parentTablesById,
      tableDisplayName: props.config.title, primaryTableDisplayName: props.primaryTableDisplayName,
      autoEnsurePrimaryRecord: true, bindingLinkMode: props.config.bindingLinkMode,
      bindingForeignKeyField: props.config.bindingForeignKeyField, filterFkFieldName: props.config.filterFkFieldName,
      fkFillSources: props.config.fkFillSources, allocatePrimaryKeys, t,
    })
    if (!prepared.ok) throw new Error(prepared.message)
    const next = mergeFormRowWithSeed(prepared.initialRow, row)
    next.platformRowUuid ??= crypto.randomUUID()
    result.rows.push(next)
    if (prepared.primaryFormDataPatch) { parent = prepared.primaryFormDataPatch; result.primaryFormDataPatch = parent }
  }
  return result
}
</script>
