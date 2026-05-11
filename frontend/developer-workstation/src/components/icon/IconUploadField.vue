<template>
  <div class="icon-upload-field">
    <IconPreview
      :icon-id="modelValue ?? undefined"
      :size="size"
    />
    <el-upload
      ref="uploadRef"
      :auto-upload="false"
      :limit="1"
      accept=".svg"
      :show-file-list="false"
      :on-change="handleFileChange"
    >
      <el-button
        type="primary"
        :loading="uploading"
      >
        <el-icon><Upload /></el-icon>
        {{ t('icon.uploadIcon') }}
      </el-button>
    </el-upload>
    <el-button
      v-if="modelValue != null"
      link
      type="danger"
      @click="handleClear"
    >
      {{ t('icon.clear') }}
    </el-button>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, type UploadFile } from 'element-plus'
import { Upload } from '@element-plus/icons-vue'
import { iconApi } from '@/api/icon'
import IconPreview from '@/components/icon/IconPreview.vue'

const { t } = useI18n()

withDefaults(
  defineProps<{
    modelValue?: number | null
    size?: 'small' | 'medium' | 'large'
  }>(),
  { size: 'medium' }
)

const emit = defineEmits<{
  'update:modelValue': [iconId: number | null]
}>()

const uploading = ref(false)
const uploadRef = ref<{ clearFiles: () => void } | null>(null)

async function handleFileChange(file: UploadFile) {
  const raw = file.raw
  if (!raw) return
  const name = raw.name.replace(/\.svg$/i, '') || 'icon'
  uploading.value = true
  try {
    const res = await iconApi.upload(raw, name, 'GENERAL')
    if (res.data?.id != null) {
      emit('update:modelValue', res.data.id)
    }
    ElMessage.success(t('icon.uploadSuccess'))
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || t('icon.uploadFailed'))
  } finally {
    uploading.value = false
    uploadRef.value?.clearFiles()
  }
}

function handleClear() {
  emit('update:modelValue', null)
  uploadRef.value?.clearFiles()
}
</script>

<style lang="scss" scoped>
.icon-upload-field {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}
</style>
