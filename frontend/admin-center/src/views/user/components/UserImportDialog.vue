<template>
  <el-dialog 
    :model-value="modelValue" 
    @update:model-value="$emit('update:modelValue', $event)" 
    title="批量导入用户" 
    width="600px"
    destroy-on-close
  >
    <div class="import-container">
      <div class="import-tips">
        <el-alert type="info" :closable="false" show-icon>
          <template #title>
            <span>请先下载模板，按照模板格式填写用户信息后上传</span>
          </template>
        </el-alert>
      </div>

      <div class="upload-area">
        <el-upload
          ref="uploadRef"
          :auto-upload="false"
          :limit="1"
          :on-change="handleFileChange"
          :on-exceed="handleExceed"
          accept=".xlsx,.xls"
          drag
        >
          <el-icon class="el-icon--upload"><Upload /></el-icon>
          <div class="el-upload__text">将文件拖到此处，或<em>点击上传</em></div>
          <template #tip>
            <div class="el-upload__tip">只能上传 xlsx/xls 文件，且不超过 5MB</div>
          </template>
        </el-upload>
      </div>

      <div v-if="importResult" class="import-result">
        <el-result 
          :icon="importResult.failed === 0 ? 'success' : 'warning'" 
          :title="importResult.failed === 0 ? '导入成功' : '部分导入成功'"
        >
          <template #sub-title>
            <div class="result-summary">
              <span>总计: {{ importResult.total }} 条</span>
              <span class="success">成功: {{ importResult.success }} 条</span>
              <span class="failed" v-if="importResult.failed > 0">失败: {{ importResult.failed }} 条</span>
            </div>
          </template>
        </el-result>

        <el-table v-if="importResult.errors?.length" :data="importResult.errors" border size="small" max-height="200">
          <el-table-column prop="row" label="行号" width="70" />
          <el-table-column prop="field" label="字段" width="100" />
          <el-table-column prop="value" label="值" width="120" />
          <el-table-column prop="message" label="错误信息" />
        </el-table>
      </div>
    </div>

    <template #footer>
      <el-button @click="handleDownloadTemplate">
        <el-icon><Download /></el-icon>下载模板
      </el-button>
      <el-button @click="$emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="loading" :disabled="!selectedFile" @click="handleImport">
        开始导入
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage, type UploadInstance, type UploadFile } from 'element-plus'
import { Upload, Download } from '@element-plus/icons-vue'
import { userApi, type ImportResult } from '@/api/user'

defineProps<{ modelValue: boolean }>()
const emit = defineEmits(['update:modelValue', 'success'])

const uploadRef = ref<UploadInstance>()
const loading = ref(false)
const selectedFile = ref<File | null>(null)
const importResult = ref<ImportResult | null>(null)

const handleFileChange = (file: UploadFile) => {
  if (file.raw) {
    if (file.raw.size > 5 * 1024 * 1024) {
      ElMessage.error('文件大小不能超过 5MB')
      uploadRef.value?.clearFiles()
      return
    }
    selectedFile.value = file.raw
    importResult.value = null
  }
}

const handleExceed = () => {
  ElMessage.warning('只能上传一个文件，请先删除已选文件')
}

const handleDownloadTemplate = async () => {
  try {
    const blob = await userApi.exportTemplate()
    const url = window.URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = '用户导入模板.xlsx'
    link.click()
    window.URL.revokeObjectURL(url)
  } catch (error: any) {
    ElMessage.error(error.message || '下载模板失败')
  }
}

const handleImport = async () => {
  if (!selectedFile.value) return
  
  loading.value = true
  try {
    importResult.value = await userApi.batchImport(selectedFile.value)
    if (importResult.value.success > 0) {
      emit('success')
    }
    if (importResult.value.failed === 0) {
      ElMessage.success('导入成功')
    }
  } catch (error: any) {
    ElMessage.error(error.message || '导入失败')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped lang="scss">
.import-container {
  .import-tips { margin-bottom: 20px; }
  .upload-area { margin-bottom: 20px; }
  .import-result {
    .result-summary {
      display: flex;
      gap: 20px;
      justify-content: center;
      .success { color: #67c23a; }
      .failed { color: #f56c6c; }
    }
  }
}
</style>
