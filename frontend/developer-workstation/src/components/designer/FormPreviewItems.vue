<template>
  <template
    v-for="(item, idx) in items"
    :key="idx"
  >
    <div
      v-if="item.kind === 'fields'"
      class="form-preview-wrapper"
    >
      <form-create
        v-if="item.rule.length"
        :key="'preview-form-' + item.modelKey"
        v-model="previewModel"
        locale="en"
        :rule="item.rule"
        :option="previewOption"
      />
    </div>

    <div
      v-else-if="item.kind === 'subTable' && item.binding.columns?.length && isDualPortalSubTablePreview(item.binding)"
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
        >
          <SubTableField
            :config="{ title: item.binding.tableName, columns: item.binding.columns }"
            :model-value="previewTableRows[item.binding.bindingId]"
            :editable="true"
            :form-rule="item.binding.rule"
            :form-option="item.binding.option"
            :preview-show-form-below="item.binding.portalViews?.assigneeTodo === 'formBelowTable'"
            :preview-lookup-compact="false"
            @update:model-value="(rows: any[]) => updateTableRows(item.binding.bindingId, rows)"
          />
        </el-tab-pane>
        <el-tab-pane
          :label="t('form.portalViews.myRequestsDisplay')"
          name="myRequest"
        >
          <SubTableField
            :config="{ title: item.binding.tableName, columns: item.binding.columns }"
            :model-value="previewTableRows[item.binding.bindingId]"
            :editable="true"
            :form-rule="item.binding.rule"
            :form-option="item.binding.option"
            :preview-show-form-below="false"
            :preview-lookup-compact="initiatorPreviewIsSummary(item.binding)"
            @update:model-value="(rows: any[]) => updateTableRows(item.binding.bindingId, rows)"
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
        v-if="item.binding.columns && item.binding.columns.length"
        :config="{ title: item.binding.tableName, columns: item.binding.columns }"
        :model-value="previewTableRows[item.binding.bindingId]"
        :editable="true"
        :form-rule="item.binding.rule"
        :form-option="item.binding.option"
        @update:model-value="(rows: any[]) => updateTableRows(item.binding.bindingId, rows)"
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
      class="relation-preview-wrapper"
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
        @update:preview-table-rows="emit('update:previewTableRows', $event)"
      />
    </el-card>
  </template>
</template>

<script setup lang="ts">
import { computed, reactive } from 'vue'
import { useI18n } from 'vue-i18n'
import SubTableField from './SubTableField.vue'
import LookupPreview from './LookupPreview.vue'
import type { FormPreviewItem } from './formPreviewTypes'
import {
  initiatorPreviewIsSummary,
  isDualPortalSubTablePreview,
} from './formPreviewTypes'

defineOptions({ name: 'FormPreviewItems' })

const props = defineProps<{
  items: FormPreviewItem[]
  previewData: Record<string, any>
  previewOption: Record<string, any>
  previewTableRows: Record<number, any[]>
}>()

const emit = defineEmits<{
  (e: 'update:previewData', value: Record<string, any>): void
  (e: 'update:previewTableRows', value: Record<number, any[]>): void
}>()

const { t } = useI18n()

/** Active tab per item index for dual To Do / My Requests sub-table form preview */
const subTableFormPreviewTab = reactive<Record<number, string>>({})

function subTableFormPreviewTabModel(idx: number): string {
  return subTableFormPreviewTab[idx] ?? 'todo'
}
function setSubTableFormPreviewTabModel(idx: number, name: string | number) {
  subTableFormPreviewTab[idx] = String(name)
}

const previewModel = computed({
  get: () => props.previewData,
  set: (value: Record<string, any>) => emit('update:previewData', value),
})

function updateTableRows(bindingId: number, rows: any[]) {
  emit('update:previewTableRows', {
    ...props.previewTableRows,
    [bindingId]: rows,
  })
}
</script>

<style scoped lang="scss">
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
