<template>
  <div class="version-manager">
    <el-table
      v-loading="loading"
      :data="store.versions"
      row-key="id"
      stripe
    >
      <el-table-column
        prop="versionNumber"
        :label="t('version.versionNumber')"
        min-width="120"
      >
        <template #default="{ row }">
          <span>{{ row.versionNumber }}</span>
          <el-tag
            v-if="row.current"
            type="success"
            size="small"
            style="margin-left: 8px;"
          >
            {{ t('version.currentActive') }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column
        prop="createdBy"
        :label="t('version.publisher')"
        min-width="120"
      />
      <el-table-column
        prop="createdAt"
        :label="t('version.publishTime')"
        min-width="180"
      >
        <template #default="{ row }">
          {{ formatDate(row.createdAt) }}
        </template>
      </el-table-column>
      <el-table-column
        :label="t('common.actions')"
        width="240"
        align="center"
      >
        <template #default="{ row }">
          <div class="action-buttons">
            <el-button
              link
              type="primary"
              @click="handleCompare(row)"
            >
              {{ t('common.compare') }}
            </el-button>
            <el-button
              link
              type="warning"
              :disabled="row.current"
              @click="handleRollback(row)"
            >
              {{ t('common.rollback') }}
            </el-button>
            <el-button
              link
              type="success"
              @click="handleExport(row)"
            >
              {{ t('common.export') }}
            </el-button>
          </div>
        </template>
      </el-table-column>
    </el-table>

    <!-- Compare Dialog -->
    <el-dialog
      v-model="showCompareDialog"
      :title="t('version.compare')"
      width="900px"
    >
      <div class="compare-container">
        <div class="version-select">
          <el-select
            v-model="compareVersion1"
            :placeholder="t('version.selectVersion1')"
            @change="handleVersionChange"
          >
            <el-option
              v-for="v in store.versions"
              :key="v.id" 
              :label="v.versionNumber"
              :value="v.id"
            />
          </el-select>
          <span class="vs-label">{{ t('common.vs') }}</span>
          <el-select
            v-model="compareVersion2"
            :placeholder="t('version.selectVersion2')"
            @change="handleVersionChange"
          >
            <el-option
              v-for="v in store.versions"
              :key="v.id" 
              :label="v.versionNumber"
              :value="v.id"
            />
          </el-select>
          <el-button
            type="primary"
            :loading="comparing"
            :disabled="!compareVersion1 || !compareVersion2" 
            @click="doCompare"
          >
            {{ t('common.compare') }}
          </el-button>
        </div>
        
        <div
          v-if="compareResult"
          class="compare-result"
        >
          <el-tabs v-model="activeTab">
            <el-tab-pane
              :label="t('common.overview')"
              name="overview"
            >
              <div class="diff-summary">
                <div class="summary-item added">
                  <el-icon><Plus /></el-icon>
                  <span>{{ t('common.added') }}: {{ compareResult.added?.length || 0 }}</span>
                </div>
                <div class="summary-item modified">
                  <el-icon><Edit /></el-icon>
                  <span>{{ t('common.modified') }}: {{ compareResult.modified?.length || 0 }}</span>
                </div>
                <div class="summary-item removed">
                  <el-icon><Minus /></el-icon>
                  <span>{{ t('common.deleted') }}: {{ compareResult.removed?.length || 0 }}</span>
                </div>
              </div>
            </el-tab-pane>
            
            <el-tab-pane
              :label="t('version.tableDefinition')"
              name="tables"
            >
              <div
                v-if="compareResult.tables"
                class="diff-section"
              >
                <div
                  v-for="diff in compareResult.tables"
                  :key="diff.name"
                  class="diff-item"
                >
                  <div
                    class="diff-header"
                    :class="diff.type"
                  >
                    <el-tag
                      :type="diffTagType(diff.type)"
                      size="small"
                    >
                      {{ diffLabel(diff.type) }}
                    </el-tag>
                    <span>{{ diff.name }}</span>
                  </div>
                  <div
                    v-if="diff.changes?.length"
                    class="diff-changes"
                  >
                    <div
                      v-for="(change, idx) in diff.changes"
                      :key="idx"
                      class="change-item"
                    >
                      <span class="field-name">{{ change.field }}:</span>
                      <span class="old-value">{{ change.oldValue }}</span>
                      <el-icon><Right /></el-icon>
                      <span class="new-value">{{ change.newValue }}</span>
                    </div>
                  </div>
                </div>
                <el-empty
                  v-if="!compareResult.tables?.length"
                  :description="t('common.noData')"
                />
              </div>
            </el-tab-pane>
            
            <el-tab-pane
              :label="t('version.formDefinition')"
              name="forms"
            >
              <div
                v-if="compareResult.forms"
                class="diff-section"
              >
                <div
                  v-for="diff in compareResult.forms"
                  :key="diff.name"
                  class="diff-item"
                >
                  <div
                    class="diff-header"
                    :class="diff.type"
                  >
                    <el-tag
                      :type="diffTagType(diff.type)"
                      size="small"
                    >
                      {{ diffLabel(diff.type) }}
                    </el-tag>
                    <span>{{ diff.name }}</span>
                  </div>
                  <div
                    v-if="diff.changes?.length"
                    class="diff-changes"
                  >
                    <div
                      v-for="(change, idx) in diff.changes"
                      :key="idx"
                      class="change-item"
                    >
                      <span class="field-name">{{ change.field }}:</span>
                      <span class="old-value">{{ change.oldValue }}</span>
                      <el-icon><Right /></el-icon>
                      <span class="new-value">{{ change.newValue }}</span>
                    </div>
                  </div>
                </div>
                <el-empty
                  v-if="!compareResult.forms?.length"
                  :description="t('common.noData')"
                />
              </div>
            </el-tab-pane>
            
            <el-tab-pane
              :label="t('version.processDefinition')"
              name="process"
            >
              <div
                v-if="compareResult.process"
                class="diff-section"
              >
                <div class="process-diff">
                  <div
                    class="diff-header"
                    :class="compareResult.process.type"
                  >
                    <el-tag
                      :type="diffTagType(compareResult.process.type)"
                      size="small"
                    >
                      {{ diffLabel(compareResult.process.type) }}
                    </el-tag>
                    <span>{{ compareResult.process.name || t('version.processDefinition') }}</span>
                  </div>
                  <div
                    v-if="compareResult.process.changes?.length"
                    class="diff-changes"
                  >
                    <div
                      v-for="(change, idx) in compareResult.process.changes"
                      :key="idx"
                      class="change-item"
                    >
                      <span class="field-name">{{ change.field }}:</span>
                      <span class="old-value">{{ change.oldValue }}</span>
                      <el-icon><Right /></el-icon>
                      <span class="new-value">{{ change.newValue }}</span>
                    </div>
                  </div>
                </div>
              </div>
              <el-empty
                v-else
                :description="t('common.noData')"
              />
            </el-tab-pane>
            
            <el-tab-pane
              :label="t('common.rawData')"
              name="raw"
            >
              <pre class="raw-json">{{ JSON.stringify(compareResult, null, 2) }}</pre>
            </el-tab-pane>
          </el-tabs>
        </div>
        
        <el-empty
          v-else-if="!comparing"
          :description="t('version.selectVersion1')"
        />
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { Plus, Minus, Edit, Right } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useFunctionUnitStore } from '@/stores/functionUnit'
import { functionUnitApi } from '@/api/functionUnit'
import dayjs from 'dayjs'

