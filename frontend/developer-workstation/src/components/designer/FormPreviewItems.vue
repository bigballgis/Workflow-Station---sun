<template>
  <template
    v-for="(item, idx) in items"
    :key="idx"
  >
    <div
      v-if="item.kind === 'fields'"
      class="form-preview-wrapper form-readonly-surface"
    >
      <form-create
        v-if="visiblePreviewRules(item.rule).length"
        :key="'preview-form-' + item.modelKey + (isMyRequestsPreview ? '-ro' : '-ed') + '-v' + previewVisibilityRenderTick"
        v-model="previewModel"
        locale="en"
        :rule="visiblePreviewRules(item.rule)"
        :option="effectivePreviewOption"
        @change="(field: string, value: unknown) => onPreviewFieldChange(item.rule, field, value)"
      />
    </div>

    <!--
      Inline Form (`inlineSubForm`): the bound sub-table's designed form, rendered right here.
      Deliberately NOT the subTable arm — no grid, no Add button. Mirrors the portal, where the
      widget edits row[0] of the binding.
    -->
    <div
      v-else-if="item.kind === 'inlineSubForm'"
      class="form-preview-wrapper form-readonly-surface inline-sub-form-preview"
    >
      <!--
        Labelled frame, matching the portal: these fields come from a DIFFERENT table than the
        host form, and without a boundary they read as ordinary host fields.
      -->
      <div class="inline-sub-form-preview__header">
        <el-icon class="inline-sub-form-preview__icon"><Document /></el-icon>
        <span class="inline-sub-form-preview__title">{{ item.binding.tableName }}</span>
      </div>
      <!-- The sub-form rule may hold the MI assignment container; bind it to THIS
           sub-table rather than whichever designer tab happens to be open. -->
      <MiAssignmentConfigScope :assignment-config="item.binding.assignmentConfig">
        <template v-if="visiblePreviewRules(item.binding.rule || []).length">
          <form-create
            :key="'preview-inline-' + item.modelKey + (isMyRequestsPreview ? '-ro' : '-ed') + '-v' + previewVisibilityRenderTick"
            :model-value="inlineSubFormModel(item.binding.bindingId)"
            locale="en"
            :rule="visiblePreviewRules(item.binding.rule || [])"
            :option="effectivePreviewOption"
            @update:model-value="(v: Record<string, any>) => setInlineSubFormModel(item.binding.bindingId, v)"
          />
        </template>
        <el-empty
          v-else
          :description="t('form.noFormContent')"
          :image-size="60"
        />
      </MiAssignmentConfigScope>
    </div>

    <div
      v-else-if="item.kind === 'subTable' && isPreviewSubTableVisible(item) && hasSubTablePreviewSurface(item.binding)"
      class="sub-table-preview-item"
    >
      <div class="sub-preview-header">
        <el-tag
          :type="item.binding.bindingType === 'SUB' ? 'success' : 'warning'"
          size="small"
        >
          {{ item.binding.bindingType === 'SUB' ? t('tableBinding.subTableType') : t('tableBinding.relationTableType') }}
        </el-tag>
        <span class="sub-preview-title">{{ item.binding.tableName }}</span>
      </div>
      <!-- One design, one preview: the form being edited is the form that renders. -->
      <SubTableField
        :config="{ title: item.binding.tableName, columns: item.binding.columns, tableId: item.binding.tableId, fieldDefinitions: item.binding.fieldDefinitions, bindingLinkMode: item.binding.bindingLinkMode, bindingForeignKeyField: item.binding.bindingForeignKeyField, bindingType: item.binding.bindingType }"
        :model-value="previewTableRows[item.binding.bindingId]"
        :editable="true"
        :allow-add="item.binding.allowAdd"
        :allow-edit="item.binding.allowEdit"
        :allow-delete="item.binding.allowDelete"
        :form-rule="item.binding.rule"
        :form-option="item.binding.option"
        :primary-form-data="previewModel"
        :function-unit-id="functionUnitId"
        :primary-table-display-name="primaryTableDisplayName"
        :primary-table-id="primaryTableId"
        :parent-tables-by-id="parentTablesById"
        :preview-table-bindings="previewTableBindings"
        :preview-lookup-compact="item.binding.compactCells === true"
        :assignment-config="item.binding.assignmentConfig"
        @update:model-value="(rows: any[]) => updateTableRows(item.binding.bindingId, rows, item.sourceRule)"
        @update:primary-form-data="mergePrimaryFormData"
      />
    </div>

    <div
      v-else-if="item.kind === 'subTable' && isPreviewSubTableVisible(item)"
      class="sub-table-preview-item"
    >
      <div class="sub-preview-header">
        <el-tag
          :type="item.binding.bindingType === 'SUB' ? 'success' : 'warning'"
          size="small"
        >
          {{ item.binding.bindingType === 'SUB' ? t('tableBinding.subTableType') : t('tableBinding.relationTableType') }}
        </el-tag>
        <span class="sub-preview-title">{{ item.binding.tableName }}</span>
      </div>
      <SubTableField
        v-if="hasSubTablePreviewSurface(item.binding)"
        :config="{ title: item.binding.tableName, columns: item.binding.columns || [], tableId: item.binding.tableId, fieldDefinitions: item.binding.fieldDefinitions, bindingLinkMode: item.binding.bindingLinkMode, bindingForeignKeyField: item.binding.bindingForeignKeyField, bindingType: item.binding.bindingType }"
        :model-value="previewTableRows[item.binding.bindingId]"
        :editable="!isMyRequestsPreview"
        :allow-add="item.binding.allowAdd"
        :allow-edit="item.binding.allowEdit"
        :allow-delete="item.binding.allowDelete"
        :form-rule="item.binding.rule"
        :form-option="item.binding.option"
        :primary-form-data="previewModel"
        :function-unit-id="functionUnitId"
        :primary-table-display-name="primaryTableDisplayName"
        :primary-table-id="primaryTableId"
        :parent-tables-by-id="parentTablesById"
        :preview-table-bindings="previewTableBindings"
        :preview-lookup-compact="isMyRequestsPreview"
        :assignment-config="item.binding.assignmentConfig"
        @update:model-value="(rows: any[]) => updateTableRows(item.binding.bindingId, rows, item.sourceRule)"
        @update:primary-form-data="mergePrimaryFormData"
      />
      <el-empty
        v-else
        :description="t('form.noFormContent')"
        :image-size="40"
        class="sub-table-preview-empty"
      />
    </div>

    <div
      v-else-if="item.kind === 'relationTable'"
      class="relation-preview-wrapper table-scroll-wrap"
    >
      <el-table
        :data="item.fields"
        border
        size="small"
        class="relation-preview-table"
      >
        <el-table-column
          prop="label"
          :label="' '"
          min-width="200"
        />
        <el-table-column
          prop="value"
          :label="' '"
          min-width="200"
        />
      </el-table>
    </div>

    <div
      v-else-if="item.kind === 'lookup' && isPreviewFieldVisible(item.field)"
      class="lookup-preview-item"
    >
      <LookupPreview
        :model-value="previewModel[item.field]"
        :label="item.label"
        :placeholder="item.placeholder"
        :search-fields="item.searchFields"
        :display-fields="item.displayFields"
        :selected-display-field="item.selectedDisplayField"
        :filter-conditions="effectivePreviewLookupFilterConditions(item)"
        :view-fields="item.viewFields"
        :field-defs="item.fieldDefs"
        :ensure-mock-fields="ensureMockFieldsForLookup(item)"
        :show-backfill-view="item.showBackfillView !== false"
        :readonly="isMyRequestsPreview || item.readonly === true"
        :multiple="item.multiple === true"
        @update:model-value="(val) => onLookupPreviewChange(item, val)"
        @select="(row) => onLookupPreviewSelect(item, row)"
        @clear="() => onLookupPreviewClear(item)"
      />
    </div>

    <el-card
      v-else-if="item.kind === 'card'"
      shadow="never"
      class="form-preview-card"
    >
      <template
        v-if="item.title"
        #header
      >
        <span class="form-preview-card-title">{{ item.title }}</span>
      </template>
      <FormPreviewItems
        v-model:preview-data="previewModel"
        :items="item.items"
        :preview-option="previewOption"
        :preview-table-rows="previewTableRows"
        :function-unit-id="functionUnitId"
        :primary-table-display-name="primaryTableDisplayName"
        :primary-table-id="primaryTableId"
        :parent-tables-by-id="parentTablesById"
        :preview-table-bindings="previewTableBindings"
        @update:preview-table-rows="emit('update:previewTableRows', $event)"
      />
    </el-card>
  </template>
