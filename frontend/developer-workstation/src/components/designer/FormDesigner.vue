<template>
  <div class="form-designer">
    <!-- Form list view -->
    <div class="form-list-view" v-if="!selectedForm">
      <div class="designer-toolbar">
        <el-button type="primary" @click="showCreateDialog = true">
          <el-icon><Plus /></el-icon> {{ t('form.createForm') }}
        </el-button>
        <el-button @click="loadForms" :loading="loading">
          <el-icon><Refresh /></el-icon> {{ t('common.refresh') }}
        </el-button>
        <el-button @click="handleImportFromTable" :disabled="store.tables.length === 0">
          <el-icon><Connection /></el-icon> {{ t('form.importFields') }}
        </el-button>
      </div>
      
      <el-table :data="store.forms" v-loading="loading" stripe @row-click="handleSelectForm">
        <el-table-column prop="formName" :label="t('form.formName')" />
        <el-table-column prop="formType" :label="t('form.formType')" width="120">
          <template #default="{ row }">
            <el-tag :type="row.formType === 'MAIN' ? 'primary' : 'info'">
              {{ formTypeLabel(row.formType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="boundTableId" :label="t('form.boundTable')" width="180">
          <template #default="{ row }">
            <template v-if="getPrimaryBinding(row)">
              <el-tag type="success" size="small">
                {{ getPrimaryBinding(row)!.tableName }}
              </el-tag>
              <el-tag v-if="getSubBindingsCount(row) > 0" type="info" size="small" style="margin-left: 4px;">
                +{{ getSubBindingsCount(row) }}
              </el-tag>
            </template>
            <el-tag v-else-if="row.boundTableId" type="success" size="small">
              {{ getTableName(row.boundTableId) }}
            </el-tag>
            <span v-else class="text-muted">{{ t('form.notBound') }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="boundNodeId" :label="t('form.boundNode')" min-width="180">
          <template #default="{ row }">
            <div class="bound-nodes">
              <template v-if="getFormBoundNodes(row.id).length > 0">
                <el-tag 
                  v-for="node in getFormBoundNodes(row.id)" 
                  :key="node.nodeId"
                  :type="node.readOnly ? 'info' : 'success'" 
                  size="small"
                  class="node-tag"
                >
                  {{ node.nodeName }}{{ node.readOnly ? `(${t('form.readOnly')})` : '' }}
                </el-tag>
              </template>
              <span v-else class="text-muted">{{ t('form.notBound') }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="description" :label="t('table.description')" show-overflow-tooltip />
        <el-table-column :label="t('common.actions')" width="320" fixed="right">
          <template #default="{ row }">
            <div class="action-buttons">
              <el-button link type="primary" @click.stop="handleSelectForm(row)">{{ t('common.edit') }}</el-button>
              <el-button link type="warning" @click.stop="handleManageBindings(row)">{{ t('form.editBindings') }}</el-button>
              <el-button link type="success" @click.stop="handleBindNode(row)">{{ t('form.boundNode') }}</el-button>
              <el-button link type="danger" @click.stop="handleDeleteForm(row)">{{ t('common.delete') }}</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- Form designer view -->
    <div class="form-editor-view" v-else>
      <div class="editor-header">
        <el-button @click="handleBackToList">
          <el-icon><ArrowLeft /></el-icon> {{ t('form.backToList') }}
        </el-button>
        <span class="form-name">{{ selectedForm.formName }}</span>
        <el-tag v-if="selectedForm.boundTableId" type="success" size="small" class="bound-table-tag">
          {{ t('form.boundTableLabel') }}: {{ getTableName(selectedForm.boundTableId) }}
        </el-tag>
        <div class="bound-nodes-header" v-if="getFormBoundNodes(selectedForm.id).length > 0">
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
          <el-button @click="handleImportFieldsToDesigner" :disabled="!selectedForm.boundTableId && (!selectedForm.tableBindings || selectedForm.tableBindings.length === 0)">
            <el-icon><Connection /></el-icon> {{ t('form.importTableFields') }}
          </el-button>
          <el-button
            v-if="commonTableBindings.length > 0"
            @click="showCommonTableRefDialog = true"
            type="warning"
            plain
          >
            <el-icon><Link /></el-icon> {{ t('commonTable.addLookUpField') }}
          </el-button>
          <el-button
            v-if="currentActiveLookupCode"
            type="primary"
            plain
            size="default"
            @click="navigateToLookupTab"
          >
            → Lookup Form
          </el-button>
          <el-button @click="handleManageBindings(selectedForm)">{{ t('form.manageBindings') }}</el-button>
          <el-button @click="handleBindNode(selectedForm)">{{ t('form.bindProcessNode') }}</el-button>
          <el-button @click="handlePreview">{{ t('common.preview') }}</el-button>
          <el-button type="primary" @click="handleSaveForm">{{ t('common.save') }}</el-button>
        </div>
      </div>
      
      <el-tabs v-model="activeDesignerTab" class="designer-tabs" @tab-change="handleTabChange">
        <el-tab-pane name="main">
          <template #label>
            <span>
              <el-tag type="primary" size="small" style="margin-right: 6px;">{{ t('form.mainTable') }}</el-tag>
              {{ selectedForm.formName }}
            </span>
          </template>
          <div class="fc-designer-wrapper">
            <fc-designer ref="designerRef" :config="designerConfig" height="calc(100vh - 260px)" />
          </div>
        </el-tab-pane>
        <el-tab-pane
          v-for="(binding, index) in designerSubBindings"
          :key="binding.bindingId"
          :name="String(binding.bindingId)"
        >
          <template #label>
            <span>
              <el-tag v-if="binding.isCommonTable" type="danger" size="small" style="margin-right: 4px;">Lookup</el-tag>
              <el-tag :type="binding.bindingType === 'SUB' ? 'success' : 'warning'" size="small" style="margin-right: 6px;">
                {{ binding.bindingType === 'SUB' ? t('tableBinding.subTableType') : t('tableBinding.relationTableType') }}
              </el-tag>
              {{ binding.tableName }}
            </span>
          </template>
          <div class="fc-designer-wrapper">
            <fc-designer
              :ref="(el: any) => setSubDesignerRef(el, index)"
              :config="designerConfig"
              height="calc(100vh - 260px)"
            />
          </div>
        </el-tab-pane>
      </el-tabs>
    </div>

    <!-- Create form dialog -->
    <el-dialog v-model="showCreateDialog" :title="t('form.createFormTitle')" width="500px">
      <el-form :model="createForm" label-width="100px" label-position="left">
        <el-form-item :label="t('form.formNameLabel')" required>
          <el-input v-model="createForm.formName" :placeholder="t('form.enterFormName')" />
        </el-form-item>
        <el-form-item :label="t('form.formTypeLabel')">
          <el-select v-model="createForm.formType" style="width: 100%">
            <el-option :label="t('form.mainForm')" value="MAIN" />
            <el-option :label="t('form.subForm')" value="SUB" />
            <el-option :label="t('form.popupForm')" value="POPUP" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('form.bindTableLabel')">
          <el-select v-model="createForm.boundTableId" :placeholder="t('form.selectTableToBind')" style="width: 100%" clearable>
            <el-option 
              v-for="table in store.tables" 
              :key="table.id" 
              :label="`${table.tableName} (${tableTypeLabel(table.tableType)})`" 
              :value="table.id" 
            />
          </el-select>
          <div class="form-item-tip">{{ t('form.bindTableHint') }}</div>
        </el-form-item>
        <el-form-item :label="t('form.descriptionLabel')">
          <el-input v-model="createForm.description" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreateDialog = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" @click="handleCreateForm">{{ t('common.confirm') }}</el-button>
      </template>
    </el-dialog>

    <!-- Preview dialog -->
    <el-dialog v-model="showPreviewDialog" :title="t('form.previewTitle')" width="900px" destroy-on-close>
      <div class="preview-container">
        <div class="form-preview-wrapper">
          <template v-if="previewSections.length">
            <template v-for="(section, idx) in previewSections" :key="idx">
              <!-- Regular form fields section -->
              <form-create
                v-if="section.type === 'form' && section.rule.length"
                v-model="previewSectionData[idx]"
                :rule="section.rule"
                :option="previewOption"
              />
              <!-- lookupSection: CSS grid keeps label / input / descriptions in perfect alignment -->
              <div v-else-if="section.type === 'lookupSection'" class="preview-lookup-section">
                <!-- col 1: field label (same styling as form-create el-form-item__label) -->
                <div class="preview-lookup-field-label">{{ section.fieldLabel }}</div>
                <!-- col 2 row 1: disabled search input -->
                <div class="preview-lookup-field-input">
                  <el-input :placeholder="section.placeholder" disabled style="width:100%" />
                </div>
                <!-- col 2 row 2: descriptions table (auto-fit label column, no truncation) -->
                <el-descriptions
                  v-if="section.lookupRule.length"
                  class="preview-lookup-desc"
                  :column="1"
                  border
                  size="small"
                >
                  <el-descriptions-item
                    v-for="col in section.lookupRule"
                    :key="col.field"
                    :label="col.title || col.field"
                  >
                    <span style="color:#c0c4cc;">-</span>
                  </el-descriptions-item>
                </el-descriptions>
              </div>
            </template>
          </template>
          <el-empty v-else :description="t('form.noFormContent')" />
        </div>

        <!-- Regular (non-lookup) sub/relation table bindings: rendered as el-table -->
        <template v-if="previewSubBindings.some(b => b.tableType !== 'COMMON')">
          <div
            v-for="binding in previewSubBindings.filter(b => b.tableType !== 'COMMON')"
            :key="binding.bindingId"
            style="margin-top: 20px;"
          >
            <div class="sub-preview-header" style="display: flex; align-items: center; justify-content: space-between; margin-bottom: 8px;">
              <div>
                <el-tag :type="binding.bindingType === 'SUB' ? 'success' : 'warning'" size="small">
                  {{ binding.bindingType === 'SUB' ? t('tableBinding.subTableType') : t('tableBinding.relationTableType') }}
                </el-tag>
                <span style="margin-left: 8px; font-weight: 500;">{{ binding.tableName }}</span>
              </div>
              <el-button
                type="primary"
                size="small"
                @click="previewTableRows[binding.bindingId].push({})"
              >
                + {{ t('common.add') }}
              </el-button>
            </div>

            <el-table
              v-if="binding.rule && binding.rule.length"
              :data="previewTableRows[binding.bindingId]"
              border
              size="small"
              style="width: 100%;"
            >
              <el-table-column
                v-for="col in binding.rule"
                :key="col.field"
                :prop="col.field"
                :label="col.title || col.field"
                min-width="140"
              >
                <template #default="{ row }">
                  <div v-if="col.type === 'upload'" style="display:flex;flex-direction:column;gap:4px;">
                    <el-upload
                      :action="(col.props?.action && col.props.action !== '/') ? col.props.action : '/api/v1/upload'"
                      :accept="col.props?.accept || '.jpg,.jpeg,.png,.pdf,.docx,.xlsx'"
                      :limit="1"
                      :show-file-list="false"
                      :on-success="(res: any, file: any) => {
                        row[col.field] = res?.data?.url || ''
                        row[col.field + '__name'] = file.name
                      }"
                      :on-error="() => {}"
                    >
                      <el-button size="small" type="primary">{{ t('form.fileUpload') }}</el-button>
                    </el-upload>
                    <el-tag
                      v-if="row[col.field + '__name']"
                      size="small"
                      type="success"
                      closable
                      @close="() => { row[col.field] = ''; row[col.field + '__name'] = '' }"
                    >
                      {{ row[col.field + '__name'] }}
                    </el-tag>
                  </div>
                  <el-input v-else v-model="row[col.field]" size="small" />
                </template>
              </el-table-column>
              <el-table-column :label="t('common.operation')" width="80" fixed="right">
                <template #default="{ $index }">
                  <el-button
                    type="danger"
                    size="small"
                    link
                    @click="previewTableRows[binding.bindingId].splice($index, 1)"
                  >{{ t('common.delete') }}</el-button>
                </template>
              </el-table-column>
            </el-table>
            <el-empty v-else :description="t('form.noFormContent')" :image-size="40" style="border: 1px solid #e6e6e6; border-radius: 4px;" />
          </div>
        </template>
      </div>
    </el-dialog>

    <!-- Bind node dialog -->
    <el-dialog v-model="showBindDialog" :title="t('form.bindNodeTitle')" width="650px" :key="bindDialogKey">
      <div class="bind-dialog-content">
        <el-alert type="info" :closable="false" style="margin-bottom: 16px;">
          {{ t('form.bindNodeHint') }}
        </el-alert>
        <div v-if="processNodes.length" class="node-list">
          <div v-for="node in processNodes" :key="`${node.id}-${bindDialogKey}`" class="node-item">
            <el-checkbox 
              :model-value="isNodeSelected(node.id)"
              @change="toggleNodeSelection(node.id, node.name, $event as boolean)"
              :key="`checkbox-${node.id}-${bindDialogKey}`"
            />
            <div class="node-icon" :class="node.type"></div>
            <div class="node-info">
              <div class="node-name">{{ node.name }}</div>
              <div class="node-type">{{ nodeTypeLabel(node.type) }}</div>
            </div>
            <el-checkbox 
              v-if="isNodeSelected(node.id)"
              :model-value="isNodeReadOnly(node.id)"
              @change="setNodeReadOnly(node.id, $event as boolean)"
            >
              {{ t('form.readOnly') }}
            </el-checkbox>
          </div>
        </div>
        <el-empty v-else :description="t('form.noNodesAvailable')" />
      </div>
      <template #footer>
        <el-button @click="showBindDialog = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" @click="handleConfirmBind">{{ t('common.confirm') }}</el-button>
      </template>
    </el-dialog>

    <!-- Import fields from table dialog -->
    <el-dialog v-model="showImportFieldsDialog" :title="t('form.importFieldsTitle')" width="800px">
      <div class="import-fields-dialog">
        <el-alert type="info" :closable="false" style="margin-bottom: 16px;">
          {{ t('form.importFieldsHint') }}
          <span v-if="formBindings.length > 0" style="display: block; margin-top: 4px;">
            {{ t('form.importFieldsHintWithBindings', { count: formBindings.length }) }}
          </span>
        </el-alert>
        
        <el-form label-width="120px" label-position="left" style="margin-bottom: 16px;">
          <el-form-item :label="t('form.selectTable')">
            <el-select v-model="importTableId" :placeholder="t('form.selectTable')" style="width: 100%;" @change="handleTableChange">
              <el-option-group v-if="formBindings.length > 0" :label="t('form.boundTables')">
                <el-option 
                  v-for="binding in formBindings" 
                  :key="getImportOptionKey(binding)" 
                  :label="`${getBindingDisplayName(binding)} (${bindingTypeLabel(binding.bindingType)})`" 
                  :value="getImportOptionValue(binding)"
                >
                  <div class="table-option-with-binding">
                    <span>{{ getBindingDisplayName(binding) }}</span>
                    <el-tag size="small" :type="bindingTypeTag(binding.bindingType)">
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
        
        <div v-if="importTableId" class="field-selection">
          <div class="field-header">
            <el-checkbox 
              :model-value="isAllFieldsSelected" 
              :indeterminate="isFieldsIndeterminate"
              @change="(val: any) => handleSelectAllFields(!!val)"
            >
              {{ t('form.selectAll') }}
            </el-checkbox>
            <span class="field-count">{{ t('form.selectedCount', { count: selectedImportFields.length, total: availableFields.length }) }}</span>
            <el-tag v-if="getImportTableBinding()" size="small" :type="bindingTypeTag(getImportTableBinding()!.bindingType)" style="margin-left: 8px;">
              {{ bindingTypeLabel(getImportTableBinding()!.bindingType) }}
            </el-tag>
          </div>
          
          <el-table :data="availableFields" size="small" max-height="300">
            <el-table-column width="50">
              <template #default="{ row }">
                <el-checkbox 
                  :model-value="isFieldSelected(row.fieldName)"
                  @change="toggleFieldSelection(row)"
                />
              </template>
            </el-table-column>
            <el-table-column prop="fieldName" :label="t('form.fieldName')" width="150" />
            <el-table-column prop="dataType" :label="t('form.dataType')" width="100" />
            <el-table-column :label="t('form.formComponent')" width="120">
              <template #default="{ row }">
                <el-tag size="small">{{ getFormComponentType(row.dataType) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column :label="t('form.sourceTable')" width="120" v-if="formBindings.length > 0">
              <template #default>
                <span class="source-table">{{ getImportTableDisplayName() }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="description" :label="t('table.description')" show-overflow-tooltip />
            <el-table-column prop="nullable" :label="t('form.required')" width="60">
              <template #default="{ row }">
                <el-tag :type="row.nullable ? 'info' : 'danger'" size="small">
                  {{ row.nullable ? t('form.no') : t('form.yes') }}
                </el-tag>
              </template>
            </el-table-column>
          </el-table>
        </div>
        
        <el-empty v-else :description="t('form.selectTableFirst')" />
      </div>
      <template #footer>
        <el-button @click="showImportFieldsDialog = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" @click="handleConfirmImportFields" :disabled="selectedImportFields.length === 0">
          {{ t('form.importButton', { count: selectedImportFields.length }) }}
        </el-button>
      </template>
    </el-dialog>

    <!-- Manage table bindings dialog -->
    <el-dialog v-model="showBindingManagerDialog" :title="t('form.manageBindingsTitle')" width="700px" destroy-on-close>
      <TableBindingManager 
        v-if="bindingManagerForm"
        ref="bindingManagerRef"
        :function-unit-id="props.functionUnitId"
        :form-id="bindingManagerForm.id"
        :tables="store.tables"
        @update="handleBindingUpdate"
      />
      <template #footer>
        <el-button @click="showBindingManagerDialog = false">{{ t('form.closeButton') }}</el-button>
      </template>
    </el-dialog>

    <!-- Common Table Reference Field dialog -->
    <el-dialog v-model="showCommonTableRefDialog" :title="t('commonTable.addLookUpField')" width="600px" destroy-on-close>
      <el-alert type="info" :closable="false" style="margin-bottom:16px;">
        {{ t('commonTable.addFieldHint') }}
      </el-alert>
      <el-form label-width="180px" label-position="top" class="common-table-ref-form">
        <el-form-item :label="t('commonTable.selectCommonTable')">
          <el-select v-model="commonTableRefConfig.commonTableCode" style="width:100%;" :placeholder="t('commonTable.selectCommonTablePlaceholder')" @change="onCommonTableSelectChange">
            <el-option
              v-for="b in commonTableBindings"
              :key="(b as any).commonTableCode"
              :label="`${b.tableName || (b as any).commonTableCode}`"
              :value="(b as any).commonTableCode"
            />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('commonTable.displayField')">
          <el-select v-model="commonTableRefConfig.displayField" style="width:100%;" :placeholder="t('commonTable.displayFieldPlaceholder')" :disabled="!commonTableRefConfig.commonTableCode">
            <el-option
              v-for="f in getCommonTableFields(commonTableRefConfig.commonTableCode)"
              :key="f.fieldName"
              :label="`${f.displayName || f.fieldName}`"
              :value="f.fieldName"
            />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('commonTable.fieldName')">
          <el-select v-model="commonTableRefConfig.fieldName" style="width:100%;" :placeholder="t('commonTable.fieldNamePlaceholder')" :disabled="!commonTableRefConfig.commonTableCode" @change="onFieldNameChange">
            <el-option
              v-for="f in getCommonTableFields(commonTableRefConfig.commonTableCode)"
              :key="f.fieldName"
              :label="`${f.isPrimaryKey ? '🔑 ' : ''}${f.displayName || f.fieldName}`"
              :value="f.fieldName"
            />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('commonTable.variableName')">
          <el-input v-model="commonTableRefConfig.variableName" :placeholder="t('commonTable.variableNamePlaceholder')" />
          <div style="font-size:12px;color:#909399;margin-top:4px;">{{ t('commonTable.variableNameHint') }}</div>
        </el-form-item>
        <el-form-item :label="t('commonTable.displayLabel')">
          <el-input v-model="commonTableRefConfig.label" :placeholder="t('commonTable.displayLabelPlaceholder')" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCommonTableRefDialog = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" @click="insertCommonTableRefField"
          :disabled="!commonTableRefConfig.commonTableCode || !commonTableRefConfig.fieldName || !commonTableRefConfig.displayField || !commonTableRefConfig.variableName">
          {{ t('commonTable.insertField') }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, onBeforeUnmount, nextTick, computed, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ArrowLeft, Plus, Refresh, Connection, Link } from '@element-plus/icons-vue'
import { commonTableApi, type CommonTableDefinition } from '@/api/commonTable'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useFunctionUnitStore } from '@/stores/functionUnit'
import type { FormDefinition, FieldDefinition, TableBinding, BindingType } from '@/api/functionUnit'
import { functionUnitApi } from '@/api/functionUnit'
import TableBindingManager from './TableBindingManager.vue'
import SubTableField from './SubTableField.vue'

const { t } = useI18n()

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
const showPreviewDialog = ref(false)
const showBindDialog = ref(false)
const previewData = ref({})
const previewRule = ref<any[]>([])
const previewSubBindings = ref<Array<{
  bindingId: number
  bindingType: string
  bindingMode: string
  tableName: string
  tableType: string
  tableDescription: string
  commonTableCode?: string
  rule: any[]
}>>([])
const previewSubData = ref<Record<number, any>>({})
const previewTableRows = ref<Record<number, any[]>>({})
const previewLookupFieldTitles = ref<Record<string, string>>({})
const previewLookupEntries = ref<Array<{ fieldTitle: string; commonTableCode: string; tableName: string; rule: any[] }>>([])
// Sections for preview: regular form-field groups OR combined lookup sections (field + inline description)
const previewSections = ref<Array<{
  type: 'form' | 'lookupSection'
  // 'form' fields
  rule: any[]
  // 'lookupSection' fields
  fieldLabel?: string
  placeholder?: string
  lookupRule: any[]
}>>([])
const previewSectionData = ref<Record<number, any>>({})

// Sub-designer refs (one per non-PRIMARY binding)
const subDesignerRefs = ref<any[]>([])
// In-memory cache: persists sub form rules across tab switches (tabs unmount when not active)
const subFormCache = ref<Record<number, { rule: any[]; options: any }>>({})

function setSubDesignerRef(el: any, index: number) {
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
        } catch {}
      }
    }
  }
  subDesignerRefs.value[index] = el
}

// Active tab: 'main' or bindingId string
const activeDesignerTab = ref<string>('main')

// Non-PRIMARY bindings for tabs
const designerSubBindings = computed(() => {
  if (!selectedForm.value) return []
  const nonPrimary = (selectedForm.value.tableBindings || []).filter((b: TableBinding) => b.bindingType !== 'PRIMARY')
  return nonPrimary.map((b: TableBinding) => {
    const isCommon = !!(b as any).commonTableBinding
    return {
      bindingId: b.id as number,
      bindingType: b.bindingType,
      bindingMode: b.bindingMode,
      tableName: isCommon ? ((b as any).tableName || (b as any).commonTableCode || '') : (getTableName(b.tableId) || b.tableName || ''),
      tableType: isCommon ? 'COMMON' : ((store.tables.find(t => t.id === b.tableId)?.tableType) || ''),
      tableDescription: isCommon ? '' : ((store.tables.find(t => t.id === b.tableId)?.description) || ''),
      isCommonTable: isCommon,
      commonTableCode: (b as any).commonTableCode,
    }
  })
})

// Common table bindings of the selected form (for reference field panel)
const commonTableBindings = computed(() => {
  if (!selectedForm.value) return []
  return (selectedForm.value.tableBindings || []).filter((b: TableBinding) => (b as any).commonTableBinding)
})

// Common Table Reference Field dialog state
const showCommonTableRefDialog = ref(false)
const commonTableRefConfig = ref({ commonTableCode: '', fieldName: '', label: '', displayField: '', storeField: '', variableName: '' })
const loadedCommonTables = ref<CommonTableDefinition[]>([])

// Toolbar: detect if the currently selected designer rule is a lookup field
const currentActiveLookupCode = ref<string | null>(null)
let activeRulePollInterval: ReturnType<typeof setInterval> | null = null

function parseLookupCodeFromInfo(info: unknown): string | null {
  if (typeof info !== 'string') return null
  const normalized = info.startsWith('common Table Ref:')
    ? 'commonTableRef:' + info.slice('common Table Ref:'.length)
    : info
  if (!normalized.startsWith('commonTableRef:')) return null
  const parts = normalized.split(':')
  return parts[1]?.trim() || null
}

function startActiveRulePoll() {
  if (activeRulePollInterval) return
  activeRulePollInterval = setInterval(() => {
    try {
      let activeRef: any = null
      if (activeDesignerTab.value === 'main') {
        activeRef = designerRef.value
      } else {
        const idx = designerSubBindings.value.findIndex(b => String(b.bindingId) === activeDesignerTab.value)
        if (idx >= 0) activeRef = subDesignerRefs.value[idx]
      }
      const info = activeRef?.activeRule?.info
      currentActiveLookupCode.value = parseLookupCodeFromInfo(info)
    } catch {
      currentActiveLookupCode.value = null
    }
  }, 300)
}

function stopActiveRulePoll() {
  if (activeRulePollInterval) {
    clearInterval(activeRulePollInterval)
    activeRulePollInterval = null
  }
}

function navigateToLookupTab() {
  if (!currentActiveLookupCode.value) return
  const binding = designerSubBindings.value.find(b => b.isCommonTable && b.commonTableCode === currentActiveLookupCode.value)
  if (binding) {
    activeDesignerTab.value = String(binding.bindingId)
  } else {
    ElMessage.warning('No lookup form tab found for: ' + currentActiveLookupCode.value)
  }
}

async function loadCommonTablesForDesigner() {
  try {
    const res = await commonTableApi.list()
    loadedCommonTables.value = (res as any).data || res || []
  } catch (e) {
    console.warn('Failed to load common tables for designer:', e)
  }
}

function getCommonTableFields(code: string) {
  const ct = loadedCommonTables.value.find(t => t.code === code)
  return ct?.fieldDefinitions || []
}

function generateVariableName(code: string, fieldName: string): string {
  if (!code) return ''
  const suffix = fieldName ? fieldName.charAt(0).toUpperCase() + fieldName.slice(1) : 'Ref'
  return `${code}${suffix}`
}

function onCommonTableSelectChange(code: string) {
  commonTableRefConfig.value.displayField = ''
  commonTableRefConfig.value.fieldName = ''
  commonTableRefConfig.value.storeField = ''
  commonTableRefConfig.value.variableName = ''
  // Auto-select primary key field
  const fields = getCommonTableFields(code)
  const pkField = fields.find(f => f.isPrimaryKey)
  if (pkField) {
    commonTableRefConfig.value.fieldName = pkField.fieldName
    commonTableRefConfig.value.storeField = pkField.fieldName
    commonTableRefConfig.value.variableName = generateVariableName(code, pkField.fieldName)
  }
}

function onFieldNameChange(fieldName: string) {
  commonTableRefConfig.value.storeField = fieldName
  commonTableRefConfig.value.variableName = generateVariableName(commonTableRefConfig.value.commonTableCode, fieldName)
}

function insertCommonTableRefField() {
  if (!commonTableRefConfig.value.commonTableCode) return
  const storeField = commonTableRefConfig.value.storeField || commonTableRefConfig.value.fieldName || 'id'
  const varName = commonTableRefConfig.value.variableName || `${commonTableRefConfig.value.commonTableCode}Ref`
  const newField = {
    type: 'input',
    field: varName,
    title: commonTableRefConfig.value.label || commonTableRefConfig.value.commonTableCode,
    props: {
      placeholder: `${t('common.search')} ${commonTableRefConfig.value.label || commonTableRefConfig.value.commonTableCode}`,
      clearable: true,
    },
    // Extra metadata stored as component info for user portal rendering
    // Format: common Table Ref:<tableCode>:<displayField>:<storeField>
    info: `common Table Ref:${commonTableRefConfig.value.commonTableCode}:${commonTableRefConfig.value.displayField}:${storeField}`,
  }
  try {
    // Target designer: active sub tab or main form
    let targetRef: any = null
    if (activeDesignerTab.value !== 'main') {
      const bindingId = Number(activeDesignerTab.value)
      const index = designerSubBindings.value.findIndex(b => b.bindingId === bindingId)
      if (index >= 0) targetRef = subDesignerRefs.value[index]
    }
    if (!targetRef) targetRef = designerRef.value

    if (!targetRef || typeof targetRef.getRule !== 'function') {
      ElMessage.info(t('commonTable.manualAddHint') + commonTableRefConfig.value.commonTableCode)
      showCommonTableRefDialog.value = false
      commonTableRefConfig.value = { commonTableCode: '', fieldName: '', label: '', displayField: '', storeField: '', variableName: '' }
      return
    }

    const currentRules: any[] = targetRef.getRule() || []
    let newRules: any[]

    // Insert at selected position if user has selected a component in the designer
    const activeRule = targetRef.activeRule
    if (activeRule && (activeRule.field || activeRule._fc_id)) {
      const idx = currentRules.findIndex(
        (r: any) => r.field === activeRule.field || r._fc_id === activeRule._fc_id
      )
      if (idx >= 0) {
        newRules = [...currentRules.slice(0, idx + 1), newField, ...currentRules.slice(idx + 1)]
      } else {
        newRules = [...currentRules, newField]
      }
    } else {
      newRules = [...currentRules, newField]
    }

    targetRef.setRule(newRules)
    ElMessage.success(t('commonTable.insertSuccess'))
  } catch (e) {
    ElMessage.info(t('commonTable.manualAddHint') + commonTableRefConfig.value.commonTableCode)
  }
  showCommonTableRefDialog.value = false
  commonTableRefConfig.value = { commonTableCode: '', fieldName: '', label: '', displayField: '', storeField: '', variableName: '' }
}

const createForm = reactive({ formName: '', formType: 'MAIN', description: '', boundTableId: null as number | null })
const bindingForm = ref<FormDefinition | null>(null)

// Table binding management state
const showBindingManagerDialog = ref(false)
const bindingManagerForm = ref<FormDefinition | null>(null)
const processNodes = ref<ProcessNode[]>([])

// Import fields state
const showImportFieldsDialog = ref(false)
const importTableId = ref<number | string | null>(null)
const selectedImportFields = ref<FieldDefinition[]>([])
const formBindings = ref<TableBinding[]>([])
const commonTableFieldsCache = ref<FieldDefinition[]>([])

// Computed: available fields for selected table (regular table or common table)
const availableFields = computed(() => {
  if (!importTableId.value) return []
  const v = importTableId.value
  if (typeof v === 'string' && v.startsWith('ct-')) {
    return commonTableFieldsCache.value
  }
  const table = store.tables.find(t => t.id === v)
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
const designerConfig = {
  showDevice: true,
  showSave: false, // Use custom save button
  fieldReadonly: false,
}

// Default form options — label left-aligned
const defaultFormOption = { form: { labelPosition: 'left' } }

// Preview options
const previewOption = ref({
  submitBtn: false,
  resetBtn: false,
  form: { labelPosition: 'left' }
})

const formTypeLabel = (type: string) => {
  const map: Record<string, string> = { MAIN: t('form.mainForm'), SUB: t('form.subForm'), POPUP: t('form.popupForm'), ACTION: t('form.actionForm') }
  return map[type] || type
}

const nodeTypeLabel = (type: string) => {
  const map: Record<string, string> = { 
    userTask: t('form.nodeTypeUserTask'), 
    serviceTask: t('form.nodeTypeServiceTask'),
    startEvent: t('form.nodeTypeStartEvent'),
    endEvent: t('form.nodeTypeEndEvent')
  }
  return map[type] || type
}

const tableTypeLabel = (type: string) => {
  const map: Record<string, string> = { MAIN: t('form.mainForm'), SUB: t('form.subForm'), ACTION: t('form.actionForm'), RELATION: t('table.relations') }
  return map[type] || type
}

// Binding type label
const bindingTypeLabel = (type: BindingType): string => {
  const map: Record<BindingType, string> = { PRIMARY: t('form.bindingTypePrimary'), SUB: t('form.bindingTypeSub'), RELATED: t('form.bindingTypeRelated') }
  return map[type] || type
}

// Binding type tag color
const bindingTypeTag = (type: BindingType): 'primary' | 'success' | 'warning' | 'info' => {
  const map: Record<BindingType, 'primary' | 'success' | 'warning' | 'info'> = { PRIMARY: 'primary', SUB: 'success', RELATED: 'warning' }
  return map[type] || 'info'
}

// Get binding info for the currently selected import table
function getImportTableBinding(): TableBinding | undefined {
  if (!importTableId.value) return undefined
  const v = importTableId.value
  if (typeof v === 'string' && v.startsWith('ct-')) {
    const id = parseInt(v.slice(3), 10)
    return formBindings.value.find((b: any) => b.commonTableId === id)
  }
  return formBindings.value.find(b => b.tableId === v)
}

// Display name for a binding (handles common table)
function getBindingDisplayName(binding: TableBinding): string {
  if ((binding as any).commonTableBinding) {
    return (binding as any).tableName || (binding as any).commonTableCode || t('form.unknownTable')
  }
  return getTableName((binding as any).tableId)
}

// Option value for import table select (ct-{id} for common table, tableId for regular)
function getImportOptionValue(binding: TableBinding): number | string {
  if ((binding as any).commonTableBinding && (binding as any).commonTableId) {
    return `ct-${(binding as any).commonTableId}`
  }
  return (binding as any).tableId ?? 0
}

// Option key for import table select
function getImportOptionKey(binding: TableBinding): string | number {
  if ((binding as any).commonTableBinding && (binding as any).commonTableId) {
    return `ct-${(binding as any).commonTableId}`
  }
  return (binding as any).tableId ?? `binding-${(binding as any).id}`
}

// Display name for the currently selected import table
function getImportTableDisplayName(): string {
  if (!importTableId.value) return ''
  const v = importTableId.value
  if (typeof v === 'string' && v.startsWith('ct-')) {
    const binding = getImportTableBinding()
    return binding ? getBindingDisplayName(binding) : ''
  }
  return getTableName(v as number)
}

// Load common table fields when a common table is selected for import
watch(importTableId, async (val) => {
  commonTableFieldsCache.value = []
  if (typeof val === 'string' && val.startsWith('ct-')) {
    const id = parseInt(val.slice(3), 10)
    if (!isNaN(id)) {
      try {
        const res = await commonTableApi.getById(id)
        const ct = (res as any).data || res
        const fields = ct?.fieldDefinitions || []
        commonTableFieldsCache.value = fields.map((f: any) => ({
          fieldName: f.fieldName,
          displayName: f.displayName,
          dataType: f.dataType,
          nullable: f.nullable ?? true,
          description: f.description
        }))
      } catch {
        commonTableFieldsCache.value = []
      }
    }
  }
}, { immediate: true })

/**
 * Derive preview columns for sub-table based on table type
 */
function derivePreviewColumns(tableType: string): Array<{ field: string; label: string; type?: string }> {
  const defaults: Record<string, Array<{ field: string; label: string; type?: string }>> = {
    'SUB': [
      { field: 'item_name', label: t('preview.itemName') },
      { field: 'quantity', label: t('preview.quantity'), type: 'number' },
      { field: 'unit_price', label: t('preview.unitPrice'), type: 'number' },
      { field: 'amount', label: t('preview.amount'), type: 'number' },
      { field: 'remark', label: t('preview.remark') }
    ],
    'ACTION': [
      { field: 'action_type', label: t('preview.actionType') },
      { field: 'action_result', label: t('preview.actionResult') },
      { field: 'comment', label: t('preview.comment') },
      { field: 'operator', label: t('preview.operator') },
      { field: 'action_time', label: t('preview.actionTime'), type: 'date' }
    ],
    'RELATION': [
      { field: 'file_name', label: t('preview.fileName') },
      { field: 'file_type', label: t('preview.fileType') },
      { field: 'file_url', label: t('preview.fileUrl') },
      { field: 'upload_time', label: t('preview.uploadTime'), type: 'date' },
      { field: 'remark', label: t('preview.remark') }
    ]
  }
  return defaults[tableType] || [{ field: 'value', label: t('preview.value') }]
}

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
  await loadForms()
  // If we're currently in the designer view, refresh the selected form's bindings in place
  if (selectedForm.value) {
    try {
      const res = await functionUnitApi.getFormBindings(props.functionUnitId, selectedForm.value.id)
      selectedForm.value = { ...selectedForm.value, tableBindings: res.data || [] }
      // Reset sub designer state so new tabs render cleanly
      subDesignerRefs.value = []
      subFormCache.value = {}
    } catch {}
  }
}

/**
 * Get form component type by data type
 */
function getFormComponentType(dataType: string): string {
  const typeMap: Record<string, string> = {
    'VARCHAR': t('form.inputBox'),
    'TEXT': t('form.textArea'),
    'INTEGER': t('form.numberInput'),
    'BIGINT': t('form.numberInput'),
    'DECIMAL': t('form.numberInput'),
    'BOOLEAN': t('form.switch'),
    'DATE': t('form.datePicker'),
    'TIMESTAMP': t('form.dateTimePicker'),
    'FILE': t('form.fileUpload')
  }
  return typeMap[dataType] || t('form.inputBox')
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
function handleTableChange() {
  selectedImportFields.value = []
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
    
    // Auto-select primary table if bound
    const primaryBinding = formBindings.value.find(b => b.bindingType === 'PRIMARY')
    if (primaryBinding) {
      if ((primaryBinding as any).commonTableBinding && (primaryBinding as any).commonTableId) {
        importTableId.value = `ct-${(primaryBinding as any).commonTableId}`
      } else {
        importTableId.value = (primaryBinding as any).tableId ?? null
      }
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
function handleConfirmImportFields() {
  if (selectedImportFields.value.length === 0) {
    ElMessage.warning(t('form.selectAtLeastOne'))
    return
  }

  if (selectedForm.value) {
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
        targetRef.setRule([...currentRules, ...newRules])
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
  selectedForm.value = { ...row }
  subDesignerRefs.value = []
  subFormCache.value = {}
  activeDesignerTab.value = 'main'

  // Load table bindings
  functionUnitApi.getFormBindings(props.functionUnitId, row.id)
    .then(res => {
      if (selectedForm.value) {
        selectedForm.value = { ...selectedForm.value, tableBindings: res.data || [] }
      }
      // Load sub designers after bindings are known
      nextTick(() => setTimeout(() => loadSubDesigners(row), 200))
    })
    .catch(() => {})

  // Load main designer
  nextTick(() => {
    setTimeout(() => {
      if (designerRef.value) {
        const config = row.configJson || {}
        try {
          designerRef.value.setRule(config.rule && config.rule.length ? config.rule : [])
          designerRef.value.setOption(config.options && Object.keys(config.options).length ? config.options : defaultFormOption)
        } catch (e) {
          console.error('Failed to load main form config:', e)
          try { designerRef.value.setRule([]); designerRef.value.setOption(defaultFormOption) } catch {}
        }
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
            subRef.setRule(subConfig.rule && subConfig.rule.length ? subConfig.rule : [])
            subRef.setOption(subConfig.options && Object.keys(subConfig.options).length ? subConfig.options : defaultFormOption)
          } catch {}
        }
      }, 150)
    })
  })
}

function handleTabChange(tabName: string) {
  if (tabName === 'main') return
  const bindingId = Number(tabName)
  const index = designerSubBindings.value.findIndex(b => b.bindingId === bindingId)
  if (index < 0) return
  const config = selectedForm.value?.configJson || {}
  const subForms = config.subForms || {}
  nextTick(() => {
    setTimeout(() => {
      const subRef = subDesignerRefs.value[index]
      if (subRef) {
        // Use cache if available (user already visited this tab), else fall back to saved config
        const cached = subFormCache.value[bindingId]
        const subConfig = cached || subForms[bindingId] || {}
        try {
          subRef.setRule(subConfig.rule && subConfig.rule.length ? subConfig.rule : [])
          subRef.setOption(subConfig.options && Object.keys(subConfig.options).length ? subConfig.options : defaultFormOption)
        } catch {}
      }
    }, 100)
  })
}

function handleBackToList() {
  selectedForm.value = null
}

async function handleCreateForm() {
  if (!createForm.formName.trim()) {
    ElMessage.warning(t('form.enterFormName'))
    return
  }
  try {
    await store.createForm(props.functionUnitId, {
      formName: createForm.formName,
      formType: createForm.formType,
      description: createForm.description,
      boundTableId: createForm.boundTableId || undefined,
      configJson: { rule: [], options: {} }
    })
    ElMessage.success(t('form.createSuccess'))
    showCreateDialog.value = false
    Object.assign(createForm, { formName: '', formType: 'MAIN', description: '', boundTableId: null })
    loadForms()
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || t('form.createFailed'))
  }
}

async function handleSaveForm() {
  if (!selectedForm.value || !designerRef.value) return
  try {
    const rule = designerRef.value.getRule()
    const options = designerRef.value.getOption()

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

    await store.updateForm(props.functionUnitId, selectedForm.value.id, {
      formName: selectedForm.value.formName,
      formType: selectedForm.value.formType,
      description: selectedForm.value.description,
      configJson: { rule, options, subForms }
    })
    ElMessage.success(t('form.saveSuccess'))
    loadForms()
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || t('form.saveFailed'))
  }
}

async function handleDeleteForm(row: FormDefinition) {
  await ElMessageBox.confirm(t('form.deleteConfirm'), t('form.deleteTitle'), { type: 'warning' })
  try {
    await store.deleteForm(props.functionUnitId, row.id)
    ElMessage.success(t('form.deleteSuccess'))
    loadForms()
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || t('form.deleteFailed'))
  }
}

function handlePreview() {
  if (!designerRef.value || !selectedForm.value) return
  previewRule.value = designerRef.value.getRule()
  previewData.value = {}
  previewSubData.value = {}
  previewTableRows.value = {}

  // Sync label position from designer option
  try {
    const opt = designerRef.value.getOption() || {}
    previewOption.value = {
      submitBtn: false,
      resetBtn: false,
      form: {
        labelPosition: opt.form?.labelPosition || 'left',
        labelWidth: 'auto'
      }
    }
  } catch {
    previewOption.value = { submitBtn: false, resetBtn: false, form: { labelPosition: 'left', labelWidth: 'auto' } }
  }

  // Build map: commonTableCode → field title from main form rules
  const lookupTitles: Record<string, string> = {}
  for (const rule of previewRule.value) {
    const code = parseLookupCodeFromInfo(rule.info)
    if (code && rule.title) lookupTitles[code] = rule.title
  }
  previewLookupFieldTitles.value = lookupTitles

  const config = selectedForm.value.configJson || {}
  const subForms = config.subForms || {}
  const nonPrimary = (selectedForm.value.tableBindings || []).filter((b: TableBinding) => b.bindingType !== 'PRIMARY')

  previewSubBindings.value = nonPrimary.map((b: TableBinding) => {
    const bindingId = b.id as number
    const index = designerSubBindings.value.findIndex(d => d.bindingId === bindingId)
    const subRef = subDesignerRefs.value[index]
    let rule: any[] = []
    try {
      if (subRef) {
        rule = subRef.getRule() || []
      } else if (subFormCache.value[bindingId]) {
        rule = subFormCache.value[bindingId].rule || []
      } else {
        rule = subForms[bindingId]?.rule || []
      }
    } catch {
      rule = subFormCache.value[bindingId]?.rule || subForms[bindingId]?.rule || []
    }
    const isCommon = !!(b as any).commonTableBinding
    const tableName = isCommon ? ((b as any).tableName || (b as any).commonTableCode || '') : (getTableName((b as any).tableId) || b.tableName || '')
    // Initialize with one empty row
    previewTableRows.value[bindingId] = [{}]
    return {
      bindingId,
      bindingType: b.bindingType,
      bindingMode: b.bindingMode,
      tableName,
      tableType: isCommon ? 'COMMON' : (store.tables.find(t => t.id === (b as any).tableId)?.tableType || ''),
      tableDescription: isCommon ? '' : (store.tables.find(t => t.id === (b as any).tableId)?.description || ''),
      commonTableCode: isCommon ? (b as any).commonTableCode : undefined,
      rule
    }
  })

  // Build lookup entries (kept for backward compat)
  const entries: Array<{ fieldTitle: string; commonTableCode: string; tableName: string; rule: any[] }> = []
  for (const rule of previewRule.value) {
    const code = parseLookupCodeFromInfo(rule.info)
    if (!code) continue
    const binding = previewSubBindings.value.find(b => b.commonTableCode === code)
    if (binding) {
      entries.push({ fieldTitle: rule.title || rule.field || code, commonTableCode: code, tableName: binding.tableName, rule: binding.rule })
    }
  }
  previewLookupEntries.value = entries

  // Build sections: regular form groups OR combined lookupSection (field label + descriptions in same grid)
  const sectionsList: Array<{
    type: 'form' | 'lookupSection'
    rule: any[]
    fieldLabel?: string
    placeholder?: string
    lookupRule: any[]
  }> = []
  let formBatch: any[] = []
  for (const rule of previewRule.value) {
    const code = parseLookupCodeFromInfo(rule.info)
    if (code) {
      if (formBatch.length > 0) {
        sectionsList.push({ type: 'form', rule: [...formBatch], lookupRule: [] })
        formBatch = []
      }
      const binding = previewSubBindings.value.find(b => b.commonTableCode === code)
      sectionsList.push({
        type: 'lookupSection',
        rule: [],
        fieldLabel: rule.title || rule.field || code,
        placeholder: `Search ${rule.title || rule.field || code}`,
        lookupRule: binding?.rule || []
      })
    } else {
      formBatch.push(rule)
    }
  }
  if (formBatch.length > 0) {
    sectionsList.push({ type: 'form', rule: formBatch, lookupRule: [] })
  }
  previewSections.value = sectionsList
  previewSectionData.value = {}

  showPreviewDialog.value = true
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
  loadCommonTablesForDesigner()
  startActiveRulePoll()
})

