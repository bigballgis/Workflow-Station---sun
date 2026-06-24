<template>
  <div class="relation-table-view sub-table-list-view">
    <!-- Left: Table columns and extend action panel -->
    <div
      v-if="columnsPanelOpen"
      class="columns-panel"
    >
      <div class="panel-section">
        <div class="columns-panel-header">
          <div class="columns-panel-title">
            <el-icon style="margin-right: 6px;">
              <Menu />
            </el-icon>
            <span>{{ t('subTableView.tableColumns') }}</span>
          </div>
          <el-icon
            class="columns-panel-close"
            @click="columnsPanelOpen = false"
          >
            <Close />
          </el-icon>
        </div>
        <div class="columns-panel-table-name">
          {{ binding.tableName }}
        </div>
        <div class="columns-panel-search">
          <el-input
            v-model="fieldSearchKeyword"
            placeholder="Search"
            clearable
            size="small"
          >
            <template #prefix>
              <el-icon><Search /></el-icon>
            </template>
          </el-input>
        </div>
        <div
          v-loading="loadingFields"
          class="columns-field-list"
        >
          <div
            v-for="field in filteredAvailableFields"
            v-if="!loadingFields"
            :key="field.fieldName"
            class="field-item"
            :class="{ active: isFieldInView(field.fieldName), dragging: dragSourceKey === field.fieldName }"
            draggable="true"
            @dragstart="onFieldDragStart($event, field)"
            @dragend="onDragEnd"
            @click="addFieldToView(field)"
          >
            <el-icon class="field-icon">
              <component :is="getFieldIcon(field.dataType)" />
            </el-icon>
            <span class="field-name">{{ field.displayName || field.fieldName }}</span>
          </div>
          <el-empty
            v-if="!loadingFields && filteredAvailableFields.length === 0"
            description="No fields"
            :image-size="40"
          />
        </div>
      </div>

      <div class="panel-section extend-action-section">
        <div class="columns-panel-header">
          <div class="columns-panel-title">
            <el-icon style="margin-right: 6px;">
              <Operation />
            </el-icon>
            <span>{{ t('subTableView.extendAction') }}</span>
          </div>
        </div>
        <div class="columns-field-list extend-action-list">
          <div
            class="field-item link-form-item"
            :class="{ dragging: dragSourceKey === genericLinkFormKey }"
            draggable="true"
            @dragstart="onLinkFormDragStart($event)"
            @dragend="onDragEnd"
            @click="addLinkFormToView"
          >
            <el-icon class="field-icon">
              <Link />
            </el-icon>
            <span class="field-name">Link Form</span>
          </div>
          <div
            class="field-item lookup-action-item"
            :class="{ dragging: dragSourceKey === genericLookupKey }"
            draggable="true"
            @dragstart="onLookupDragStart($event)"
            @dragend="onDragEnd"
            @click="addLookupToView"
          >
            <el-icon class="field-icon">
              <Search />
            </el-icon>
            <span class="field-name">Lookup</span>
          </div>
        </div>
      </div>
    </div>

    <!-- Toggle button when collapsed -->
    <div
      v-else
      class="columns-toggle"
      @click="columnsPanelOpen = true"
    >
      <el-icon><DArrowRight /></el-icon>
    </div>

    <div class="list-view-workspace">
      <!-- Right: Data grid -->
      <div
        class="data-grid-panel"
        @dragover.prevent="onGridDragOver"
        @drop="onGridDrop"
      >
        <!-- Toolbar with Preview and Clear -->
        <div
          v-if="viewColumns.length > 0"
          class="grid-toolbar"
        >
          <div class="toolbar-left">
            <span class="field-count">{{ viewColumns.length }} {{ t('subTableView.columns') }}</span>
          </div>
          <div class="toolbar-right">
            <el-button
              size="small"
              @click="handlePreview"
            >
              {{ t('common.preview') }}
            </el-button>
            <el-button
              size="small"
              type="danger"
              plain
              @click="handleClear"
            >
              {{ t('common.clear') }}
            </el-button>
          </div>
        </div>

        <!-- Column headers + mock row: single preview, or dual To Do / My Requests -->
        <template v-if="viewColumns.length > 0 && !dualPortalListPreview">
          <div class="column-headers">
            <div
              v-for="(column, index) in viewColumns"
              :key="getColumnKey(column)"
              class="column-header"
              :class="{ 'drag-over': dragOverIndex === index, 'link-column': isLinkColumn(column) }"
              draggable="true"
              @dragstart="onColDragStart($event, index)"
              @dragover.prevent="onColDragOver($event, index)"
              @dragleave="onColDragLeave"
              @drop.stop="onColDrop($event, index)"
              @dragend="onColDragEnd"
            >
              <span class="col-name">{{ getColumnLabel(column) }}</span>
              <span class="col-actions">
                <el-icon
                  v-if="isConfigurableActionColumn(column)"
                  class="col-edit"
                  @click.stop="openActionColumnConfig(column, index)"
                ><EditPen /></el-icon>
                <el-icon
                  class="col-remove"
                  @click.stop="removeField(index)"
                ><Close /></el-icon>
              </span>
            </div>
          </div>
          <div class="data-row">
            <div
              v-for="column in viewColumns"
              :key="getColumnKey(column)"
              class="data-cell"
            >
              <el-link
                v-if="isLinkColumn(column)"
                type="primary"
                :underline="false"
                @click.stop="openLinkFormDialog(column)"
              >
                {{ getLinkText(column) }}
              </el-link>
              <LookupPreview
                v-else-if="isLookupColumn(column)"
                class="list-view-lookup-preview"
                :label="''"
                :placeholder="getLookupPreviewConfig(column).placeholder"
                :search-fields="getLookupPreviewConfig(column).searchFields"
                :display-fields="getLookupPreviewConfig(column).displayFields"
                :selected-display-field="getLookupPreviewConfig(column).selectedDisplayField"
                :filter-conditions="getLookupPreviewConfig(column).filterConditions"
                :view-fields="getLookupPreviewConfig(column).viewFields"
                :field-defs="getLookupPreviewConfig(column).fieldDefs"
                :show-backfill-view="getLookupPreviewConfig(column).showBackfillView"
              />
              <span v-else>{{ getMockValue(column) }}</span>
            </div>
          </div>
        </template>

        <div
          v-else-if="viewColumns.length > 0 && dualPortalListPreview"
          class="dual-portal-split"
        >
          <div
            v-for="pane in dualPreviewPanes"
            :key="pane.key"
            class="portal-preview-pane"
          >
            <div class="portal-preview-pane-title">
              {{ pane.title }}
            </div>
            <div class="column-headers">
              <div
                v-for="(column, index) in viewColumns"
                :key="getColumnKey(column) + '-' + pane.key"
                class="column-header"
                :class="{ 'drag-over': dragOverIndex === index, 'link-column': isLinkColumn(column) }"
                draggable="true"
                @dragstart="onColDragStart($event, index)"
                @dragover.prevent="onColDragOver($event, index)"
                @dragleave="onColDragLeave"
                @drop.stop="onColDrop($event, index)"
                @dragend="onColDragEnd"
              >
                <span class="col-name">{{ getColumnLabel(column) }}</span>
                <span class="col-actions">
                  <el-icon
                    v-if="isConfigurableActionColumn(column)"
                    class="col-edit"
                    @click.stop="openActionColumnConfig(column, index)"
                  ><EditPen /></el-icon>
                  <el-icon
                    class="col-remove"
                    @click.stop="removeField(index)"
                  ><Close /></el-icon>
                </span>
              </div>
            </div>
            <div class="data-row">
              <div
                v-for="column in viewColumns"
                :key="getColumnKey(column) + '-' + pane.key + '-cell'"
                class="data-cell"
              >
                <el-link
                  v-if="isLinkColumn(column)"
                  type="primary"
                  :underline="false"
                  @click.stop="openLinkFormDialog(column)"
                >
                  {{ getLinkText(column) }}
                </el-link>
                <LookupPreview
                  v-else-if="isLookupColumn(column)"
                  class="list-view-lookup-preview"
                  :label="''"
                  :placeholder="getLookupPreviewConfig(column).placeholder"
                  :search-fields="getLookupPreviewConfig(column).searchFields"
                  :display-fields="getLookupPreviewConfig(column).displayFields"
                  :selected-display-field="getLookupPreviewConfig(column).selectedDisplayField"
                  :filter-conditions="getLookupPreviewConfig(column).filterConditions"
                  :view-fields="getLookupPreviewConfig(column).viewFields"
                  :field-defs="getLookupPreviewConfig(column).fieldDefs"
                  :show-backfill-view="pane.key === 'initiator' && initiatorIsSummary
                    ? false
                    : (getLookupPreviewConfig(column).showBackfillView !== false)"
                />
                <span v-else>{{ getMockValue(column) }}</span>
              </div>
            </div>
            <div
              v-if="pane.key === 'todo' && assigneeTodoIsFormBelow"
              class="inline-form-below-preview"
            >
              <el-divider content-position="left">
                {{ t('subTableView.assigneeFormBelowDivider') }}
              </el-divider>
              <div class="inline-form-below-body">
                <form-create
                  v-if="inlineFormBelowDesign.rule.length"
                  v-model="inlineFormPreviewData"
                  locale="en"
                  :rule="inlineFormBelowDesign.rule"
                  :option="inlineFormPreviewOption"
                />
                <el-empty
                  v-else
                  :description="t('subTable.noFormDesign')"
                  :image-size="48"
                />
                <div
                  v-if="inlineFormBelowDesign.rule.length"
                  class="inline-form-actions"
                >
                  <el-button
                    type="primary"
                    disabled
                    @click="handleInlineFormBelowPreviewSave"
                  >
                    {{ t('common.save') }}
                  </el-button>
                </div>
              </div>
            </div>
          </div>
        </div>

        <el-empty
          v-if="viewColumns.length === 0"
          :description="t('subTableView.noFieldsImported')"
          :image-size="60"
        />
      </div>
    </div>

    <!-- Preview dialog -->
    <SubTablePreviewDialog
      v-model="showPreview"
      :columns="previewColumns"
      :split-columns="splitPreviewColumns"
    />

    <el-dialog
      v-model="showLinkFormDialog"
      :title="linkFormDialogTitle"
      width="700px"
      destroy-on-close
      :close-on-click-modal="false"
      @closed="handleLinkFormDialogClosed"
    >
      <div
        v-if="selectedSubTableFormDesign.rule && selectedSubTableFormDesign.rule.length"
        class="link-form-dialog-body"
      >
        <form-create
          v-if="formCreateMounted"
          v-model="linkFormData"
          locale="en"
          :rule="selectedSubTableFormDesign.rule"
          :option="linkFormOption"
        />
      </div>
      <el-empty
        v-else
        :description="t('subTable.noFormDesign')"
        :image-size="60"
      />
      <template #footer>
        <el-button @click="showLinkFormDialog = false">
          {{ t('common.cancel') }}
        </el-button>
        <el-button
          type="primary"
          :loading="savingLinkForm"
          @click="handleLinkFormSave"
        >
          {{ t('common.save') }}
        </el-button>
      </template>
    </el-dialog>

    <SubTableColumnConfigDialog
      :show-action-column-config="showActionColumnConfig"
      :editing-action-column-type="editingActionColumnType"
      :link-column-config="linkColumnConfig"
      :lookup-column-config="lookupColumnConfig"
      :sub-table-binding-options="subTableBindingOptions"
      @save="saveActionColumnConfig"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { Search, Close, Menu, DArrowRight, EditPen, Link, Operation } from '@element-plus/icons-vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import SubTablePreviewDialog from './sub-table-list/SubTablePreviewDialog.vue'
