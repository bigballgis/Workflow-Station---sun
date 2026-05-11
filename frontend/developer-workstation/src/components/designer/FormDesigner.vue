<template>
  <div class="form-designer">
    <!-- Form list view -->
    <FormListSidebar
      v-if="!selectedForm"
      :forms="store.forms"
      :loading="loading"
      :has-tables="store.tables.length > 0"
      :form-type-label="formTypeLabel"
      :get-primary-binding="getPrimaryBinding"
      :get-sub-bindings-count="getSubBindingsCount"
      :get-table-name="getTableName"
      :get-form-bound-nodes="getFormBoundNodes"
      @create="showCreateDialog = true"
      @refresh="loadForms"
      @import-from-table="handleImportFromTable"
      @select-form="handleSelectForm"
      @delete-form="handleDeleteForm"
      @more-action="onFormListMoreAction"
    />

    <!-- Form designer view -->
    <div
      v-else
      class="form-editor-view"
    >
      <div class="editor-header">
        <el-button @click="handleBackToList">
          <el-icon><ArrowLeft /></el-icon> {{ t('form.backToList') }}
        </el-button>
        <span class="form-name">{{ selectedForm.formName }}</span>
        <el-tag
          v-if="selectedForm.boundTableId"
          type="success"
          size="small"
          class="bound-table-tag"
        >
          {{ t('form.boundTableLabel') }}: {{ getTableName(selectedForm.boundTableId) }}
        </el-tag>
        <div
          v-if="getFormBoundNodes(selectedForm.id).length > 0"
          class="bound-nodes-header"
        >
          <el-tag 
            v-for="node in getFormBoundNodes(selectedForm.id)" 
            :key="node.nodeId"
            :type="node.readOnly ? 'info' : 'success'" 
            size="small"
          >
            {{ node.nodeName }}{{ node.readOnly ? `(${t('form.readOnly')})` : '' }}
          </el-tag>
        </div>
        <div class="header-actions">
          <div class="auto-save-status">
            <span
              v-if="autoSaving"
              class="auto-saving"
            >
              <el-icon class="is-loading"><Loading /></el-icon>
              {{ t('form.autoSaving') }}
            </span>
            <span
              v-else-if="lastAutoSaveTime"
              class="auto-saved"
            >
              <el-icon><CircleCheck /></el-icon>
              {{ t('form.autoSaved') }} {{ formatAutoSaveTime(lastAutoSaveTime) }}
            </span>
          </div>
          <el-button
            :disabled="!selectedForm.boundTableId && (!selectedForm.tableBindings || selectedForm.tableBindings.length === 0)"
            @click="handleImportFieldsToDesigner"
          >
            <el-icon><Connection /></el-icon> {{ t('form.importTableFields') }}
          </el-button>
          <el-button @click="openRenameDialog(selectedForm)">
            {{ t('form.renameForm') }}
          </el-button>
          <el-button @click="handleManageBindings(selectedForm)">
            {{ t('form.manageBindings') }}
          </el-button>
          <el-button @click="handleBindNode(selectedForm)">
            {{ t('form.bindProcessNode') }}
          </el-button>
          <el-button @click="handlePreview">
            {{ t('common.preview') }}
          </el-button>
          <el-button
            type="primary"
            @click="handleSaveForm(true)"
          >
            {{ t('common.save') }}
          </el-button>
        </div>
      </div>
      
      <el-tabs
        v-model="activeDesignerTab"
        class="designer-tabs"
        @tab-change="handleTabChange"
      >
        <el-tab-pane name="main">
          <template #label>
            <span>
              <el-tag
                type="primary"
                size="small"
                style="margin-right: 6px;"
              >{{ t('form.mainTable') }}</el-tag>
              {{ selectedForm.formName }}
            </span>
          </template>
          <div class="fc-designer-wrapper">
            <fc-designer
              ref="designerRef"
              :locale="fcDesignerEnLocale"
              :config="designerConfig"
              height="calc(100vh - 260px)"
            />
          </div>
        </el-tab-pane>
        <el-tab-pane
          v-for="(binding, index) in designerSubBindings"
          :key="binding.bindingId"
          :name="String(binding.bindingId)"
        >
          <template #label>
            <span>
              <el-tag
                :type="binding.bindingType === 'SUB' ? 'success' : 'warning'"
                size="small"
                style="margin-right: 6px;"
              >
                {{ binding.bindingType === 'SUB' ? t('tableBinding.subTableType') : t('tableBinding.relationTableType') }}
              </el-tag>
              {{ binding.tableName }}
            </span>
          </template>
          <!-- Relation Table: show data view instead of form designer -->
          <RelationTableView
            v-if="binding.bindingType === 'RELATED'"
            :ref="(el: any) => { if (el) relationTableViewRefs[binding.bindingId] = el }"
            :binding="binding"
            :function-unit-id="props.functionUnitId"
            :form-id="selectedForm!.id"
            :available-fields="relationViewState[binding.bindingId]?.allFields || []"
            :model-value="relationViewState[binding.bindingId]?.viewFields || []"
            @update:model-value="(val: any) => updateRelationViewFields(binding.bindingId, val)"
            @update:available-fields="(val: any) => updateRelationViewAllFields(binding.bindingId, val)"
          />
          <!-- Sub Table: show form designer with List View tab (FORM_ONLY has no list view) -->
          <div
            v-else-if="binding.bindingType === 'SUB'"
            class="sub-table-design-wrapper"
          >
            <el-tabs
              v-model="subTableActiveTab"
              @tab-change="(tabName: any) => handleSubTableInnerTabChange(tabName, binding)"
            >
              <el-tab-pane
                :label="t('subTableView.formDesign')"
                name="form"
              >
                <div class="fc-designer-wrapper">
                  <fc-designer
                    :ref="(el: any) => setSubDesignerRef(el, index)"
                    :locale="fcDesignerEnLocale"
                    :config="designerConfig"
                    height="calc(100vh - 320px)"
                  />
                </div>
              </el-tab-pane>
              <el-tab-pane
                v-if="binding.subMode !== 'FORM_ONLY'"
                :label="t('subTableView.listView')"
                name="listView"
              >
                <SubTableListView
                  :ref="(el: any) => setSubTableListViewRef(el, binding.bindingId)"
                  :binding="binding"
                  :function-unit-id="props.functionUnitId"
                  :form-id="selectedForm!.id"
                  :available-fields="subTableViewState[binding.bindingId]?.allFields || []"
                  :model-value="subTableViewState[binding.bindingId]?.viewFields || []"
                  :link-form-components="linkFormComponents"
                  :sub-table-bindings="designerSubBindings.filter(b => b.bindingType === 'SUB')"
                  :resolve-sub-table-form-design="getSubTableFormDesign"
                  :resolve-lookup-preview-config="resolveLookupPreviewConfig"
                  :form-rule="getSubTableFormRule(binding.bindingId)"
                  :form-option="getSubTableFormOption(binding.bindingId)"
                  @update:model-value="(val: any) => updateSubTableViewFields(binding.bindingId, val)"
                  @update:available-fields="(val: any) => updateSubTableViewAllFields(binding.bindingId, val)"
                  @save="handleSubTableViewSave(binding.bindingId)"
                />
              </el-tab-pane>
            </el-tabs>
          </div>
          <!-- Sub Table (non-SUB fallback, should not happen) -->
          <div
            v-else
            class="fc-designer-wrapper"
          >
            <fc-designer
              :ref="(el: any) => setSubDesignerRef(el, index)"
              :locale="fcDesignerEnLocale"
              :config="designerConfig"
              height="calc(100vh - 260px)"
            />
          </div>
        </el-tab-pane>
      </el-tabs>

      <!-- Field Permission Configuration (TASK forms only) -->
      <div
        v-if="selectedForm.formType === 'TASK'"
        class="field-permission-section"
        style="margin-top: 16px;"
      >
        <el-divider content-position="left">
          {{ t('form.fieldPermission') }}
        </el-divider>
        <el-table
          :data="currentFormFields"
          size="small"
          max-height="300"
          border
        >
          <el-table-column
            prop="field"
            :label="t('form.fieldName')"
            width="200"
          />
          <el-table-column
            prop="title"
            label="Label"
            width="200"
          />
          <el-table-column
            :label="t('form.fieldPermission')"
            width="180"
          >
            <template #default="{ row }">
              <el-select
                :model-value="getFieldPermission(row.field)"
                size="small"
                style="width: 100%"
                @update:model-value="setFieldPermission(row.field, $event)"
              >
                <el-option
                  :label="t('form.fieldPermissionEditable')"
                  value="EDITABLE"
                />
                <el-option
                  :label="t('form.fieldPermissionReadonly')"
                  value="READONLY"
                />
              </el-select>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </div>

    <!-- Create form dialog -->
    <FormCreateDialog
      v-model="showCreateDialog"
      v-model:stage-ids="createFormStageIds"
      :create-form="createForm"
      :forms="store.forms"
      :tables="store.tables"
      :create-dialog-process-nodes="createDialogProcessNodes"
      :table-type-label="tableTypeLabel"
      :handle-create-form-type-change="handleCreateFormTypeChange"
      @confirm="handleCreateForm"
    />

    <!-- Rename form dialog -->
    <FormRenameDialog
      v-model="showRenameDialog"
      v-model:form-name="renameFormName"
      :loading="renaming"
      :title="t('form.renameFormTitle')"
      @confirm="handleConfirmRename"
    />

    <!-- Preview dialog -->
    <el-dialog
      v-model="showPreviewDialog"
      :title="t('form.previewTitle')"
      width="900px"
      destroy-on-close
    >
      <div class="preview-container">
        <FormPreviewItems
          v-if="previewItems.length > 0"
          v-model:preview-data="previewData"
          v-model:preview-table-rows="previewTableRows"
          :items="previewItems"
          :preview-option="previewOption"
        />
        <el-empty
          v-else
          :description="t('form.noFormContent')"
        />
      </div>
    </el-dialog>

    <!-- Bind node dialog -->
    <FormNodeBindDialog
      v-model="showBindDialog"
      :process-nodes="processNodes"
      :bind-dialog-key="bindDialogKey"
      :is-node-selected="isNodeSelected"
      :is-node-read-only="isNodeReadOnly"
      :toggle-node-selection="toggleNodeSelection"
      :set-node-read-only="setNodeReadOnly"
      :node-type-label="nodeTypeLabel"
      @confirm="handleConfirmBind"
    />

    <!-- Import fields from table dialog -->
    <el-dialog
      v-model="showImportFieldsDialog"
      :title="t('form.importFieldsTitle')"
      width="800px"
    >
      <div class="import-fields-dialog">
        <el-alert
          type="info"
          :closable="false"
          style="margin-bottom: 16px;"
        >
          {{ t('form.importFieldsHint') }}
          <span
            v-if="formBindings.length > 0"
            style="display: block; margin-top: 4px;"
          >
            {{ t('form.importFieldsHintWithBindings', { count: formBindings.length }) }}
          </span>
        </el-alert>
        
        <el-form
          label-width="120px"
          label-position="left"
          style="margin-bottom: 16px;"
        >
          <el-form-item :label="t('form.selectTable')">
            <el-select
              v-model="importTableId"
              :placeholder="t('form.selectTable')"
              style="width: 100%;"
              @change="handleTableChange"
            >
              <el-option-group
                v-if="formBindings.length > 0"
                :label="t('form.boundTables')"
              >
                <el-option 
                  v-for="binding in formBindings" 
                  :key="binding.tableId" 
                  :label="`${binding.tableName || getTableName(binding.tableId)} (${bindingTypeLabel(binding.bindingType)})`" 
                  :value="binding.tableId"
                >
                  <div class="table-option-with-binding">
                    <span>{{ binding.tableName || getTableName(binding.tableId) }}</span>
                    <el-tag
                      size="small"
                      :type="bindingTypeTag(binding.bindingType)"
                    >
                      {{ bindingTypeLabel(binding.bindingType) }}
                    </el-tag>
                  </div>
                </el-option>
              </el-option-group>
              <el-option-group :label="t('form.allTables')">
                <el-option 
                  v-for="table in store.tables" 
                  :key="table.id" 
                  :label="`${table.tableName} (${tableTypeLabel(table.tableType)})`" 
                  :value="table.id" 
                />
              </el-option-group>
            </el-select>
          </el-form-item>
        </el-form>
        
        <div
          v-if="importTableId"
          class="field-selection"
        >
          <div class="field-header">
            <el-checkbox 
              :model-value="isAllFieldsSelected" 
              :indeterminate="isFieldsIndeterminate"
              @change="(val: any) => handleSelectAllFields(!!val)"
            >
              {{ t('form.selectAll') }}
            </el-checkbox>
            <span class="field-count">{{ t('form.selectedCount', { count: selectedImportFields.length, total: availableFields.length }) }}</span>
            <el-tag
              v-if="getImportTableBinding()"
              size="small"
              :type="bindingTypeTag(getImportTableBinding()!.bindingType)"
              style="margin-left: 8px;"
            >
              {{ bindingTypeLabel(getImportTableBinding()!.bindingType) }}
            </el-tag>
          </div>
          
          <el-table
            :data="availableFields"
            size="small"
            max-height="300"
          >
            <el-table-column width="50">
              <template #default="{ row }">
                <el-checkbox 
                  :model-value="isFieldSelected(row.fieldName)"
                  @change="toggleFieldSelection(row)"
                />
              </template>
            </el-table-column>
            <el-table-column
              prop="fieldName"
              :label="t('form.fieldName')"
              width="150"
            />
            <el-table-column
              prop="dataType"
              :label="t('form.dataType')"
              width="100"
            />
            <el-table-column
              :label="t('form.formComponent')"
              width="120"
            >
              <template #default="{ row }">
                <el-tag size="small">
                  {{ getFormComponentType(row.dataType) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column
              v-if="formBindings.length > 0"
              :label="t('form.sourceTable')"
              width="120"
            >
              <template #default>
                <span class="source-table">{{ getTableName(importTableId!) }}</span>
              </template>
            </el-table-column>
            <el-table-column
              prop="description"
              :label="t('table.description')"
              show-overflow-tooltip
            />
            <el-table-column
              prop="nullable"
              :label="t('form.required')"
              width="60"
            >
              <template #default="{ row }">
                <el-tag
                  :type="row.nullable ? 'info' : 'danger'"
                  size="small"
                >
                  {{ row.nullable ? t('form.no') : t('form.yes') }}
                </el-tag>
              </template>
            </el-table-column>
          </el-table>
        </div>
        
        <el-empty
          v-else
          :description="t('form.selectTableFirst')"
        />
      </div>
      <template #footer>
        <el-button @click="showImportFieldsDialog = false">
          {{ t('common.cancel') }}
        </el-button>
        <el-button
          type="primary"
          :disabled="selectedImportFields.length === 0"
          @click="handleConfirmImportFields"
        >
          {{ t('form.importButton', { count: selectedImportFields.length }) }}
        </el-button>
      </template>
    </el-dialog>

    <!-- Manage table bindings dialog -->
    <el-dialog
      v-model="showBindingManagerDialog"
      :title="t('form.manageBindingsTitle')"
      width="700px"
      destroy-on-close
    >
      <TableBindingManager 
        v-if="bindingManagerForm"
        ref="bindingManagerRef"
        :function-unit-id="props.functionUnitId"
        :form-id="bindingManagerForm.id"
        :form-type="bindingManagerForm.formType"
        :tables="store.tables"
        @update="handleBindingUpdate"
      />
      <template #footer>
        <el-button @click="showBindingManagerDialog = false">
          {{ t('form.closeButton') }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, nextTick, computed, provide, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { useFormAutoSave } from '@/composables/modules/useFormAutoSave'
import { useFormLabels } from '@/composables/modules/useFormLabels'
import { useFormActions } from '@/composables/modules/useFormActions'
import { parseLookupConfig, getMockValueForType, derivePreviewColumns } from '@/utils/formPreview'
import { cloneFormRules, injectUploadButtonLabels, mergeLoadedFormOptions, getRuleChildren, collectSubTableRules, isCardRule, getLayoutLabel } from '@/utils/formDesigner'
import { ArrowLeft, Connection, Loading, CircleCheck } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { TabPaneName } from 'element-plus'
import { useFunctionUnitStore } from '@/stores/functionUnit'
import type { FormDefinition, FieldDefinition, TableBinding, BindingType, FormType } from '@/api/functionUnit'
import { functionUnitApi } from '@/api/functionUnit'
import { relationTableBindingApi, type RelationFieldDTO } from '@/api/relationTable'
import TableBindingManager from './TableBindingManager.vue'
import FormRenameDialog from './form-designer/FormRenameDialog.vue'
import FormCreateDialog from './form-designer/FormCreateDialog.vue'
import FormNodeBindDialog from './form-designer/FormNodeBindDialog.vue'
import FormListSidebar from './form-designer/FormListSidebar.vue'
import RelationTableView from './RelationTableView.vue'
import SubTableListView from './SubTableListView.vue'
import FormPreviewItems from './FormPreviewItems.vue'
import type { FormPreviewItem } from './formPreviewTypes'
import { lookupStore } from './lookupStore'
import api from '@/api'
import { BUILT_IN_TEMPLATES, type FormTemplate } from './formTemplates'
import { subTableViewApi, type SubTableFieldDTO, type SubTableViewConfig } from '@/api/subTableView'
import fcDesignerEnLocale from '@form-create/designer/locale/en.js'

const { t } = useI18n()

type SubTableListColumnDTO = SubTableFieldDTO & {
  columnType?: 'field' | 'linkForm' | 'lookup'
  componentId?: number
  linkedFormId?: number
  linkedFormName?: string
  linkText?: string
  columnLabel?: string
  boundSubTableBindingId?: number
  boundSubTableName?: string
  lookupConfig?: string
}
const router = useRouter()

interface ProcessNode {
  id: string
  name: string
  type: string
}

const props = defineProps<{ functionUnitId: number }>()

const store = useFunctionUnitStore()
const loading = ref(false)
const selectedForm = ref<FormDefinition | null>(null)
const designerRef = ref<any>(null)
const showCreateDialog = ref(false)
const showRenameDialog = ref(false)
const showPreviewDialog = ref(false)
const showBindDialog = ref(false)
const renameFormName = ref('')
const renameTargetForm = ref<FormDefinition | null>(null)

// Form CRUD actions composable
const { renaming, handleDeleteForm, handleConfirmRename, handleCopyForm } = useFormActions({
  functionUnitId: props.functionUnitId,
  store: store as any,
  renameTargetForm,
  renameFormName,
  showRenameDialog,
  selectedForm,
  loadForms,
  t,
})
const previewData = ref({})
const previewRule = ref<any[]>([])
const previewSubBindings = ref<Array<{
  bindingId: number
  bindingType: string
  bindingMode: string
  tableName: string
  tableType: string
  tableDescription: string
  rule: any[]
  option?: any
  columns: any[]
  subMode?: string
}>>([])
const previewSubData = ref<Record<number, any>>({})
const previewTableRows = ref<Record<number, any[]>>({})
const autoSaving = ref(false)
const lastAutoSaveTime = ref<Date | null>(null)
// Note: autoSaveTimer, lastDesignerState, pollTimerRef moved to useFormAutoSave composable

// relationViewState must be declared before useFormAutoSave (TDZ)
const relationViewState = ref<Record<number, { allFields: any[]; viewFields: any[] }>>({})

const { formatAutoSaveTime, scheduleAutoSave, setupAutoSavePolling, cleanupAutoSavePolling } = useFormAutoSave({
  selectedForm,
  designerRef,
  handleSaveForm,
  relationViewState,
  t,
  autoSaving,
  lastAutoSaveTime,
})

// Link form components loaded from API (for LinkForm widget binding selection)
const linkFormComponents = ref<Array<{
  id: number
  componentName: string
  linkedFormId: number
  linkedFormName?: string
  displayField?: string
  linkText: string
  columnLabel?: string
  sortOrder: number
}>>([])

// Mixed preview items: alternating form-create rule segments and inline sub-tables
const previewItems = ref<FormPreviewItem[]>([])

// Sub-designer refs (one per non-PRIMARY binding)
const subDesignerRefs = ref<any[]>([])
// Relation table view refs (keyed by bindingId)
const relationTableViewRefs = ref<Record<number, any>>({})
// Sub-table list view refs (keyed by bindingId)
const subTableListViewRefs = ref<Record<number, any>>({})
// Sub-table list view state (keyed by bindingId)
const subTableViewState = ref<Record<number, { allFields: SubTableFieldDTO[]; viewFields: SubTableListColumnDTO[] }>>({})
// In-memory cache: persists sub form rules across tab switches (tabs unmount when not active)
const subFormCache = ref<Record<number, { rule: any[]; options: any }>>({})

function setSubTableListViewRef(el: any, bindingId: number) {
  if (el) {
    subTableListViewRefs.value[bindingId] = el
  } else {
    delete subTableListViewRefs.value[bindingId]
  }
}

function setSubDesignerRef(el: any, index: number) {
  console.log('[FormDesigner] setSubDesignerRef:', { el: !!el, index, binding: designerSubBindings.value[index] })
  if (!el) {
    // Tab is unmounting — snapshot current rule into cache before ref is lost
    const prev = subDesignerRefs.value[index]
    if (prev) {
      const binding = designerSubBindings.value[index]
      if (binding) {
        try {
          subFormCache.value[binding.bindingId] = {
            rule: prev.getRule() || [],
            options: prev.getOption() || {}
          }
          console.log('[FormDesigner] Cached sub form:', { bindingId: binding.bindingId, ruleCount: prev.getRule()?.length })
        } catch {}
      }
    }
  }
  subDesignerRefs.value[index] = el
}

// Active tab: 'main' or bindingId string
const activeDesignerTab = ref<string>('main')

function updateRelationViewFields(bindingId: number, fields: any[]) {
  const existing = relationViewState.value[bindingId] || { allFields: [], viewFields: [] }
  relationViewState.value = { ...relationViewState.value, [bindingId]: { ...existing, viewFields: fields } }
}

function updateRelationViewAllFields(bindingId: number, fields: any[]) {
  const existing = relationViewState.value[bindingId] || { allFields: [], viewFields: [] }
  relationViewState.value = { ...relationViewState.value, [bindingId]: { ...existing, allFields: fields } }
}

// Sub-table list view state management
function updateSubTableViewFields(bindingId: number, fields: SubTableListColumnDTO[]) {
  const existing = subTableViewState.value[bindingId] || { allFields: [], viewFields: [] }
  subTableViewState.value = { ...subTableViewState.value, [bindingId]: { ...existing, viewFields: fields } }
}

function updateSubTableViewAllFields(bindingId: number, fields: SubTableFieldDTO[]) {
  const existing = subTableViewState.value[bindingId] || { allFields: [], viewFields: [] }
  subTableViewState.value = { ...subTableViewState.value, [bindingId]: { ...existing, allFields: fields } }
}

async function handleSubTableViewSave(bindingId: number) {
  const state = subTableViewState.value[bindingId]
  if (!state || !selectedForm.value) return

  const fields = state.viewFields
    .filter(f => !f.columnType || f.columnType === 'field')
    .map((f, index) => ({
    fieldName: f.fieldName,
    displayLabel: f.comment || f.fieldName,
    columnWidth: 150,
    sortOrder: index,
    visible: true
  }))

  try {
    if (fields.length > 0) {
      await subTableViewApi.saveViewConfig(selectedForm.value.id, bindingId, fields)
    }
    await persistSubTableListViewColumns(bindingId, state.viewFields)
  } catch (e) {
    console.error('[FormDesigner] Failed to save sub-table view config:', e)
  }
}

async function persistSubTableListViewColumns(bindingId: number, columns: SubTableListColumnDTO[]) {
  if (!selectedForm.value) return
  const currentConfig = selectedForm.value.configJson || {}
  const subListViews = {
    ...(currentConfig.subListViews || {}),
    [bindingId]: { columns, allowEmptyColumns: columns.length === 0 }
  }
  const nextConfig = { ...currentConfig, subListViews }
  selectedForm.value = { ...selectedForm.value, configJson: nextConfig }
  const updated = await store.updateForm(props.functionUnitId, selectedForm.value.id, {
    formName: selectedForm.value.formName,
    formType: selectedForm.value.formType,
    description: selectedForm.value.description,
    configJson: nextConfig,
    ...(selectedForm.value.formType === 'TASK' && selectedForm.value.fieldPermissions
      ? { fieldPermissions: selectedForm.value.fieldPermissions }
      : {})
  })
  selectedForm.value = {
    ...selectedForm.value,
    configJson: updated.configJson || nextConfig
  }
}

async function loadSubTableViewConfig(bindingId: number, binding: any) {
  if (!selectedForm.value) return

  try {
    // Get or create the view config
    const res = await subTableViewApi.getOrCreateView(selectedForm.value.id, bindingId)
    const config: SubTableViewConfig = res.data

    // Get available fields from the sub-table
    const fieldsRes = await subTableViewApi.getAvailableFields(selectedForm.value.id, bindingId)
    const availableFields: SubTableFieldDTO[] = fieldsRes.data || []

    // Merge view config with available fields
    const viewFieldsMap = new Map(config.viewFields.map(f => [f.fieldName, f]))
    let viewFields: SubTableFieldDTO[] = config.viewFields
      .filter(f => f.visible !== false)
      .sort((a, b) => a.sortOrder - b.sortOrder)
      .map(f => {
        const available = availableFields.find(af => af.fieldName === f.fieldName)
        return {
          fieldName: f.fieldName,
          dataType: available?.dataType || 'VARCHAR',
          comment: f.displayLabel || f.fieldName,
        } as SubTableFieldDTO
      })
    if (viewFields.length === 0 && availableFields.length > 0) {
      viewFields = availableFields.map(field => ({ ...field, columnType: 'field' as const }))
    }

    const savedListDesigner = (selectedForm.value.configJson?.subListViews || {})[bindingId] || {}
    const mergedViewFields = mergeSubTableListColumns(viewFields, savedListDesigner)
    subTableViewState.value[bindingId] = {
      allFields: availableFields,
      viewFields: mergedViewFields
    }
  } catch (e) {
    console.error('[FormDesigner] Failed to load sub-table view config:', e)
    // Initialize with empty state
    const savedListDesigner = (selectedForm.value.configJson?.subListViews || {})[bindingId] || {}
    const mergedViewFields = mergeSubTableListColumns([], savedListDesigner)
    subTableViewState.value[bindingId] = {
      allFields: [],
      viewFields: mergedViewFields
    }
  }
}

function mergeSubTableListColumns(
  viewFields: SubTableFieldDTO[],
  savedListConfig: any
): SubTableListColumnDTO[] {
  const fieldColumns = viewFields.map(field => ({ ...field, columnType: 'field' as const }))
  const savedColumns = Array.isArray(savedListConfig?.columns) ? savedListConfig.columns : []
  if (savedColumns.length > 0) {
    const fieldByName = new Map(fieldColumns.map(field => [field.fieldName, field]))
    const mergedColumns = savedColumns
      .map((column: any) => {
        if (column?.columnType === 'linkForm') return hydrateLinkFormColumn(column)
        if (column?.columnType === 'lookup') return hydrateLookupColumn(column)
        const field = fieldByName.get(column?.fieldName)
        if (field) {
          return { ...field, comment: column.comment || column.displayLabel || field.comment }
        }
        // Keep columns that exist only in configJson (e.g. server dw_sub_table view config was cleared
        // or field names temporarily out of sync) — otherwise merge drops everything and save wipes subListViews.
        if (column?.fieldName) {
          return {
            fieldName: column.fieldName,
            dataType: column.dataType || 'VARCHAR',
            nullable: column.nullable !== false,
            isPrimaryKey: !!column.isPrimaryKey,
            comment: column.displayLabel || column.comment || column.fieldName,
            columnType: 'field' as const,
          } as SubTableListColumnDTO
        }
        return null
      })
      .filter(Boolean) as SubTableListColumnDTO[]
    return mergedColumns
  }

  const legacyRules = Array.isArray(savedListConfig?.rule) ? savedListConfig.rule : []
  const legacyLinkColumns = legacyRules
    .filter((rule: any) => rule?.type === 'linkForm')
    .map((rule: any) => hydrateLinkFormColumn({
      columnType: 'linkForm',
      componentId: rule._componentId ?? rule.props?._componentId
    }))
    .filter((column: SubTableListColumnDTO) => !!column.componentId)

  return [...fieldColumns, ...legacyLinkColumns]
}

function hydrateLookupColumn(column: any): SubTableListColumnDTO {
  return {
    columnType: 'lookup',
    fieldName: column.fieldName || `lookup:${column.bindingId || 'action'}`,
    dataType: 'LOOKUP',
    nullable: true,
    isPrimaryKey: false,
    comment: column.comment || column.columnLabel || 'Lookup',
    columnLabel: column.columnLabel || column.comment || 'Lookup',
    lookupConfig: column.lookupConfig || '{}'
  }
}

function hydrateLinkFormColumn(column: any): SubTableListColumnDTO {
  const componentId = Number(column.componentId)
  const component = linkFormComponents.value.find(c => c.id === componentId)
  return {
    columnType: 'linkForm',
    fieldName: `linkForm:${componentId || column.fieldName || Date.now()}`,
    dataType: 'LINK_FORM',
    nullable: true,
    isPrimaryKey: false,
    componentId,
    linkedFormId: column.linkedFormId ?? component?.linkedFormId,
    linkedFormName: column.linkedFormName ?? component?.linkedFormName,
    comment: column.comment || column.columnLabel || component?.columnLabel || component?.componentName || t('linkForm.defaultLinkText'),
    columnLabel: column.columnLabel ?? component?.columnLabel,
    linkText: column.linkText || component?.linkText || t('linkForm.defaultLinkText'),
    boundSubTableBindingId: column.boundSubTableBindingId,
    boundSubTableName: column.boundSubTableName
  }
}

function getSubTableFormDesign(bindingId: number): { rule: any[]; options: any } {
  const index = designerSubBindings.value.findIndex(b => b.bindingId === bindingId)
  const subRef = index >= 0 ? subDesignerRefs.value[index] : null
  const saved = (selectedForm.value?.configJson?.subForms || {})[bindingId] || {}
  try {
    if (subRef) {
      return {
        rule: subRef.getRule?.() || [],
        options: subRef.getOption?.() || {}
      }
    }
  } catch {}
  return subFormCache.value[bindingId] || { rule: saved.rule || [], options: saved.options || defaultFormOption.value }
}

function getSubTableFormRule(bindingId: number): any[] {
  return getSubTableFormDesign(bindingId).rule
}

function getSubTableFormOption(bindingId: number): any {
  return getSubTableFormDesign(bindingId).options
}

// Non-PRIMARY bindings for tabs（RELATED 用于 Lookup，也需要显示在设计器里配置视图字段）
const designerSubBindings = computed(() => {
  if (!selectedForm.value) return []
  const nonPrimary = (selectedForm.value.tableBindings || []).filter((b: TableBinding) => b.bindingType !== 'PRIMARY')
  return nonPrimary.map((b: TableBinding) => ({
    bindingId: b.id as number,
    bindingType: b.bindingType,
    bindingMode: b.bindingMode,
    tableName: b.tableName || getTableName(b.tableId),
    tableId: b.tableId,
    tableType: (store.tables.find(t => t.id === b.tableId)?.tableType) || (b.bindingType === 'RELATED' ? 'RELATION' : ''),
    tableDescription: (store.tables.find(t => t.id === b.tableId)?.description) || '',
    subMode: b.subMode,
  }))
})

// Provide subBindings to SubTablePlaceholderWidget via inject
// The widget uses inject('designerSubBindings') to get the current list
provide('designerSubBindings', () => designerSubBindings.value.map(b => ({
  id: b.bindingId,
  tableName: b.tableName,
  tableDescription: b.tableDescription,
  bindingType: b.bindingType,
})))

// Provide relation bindings for LookupBindingSelect
provide('designerRelationBindings', () => designerSubBindings.value
  .filter(b => b.bindingType === 'RELATED')
  .map(b => ({
    bindingId: b.bindingId,
    tableName: b.tableName,
    tableDescription: b.tableDescription,
    tableId: b.tableId,
  }))
)

// Provide formId for lookup config components
provide('designerFormId', () => selectedForm.value?.id ?? null)

// Provide link form components for LinkFormBindingSelect
provide('linkFormComponents', () => linkFormComponents.value.map(c => ({
  id: c.id,
  componentName: c.componentName,
  linkedFormName: c.linkedFormName,
})))

// Sync relation bindings and formId to lookupStore for fc-designer property panel components
watch([() => selectedForm.value?.id, designerSubBindings, () => store.tables], () => {
  lookupStore.formId = selectedForm.value?.id ?? null
  lookupStore.relationBindings = designerSubBindings.value
    .filter(b => b.bindingType === 'RELATED')
    .map(b => ({
      bindingId: b.bindingId,
      tableName: b.tableName,
      tableDescription: b.tableDescription,
      tableId: b.tableId,
    }))
  lookupStore.tables = store.tables as any[]
}, { immediate: true })

// Watch for tableBindings changes and log for debugging
watch(() => selectedForm.value?.tableBindings, (newVal) => {
  console.log('[FormDesigner] tableBindings changed:', newVal)
}, { deep: true })

// Watch for selectedForm changes and load linkFormComponents
watch([() => selectedForm.value, () => props.functionUnitId], async ([form, fuId]) => {
  if (form && fuId) {
    await loadLinkFormComponents()
  } else {
    linkFormComponents.value = []
  }
}, { immediate: true })

async function loadLinkFormComponents() {
  try {
    const { linkFormComponentApi } = await import('@/api/linkFormComponent')
    const res = await linkFormComponentApi.getComponents(props.functionUnitId)
    linkFormComponents.value = res.data || []
  } catch (e) {
    console.error('[FormDesigner] failed to load linkFormComponents:', e)
    linkFormComponents.value = []
  }
}


const createForm = reactive({ formName: '', formType: 'PROCESS' as FormType, description: '', boundTableId: null as number | null })
const bindingForm = ref<FormDefinition | null>(null)

// Stage binding state for create dialog (TASK type)
const createFormStageIds = ref<string[]>([])
const createDialogProcessNodes = ref<ProcessNode[]>([])

// Data_Table columns for field name autocomplete/validation
const dataTableColumns = ref<string[]>([])

// Table binding management state
const showBindingManagerDialog = ref(false)
const bindingManagerForm = ref<FormDefinition | null>(null)
const processNodes = ref<ProcessNode[]>([])

// Import fields state
const showImportFieldsDialog = ref(false)
const importTableId = ref<number | null>(null)
const selectedImportFields = ref<FieldDefinition[]>([])
const formBindings = ref<TableBinding[]>([])
// Relation table fields loaded from API (for RELATION type table import)
const relationTableFields = ref<FieldDefinition[]>([])

// Check if the currently selected import table is a RELATION type table
function isImportingRelationTable(): boolean {
  if (!importTableId.value) return false
  // Check store.tables first (for local RELATION type tables)
  const table = store.tables.find(t => t.id === importTableId.value)
  if (table?.tableType === 'RELATION') return true
  // Check formBindings (for deployed relation tables where tableId is from rt_table_definitions)
  const binding = formBindings.value.find(b => b.tableId === importTableId.value)
  return binding?.bindingType === 'RELATED'
}

// Check if the currently selected import table is a SUB type table
function isImportingSubTable(): boolean {
  if (!importTableId.value) return false
  // Check store.tables for SUB type tables
  const table = store.tables.find(t => t.id === importTableId.value)
  if (table?.tableType === 'SUB') return true
  // Check formBindings for SUB type bindings
  const binding = formBindings.value.find(b => b.tableId === importTableId.value)
  return binding?.bindingType === 'SUB'
}

// Get bindingId for a given tableId (for relation table imports)
// Checks formBindings first, then designerSubBindings, then falls back to active tab
function getBindingIdForTable(tableId: number): number | null {
  // Check formBindings (populated in handleImportFieldsToDesigner)
  const fromFormBindings = formBindings.value.find(b => b.tableId === tableId)
  if (fromFormBindings) return fromFormBindings.id as number

  // Check designerSubBindings (from selectedForm.tableBindings)
  const fromSubBindings = designerSubBindings.value.find(b => b.tableId === tableId)
  if (fromSubBindings) return fromSubBindings.bindingId

  // Fallback: if importing from the active RELATED tab, use active tab's bindingId
  if (activeDesignerTab.value !== 'main') {
    return Number(activeDesignerTab.value)
  }

  return null
}

// Computed: available fields for selected table
const availableFields = computed(() => {
  if (!importTableId.value) return []
  // If importing from a relation table, use the fetched relation fields
  if (isImportingRelationTable() && relationTableFields.value.length > 0) {
    return relationTableFields.value
  }
  const table = store.tables.find(t => t.id === importTableId.value)
  return table?.fieldDefinitions || []
})

// Computed: all fields selected
const isAllFieldsSelected = computed(() => {
  return availableFields.value.length > 0 && 
         selectedImportFields.value.length === availableFields.value.length
})

// Computed: indeterminate selection state
const isFieldsIndeterminate = computed(() => {
  return selectedImportFields.value.length > 0 && 
         selectedImportFields.value.length < availableFields.value.length
})
// Store form-node bindings parsed from BPMN XML (supports multiple nodes)
const formNodeBindings = ref<Map<number, Array<{ nodeId: string; nodeName: string; readOnly: boolean }>>>(new Map())

// Selected nodes in bind dialog
const selectedBindNodes = ref<Array<{ nodeId: string; nodeName: string; readOnly: boolean }>>([])
// Key to force checkbox re-render
const bindDialogKey = ref(0)

// Form-create designer config
const designerConfig = computed(() => ({
  showDevice: true,
  showSave: false, // Use custom save button
  fieldReadonly: false,
}))

// Default form options — label left-aligned; locale + language so fcUpload `t('clickToUpload')` is English
const defaultFormOption = computed(() => ({
  form: { labelPosition: 'left' },
  language: {
    en: {
      clickToUpload: t('form.clickToUpload'),
    },
  },
}))

// Sub-table tabs default to form design
const subTableActiveTab = ref('form')

// Preview option: mutable flags + form-create English strings (library defaults to zh-cn without `locale` / `language`)
const previewOptionState = reactive({
  submitBtn: false,
  resetBtn: false,
})
const previewOption = computed(() => ({
  ...previewOptionState,
  language: {
    en: {
      clickToUpload: t('form.clickToUpload'),
    },
  },
}))

const getPreviewOption = (): Record<string, any> => ({
  submitBtn: false,
  resetBtn: false,
  language: {
    en: {
      clickToUpload: t('form.clickToUpload'),
    },
  },
})

/** Deep-clone form rules so we do not mutate Pinia / API payloads in place. */
// Label / type mapping composable
const { formTypeLabel, nodeTypeLabel, tableTypeLabel, bindingTypeLabel, bindingTypeTag, getFormComponentType } = useFormLabels(t)

// Get binding info for the currently selected import table
function getImportTableBinding(): TableBinding | undefined {
  if (!importTableId.value) return undefined
  return formBindings.value.find(b => b.tableId === importTableId.value)
}

/**
 * Generate mock value based on data type for relation table preview
 */
// Pure preview utilities moved to @/utils/formPreview.ts

/**
 * Derive columns from sub-form binding rule (supports all 15 field types)
 */
function deriveColumnsFromBinding(binding: any, subForms?: Record<string, any>) {
  const subFormRule = subForms?.[binding.bindingId]?.rule
  if (subFormRule && Array.isArray(subFormRule) && subFormRule.length > 0) {
    return subFormRule.map((r: any) => {
      const rProps = r.props || {}
      let type: string | undefined
      if (r.type === 'input') {
        if (rProps.type === 'textarea') type = 'textarea'
        else if (rProps.type === 'password') type = 'password'
        else type = 'text'
      }
      else if (r.type === 'inputNumber') type = 'number'
      else if (r.type === 'select') type = 'select'
      else if (r.type === 'radio') type = 'radio'
      else if (r.type === 'switch') type = 'switch'
      else if (r.type === 'datePicker') type = rProps.type === 'datetime' ? 'datetime' : 'date'
      else if (r.type === 'timePicker') type = rProps.isRange === true ? 'timerange' : 'time'
      else if (r.type === 'treeSelect') type = 'treeselect'
      else if (r.type === 'elTreeSelect') type = 'treeselect'
      else if (r.type === 'tree') type = 'tree'
      else if (r.type === 'upload') type = 'upload'
      else if (r.type === 'userSelect' || r.type === 'user') type = 'user'
      else if (r.type === 'departmentSelect' || r.type === 'department') type = 'department'
      else if (r.type === 'colorPicker') type = 'colorPicker'
      else if (r.type === 'rate') type = 'rate'
      else if (r.type === 'slider') type = 'slider'
      else if (r.type === 'editor') type = 'editor'
      else if (r.type === 'signature') type = 'signature'
      else if (r.type === 'transfer') type = 'transfer'
      else if (r.type === 'cascader') type = 'cascader'
      else type = r.type as any
      const rawOptions = r.options || rProps.options
      const options = rawOptions ? (type === 'cascader' ? rawOptions : rawOptions.map((o: any) => ({ label: o.label ?? o.value, value: o.value }))) : undefined
      const passProps: Record<string, any> = {}
      for (const key of [
        'action', 'accept', 'multiple', 'precision', 'min', 'max', 'rows', 'maxlength', 'fileNameTargetField',
        'isRange', 'valueFormat', 'startPlaceholder', 'endPlaceholder', 'treeData', 'checkStrictly',
        'showAlpha', 'allowHalf', 'step', 'cascaderProps', 'leftTitle', 'rightTitle',
      ]) {
        if (rProps[key] !== undefined) passProps[key] = rProps[key]
      }
      if (rProps.data !== undefined) passProps.treeData = rProps.data
      if (rProps.nodeKey !== undefined) passProps.nodeKey = rProps.nodeKey
      if (rProps.showCheckbox !== undefined) passProps.showCheckbox = rProps.showCheckbox
      if (rProps.props !== undefined) passProps.labelProps = rProps.props
      // cascader: map props.props to cascaderProps if not already set
      if (type === 'cascader' && rProps.props && !passProps.cascaderProps) passProps.cascaderProps = rProps.props
      if (type === 'lookup') {
        const lookupPreviewConfig = resolveLookupPreviewConfig(rProps.lookupConfig || '{}')
        passProps.lookupConfig = rProps.lookupConfig || '{}'
        passProps.searchFields = lookupPreviewConfig.searchFields
        passProps.displayFields = lookupPreviewConfig.displayFields
        passProps.selectedDisplayField = lookupPreviewConfig.selectedDisplayField
        passProps.filterConditions = lookupPreviewConfig.filterConditions
        passProps.viewFields = lookupPreviewConfig.viewFields
        passProps.fieldDefs = lookupPreviewConfig.fieldDefs
        passProps.showBackfillView = lookupPreviewConfig.showBackfillView
      }
      if (options) passProps.options = options
      return {
        field: r.field,
        label: r.title || r.field,
        type,
        required: r.validate?.some((v: any) => v.required) || false,
        ...(options ? { options } : {}),
        ...(Object.keys(passProps).length > 0 ? { props: passProps } : {}),
      }
    })
  }
  return []
}

function getRelationFieldDefs(bindingId?: number, config: any = {}) {
  if (!bindingId) return []
  const state = relationViewState.value[bindingId]
  const saved = (config.relationViews || {})[bindingId]
  const fields = state?.allFields || saved?.allFields || []
  if (fields.length) {
    return fields.map((f: any) => ({
      fieldName: f.fieldName,
      dataType: f.dataType,
      comment: f.comment,
      description: f.description || f.comment
    }))
  }

  const binding = designerSubBindings.value.find(b => b.bindingId === bindingId)
  const table = store.tables.find(t => t.id === binding?.tableId)
  return ((table as any)?.fieldDefinitions || []).map((f: any) => ({
    fieldName: f.fieldName,
    dataType: f.dataType,
    comment: f.comment || f.description,
    description: f.description || f.comment
  }))
}

function makeLookupPreviewItem(ruleItem: any, config: any) {
  const previewConfig = resolveLookupPreviewConfig(ruleItem.props?.lookupConfig || '{}', config)
  return {
    kind: 'lookup' as const,
    label: ruleItem.title || 'Lookup',
    placeholder: ruleItem.props?.placeholder || previewConfig.placeholder,
    searchFields: previewConfig.searchFields,
    displayFields: previewConfig.displayFields,
    selectedDisplayField: previewConfig.selectedDisplayField,
    filterConditions: previewConfig.filterConditions,
    viewFields: previewConfig.viewFields,
    fieldDefs: previewConfig.fieldDefs,
    showBackfillView: previewConfig.showBackfillView,
    bindingId: previewConfig.bindingId
  }
}

function resolveLookupPreviewConfig(rawLookupConfig: string, explicitConfig?: any) {
  const config = explicitConfig || selectedForm.value?.configJson || {}
  const lookupConfig = parseLookupConfig(rawLookupConfig)
  const bindingId = lookupConfig.bindingId
  const savedRelationView = bindingId ? (config.relationViews || {})[bindingId] : null
  return {
    placeholder: 'Click to search',
    searchFields: lookupConfig.searchFields || [],
    displayFields: lookupConfig.displayFields || [],
    selectedDisplayField: lookupConfig.selectedDisplayField || lookupConfig.displayField || '',
    filterConditions: Array.isArray(lookupConfig.filterConditions) ? lookupConfig.filterConditions : [],
    viewFields: lookupConfig.showBackfillView === false
      ? []
      : (savedRelationView?.viewFields || relationViewState.value[bindingId]?.viewFields || []),
    fieldDefs: getRelationFieldDefs(bindingId, config),
    showBackfillView: lookupConfig.showBackfillView !== false,
    bindingId
  }
}

function toSubTablePreviewColumns(bindingId: number, rule: any[], config: any) {
  const liveColumns = subTableViewState.value[bindingId]?.viewFields
  const savedColumns = (config.subListViews || {})[bindingId]?.columns
  const listColumns = liveColumns?.length ? liveColumns : savedColumns
  if (Array.isArray(listColumns) && listColumns.length) {
    const ruleByField = new Map((Array.isArray(rule) ? rule : []).map((ruleItem: any) => [ruleItem?.field, ruleItem]))
    return listColumns.map((column: any) => {
      if (column.columnType === 'linkForm') {
        const targetBindingId = column.boundSubTableBindingId || bindingId
        const targetFormDesign = getSubTableFormDesign(targetBindingId)
        const boundSubTableName = column.boundSubTableName
          || designerSubBindings.value.find(b => b.bindingId === targetBindingId)?.tableName
          || ''
        return {
          field: column.fieldName || `linkForm:${column.componentId || bindingId}`,
          label: column.columnLabel || column.comment || column.linkText || t('linkForm.defaultLinkText'),
          type: 'linkForm',
          minWidth: 120,
          props: {
            linkText: column.linkText || t('linkForm.defaultLinkText'),
            formRule: targetFormDesign.rule,
            formOption: targetFormDesign.options,
            boundSubTableName,
          }
        }
      }
      if (column.columnType === 'lookup') {
        const lookupPreviewConfig = resolveLookupPreviewConfig(column.lookupConfig || '{}', config)
        return {
          field: column.fieldName || `lookup:${bindingId}`,
          label: column.columnLabel || column.comment || 'Lookup',
          type: 'lookup',
          minWidth: 260,
          placeholder: lookupPreviewConfig.placeholder,
          props: {
            searchFields: lookupPreviewConfig.searchFields,
            displayFields: lookupPreviewConfig.displayFields,
            selectedDisplayField: lookupPreviewConfig.selectedDisplayField,
            filterConditions: lookupPreviewConfig.filterConditions,
            viewFields: lookupPreviewConfig.viewFields,
            fieldDefs: lookupPreviewConfig.fieldDefs,
            showBackfillView: lookupPreviewConfig.showBackfillView
          }
        }
      }
      const fieldRule = ruleByField.get(column.fieldName)
      if (fieldRule?.type === 'lookup' || fieldRule?.props?.lookupConfig) {
        const lookupPreviewConfig = resolveLookupPreviewConfig(fieldRule.props?.lookupConfig || '{}', config)
        return {
          field: column.fieldName,
          label: column.comment || column.columnLabel || fieldRule.title || column.fieldName,
          type: 'lookup',
          minWidth: 260,
          placeholder: fieldRule.props?.placeholder || lookupPreviewConfig.placeholder,
          props: {
            searchFields: lookupPreviewConfig.searchFields,
            displayFields: lookupPreviewConfig.displayFields,
            selectedDisplayField: lookupPreviewConfig.selectedDisplayField,
            filterConditions: lookupPreviewConfig.filterConditions,
            viewFields: lookupPreviewConfig.viewFields,
            fieldDefs: lookupPreviewConfig.fieldDefs,
            showBackfillView: lookupPreviewConfig.showBackfillView
          }
        }
      }
      return {
        field: column.fieldName,
        label: column.comment || column.columnLabel || column.fieldName,
        type: undefined,
        minWidth: 100
      }
    })
  }

  return deriveColumnsFromBinding({ bindingId }, { [bindingId]: { rule } })
}

/**
 * Derive preview columns for sub-table based on table type
 */
// Preview utilities moved to @/utils/formPreview.ts

/**
 * Get table name by table ID
 */
function getTableName(tableId: number): string {
  const table = store.tables.find(t => t.id === tableId)
  return table?.tableDisplayName || table?.tableName || t('form.unknownTable')
}

/**
 * Get PRIMARY binding for a form
 */
function getPrimaryBinding(form: FormDefinition): TableBinding | undefined {
  return form.tableBindings?.find(b => b.bindingType === 'PRIMARY')
}

/**
 * Get sub/related binding count for a form
 */
function getSubBindingsCount(form: FormDefinition): number {
  return form.tableBindings?.filter(b => b.bindingType !== 'PRIMARY').length || 0
}

/**
 * Open manage table bindings dialog
 */
function handleManageBindings(form: FormDefinition) {
  bindingManagerForm.value = form
  showBindingManagerDialog.value = true
}

/**
 * Table binding update callback
 */
async function handleBindingUpdate() {
  // Reload bindings directly instead of full loadForms
  if (selectedForm.value) {
    try {
      const res = await functionUnitApi.getFormBindings(props.functionUnitId, selectedForm.value.id)
      selectedForm.value = { ...selectedForm.value, tableBindings: res.data || [] }
      console.log('[FormDesigner] handleBindingUpdate - Updated tableBindings:', selectedForm.value.tableBindings)
      console.log('[FormDesigner] handleBindingUpdate - designerSubBindings:', designerSubBindings.value)
      // Reset sub designer state so new tabs render cleanly
      subDesignerRefs.value = []
      subFormCache.value = {}
      // relationViewState is keyed by bindingId — rebuild it for all RELATED bindings so
      // newly added ones get initialised with empty state (rather than undefined → blank view)
      const updated: Record<number, { allFields: any[]; viewFields: any[] }> = {}
      const config = selectedForm.value.configJson || {}
      for (const b of (selectedForm.value.tableBindings || [])) {
        if (b.bindingType === 'RELATED') {
          const id = b.id as number
          const saved = (config.relationViews || {})[id]
          updated[id] = saved
            ? { allFields: saved.allFields || [], viewFields: saved.viewFields || [] }
            : { allFields: [], viewFields: [] }
        }
      }
      relationViewState.value = updated
    } catch (e) {
      console.error('[FormDesigner] Failed to update bindings:', e)
    }
  }
}

/**
 * Check if a field is selected
 */
function isFieldSelected(fieldName: string): boolean {
  return selectedImportFields.value.some(f => f.fieldName === fieldName)
}

/**
 * Toggle field selection
 */
function toggleFieldSelection(field: FieldDefinition) {
  const index = selectedImportFields.value.findIndex(f => f.fieldName === field.fieldName)
  if (index >= 0) {
    selectedImportFields.value.splice(index, 1)
  } else {
    selectedImportFields.value.push({ ...field })
  }
}

/**
 * Select/deselect all fields
 */
function handleSelectAllFields(checked: boolean) {
  if (checked) {
    selectedImportFields.value = availableFields.value.map(f => ({ ...f }))
  } else {
    selectedImportFields.value = []
  }
}

/**
 * Reset selected fields when table changes
 */
async function handleTableChange() {
  selectedImportFields.value = []
  relationTableFields.value = []
  // If the selected table is a RELATION type, fetch its fields from the relation table API
  if (isImportingRelationTable()) {
    try {
      const res = await relationTableBindingApi.getAvailableTables()
      const tables = res.data || []
      // For deployed relation tables, importTableId is the rt_table_definitions ID
      // Try direct ID match first, then fall back to name match
      let rtTable = tables.find((t: any) => t.id === importTableId.value)
      if (!rtTable) {
        const selectedTable = store.tables.find(t => t.id === importTableId.value)
        if (selectedTable) {
          rtTable = tables.find((t: any) => t.tableName === selectedTable.tableName || t.displayName === selectedTable.tableName)
        }
      }
      if (rtTable?.fieldDefinitions) {
        relationTableFields.value = rtTable.fieldDefinitions.map((f: any) => ({
          fieldName: f.fieldName,
          dataType: f.dataType,
          length: f.length,
          precision: f.precision,
          scale: f.scale,
          nullable: f.nullable,
          isPrimaryKey: f.isPrimaryKey,
          defaultValue: f.defaultValue,
          description: f.comment,
        } as FieldDefinition))
      }
    } catch {
      relationTableFields.value = []
    }
  }
}

/**
 * Open import fields dialog (from list page)
 */
async function handleImportFromTable() {
  await store.fetchTables(props.functionUnitId)
  formBindings.value = []
  importTableId.value = null
  selectedImportFields.value = []
  showImportFieldsDialog.value = true
}

/**
 * Open import fields dialog (from designer page)
 */
async function handleImportFieldsToDesigner() {
  await store.fetchTables(props.functionUnitId)
  
  // Load form bindings
  if (selectedForm.value) {
    try {
      const res = await functionUnitApi.getFormBindings(props.functionUnitId, selectedForm.value.id)
      formBindings.value = res.data || []
    } catch (e) {
      formBindings.value = []
    }
    
    // If active tab is a RELATED binding, auto-select its table from store.tables
    if (activeDesignerTab.value !== 'main') {
      const bindingId = Number(activeDesignerTab.value)
      const subBinding = designerSubBindings.value.find(b => b.bindingId === bindingId && b.bindingType === 'RELATED')
      if (subBinding) {
        // Find the table in store.tables by name (since dw_table_definitions has RELATION type tables)
        const dwTable = store.tables.find(t => t.tableName === subBinding.tableName || t.tableDisplayName === subBinding.tableName)
        if (dwTable) {
          importTableId.value = dwTable.id
        }
        selectedImportFields.value = []
        relationTableFields.value = []
        showImportFieldsDialog.value = true
        // Fetch relation table fields
        await handleTableChange()
        return
      }
    }
    
    // Auto-select primary table if bound
    const primaryBinding = formBindings.value.find(b => b.bindingType === 'PRIMARY')
    if (primaryBinding) {
      importTableId.value = primaryBinding.tableId
    } else if (selectedForm.value.boundTableId) {
      importTableId.value = selectedForm.value.boundTableId
    } else {
      importTableId.value = null
    }
  } else {
    formBindings.value = []
    importTableId.value = null
  }
  
  selectedImportFields.value = []
  relationTableFields.value = []
  showImportFieldsDialog.value = true
}

/**
 * Convert database field type to form-create rule
 */
function fieldToFormRule(field: FieldDefinition): any {
  const baseRule = {
    field: field.fieldName,
    title: field.description || field.fieldName,
    props: {},
    validate: [] as any[]
  }
  
  // Add required validation if field is not nullable
  if (!field.nullable) {
    baseRule.validate.push({
      required: true,
      message: `${field.description || field.fieldName} ${t('form.required').toLowerCase()}`,
      trigger: 'blur'
    })
  }
  
  // Map data type to form component
  switch (field.dataType) {
    case 'VARCHAR':
      return {
        ...baseRule,
        type: 'input',
        props: {
          placeholder: `${t('common.inputPlaceholder')} ${field.description || field.fieldName}`,
          maxlength: field.length || 255,
          showWordLimit: true
        }
      }
    case 'TEXT':
      return {
        ...baseRule,
        type: 'input',
        props: {
          type: 'textarea',
          placeholder: `${t('common.inputPlaceholder')} ${field.description || field.fieldName}`,
          rows: 3
        }
      }
    case 'INTEGER':
    case 'BIGINT':
      return {
        ...baseRule,
        type: 'inputNumber',
        props: {
          placeholder: `${t('common.inputPlaceholder')} ${field.description || field.fieldName}`,
          precision: 0
        }
      }
    case 'DECIMAL':
      return {
        ...baseRule,
        type: 'inputNumber',
        props: {
          placeholder: `${t('common.inputPlaceholder')} ${field.description || field.fieldName}`,
          precision: field.scale || 2
        }
      }
    case 'BOOLEAN':
      return {
        ...baseRule,
        type: 'switch',
        props: {}
      }
    case 'DATE':
      return {
        ...baseRule,
        type: 'datePicker',
        props: {
          type: 'date',
          placeholder: `${t('common.inputPlaceholder')} ${field.description || field.fieldName}`,
          valueFormat: 'YYYY-MM-DD'
        }
      }
    case 'TIMESTAMP':
      return {
        ...baseRule,
        type: 'datePicker',
        props: {
          type: 'datetime',
          placeholder: `${t('common.inputPlaceholder')} ${field.description || field.fieldName}`,
          valueFormat: 'YYYY-MM-DD HH:mm:ss'
        }
      }
    case 'FILE':
      return {
        ...baseRule,
        type: 'upload',
        props: {
          action: '/api/v1/upload',
          accept: '.jpg,.jpeg,.png,.pdf,.docx,.xlsx',
          limit: 1,
          multiple: false,
          listType: 'text',
          uploadText: t('form.clickToUpload'),
          tip: t('form.fileUploadTip')
        }
      }
    default:
      return {
        ...baseRule,
        type: 'input',
        props: {
          placeholder: `${t('common.inputPlaceholder')} ${field.description || field.fieldName}`
        }
      }
  }
}

/**
 * Confirm importing fields to form designer
 */
async function handleConfirmImportFields() {
  if (selectedImportFields.value.length === 0) {
    ElMessage.warning(t('form.selectAtLeastOne'))
    return
  }

  if (selectedForm.value) {
    // Check if importing into a relation table
    if (isImportingRelationTable()) {
      // Convert selected fields to RelationFieldDTO format and pass directly to the view
      const relationFields = selectedImportFields.value.map((f, idx) => ({
        id: idx,
        fieldName: f.fieldName,
        dataType: f.dataType || 'VARCHAR',
        length: f.length,
        precision: f.precision,
        scale: f.scale,
        nullable: f.nullable ?? true,
        isPrimaryKey: f.isPrimaryKey ?? false,
        defaultValue: f.defaultValue,
        comment: f.description,
        sortOrder: idx,
      }))
      // Convert ALL available fields (not just selected) for the left panel
      const allRelationFields = availableFields.value.map((f, idx) => ({
        id: idx,
        fieldName: f.fieldName,
        dataType: f.dataType || 'VARCHAR',
        length: f.length,
        precision: f.precision,
        scale: f.scale,
        nullable: f.nullable ?? true,
        isPrimaryKey: f.isPrimaryKey ?? false,
        defaultValue: f.defaultValue,
        comment: f.description,
        sortOrder: idx,
      }))
      // Update relation view state directly
      // Always update based on the imported table's binding, regardless of current tab
      const bindingId = importTableId.value
        ? getBindingIdForTable(importTableId.value)
        : Number(activeDesignerTab.value)
      if (bindingId) {
        relationViewState.value = {
          ...relationViewState.value,
          [bindingId]: { allFields: allRelationFields, viewFields: relationFields }
        }
      }
      ElMessage.success(t('form.importedSuccess', { count: selectedImportFields.value.length }))
      showImportFieldsDialog.value = false
      return
    }

    // Check if importing into a sub table - update both form designer and list view
    if (isImportingSubTable()) {
      // Update sub-table list view state
      const subFields = selectedImportFields.value.map((f, idx) => ({
        fieldName: f.fieldName,
        dataType: f.dataType || 'VARCHAR',
        comment: f.description || f.fieldName,
      })) as SubTableFieldDTO[]
      const allSubFields = availableFields.value.map((f) => ({
        fieldName: f.fieldName,
        dataType: f.dataType || 'VARCHAR',
        comment: f.description || f.fieldName,
      })) as SubTableFieldDTO[]

      const bindingId = importTableId.value
        ? getBindingIdForTable(importTableId.value)
        : Number(activeDesignerTab.value)
      if (bindingId) {
        subTableViewState.value = {
          ...subTableViewState.value,
          [bindingId]: { allFields: allSubFields, viewFields: subFields }
        }
      }

      // Also import to sub-table form designer
      const rules = selectedImportFields.value.map(fieldToFormRule)

      // Find target sub designer ref
      let targetRef: any = null
      if (bindingId) {
        const index = designerSubBindings.value.findIndex(b => b.bindingId === bindingId)
        if (index >= 0) targetRef = subDesignerRefs.value[index]
      }

      if (targetRef) {
        const currentRules: any[] = targetRef.getRule() || []
        const existingFields = new Set(currentRules.map((r: any) => r.field))
        const newRules = rules.filter(r => !existingFields.has(r.field))
        const duplicateCount = rules.length - newRules.length

        if (duplicateCount > 0) {
          ElMessage.warning(t('form.skipExisting', { count: duplicateCount }))
        }

        if (newRules.length > 0) {
          const merged = [...currentRules, ...newRules]
          injectUploadButtonLabels(merged, t('form.clickToUpload'))
          targetRef.setRule(merged)
        }
      }

      ElMessage.success(t('form.importedSuccess', { count: selectedImportFields.value.length }))
      showImportFieldsDialog.value = false
      return
    }

    const rules = selectedImportFields.value.map(fieldToFormRule)

    // Determine target designer: active sub tab or main
    let targetRef: any = null
    if (activeDesignerTab.value !== 'main') {
      const bindingId = Number(activeDesignerTab.value)
      const index = designerSubBindings.value.findIndex(b => b.bindingId === bindingId)
      if (index >= 0) targetRef = subDesignerRefs.value[index]
    }
    // Fall back to main designer if no sub ref found
    if (!targetRef) targetRef = designerRef.value

    if (targetRef) {
      const currentRules: any[] = targetRef.getRule() || []
      const existingFields = new Set(currentRules.map((r: any) => r.field))
      const newRules = rules.filter(r => !existingFields.has(r.field))
      const duplicateCount = rules.length - newRules.length

      if (duplicateCount > 0) {
        ElMessage.warning(t('form.skipExisting', { count: duplicateCount }))
      }

      if (newRules.length > 0) {
        const merged = [...currentRules, ...newRules]
          injectUploadButtonLabels(merged, t('form.clickToUpload'))
        targetRef.setRule(merged)
        ElMessage.success(t('form.importedSuccess', { count: newRules.length }))
      }
    }
  } else {
    ElMessage.info(t('form.selectOrCreateForm'))
  }

  showImportFieldsDialog.value = false
}

async function loadForms() {
  loading.value = true
  try {
    await store.fetchForms(props.functionUnitId)
    await store.fetchTables(props.functionUnitId)
    await store.fetchProcess(props.functionUnitId)
    if (selectedForm.value) {
      const refreshed = store.forms.find(form => form.id === selectedForm.value?.id)
      if (refreshed) {
        selectedForm.value = {
          ...selectedForm.value,
          ...refreshed,
          tableBindings: selectedForm.value.tableBindings
        }
      }
    }
    // 解析BPMN XML获取表单绑定信息
    parseFormBindingsFromBpmn()
  } finally {
    loading.value = false
  }
}

/**
 * 从BPMN XML解析表单与节点的绑定关系（支持多节点）
 */
function parseFormBindingsFromBpmn() {
  const bindings = new Map<number, Array<{ nodeId: string; nodeName: string; readOnly: boolean }>>()
  
  if (!store.process?.bpmnXml) {
    formNodeBindings.value = bindings
    return
  }
  
  try {
    const parser = new DOMParser()
    const xmlDoc = parser.parseFromString(store.process.bpmnXml, 'text/xml')
    
    // 查找所有任务节点 - 支持带命名空间和不带命名空间的情况
    // querySelectorAll 不支持命名空间，使用 getElementsByTagNameNS 或正则匹配
    const allElements = xmlDoc.getElementsByTagName('*')
    const tasks: Element[] = []
    
    for (let i = 0; i < allElements.length; i++) {
      const el = allElements[i]
      const localName = el.localName || el.nodeName.split(':').pop()
      if (localName === 'userTask' || localName === 'serviceTask') {
        tasks.push(el)
      }
    }
    
    tasks.forEach(task => {
      const taskId = task.getAttribute('id') || ''
      const taskName = task.getAttribute('name') || taskId
      
      // 查找formId和formReadOnly属性 - 支持 property 和 values 两种格式
      const allProps = task.getElementsByTagName('*')
      let formId: number | null = null
      let readOnly = false
      
      for (let i = 0; i < allProps.length; i++) {
        const prop = allProps[i]
        const localName = prop.localName || prop.nodeName.split(':').pop()
        
        if (localName === 'property' || localName === 'values') {
          const name = prop.getAttribute('name')
          const value = prop.getAttribute('value')
          
          // 添加调试日志
          if (name && ['formId', 'formName', 'formReadOnly'].includes(name)) {
            console.log(`[FormDesigner] parseFormBindingsFromBpmn: task ${taskId}, property name=${name}, value=${value}, nodeName=${prop.nodeName}, localName=${localName}`)
          }
          
          if (name === 'formId' && value) {
            formId = parseInt(value, 10)
            console.log(`[FormDesigner] parseFormBindingsFromBpmn: task ${taskId}, found formId=${formId}`)
          }
          if (name === 'formReadOnly' && value === 'true') {
            readOnly = true
          }
        }
      }
      
      if (formId !== null && !isNaN(formId)) {
        if (!bindings.has(formId)) {
          bindings.set(formId, [])
        }
        bindings.get(formId)!.push({ nodeId: taskId, nodeName: taskName, readOnly })
        console.log(`[FormDesigner] parseFormBindingsFromBpmn: added binding for formId=${formId}, taskId=${taskId}`)
      }
    })
  } catch (e) {
    console.error('Failed to parse BPMN XML:', e)
  }
  
  formNodeBindings.value = bindings
}

/**
 * 获取表单绑定的所有节点信息
 */
function getFormBoundNodes(formId: number): Array<{ nodeId: string; nodeName: string; readOnly: boolean }> {
  return formNodeBindings.value.get(formId) || []
}

/**
 * 检查节点是否被选中
 */
function isNodeSelected(nodeId: string): boolean {
  const result = selectedBindNodes.value.some(n => n.nodeId === nodeId)
  // 添加日志以便调试
  if (processNodes.value.some(n => n.id === nodeId)) {
    console.log(`[FormDesigner] isNodeSelected(${nodeId}):`, result, 'selectedBindNodes:', selectedBindNodes.value.map(n => n.nodeId))
  }
  return result
}

/**
 * 检查节点是否为只读
 */
function isNodeReadOnly(nodeId: string): boolean {
  const node = selectedBindNodes.value.find(n => n.nodeId === nodeId)
  return node?.readOnly || false
}

/**
 * 切换节点选中状态
 */
function toggleNodeSelection(nodeId: string, nodeName: string, selected: boolean) {
  if (selected) {
    if (!isNodeSelected(nodeId)) {
      selectedBindNodes.value.push({ nodeId, nodeName, readOnly: false })
    }
  } else {
    selectedBindNodes.value = selectedBindNodes.value.filter(n => n.nodeId !== nodeId)
  }
}

/**
 * 设置节点只读状态
 */
function setNodeReadOnly(nodeId: string, readOnly: boolean) {
  const node = selectedBindNodes.value.find(n => n.nodeId === nodeId)
  if (node) {
    node.readOnly = readOnly
  }
}

async function loadProcessNodes() {
  try {
    await store.fetchProcess(props.functionUnitId)
    if (store.process?.bpmnXml) {
      const parser = new DOMParser()
      const doc = parser.parseFromString(store.process.bpmnXml, 'text/xml')
      const nodes: ProcessNode[] = []
      
      const userTasks = doc.querySelectorAll('userTask')
      userTasks.forEach(task => {
        nodes.push({
          id: task.getAttribute('id') || '',
          name: task.getAttribute('name') || task.getAttribute('id') || '',
          type: 'userTask'
        })
      })
      
      const serviceTasks = doc.querySelectorAll('serviceTask')
      serviceTasks.forEach(task => {
        nodes.push({
          id: task.getAttribute('id') || '',
          name: task.getAttribute('name') || task.getAttribute('id') || '',
          type: 'serviceTask'
        })
      })
      
      processNodes.value = nodes
    } else {
      processNodes.value = []
    }
  } catch {
    processNodes.value = []
  }
}

function handleSelectForm(row: FormDefinition) {
  // Clean up any existing polling before selecting new form
  cleanupAutoSavePolling()

  selectedForm.value = { ...row }
  subDesignerRefs.value = []
  subFormCache.value = {}
  subTableListViewRefs.value = {}
  subTableViewState.value = {}
  activeDesignerTab.value = 'main'
  subTableActiveTab.value = 'form'

  // Load table bindings
  functionUnitApi.getFormBindings(props.functionUnitId, row.id)
    .then(res => {
      if (selectedForm.value) {
        selectedForm.value = { ...selectedForm.value, tableBindings: res.data || [] }
      }
      // Restore saved relationViewState for RELATED bindings so imported fields survive save
      const config = row.configJson || {}
      const savedViews = config.relationViews || {}
      const initialState: Record<number, { allFields: any[]; viewFields: any[] }> = {}
      for (const b of (res.data || [])) {
        if (b.bindingType === 'RELATED') {
          const id = b.id as number
          initialState[id] = savedViews[id]
            ? { allFields: savedViews[id].allFields || [], viewFields: savedViews[id].viewFields || [] }
            : { allFields: [], viewFields: [] }
        }
      }
      relationViewState.value = initialState
      const savedSubListViews = config.subListViews || {}
      const initialSubTableViewState: Record<number, { allFields: SubTableFieldDTO[]; viewFields: SubTableListColumnDTO[] }> = {}
      for (const b of (res.data || [])) {
        if (b.bindingType === 'SUB') {
          const id = b.id as number
          const saved = savedSubListViews[id]
          initialSubTableViewState[id] = {
            allFields: [],
            viewFields: Array.isArray(saved?.columns) ? saved.columns : []
          }
        }
      }
      subTableViewState.value = initialSubTableViewState
      // Load sub designers after bindings are known
      nextTick(() => setTimeout(() => loadSubDesigners(row), 200))
    })
    .catch(() => {})

  // Load main designer and start auto-save polling
  nextTick(() => {
    setTimeout(() => {
      if (designerRef.value) {
        const config = row.configJson || {}
        try {
          const rules = cloneFormRules(config.rule && config.rule.length ? config.rule : [])
          injectUploadButtonLabels(rules, t('form.clickToUpload'))
          designerRef.value.setRule(rules)
          designerRef.value.setOption(
            mergeLoadedFormOptions(
              config.options && Object.keys(config.options).length ? config.options : undefined,
              defaultFormOption.value,
              t('form.clickToUpload')
            )
          )
        } catch (e) {
          console.error('Failed to load main form config:', e)
          try { designerRef.value.setRule([]); designerRef.value.setOption({ ...defaultFormOption.value }) } catch {}
        }
        // Start auto-save polling after designer is loaded
        setupAutoSavePolling()
      }
    }, 100)
  })
}

function loadSubDesigners(row: FormDefinition) {
  const config = row.configJson || {}
  const subForms = config.subForms || {}
  designerSubBindings.value.forEach((binding, index) => {
    nextTick(() => {
      setTimeout(() => {
        const subRef = subDesignerRefs.value[index]
        if (subRef) {
          const subConfig = subForms[binding.bindingId] || {}
          try {
            const rules = cloneFormRules(subConfig.rule && subConfig.rule.length ? subConfig.rule : [])
          injectUploadButtonLabels(rules, t('form.clickToUpload'))
            subRef.setRule(rules)
            subRef.setOption(
              mergeLoadedFormOptions(
                subConfig.options && Object.keys(subConfig.options).length ? subConfig.options : undefined,
                defaultFormOption.value,
                t('form.clickToUpload')
              )
            )
          } catch {}
        }
      }, 150)
    })
  })
}

function handleTabChange(tabName: TabPaneName) {
  if (tabName === 'main') return
  const bindingId = Number(tabName)
  const index = designerSubBindings.value.findIndex(b => b.bindingId === bindingId)
  if (index < 0) return
  const binding = designerSubBindings.value[index]
  const config = selectedForm.value?.configJson || {}

  // For RELATED bindings, restore saved view fields
  if (binding.bindingType === 'RELATED') {
    // Restore saved relation view state if not already loaded
    if (!relationViewState.value[bindingId]) {
      const saved = (config.relationViews || {})[bindingId]
      if (saved) {
        relationViewState.value = {
          ...relationViewState.value,
          [bindingId]: { allFields: saved.allFields || [], viewFields: saved.viewFields || [] }
        }
      }
    }
    return
  }

  // For SUB bindings, load sub-table list view config
  if (binding.bindingType === 'SUB') {
    if (!subTableViewState.value[bindingId] || subTableViewState.value[bindingId].allFields.length === 0) {
      loadSubTableViewConfig(bindingId, binding)
    }
  }

  const subForms = config.subForms || {}
  nextTick(() => {
    setTimeout(() => {
      const subRef = subDesignerRefs.value[index]
      if (subRef) {
        // Use cache if available (user already visited this tab), else fall back to saved config
        const cached = subFormCache.value[bindingId]
        const subConfig = cached || subForms[bindingId] || {}
        try {
          const rules = cloneFormRules(subConfig.rule && subConfig.rule.length ? subConfig.rule : [])
          injectUploadButtonLabels(rules, t('form.clickToUpload'))
          subRef.setRule(rules)
          subRef.setOption(
            mergeLoadedFormOptions(
              subConfig.options && Object.keys(subConfig.options).length ? subConfig.options : undefined,
              defaultFormOption.value,
              t('form.clickToUpload')
            )
          )
        } catch {}
      }
    }, 100)
  })
}

function handleSubTableInnerTabChange(tabName: string, binding: any) {
  if (tabName !== 'listView') return
  if (!subTableViewState.value[binding.bindingId] || subTableViewState.value[binding.bindingId].allFields.length === 0) {
    loadSubTableViewConfig(binding.bindingId, binding)
  }
}

function handleBackToList() {
  selectedForm.value = null
  cleanupAutoSavePolling()
}

async function handleCreateForm() {
  if (!createForm.formName.trim()) {
    ElMessage.warning(t('form.enterFormName'))
    return
  }
  // PROCESS type: check uniqueness
  if (createForm.formType === 'PROCESS') {
    const existingProcess = store.forms.find(f => f.formType === 'PROCESS')
    if (existingProcess) {
      ElMessage.warning(t('form.processFormAlreadyExists'))
      return
    }
  }
  // TASK type: require stage binding
  if (createForm.formType === 'TASK') {
    if (createFormStageIds.value.length === 0) {
      ElMessage.warning(t('form.stageBindingRequired'))
      return
    }
  }
  try {
    const stageBindings = createForm.formType === 'TASK'
      ? createFormStageIds.value.map(id => {
          const node = createDialogProcessNodes.value.find(n => n.id === id)
          return { stageId: id, stageName: node?.name }
        })
      : undefined
    await store.createForm(props.functionUnitId, {
      formName: createForm.formName,
      formType: createForm.formType,
      description: createForm.description,
      boundTableId: createForm.boundTableId || undefined,
      configJson: { rule: [], options: {} },
      ...(stageBindings ? { stageBindings } : {})
    })
    ElMessage.success(t('form.createSuccess'))
    showCreateDialog.value = false
    Object.assign(createForm, { formName: '', formType: 'PROCESS', description: '', boundTableId: null })
    createFormStageIds.value = []
    loadForms()
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || t('form.createFailed'))
  }
}

/**
 * Check if a bindingId is already used by another subTable placeholder
 * Returns true if duplicate found (warning should be shown)
 */
const checkDuplicateBinding = (selectedId: number, currentRuleIndex: number): boolean => {
  if (!designerRef.value) return false
  try {
    const rule = designerRef.value.getRule()
    return rule.some((r: any, idx: number) =>
      idx !== currentRuleIndex && r.type === 'subTable' && r._bindingId === selectedId
    )
  } catch {
    return false
  }
}

// Rule tree helpers moved to @/utils/formDesigner.ts

/**
 * Handle sub-table binding selection change — check for duplicates and warn
 */
const handleSubTableBindingChange = (selectedId: number | null, ruleIndex: number) => {
  if (selectedId == null) return
  if (checkDuplicateBinding(selectedId, ruleIndex)) {
    ElMessage.warning(t('form.duplicateSubTableBinding'))
  }
}

/**
 * Handle navigate event from SubTablePlaceholderWidget
 * Navigates to the Sub Table form designer page for the given bindingId
 */
const handleSubTableNavigate = (bindingId: number) => {
  if (!bindingId) return
  router.push({
    name: 'SubTableFormDesigner',
    params: { bindingId: String(bindingId) }
  })
}

/** Load process nodes for stage binding in create dialog */
async function loadCreateDialogProcessNodes() {
  try {
    const processData = await functionUnitApi.getProcess(props.functionUnitId)
    const bpmnXml = processData?.data?.bpmnXml
    if (bpmnXml) {
      const parser = new DOMParser()
      const doc = parser.parseFromString(bpmnXml, 'text/xml')
      const userTasks = doc.querySelectorAll('userTask')
      createDialogProcessNodes.value = Array.from(userTasks).map(task => ({
        id: task.getAttribute('id') || '',
        name: task.getAttribute('name') || task.getAttribute('id') || '',
        type: 'userTask'
      }))
    } else {
      createDialogProcessNodes.value = []
    }
  } catch {
    createDialogProcessNodes.value = []
  }
}

/** Handle form type change in create dialog */
function handleCreateFormTypeChange(type: FormType) {
  if (type === 'TASK' && createDialogProcessNodes.value.length === 0) {
    loadCreateDialogProcessNodes()
  }
  createFormStageIds.value = []
}

/** Load Data_Table columns for field name autocomplete */
async function loadDataTableColumns() {
  try {
    const res = await functionUnitApi.getDataTableColumns(props.functionUnitId)
    dataTableColumns.value = res?.data || []
  } catch {
    dataTableColumns.value = []
  }
}

/** Validate field names against Data_Table columns */
function validateFieldNames(fieldNames: string[]): string[] {
  if (dataTableColumns.value.length === 0) return []
  return fieldNames.filter(name => !dataTableColumns.value.includes(name))
}

/** 列表「更多」：复制 / 表绑定 / 绑定节点 */
function onFormListMoreAction(command: string, row: FormDefinition) {
  switch (command) {
    case 'rename':
      openRenameDialog(row)
      break
    case 'copy':
      void handleCopyForm(row)
      break
    case 'bindings':
      handleManageBindings(row)
      break
    case 'bindNode':
      handleBindNode(row)
      break
    default:
      break
  }
}

function openRenameDialog(form: FormDefinition) {
  renameTargetForm.value = form
  renameFormName.value = form.formName
  showRenameDialog.value = true
}

// Form CRUD actions moved to useFormActions composable

/** Get current form fields from the designer for field permission config */
const currentFormFields = computed(() => {
  if (!designerRef.value || !selectedForm.value) return []
  try {
    const rule = designerRef.value.getRule() || []
    return rule
      .filter((r: any) => r.field && r.type !== 'subTable')
      .map((r: any) => ({ field: r.field, title: r.title || r.field }))
  } catch {
    // Fallback to saved configJson
    const rule = selectedForm.value.configJson?.rule || []
    return rule
      .filter((r: any) => r.field && r.type !== 'subTable')
      .map((r: any) => ({ field: r.field, title: r.title || r.field }))
  }
})

/** Get field permission value */
function getFieldPermission(fieldName: string): string {
  return selectedForm.value?.fieldPermissions?.[fieldName] || 'EDITABLE'
}

/** Set field permission value */
function setFieldPermission(fieldName: string, value: string) {
  if (!selectedForm.value) return
  if (!selectedForm.value.fieldPermissions) {
    selectedForm.value.fieldPermissions = {}
  }
  selectedForm.value.fieldPermissions[fieldName] = value
}

async function handleSaveForm(isManual = false) {
  if (!selectedForm.value || !designerRef.value) return

  if (!isManual) {
    autoSaving.value = true
  }

  try {
    const rule = designerRef.value.getRule()
    const options = designerRef.value.getOption()

    const subTableRules = collectSubTableRules(rule)

    // Validate: all subTable placeholders must have a _bindingId selected
    const invalidPlaceholders = subTableRules.filter((r: any) => !r._bindingId)
    if (invalidPlaceholders.length > 0) {
      if (isManual) ElMessage.error(t('form.subTableBindingRequired'))
      return
    }

    // 子表占位符必须绑定 SUB 类型表绑定（流程/任务表单下一主多子，数据走子表单增删改）
    if (selectedForm.value.formType === 'PROCESS' || selectedForm.value.formType === 'TASK') {
      const boundSubTableRules = subTableRules.filter((r: any) => r._bindingId)
      // Use designerSubBindings for validation (includes latest bindings from store)
      const bindingMap = new Map(designerSubBindings.value.map(b => [b.bindingId, b.bindingType]))
      console.log('[FormDesigner] Validating subTable widgets:', {
        subTableRules: boundSubTableRules.map(r => ({ _bindingId: r._bindingId, title: r.title })),
        bindingMap: Array.from(bindingMap.entries()),
        designerSubBindings: designerSubBindings.value,
        selectedFormTableBindings: selectedForm.value.tableBindings
      })
      for (const st of boundSubTableRules) {
        const bindingType = bindingMap.get(st._bindingId)
        console.log('[FormDesigner] Checking subTable widget:', { bindingId: st._bindingId, bindingType, isSub: bindingType === 'SUB' })
        if (!bindingType) {
          console.error('[FormDesigner] bindingId not found in bindingMap:', st._bindingId)
          // Try to find in selectedForm.tableBindings directly
          const directFind = selectedForm.value.tableBindings?.find((b: any) => b.id === st._bindingId)
          console.error('[FormDesigner] Direct lookup in tableBindings:', directFind)
        }
        if (!bindingType || bindingType !== 'SUB') {
          if (isManual) ElMessage.error(t('form.subTableOnlySubBinding'))
          return
        }
      }
    }

    // Validate field names against Data_Table columns (for PROCESS and TASK forms)
    if (selectedForm.value.formType === 'PROCESS' || selectedForm.value.formType === 'TASK') {
      const fieldNames = rule
        .filter((r: any) => r.field && r.type !== 'subTable')
        .map((r: any) => r.field as string)
      const invalidFields = validateFieldNames(fieldNames)
      if (invalidFields.length > 0) {
        if (isManual) ElMessage.error(t('form.fieldNameValidationFailed'))
        return
      }
    }

    // Collect sub form rules — prefer live ref, then cache, then previously saved
    const subForms: Record<number, { rule: any[]; options: any }> = {}
    designerSubBindings.value.forEach((binding, index) => {
      const subRef = subDesignerRefs.value[index]
      if (subRef) {
        // Tab is currently active and mounted
        try {
          const liveRule = subRef.getRule() || []
          const liveOptions = subRef.getOption() || {}
          subForms[binding.bindingId] = { rule: liveRule, options: liveOptions }
          // Also update cache
          subFormCache.value[binding.bindingId] = { rule: liveRule, options: liveOptions }
        } catch {}
      } else if (subFormCache.value[binding.bindingId]) {
        // Tab was visited but is now unmounted — use cache
        subForms[binding.bindingId] = subFormCache.value[binding.bindingId]
      } else {
        // Tab never visited — preserve previously saved data
        const existing = (selectedForm.value!.configJson?.subForms || {})[binding.bindingId]
        if (existing) subForms[binding.bindingId] = existing
      }
    })

    // Collect relation table view fields
    const relationViews: Record<number, { viewFields: any[]; allFields: any[] }> = {}
    designerSubBindings.value.forEach((binding) => {
      if (binding.bindingType === 'RELATED') {
        const state = relationViewState.value[binding.bindingId]
        if (state && (state.viewFields.length > 0 || state.allFields.length > 0)) {
          relationViews[binding.bindingId] = state
        } else {
          // Preserve previously saved data
          const existing = (selectedForm.value!.configJson?.relationViews || {})[binding.bindingId]
          if (existing) relationViews[binding.bindingId] = existing
        }
      }
    })

    // Collect sub-table list view columns, including dropped Link Form columns.
    const subListViews: Record<number, { columns: SubTableListColumnDTO[] }> = {
      ...(selectedForm.value.configJson?.subListViews || {})
    }
    designerSubBindings.value.forEach((binding) => {
      if (binding.bindingType !== 'SUB') return
      const listRef = subTableListViewRefs.value[binding.bindingId]
      if (listRef) {
        const columns = listRef.getListColumns?.() || listRef.getViewFields?.() || []
        const state = subTableViewState.value[binding.bindingId]
        const existing = (selectedForm.value!.configJson?.subListViews || {})[binding.bindingId]
        const existingColumns = Array.isArray(existing?.columns) ? existing.columns : []
        // Only treat list state as "ready" when we have columns in memory. allFields alone is not enough:
        // after a bad merge, viewFields can be empty while allFields is populated — saving would otherwise
        // persist { columns: [] } and wipe configJson.subListViews.
        const stateLoaded = !!state && (state.viewFields?.length || 0) > 0
        if (columns.length === 0 && existingColumns.length > 0 && !stateLoaded) {
          // The list-view tab can mount before its async config load finishes; preserve saved columns.
          subListViews[binding.bindingId] = existing
        } else {
          subListViews[binding.bindingId] = { columns }
          const nextState = state || { allFields: [], viewFields: [] }
          subTableViewState.value[binding.bindingId] = {
            ...nextState,
            viewFields: columns
          }
        }
      } else {
        const state = subTableViewState.value[binding.bindingId]
        if (state?.viewFields?.length) {
          subListViews[binding.bindingId] = { columns: state.viewFields }
        } else {
          const existing = (selectedForm.value!.configJson?.subListViews || {})[binding.bindingId]
          if (existing) subListViews[binding.bindingId] = existing
        }
      }
    })

    const nextConfig = { rule, options, subForms, relationViews, subListViews }
    const updated = await store.updateForm(props.functionUnitId, selectedForm.value.id, {
      formName: selectedForm.value.formName,
      formType: selectedForm.value.formType,
      description: selectedForm.value.description,
      configJson: nextConfig,
      ...(selectedForm.value.formType === 'TASK' && selectedForm.value.fieldPermissions
        ? { fieldPermissions: selectedForm.value.fieldPermissions }
        : {})
    })
    selectedForm.value = {
      ...selectedForm.value,
      configJson: updated.configJson || nextConfig
    }

    if (isManual) {
      ElMessage.success(t('form.saveSuccess'))
      await loadForms()
    } else {
      lastAutoSaveTime.value = new Date()
    }
  } catch (e: any) {
    if (isManual) {
      ElMessage.error(e.response?.data?.message || t('form.saveFailed'))
    }
  } finally {
    if (!isManual) {
      autoSaving.value = false
    }
  }
}

function handlePreview() {
  console.log('[DEBUG] ==================== handlePreview START ====================')
  console.log('[DEBUG] after fetch')

  // Wrapper to catch errors during preview generation
  function buildPreview() {
  if (!selectedForm.value) {
    console.log('[DEBUG] no selectedForm, returning early')
    return
  }
  // Always use live designer rule so unsaved reordering is reflected in preview.
  // Fall back to saved configJson rule only when the designer ref is unavailable.
  let rawRule: any[] = []
  try {
    rawRule = designerRef.value?.getRule() || []
  } catch {}
  if (!rawRule.length) {
    rawRule = selectedForm.value.configJson?.rule || []
  }
  console.log('[DEBUG] rawRule fetched, length:', rawRule.length, 'lookup items:', rawRule.filter(r => r?.type === 'lookup').length)
  console.log('[DEBUG] rawRule items:', rawRule.map(r => ({ type: r?.type, field: r?.field, title: r?.title })))
  console.log('[DEBUG] A: before deepScan')
  // Deep scan for lookup inside elCard, groups, etc. (with depth limit to prevent stack overflow)
  function deepScan(items: any[], depth = 0): any[] {
    if (!items || !Array.isArray(items) || depth > 10) return []
    const results: any[] = []
    for (const item of items) {
      if (!item) continue
      results.push({ depth, type: item.type, field: item.field, title: item.title, hasChildren: !!(item.children || item.$el?.children) })
      if (item.children && Array.isArray(item.children)) {
        results.push(...deepScan(item.children, depth + 1))
      }
    }
    return results
  }
  console.log('[DEBUG] A2: before deepScan call')
  try {
    const dsResult = deepScan(rawRule)
    console.log('[DEBUG] deepScan result:', JSON.stringify(dsResult))
  } catch (e) {
    console.log('[DEBUG] deepScan error:', e)
  }
  console.log('[DEBUG] B: after deepScan')
  previewData.value = {}
  previewSubData.value = {}
  previewTableRows.value = {}

  // Sync label position from designer option
  Object.assign(previewOptionState, {
    submitBtn: false,
    resetBtn: false,
  })

  const config = selectedForm.value.configJson || {}
  const subForms = config.subForms || {}
  const nonPrimary = (selectedForm.value.tableBindings || []).filter((b: TableBinding) => b.bindingType !== 'PRIMARY')

  // Build a map of bindingId -> binding info for quick lookup
  const bindingMap = new Map<number, { bindingId: number; bindingType: string; bindingMode: string; tableName: string; tableType: string; tableDescription: string; rule: any[]; option?: any; columns: any[]; subMode?: string }>()
  nonPrimary.forEach((b: TableBinding) => {
    const bindingId = b.id as number
    const index = designerSubBindings.value.findIndex(d => d.bindingId === bindingId)
    const subRef = subDesignerRefs.value[index]
    let rule: any[] = []
    let option: any = {}
    try {
      if (subRef) {
        rule = subRef.getRule() || []
        option = subRef.getOption() || {}
      } else if (subFormCache.value[bindingId]) {
        rule = subFormCache.value[bindingId].rule || []
        option = subFormCache.value[bindingId].options || {}
      } else {
        rule = subForms[bindingId]?.rule || []
        option = subForms[bindingId]?.options || {}
      }
    } catch {
      rule = subFormCache.value[bindingId]?.rule || subForms[bindingId]?.rule || []
      option = subFormCache.value[bindingId]?.options || subForms[bindingId]?.options || {}
    }
    previewTableRows.value[bindingId] = []
    const columns = toSubTablePreviewColumns(bindingId, rule, config)
    bindingMap.set(bindingId, {
      bindingId,
      bindingType: b.bindingType,
      bindingMode: b.bindingMode,
      tableName: b.tableName || getTableName(b.tableId),
      tableType: (store.tables.find(t => t.id === b.tableId)?.tableType) || (b.bindingType === 'RELATED' ? 'RELATION' : ''),
      tableDescription: (store.tables.find(t => t.id === b.tableId)?.description) || '',
      rule,
      option,
      columns,
      subMode: b.subMode,
    })
  })

  // Debug
  console.log('[Preview] rawRule types:', rawRule.map(r => `${r.type}(${r._bindingId ?? r.field})`))
  console.log('[Preview] bindingMap keys:', [...bindingMap.keys()])
  console.log('[Preview] nonPrimary bindings:', nonPrimary.map((b: TableBinding) => ({ id: b.id, bindingType: b.bindingType })))
  console.log('[Preview] bindingMap content:', [...bindingMap.entries()].map(([k, v]) => ({ key: k, columnsCount: v.columns?.length, columns: v.columns })))

  // ── Normalize custom types for form-create preview ──────────────────────────
  // form-create cannot pass nested options (with children) to custom components
  // correctly, so we convert them to native element-plus tags that form-create
  // renders via its built-in el-* passthrough.
  rawRule = rawRule.map((r: any) => {
    const rp = r.props || {}
    if (r.type === 'transfer') {
      return {
        ...r,
        type: 'el-transfer',
        props: {
          data: (rp.options ?? []).map((o: any) => ({ key: o.value, label: o.label })),
          titles: [rp.leftTitle || 'Source', rp.rightTitle || 'Target'],
          filterable: true,
        },
      }
    }
    if (r.type === 'cascader') {
      return {
        ...r,
        type: 'el-cascader',
        props: {
          options: rp.options ?? [],
          props: rp.cascaderProps || rp.props,
          placeholder: rp.placeholder || 'Please select',
          clearable: true,
        },
      }
    }
    // Strip prefix/suffix virtual nodes that form-create can't render in preview
    if (r.prefix || r.suffix) {
      const { prefix, suffix, ...rest } = r
      return rest
    }
    return r
  })

  // form-create proprietary types that should not be rendered in preview
  const FC_SKIP_PREVIEW = new Set(['subForm', 'tableForm', 'tableFormColumn', 'group', 'el-row', 'el-col'])

  function containsSubTableRule(item: any): boolean {
    if (!item) return false
    if (item.type === 'subTable' && (item._bindingId ?? item.props?._bindingId) != null) return true
    return getRuleChildren(item).some(child => containsSubTableRule(child))
  }

  function buildPreviewItems(ruleItems: any[], localBindingMap: Map<number, any>, keyPrefix = 'seg'): FormPreviewItem[] {
    const items: FormPreviewItem[] = []
    let currentSegment: any[] = []
    let segmentIndex = 0

    function flushSegment() {
      if (currentSegment.length > 0) {
        items.push({ kind: 'fields', rule: [...currentSegment], modelKey: `${keyPrefix}_${segmentIndex++}` })
        currentSegment = []
      }
    }

    for (const ruleItem of ruleItems) {
      const itemBindingId = ruleItem._bindingId ?? ruleItem.props?._bindingId ?? null
      console.log('[Preview] Checking ruleItem:', { type: ruleItem.type, _bindingId: itemBindingId, field: ruleItem.field })

      if (ruleItem.type === 'subTable' && itemBindingId != null) {
        flushSegment()
        const binding = localBindingMap.get(Number(itemBindingId))
        console.log('[Preview] Found subTable with bindingId:', itemBindingId, 'binding found:', !!binding)
        if (binding) {
          items.push({ kind: 'subTable', binding })
          localBindingMap.delete(Number(itemBindingId))
        }
      } else if (isCardRule(ruleItem) && containsSubTableRule(ruleItem)) {
        flushSegment()
        items.push({
          kind: 'card',
          title: getLayoutLabel(ruleItem),
          items: buildPreviewItems(getRuleChildren(ruleItem), localBindingMap, `card_${segmentIndex++}`),
          modelKey: `${keyPrefix}_card_${segmentIndex}`,
        })
      } else if (ruleItem.type === 'lookup') {
        flushSegment()
        items.push(makeLookupPreviewItem(ruleItem, config))
      } else if (FC_SKIP_PREVIEW.has(ruleItem.type)) {
        if (containsSubTableRule(ruleItem)) {
          flushSegment()
          items.push(...buildPreviewItems(getRuleChildren(ruleItem), localBindingMap, `${keyPrefix}_layout_${segmentIndex++}`))
        }
      } else {
        currentSegment.push(ruleItem)
      }
    }

    flushSegment()
    return items
  }

  const items = buildPreviewItems(rawRule, bindingMap)
  // Append any unplaced bindings at the bottom (skip RELATED — already shown under lookup fields)
  // Only SUB bindings that were explicitly placed via subTable component are shown;
  // unplaced bindings (no component in the form) are not rendered.
  // (placed bindings were already deleted from bindingMap above)

  previewItems.value = items
  // Keep previewRule for backward compat (used by previewSubBindings logic elsewhere if any)
  previewRule.value = rawRule.filter(r => r.type !== 'subTable')
  console.log('[Preview] previewItems:', items.map(i => i.kind === 'fields' ? `fields(${i.rule.length})` : i.kind))
  console.log('[DEBUG FormDesigner] previewItems built', {
    itemKinds: items.map(i => i.kind),
    lookupCount: items.filter(i => i.kind === 'lookup').length,
    subTableCount: items.filter(i => i.kind === 'subTable').length,
    items: items.map(i => ({
      kind: i.kind,
      hasBinding: i.kind === 'subTable' ? !!i.binding : undefined,
      columnsCount: i.kind === 'subTable' ? i.binding?.columns?.length : undefined
    }))
  })
  previewSubBindings.value = [] // no longer used for bottom rendering

  showPreviewDialog.value = true
  console.log('[DEBUG] ==================== handlePreview END ====================')
  } // end of buildPreview function

  // Wrap the entire preview building in try-catch to handle circular dependency errors
  try {
    buildPreview()
  } catch (e: any) {
    console.error('[FormDesigner] Preview build error:', e)
    // Try a simpler preview with just the basic rule
    try {
      const basicRule = (selectedForm.value?.configJson?.rule || []).filter((r: any) => r.type !== 'subTable')
      previewItems.value = [{ kind: 'fields', rule: basicRule, modelKey: 'fallback' }]
      previewSubBindings.value = []
      showPreviewDialog.value = true
    } catch (e2) {
      console.error('[FormDesigner] Fallback preview also failed:', e2)
      ElMessage.error(t('form.previewFailed'))
    }
  }
}

async function handleBindNode(form: FormDefinition) {
  bindingForm.value = form
  // 确保流程数据是最新的
  await store.fetchProcess(props.functionUnitId)
  parseFormBindingsFromBpmn()
  // 从BPMN中获取当前绑定信息
  const boundNodes = getFormBoundNodes(form.id)
  selectedBindNodes.value = boundNodes.map(n => ({ ...n }))
  await loadProcessNodes()
  showBindDialog.value = true
}

async function handleConfirmBind() {
  if (!bindingForm.value) return
  try {
    console.log('[FormDesigner] Saving bindings for form:', bindingForm.value.id, 'Selected nodes:', selectedBindNodes.value)
    // 更新BPMN XML中的节点formId属性
    if (store.process?.bpmnXml) {
      await updateBpmnFormBindings(bindingForm.value.id, bindingForm.value.formName, selectedBindNodes.value)
    }
    
    // 重新加载流程数据，确保获取最新的 BPMN XML
    await store.fetchProcess(props.functionUnitId)
    // 重新解析绑定信息
    parseFormBindingsFromBpmn()
    
    // 更新对话框中的选中状态，确保与保存后的数据一致
    const boundNodes = getFormBoundNodes(bindingForm.value.id)
    console.log('[FormDesigner] After save, bound nodes:', boundNodes)
    console.log('[FormDesigner] Before update, selectedBindNodes:', selectedBindNodes.value.map(n => n.nodeId))
    
    // 创建一个新数组，确保 Vue 能够检测到变化
    // 使用 splice 来替换整个数组，确保响应式更新
    selectedBindNodes.value.splice(0, selectedBindNodes.value.length, ...boundNodes.map(n => ({ ...n })))
    
    console.log('[FormDesigner] After update, selectedBindNodes:', selectedBindNodes.value.map(n => n.nodeId))
    
    // 强制更新对话框，确保复选框状态正确更新
    bindDialogKey.value++
    
    // 使用 nextTick 确保 Vue 能够检测到变化并更新 UI
    await nextTick()
    
    // 验证更新后的状态
    processNodes.value.forEach(node => {
      const isSelected = isNodeSelected(node.id)
      console.log(`[FormDesigner] Node ${node.id} isSelected:`, isSelected)
    })
    
    ElMessage.success(t('form.bindSuccess'))
    // 不关闭对话框，让用户看到更新后的状态
    // showBindDialog.value = false
  } catch (e: any) {
    console.error('[FormDesigner] Save binding failed:', e)
    ElMessage.error(e.response?.data?.message || t('form.bindFailed'))
  }
}

/**
 * 更新BPMN XML中多个节点的表单绑定
 */
async function updateBpmnFormBindings(
  formId: number, 
  formName: string, 
  nodes: Array<{ nodeId: string; nodeName: string; readOnly: boolean }>
) {
  if (!store.process?.bpmnXml) return
  
  console.log('[FormDesigner] updateBpmnFormBindings called with:', { formId, formName, nodesCount: nodes.length, nodes })
  
  const parser = new DOMParser()
  const xmlDoc = parser.parseFromString(store.process.bpmnXml, 'text/xml')
  
  // 先从所有节点中移除此表单的绑定
  // 使用更通用的方式查找任务节点，支持命名空间
  const allElements = xmlDoc.getElementsByTagName('*')
  const allTasks: Element[] = []
  for (let i = 0; i < allElements.length; i++) {
    const el = allElements[i]
    const localName = el.localName || el.nodeName.split(':').pop()
    if (localName === 'userTask' || localName === 'serviceTask') {
      allTasks.push(el)
    }
  }
  
  let removedCount = 0
  allTasks.forEach(task => {
    const taskId = task.getAttribute('id') || ''
    console.log(`[FormDesigner] Processing task ${taskId} for formId ${formId}`)
    
    // 使用与 parseFormBindingsFromBpmn 相同的方法查找属性
    // 查找所有后代元素中的 property，而不是只查找直接子元素
    const allProps = task.getElementsByTagName('*')
    const formIdProps: Element[] = []
    const allPropertyElements: Element[] = []
    
    // 第一步：找到所有 property 或 values 元素并记录
    // 注意：parseFormBindingsFromBpmn 检查的是 localName === 'property' || localName === 'values'
    for (let i = 0; i < allProps.length; i++) {
      const prop = allProps[i]
      const localName = prop.localName || prop.nodeName.split(':').pop()
      
      // 与 parseFormBindingsFromBpmn 保持一致：检查 property 或 values
      if (localName === 'property' || localName === 'values') {
        allPropertyElements.push(prop)
        const name = prop.getAttribute('name')
        const value = prop.getAttribute('value')
        console.log(`[FormDesigner] Found property/values in task ${taskId}: name=${name}, value=${value}, nodeName=${prop.nodeName}, localName=${localName}`)
        
        if (name === 'formId' && value === String(formId)) {
          formIdProps.push(prop)
          console.log(`[FormDesigner] ✓ Matched formId property in task ${taskId}: name=${name}, value=${value}`)
        }
      }
    }
    
    console.log(`[FormDesigner] Task ${taskId}: found ${allPropertyElements.length} total properties, ${formIdProps.length} matching formId=${formId}`)
    
    if (formIdProps.length > 0) {
      console.log(`[FormDesigner] Found ${formIdProps.length} formId properties in task ${taskId}, removing all related properties...`)
      
      // 对于每个找到的 formId property，找到它的父 properties 元素
      const processedProperties = new Set<Element>()
      
      formIdProps.forEach(formIdProp => {
        // 找到包含这个 property 的 properties 元素
        let properties: Element | null = null
        let current: Element | null = formIdProp as Element
        
        // 向上查找 properties 元素
        while (current && current.parentElement) {
          current = current.parentElement as Element
          const localName = current.localName || current.nodeName.split(':').pop()
          if (localName === 'properties') {
            properties = current
            break
          }
        }
        
        if (!properties || processedProperties.has(properties)) {
          return
        }
        
        processedProperties.add(properties)
        
        // 查找这个 properties 元素下的所有 property 或 values 元素
        // 注意：需要同时查找 property 和 values，因为 XML 中可能使用 values
        const allPropertyElements = properties.getElementsByTagName('*')
        const propsToRemove: Element[] = []
        
        for (let i = 0; i < allPropertyElements.length; i++) {
          const prop = allPropertyElements[i]
          const propLocalName = prop.localName || prop.nodeName.split(':').pop()
          
          // 检查是否是 property 或 values 元素
          if (propLocalName === 'property' || propLocalName === 'values') {
            const name = prop.getAttribute('name')
            if (name && ['formId', 'formName', 'formReadOnly'].includes(name)) {
              // 检查这个 property/values 是否在当前 properties 元素下（直接或间接）
              let parent: Element | null = prop.parentElement as Element
              while (parent && parent !== properties) {
                parent = parent.parentElement as Element
              }
              if (parent === properties) {
                propsToRemove.push(prop)
              }
            }
          }
        }
        
        console.log(`[FormDesigner] Removing ${propsToRemove.length} properties from task ${taskId}`)
        propsToRemove.forEach(p => {
          p.remove()
          removedCount++
        })
        
        // 如果 properties 为空，移除它
        if (properties.children.length === 0) {
          properties.remove()
          console.log(`[FormDesigner] Removed empty properties from task ${taskId}`)
          
          // 检查 extensionElements 是否为空
          let extensionElements: Element | null = null
          let current: Element | null = properties.parentElement as Element
          while (current) {
            const localName = current.localName || current.nodeName.split(':').pop()
            if (localName === 'extensionElements') {
              extensionElements = current
              break
            }
            current = current.parentElement as Element
          }
          
          if (extensionElements && extensionElements.children.length === 0) {
            extensionElements.remove()
            console.log(`[FormDesigner] Removed empty extensionElements from task ${taskId}`)
          }
        }
      })
    } else {
      console.log(`[FormDesigner] No formId=${formId} property found in task ${taskId}`)
    }
  })
  
  console.log(`[FormDesigner] Total removed ${removedCount} form binding properties`)
  
  // 为选中的节点添加绑定
  for (const node of nodes) {
    // 使用更通用的方式查找任务节点，支持命名空间
    const allElements = xmlDoc.getElementsByTagName('*')
    let task: Element | null = null
    for (let i = 0; i < allElements.length; i++) {
      const el = allElements[i]
      const localName = el.localName || el.nodeName.split(':').pop()
      if ((localName === 'userTask' || localName === 'serviceTask') && el.getAttribute('id') === node.nodeId) {
        task = el
        break
      }
    }
    if (!task) {
      console.warn(`[FormDesigner] Task node not found: ${node.nodeId}`)
      continue
    }
    
    // 获取或创建extensionElements
    let extensionElements: Element | null = null
    const taskChildren = Array.from(task.children)
    for (const child of taskChildren) {
      const localName = child.localName || child.nodeName.split(':').pop()
      if (localName === 'extensionElements') {
        extensionElements = child
        break
      }
    }
    
    if (!extensionElements) {
      // 创建 extensionElements，使用与现有元素相同的命名空间
      const bpmnNamespace = task.namespaceURI || 'http://www.omg.org/spec/BPMN/20100524/MODEL'
      extensionElements = xmlDoc.createElementNS(bpmnNamespace, 'extensionElements')
      task.insertBefore(extensionElements, task.firstChild)
    }
    
    // 获取或创建properties
    let properties: Element | null = null
    const extChildren = Array.from(extensionElements.children)
    for (const child of extChildren) {
      const localName = child.localName || child.nodeName.split(':').pop()
      if (localName === 'properties') {
        properties = child
        break
      }
    }
    
    if (!properties) {
      // 创建 properties，使用自定义命名空间
      const customNamespace = 'http://custom.bpmn.io/schema'
      properties = xmlDoc.createElementNS(customNamespace, 'properties')
      extensionElements.appendChild(properties)
    }
    
    // 检查是否已存在 formId 属性，如果存在则先移除
    const existingProps = Array.from(properties.children)
    const existingFormId = existingProps.find(p => {
      const localName = p.localName || p.nodeName.split(':').pop()
      if (localName === 'property') {
        return p.getAttribute('name') === 'formId' && p.getAttribute('value') === String(formId)
      }
      return false
    })
    
    if (existingFormId) {
      // 移除现有的 formId, formName, formReadOnly
      const propsToRemove = existingProps.filter(p => {
        const localName = p.localName || p.nodeName.split(':').pop()
        if (localName === 'property') {
          const name = p.getAttribute('name')
          return name && ['formId', 'formName', 'formReadOnly'].includes(name)
        }
        return false
      })
      propsToRemove.forEach(p => p.remove())
    }
    
    // 添加formId
    const customNamespace = 'http://custom.bpmn.io/schema'
    const formIdProp = xmlDoc.createElementNS(customNamespace, 'property')
    formIdProp.setAttribute('name', 'formId')
    formIdProp.setAttribute('value', String(formId))
    properties.appendChild(formIdProp)
    
    // 添加formName
    const formNameProp = xmlDoc.createElementNS(customNamespace, 'property')
    formNameProp.setAttribute('name', 'formName')
    formNameProp.setAttribute('value', formName)
    properties.appendChild(formNameProp)
    
    // 如果是只读，添加formReadOnly
    if (node.readOnly) {
      const readOnlyProp = xmlDoc.createElementNS(customNamespace, 'property')
      readOnlyProp.setAttribute('name', 'formReadOnly')
      readOnlyProp.setAttribute('value', 'true')
      properties.appendChild(readOnlyProp)
    }
  }
  
  // 序列化并保存
  const serializer = new XMLSerializer()
  const newXml = serializer.serializeToString(xmlDoc)
  
  console.log('[FormDesigner] Serialized XML length:', newXml.length)
  console.log('[FormDesigner] Saving process with updated BPMN XML')
  
  await store.saveProcess(props.functionUnitId, {
    ...store.process,
    bpmnXml: newXml
  })
  
  console.log('[FormDesigner] Process saved successfully')
}

onMounted(() => {
  loadForms()
  loadDataTableColumns()
  loadCreateDialogProcessNodes()
})

</script>


<style lang="scss" scoped>
.form-designer {
  height: 100%;
}

.form-editor-view {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.bound-nodes-header {
  display: flex;
  gap: 4px;
}

.editor-header {
  display: flex;
  align-items: center;
  gap: 16px;
  padding-bottom: 16px;
  border-bottom: 1px solid #e6e6e6;
  margin-bottom: 16px;
  
  .form-name {
    font-size: 18px;
    font-weight: 600;
    color: #303133;
  }
  
  .header-actions {
    margin-left: auto;
    display: flex;
    gap: 8px;
    align-items: center;
  }

  .auto-save-status {
    font-size: 14px;
    color: #909399;
    display: flex;
    align-items: center;
    min-width: 150px;

    .auto-saving {
      display: flex;
      align-items: center;
      gap: 6px;
      color: #409eff;
    }

    .auto-saved {
      display: flex;
      align-items: center;
      gap: 6px;
      color: #67c23a;
    }
  }
}

.fc-designer-wrapper {
  flex: 1;
  overflow: hidden;
  border: 1px solid #e6e6e6;
  border-radius: 4px;
  
  :deep(.fc-designer) {
    height: 100% !important;
  }
  
  // 确保 form-create 设计器内的样式正确应用
  :deep(.form-create) {
    width: 100%;
  }
  
  // 确保设计器内的表单项样式正确
  :deep(.el-form-item) {
    margin-bottom: 18px;
  }
  
  // 确保设计器内的输入框等组件样式正确
  :deep(.el-input),
  :deep(.el-select),
  :deep(.el-date-picker),
  :deep(.el-textarea) {
    width: 100%;
  }
}

.designer-tabs {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;

  :deep(.el-tabs__header) {
    margin-bottom: 0;
    flex-shrink: 0;
  }

  :deep(.el-tabs__content) {
    flex: 1;
    overflow: hidden;
  }

  :deep(.el-tab-pane) {
    height: 100%;
  }
}

.designer-sub-tables {
  margin-top: 16px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.designer-sub-table-item {
  .sub-table-binding-label {
    display: flex;
    align-items: center;
    gap: 8px;
    margin-bottom: 8px;

    .sub-table-name {
      font-size: 14px;
      font-weight: 500;
      color: #303133;
    }
  }
}

.sub-preview-header {
  display: flex;
  align-items: center;
  padding: 8px 0 4px;
}

.preview-container {
  min-height: 300px;
  padding: 20px;
  
  .form-preview-wrapper {
    // 确保 form-create 样式能够正确应用
    :deep(.form-create) {
      width: 100%;
    }
    
    // 确保表单项样式正确
    :deep(.el-form-item) {
      margin-bottom: 18px;
    }

    // label 不截断，自动撑开宽度
    :deep(.el-form-item__label) {
      white-space: nowrap !important;
      width: auto !important;
      min-width: fit-content !important;
      max-width: 200px !important;
      height: auto !important;
      line-height: 1.5 !important;
      padding-top: 6px;
    }

    :deep(.el-form-item) {
      display: flex !important;
      align-items: flex-start !important;
    }
    
    // 确保输入框等组件样式正确
    :deep(.el-input),
    :deep(.el-select),
    :deep(.el-date-picker),
    :deep(.el-textarea) {
      width: 100%;
    }
    
    // 确保按钮样式正确
    :deep(.el-button) {
      margin-right: 10px;
    }
  }
}



.import-fields-dialog {
  .field-selection {
    .field-header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      margin-bottom: 12px;
      padding: 8px 12px;
      background: #f5f7fa;
      border-radius: 4px;
      
      .field-count {
        font-size: 13px;
        color: #909399;
      }
    }
  }
  
  .table-option-with-binding {
    display: flex;
    justify-content: space-between;
    align-items: center;
    width: 100%;
  }
  
  .source-table {
    font-size: 12px;
    color: #909399;
  }
}

.form-item-tip {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
}

.bound-table-tag {
  margin-left: 8px;
}



.bind-table-dialog {
  .table-option {
    display: flex;
    justify-content: space-between;
    align-items: center;
    width: 100%;
  }
  
  .table-fields-preview {
    max-height: 150px;
    overflow-y: auto;
    padding: 8px;
    background: #f5f7fa;
    border-radius: 4px;
  }
}

.sub-table-placeholder-widget {
  display: flex;
  align-items: center;
  padding: 8px 12px;
  border: 1px dashed #c0c4cc;
  border-radius: 4px;
  background: #f5f7fa;
  min-height: 36px;
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