onBeforeUnmount(() => {
  stopActiveRulePoll()
})
</script>


<style lang="scss" scoped>
.form-designer {
  height: 100%;
}

.form-list-view {
  padding: 0;
}

.designer-toolbar {
  margin-bottom: 16px;
}

.text-muted {
  color: #909399;
  font-size: 12px;
}

.bound-nodes {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.node-tag {
  margin: 0;
}

.bound-nodes-header {
  display: flex;
  gap: 4px;
}

.form-editor-view {
  height: 100%;
  display: flex;
  flex-direction: column;
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
    :deep(.form-create) {
      width: 100%;
    }

    :deep(.el-form-item) {
      margin-bottom: 18px;
      display: flex !important;
      align-items: flex-start !important;
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

    // lookupSection: CSS grid with 2 columns — label (max-content) | content (1fr)
    // Row 1: field label + disabled search input
    // Row 2: (empty)         + descriptions table
    // => descriptions automatically left-aligns with the search input
    .preview-lookup-section {
      display: grid;
      grid-template-columns: max-content 1fr;
      grid-template-rows: auto auto;
      column-gap: 12px;
      row-gap: 8px;
      margin-bottom: 18px;
      align-items: center;

      .preview-lookup-field-label {
        grid-column: 1;
        grid-row: 1;
        white-space: nowrap;
        text-align: right;
        font-size: 14px;
        color: #606266;
        padding-right: 0;
      }

      .preview-lookup-field-input {
        grid-column: 2;
        grid-row: 1;
      }

      // Descriptions table sits in column 2, row 2 — aligned with input above
      :deep(.preview-lookup-desc) {
        grid-column: 2;
        grid-row: 2;
        align-self: start;

        // Let the label cell auto-fit its content; no truncation, no wrapping
        .el-descriptions__label {
          white-space: nowrap;
          width: auto !important;
          min-width: unset !important;
          max-width: unset !important;
          overflow: visible !important;
          text-overflow: clip !important;
        }
      }
    }
  }
}

.bind-dialog-content {
  .node-list {
    max-height: 350px;
    overflow-y: auto;
  }
  
  .node-item {
    display: flex;
    align-items: center;
    gap: 12px;
    padding: 12px;
    border: 1px solid #e6e6e6;
    border-radius: 4px;
    margin-bottom: 8px;
    cursor: pointer;
    transition: all 0.2s;
    
    &:hover {
      border-color: #DB0011;
      background-color: rgba(219, 0, 17, 0.02);
    }
    
    &.selected {
      border-color: #DB0011;
      background-color: rgba(219, 0, 17, 0.08);
    }
    
    .node-icon {
      width: 32px;
      height: 32px;
      border-radius: 4px;
      
      &.userTask { background-color: #409EFF; }
      &.serviceTask { background-color: #67C23A; }
      &.startEvent { background-color: #00A651; border-radius: 50%; }
      &.endEvent { background-color: #DB0011; border-radius: 50%; }
    }
    
    .node-info {
      flex: 1;
      
      .node-name {
        font-weight: 500;
        margin-bottom: 2px;
      }
      
      .node-type {
        font-size: 12px;
        color: #909399;
      }
    }
    
    .check-icon {
      color: #DB0011;
      font-size: 20px;
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

.action-buttons {
  display: flex;
  flex-wrap: nowrap;
  gap: 4px;
  white-space: nowrap;
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

.common-table-ref-form {
  :deep(.el-form-item) {
    margin-bottom: 20px;
  }
  :deep(.el-form-item__label) {
    display: block;
    margin-bottom: 8px;
  }
}
</style>
