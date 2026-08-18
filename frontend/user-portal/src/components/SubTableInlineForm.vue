<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import PortalFormFields, { type PortalSubTableBindingLite } from './PortalFormFields.vue'
import { flattenLeafFormFields, type FormField } from './formRendererHelpers'
import type { BindingFieldDefinition } from '@/utils/subTableRowRuntime'
import {
  useSubTableDialogComponentEvents,
  type DialogColumnWithEvents,
} from '@/composables/subTableAddDialog/useSubTableDialogComponentEvents'

/**
 * Inline form rendered **below** a SubTableField when the designer chose
 * portalViews.assigneeTodo = 'formBelowTable'. Nested subTable widgets use
 * {@link PortalFormFields} so structure matches Developer Workstation preview.
 *
 * Form Design Events reuse the Add/Edit dialog runtime (same subset: Form
 * onCreated/onMounted/onChange/onReload/beforeSubmit/onSubmit/onReset and
 * field change/blur + hook load/mounted/value).
 */

interface Props {
  title?: string
  fields: FormField[]
  currentRow?: Record<string, unknown> | null
  readonly?: boolean
  labelWidth?: string
  subTableBindings?: PortalSubTableBindingLite[]
  linkedSubTableBindings?: PortalSubTableBindingLite[]
  suppressLinkOnlyStandaloneSubTables?: boolean
  /** FK/PK runtime context of the sub-table row this form edits — needed by nested sub-tables. */
  hostTableId?: number | null
  hostFieldDefinitions?: BindingFieldDefinition[]
  hostFunctionUnitId?: string
  hostTaskId?: string
  hostPrimaryFormData?: Record<string, unknown>
  hostPrimaryTableId?: number | null
  /** Sub-form Form Design options — Form-level onCreated / onMounted / onChange. */
  formOptions?: Record<string, unknown> | null
  /** Canvas columns from the source binding — sourceRule fallback when FormField has none. */
  dialogColumns?: DialogColumnWithEvents[] | Array<Record<string, unknown>> | null
}

const props = withDefaults(defineProps<Props>(), {
  title: '',
  readonly: false,
  labelWidth: '160px',
  suppressLinkOnlyStandaloneSubTables: false,
})

const emit = defineEmits<{
  (e: 'update:row', row: Record<string, unknown>): void
  (e: 'change', key: string, value: unknown): void
  (e: 'save'): void
}>()

const { t } = useI18n()

const rowModel = ref<Record<string, unknown>>({})

const INLINE_ROW_IDENTITY_KEYS = ['row_id', 'sub_task_id', 'id', 'id_idw'] as const

function identityKeyValue(row: Record<string, unknown>, key: string): string | null {
  const v = row[key]
  if (v == null || String(v).trim() === '') return null
  return String(v)
}

/**
 * Stable row id for bootstrap. Prefer business keys (`row_id` / `sub_task_id`)
 * before allocated `id` / `id_idw` — otherwise PK allocation changes the identity
 * string, rebootstrap copies the parent snapshot, and in-progress Y/N is dropped.
 */
function inlineFormRowIdentity(row: Record<string, unknown> | null | undefined): string {
  if (!row) return ''
  for (const k of INLINE_ROW_IDENTITY_KEYS) {
    const v = identityKeyValue(row, k)
    if (v != null) return `${k}:${v}`
  }
  return ''
}

/**
 * Same selected row after parent write-back (PK appeared, or only one identity
 * key is present on each side). A real row switch has at least one shared key
 * with a different value.
 */
function inlineFormIsSameLogicalRow(
  prev: Record<string, unknown> | null,
  next: Record<string, unknown> | null,
): boolean {
  if (!prev || !next) return false
  let compared = false
  for (const k of INLINE_ROW_IDENTITY_KEYS) {
    const a = identityKeyValue(prev, k)
    const b = identityKeyValue(next, k)
    if (a == null || b == null) continue
    compared = true
    if (a !== b) return false
  }
  if (compared) return true
  const prevHadId = INLINE_ROW_IDENTITY_KEYS.some(k => identityKeyValue(prev, k) != null)
  const nextHadId = INLINE_ROW_IDENTITY_KEYS.some(k => identityKeyValue(next, k) != null)
  return !prevHadId && nextHadId
}

function formOptionsEventFingerprint(options: Record<string, unknown> | null | undefined): string {
  if (!options || typeof options !== 'object') return ''
  return ['onChange', 'onMounted', 'onCreated', 'onReload', 'beforeSubmit']
    .map((k) => String(options[k] ?? ''))
    .join('\0')
}

function eventColumnsFromInlineForm(): DialogColumnWithEvents[] {
  const dialogByField = new Map<string, DialogColumnWithEvents>()
  for (const col of props.dialogColumns ?? []) {
    if (!col || typeof col !== 'object') continue
    const field = String((col as DialogColumnWithEvents).field ?? '').trim()
    if (!field) continue
    dialogByField.set(field, col as DialogColumnWithEvents)
  }
  return flattenLeafFormFields(props.fields)
    .filter(f => typeof f.key === 'string' && f.key.length > 0 && !f.key.startsWith('__'))
    .map((f) => {
      const d = dialogByField.get(f.key)
      return {
        field: f.key,
        label: f.label,
        type: f.type,
        hidden: f.hidden === true || d?.hidden === true,
        sourceRule: f.sourceRule ?? d?.sourceRule,
      }
    })
}