const { t } = useI18n()

const props = defineProps<{ functionUnitId: number }>()

const store = useFunctionUnitStore()
const loading = ref(false)
const comparing = ref(false)
const showCompareDialog = ref(false)
const compareVersion1 = ref<number>()
const compareVersion2 = ref<number>()
const compareResult = ref<any>(null)
const activeTab = ref('overview')

const formatDate = (date: string) => dayjs(date).format('YYYY-MM-DD HH:mm:ss')

const diffTagType = (type: string) => {
  const map: Record<string, string> = { added: 'success', modified: 'warning', removed: 'danger' }
  return map[type] || 'info'
}

const diffLabel = (type: string) => {
  const map: Record<string, string> = { 
    added: t('common.added'), 
    modified: t('common.modified'), 
    removed: t('common.deleted') 
  }
  return map[type] || type
}

async function loadVersions() {
  loading.value = true
  try {
    await Promise.all([
      store.fetchVersions(props.functionUnitId),
      store.fetchById(props.functionUnitId)
    ])
  } finally {
    loading.value = false
  }
}

function resolveApiError(e: any): string {
  return e?.response?.data?.error?.message
    || e?.response?.data?.message
    || t('common.error')
}

function activeVersionNumber(): string {
  return store.current?.currentVersion
    || store.versions.find(v => v.current)?.versionNumber
    || store.versions?.[0]?.versionNumber
    || '?'
}

