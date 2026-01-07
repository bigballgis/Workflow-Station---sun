<template>
  <div class="version-manager">
    <el-table :data="store.versions" v-loading="loading" stripe>
      <el-table-column prop="versionNumber" label="版本号" width="120" />
      <el-table-column prop="changeLog" label="变更日志" show-overflow-tooltip />
      <el-table-column prop="createdBy" label="发布人" width="120" />
      <el-table-column prop="createdAt" label="发布时间" width="180">
        <template #default="{ row }">
          {{ formatDate(row.createdAt) }}
        </template>
      </el-table-column>
      <el-table-column label="操作" width="200">
        <template #default="{ row }">
          <el-button link type="primary" @click="handleCompare(row)">比较</el-button>
          <el-button link type="warning" @click="handleRollback(row)">回滚</el-button>
          <el-button link type="success" @click="handleExport(row)">导出</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- Compare Dialog -->
    <el-dialog v-model="showCompareDialog" title="版本比较" width="900px">
      <div class="compare-container">
        <div class="version-select">
          <el-select v-model="compareVersion1" placeholder="选择版本1" @change="handleVersionChange">
            <el-option v-for="v in store.versions" :key="v.id" 
                       :label="v.versionNumber" :value="v.id" />
          </el-select>
          <span class="vs-label">vs</span>
          <el-select v-model="compareVersion2" placeholder="选择版本2" @change="handleVersionChange">
            <el-option v-for="v in store.versions" :key="v.id" 
                       :label="v.versionNumber" :value="v.id" />
          </el-select>
          <el-button type="primary" @click="doCompare" :loading="comparing" 
                     :disabled="!compareVersion1 || !compareVersion2">比较</el-button>
        </div>
        
        <div v-if="compareResult" class="compare-result">
          <el-tabs v-model="activeTab">
            <el-tab-pane label="概览" name="overview">
              <div class="diff-summary">
                <div class="summary-item added">
                  <el-icon><Plus /></el-icon>
                  <span>新增: {{ compareResult.added?.length || 0 }} 项</span>
                </div>
                <div class="summary-item modified">
                  <el-icon><Edit /></el-icon>
                  <span>修改: {{ compareResult.modified?.length || 0 }} 项</span>
                </div>
                <div class="summary-item removed">
                  <el-icon><Minus /></el-icon>
                  <span>删除: {{ compareResult.removed?.length || 0 }} 项</span>
                </div>
              </div>
            </el-tab-pane>
            
            <el-tab-pane label="表定义" name="tables">
              <div class="diff-section" v-if="compareResult.tables">
                <div v-for="diff in compareResult.tables" :key="diff.name" class="diff-item">
                  <div class="diff-header" :class="diff.type">
                    <el-tag :type="diffTagType(diff.type)" size="small">{{ diffLabel(diff.type) }}</el-tag>
                    <span>{{ diff.name }}</span>
                  </div>
                  <div v-if="diff.changes?.length" class="diff-changes">
                    <div v-for="(change, idx) in diff.changes" :key="idx" class="change-item">
                      <span class="field-name">{{ change.field }}:</span>
                      <span class="old-value">{{ change.oldValue }}</span>
                      <el-icon><Right /></el-icon>
                      <span class="new-value">{{ change.newValue }}</span>
                    </div>
                  </div>
                </div>
                <el-empty v-if="!compareResult.tables?.length" description="无表定义变更" />
              </div>
            </el-tab-pane>
            
            <el-tab-pane label="表单定义" name="forms">
              <div class="diff-section" v-if="compareResult.forms">
                <div v-for="diff in compareResult.forms" :key="diff.name" class="diff-item">
                  <div class="diff-header" :class="diff.type">
                    <el-tag :type="diffTagType(diff.type)" size="small">{{ diffLabel(diff.type) }}</el-tag>
                    <span>{{ diff.name }}</span>
                  </div>
                  <div v-if="diff.changes?.length" class="diff-changes">
                    <div v-for="(change, idx) in diff.changes" :key="idx" class="change-item">
                      <span class="field-name">{{ change.field }}:</span>
                      <span class="old-value">{{ change.oldValue }}</span>
                      <el-icon><Right /></el-icon>
                      <span class="new-value">{{ change.newValue }}</span>
                    </div>
                  </div>
                </div>
                <el-empty v-if="!compareResult.forms?.length" description="无表单定义变更" />
              </div>
            </el-tab-pane>
            
            <el-tab-pane label="流程定义" name="process">
              <div class="diff-section" v-if="compareResult.process">
                <div class="process-diff">
                  <div class="diff-header" :class="compareResult.process.type">
                    <el-tag :type="diffTagType(compareResult.process.type)" size="small">
                      {{ diffLabel(compareResult.process.type) }}
                    </el-tag>
                    <span>{{ compareResult.process.name || '流程定义' }}</span>
                  </div>
                  <div v-if="compareResult.process.changes?.length" class="diff-changes">
                    <div v-for="(change, idx) in compareResult.process.changes" :key="idx" class="change-item">
                      <span class="field-name">{{ change.field }}:</span>
                      <span class="old-value">{{ change.oldValue }}</span>
                      <el-icon><Right /></el-icon>
                      <span class="new-value">{{ change.newValue }}</span>
                    </div>
                  </div>
                </div>
              </div>
              <el-empty v-else description="无流程定义变更" />
            </el-tab-pane>
            
            <el-tab-pane label="原始数据" name="raw">
              <pre class="raw-json">{{ JSON.stringify(compareResult, null, 2) }}</pre>
            </el-tab-pane>
          </el-tabs>
        </div>
        
        <el-empty v-else-if="!comparing" description="选择两个版本进行比较" />
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { Plus, Minus, Edit, Right } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useFunctionUnitStore } from '@/stores/functionUnit'
import { functionUnitApi } from '@/api/functionUnit'
import dayjs from 'dayjs'

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
  const map: Record<string, string> = { added: '新增', modified: '修改', removed: '删除' }
  return map[type] || type
}

async function loadVersions() {
  loading.value = true
  try {
    await store.fetchVersions(props.functionUnitId)
  } finally {
    loading.value = false
  }
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
    ElMessage.warning('请选择两个版本')
    return
  }
  if (compareVersion1.value === compareVersion2.value) {
    ElMessage.warning('请选择不同的版本')
    return
  }
  comparing.value = true
  try {
    const res = await functionUnitApi.compareVersions?.(props.functionUnitId, compareVersion1.value, compareVersion2.value)
    compareResult.value = res?.data || {}
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || '比较失败')
  } finally {
    comparing.value = false
  }
}

async function handleRollback(row: any) {
  await ElMessageBox.confirm(`确定要回滚到版本 ${row.versionNumber} 吗？`, '提示', { type: 'warning' })
  try {
    await store.rollback(props.functionUnitId, row.id)
    ElMessage.success('回滚成功')
    loadVersions()
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || '回滚失败')
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
