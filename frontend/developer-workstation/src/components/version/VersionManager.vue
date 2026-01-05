<template>
  <div class="version-manager">
    <el-table :data="versions" v-loading="loading" stripe>
      <el-table-column prop="versionNumber" label="版本号" width="120" />
      <el-table-column prop="changeLog" label="变更日志" show-overflow-tooltip />
      <el-table-column prop="publishedBy" label="发布人" width="120" />
      <el-table-column prop="publishedAt" label="发布时间" width="180">
        <template #default="{ row }">
          {{ formatDate(row.publishedAt) }}
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
    <el-dialog v-model="showCompareDialog" title="版本比较" width="800px">
      <div class="compare-container">
        <div class="version-select">
          <el-select v-model="compareVersion1" placeholder="选择版本1">
            <el-option v-for="v in versions" :key="v.id" 
                       :label="v.versionNumber" :value="v.id" />
          </el-select>
          <span>vs</span>
          <el-select v-model="compareVersion2" placeholder="选择版本2">
            <el-option v-for="v in versions" :key="v.id" 
                       :label="v.versionNumber" :value="v.id" />
          </el-select>
          <el-button type="primary" @click="doCompare">比较</el-button>
        </div>
        <div v-if="compareResult" class="compare-result">
          <pre>{{ JSON.stringify(compareResult, null, 2) }}</pre>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '@/api'
import dayjs from 'dayjs'

const props = defineProps<{ functionUnitId: number }>()

const versions = ref<any[]>([])
const loading = ref(false)
const showCompareDialog = ref(false)
const compareVersion1 = ref<number>()
const compareVersion2 = ref<number>()
const compareResult = ref<any>(null)

const formatDate = (date: string) => dayjs(date).format('YYYY-MM-DD HH:mm:ss')

async function loadVersions() {
  loading.value = true
  try {
    const res = await api.get(`/function-units/${props.functionUnitId}/versions`)
    versions.value = res.data || []
  } finally {
    loading.value = false
  }
}

function handleCompare(row: any) {
  compareVersion1.value = row.id
  showCompareDialog.value = true
}

async function doCompare() {
  if (!compareVersion1.value || !compareVersion2.value) {
    ElMessage.warning('请选择两个版本')
    return
  }
  const res = await api.get(`/function-units/${props.functionUnitId}/versions/compare`, {
    params: { versionId1: compareVersion1.value, versionId2: compareVersion2.value }
  })
  compareResult.value = res.data
}

async function handleRollback(row: any) {
  await ElMessageBox.confirm(`确定要回滚到版本 ${row.versionNumber} 吗？`, '提示', { type: 'warning' })
  await api.post(`/function-units/${props.functionUnitId}/versions/${row.id}/rollback`)
  ElMessage.success('回滚成功')
}

async function handleExport(row: any) {
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
    gap: 10px;
    margin-bottom: 20px;
  }
  
  .compare-result {
    background-color: #f5f7fa;
    padding: 15px;
    border-radius: 4px;
    max-height: 400px;
    overflow: auto;
    
    pre {
      margin: 0;
      font-size: 12px;
    }
  }
}
</style>
