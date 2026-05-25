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
          width="120"
        >
          <template #default="{ row }">
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
          </template>
        </el-table-column>
      </el-table>
      
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
        label-width="120px"
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
          :label="t('tableBinding.foreignKeyField')"
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
            {{ t('tableBinding.foreignKeyTip') }}
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
import { ref, computed, watch, onMounted } from 'vue'
import { Plus } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { functionUnitApi, type TableBinding, type TableBindingRequest, type TableDefinition, type BindingType } from '@/api/functionUnit'
import { relationTableBindingApi, type RelationTableDTO } from '@/api/relationTable'
import { pickHttpErrorBodyMessage, resolveUserFacingHttpMessage } from '@/utils/httpErrorMessage'

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
}>()

const loading = ref(false)
const submitting = ref(false)
const bindings = ref<TableBinding[]>([])
const showAddDialog = ref(false)
const editingBinding = ref<TableBinding | null>(null)
const formRef = ref<FormInstance>()
const deployedRelationTables = ref<RelationTableDTO[]>([])

function toRelationTableOptionId(tableId: number): number {
  return tableId < 0 ? tableId : -tableId
}

function makeEmptyBindingForm(): TableBindingRequest {
  // 默认类型：若尚无 PRIMARY 就让用户先建 PRIMARY，否则默认 SUB
  const defaultType: BindingType = bindings.value.some(b => b.bindingType === 'PRIMARY') ? 'SUB' : 'PRIMARY'
  return {
    tableId: undefined as unknown as number,
    bindingType: defaultType,
    bindingMode: defaultType === 'PRIMARY' ? 'EDITABLE' : 'READONLY',
    foreignKeyField: undefined,
    subMode: defaultType === 'SUB' ? 'FULL' : undefined
  }
}

const bindingForm = ref<TableBindingRequest>(makeEmptyBindingForm())

const formRules = computed<FormRules>(() => {
  const base: FormRules = {
    tableId: [{ required: true, message: t('tableBinding.selectTableRequired'), trigger: 'change' }],
    bindingType: [{ required: true, message: t('tableBinding.selectBindingTypeRequired'), trigger: 'change' }],
    bindingMode: [{ required: true, message: t('tableBinding.selectBindingModeRequired'), trigger: 'change' }]
  }
  if (restrictPrimarySubOnly.value && (bindingForm.value.bindingType === 'SUB')) {
    base.foreignKeyField = [
      { required: true, message: t('tableBinding.foreignKeyRequired'), trigger: 'change' }
    ]
  }
  return base
})

// Whether a primary binding already exists
const hasPrimaryBinding = computed(() => {
  return bindings.value.some(b => b.bindingType === 'PRIMARY')
})

/** 对 PROCESS / TASK 表单：SUB 必须在有 PRIMARY 之后才能添加（后端 SUB_REQUIRES_PRIMARY）。
 *  RELATED 不受此限制（它是 Lookup 参考数据，与主从关系无关）。 */
const needsPrimaryFirst = computed(
  () => restrictPrimarySubOnly.value && !hasPrimaryBinding.value
)

// Available tables
const availableTables = computed(() => {
  return props.tables
})

// Filtered tables based on binding type — 严格按类型过滤，避免用户在 SUB 里看到 MAIN 再报错
const filteredAvailableTables = computed(() => {
  const bt = bindingForm.value.bindingType
  if (bt === 'PRIMARY') {
    return props.tables
      .filter(t => t.tableType === 'MAIN')
      .map(t => ({ id: t.id, displayLabel: `${t.tableName} (${tableTypeLabel(t.tableType)})`, fieldDefinitions: t.fieldDefinitions }))
  }
  if (bt === 'SUB') {
    return props.tables
      .filter(t => t.tableType === 'SUB')
      .map(t => ({ id: t.id, displayLabel: `${t.tableName} (${tableTypeLabel(t.tableType)})`, fieldDefinitions: t.fieldDefinitions }))
  }
  // RELATED：列出本功能单元的 RELATION 表 + 管理中心已部署的关联表
  const localRelation = props.tables
    .filter(t => t.tableType === 'RELATION')
    .map(t => ({ id: t.id, displayLabel: `${t.tableName} (${tableTypeLabel(t.tableType)})`, fieldDefinitions: t.fieldDefinitions }))
  const deployedLabel = t('tableBinding.deployedRelationTable')
  const remote = deployedRelationTables.value.map(r => ({
    id: toRelationTableOptionId(r.id), // negative ID to distinguish from local tables
    displayLabel: `${r.displayName || r.tableName} (${deployedLabel})`,
    fieldDefinitions: r.fieldDefinitions || []
  }))
  return [...localRelation, ...remote]
})

const selectTablePlaceholder = computed(() => {
  if (needsPrimaryFirst.value) return t('tableBinding.primaryFirstHint')
  return t('tableBinding.selectTablePlaceholder')
})