</template>

<script setup lang="ts">
import { computed, inject, provide, reactive } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  PREVIEW_MY_REQUESTS_ACTIVE_KEY,
} from './previewSubTableDialog'
import { PREVIEW_LOOKUP_CASCADE_KEY } from './previewLookupCascade'
import SubTableField from './SubTableField.vue'
import MiAssignmentConfigScope from './MiAssignmentConfigScope.vue'
import LookupPreview from './LookupPreview.vue'
import type { FormPreviewItem, PreviewSubTableBinding } from './formPreviewTypes'
import {
  hasSubTablePreviewSurface,
} from './formPreviewTypes'
import {
  dispatchPreviewFieldValueChange,
} from '@/utils/formCreatePreviewEvents'
import { subTableComponentEventFieldKey } from '@/utils/formCreateComponentEvents'
import { useFormPreviewEventVisibility } from '@/composables/formDesigner/useFormPreviewEventVisibility'
import {
  buildDerivedFilterConditions,
  buildPreviewAutofillModelValue,
  normalizeLookupRow,
} from '@/utils/lookupCascade'

defineOptions({ name: 'FormPreviewItems' })

const props = defineProps<{
  items: FormPreviewItem[]
  previewData: Record<string, any>
  previewOption: Record<string, any>
  previewTableRows: Record<number, any[]>
  functionUnitId?: number
  primaryTableDisplayName?: string
  primaryTableId?: number | null
  parentTablesById?: Record<number, { fieldDefinitions: import('@/api/functionUnit').FieldDefinition[] }>
  previewTableBindings?: Array<{ tableId?: number | null; bindingType?: string }>
  /** Main table Request ID config — preview recomputes the readonly Request ID live from these fields. */
  requestIdConfig?: import('@/api/functionUnit').RequestIdConfig | null
}>()

