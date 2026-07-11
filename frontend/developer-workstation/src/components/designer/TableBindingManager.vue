<template>
  <div class="table-binding-manager">
    <el-alert
      v-if="restrictPrimarySubOnly"
      type="info"
      :closable="false"
      show-icon
      class="binding-constraint-alert"
    >
      {{ t('tableBinding.primarySubOnlyHint') }}
    </el-alert>
    <!-- Binding list -->
    <div class="binding-list">
      <div class="binding-header">
        <span class="title">{{ t('tableBinding.title') }}</span>
        <el-button
          type="primary"
          size="small"
          @click="showAddDialog = true"
        >
          <el-icon><Plus /></el-icon> {{ t('tableBinding.addBinding') }}
        </el-button>
      </div>
      
      <div class="table-scroll-wrap">
      <el-table
        v-loading="loading"
        :data="bindings"
        size="small"
      >
        <el-table-column
          prop="tableName"
          :label="t('tableBinding.tableName')"
          min-width="120"
        >
          <template #default="{ row }">
            <span>{{ getTableName(row.tableId, row.tableName) }}</span>
          </template>
        </el-table-column>
        <el-table-column
          prop="bindingType"
          :label="t('tableBinding.bindingType')"
          width="100"
        >
          <template #default="{ row }">
            <el-tag
              :type="bindingTypeTag(row.bindingType)"
              size="small"
            >
              {{ bindingTypeLabel(row.bindingType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column
          prop="bindingMode"
          :label="t('tableBinding.mode')"
          width="80"
        >
          <template #default="{ row }">
            <el-tag
              :type="row.bindingMode === 'EDITABLE' ? 'success' : 'info'"
              size="small"
            >
              {{ row.bindingMode === 'EDITABLE' ? t('tableBinding.editable') : t('tableBinding.readOnly') }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column
          prop="foreignKeyField"
          :label="t('tableBinding.foreignKeyField')"
          width="120"
        >
          <template #default="{ row }">
            <span v-if="row.foreignKeyField">{{ row.foreignKeyField }}</span>
            <span
              v-else
              class="text-muted"
            >-</span>
          </template>
        </el-table-column>
        <el-table-column
          prop="bindingLinkMode"
          :label="t('tableBinding.linkMode')"
          width="130"
        >
          <template #default="{ row }">
            <span v-if="row.bindingType === 'SUB'">
              {{ bindingLinkModeLabel(row.bindingLinkMode) }}
            </span>
            <span
              v-else
              class="text-muted"
            >-</span>
          </template>
        </el-table-column>
        <el-table-column
          prop="subMode"
          :label="t('tableBinding.subMode')"
          width="130"
        >
          <template #default="{ row }">
            <span v-if="row.bindingType === 'SUB'">
              <el-tag
                :type="row.subMode === 'FULL' ? 'success' : 'info'"
                size="small"
              >
                {{ row.subMode === 'FULL' ? t('tableBinding.subModeFull') : t('tableBinding.subModeFormOnly') }}
              </el-tag>
            </span>
            <span
              v-else
              class="text-muted"
            >-</span>
          </template>
        </el-table-column>
        <el-table-column
          :label="t('tableBinding.operations')"
          min-width="120"
          fixed="right"
        >
          <template #default="{ row }">
            <div class="table-row-actions">
              <el-button
                link
                type="primary"
                size="small"
                @click="handleEdit(row)"
              >
                {{ t('common.edit') }}
              </el-button>
              <el-button
                link
                type="danger"
                size="small"
                :disabled="row.bindingType === 'PRIMARY'"
                @click="handleDelete(row)"
              >
                {{ t('common.delete') }}
              </el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
      </div>
      
      <el-empty
        v-if="bindings.length === 0 && !loading"
        :description="t('tableBinding.noBindings')"
        :image-size="60"
      />
    </div>

    <!-- Add/Edit binding dialog -->
    <el-dialog 
      v-model="showAddDialog" 
      :title="editingBinding ? t('tableBinding.editBinding') : t('tableBinding.addBinding')" 
      width="500px"
      @close="resetForm"
    >
      <el-form
        ref="formRef"
        :model="bindingForm"
        :rules="formRules"
        label-width="auto"
        label-position="left"
      >
        <!-- Must-add-primary-first hint -->
        <el-alert
          v-if="needsPrimaryFirst && !editingBinding"
          type="warning"
          :closable="false"
          show-icon
          style="margin-bottom: 12px;"
        >
          <template #title>
            {{ t('tableBinding.primaryFirstHint') }}
          </template>
        </el-alert>

        <el-form-item
          :label="t('tableBinding.bindingType')"
          prop="bindingType"
        >
          <el-select
            v-model="bindingForm.bindingType"
            style="width: 100%"
            :disabled="!!editingBinding && editingBinding.bindingType === 'PRIMARY'"
            @change="handleBindingTypeChange"
          >
            <el-option
              :label="t('tableBinding.primaryTable')"
              value="PRIMARY"
              :disabled="hasPrimaryBinding && bindingForm.bindingType !== 'PRIMARY'"
            />
            <el-option
              :label="t('tableBinding.subTable')"
              value="SUB"
              :disabled="needsPrimaryFirst"
            />
            <el-option
              :label="t('tableBinding.relatedTable')"
              value="RELATED"
            />
          </el-select>
          <div
            v-if="bindingForm.bindingType === 'RELATED' && !editingBinding"
            class="form-item-tip"
          >
            {{ t('tableBinding.relatedForLookupHint') }}
          </div>
        </el-form-item>

        <!-- Sub binding mode (only show for SUB type) -->
        <el-form-item
          v-if="bindingForm.bindingType === 'SUB'"
          :label="t('tableBinding.subMode')"
        >
          <el-radio-group v-model="bindingForm.subMode">
            <el-radio :value="'FULL'">
              {{ t('tableBinding.subModeFull') }}
            </el-radio>
            <el-radio :value="'FORM_ONLY'">
              {{ t('tableBinding.subModeFormOnly') }}
            </el-radio>
          </el-radio-group>
          <div class="form-item-tip">
            {{ t('tableBinding.subModeTip') }}
          </div>
        </el-form-item>

        <el-form-item
          :label="t('tableBinding.selectTable')"
          prop="tableId"
        >
          <el-select 
            v-model="bindingForm.tableId" 
            :placeholder="selectTablePlaceholder" 
            style="width: 100%"
            :disabled="!!editingBinding || !bindingForm.bindingType"
            @change="handleTableSelect"
          >
            <el-option 
              v-for="table in filteredAvailableTables" 
              :key="table.id" 
              :label="table.displayLabel" 
              :value="table.id"
              :disabled="isTableBound(table.id)"
            />
            <template #empty>
              <div style="padding: 8px 12px; color: #909399; font-size: 12px;">
                {{ emptyTableListHint }}
              </div>
            </template>
          </el-select>
          <div
            v-if="bindingForm.bindingType === 'RELATED'"
            class="form-item-tip"
          >
            {{ t('tableBinding.deployedRelationTableTip') }}
          </div>
        </el-form-item>
        
        <el-form-item
          :label="t('tableBinding.bindingMode')"
          prop="bindingMode"
        >
          <el-radio-group
            v-model="bindingForm.bindingMode"
            :disabled="bindingForm.bindingType === 'RELATED'"
          >
            <el-radio :value="'EDITABLE'">
              {{ t('tableBinding.editable') }}
            </el-radio>
            <el-radio :value="'READONLY'">
              {{ t('tableBinding.readOnly') }}
            </el-radio>
          </el-radio-group>
        </el-form-item>
        
        <el-form-item
          v-if="bindingForm.bindingType === 'SUB'"
          :label="t('tableBinding.linkMode')"
          prop="bindingLinkMode"
        >
          <el-radio-group
            v-model="bindingForm.bindingLinkMode"
            @change="handleBindingLinkModeChange"
          >
            <el-radio :value="'structuralFk'">
              {{ t('tableBinding.linkModeStructuralFk') }}
            </el-radio>
            <el-radio :value="'miParticipantRow'">
              {{ t('tableBinding.linkModeMiParticipantRow') }}
            </el-radio>
          </el-radio-group>
          <div class="form-item-tip">
            {{ t('tableBinding.linkModeTip') }}
          </div>
        </el-form-item>

        <el-form-item
          v-if="bindingForm.bindingType === 'SUB' && bindingForm.bindingLinkMode === 'structuralFk'"
          :label="t('tableBinding.structuralFkFields')"
        >
          <div v-if="structuralFkFieldNames.length">
            <el-tag
              v-for="name in structuralFkFieldNames"
              :key="name"
              size="small"
              style="margin-right: 6px;"
            >
              {{ name }}
            </el-tag>
          </div>
          <span
            v-else
            class="text-muted"
          >{{ t('tableBinding.noStructuralFkFields') }}</span>
          <div class="form-item-tip">
            {{ t('tableBinding.structuralFkTip') }}
          </div>
        </el-form-item>
        
        <el-form-item 
          v-if="bindingForm.bindingType === 'SUB' && bindingForm.bindingLinkMode === 'miParticipantRow'" 
          :label="t('tableBinding.participantRowField')"
          prop="foreignKeyField"
        >
          <el-select 
            v-model="bindingForm.foreignKeyField" 
            :placeholder="t('tableBinding.selectForeignKey')" 
            style="width: 100%"
            clearable
          >
            <el-option 
              v-for="field in selectedTableFields" 
              :key="field.fieldName" 
              :label="`${field.fieldName} (${field.dataType})`" 
              :value="field.fieldName" 
            />
          </el-select>
          <div class="form-item-tip">
            {{ t('tableBinding.miParticipantRowTip') }}
          </div>
        </el-form-item>
      </el-form>
      
      <template #footer>
        <el-button @click="showAddDialog = false">
          {{ t('common.cancel') }}
        </el-button>
        <el-button
          type="primary"
          :loading="submitting"
          @click="handleSubmit"
        >
          {{ editingBinding ? t('common.save') : t('tableBinding.add') }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, watch, onMounted } from 'vue'
import { Plus } from '@element-plus/icons-vue'
import { useI18n } from 'vue-i18n'
import { type TableDefinition } from '@/api/functionUnit'
import { useTableBindingList } from '@/composables/tableBindingManager/useTableBindingList'
import { useTableBindingForm } from '@/composables/tableBindingManager/useTableBindingForm'
import { useTableBindingSubmit } from '@/composables/tableBindingManager/useTableBindingSubmit'

const { t } = useI18n()

const props = defineProps<{
  functionUnitId: number
  formId: number
  /** PROCESS / TASK：仅允许主表+子表一对多绑定，子表数据通过表单内子表组件增删改 */
  formType?: string
  tables: TableDefinition[]
}>()

/** PROCESS / TASK 表单走「一主多子 + RELATED 用于 Lookup」的模式；
 *  非 PROCESS / TASK 表单不强制这套主/子+外键规则。 */
const restrictPrimarySubOnly = computed(
  () => props.formType === 'PROCESS' || props.formType === 'TASK'
)

const emit = defineEmits<{
  (e: 'update'): void
  (e: 'add', payload: { tableId: number; bindingType: string; bindingId: number }): void
}>()

// 列表状态与展示/删除逻辑
const {
  loading,
  bindings,
  loadBindings,
  getTableName: getTableNameRaw,
  bindingTypeLabel,
  bindingTypeTag,
  tableTypeLabel,
  handleDelete,
} = useTableBindingList({
  functionUnitId: props.functionUnitId,
  getFormId: () => props.formId,
  t,
  emitUpdate: () => emit('update'),
})

// Get table name by ID
function getTableName(tableId: number, fallback?: string): string {
  return getTableNameRaw(props.tables, tableId, fallback)
}

// Add/Edit 表单状态与逻辑
const {
  submitting,
  showAddDialog,
  editingBinding,
  formRef,
  deployedRelationTables,
  bindingForm,
  formRules,
  hasPrimaryBinding,
  needsPrimaryFirst,
  filteredAvailableTables,
  selectTablePlaceholder,
  emptyTableListHint,
  selectedTableFields,
  structuralFkFieldNames,
  toRelationTableOptionId,
  bindingLinkModeLabel,
  isTableBound,
  handleBindingTypeChange,
  handleBindingLinkModeChange,
  handleTableSelect,
  handleEdit,
  resetForm,
} = useTableBindingForm({
  getTables: () => props.tables,
  bindings,
  restrictPrimarySubOnly,
  tableTypeLabel,
  t,
})

// 提交与后端错误映射
const { handleSubmit } = useTableBindingSubmit({
  functionUnitId: props.functionUnitId,
  getFormId: () => props.formId,
  formRef,
  bindingForm,
  submitting,
  editingBinding,
  showAddDialog,
  structuralFkFieldNames,
  deployedRelationTables,
  toRelationTableOptionId,
  reloadBindings: loadBindings,
  emitUpdate: () => emit('update'),
  emitAdd: (payload) => emit('add', payload),
  t,
})

// Reload when formId changes
watch(() => props.formId, () => {
  if (props.formId) {
    loadBindings()
  }
}, { immediate: true })

onMounted(() => {
  if (props.formId) {
    loadBindings()
  }
})

// Expose methods for parent component
defineExpose({
  loadBindings,
  bindings
})
</script>

<style lang="scss" scoped>
.table-binding-manager {
  .binding-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 12px;
    
    .title {
      font-weight: 500;
      font-size: 14px;
    }
  }
  
  .text-muted {
    color: #909399;
  }
  
  .form-item-tip {
    font-size: 12px;
    color: #909399;
    margin-top: 4px;
  }

  .binding-constraint-alert {
    margin-bottom: 12px;
  }
}
</style>