import SubTableColumnConfigDialog from './sub-table-list/SubTableColumnConfigDialog.vue'
import LookupPreview from './LookupPreview.vue'
import type {
  SubTableBindingOption,
  SubTableListViewEmit,
  SubTableListViewProps,
} from '@/composables/subTableListView/types'
import { useColumnHelpers } from '@/composables/subTableListView/useColumnHelpers'
import { useViewColumns } from '@/composables/subTableListView/useViewColumns'
import { useColumnDrag } from '@/composables/subTableListView/useColumnDrag'
import { useLinkFormDialog } from '@/composables/subTableListView/useLinkFormDialog'
import { useActionColumnConfig } from '@/composables/subTableListView/useActionColumnConfig'
import { usePortalPreview } from '@/composables/subTableListView/usePortalPreview'

export type { SubTableListColumnDTO } from '@/composables/subTableListView/types'

const { t } = useI18n()

const props = defineProps<SubTableListViewProps>()

const emit = defineEmits<SubTableListViewEmit>()

const columnsPanelOpen = ref(true)
const fieldSearchKeyword = ref('')
const showPreview = ref(false)
/** Dummy model for read-only inline form-below preview (assignee pane). */
const inlineFormPreviewData = ref<Record<string, unknown>>({})

const subTableBindingOptions = computed<SubTableBindingOption[]>(() => {
  if (props.subTableBindings?.length) return props.subTableBindings
  return [{
    bindingId: props.binding.bindingId,
    tableName: props.binding.tableName,
    tableDisplayName: props.binding.tableDisplayName,
    tableId: props.binding.tableId,
    tableDescription: props.binding.tableDescription
  }]
})