const emit = defineEmits<{
  (e: 'update:previewData', value: Record<string, any>): void
  (e: 'update:previewTableRows', value: Record<number, any[]>): void
}>()

const { t } = useI18n()

const previewMyRequestsGlobal = inject(PREVIEW_MY_REQUESTS_ACTIVE_KEY, undefined)

const isMyRequestsPreview = computed(() => previewMyRequestsGlobal?.value === true)

const previewModel = computed({
  get: () => props.previewData,
  set: (value: Record<string, any>) => emit('update:previewData', value),
})

/**
 * Inline Form preview model: the widget is 1:1 with row[0] of its binding, so preview backs it
 * with that same row rather than the main-form model — otherwise sub-form field names would
 * collide with same-named main-table fields.
 */
function inlineSubFormModel(bindingId: number): Record<string, any> {
  const rows = props.previewTableRows[Number(bindingId)]
  const first = Array.isArray(rows) ? rows[0] : undefined
  return first && typeof first === 'object' ? first : {}
}

function setInlineSubFormModel(bindingId: number, value: Record<string, any>) {
  const id = Number(bindingId)
  const next = { ...props.previewTableRows }
  const rows = Array.isArray(next[id]) ? [...next[id]] : []
  rows[0] = { ...(rows[0] ?? {}), ...(value ?? {}) }
  next[id] = rows
  emit('update:previewTableRows', next)
}

const parentCascade = inject(PREVIEW_LOOKUP_CASCADE_KEY, null)
const lookupSelectedRows =
  parentCascade?.lookupSelectedRows ?? reactive<Record<string, Record<string, unknown>>>({})

const {
  previewVisibilityRenderTick,
  visiblePreviewRules,
  isPreviewFieldVisible,
  isPreviewSubTableVisible,
  previewVisibilityBridge,
  effectivePreviewOption,
} = useFormPreviewEventVisibility({
  items: () => props.items,
  previewOption: () => props.previewOption,
  previewModel,
  isMyRequestsPreview,
  emitPreviewData: (value) => emit('update:previewData', value),
})

function collectLookupPreviewItems(nodes: FormPreviewItem[]): Extract<FormPreviewItem, { kind: 'lookup' }>[] {
  const out: Extract<FormPreviewItem, { kind: 'lookup' }>[] = []
  for (const node of nodes) {
    if (node.kind === 'lookup') out.push(node)
    if (node.kind === 'card') out.push(...collectLookupPreviewItems(node.items))
  }
  return out
}