function handleCompare(row: any) {
  compareVersion1.value = row.id
  compareResult.value = null
  activeTab.value = 'overview'
  showCompareDialog.value = true
}

function handleVersionChange() {
  compareResult.value = null
}

async function doCompare() {
  if (!compareVersion1.value || !compareVersion2.value) {
    ElMessage.warning(t('version.selectVersion1'))
    return
  }
  if (compareVersion1.value === compareVersion2.value) {
    ElMessage.warning(t('version.selectVersion2'))
    return
  }
  comparing.value = true
  try {
    const res = await functionUnitApi.compareVersions?.(props.functionUnitId, compareVersion1.value, compareVersion2.value)
    compareResult.value = res?.data || {}
  } catch (e: any) {
    ElMessage.error(resolveApiError(e))
  } finally {
    comparing.value = false
  }
}

async function handleRollback(row: any) {
  if (row.current) {
    ElMessage.warning(t('version.cannotRollbackToCurrent', { version: row.versionNumber }))
    return
  }
  const targetVersion = row.versionNumber
  const currentVersion = activeVersionNumber()
  await ElMessageBox.confirm(
    t('version.rollbackConfirmDetail', { targetVersion, currentVersion }),
    t('version.rollbackTitle'),
    {
      type: 'warning',
      confirmButtonText: t('common.confirm'),
      cancelButtonText: t('common.cancel')
    }
  )
  try {
    await store.rollback(props.functionUnitId, row.id)
    ElMessage.success(t('common.success'))
    await loadVersions()
  } catch (e: any) {
    ElMessage.error(resolveApiError(e))
  }
}

function handleExport(row: any) {
  window.open(`/api/v1/function-units/${props.functionUnitId}/versions/${row.id}/export`)
}

onMounted(loadVersions)
</script>

<style lang="scss" scoped>
.version-manager {
  min-height: 300px;

  .action-buttons {
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 8px;
    white-space: nowrap;
  }
}

.compare-container {
  .version-select {
    display: flex;
    align-items: center;
    gap: 12px;
    margin-bottom: 20px;
    padding-bottom: 16px;
    border-bottom: 1px solid #e6e6e6;
    
    .vs-label {
      font-weight: bold;
      color: #909399;
    }
  }
  
  .compare-result {
    min-height: 300px;
  }
  
  .diff-summary {
    display: flex;
    gap: 24px;
    padding: 20px;
    background: #f5f7fa;
    border-radius: 8px;
    
    .summary-item {
      display: flex;
      align-items: center;
      gap: 8px;
      font-size: 16px;
      
      &.added { color: #67C23A; }
      &.modified { color: #E6A23C; }
      &.removed { color: #F56C6C; }
    }
  }
  
  .diff-section {
    max-height: 400px;
    overflow-y: auto;
  }
  
  .diff-item, .process-diff {
    margin-bottom: 12px;
    border: 1px solid #e6e6e6;
    border-radius: 4px;
    overflow: hidden;
    
    .diff-header {
      display: flex;
      align-items: center;
      gap: 10px;
      padding: 10px 12px;
      background: #f5f7fa;
      font-weight: 500;
      
      &.added { background: rgba(103, 194, 58, 0.1); }
      &.modified { background: rgba(230, 162, 60, 0.1); }
      &.removed { background: rgba(245, 108, 108, 0.1); }
    }
    
    .diff-changes {
      padding: 12px;
      
      .change-item {
        display: flex;
        align-items: center;
        gap: 8px;
        padding: 6px 0;
        font-size: 13px;
        
        &:not(:last-child) {
          border-bottom: 1px dashed #e6e6e6;
        }
        
        .field-name {
          font-weight: 500;
          color: #606266;
          min-width: 100px;
        }
        
        .old-value {
          color: #F56C6C;
          text-decoration: line-through;
          max-width: 200px;
          overflow: hidden;
          text-overflow: ellipsis;
          white-space: nowrap;
        }
        
        .new-value {
          color: #67C23A;
          max-width: 200px;
          overflow: hidden;
          text-overflow: ellipsis;
          white-space: nowrap;
        }
      }
    }
  }
  
  .raw-json {
    background: #f5f7fa;
    padding: 15px;
    border-radius: 4px;
    max-height: 400px;
    overflow: auto;
    margin: 0;
    font-size: 12px;
    font-family: 'Monaco', 'Menlo', monospace;
  }
}
</style>