// --- Column classification, labels, icons and mock values ---
const {
  isLinkColumn,
  isLookupColumn,
  isConfigurableActionColumn,
  getLinkColumnKey,
  getColumnKey,
  getColumnLabel,
  getLinkText,
  resolveSubTableBindingDisplayName,
  getLinkFormBoundTableName,
  getLookupPreviewConfig,
  getFieldIcon,
  getMockValue,
} = useColumnHelpers({ props, subTableBindingOptions, t })

// --- View columns, available fields, and field/action add/remove/clear ---
const {
  loadingFields,
  genericLinkFormKey,
  genericLookupKey,
  allFields,
  viewColumns,
  loadFields,
  filteredAvailableFields,
  isFieldInView,
  addFieldToView,
  addLinkFormToView,
  addLookupToView,
  removeField,
  handleClear,
} = useViewColumns({
  props,
  emit,
  fieldSearchKeyword,
  isLinkColumn,
  isLookupColumn,
  getLinkColumnKey,
  t,
})

// --- Drag from panel to grid + column reorder ---
const {
  dragSourceKey,
  dragOverIndex,
  onFieldDragStart,
  onLinkFormDragStart,
  onLookupDragStart,
  onDragEnd,
  onGridDragOver,
  onGridDrop,
  onColDragStart,
  onColDragOver,
  onColDragLeave,
  onColDrop,
  onColDragEnd,
} = useColumnDrag({
  emit,
  viewColumns,
  allFields,
  genericLinkFormKey,
  genericLookupKey,
  isFieldInView,
  addLinkFormToView,
  addLookupToView,
})