function applyCascadeAutofillFromParent(parentField: string, parentRow: Record<string, unknown> | null) {
  const deps = collectLookupPreviewItems(props.items)
  let nextModel: Record<string, any> | null = null
  for (const dep of deps) {
    if (dep.derivedFrom?.parentField !== parentField) continue
    if (dep.derivedFrom?.derivedMode !== 'autofill') continue
    const autofill = parentRow
      ? buildPreviewAutofillModelValue(
        { derivedFrom: dep.derivedFrom, filterConditions: dep.filterConditions },
        parentRow,
        {
          searchFields: dep.searchFields,
          selectedDisplayField: dep.selectedDisplayField,
          displayFields: dep.displayFields,
          multiple: dep.multiple === true,
        },
      )
      : (dep.multiple === true ? [] : null)
    const firstRow = Array.isArray(autofill) ? (autofill[0] ?? null) : autofill
    if (firstRow) lookupSelectedRows[dep.field] = firstRow
    else delete lookupSelectedRows[dep.field]
    if (!nextModel) nextModel = { ...previewModel.value }
    nextModel[dep.field] = autofill
    onPreviewFieldChange([dep.rule], dep.field, autofill)
  }
  if (nextModel) emit('update:previewData', nextModel)
}

function notifyLookupChange(field: string, row: Record<string, unknown> | null) {
  if (row) lookupSelectedRows[field] = row
  else delete lookupSelectedRows[field]
  applyCascadeAutofillFromParent(field, row)
}

function filterFor(
  base: import('@/utils/lookupFilterConditions').LookupFilterCondition[],
  derivedFrom: import('@/utils/lookupCascade').LookupDerivedFrom | undefined,
) {
  if (!derivedFrom?.parentField) return base
  const parentRow = lookupSelectedRows[derivedFrom.parentField] ?? null
  return buildDerivedFilterConditions(base, { derivedFrom, filterConditions: base }, parentRow)
}

if (!parentCascade) {
  provide(PREVIEW_LOOKUP_CASCADE_KEY, {
    lookupSelectedRows,
    filterFor,
    notifyLookupChange,
  })
}

function effectivePreviewLookupFilterConditions(
  item: Extract<FormPreviewItem, { kind: 'lookup' }>,
): import('@/utils/lookupFilterConditions').LookupFilterCondition[] {
  const base = item.filterConditions || []
  return (parentCascade?.filterFor ?? filterFor)(base, item.derivedFrom)
}

/** Join columns that must exist on mock rows for cascade filter/autofill to demonstrate. */
function ensureMockFieldsForLookup(
  item: Extract<FormPreviewItem, { kind: 'lookup' }>,
): string[] {
  const fields = new Set<string>()
  for (const j of item.derivedFrom?.joins || []) {
    if (j.toColumn) fields.add(j.toColumn)
  }
  for (const dep of collectLookupPreviewItems(props.items)) {
    if (dep.derivedFrom?.parentField !== item.field) continue
    for (const j of dep.derivedFrom.joins || []) {
      if (j.fromColumn) fields.add(j.fromColumn)
    }
  }
  return Array.from(fields)
}


function updateTableRows(
  bindingId: number,
  rows: any[],
  sourceRule?: Record<string, unknown>,
) {
  emit('update:previewTableRows', {
    ...props.previewTableRows,
    [bindingId]: rows,
  })
  if (isMyRequestsPreview.value) return
  if (!sourceRule) return
  const fieldKey = subTableComponentEventFieldKey(bindingId)
  dispatchPreviewFieldValueChange([sourceRule], fieldKey, rows, previewModel, {
    requestIdConfig: props.requestIdConfig,
    requestIdRecompute: recomputeRequestId,
    visibility: previewVisibilityBridge(),
  })
}

/** Live-compute the readonly Request ID from configured fields + separator (preview has no backend). */
function recomputeRequestId(model: Record<string, any>): string | undefined {
  const cfg = props.requestIdConfig
  if (!cfg || !Array.isArray(cfg.fieldNames) || cfg.fieldNames.length === 0) return undefined
  const parts = cfg.fieldNames
    .map((name) => {
      const v = model[name]
      return v == null ? '' : String(v).trim()
    })
    .filter((s) => s !== '') // skip empty fields, mirrors backend RequestIdEnricher
  return parts.length ? parts.join(cfg.separator ?? '') : ''
}

function onPreviewFieldChange(segmentRules: unknown[], field: string, value: unknown) {
  if (!field || isMyRequestsPreview.value) return
  dispatchPreviewFieldValueChange(segmentRules, field, value, previewModel, {
    requestIdConfig: props.requestIdConfig,
    requestIdRecompute: recomputeRequestId,
    visibility: previewVisibilityBridge(),
  })
}

function onLookupPreviewChange(
  item: Extract<FormPreviewItem, { kind: 'lookup' }>,
  value: unknown,
) {
  if (!item.field || isMyRequestsPreview.value) return
  onPreviewFieldChange([item.rule], item.field, value)
  // Single-select: model value is the row object → drive cascade here.
  // Multi-select: model value is PK[]; cascade parent row comes from @select/@clear.
  if (item.multiple) return
  const row = normalizeLookupRow(value)
  if (parentCascade) parentCascade.notifyLookupChange(item.field, row)
  else notifyLookupChange(item.field, row)
}

