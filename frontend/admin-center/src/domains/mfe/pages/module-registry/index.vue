<template>
  <div class="mfe-page">
    <div class="page-header">
      <h2>{{ t('mfe.title') }}</h2>
      <el-button type="primary" @click="showCreateDialog">
        {{ t('mfe.createModule') }}
      </el-button>
    </div>

    <div class="filter-bar">
      <el-select v-model="filter.hostApp" :placeholder="t('mfe.hostApp')" style="width: 180px" @change="fetchData">
        <el-option label="user-portal" value="user-portal" />
        <el-option label="admin-center" value="admin-center" />
        <el-option label="developer-workstation" value="developer-workstation" />
      </el-select>
      <el-select v-model="filter.env" :placeholder="t('mfe.env')" style="width: 120px; margin-left: 12px" @change="fetchData">
        <el-option label="DEV" value="DEV" />
        <el-option label="SIT" value="SIT" />
        <el-option label="UAT" value="UAT" />
        <el-option label="PROD" value="PROD" />
      </el-select>
      <el-select v-model="filter.enabled" :placeholder="t('mfe.status')" style="width: 120px; margin-left: 12px" clearable @change="fetchData">
        <el-option :label="t('mfe.enabled')" :value="true" />
        <el-option :label="t('mfe.disabled')" :value="false" />
      </el-select>
      <div style="flex: 1"></div>
      <el-button type="primary" @click="showImportDialog">
        {{ t('mfe.importPackage') }}
      </el-button>
    </div>

    <el-table :data="tableData" v-loading="loading" stripe style="margin-top: 16px">
      <el-table-column prop="moduleCode" :label="t('mfe.moduleCode')" width="180" />
      <el-table-column prop="displayName" :label="t('mfe.displayName')" min-width="160" />
      <el-table-column prop="routePath" :label="t('mfe.routePath')" width="160" />
      <el-table-column prop="version" :label="t('mfe.version')" width="100" />
      <el-table-column prop="env" :label="t('mfe.env')" width="80" />
      <el-table-column prop="orderNo" :label="t('mfe.orderNo')" width="80" />
      <el-table-column :label="t('mfe.status')" width="90">
        <template #default="{ row }">
          <el-tag :type="row.enabled ? 'success' : 'info'" size="small">
            {{ row.enabled ? t('mfe.enabled') : t('mfe.disabled') }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column :label="t('common.operation')" width="340" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="showEditDialog(row)">{{ t('common.edit') }}</el-button>
          <el-button v-if="row.enabled" link type="warning" @click="handleDisable(row)">{{ t('mfe.disable') }}</el-button>
          <el-button v-else link type="success" @click="handleEnable(row)">{{ t('mfe.enable') }}</el-button>
          <el-button link type="primary" @click="showVersionDialog(row)">{{ t('mfe.switchVersion') }}</el-button>
          <el-button link type="danger" @click="showRollbackDialog(row)">{{ t('mfe.rollback') }}</el-button>
          <el-button link type="info" @click="showVersionsHistory(row)">{{ t('mfe.versionHistory') }}</el-button>
          <el-button link type="success" @click="handleExportPackage(row)">{{ t('mfe.exportPackage') }}</el-button>
          <el-button link type="warning" @click="handleHealthCheck(row)">{{ t('mfe.healthCheck') }}</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      v-model:current-page="page"
      v-model:page-size="size"
      :total="total"
      layout="total, prev, pager, next"
      style="margin-top: 16px"
      @current-change="fetchData"
    />

    <!-- Create / Edit Dialog -->
    <el-dialog v-model="dialogVisible" :title="isEditing ? t('mfe.editModule') : t('mfe.createModule')" width="600px">
      <el-form :model="form" label-width="140px">
        <el-form-item :label="t('mfe.hostApp')">
          <el-select v-model="form.hostApp" style="width: 100%">
            <el-option label="user-portal" value="user-portal" />
            <el-option label="admin-center" value="admin-center" />
            <el-option label="developer-workstation" value="developer-workstation" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('mfe.moduleCode')">
          <el-input v-model="form.moduleCode" />
        </el-form-item>
        <el-form-item :label="t('mfe.displayName')">
          <el-input v-model="form.displayName" />
        </el-form-item>
        <el-form-item :label="t('mfe.routePath')">
          <el-input v-model="form.routePath" />
        </el-form-item>
        <el-form-item :label="t('mfe.icon')">
          <el-input v-model="form.icon" placeholder="Bell" />
        </el-form-item>
        <el-form-item :label="t('mfe.orderNo')">
          <el-input-number v-model="form.orderNo" :min="0" :max="9999" />
        </el-form-item>
        <el-form-item :label="t('mfe.remoteEntryUrl')">
          <el-input v-model="form.remoteEntryUrl" />
        </el-form-item>
        <el-form-item :label="t('mfe.exposedModule')">
          <el-input v-model="form.exposedModule" placeholder="./App" />
        </el-form-item>
        <el-form-item :label="t('mfe.env')">
          <el-select v-model="form.env" style="width: 100%">
            <el-option label="DEV" value="DEV" />
            <el-option label="SIT" value="SIT" />
            <el-option label="UAT" value="UAT" />
            <el-option label="PROD" value="PROD" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('mfe.version')">
          <el-input v-model="form.version" placeholder="1.0.0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" @click="handleSave">{{ t('common.confirm') }}</el-button>
      </template>
    </el-dialog>

    <!-- Version Switch Dialog -->
    <el-dialog v-model="versionDialogVisible" :title="t('mfe.switchVersion')" width="450px">
      <el-form :model="versionForm" label-width="140px">
        <el-form-item :label="t('mfe.version')">
          <el-input v-model="versionForm.version" placeholder="1.0.3" />
        </el-form-item>
        <el-form-item :label="t('mfe.remoteEntryUrl')">
          <el-input v-model="versionForm.remoteEntryUrl" placeholder="https://cdn.example.com/module/1.0.3/remoteEntry.js" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="versionDialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" @click="handleSwitchVersion">{{ t('common.confirm') }}</el-button>
      </template>
    </el-dialog>

    <!-- Rollback Dialog -->
    <el-dialog v-model="rollbackDialogVisible" :title="t('mfe.rollback')" width="400px">
      <el-form :model="rollbackForm" label-width="140px">
        <el-form-item :label="t('mfe.targetVersion')">
          <el-input v-model="rollbackForm.targetVersion" placeholder="1.0.2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="rollbackDialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="danger" @click="handleRollback">{{ t('common.confirm') }}</el-button>
      </template>
    </el-dialog>

    <!-- Version History Dialog -->
    <el-dialog v-model="versionsDialogVisible" :title="t('mfe.versionHistory')" width="700px">
      <el-table :data="versionsData" v-loading="versionsLoading" stripe max-height="400">
        <el-table-column prop="version" :label="t('mfe.version')" width="120" />
        <el-table-column prop="remoteEntryUrl" :label="t('mfe.remoteEntryUrl')" min-width="280" />
        <el-table-column :label="t('mfe.active')" width="80">
          <template #default="{ row }">
            <el-tag :type="row.isActive ? 'success' : 'info'" size="small">
              {{ row.isActive ? t('common.yes') : t('common.no') }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdBy" :label="t('mfe.createdBy')" width="100" />
        <el-table-column prop="createdAt" :label="t('mfe.createdAt')" width="170" />
      </el-table>
    </el-dialog>

    <!-- Health Check Result Dialog -->
    <el-dialog v-model="healthDialogVisible" :title="t('mfe.healthCheckResult')" width="500px">
      <el-result v-if="healthResult" :icon="healthResult.status === 'HEALTHY' ? 'success' : 'error'"
        :title="healthResult.status === 'HEALTHY' ? t('mfe.healthy') : t('mfe.unhealthy')"
        :sub-title="healthResult.detail || ''">
      </el-result>
    </el-dialog>
  </div>

    <!-- Import Package Dialog -->
    <el-dialog v-model="importDialogVisible" :title="t('mfe.importPackageTitle')" width="600px">
      <el-form label-width="100px">
        <el-form-item :label="t('mfe.targetEnv')">
          <el-select v-model="importTargetEnv" style="width: 200px">
            <el-option label="DEV" value="DEV" />
            <el-option label="SIT" value="SIT" />
            <el-option label="UAT" value="UAT" />
            <el-option label="PROD" value="PROD" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('mfe.importPackage')">
          <el-upload
            :auto-upload="false"
            :on-change="handleFileChange"
            :limit="1"
            accept=".zip"
            drag
          >
            <div class="el-upload__text">{{ t('mfe.importFileHint') }}</div>
          </el-upload>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="importDialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :disabled="!importFile" :loading="importLoading" @click="handleImport">
          {{ t('mfe.confirmImport') }}
        </el-button>
      </template>
    </el-dialog>

</template>

<script setup lang="ts">
import { ref, onMounted, reactive } from 'vue'
import { useI18n } from 'vue-i18n'
import { listModules, createModule, updateModule, enableModule, disableModule, switchVersion, rollbackVersion, getVersions, healthCheck, exportPackage, importPackage } from '@/domains/mfe/api/mfe'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { ModuleVersion, HealthCheckResult } from '@/domains/mfe/types/mfe'

const { t } = useI18n()

const loading = ref(false)
const tableData = ref<any[]>([])
const page = ref(1)
const size = ref(20)
const total = ref(0)

const filter = reactive({
  hostApp: 'user-portal',
  env: 'DEV',
  enabled: undefined as boolean | undefined
})

// -- CRUD --
const dialogVisible = ref(false)
const isEditing = ref(false)
const editingId = ref<number | null>(null)
const form = ref({
  hostApp: 'user-portal',
  moduleCode: '',
  displayName: '',
  routePath: '',
  icon: '',
  orderNo: 100,
  remoteEntryUrl: '',
  exposedModule: './App',
  env: 'DEV',
  version: '1.0.0'
})

// -- Version --
const versionDialogVisible = ref(false)
const versionTargetId = ref<number | null>(null)
const versionForm = ref({ version: '', remoteEntryUrl: '' })

// -- Rollback --
const rollbackDialogVisible = ref(false)
const rollbackTargetId = ref<number | null>(null)
const rollbackForm = ref({ targetVersion: '' })

// -- Version History --
const versionsDialogVisible = ref(false)
const versionsLoading = ref(false)
const versionsData = ref<ModuleVersion[]>([])

// -- Health Check --
const healthDialogVisible = ref(false)
const healthResult = ref<HealthCheckResult | null>(null)

const fetchData = async () => {
  loading.value = true
  try {
    const res = await listModules({
      hostApp: filter.hostApp,
      env: filter.env,
      enabled: filter.enabled,
      page: page.value - 1,
      size: size.value
    })
    tableData.value = res.content || []
    total.value = res.totalElements || 0
  } catch (e: any) {
    ElMessage.error(e.response?.data?.error?.message || e.message || t('common.error'))
  } finally {
    loading.value = false
  }
}

const showCreateDialog = () => {
  isEditing.value = false
  editingId.value = null
  form.value = {
    hostApp: filter.hostApp,
    moduleCode: '',
    displayName: '',
    routePath: '',
    icon: '',
    orderNo: 100,
    remoteEntryUrl: '',
    exposedModule: './App',
    env: filter.env,
    version: '1.0.0'
  }
  dialogVisible.value = true
}

const showEditDialog = (row: any) => {
  isEditing.value = true
  editingId.value = row.id
  form.value = { ...row }
  dialogVisible.value = true
}

const handleSave = async () => {
  try {
    if (isEditing.value && editingId.value) {
      await updateModule(editingId.value, form.value)
      ElMessage.success(t('mfe.updated'))
    } else {
      await createModule(form.value)
      ElMessage.success(t('mfe.created'))
    }
    dialogVisible.value = false
    fetchData()
  } catch (e: any) {
    ElMessage.error(e.response?.data?.error?.message || e.message || t('common.error'))
  }
}

const handleEnable = async (row: any) => {
  try {
    await enableModule(row.id)
    ElMessage.success(t('mfe.enabled'))
    fetchData()
  } catch (e: any) {
    ElMessage.error(e.response?.data?.error?.message || e.message || t('common.error'))
  }
}

const handleDisable = async (row: any) => {
  try {
    await ElMessageBox.confirm(t('mfe.disableConfirm'), t('common.warning'), {
      confirmButtonText: t('common.confirm'),
      cancelButtonText: t('common.cancel'),
      type: 'warning'
    })
    await disableModule(row.id)
    ElMessage.success(t('mfe.disabled'))
    fetchData()
  } catch (e: any) {
    if (e !== 'cancel') {
      ElMessage.error(e.response?.data?.error?.message || e.message || t('common.error'))
    }
  }
}

const showVersionDialog = (row: any) => {
  versionTargetId.value = row.id
  versionForm.value = { version: row.version, remoteEntryUrl: row.remoteEntryUrl }
  versionDialogVisible.value = true
}

const handleSwitchVersion = async () => {
  try {
    await switchVersion(versionTargetId.value!, versionForm.value)
    ElMessage.success(t('mfe.versionSwitched'))
    versionDialogVisible.value = false
    fetchData()
  } catch (e: any) {
    ElMessage.error(e.response?.data?.error?.message || e.message || t('common.error'))
  }
}

const showRollbackDialog = (row: any) => {
  rollbackTargetId.value = row.id
  rollbackForm.value = { targetVersion: '' }
  rollbackDialogVisible.value = true
}

const handleRollback = async () => {
  try {
    await rollbackVersion(rollbackTargetId.value!, rollbackForm.value)
    ElMessage.success(t('mfe.rollbackSuccess'))
    rollbackDialogVisible.value = false
    fetchData()
  } catch (e: any) {
    ElMessage.error(e.response?.data?.error?.message || e.message || t('common.error'))
  }
}

const showVersionsHistory = async (row: any) => {
  versionsDialogVisible.value = true
  versionsLoading.value = true
  try {
    const res = await getVersions(row.id)
    versionsData.value = res
  } catch (e: any) {
    ElMessage.error(e.response?.data?.error?.message || e.message || t('common.error'))
  } finally {
    versionsLoading.value = false
  }
}

const handleHealthCheck = async (row: any) => {

// -- Export Package --
const handleExportPackage = async (row: any) => {
  try {
    const blob = await exportPackage(row.id)
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = row.moduleCode + '-' + (row.version || 'unknown') + '.zip'
    a.click()
    URL.revokeObjectURL(url)
    ElMessage.success(t('mfe.exportSuccess'))
  } catch (e: any) {
    const msg = e.response?.data?.error?.message || e.response?.data?.message || e.message || t('common.error')
    ElMessage.error(msg)
  }
}

// -- Import Package --
const importDialogVisible = ref(false)
const importTargetEnv = ref('SIT')
const importFile = ref<File | null>(null)
const importLoading = ref(false)

const showImportDialog = () => {
  importFile.value = null
  importTargetEnv.value = 'SIT'
  importDialogVisible.value = true
}

const handleFileChange = (file: any) => {
  importFile.value = file.raw
}

const handleImport = async () => {
  if (!importFile.value) return
  importLoading.value = true
  try {
    const res = await importPackage(importTargetEnv.value, importFile.value)
    if (res.success) {
      ElMessage.success(t('mfe.importPackageSuccess'))
      importDialogVisible.value = false
      fetchData()
    } else {
      ElMessage.error(res.error || t('common.error'))
    }
  } catch (e: any) {
    const msg = e.response?.data?.error?.message || e.response?.data?.message || e.message || t('common.error')
    ElMessage.error(msg)
  } finally {
    importLoading.value = false
  }
}

  try {
    const res = await healthCheck(row.id)
    healthResult.value = res
    healthDialogVisible.value = true
  } catch (e: any) {
    ElMessage.error(e.response?.data?.error?.message || e.message || t('common.error'))
  }
}

onMounted(fetchData)
</script>

<style scoped>
.mfe-page {
  padding: 20px;
}
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}
.page-header h2 {
  margin: 0;
  font-size: 20px;
}
.filter-bar {
  display: flex;
  align-items: center;
}
</style>