const emptyTableListHint = computed(() => {
  const bt = bindingForm.value.bindingType
  if (bt === 'PRIMARY') return t('tableBinding.noMainTableAvailable')
  if (bt === 'SUB') return t('tableBinding.noSubTableAvailable')
  if (bt === 'RELATED') return t('tableBinding.noRelationTableAvailable')
  return t('tableBinding.selectBindingTypeFirst')
})

// Fields of the selected table
const selectedTableFields = computed(() => {
  if (!bindingForm.value.tableId) return []
  if (bindingForm.value.tableId > 0) {
    const table = props.tables.find(t => t.id === bindingForm.value.tableId)
    return table?.fieldDefinitions || []
  }
  // For deployed/system relation tables (negative ID), look up from loaded data.
  const table = deployedRelationTables.value.find(t => toRelationTableOptionId(t.id) === bindingForm.value.tableId)
  return table?.fieldDefinitions || []
})

// Check if table is already bound
function isTableBound(tableId: number): boolean {
  if (editingBinding.value?.tableId === tableId) return false
  return bindings.value.some(b => {
    if (bindingForm.value.bindingType === 'RELATED' && b.bindingType === 'RELATED') {
      return toRelationTableOptionId(b.tableId) === tableId
    }
    return b.tableId === tableId
  })
}

// Get table name by ID
function getTableName(tableId: number, fallback?: string): string {
  const table = props.tables.find(t => t.id === tableId)
  return table?.tableDisplayName || table?.tableName || fallback || t('tableBinding.unknownTable')
}

// Binding type label
function bindingTypeLabel(type: BindingType): string {
  const map: Record<BindingType, string> = {
    PRIMARY: t('tableBinding.primaryTable'),
    SUB: t('tableBinding.subTable'),
    RELATED: t('tableBinding.relatedTable')
  }
  return map[type] || type
}

// Binding type tag color
function bindingTypeTag(type: BindingType): 'primary' | 'success' | 'warning' | 'info' | 'danger' {
  const map: Record<BindingType, 'primary' | 'success' | 'warning' | 'info' | 'danger'> = { PRIMARY: 'primary', SUB: 'success', RELATED: 'warning' }
  return map[type] || 'info'
}

// Table type label
function tableTypeLabel(type: string): string {
  const map: Record<string, string> = {
    MAIN: t('tableBinding.mainTableType'),
    SUB: t('tableBinding.subTableType'),
    ACTION: t('tableBinding.actionTableType'),
    RELATION: t('tableBinding.relationTableType')
  }
  return map[type] || type
}

// Load bindings
async function loadBindings() {
  loading.value = true
  try {
    const res = await functionUnitApi.getFormBindings(props.functionUnitId, props.formId)
    bindings.value = res.data || []
  } catch (e: any) {
    console.error('Failed to load bindings:', e)
    bindings.value = []
  } finally {
    loading.value = false
  }
}

// Handle binding type change - reset table selection
function handleBindingTypeChange() {
  bindingForm.value.tableId = undefined as unknown as number
  bindingForm.value.foreignKeyField = undefined
  if (bindingForm.value.bindingType === 'SUB') {
    bindingForm.value.subMode = bindingForm.value.subMode || 'FULL'
  } else {
    bindingForm.value.subMode = undefined
  }
  // RELATED type must be READONLY
  if (bindingForm.value.bindingType === 'RELATED') {
    bindingForm.value.bindingMode = 'READONLY'
  }
  if (
    bindingForm.value.bindingType === 'RELATED'
    && deployedRelationTables.value.length === 0
  ) {
    loadDeployedRelationTables()
  }
}

// Load deployed relation tables from admin center
async function loadDeployedRelationTables() {
  try {
    const res = await relationTableBindingApi.getAvailableTables()
    deployedRelationTables.value = res.data || []
  } catch (e: any) {
    console.error('Failed to load deployed relation tables:', e)
    deployedRelationTables.value = []
  }
}

// Handle table selection change — 仅调整 bindingMode 默认值，bindingType 由用户主动选择，
// 这样 filteredAvailableTables 已经保证了 tableType 与 bindingType 一致，不会再出现后端报错
function handleTableSelect(_tableId?: number) {
  const bt = bindingForm.value.bindingType
  if (bt === 'PRIMARY') {
    bindingForm.value.bindingMode = 'EDITABLE'
  } else {
    bindingForm.value.bindingMode = 'READONLY'
  }
}

// Edit binding
function handleEdit(binding: TableBinding) {
  editingBinding.value = binding
  bindingForm.value = {
    tableId: binding.tableId,
    bindingType: binding.bindingType,
    bindingMode: binding.bindingMode,
    foreignKeyField: binding.foreignKeyField,
    sortOrder: binding.sortOrder,
    subMode: binding.bindingType === 'SUB' ? (binding.subMode || 'FULL') : undefined
  }
  showAddDialog.value = true
}

