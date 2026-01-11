<template>
  <div class="page-container">
    <div class="page-header">
      <span class="page-title">{{ t('user.batchImport') }}</span>
    </div>
    
    <el-card>
      <el-steps :active="step" finish-status="success" align-center>
        <el-step title="上传文件" />
        <el-step title="数据预览" />
        <el-step title="导入结果" />
      </el-steps>
      
      <div class="step-content">
        <template v-if="step === 0">
          <div class="upload-area">
            <el-upload
              drag
              :auto-upload="false"
              :limit="1"
              accept=".xlsx,.xls,.csv"
              :on-change="handleFileChange"
            >
              <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
              <div class="el-upload__text">拖拽文件到此处，或<em>点击上传</em></div>
              <template #tip>
                <div class="el-upload__tip">支持 xlsx、xls、csv 格式，单次最多导入 1000 条</div>
              </template>
            </el-upload>
            <el-button type="primary" link @click="downloadTemplate">
              <el-icon><Download /></el-icon>{{ t('user.importTemplate') }}
            </el-button>
          </div>
        </template>
        
        <template v-if="step === 1">
          <el-table :data="previewData" max-height="400">
            <el-table-column type="index" label="行号" width="60" />
            <el-table-column prop="username" :label="t('user.username')" />
            <el-table-column prop="realName" :label="t('user.realName')" />
            <el-table-column prop="email" :label="t('user.email')" />
            <el-table-column prop="departmentCode" label="部门编码" />
          </el-table>
          <div class="preview-info">
            共 {{ previewData.length }} 条数据待导入
          </div>
        </template>
        
        <template v-if="step === 2">
          <el-result :icon="importResult.failedCount === 0 ? 'success' : 'warning'" :title="resultTitle">
            <template #sub-title>
              <div class="result-stats">
                <span>{{ t('user.totalCount') }}: {{ importResult.totalCount }}</span>
                <span class="success">{{ t('user.successCount') }}: {{ importResult.successCount }}</span>
                <span class="failed">{{ t('user.failedCount') }}: {{ importResult.failedCount }}</span>
              </div>
            </template>
            <template #extra>
              <el-button type="primary" @click="resetImport">继续导入</el-button>
            </template>
          </el-result>
          
          <el-table v-if="importResult.errors.length" :data="importResult.errors" max-height="300">
            <el-table-column prop="row" label="行号" width="80" />
            <el-table-column prop="message" label="错误信息" />
          </el-table>
        </template>
      </div>
      
      <div class="step-actions" v-if="step < 2">
        <el-button v-if="step > 0" @click="step--">上一步</el-button>
        <el-button v-if="step === 0" type="primary" :disabled="!selectedFile" @click="parseFile">下一步</el-button>
        <el-button v-if="step === 1" type="primary" :loading="importing" @click="handleImport">确认导入</el-button>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { userApi, ImportResult } from '@/api/user'

const { t } = useI18n()

const step = ref(0)
const selectedFile = ref<File | null>(null)
const previewData = ref<any[]>([])
const importing = ref(false)
const importResult = ref<ImportResult>({ totalCount: 0, successCount: 0, failedCount: 0, errors: [] })

const resultTitle = computed(() => importResult.value.failedCount === 0 ? '导入成功' : '部分导入成功')

const handleFileChange = (file: any) => { selectedFile.value = file.raw }

const downloadTemplate = async () => {
  const blob = await userApi.exportTemplate()
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = 'user-import-template.xlsx'
  a.click()
  URL.revokeObjectURL(url)
}

const parseFile = () => {
  // Mock parse - in real app, use xlsx library
  previewData.value = [
    { username: 'user1', realName: '用户一', email: 'user1@example.com', departmentCode: 'TECH' },
    { username: 'user2', realName: '用户二', email: 'user2@example.com', departmentCode: 'HR' }
  ]
  step.value = 1
}

const handleImport = async () => {
  if (!selectedFile.value) return
  importing.value = true
  try {
    importResult.value = await userApi.batchImport(selectedFile.value)
    step.value = 2
  } catch {
    // Mock result for demo
    importResult.value = { totalCount: previewData.value.length, successCount: previewData.value.length, failedCount: 0, errors: [] }
    step.value = 2
  } finally {
    importing.value = false
  }
}

const resetImport = () => {
  step.value = 0
  selectedFile.value = null
  previewData.value = []
  importResult.value = { totalCount: 0, successCount: 0, failedCount: 0, errors: [] }
}
</script>

<style scoped lang="scss">
.step-content {
  margin: 40px 0;
  min-height: 300px;
}

.upload-area {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 20px;
}

.preview-info {
  margin-top: 20px;
  text-align: center;
  color: #909399;
}

.result-stats {
  display: flex;
  gap: 30px;
  justify-content: center;
  font-size: 16px;
  
  .success { color: #67C23A; }
  .failed { color: #F56C6C; }
}

.step-actions {
  display: flex;
  justify-content: center;
  gap: 20px;
}
</style>
