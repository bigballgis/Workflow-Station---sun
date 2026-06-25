<template>
  <el-dialog
    v-model="visible"
    :title="t('functionUnit.importPackage')"
    width="520px"
    destroy-on-close
    @closed="resetForm"
  >
    <el-alert
      :title="t('functionUnit.importVersionHint')"
      type="info"
      :closable="false"
      show-icon
      style="margin-bottom: 12px;"
    />
    <el-form label-width="120px">
      <el-form-item :label="t('functionUnit.changeLog')">
        <el-input
          v-model="changeLog"
          type="textarea"
          :rows="2"
          :placeholder="t('functionUnit.changeLogPlaceholder')"
          maxlength="500"
          show-word-limit
        />
      </el-form-item>
    </el-form>
    <el-upload
      ref="uploadRef"
      drag
      :auto-upload="false"
      accept=".zip"
      :limit="1"
      :on-change="handleFileChange"
      :on-exceed="handleExceed"
    >
      <el-icon class="el-icon--upload">
        <UploadFilled />
      </el-icon>
      <div class="el-upload__text">
        {{ t('functionUnit.dragPackageHere') }}<em>{{ t('functionUnit.clickToUpload') }}</em>
      </div>
      <template #tip>
        <div class="el-upload__tip">
          {{ t('functionUnit.zipFormatTip') }}
        </div>
      </template>
    </el-upload>
    <template #footer>
      <el-button @click="visible = false">
        {{ t('common.cancel') }}
      </el-button>
      <el-button
        type="primary"
        :loading="loading"
        :disabled="!importFile"
        @click="handleStartImport"
      >
        {{ t('functionUnit.startImport') }}
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, type UploadInstance, type UploadFile } from 'element-plus'
import { UploadFilled } from '@element-plus/icons-vue'
import { functionUnitApi } from '@/api/functionUnit'

const props = defineProps<{
  modelValue: boolean
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  imported: []
}>()

const { t } = useI18n()

const uploadRef = ref<UploadInstance>()
const importFile = ref<File | null>(null)
const loading = ref(false)
const changeLog = ref('')

const visible = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emit('update:modelValue', value)
})

function resetForm() {
  importFile.value = null
  changeLog.value = ''
  uploadRef.value?.clearFiles()
}

function handleFileChange(file: UploadFile) {
  importFile.value = file.raw ?? null
}

function handleExceed(files: File[]) {
  uploadRef.value?.clearFiles()
  importFile.value = files[0] ?? null
}

async function handleStartImport() {
  if (!importFile.value) return
  loading.value = true
  try {
    const response = await functionUnitApi.importFunctionUnit(importFile.value, changeLog.value)
    const result = response.data
    ElMessage.success(
      result.versioned ? t('functionUnit.importVersioned') : t('functionUnit.importSuccess')
    )
    visible.value = false
    emit('imported')
  } catch (e: unknown) {
    const message = (e as { response?: { data?: { error?: { message?: string }; message?: string } } })
      ?.response?.data?.error?.message
      || (e as { response?: { data?: { message?: string } } })?.response?.data?.message
    ElMessage.error(message || t('functionUnit.importFailed'))
  } finally {
    loading.value = false
  }
}
</script>
