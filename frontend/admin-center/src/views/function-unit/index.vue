<template>
  <div class="page-container">
    <PageHeader :title="t('menu.functionUnit')">
      <template #actions>
        <el-button
          type="primary"
          @click="showImportDialog = true"
        >
          <el-icon><Upload /></el-icon>{{ t('common.import') }}
        </el-button>
      </template>
    </PageHeader>
    
    <el-tabs v-model="activeTab">
      <el-tab-pane
        :label="t('functionUnit.list')"
        name="list"
      >
        <div style="margin-bottom: 16px; display: flex; align-items: center; gap: 12px;">
          <el-input
            v-model="searchKeyword"
            :placeholder="t('functionUnit.searchPlaceholder')"
            clearable
            style="width: 300px;"
          />
          <template v-if="selectedUnits.length > 0">
            <span style="color: #909399; font-size: 13px;">{{ t('functionUnit.selected', { count: selectedUnits.length }) }}</span>
            <el-button
              type="success"
              size="small"
              @click="handleBatchEnable"
            >
              {{ t('functionUnit.batchEnable') }}
            </el-button>
            <el-button
              type="warning"
              size="small"
              @click="handleBatchDisable"
            >
              {{ t('functionUnit.batchDisable') }}
            </el-button>
            <el-button
              type="danger"
              size="small"
              @click="handleBatchDelete"
            >
              {{ t('functionUnit.batchDelete') }}
            </el-button>
          </template>
        </div>
        <el-table
          v-loading="loading"
          :data="filteredFunctionUnits"
          stripe
          @selection-change="handleSelectionChange"
        >
          <el-table-column
            type="selection"
            width="50"
          />
          <el-table-column
            prop="name"
            :label="t('common.name')"
          />
          <el-table-column
            prop="code"
            :label="t('common.code')"
          />
          <el-table-column
            prop="version"
            :label="t('functionUnit.version')"
          />
          <el-table-column
            prop="status"
            :label="t('common.status')"
          >
            <template #default="{ row }">
              <el-tag :type="functionUnitStatusType(row.status)">
                {{ t(functionUnitStatusKey(row.status)) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column
            :label="t('common.enable')"
            width="80"
          >
            <template #default="{ row }">
              <el-switch
                v-model="row.enabled"
                :loading="row._enabledLoading"
                @change="() => handleEnabledChange(row, row.enabled)"
              />
            </template>
          </el-table-column>
          <el-table-column
            prop="updatedAt"
            :label="t('common.updateTime')"
          />
          <el-table-column
            :label="t('common.actions')"
            width="420"
            fixed="right"
          >
            <template #default="{ row }">
              <div style="display: flex; align-items: center; flex-wrap: nowrap; white-space: nowrap;">
                <el-button
                  link
                  type="primary"
                  @click="showAccessDialog(row)"
                >
                  {{ t('functionUnit.access') }}
                </el-button>
                <el-button
                  v-if="canValidateFunctionUnit(row.status)"
                  link
                  type="primary"
                  :loading="validateLoadingId === row.id"
                  @click="handleValidate(row)"
                >
                  {{ t('functionUnit.validate') }}
                </el-button>
                <el-tooltip
                  v-if="!canDeployFunctionUnit(row.status)"
                  :content="t('functionUnit.deployRequiresValidation')"
                >
                  <span>
                    <el-button
                      link
                      type="primary"
                      disabled
                    >
                      {{ t('functionUnit.deploy') }}
                    </el-button>
                  </span>
                </el-tooltip>
                <el-button
                  v-else
                  link
                  type="primary"
                  :loading="deployLoadingId === row.id"
                  @click="handleDeploy(row)"
                >
                  {{ t('functionUnit.deploy') }}
                </el-button>
                <el-button
                  link
                  type="primary"
                  @click="showVersions(row)"
                >
                  {{ t('functionUnit.versions') }}
                </el-button>
                <el-button
                  link
                  type="danger"
                  @click="handleRollback(row)"
                >
                  {{ t('functionUnit.rollback') }}
                </el-button>
                <el-button
                  link
                  type="danger"
                  @click="handleDeleteClick(row)"
                >
                  {{ t('common.delete') }}
                </el-button>
              </div>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane
        :label="t('functionUnit.archiveList')"
        name="archive"
      >
        <div style="margin-bottom: 16px;">
          <el-input
            v-model="archiveSearchKeyword"
            :placeholder="t('functionUnit.searchPlaceholder')"
            clearable
            style="width: 300px;"
          />
        </div>
        <el-table
          v-loading="archivedLoading"
          :data="filteredArchivedFunctionUnits"
          stripe
        >
          <el-table-column
            prop="name"
            :label="t('common.name')"
          />
          <el-table-column
            prop="code"
            :label="t('common.code')"
          />
          <el-table-column
            prop="version"
            :label="t('functionUnit.version')"
          />
          <el-table-column
            prop="status"
            :label="t('common.status')"
          >
            <template #default="{ row }">
              <el-tag :type="functionUnitStatusType(row.status)">
                {{ t(functionUnitStatusKey(row.status)) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column
            prop="updatedAt"
            :label="t('common.updateTime')"
          />
          <el-table-column
            prop="updatedBy"
            :label="t('common.updatedBy')"
          />
          <el-table-column
            :label="t('common.actions')"
            width="120"
            fixed="right"
          >
            <template #default="{ row }">
              <el-button
                link
                type="primary"
                :loading="restoreLoadingId === row.id"
                @click="handleRestore(row)"
              >
                {{ t('functionUnit.restore') }}
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
      
      <el-tab-pane
        :label="t('functionUnit.deploymentRecords')"
        name="deployments"
      >
        <el-table
          v-loading="deploymentsLoading"
          :data="deployments"
          stripe
        >
          <el-table-column
            prop="functionUnitName"
            :label="t('menu.functionUnit')"
          />
          <el-table-column
            prop="version"
            :label="t('functionUnit.version')"
          />
          <el-table-column
            prop="status"
            :label="t('common.status')"
          >
            <template #default="{ row }">
              <el-tag :type="deployStatusType(row.status)">
                {{ row.status }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column
            prop="deployedAt"
            :label="t('functionUnit.deployedAt')"
          />
          <el-table-column
            prop="deployedBy"
            :label="t('functionUnit.deployedBy')"
          />
        </el-table>
      </el-tab-pane>
    </el-tabs>
    
    <!-- Import Dialog (extracted) -->
    <FunctionUnitImportDialog
      v-model="showImportDialog"
      :import-loading="importLoading"
      :import-file="importFile"
      @file-change="handleImportFileChange"
      @start-import="handleStartImport"
    />
    
    <!-- Access Config Dialog -->
    <AccessConfigDialog
      v-model="showAccessDialogVisible"
      :function-unit-id="currentUnit?.id"
      :function-unit-name="currentUnit?.name"
    />
    
    <!-- Version History Dialog -->
    <el-dialog
      v-model="showVersionsDialogVisible"
      :title="t('functionUnit.versions') + ' - ' + (currentUnit?.name || '')"
      width="800px"
    >
      <el-table
        v-loading="versionsLoading"
        :data="versionList"
        stripe
      >
        <el-table-column
          prop="name"
          :label="t('common.name')"
        />
        <el-table-column
          prop="version"
          :label="t('functionUnit.version')"
          width="120"
        />
        <el-table-column
          prop="status"
          :label="t('common.status')"
          width="120"
        >
          <template #default="{ row }">
            <el-tag :type="functionUnitStatusType(row.status)">
              {{ t(functionUnitStatusKey(row.status)) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column
          :label="t('common.enable')"
          width="80"
        >
          <template #default="{ row }">
            <el-tag
              :type="row.enabled ? 'success' : 'info'"
              size="small"
            >
              {{ row.enabled ? t('common.yes') : t('common.no') }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column
          prop="updatedAt"
          :label="t('common.updateTime')"
        />
        <el-table-column
          :label="t('common.actions')"
          width="100"
        >
          <template #default="{ row }">
            <el-button
              link
              type="primary"
              @click="handleCompareVersion(row)"
            >
              {{ t('version.compare') }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="showVersionsDialogVisible = false">
          {{ t('common.close') }}
        </el-button>
      </template>
    </el-dialog>
    
    <!-- Delete Confirm Dialog -->
    <DeleteConfirmDialog
      v-model="showDeleteDialogVisible"
      :function-unit="deleteTargetUnit"
      :preview="deletePreview"
      @confirm="handleDeleteConfirm"
    />

    <!-- Version Compare Dialog -->
    <el-dialog
      v-model="showCompareDialogVisible"
      :title="t('version.compare')"
      width="700px"
    >
      <div v-if="compareVersion">
        <el-descriptions
          :column="2"
          border
        >
          <el-descriptions-item :label="t('functionUnit.version')">
            {{ compareVersion.version }}
          </el-descriptions-item>
          <el-descriptions-item :label="t('common.status')">
            <el-tag :type="functionUnitStatusType(compareVersion.status)">
              {{ t(functionUnitStatusKey(compareVersion.status)) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item :label="t('common.updateTime')">
            {{ compareVersion.updatedAt }}
          </el-descriptions-item>
          <el-descriptions-item :label="t('common.enable')">
            {{ compareVersion.enabled ? t('common.yes') : t('common.no') }}
          </el-descriptions-item>
        </el-descriptions>
      </div>
      <template #footer>
        <el-button @click="showCompareDialogVisible = false">
          {{ t('common.close') }}
        </el-button>
      </template>
    </el-dialog>

    <ValidateResultDialog
      v-model="showValidateResultDialog"
      :result="validateResult"
    />
  </div>
</template>

<script setup lang="ts">
import { onMounted, onActivated } from 'vue'
import { useI18n } from 'vue-i18n'
import { Plus } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import DeleteConfirmDialog from './components/DeleteConfirmDialog.vue'
import AccessConfigDialog from './components/AccessConfigDialog.vue'
import FunctionUnitImportDialog from './components/FunctionUnitImportDialog.vue'
import ValidateResultDialog from './components/ValidateResultDialog.vue'
import { functionUnitStatusType, functionUnitStatusKey, deployStatusType, formatDate, canValidateFunctionUnit, canDeployFunctionUnit } from '@/utils/format'
import { useFunctionUnit } from '@/composables/modules/useFunctionUnit'

const { t } = useI18n()

// All business logic is now in the composable — component is pure template binding
const {
  activeTab, loading, archivedLoading, deploymentsLoading, versionsLoading, importLoading, deployLoadingId, validateLoadingId, restoreLoadingId,
  functionUnits, archivedFunctionUnits, deployments, versionList, searchKeyword, archiveSearchKeyword,
  filteredFunctionUnits, filteredArchivedFunctionUnits, selectedUnits,
  showImportDialog, showAccessDialogVisible,
  showDeleteDialogVisible, showVersionsDialogVisible, showCompareDialogVisible,
  showValidateResultDialog, validateResult,
  currentUnit, deleteTargetUnit, deletePreview, compareVersion,
  importFile, importUploadRef,
  fetchFunctionUnits, showAccessDialog, showVersions,
  handleValidate, handleDeploy, handleRestore, handleRollback, handleEnabledChange,
  handleDeleteClick, handleDeleteConfirm,
  handleSelectionChange, handleBatchEnable, handleBatchDisable,   handleBatchDelete,
  handleCompareVersion,
  handleImportFileChange, handleStartImport,
} = useFunctionUnit()

onMounted(() => {
  fetchFunctionUnits()
})

// Re-fetch when navigating back (keep-alive reactivation)
onActivated(() => {
  fetchFunctionUnits()
})
</script>

<style scoped>
</style>
