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
          <div class="el-upload__text" v-html="DOMPurify.sanitize(t('user.dragFileOrClick'))"></div>
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
import type { UploadInstance, UploadFile, UploadRawFile } from 'element-plus'
import { Upload, Download } from '@element-plus/icons-vue'
import DOMPurify from 'dompurify'
import { useUserImport } from '@/composables/modules/useUserImport'

const { t } = useI18n()
defineProps<{ modelValue: boolean }>()
const emit = defineEmits(['update:modelValue', 'success'])
const uploadRef = ref<UploadInstance>()

const { loading, selectedFile, importResult, validateFile, downloadTemplate, doImport }
  = useUserImport(() => emit('success'))

const handleFileChange = (file: UploadFile) => { if (file.raw) validateFile(file.raw) || uploadRef.value?.clearFiles() }
const handleDownloadTemplate = () => downloadTemplate()
const handleImport = () => doImport()
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