// --- Link Form dialog (open / save / form-create option / title) ---
const {
  showLinkFormDialog,
  formCreateMounted,
  savingLinkForm,
  linkFormData,
  selectedSubTableFormDesign,
  linkFormOption,
  linkFormDialogTitle,
  openLinkFormDialog,
  handleLinkFormSave,
  handleLinkFormDialogClosed,
} = useLinkFormDialog({ props, getLinkFormBoundTableName, t })

// --- Action column (Link Form / Lookup) config dialog ---
const {
  showActionColumnConfig,
  editingActionColumnType,
  linkColumnConfig,
  lookupColumnConfig,
  openActionColumnConfig,
  saveActionColumnConfig,
} = useActionColumnConfig({
  props,
  emit,
  viewColumns,
  subTableBindingOptions,
  isLookupColumn,
  isConfigurableActionColumn,
  resolveSubTableBindingDisplayName,
  t,
})

// --- User Portal dual-view preview (To Do / My Requests) ---
const {
  inlineFormBelowDesign,
  inlineFormPreviewOption,
  dualPortalListPreview,
  assigneeTodoIsFormBelow,
  initiatorIsSummary,
  dualPreviewPanes,
  previewColumns,
  splitPreviewColumns,
} = usePortalPreview({
  props,
  viewColumns,
  isLinkColumn,
  isLookupColumn,
  getLinkText,
  getColumnLabel,
  getMockValue,
  t,
})

// If parent hasn't populated allFields yet, load from API on mount
onMounted(() => {
  if (!props.availableFields?.length) {
    loadFields()
  }
})

const handlePreview = () => { showPreview.value = true }

/** Portal parity: Save control on assignee form-below-table strip (design-time preview is read-only). */
function handleInlineFormBelowPreviewSave() {
  ElMessage.success(t('common.saveSuccess'))
}

// --- Expose for parent (getters for save) ---
defineExpose({
  getViewFields: () => viewColumns.value,
  getAllFields: () => allFields.value,
  getListColumns: () => viewColumns.value,
  loadFields,
})
</script>

<style scoped>
.sub-table-list-view {
  display: flex;
  height: calc(100vh - 260px);
  border: 1px solid var(--el-border-color-light);
  border-radius: 4px;
  overflow: hidden;
}

