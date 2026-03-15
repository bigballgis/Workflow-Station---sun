<template>
  <div class="page-container">
    <div class="card">
      <div class="filter-panel">
        <div class="filter-left">
          <el-input
            v-model="searchText"
            placeholder="Search by name or code"
            clearable
            style="width: 240px;"
            @clear="loadTables"
            @keyup.enter="loadTables"
          >
            <template #prefix><el-icon><Search /></el-icon></template>
          </el-input>
          <el-button @click="loadTables">Search</el-button>
        </div>
        <el-button v-if="canCreate" type="primary" @click="openCreateDialog">
          <el-icon><Plus /></el-icon>
          Create Table
        </el-button>
      </div>

      <el-table :data="filteredTables" v-loading="loading" stripe style="width:100%;">
        <el-table-column prop="code" label="Table Name" min-width="160" show-overflow-tooltip />
        <el-table-column prop="name" label="Display Name" min-width="160" show-overflow-tooltip />
        <el-table-column prop="description" label="Description" min-width="200" show-overflow-tooltip />
        <el-table-column prop="status" label="Status" width="110">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="Fields" width="80" align="center">
          <template #default="{ row }">{{ row.fieldDefinitions?.length || 0 }}</template>
        </el-table-column>
        <el-table-column label="Actions" width="160" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleEdit(row)">Edit</el-button>
            <el-button link type="danger" @click="handleDelete(row)" :disabled="!canDelete">Delete</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-empty v-if="filteredTables.length === 0 && !loading" description="No common tables found" />
    </div>

    <!-- Create Table Dialog -->
    <el-dialog
      v-model="showCreateDialog"
      title="Create Table"
      width="90vw"
      :close-on-click-modal="false"
      @close="resetForm"
      class="create-table-dialog"
    >
      <el-form :model="createForm" :rules="formRules" ref="formRef" label-position="top">
        <!-- Basic Info -->
        <div class="section-title">Table Info</div>
        <div class="basic-info-row">
          <el-form-item label="Table Name" prop="code" class="basic-item">
            <el-input v-model="createForm.code" placeholder="e.g. customer_info (letter/digit/underscore, start with letter)" />
          </el-form-item>
          <el-form-item label="Table Description" prop="description" class="basic-item">
            <el-input v-model="createForm.description" placeholder="Optional description" />
          </el-form-item>
        </div>

        <!-- Fields -->
        <div class="section-header">
          <div class="section-title" style="margin-bottom:0;">Field Configuration</div>
          <el-button type="primary" size="small" @click="addField">
            <el-icon><Plus /></el-icon> Add Field
          </el-button>
        </div>

        <el-table :data="createForm.fields" border size="small" class="fields-table" style="width:100%;" table-layout="fixed">
          <el-table-column label="Field Name" min-width="150">
            <template #default="{ row, $index }">
              <el-input
                v-model="row.fieldName"
                size="small"
                placeholder="field_name"
                :class="{ 'is-error': fieldErrors[$index]?.fieldName }"
              />
              <div v-if="fieldErrors[$index]?.fieldName" class="field-error">{{ fieldErrors[$index].fieldName }}</div>
            </template>
          </el-table-column>

          <el-table-column label="Data Type" min-width="130">
            <template #default="{ row }">
              <el-select v-model="row.dataType" size="small" style="width:100%;" @change="onDataTypeChange(row)">
              <el-option label="VARCHAR" value="VARCHAR" />
              <el-option label="INTEGER" value="INTEGER" />
              <el-option label="BIGINT" value="BIGINT" />
              <el-option label="DECIMAL" value="DECIMAL" />
              <el-option label="BOOLEAN" value="BOOLEAN" />
              <el-option label="DATE" value="DATE" />
              <el-option label="TIMESTAMP" value="TIMESTAMP" />
              <el-option label="TEXT" value="TEXT" />
              </el-select>
            </template>
          </el-table-column>

          <el-table-column label="Length" width="100">
            <template #default="{ row }">
              <el-input-number
                v-model="row.length"
                size="small"
                :min="1"
                :max="65535"
                controls-position="right"
                style="width:100%;"
                :disabled="row.dataType !== 'VARCHAR'"
              />
            </template>
          </el-table-column>

          <el-table-column label="Nullable" width="80" align="center">
            <template #default="{ row }">
              <el-checkbox v-model="row.nullable" :disabled="row.isPrimaryKey" />
            </template>
          </el-table-column>

          <el-table-column label="Primary Key" width="100" align="center">
            <template #default="{ row, $index }">
              <el-checkbox v-model="row.isPrimaryKey" @change="onPrimaryKeyChange(row, $index)" />
            </template>
          </el-table-column>

          <el-table-column label="Default" min-width="120">
            <template #default="{ row }">
              <el-input v-model="row.defaultValue" size="small" placeholder="default value" />
            </template>
          </el-table-column>

          <el-table-column label="Comment" min-width="140">
            <template #default="{ row }">
              <el-input v-model="row.description" size="small" placeholder="field comment" />
            </template>
          </el-table-column>

          <el-table-column label="Operation" width="90" align="center" fixed="right">
            <template #default="{ $index }">
              <el-button
                link
                type="danger"
                size="small"
                :disabled="createForm.fields.length <= 1"
                @click="removeField($index)"
              >
                Delete
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-form>

      <template #footer>
        <el-button @click="showCreateDialog = false">Cancel</el-button>
        <el-button type="primary" @click="handleCreate" :loading="creating">Create</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Plus } from '@element-plus/icons-vue'
