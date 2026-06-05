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
        v-if="item.rule.length"
        :key="'preview-form-' + item.modelKey + (isMyRequestsPreview ? '-ro' : '-ed')"
        v-model="previewModel"
        locale="en"
        :rule="item.rule"
        :option="effectivePreviewOption"
        @change="(field: string, value: unknown) => onPreviewFieldChange(item.rule, field, value)"
      />
    </div>

    <div
      v-else-if="item.kind === 'subTable' && hasSubTablePreviewSurface(item.binding) && isDualPortalSubTablePreview(item.binding)"
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
            @update:model-value="(rows: any[]) => updateTableRows(item.binding.bindingId, rows)"
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
            @update:model-value="(rows: any[]) => updateTableRows(item.binding.bindingId, rows)"
            @update:primary-form-data="mergePrimaryFormData"
          />
        </el-tab-pane>
      </el-tabs>
    </div>

    <div
      v-else-if="item.kind === 'subTable'"
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
        :form-rule="item.binding.rule"
        :form-option="item.binding.option"
        :primary-form-data="previewModel"
        :function-unit-id="functionUnitId"
        :primary-table-display-name="primaryTableDisplayName"
        :primary-table-id="primaryTableId"
        :parent-tables-by-id="parentTablesById"
        :preview-table-bindings="previewTableBindings"
        :preview-lookup-compact="isMyRequestsPreview"
        @update:model-value="(rows: any[]) => updateTableRows(item.binding.bindingId, rows)"
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
      v-else-if="item.kind === 'lookup'"
      class="lookup-preview-item"
    >
      <LookupPreview
        :label="item.label"
        :placeholder="item.placeholder"
        :search-fields="item.searchFields"
        :display-fields="item.displayFields"
        :selected-display-field="item.selectedDisplayField"
        :filter-conditions="item.filterConditions || []"
        :view-fields="item.viewFields"
        :field-defs="item.fieldDefs"
        :show-backfill-view="item.showBackfillView !== false"
        :readonly="isMyRequestsPreview || item.readonly === true"
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
import { computed, inject, reactive, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  PREVIEW_MY_REQUESTS_ACTIVE_KEY,
  PREVIEW_RESOLVE_SUBTABLE_FORM_KEY,
} from './previewSubTableDialog'
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
  collectFieldComponentEventsFromRules,
  runComponentFieldEventsOnValueChange,
} from '@/utils/formCreateComponentEvents'
import { createPortalFormApi } from '@/utils/formCreateEventRuntime'

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

const effectivePreviewOption = computed(() => {
  if (!isMyRequestsPreview.value) return props.previewOption
  const baseForm =
    props.previewOption.form && typeof props.previewOption.form === 'object'
      ? props.previewOption.form
      : {}
  return {
    ...props.previewOption,
    form: {
      ...baseForm,
      disabled: true,
    },
  }
})

const previewModel = computed({
  get: () => props.previewData,
  set: (value: Record<string, any>) => emit('update:previewData', value),
})

function inlineFormBelowForBinding(binding: PreviewSubTableBinding) {
  return resolvePreviewInlineFormBelowDesign(binding, resolveSubTableFormDesign)
}

function updateTableRows(bindingId: number, rows: any[]) {
  emit('update:previewTableRows', {
    ...props.previewTableRows,
    [bindingId]: rows,
  })
}

function onPreviewFieldChange(segmentRules: unknown[], field: string, value: unknown) {
  if (!field || isMyRequestsPreview.value) return
  const patch = { [field]: value }
  previewModel.value = { ...previewModel.value, ...patch }
  const api = createPortalFormApi(
    () => previewModel.value,
    (p) => {
      previewModel.value = { ...previewModel.value, ...p }
    },
  )
  const ev = collectFieldComponentEventsFromRules(segmentRules).get(field)
  runComponentFieldEventsOnValueChange(ev, {
    field,
    value,
    api,
    onEvent: 'change',
    hookEvent: 'value',
    fieldType: ev?.rule?.type != null ? String(ev.rule.type) : undefined,
  })
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

  :deep(.el-form-item) {
    display: flex !important;
    align-items: flex-start !important;
    margin-bottom: 18px;
  }

  :deep(.el-form-item__label) {
    white-space: nowrap !important;
    width: auto !important;
    min-width: fit-content !important;
    max-width: 200px !important;
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
  margin-bottom: 16px;
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
