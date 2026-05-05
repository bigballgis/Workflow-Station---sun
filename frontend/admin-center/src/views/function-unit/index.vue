<template>
  <div class="page-container">
    <PageHeader :title="t('menu.functionUnit')">
      <template #actions>
        <el-button type="primary" @click="showImportDialog = true">
          <el-icon><Upload /></el-icon>{{ t('common.import') }}
        </el-button>
      </template>
    </PageHeader>
    
    <el-tabs v-model="activeTab">
      <el-tab-pane :label="t('functionUnit.list')" name="list">
        <div style="margin-bottom: 16px; display: flex; align-items: center; gap: 12px;">
          <el-input
            v-model="searchKeyword"
            :placeholder="t('functionUnit.searchPlaceholder')"
            clearable
            style="width: 300px;"
          />
          <template v-if="selectedUnits.length > 0">
            <span style="color: #909399; font-size: 13px;">{{ t('functionUnit.selected', { count: selectedUnits.length }) }}</span>
            <el-button type="success" size="small" @click="handleBatchEnable">{{ t('functionUnit.batchEnable') }}</el-button>
            <el-button type="warning" size="small" @click="handleBatchDisable">{{ t('functionUnit.batchDisable') }}</el-button>
            <el-button type="danger" size="small" @click="handleBatchDelete">{{ t('functionUnit.batchDelete') }}</el-button>
          </template>
        </div>
        <el-table :data="filteredFunctionUnits" stripe v-loading="loading" @selection-change="handleSelectionChange">
          <el-table-column type="selection" width="50" />
          <el-table-column prop="name" :label="t('common.name')" />
          <el-table-column prop="code" :label="t('common.code')" />
          <el-table-column prop="version" :label="t('functionUnit.version')" />
          <el-table-column prop="status" :label="t('common.status')">
            <template #default="{ row }">
              <el-tag :type="functionUnitStatusType(row.status)">{{ t(functionUnitStatusKey(row.status)) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column :label="t('common.enable')" width="80">
            <template #default="{ row }">
              <el-switch
                v-model="row.enabled"
                :loading="row._enabledLoading"
                @change="() => handleEnabledChange(row, row.enabled)"
              />
            </template>
          </el-table-column>
          <el-table-column prop="updatedAt" :label="t('common.updateTime')" />
          <el-table-column :label="t('common.actions')" width="360" fixed="right">
            <template #default="{ row }">
              <div style="display: flex; align-items: center; flex-wrap: nowrap; white-space: nowrap;">
                <el-button link type="primary" @click="showAccessDialog(row)">{{ t('functionUnit.access') }}</el-button>
                <el-button link type="primary" @click="showDeployDialog(row)">{{ t('functionUnit.deploy') }}</el-button>
                <el-button link type="primary" @click="showVersions(row)">{{ t('functionUnit.versions') }}</el-button>
                <el-button link type="danger" @click="handleRollback(row)">{{ t('functionUnit.rollback') }}</el-button>
                <el-button link type="danger" @click="handleDeleteClick(row)">{{ t('common.delete') }}</el-button>
              </div>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
      
      <el-tab-pane :label="t('functionUnit.deploymentRecords')" name="deployments">
        <el-table :data="deployments" stripe>
          <el-table-column prop="functionUnitName" :label="t('menu.functionUnit')" />
          <el-table-column prop="version" :label="t('functionUnit.version')" />
          <el-table-column prop="environment" :label="t('functionUnit.environment')" />
          <el-table-column prop="strategy" :label="t('functionUnit.strategy')" />
          <el-table-column prop="status" :label="t('common.status')">
            <template #default="{ row }">
              <el-tag :type="deployStatusType(row.status)">{{ row.status }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="deployedAt" :label="t('functionUnit.deployedAt')" />
          <el-table-column prop="deployedBy" :label="t('functionUnit.deployedBy')" />
          <el-table-column :label="t('common.actions')" width="120" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="handleViewLog(row)">{{ t('deployment.viewLog') }}</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>
    
    <el-dialog v-model="showImportDialog" :title="t('functionUnit.importPackage')" width="500px">
      <el-upload drag :auto-upload="false" accept=".zip" :limit="1" ref="importUploadRef" :on-change="handleImportFileChange">
        <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
        <div class="el-upload__text">{{ t('functionUnit.dragPackageHere') }}<em>{{ t('functionUnit.clickToUpload') }}</em></div>
        <template #tip>
          <div class="el-upload__tip">{{ t('functionUnit.zipFormatTip') }}</div>
        </template>
      </el-upload>
      <template #footer>
        <el-button @click="showImportDialog = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="importLoading" :disabled="!importFile" @click="handleStartImport">{{ t('functionUnit.startImport') }}</el-button>
      </template>
    </el-dialog>
    
    <el-dialog v-model="showDeployDialogVisible" :title="t('functionUnit.deployFunctionUnit')" width="500px">
      <el-form label-width="160px" label-position="left">
        <el-form-item :label="t('functionUnit.targetEnvironment')">
          <el-select v-model="deployForm.environment" style="width: 100%">
            <el-option :label="t('functionUnit.envDev')" value="DEV" />
            <el-option :label="t('functionUnit.envTest')" value="TEST" />
            <el-option :label="t('functionUnit.envStaging')" value="STAGING" />
            <el-option :label="t('functionUnit.envProd')" value="PROD" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('functionUnit.deployStrategy')">
          <el-select v-model="deployForm.strategy" style="width: 100%">
            <el-option :label="t('functionUnit.strategyFull')" value="FULL" />
            <el-option :label="t('functionUnit.strategyIncremental')" value="INCREMENTAL" />
            <el-option :label="t('functionUnit.strategyCanary')" value="CANARY" />
            <el-option :label="t('functionUnit.strategyBlueGreen')" value="BLUE_GREEN" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showDeployDialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" @click="handleDeploy">{{ t('functionUnit.confirmDeploy') }}</el-button>
      </template>
    </el-dialog>
    
    <!-- Access Config Dialog -->
    <AccessConfigDialog
      v-model="showAccessDialogVisible"
      :function-unit-id="currentUnit?.id"
      :function-unit-name="currentUnit?.name"
    />
    
    <!-- Version History Dialog -->
    <el-dialog v-model="showVersionsDialogVisible" :title="t('functionUnit.versions') + ' - ' + (currentUnit?.name || '')" width="800px">
      <el-table :data="versionList" stripe v-loading="versionsLoading">
        <el-table-column prop="name" :label="t('common.name')" />
        <el-table-column prop="version" :label="t('functionUnit.version')" width="120" />
        <el-table-column prop="status" :label="t('common.status')" width="120">
          <template #default="{ row }">
            <el-tag :type="functionUnitStatusType(row.status)">{{ t(functionUnitStatusKey(row.status)) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="t('common.enable')" width="80">
          <template #default="{ row }">
            <el-tag :type="row.enabled ? 'success' : 'info'" size="small">{{ row.enabled ? t('common.yes') : t('common.no') }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="updatedAt" :label="t('common.updateTime')" />
        <el-table-column :label="t('common.actions')" width="100">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleCompareVersion(row)">{{ t('version.compare') }}</el-button>
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="showVersionsDialogVisible = false">{{ t('common.close') }}</el-button>
      </template>
    </el-dialog>
    
    <!-- Delete Confirm Dialog -->
    <DeleteConfirmDialog
      v-model="showDeleteDialogVisible"
      :function-unit="deleteTargetUnit"
      :preview="deletePreview"
      @confirm="handleDeleteConfirm"
    />

    <!-- Deployment Log Dialog -->
    <el-dialog v-model="showLogDialogVisible" :title="t('deployment.viewLog')" width="600px">
      <div v-if="logDeployment">
        <el-descriptions :column="1" border>
          <el-descriptions-item :label="t('menu.functionUnit')">{{ logDeployment.functionUnitName }}</el-descriptions-item>
          <el-descriptions-item :label="t('functionUnit.environment')">{{ logDeployment.environment }}</el-descriptions-item>
          <el-descriptions-item :label="t('common.status')">
            <el-tag :type="deployStatusType(logDeployment.status)">{{ logDeployment.status }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item :label="t('functionUnit.deployedAt')">{{ logDeployment.deployedAt || logDeployment.createdAt }}</el-descriptions-item>
          <el-descriptions-item :label="t('functionUnit.deployedBy')">{{ logDeployment.deployedBy }}</el-descriptions-item>
        </el-descriptions>
      </div>
      <template #footer>
        <el-button @click="showLogDialogVisible = false">{{ t('common.close') }}</el-button>
      </template>
    </el-dialog>

    <!-- Version Compare Dialog -->
    <el-dialog v-model="showCompareDialogVisible" :title="t('version.compare')" width="700px">
      <div v-if="compareVersion">
        <el-descriptions :column="2" border>
          <el-descriptions-item :label="t('functionUnit.version')">{{ compareVersion.version }}</el-descriptions-item>
          <el-descriptions-item :label="t('common.status')">
            <el-tag :type="functionUnitStatusType(compareVersion.status)">{{ t(functionUnitStatusKey(compareVersion.status)) }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item :label="t('common.updateTime')">{{ compareVersion.updatedAt }}</el-descriptions-item>
          <el-descriptions-item :label="t('common.enable')">{{ compareVersion.enabled ? t('common.yes') : t('common.no') }}</el-descriptions-item>
        </el-descriptions>
      </div>
      <template #footer>
        <el-button @click="showCompareDialogVisible = false">{{ t('common.close') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { Plus } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import DeleteConfirmDialog from './components/DeleteConfirmDialog.vue'
import AccessConfigDialog from './components/AccessConfigDialog.vue'
import { functionUnitStatusType, functionUnitStatusKey, deployStatusType, formatDate } from '@/utils/format'
import { useFunctionUnit } from '@/composables/modules/useFunctionUnit'

const { t } = useI18n()

// All business logic is now in the composable — component is pure template binding
const {
  activeTab, loading, deploymentsLoading, versionsLoading, importLoading,
  functionUnits, deployments, versionList, searchKeyword, filteredFunctionUnits, selectedUnits,
  showImportDialog, showDeployDialogVisible, showAccessDialogVisible,
  showDeleteDialogVisible, showVersionsDialogVisible, showLogDialogVisible, showCompareDialogVisible,
  currentUnit, deleteTargetUnit, deletePreview, logDeployment, compareVersion,
  deployForm, importFile, importUploadRef,
  fetchFunctionUnits, showDeployDialog, showAccessDialog, showVersions,
  handleDeploy, handleRollback, handleEnabledChange,
  handleDeleteClick, handleDeleteConfirm,
  handleSelectionChange, handleBatchEnable, handleBatchDisable, handleBatchDelete,
  handleViewLog, handleCompareVersion,
  handleImportFileChange, handleStartImport,
} = useFunctionUnit()

onMounted(() => {
  fetchFunctionUnits()
})
</script>

<style scoped>
</style>
