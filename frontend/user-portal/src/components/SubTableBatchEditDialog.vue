<template>
  <BatchWorkspace
    :visible="visible" :rows="rows" :fields="fields" :row-identity="rowIdentity"
    :field-write-modes="capability.rowDesign?.fieldWriteModes" :field-definitions="workspaceFields"
    :default-value-for-field="defaultValueForField" :format-error="formatError"
    :show-add="showAdd" :show-delete="showDelete" :allow-edit="allowEdit" :show-assign="showAssign"
    :parent-selection="parentSelection" :prepare-rows="prepareRows"
    :collect-assignment="collectAssignment" :assignment-has-changes="assignmentHasChanges"
    @update:visible="emit('update:visible', $event)"
    @apply="(next, changes) => emit('apply', next, changes)"
    @apply-workspace="(next, changes, prepared, assignment) => emit('apply-workspace', next, changes, prepared, assignment)"
  >
    <template #help>
      <DesignerHelpLink path="/form-ctl-sub-table#batch" :ariaLabel="t('subTable.batchGuideLinkAria')" test-id="batch-workspace-guide-link" />
    </template>
    <template #assignment="{ rows: assignmentRows }">
      <SubTableBatchAssignmentEditor
        v-if="visible && showAssign && assignmentCapability && rowScopeKey" ref="assignmentEditor"
        :rows="assignmentRows" :capability="assignmentCapability" :row-identity="rowIdentity" :row-scope-key="rowScopeKey"
      />
    </template>
  </BatchWorkspace>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import BatchWorkspace from '@platform-shared/batch/BatchWorkspace.vue'
import type { BatchEditableField } from '@platform-shared/batch/fields'
import type { BatchWorkspaceField } from '@platform-shared/batch/workspaceTypes'
import type { BindingFieldDefinition, BindingParentSelection } from '@/utils/subTableRowRuntime'
import { pkNeedsAllocation } from '@/utils/subTableRowRuntime/pkPredicates'
import type { PreparedBatchRows } from '@/utils/subTableBatchAdd'
import { resolveUserFacingHttpMessage } from '@/utils/httpErrorMessage'
import { coerceDefaultValueForRuleType } from '@/utils/formCreateRuleDefaults'
import { extractBatchEditableFields, type SubTableBatchCapability } from '@/utils/subTableBatchCapability'
import type { BatchDraftChangeSet } from '@/utils/subTableBatchDraft'
import type { SubTableBatchChange } from '@/utils/subTableBatchChanges'
import DesignerHelpLink from './DesignerHelpLink.vue'
import SubTableBatchAssignmentEditor from './SubTableBatchAssignmentEditor.vue'

const props = withDefaults(defineProps<{
  visible: boolean
  rows: Array<Record<string, unknown>>
  capability: SubTableBatchCapability
  rowIdentity: (row: Record<string, unknown>) => string | null
  showAssign?: boolean
  assignmentCapability?: SubTableBatchCapability | null
  rowScopeKey?: (row: Record<string, unknown>) => Record<string, unknown> | null
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
  (event: 'open-delete'): void
  (event: 'apply-workspace', rows: Array<Record<string, unknown>>, changes: BatchDraftChangeSet, prepared?: PreparedBatchRows, assignment?: SubTableBatchChange): void
}>()
const { t } = useI18n()
const assignmentEditor = ref<InstanceType<typeof SubTableBatchAssignmentEditor>>()
const fields = computed(() => extractBatchEditableFields(props.capability) ?? [])
const workspaceFields = computed<BatchWorkspaceField[]>(() =>
  (props.capability.rowDesign?.binding.fieldDefinitions ?? props.fieldDefinitions ?? []).map(field => ({
    ...field, requiresAllocation: pkNeedsAllocation(field),
    nullable: props.capability.rowDesign?.fields.find(item => item.fieldName === field.fieldName)?.nullable,
  })))

function defaultValueForField(field: BatchEditableField): unknown {
  const metadata = props.capability.rowDesign?.fields.find(item => item.fieldName === field.field)
  return coerceDefaultValueForRuleType(field.type === 'number' ? 'inputNumber' : field.type,
    field.defaultValue ?? metadata?.defaultValue ?? null, { options: field.options })
}
function formatError(error: unknown): string {
  return error instanceof Error && error.message.startsWith('subTable.') ? t(error.message) : resolveUserFacingHttpMessage(error, t)
}
function collectAssignment(rows: Array<Record<string, unknown>>) {
  if (!assignmentEditor.value) throw new Error(t('common.operationFailed'))
  return assignmentEditor.value.collect(rows)
}
function assignmentHasChanges(): boolean { return assignmentEditor.value?.hasChanges() === true }
</script>