const {
  onDialogFieldChange,
  onDialogFieldBlur,
  isDialogFieldVisible,
  resetDialogEventVisibility,
  bootstrapDialogFormLifecycle,
  runFormOnReload,
  runFormBeforeSubmit,
  runFormOnSubmit,
  runFormOnReset,
} = useSubTableDialogComponentEvents(
  rowModel,
  eventColumnsFromInlineForm,
  () => props.formOptions,
)

/**
 * Bind rowModel + Event bootstrap only when the selected row changes.
 * `getCurrentRowForInlineForm` returns a new object every parent render — copying
 * that snapshot back (and emitting it) was overwriting in-progress Y/N edits and
 * triggering sub-table autosave of the page-load value, so Save/reload showed N again.
 * Dialog Event runtime also keeps bootstrap mutations local until the user confirms.
 *
 * Identity string can still change on the same row (`''` → `id:…` after PK
 * allocation). Rebootstrap would run onChange('__bootstrap__'), which field-gated
 * scripts skip, wiping api.hidden from the click that just allocated the PK.
 */
let lastBoundRow: Record<string, unknown> | null = null

watch(
  () => inlineFormRowIdentity(props.currentRow),
  (_nextId, prevId) => {
    const r = props.currentRow
    if (r && lastBoundRow && inlineFormIsSameLogicalRow(lastBoundRow, r)) {
      lastBoundRow = { ...r }
      return
    }
    lastBoundRow = r != null && typeof r === 'object' ? { ...r } : null
    rowModel.value = lastBoundRow ? { ...lastBoundRow } : {}
    resetDialogEventVisibility()
    if (!r) {
      if (prevId) runFormOnReset()
      return
    }
    bootstrapDialogFormLifecycle()
    if (prevId) runFormOnReload()
  },
  { immediate: true },
)

watch(
  () => formOptionsEventFingerprint(props.formOptions),
  (next, prev) => {
    if (!next || next === prev) return
    if (!inlineFormRowIdentity(props.currentRow) && !props.currentRow) return
    resetDialogEventVisibility()
    if (props.currentRow) bootstrapDialogFormLifecycle()
  },
)

function handleFieldUpdate(key: string, value: unknown) {
  onDialogFieldChange(key, value)
  emit('update:row', { ...rowModel.value })
  emit('change', key, value)
}

function handleFieldBlur(key: string) {
  onDialogFieldBlur(key)
  emit('update:row', { ...rowModel.value })
}

/** Flush row model into bindings before persist so Save allocates PK on the latest inline edits. */
function handleSaveClick() {
  if (!runFormBeforeSubmit()) return
  const merged = { ...rowModel.value }
  rowModel.value = merged
  emit('update:row', merged)
  runFormOnSubmit()
  emit('save')
}

const cardTitle = computed(() =>
  props.title?.trim() ? props.title : t('subTable.formBelowTableTitle'),
)
</script>

<template>
  <el-card
    shadow="never"
    class="sub-table-inline-form"
  >
    <template #header>
      <span class="title">{{ cardTitle }}</span>
    </template>
    <el-form
      :model="rowModel"
      :label-width="labelWidth"
      :disabled="readonly"
    >
      <el-row :gutter="20">
        <PortalFormFields
          :fields="fields"
          :model="rowModel"
          :readonly="readonly"
          :editable="!readonly"
          :sub-table-bindings="subTableBindings"
          :linked-sub-table-bindings="linkedSubTableBindings"
          :parent-row="currentRow"
          :suppress-link-only-standalone-sub-tables="suppressLinkOnlyStandaloneSubTables"
          :host-table-id="hostTableId ?? null"
          :host-field-definitions="hostFieldDefinitions"
          :host-function-unit-id="hostFunctionUnitId"
          :host-task-id="hostTaskId"
          :host-primary-form-data="hostPrimaryFormData"
          :host-primary-table-id="hostPrimaryTableId ?? null"
          :is-field-visible="isDialogFieldVisible"
          @update:field="handleFieldUpdate"
          @field-blur="handleFieldBlur"
        />
      </el-row>
      <el-empty
        v-if="fields.length === 0"
        :description="t('subTable.formBelowTableEmpty')"
      />
      <div
        v-if="!readonly && fields.length > 0"
        class="inline-form-actions"
      >
        <el-button
          type="primary"
          @click="handleSaveClick"
        >
          {{ t('common.save') }}
        </el-button>
      </div>
    </el-form>
  </el-card>
</template>

<style scoped>
.sub-table-inline-form {
  margin-bottom: 16px;
}

.sub-table-inline-form .title {
  font-weight: 600;
  font-size: 14px;
}

.inline-form-actions {
  margin-top: 12px;
  display: flex;
  justify-content: flex-end;
}
</style>
