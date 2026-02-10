<template>
  <el-dialog 
    :model-value="modelValue" 
    @update:model-value="$emit('update:modelValue', $event)" 
    :title="t('user.batchImportUsers')" 
    width="600px"
    destroy-on-close
  >
    <div class="import-container">
      <div class="import-tips">
        <el-alert type="info" :closable="false" show-icon>
          <template #title>
            <span>{{ t('user.importTip') }}</span>
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
          <div class="el-upload__text" v-html="t('user.dragFileOrClick')"></div>
          <template #tip>
            <div class="el-upload__tip">{{ t('user.uploadFileLimitTip') }}</div>
          </template>
        </el-upload>
      </div>

      <div v-if="importResult" class="import-result">
        <el-result 
          :icon="importResult.failed === 0 ? 'success' : 'warning'" 
          :title="importResult.failed === 0 ? t('user.importSuccessResult') : t('user.importPartialResult')"
        >
          <template #sub-title>
            <div class="result-summary">
              <span>{{ t('user.totalRecords', { count: importResult.total }) }}</span>
              <span class="success">{{ t('user.successRecords', { count: importResult.success }) }}</span>
              <span class="failed" v-if="importResult.failed > 0">{{ t('user.failedRecords', { count: importResult.failed }) }}</span>
            </div>
          </template>
        </el-result>

        <el-table v-if="importResult.errors?.length" :data="importResult.errors" border size="small" max-height="200">
          <el-table-column prop="row" :label="t('common.rowNumber')" width="70" />
          <el-table-column prop="field" :label="t('common.field')" width="100" />
          <el-table-column prop="value" :label="t('common.value')" width="120" />
          <el-table-column prop="message" :label="t('common.errorMessage')" />
        </el-table>
      </div>
    </div>

    <template #footer>
      <el-button @click="handleDownloadTemplate">
        <el-icon><Download /></el-icon>{{ t('user.downloadTemplate') }}
      </el-button>
      <el-button @click="$emit('update:modelValue', false)">{{ t('common.cancel') }}</el-button>
      <el-button type="primary" :loading="loading" :disabled="!selectedFile" @click="handleImport">
        {{ t('user.startImport') }}
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, type UploadInstance, type UploadFile, type UploadRawFile } from 'element-plus'
import { Upload, Download } from '@element-plus/icons-vue'
import { userApi, type ImportResult } from '@/api/user'

const { t } = useI18n()

defineProps<{ modelValue: boolean }>()
const emit = defineEmits(['update:modelValue', 'success'])

const uploadRef = ref<UploadInstance>()
const loading = ref(false)
const selectedFile = ref<File | null>(null)
const importResult = ref<ImportResult | null>(null)

const handleFileChange = (file: UploadFile) => {
  if (file.raw) {
    if (file.raw.size > 5 * 1024 * 1024) {
      ElMessage.error(t('user.fileSizeExceeded'))
      uploadRef.value?.clearFiles()
      return
    }
    selectedFile.value = file.raw
    importResult.value = null
  }
}

const handleExceed = () => {
  ElMessage.warning(t('user.onlyOneFile'))
}

const handleDownloadTemplate = async () => {
  try {
    const blob = await userApi.exportTemplate()
    const url = window.URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = t('user.templateFileName')
    link.click()
    window.URL.revokeObjectURL(url)
  } catch (error: any) {
    ElMessage.error(error.message || t('user.downloadTemplateFailed'))
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
      ElMessage.success(t('user.importSuccessResult'))
    }
  } catch (error: any) {
    ElMessage.error(error.message || t('user.importFailed'))
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
