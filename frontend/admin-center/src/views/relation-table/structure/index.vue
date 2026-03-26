<template>
  <div class="page-container">
    <div class="page-header">
      <span class="page-title">Table Structure</span>
      <el-button type="primary" @click="router.push('/relation-tables/structure/create')">
        <el-icon><Plus /></el-icon>Create Table
      </el-button>
    </div>

    <el-table :data="tableList" stripe v-loading="loading">
      <el-table-column prop="tableName" label="Name" min-width="140" />
      <el-table-column prop="displayName" label="Display Name" min-width="140" />
      <el-table-column prop="currentVersion" label="Version" width="90" align="center">
        <template #default="{ row }">
          v{{ row.currentVersion }}
        </template>
      </el-table-column>
      <el-table-column prop="status" label="Status" width="110" align="center">
        <template #default="{ row }">
          <el-tag :type="statusTagType(row.status)" size="small">{{ row.status }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="Enable" width="80" align="center">
        <template #default="{ row }">
          <el-switch
            v-model="row.enabled"
            :loading="row._enableLoading"
            @change="(val: string | number | boolean) => handleToggleEnabled(row, val as boolean)"
          />
        </template>
      </el-table-column>
      <el-table-column label="Portal Visibility" width="130" align="center">
        <template #default="{ row }">
          <el-switch
            v-model="row.portalVisible"
            :loading="row._portalLoading"
            :disabled="!row.enabled"
            @change="(val: string | number | boolean) => handleTogglePortalVisibility(row, val as boolean)"
          />
        </template>
      </el-table-column>
      <el-table-column prop="createdAt" label="Created At" width="170">
        <template #default="{ row }">
          {{ formatDate(row.createdAt) }}
        </template>
      </el-table-column>
      <el-table-column prop="createdBy" label="Created By" width="120" />
      <el-table-column prop="updatedAt" label="Updated At" width="170">
        <template #default="{ row }">
          {{ formatDate(row.updatedAt) }}
        </template>
      </el-table-column>
      <el-table-column prop="updatedBy" label="Updated By" width="120" />
      <el-table-column label="Actions" width="380" fixed="right">
        <template #default="{ row }">
          <div style="display: flex; align-items: center; flex-wrap: nowrap; white-space: nowrap;">
            <el-button link type="warning" @click="handleEdit(row)">Edit</el-button>
            <el-button link type="danger" @click="handleDelete(row)">Delete</el-button>
            <el-button link type="primary" @click="handleDeploy(row)">Deploy</el-button>
            <el-button link type="danger" @click="handleRollback(row)">Rollback</el-button>
            <el-button link type="primary" @click="handleVersions(row)">Version</el-button>
            <el-button link type="primary" @click="handleAccess(row)">Access</el-button>
          </div>
        </template>
      </el-table-column>
    </el-table>

    <!-- Version History Dialog -->
    <VersionDialog
      v-model="showVersionDialog"
      :table-id="currentTable?.id"
      :table-name="currentTable?.tableName"
      @rollback-success="fetchTableList"
    />

    <!-- Access Config Dialog -->
    <AccessConfigDialog
      v-model="showAccessDialog"
      :table-id="currentTable?.id"
      :table-name="currentTable?.tableName"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onActivated } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import {
  relationTableStructureApi,
  type RelationTableResponse,
  type RelationTableStatus
} from '@/api/relationTable'
import VersionDialog from './components/VersionDialog.vue'
import AccessConfigDialog from './components/AccessConfigDialog.vue'

const router = useRouter()

const loading = ref(false)
const tableList = ref<(RelationTableResponse & { _enableLoading?: boolean; _portalLoading?: boolean })[]>([])
const currentTable = ref<RelationTableResponse | null>(null)
const showVersionDialog = ref(false)
const showAccessDialog = ref(false)

type TagType = 'success' | 'warning' | 'danger' | 'info'
const statusTagType = (status: RelationTableStatus): TagType => {
  const map: Record<RelationTableStatus, TagType> = {
    DRAFT: 'warning',
    DEPLOYED: 'success',
    ROLLBACK: 'danger',
    INIT: 'info',
    UPDATED: 'warning'
  }
  return map[status] || 'info'
}

const formatDate = (dateStr: string) => {
  if (!dateStr) return ''
  return new Date(dateStr).toLocaleString('zh-CN')
}

const fetchTableList = async () => {
  loading.value = true
  try {
    const result = await relationTableStructureApi.list()
    tableList.value = (result || []).map(t => ({ ...t, _enableLoading: false, _portalLoading: false }))
  } catch (e) {
    console.error('Failed to load table structures:', e)
  } finally {
    loading.value = false
  }
}

const handleToggleEnabled = async (row: RelationTableResponse & { _enableLoading?: boolean }, val: boolean) => {
  row._enableLoading = true
  try {
    await relationTableStructureApi.setEnabled(row.id, val)
    ElMessage.success(val ? 'Enabled' : 'Disabled')
  } catch {
    row.enabled = !val
  } finally {
    row._enableLoading = false
  }
}

const handleTogglePortalVisibility = async (row: RelationTableResponse & { _portalLoading?: boolean }, val: boolean) => {
  row._portalLoading = true
  try {
    await relationTableStructureApi.setPortalVisibility(row.id, val)
    ElMessage.success(val ? 'Portal visible' : 'Portal hidden')
  } catch {
    row.portalVisible = !val
  } finally {
    row._portalLoading = false
  }
}

const handleAccess = (row: RelationTableResponse) => {
  currentTable.value = row
  showAccessDialog.value = true
}

const handleDeploy = async (row: RelationTableResponse) => {
  try {
    await ElMessageBox.confirm(`Deploy table "${row.tableName}" to database?`, 'Confirm Deploy', { type: 'warning' })
    await relationTableStructureApi.deploy(row.id)
    ElMessage.success('Deployed successfully')
    fetchTableList()
  } catch (e: any) {
    if (e !== 'cancel') {
      console.error('Deploy failed:', e)
    }
  }
}

const handleVersions = (row: RelationTableResponse) => {
  currentTable.value = row
  showVersionDialog.value = true
}

const handleEdit = (row: RelationTableResponse) => {
  router.push(`/relation-tables/structure/${row.id}/edit`)
}

const handleRollback = (row: RelationTableResponse) => {
  currentTable.value = row
  showVersionDialog.value = true
}

const handleDelete = async (row: RelationTableResponse) => {
  try {
    await ElMessageBox.confirm(`Delete table "${row.tableName}"? This action cannot be undone.`, 'Confirm Delete', { type: 'warning' })
    await relationTableStructureApi.delete(row.id)
    ElMessage.success('Deleted successfully')
    fetchTableList()
  } catch (e: any) {
    if (e !== 'cancel') {
      console.error('Delete failed:', e)
    }
  }
}

onMounted(() => {
  fetchTableList()
})

onActivated(() => {
  fetchTableList()
})
</script>

<style scoped>
.page-container {
  padding: 20px;
}
</style>