// Delete binding
async function handleDelete(binding: TableBinding) {
  if (binding.bindingType === 'PRIMARY') {
    ElMessage.warning(t('tableBinding.cannotDeletePrimary'))
    return
  }
  
  await ElMessageBox.confirm(t('tableBinding.deleteConfirm'), t('tableBinding.confirmTitle'), { type: 'warning' })
  
  try {
    await functionUnitApi.deleteFormBinding(props.functionUnitId, props.formId, binding.id!)
    ElMessage.success(t('tableBinding.deleteSuccess'))
    loadBindings()
    emit('update')
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || t('tableBinding.deleteFailed'))
  }
}

/** 从响应体取出业务错误码（platform.common 与手写 JSON） */
function extractBindingErrorCode(data: unknown): string | undefined {
  if (!data || typeof data !== 'object') return undefined
  const o = data as Record<string, unknown>
  const nested = o.error
  if (nested && typeof nested === 'object') {
    const e = nested as Record<string, unknown>
    const c = e.code ?? e.errorCode
    if (typeof c === 'string' && c.trim()) return c.trim()
  }
  const top = o.code ?? o.errorCode
  if (typeof top === 'string' && top.trim()) return top.trim()
  return undefined
}

/** 把后端业务错误码映射成对用户友好的提示 */
function mapBackendError(err: any): string {
  const data = err?.response?.data
  const code = extractBindingErrorCode(data)
  const codeMap: Record<string, string> = {
    SUB_REQUIRES_PRIMARY: t('tableBinding.primaryFirstHint'),
    PRIMARY_BINDING_EXISTS: t('tableBinding.primaryBindingExists'),
    BINDING_EXISTS: t('tableBinding.bindingExists'),
    PRIMARY_REQUIRES_MAIN_TABLE: t('tableBinding.primaryRequiresMainTable'),
    SUB_BINDING_REQUIRES_SUB_TABLE: t('tableBinding.subBindingRequiresSubTable'),
    SUB_REQUIRES_FOREIGN_KEY: t('tableBinding.foreignKeyRequired'),
    INVALID_FOREIGN_KEY: t('tableBinding.invalidForeignKey'),
    RELATED_BINDING_REQUIRES_RELATION_TABLE: t('tableBinding.relatedBindingRequiresRelationTable'),
    SYS_INTERNAL_ERROR: t('api.serverError'),
    RES_NOT_FOUND: t('api.notFound'),
    VAL_INVALID_INPUT: t('api.invalidParams')
  }
  if (code && codeMap[code]) return codeMap[code]

  const fromBody = pickHttpErrorBodyMessage(data)
  if (fromBody) return fromBody

  return resolveUserFacingHttpMessage(err, t)
}

// Submit form
async function handleSubmit() {
  if (!formRef.value) return

  try {
    await formRef.value.validate()
  } catch {
    return
  }

  submitting.value = true
  try {
    // For deployed/system relation tables (negative ID), convert to relationTableId
    const requestData = { ...bindingForm.value }
    // tableId < 0 means it's a deployed/system relation table option
    if (requestData.tableId && requestData.tableId < 0) {
      const remoteTable = deployedRelationTables.value.find(t => toRelationTableOptionId(t.id) === requestData.tableId)
      requestData.relationTableId = remoteTable ? remoteTable.id : -requestData.tableId
      requestData.tableId = undefined
    }
    
    if (editingBinding.value) {
      await functionUnitApi.updateFormBinding(
        props.functionUnitId, 
        props.formId, 
        editingBinding.value.id!, 
        requestData
      )
      ElMessage.success(t('tableBinding.updateSuccess'))
    } else {
      await functionUnitApi.createFormBinding(props.functionUnitId, props.formId, requestData)
      ElMessage.success(t('tableBinding.addSuccess'))
    }
    showAddDialog.value = false
    loadBindings()
    emit('update')
  } catch (e: any) {
    console.error('[TableBindingManager] submit failed:', e?.response?.data || e)
    ElMessage({ type: 'error', message: mapBackendError(e), duration: 5000 })
  } finally {
    submitting.value = false
  }
}

// Reset form
function resetForm() {
  editingBinding.value = null
  bindingForm.value = makeEmptyBindingForm()
  formRef.value?.resetFields()
}

// 打开 Add 对话框前根据当前 bindings 刷新默认值，并预加载已部署的关联表（RELATED 选项可能被切换到）
watch(showAddDialog, (open) => {
  if (open) {
    if (!editingBinding.value) {
      bindingForm.value = makeEmptyBindingForm()
    }
    if (deployedRelationTables.value.length === 0) {
      loadDeployedRelationTables()
    }
  }
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
