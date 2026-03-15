<template>
  <div class="designer-page" v-loading="loading">
    <!-- Top Tab Bar -->
    <div class="tab-bar">
      <div class="tab-bar-left">
        <button class="tab active">Table Designer</button>
      </div>
      <div class="tab-bar-actions">
        <el-button size="small" @click="loadTable">
          <el-icon><Refresh /></el-icon> Refresh
        </el-button>
      </div>
    </div>

    <!-- Content -->
    <div class="designer-content">
      <!-- Header row: Back + Title + Save + Deploy -->
      <div class="content-header">
        <el-button text size="small" @click="router.push('/common-tables')">
          <el-icon><ArrowLeft /></el-icon> Back to List
        </el-button>
        <h2 class="table-title">{{ form.code || 'New Table' }}</h2>
        <el-tag v-if="table?.status === 'PUBLISHED'" type="success" size="small" style="margin-right:8px;">Published v{{ table?.version }}</el-tag>
        <el-button @click="handleSave" :loading="saving" style="margin-right:8px;">Save</el-button>
        <el-button @click="openVersionHistory" style="margin-right:8px;">Version History</el-button>
        <el-button type="primary" @click="confirmDeploy" :loading="deploying">Deploy</el-button>
      </div>

      <!-- Basic Info Form -->
      <el-form :model="form" :rules="rules" ref="formRef" label-width="160px" label-position="left" class="basic-form">
        <el-form-item label="Table Name" prop="code">
          <el-input v-model="form.code" placeholder="e.g. customer_info" style="max-width:360px;" />
        </el-form-item>
        <el-form-item label="Table Display Name" prop="name">
          <el-input v-model="form.name" placeholder="Display name" style="max-width:360px;" />
        </el-form-item>
        <el-form-item label="Status">
          <el-select v-model="form.status" style="max-width:200px;">
            <el-option label="Draft" value="DRAFT" />
            <el-option label="Published" value="PUBLISHED" />
            <el-option label="Archived" value="ARCHIVED" />
          </el-select>
        </el-form-item>
        <el-form-item label="Description">
          <el-input v-model="form.description" type="textarea" :rows="2"
            placeholder="Table description" style="max-width:560px;" />
        </el-form-item>
      </el-form>

      <!-- Fields Section -->
      <div class="fields-section">
        <div class="fields-header">
          <span class="fields-title">Fields</span>
          <el-button size="small" @click="addField">Add Field</el-button>
        </div>

        <el-table
          :data="form.fields"
          border
          size="small"
          style="width:100%;"
          table-layout="fixed"
        >
          <el-table-column label="Field Name *" min-width="150">
            <template #default="{ row }">
              <el-input
                v-model="row.fieldName"
                size="small"
                placeholder="field_name"
                :class="{ 'field-error': saveAttempted && !row.fieldName?.trim() }"
              />
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
                <el-option label="FILE" value="FILE" />
              </el-select>
            </template>
          </el-table-column>

          <el-table-column label="Length" width="110">
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

          <el-table-column label="Description" min-width="160">
            <template #default="{ row }">
              <el-input v-model="row.description" size="small" placeholder="comment" />
            </template>
          </el-table-column>

          <el-table-column label="Operation" width="90" align="center" fixed="right">
            <template #default="{ $index }">
              <el-button
                link
                type="danger"
                size="small"
                :disabled="form.fields.length <= 1"
                @click="removeField($index)"
              >Delete</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </div>
  </div>

  <!-- Version History Dialog -->
  <el-dialog
    v-model="showVersionDialog"
    title="Version History"
    width="820px"
    :close-on-click-modal="false"
    destroy-on-close
  >
    <div class="version-dialog-header">
      <span class="version-table-label">Table: <strong>{{ form.code }}</strong></span>
      <el-tag v-if="table?.version" type="success" size="small">Current: v{{ table.version }}</el-tag>
    </div>

    <el-table
      :data="deployments"
      v-loading="loadingDeployments"
      stripe
      border
      size="small"
      style="width:100%;"
      :empty-text="'No deployment records yet'"
    >
      <el-table-column label="Version" width="90" align="center">
        <template #default="{ row }">
          <el-tag size="small" type="primary">v{{ row.version }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="Status" width="110" align="center">
        <template #default="{ row }">
          <el-tag :type="deployStatusType(row.status)" size="small">{{ row.status }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="Fields" width="80" align="center">
        <template #default="{ row }">
          {{ countSnapshotFields(row.fieldSnapshot) }}
        </template>
      </el-table-column>
      <el-table-column label="Deployed At" min-width="160">
        <template #default="{ row }">{{ formatDate(row.deployedAt) }}</template>
      </el-table-column>
      <el-table-column label="Deployed By" min-width="130" show-overflow-tooltip>
        <template #default="{ row }">{{ row.deployedBy || '-' }}</template>
      </el-table-column>
      <el-table-column label="Notes" min-width="140" show-overflow-tooltip>
        <template #default="{ row }">{{ row.notes || '-' }}</template>
      </el-table-column>
      <el-table-column label="Field Snapshot" width="100" align="center">
        <template #default="{ row }">
          <el-button
            v-if="row.fieldSnapshot"
            link
            type="primary"
            size="small"
            @click="showSnapshot(row)"
          >View</el-button>
          <span v-else>-</span>
        </template>
      </el-table-column>
    </el-table>

    <template #footer>
      <el-button @click="showVersionDialog = false">Close</el-button>
    </template>
  </el-dialog>

  <!-- Field Snapshot Dialog -->
  <el-dialog
    v-model="showSnapshotDialog"
    :title="`Field Snapshot — v${selectedDeployment?.version}`"
    width="700px"
    :close-on-click-modal="false"
  >
    <el-table
      :data="parsedSnapshotFields"
      border
      size="small"
      style="width:100%;"
    >
      <el-table-column label="Field Name" prop="fieldName" min-width="130" />
      <el-table-column label="Data Type" prop="dataType" width="110" align="center" />
      <el-table-column label="Length" prop="length" width="80" align="center">
        <template #default="{ row }">{{ row.length || '-' }}</template>
      </el-table-column>
      <el-table-column label="Nullable" width="80" align="center">
        <template #default="{ row }">
          <el-tag :type="row.nullable ? 'info' : 'warning'" size="small">{{ row.nullable ? 'Yes' : 'No' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="Primary Key" width="100" align="center">
        <template #default="{ row }">
          <el-tag v-if="row.isPrimaryKey" type="danger" size="small">PK</el-tag>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column label="Description" min-width="140" show-overflow-tooltip>
        <template #default="{ row }">{{ row.description || '-' }}</template>
      </el-table-column>
    </el-table>
    <template #footer>
      <el-button @click="showSnapshotDialog = false">Close</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft, Refresh } from '@element-plus/icons-vue'
import { commonTableApi, type CommonTableDefinition, type CommonFieldDefinition, type CommonTableDeployment } from '@/api/commonTable'

const route = useRoute()
const router = useRouter()

const tableId = Number(route.params.id)
const loading = ref(false)
const saving = ref(false)
const deploying = ref(false)
const saveAttempted = ref(false)

// Version History
const showVersionDialog = ref(false)
const loadingDeployments = ref(false)
const deployments = ref<CommonTableDeployment[]>([])
const showSnapshotDialog = ref(false)
const selectedDeployment = ref<CommonTableDeployment | null>(null)

const parsedSnapshotFields = computed(() => {
  if (!selectedDeployment.value?.fieldSnapshot) return []
  try {
    return JSON.parse(selectedDeployment.value.fieldSnapshot)
  } catch {
    return []
  }
})
const table = ref<CommonTableDefinition | null>(null)
const formRef = ref()

const form = reactive<{
  code: string
  name: string
  description: string
  status: string
  fields: CommonFieldDefinition[]
}>({
  code: '',
  name: '',
  description: '',
  status: 'DRAFT',
  fields: []
})

const rules = {
  code: [
    { required: true, message: 'Table Name is required', trigger: 'blur' },
    { pattern: /^[a-zA-Z][a-zA-Z0-9_]*$/, message: 'Must start with a letter, only letters/digits/underscores allowed', trigger: 'blur' }
  ],
  name: [{ required: true, message: 'Display Name is required', trigger: 'blur' }]
}

function onDataTypeChange(row: CommonFieldDefinition) {
  if (row.dataType !== 'VARCHAR') {
    row.length = undefined
  } else {
    row.length = 255
  }
}

function onPrimaryKeyChange(row: CommonFieldDefinition, index: number) {
  if (row.isPrimaryKey) {
    form.fields.forEach((f, i) => { if (i !== index) f.isPrimaryKey = false })
    row.nullable = false
  }
}

function addField() {
  form.fields.push({
    fieldName: '',
    displayName: '',
    dataType: 'VARCHAR',
    length: 255,
    isPrimaryKey: false,
    nullable: true,
    description: '',
    sortOrder: form.fields.length
  })
}

function removeField(index: number) {
  if (form.fields.length <= 1) {
    ElMessage.warning('At least one field is required')
    return
  }
  form.fields.splice(index, 1)
}

async function loadTable() {
  loading.value = true
  try {
    const res = await commonTableApi.getById(tableId)
    const data: CommonTableDefinition = (res as any).data || res
    table.value = data
    form.code = data.code
    form.name = data.name
    form.description = data.description || ''
    form.status = data.status || 'DRAFT'
    form.fields = (data.fieldDefinitions || []).map(f => ({ ...f }))
  } catch (e) {
    ElMessage.error('Failed to load table')
    router.push('/common-tables')
  } finally {
    loading.value = false
  }
}

async function handleSave() {
  try {
    await formRef.value?.validate()
  } catch {
    return
  }

  saveAttempted.value = true

  // Validate field names
  const emptyNameIndices = form.fields
    .map((f, i) => (!f.fieldName || !f.fieldName.trim() ? i + 1 : null))
    .filter(i => i !== null)
  if (emptyNameIndices.length > 0) {
    ElMessage.error(`Field Name is required. Please fill in row${emptyNameIndices.length > 1 ? 's' : ''}: ${emptyNameIndices.join(', ')}`)
    return
  }

  // Check for duplicate field names
  const names = form.fields.map(f => f.fieldName.trim().toLowerCase())
  const duplicates = names.filter((n, i) => names.indexOf(n) !== i)
  if (duplicates.length > 0) {
    ElMessage.error(`Duplicate Field Name: "${[...new Set(duplicates)].join('", "')}"`)
    return
  }

  saving.value = true
  try {
    await commonTableApi.update(tableId, {
      code: form.code,
      name: form.name,
      description: form.description,
      status: form.status,
      fields: form.fields.map((f, i) => ({ ...f, sortOrder: i }))
    })
    ElMessage.success('Saved successfully')
    loadTable()
  } catch (e) {
    ElMessage.error('Save failed')
  } finally {
    saving.value = false
  }
}

async function confirmDeploy() {
  try {
    await ElMessageBox.confirm(
      `Deploy "${form.code}" to Admin Center? This will publish the table and make it available in User Portal after enabling.`,
      'Confirm Deploy',
      { type: 'warning', confirmButtonText: 'Deploy', cancelButtonText: 'Cancel' }
    )
  } catch {
    return
  }
  deploying.value = true
  try {
    const res = await commonTableApi.deploy(tableId)
    const deployed = (res as any).data || res
    ElMessage.success(`Deployed successfully as version ${deployed.version}`)
    loadTable()
  } catch (e) {
    ElMessage.error('Deploy failed')
  } finally {
    deploying.value = false
  }
}

async function openVersionHistory() {
  showVersionDialog.value = true
  loadingDeployments.value = true
  try {
    const res = await commonTableApi.getDeployments(tableId)
    deployments.value = (res as any).data || res || []
  } catch {
    ElMessage.error('Failed to load version history')
  } finally {
    loadingDeployments.value = false
  }
}

function showSnapshot(dep: CommonTableDeployment) {
  selectedDeployment.value = dep
  showSnapshotDialog.value = true
}

function countSnapshotFields(snapshot?: string): number {
  if (!snapshot) return 0
  try {
    const arr = JSON.parse(snapshot)
    return Array.isArray(arr) ? arr.length : 0
  } catch {
    return 0
  }
}

type TagType = '' | 'success' | 'info' | 'warning' | 'danger'
function deployStatusType(status: string): TagType {
  const map: Record<string, TagType> = {
    COMPLETED: 'success',
    ROLLED_BACK: 'warning',
    FAILED: 'danger'
  }
  return map[status] ?? 'info'
}

function formatDate(val?: string): string {
  if (!val) return '-'
  const d = new Date(val)
  if (isNaN(d.getTime())) return val
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

onMounted(loadTable)
</script>

<style lang="scss" scoped>
.designer-page {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: #f5f5f5;
}

/* Tab bar */
.tab-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #fff;
  border-bottom: 1px solid #e4e7ed;
  padding: 0 20px;
  height: 44px;
  flex-shrink: 0;
}
.tab-bar-left {
  display: flex;
  gap: 0;
  height: 100%;
}
.tab {
  height: 100%;
  padding: 0 20px;
  border: none;
  background: none;
  cursor: pointer;
  font-size: 14px;
  color: #606266;
  border-bottom: 3px solid transparent;
  white-space: nowrap;
  &.active {
    color: #c0392b;
    border-bottom-color: #c0392b;
    font-weight: 600;
  }
  &:hover:not(.active) {
    color: #303133;
  }
}
.tab-bar-actions {
  display: flex;
  gap: 8px;
  align-items: center;
}

/* Content */
.designer-content {
  flex: 1;
  overflow-y: auto;
  background: #fff;
  padding: 20px 28px;
}

/* Header row */
.content-header {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 24px;
  flex-wrap: nowrap;
}
.table-title {
  flex: 1;
  font-size: 20px;
  font-weight: 700;
  margin: 0;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

/* Basic form */
.basic-form {
  margin-bottom: 28px;
  :deep(.el-form-item) {
    margin-bottom: 16px;
  }
  :deep(.el-form-item__label) {
    white-space: nowrap;
    font-weight: 500;
    color: #303133;
  }
}

/* Fields section */
.fields-section {
  border-top: 1px solid #ebeef5;
  padding-top: 20px;
}
.fields-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}
.fields-title {
  font-size: 16px;
  font-weight: 700;
  color: #303133;
}

/* Table */
:deep(.el-table) {
  width: 100% !important;
}
:deep(.el-table .el-table__cell) {
  white-space: nowrap;
  padding: 6px 8px;
}
:deep(.el-table .cell) {
  white-space: nowrap;
  overflow: visible;
}
:deep(.el-table .el-input-number) {
  width: 100%;
}
:deep(.field-error .el-input__wrapper) {
  box-shadow: 0 0 0 1px #f56c6c inset;
}
.version-dialog-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
}
.version-table-label {
  font-size: 14px;
  color: #606266;
}
</style>