function onLookupPreviewSelect(
  item: Extract<FormPreviewItem, { kind: 'lookup' }>,
  row: Record<string, unknown>,
) {
  if (!item.field || isMyRequestsPreview.value || !item.multiple) return
  if (parentCascade) parentCascade.notifyLookupChange(item.field, row)
  else notifyLookupChange(item.field, row)
}

function onLookupPreviewClear(item: Extract<FormPreviewItem, { kind: 'lookup' }>) {
  if (!item.field || isMyRequestsPreview.value) return
  if (parentCascade) parentCascade.notifyLookupChange(item.field, null)
  else notifyLookupChange(item.field, null)
}

function mergePrimaryFormData(patch: Record<string, unknown>) {
  emit('update:previewData', { ...props.previewData, ...patch })
}
</script>

<style scoped lang="scss">
@import '@/styles/form-readonly.scss';

/*
 * Inline Form preview: labelled boundary around the embedded sub-table's fields, so they are
 * not mistaken for host-form fields. Kept visually identical to the portal's framed variant
 * (SubTableInlineForm.is-framed) — design parity is the whole point of Preview.
 */
.inline-sub-form-preview {
  margin: 8px 0 16px;
  padding: 0 0 4px;
  /* Neutral grey only: the brand accent here is red, which reads as an error state on a
     block that is merely a grouping boundary. */
  border: 1px solid var(--el-border-color, #dcdfe6);
  border-radius: 4px;
  background: var(--el-fill-color-blank, #fff);
}

.inline-sub-form-preview__header {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 12px;
  margin-bottom: 8px;
  border-bottom: 1px solid var(--el-border-color-lighter, #ebeef5);
  background: var(--el-fill-color-light, #f5f7fa);
  border-radius: 3px 3px 0 0;
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-regular, #606266);
}

.inline-sub-form-preview__icon {
  color: var(--el-text-color-secondary, #909399);
}

.inline-sub-form-preview :deep(.form-create) {
  padding: 0 12px;
}

.form-preview-wrapper {
  :deep(.form-create) {
    width: 100%;
  }

  /* form-create elCard rules stay inside fields segments — gap matches Portal FormRenderer */
  :deep(.el-card) {
    margin-bottom: 10px;
  }

  :deep(.el-form-item) {
    display: flex !important;
    align-items: flex-start !important;
    margin-bottom: 18px;
  }

  // label 不折行；保留 label-width 统一宽度使各行输入框左对齐，超长时撑开
  :deep(.el-form-item__label) {
    white-space: nowrap !important;
    min-width: max-content !important;
    max-width: none !important;
    height: auto !important;
    line-height: 1.5 !important;
    padding-top: 6px;
  }

  :deep(.el-input),
  :deep(.el-select),
  :deep(.el-date-picker),
  :deep(.el-textarea) {
    width: 100%;
  }

  :deep(.el-button) {
    margin-right: 10px;
  }

  /* index.scss hides .el-form-item__error globally for the designer canvas — re-enable in preview */
  :deep(.el-form-item__error) {
    display: block !important;
    position: static;
  }

  :deep(.el-form-item.is-error .el-input__wrapper),
  :deep(.el-form-item.is-error .el-textarea__inner) {
    box-shadow: 0 0 0 1px var(--el-color-danger) inset;
  }
}

.sub-table-form-preview-tabs {
  width: 100%;

  :deep(.el-tabs__content) {
    padding-top: 10px;
  }
}

.sub-table-preview-item {
  margin-top: 16px;
  margin-bottom: 8px;
}

.sub-preview-header {
  display: flex;
  align-items: center;
  margin-bottom: 8px;
}

.sub-preview-title {
  margin-left: 8px;
  font-weight: 500;
}

.sub-table-preview-empty {
  border: 1px solid #e6e6e6;
  border-radius: 4px;
}

.form-preview-card {
  margin-bottom: 10px;
}

.form-preview-card-title {
  font-weight: 500;
}

.relation-preview-wrapper {
  margin: -4px 0 16px 0;
}

.relation-preview-table {
  width: 100%;

  :deep(tr) {
    background-color: #f5f7fa !important;
  }

  :deep(td.el-table__cell) {
    background-color: #f5f7fa !important;
  }
}
</style>