import { commonTableApi, type CommonTableDefinition, type CommonFieldDefinition } from '@/api/commonTable'
import { permissions } from '@/utils/permission'

const router = useRouter()

const loading = ref(false)
const creating = ref(false)
const tables = ref<CommonTableDefinition[]>([])
const searchText = ref('')
const showCreateDialog = ref(false)
const formRef = ref()

interface FieldRow extends CommonFieldDefinition {
  dataType: string
  nullable: boolean
  isPrimaryKey: boolean
}

const createForm = reactive<{
  code: string
  description: string
  fields: FieldRow[]
}>({
  code: '',
  description: '',
  fields: []
})

const fieldErrors = ref<Record<number, { fieldName?: string }>>({})

const formRules = {
  code: [
    { required: true, message: 'Table Name is required', trigger: 'blur' },
    { pattern: /^[a-zA-Z][a-zA-Z0-9_]*$/, message: 'Must start with a letter, only letters/digits/underscores allowed', trigger: 'blur' }
  ]
}

const canCreate = computed(() => permissions.canCreate())
const canDelete = computed(() => permissions.canDelete())

const filteredTables = computed(() => {
  if (!searchText.value) return tables.value
  const q = searchText.value.toLowerCase()
  return tables.value.filter(t =>
    t.name.toLowerCase().includes(q) || t.code.toLowerCase().includes(q)
  )
})

function statusTagType(status: string) {
  if (status === 'PUBLISHED') return 'success'
  if (status === 'ARCHIVED') return 'info'
  return 'warning'
}

function statusLabel(status: string) {
  if (status === 'PUBLISHED') return 'Published'
  if (status === 'ARCHIVED') return 'Archived'
  return 'Draft'
}

function makeEmptyField(): FieldRow {
  return {
    fieldName: '',
    displayName: '',
    dataType: 'VARCHAR',
    length: 255,
    isPrimaryKey: false,
    nullable: true,
    defaultValue: '',
    description: '',
    sortOrder: 0
  }
}

function openCreateDialog() {
  createForm.code = ''
  createForm.description = ''
  createForm.fields = [makeEmptyField()]
  fieldErrors.value = {}
  showCreateDialog.value = true
}

function addField() {
  createForm.fields.push({ ...makeEmptyField(), sortOrder: createForm.fields.length })
}

function removeField(index: number) {
  if (createForm.fields.length <= 1) {
    ElMessage.warning('At least one field is required')
    return
  }
  createForm.fields.splice(index, 1)
  delete fieldErrors.value[index]
}

function onDataTypeChange(row: FieldRow) {
  if (row.dataType !== 'VARCHAR') {
    row.length = undefined
  } else {
    row.length = 255
  }
}

function onPrimaryKeyChange(row: FieldRow, index: number) {
  if (row.isPrimaryKey) {
    // Only one primary key allowed
    createForm.fields.forEach((f, i) => {
      if (i !== index) f.isPrimaryKey = false
    })
    // Primary key cannot be nullable
    row.nullable = false
  }
}