.columns-panel {
  width: 240px;
  flex-shrink: 0;
  border-right: 1px solid var(--el-border-color-light);
  display: flex;
  flex-direction: column;
  background: #fff;
}
.panel-section {
  display: flex;
  flex-direction: column;
  min-height: 0;
}
.panel-section:first-child {
  flex: 1;
}
.extend-action-section {
  max-height: 40%;
  border-top: 1px solid var(--el-border-color-light);
}
.columns-panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 12px 8px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}
.columns-panel-title {
  display: flex;
  align-items: center;
  font-weight: 600;
  font-size: 14px;
}
.columns-panel-close { cursor: pointer; color: #999; font-size: 16px; }
.columns-panel-close:hover { color: #333; }
.columns-panel-table-name { padding: 8px 12px 4px; font-size: 13px; color: #666; font-style: italic; }
.columns-panel-search { padding: 4px 8px 8px; }
.columns-field-list { flex: 1; overflow-y: auto; padding: 0 4px; }

.field-item {
  display: flex;
  align-items: center;
  padding: 8px 12px;
  cursor: grab;
  border-radius: 4px;
  font-size: 13px;
  color: #333;
  transition: background 0.15s;
  user-select: none;
}
.field-item:hover { background: #f5f7fa; }
.field-item.active { background: var(--el-color-primary-light-9, #ecf5ff); color: var(--el-color-primary, #409eff); }
.field-item.dragging { opacity: 0.5; }
.field-icon { margin-right: 8px; font-size: 15px; color: #999; }
.field-item.active .field-icon { color: var(--el-color-primary, #409eff); }
.field-name { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.link-form-item { color: var(--el-color-primary, #409eff); }
.extend-action-list { padding-top: 4px; }

.columns-toggle {
  width: 20px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  background: #fafafa;
  border-right: 1px solid var(--el-border-color-light);
  color: #999;
  flex-shrink: 0;
}
.columns-toggle:hover { background: #f0f0f0; color: #333; }

.list-view-workspace {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  overflow: auto;
}

.data-grid-panel {
  min-height: 140px;
  padding: 12px;
  display: flex;
  flex-direction: column;
}

.grid-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}
.toolbar-left { font-size: 13px; color: #666; }
.toolbar-right { display: flex; gap: 8px; }

.column-headers {
  display: flex;
  border: 1px solid var(--el-border-color-light);
  border-bottom: 2px solid var(--el-border-color);
  background: #fafafa;
  min-height: 36px;
}
.column-header {
  flex: 1;
  min-width: 80px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 6px 10px;
  font-size: 13px;
  font-weight: 500;
  color: #333;
  cursor: grab;
  border-right: 1px solid var(--el-border-color-lighter);
  user-select: none;
  transition: background 0.15s;
}
.column-header:last-child { border-right: none; }
.column-header:hover { background: #f0f0f0; }
.column-header.drag-over { background: var(--el-color-primary-light-9, #ecf5ff); border-left: 2px solid var(--el-color-primary, #409eff); }
.column-header.link-column { color: var(--el-color-primary, #409eff); }
.col-name { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.col-remove { font-size: 12px; color: #ccc; cursor: pointer; flex-shrink: 0; margin-left: 4px; }
.col-remove:hover { color: var(--el-color-danger, #f56c6c); }

.data-row {
  display: flex;
  border: 1px solid var(--el-border-color-light);
  border-top: none;
  min-height: 36px;
}
.data-cell {
  flex: 1;
  min-width: 80px;
  display: flex;
  align-items: center;
  padding: 6px 10px;
  font-size: 13px;
  color: #666;
  border-right: 1px solid var(--el-border-color-lighter);
}
.data-cell:last-child { border-right: none; }

.list-view-lookup-preview {
  width: 100%;
  min-width: 220px;
  margin-bottom: 0;
}

.list-view-lookup-preview :deep(.lookup-label-text) {
  display: none;
}

.link-form-dialog-body {
  min-height: 200px;
  max-height: 60vh;
  overflow-y: auto;
}

.dual-portal-split {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.portal-preview-pane {
  border: 1px solid var(--el-border-color-light);
  border-radius: var(--el-border-radius-base);
  background: var(--el-fill-color-blank);
  overflow: hidden;
}

.portal-preview-pane-title {
  padding: 8px 12px;
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-primary);
  background: var(--el-fill-color-light, #f5f7fa);
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.inline-form-below-preview {
  border-top: 1px dashed var(--el-border-color);
  background: var(--el-fill-color-lighter, #fafafa);
}

.inline-form-below-body {
  padding: 0 12px 12px;
  max-height: 320px;
  overflow-y: auto;
}

.inline-form-below-body :deep(.form-create) {
  width: 100%;
}

.inline-form-actions {
  margin-top: 12px;
  display: flex;
  justify-content: flex-end;
  padding-bottom: 8px;
}
</style>
