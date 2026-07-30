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

    <div
      v-else-if="item.kind === 'subTable' && isPreviewSubTableVisible(item) && hasSubTablePreviewSurface(item.binding) && isDualPortalSubTablePreview(item.binding)"
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
      <el-tabs
        :model-value="subTableFormPreviewTabModel(idx)"
        class="sub-table-form-preview-tabs"
        @update:model-value="setSubTableFormPreviewTabModel(idx, $event)"
      >
        <el-tab-pane
          :label="t('form.portalViews.toDoDisplay')"
          name="todo"
          lazy
        >
          <SubTableField
            :config="{ title: item.binding.tableName, columns: item.binding.columns, tableId: item.binding.tableId, fieldDefinitions: item.binding.fieldDefinitions, bindingLinkMode: item.binding.bindingLinkMode, bindingForeignKeyField: item.binding.bindingForeignKeyField }"
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
            :preview-inline-form-rule="inlineFormBelowForBinding(item.binding).rule"
            :preview-inline-form-option="inlineFormBelowForBinding(item.binding).option"
            :preview-show-form-below="item.binding.portalViews?.assigneeTodo === 'formBelowTable'"
            :preview-link-form-scroll-to-inline="item.binding.portalViews?.assigneeTodo === 'formBelowTable'"
            :preview-lookup-compact="false"
            @update:model-value="(rows: any[]) => updateTableRows(item.binding.bindingId, rows, item.sourceRule)"
            @update:primary-form-data="mergePrimaryFormData"
          />
        </el-tab-pane>
        <el-tab-pane
          :label="t('form.portalViews.myRequestsDisplay')"
          name="myRequest"
          lazy
        >
          <SubTableField
            :config="{ title: item.binding.tableName, columns: item.binding.columns, tableId: item.binding.tableId, fieldDefinitions: item.binding.fieldDefinitions, bindingLinkMode: item.binding.bindingLinkMode, bindingForeignKeyField: item.binding.bindingForeignKeyField }"
            :model-value="previewTableRows[item.binding.bindingId]"
            :editable="false"
            :form-rule="item.binding.rule"
            :form-option="item.binding.option"
            :primary-form-data="previewModel"
            :function-unit-id="functionUnitId"
            :primary-table-display-name="primaryTableDisplayName"
            :primary-table-id="primaryTableId"
            :parent-tables-by-id="parentTablesById"
            :preview-table-bindings="previewTableBindings"
            :preview-show-form-below="false"
            :preview-lookup-compact="initiatorPreviewIsSummary(item.binding)"
            @update:model-value="(rows: any[]) => updateTableRows(item.binding.bindingId, rows, item.sourceRule)"
            @update:primary-form-data="mergePrimaryFormData"
          />
        </el-tab-pane>
      </el-tabs>
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
        :config="{ title: item.binding.tableName, columns: item.binding.columns || [], tableId: item.binding.tableId, fieldDefinitions: item.binding.fieldDefinitions, bindingLinkMode: item.binding.bindingLinkMode, bindingForeignKeyField: item.binding.bindingForeignKeyField }"
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
import { computed, inject, provide, reactive, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  PREVIEW_MY_REQUESTS_ACTIVE_KEY,
  PREVIEW_RESOLVE_SUBTABLE_FORM_KEY,
} from './previewSubTableDialog'
import { PREVIEW_LOOKUP_CASCADE_KEY } from './previewLookupCascade'
import SubTableField from './SubTableField.vue'
import LookupPreview from './LookupPreview.vue'
import type { FormPreviewItem, PreviewSubTableBinding } from './formPreviewTypes'
import {
  hasSubTablePreviewSurface,
  initiatorPreviewIsSummary,
  isDualPortalSubTablePreview,
  resolvePreviewInlineFormBelowDesign,
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

const resolveSubTableFormDesign = inject(PREVIEW_RESOLVE_SUBTABLE_FORM_KEY, undefined)
const previewMyRequestsGlobal = inject(PREVIEW_MY_REQUESTS_ACTIVE_KEY, undefined)

/** Active tab per item index for dual To Do / My Requests sub-table form preview */
const subTableFormPreviewTab = reactive<Record<number, string>>({})

function subTableFormPreviewTabModel(idx: number): string {
  return subTableFormPreviewTab[idx] ?? 'todo'
}
function setSubTableFormPreviewTabModel(idx: number, name: string | number) {
  subTableFormPreviewTab[idx] = String(name)
}

/** My Requests tab active on a dual-portal sub-table in this preview tree. */
const myRequestsPreviewActive = computed(() =>
  Object.values(subTableFormPreviewTab).some((tab) => tab === 'myRequest'),
)

watch(
  myRequestsPreviewActive,
  (active) => {
    if (previewMyRequestsGlobal) previewMyRequestsGlobal.value = active
  },
  { immediate: true },
)

const isMyRequestsPreview = computed(
  () => myRequestsPreviewActive.value || previewMyRequestsGlobal?.value === true,
)

const previewModel = computed({
  get: () => props.previewData,
  set: (value: Record<string, any>) => emit('update:previewData', value),
})

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

function inlineFormBelowForBinding(binding: PreviewSubTableBinding) {
  return resolvePreviewInlineFormBelowDesign(binding, resolveSubTableFormDesign)
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