function validateFields(): boolean {
  const errors: Record<number, { fieldName?: string }> = {}
  const names = new Set<string>()
  let hasPrimaryKey = false

  for (let i = 0; i < createForm.fields.length; i++) {
    const f = createForm.fields[i]
    errors[i] = {}

    if (!f.fieldName || !f.fieldName.trim()) {
      errors[i].fieldName = 'Field Name is required'
    } else if (names.has(f.fieldName)) {
      errors[i].fieldName = 'Field Name must be unique'
    } else {
      names.add(f.fieldName)
    }

    if (f.isPrimaryKey) hasPrimaryKey = true
  }

  fieldErrors.value = errors

  const hasFieldErrors = Object.values(errors).some(e => Object.keys(e).length > 0)
  if (hasFieldErrors) return false

  if (!hasPrimaryKey) {
    ElMessage.error('At least one field must be designated as Primary Key')
    return false
  }

  return true
}

async function loadTables() {
  loading.value = true
  try {
    const res = await commonTableApi.list()
    tables.value = (res as any).data || res || []
  } catch (e) {
    ElMessage.error('Failed to load tables')
  } finally {
    loading.value = false
  }
}

function handleEdit(row: CommonTableDefinition) {
  router.push(`/common-tables/${row.id}`)
}

async function handleDelete(row: CommonTableDefinition) {
  try {
    await ElMessageBox.confirm('Are you sure to delete this common table? This cannot be undone.', 'Confirm', { type: 'warning' })
    await commonTableApi.delete(row.id)
    ElMessage.success('Deleted successfully')
    loadTables()
  } catch (e: any) {
    if (e !== 'cancel') ElMessage.error('Delete failed')
  }
}

async function handleCreate() {
  try {
    await formRef.value?.validate()
  } catch {
    return
  }

  if (!validateFields()) return

  creating.value = true
  try {
    const payload = {
      code: createForm.code,
      name: createForm.code, // use code as name for now
      description: createForm.description,
      status: 'DRAFT',
      fields: createForm.fields.map((f, i) => ({ ...f, sortOrder: i }))
    }
    await commonTableApi.create(payload)
    ElMessage.success('Common table created successfully')
    showCreateDialog.value = false
    loadTables()
  } catch (e) {
    ElMessage.error('Create failed')
  } finally {
    creating.value = false
  }
}

function resetForm() {
  createForm.code = ''
  createForm.description = ''
  createForm.fields = [makeEmptyField()]
  fieldErrors.value = {}
  formRef.value?.resetFields()
}

onMounted(loadTables)
</script>

<style lang="scss" scoped>
.page-container {
  padding: 20px;
  width: 100%;
}
.card {
  background: #fff;
  border-radius: 8px;
  padding: 20px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
  width: 100%;
}
.filter-panel {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
  flex-wrap: nowrap;
}
.filter-left {
  display: flex;
  gap: 8px;
  align-items: center;
  flex-wrap: nowrap;
}

/* Dialog styles */
.section-title {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 12px;
  padding-left: 2px;
  border-left: 3px solid #409eff;
  padding-left: 8px;
}
.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
  flex-wrap: nowrap;
}
.basic-info-row {
  display: flex;
  gap: 24px;
  flex-wrap: nowrap;
  margin-bottom: 16px;
}
.basic-item {
  flex: 1 1 0;
  min-width: 0;
  margin-bottom: 0;
}
.fields-table {
  width: 100%;
}
.field-error {
  color: #f56c6c;
  font-size: 12px;
  line-height: 1.4;
  margin-top: 2px;
}
:deep(.is-error .el-input__wrapper) {
  box-shadow: 0 0 0 1px #f56c6c inset;
}

/* List table */
:deep(.el-table) {
  width: 100% !important;
}
:deep(.el-table .el-table__cell) {
  white-space: nowrap;
}

/* Dialog table */
:deep(.create-table-dialog .el-dialog__body) {
  padding: 16px 24px;
  max-height: 75vh;
  overflow-y: auto;
}
:deep(.create-table-dialog .el-form-item__label) {
  white-space: nowrap;
  font-weight: 500;
}
:deep(.create-table-dialog .el-table .el-table__cell) {
  white-space: nowrap;
  padding: 6px 8px;
}
:deep(.create-table-dialog .el-table .el-table__cell .cell) {
  white-space: nowrap;
  overflow: visible;
}
</style>
