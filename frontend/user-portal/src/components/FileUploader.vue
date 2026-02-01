<template>
  <div class="file-uploader">
    <el-upload
      ref="uploadRef"
      v-model:file-list="fileList"
      :action="uploadUrl"
      :headers="uploadHeaders"
      :accept="acceptTypes"
      :limit="limit"
      :multiple="multiple"
      :disabled="disabled || readonly"
      :before-upload="handleBeforeUpload"
      :on-success="handleSuccess"
      :on-error="handleError"
      :on-remove="handleRemove"
      :on-exceed="handleExceed"
      :on-preview="handlePreview"
      :drag="drag"
      :list-type="listType"
      :class="{ 'is-readonly': readonly }"
    >
      <!-- 拖拽上传 -->
      <template v-if="drag">
        <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
        <div class="el-upload__text">
          {{ $t('upload.dragText') }}
          <em>{{ $t('upload.clickText') }}</em>
        </div>
      </template>

      <!-- 按钮上传 -->
      <template v-else-if="listType === 'text'">
        <el-button type="primary" :disabled="disabled || readonly">
          <el-icon><Upload /></el-icon>
          {{ buttonText || $t('upload.selectFile') }}
        </el-button>
      </template>

      <!-- 图片上传 -->
      <template v-else-if="listType === 'picture-card'">
        <el-icon><Plus /></el-icon>
      </template>

      <!-- 提示信息 -->
      <template #tip>
        <div class="el-upload__tip" v-if="showTip">
          {{ tipText || defaultTipText }}
        </div>
      </template>

      <!-- 文件列表项 -->
      <template #file="{ file }">
        <div class="file-item" :class="{ 'is-error': file.status === 'fail' }">
          <el-icon class="file-icon">
            <component :is="getFileIcon(file.name)" />
          </el-icon>
          <span class="file-name" :title="file.name">{{ file.name }}</span>
          <span class="file-size" v-if="file.size">{{ formatFileSize(file.size) }}</span>
          <div class="file-actions">
            <el-button
              v-if="file.status === 'success'"
              link
              type="primary"
              @click.stop="handlePreview(file)"
            >
              <el-icon><View /></el-icon>
            </el-button>
            <el-button
              v-if="file.status === 'success'"
              link
              type="primary"
              @click.stop="handleDownload(file)"
            >
              <el-icon><Download /></el-icon>
            </el-button>
            <el-button
              v-if="!readonly"
              link
              type="danger"
              @click.stop="handleRemoveFile(file)"
            >
              <el-icon><Delete /></el-icon>
            </el-button>
          </div>
          <el-progress
            v-if="file.status === 'uploading'"
            :percentage="file.percentage || 0"
            :stroke-width="2"
          />
        </div>
      </template>
    </el-upload>

    <!-- 图片预览 -->
    <el-image-viewer
      v-if="previewVisible && isImage(previewUrl)"
      :url-list="[previewUrl]"
      @close="previewVisible = false"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import {
  UploadFilled,
  Upload,
  Plus,
  View,
  Download,
  Delete,
  Document,
  Picture,
  VideoPlay,
  Headset,
  FolderOpened
} from '@element-plus/icons-vue'
import type { UploadInstance, UploadFile, UploadRawFile, UploadUserFile } from 'element-plus'

export interface FileInfo {
  id: string
  name: string
  url: string
  size?: number
  type?: string
}

interface Props {
  modelValue?: FileInfo[]
  uploadUrl?: string
  accept?: string
  limit?: number
  maxSize?: number // MB
  multiple?: boolean
  disabled?: boolean
  readonly?: boolean
  drag?: boolean
  listType?: 'text' | 'picture' | 'picture-card'
  buttonText?: string
  tipText?: string
  showTip?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  modelValue: () => [],
  uploadUrl: '/api/upload',
  accept: '',
  limit: 10,
  maxSize: 10,
  multiple: true,
  disabled: false,
  readonly: false,
  drag: false,
  listType: 'text',
  showTip: true
})

const emit = defineEmits<{
  (e: 'update:modelValue', value: FileInfo[]): void
  (e: 'success', file: FileInfo): void
  (e: 'error', error: Error): void
  (e: 'remove', file: FileInfo): void
  (e: 'preview', file: FileInfo): void
  (e: 'download', file: FileInfo): void
}>()

const { t } = useI18n()

const uploadRef = ref<UploadInstance>()
const fileList = ref<UploadUserFile[]>([])
const previewVisible = ref(false)
const previewUrl = ref('')

// 上传请求头
const uploadHeaders = computed(() => ({
  Authorization: `Bearer ${localStorage.getItem('token') || ''}`
}))

// 接受的文件类型
const acceptTypes = computed(() => {
  if (props.accept) return props.accept
  return '.jpg,.jpeg,.png,.gif,.pdf,.doc,.docx,.xls,.xlsx,.ppt,.pptx,.txt,.zip,.rar'
})

// 默认提示文本
const defaultTipText = computed(() => {
  const types = acceptTypes.value.split(',').slice(0, 5).join('、')
  return t('upload.tip', { types, size: props.maxSize })
})

// 初始化文件列表
watch(() => props.modelValue, (newVal) => {
  fileList.value = newVal.map(file => ({
    name: file.name,
    url: file.url,
    uid: file.id as any,
    status: 'success'
  }))
}, { immediate: true })

// 上传前校验
const handleBeforeUpload = (file: UploadRawFile) => {
  // 检查文件大小
  const isLtMaxSize = file.size / 1024 / 1024 < props.maxSize
  if (!isLtMaxSize) {
    ElMessage.error(t('upload.sizeExceed', { size: props.maxSize }))
    return false
  }

  // 检查文件类型
  if (props.accept) {
    const ext = '.' + file.name.split('.').pop()?.toLowerCase()
    const accepts = props.accept.toLowerCase().split(',')
    if (!accepts.includes(ext)) {
      ElMessage.error(t('upload.typeError'))
      return false
    }
  }

  return true
}

// 上传成功
const handleSuccess = (response: any, file: UploadFile) => {
  const fileInfo: FileInfo = {
    id: response.data?.id || file.uid,
    name: file.name,
    url: response.data?.url || file.url || '',
    size: file.size,
    type: file.raw?.type
  }

  const newValue = [...props.modelValue, fileInfo]
  emit('update:modelValue', newValue)
  emit('success', fileInfo)
  ElMessage.success(t('upload.success'))
}

// 上传失败
const handleError = (error: Error) => {
  emit('error', error)
  ElMessage.error(t('upload.failed'))
}

// 移除文件
const handleRemove = (file: UploadFile) => {
  const fileInfo = props.modelValue.find(f => {
    const fileId = typeof f.id === 'string' ? f.id : String(f.id)
    const fileUid = typeof file.uid === 'string' ? file.uid : String(file.uid)
    return fileId === fileUid || f.name === file.name
  })
  if (fileInfo) {
    const newValue = props.modelValue.filter(f => {
      const fileId = typeof f.id === 'string' ? f.id : String(f.id)
      const infoId = typeof fileInfo.id === 'string' ? fileInfo.id : String(fileInfo.id)
      return fileId !== infoId
    })
    emit('update:modelValue', newValue)
    emit('remove', fileInfo)
  }
}

// 手动移除文件
const handleRemoveFile = (file: UploadFile) => {
  uploadRef.value?.handleRemove(file)
}

// 超出限制
const handleExceed = () => {
  ElMessage.warning(t('upload.limitExceed', { limit: props.limit }))
}

// 预览文件
const handlePreview = (file: UploadFile) => {
  const fileInfo = props.modelValue.find(f => f.name === file.name)
  if (fileInfo) {
    if (isImage(file.name)) {
      previewUrl.value = fileInfo.url
      previewVisible.value = true
    } else {
      window.open(fileInfo.url, '_blank')
    }
    emit('preview', fileInfo)
  }
}

// 下载文件
const handleDownload = (file: UploadFile) => {
  const fileInfo = props.modelValue.find(f => f.name === file.name)
  if (fileInfo) {
    const link = document.createElement('a')
    link.href = fileInfo.url
    link.download = fileInfo.name
    link.click()
    emit('download', fileInfo)
  }
}

// 判断是否为图片
const isImage = (filename: string) => {
  const ext = filename.split('.').pop()?.toLowerCase()
  return ['jpg', 'jpeg', 'png', 'gif', 'bmp', 'webp'].includes(ext || '')
}

// 获取文件图标
const getFileIcon = (filename: string) => {
  const ext = filename.split('.').pop()?.toLowerCase()
  if (['jpg', 'jpeg', 'png', 'gif', 'bmp', 'webp'].includes(ext || '')) {
    return Picture
  }
  if (['mp4', 'avi', 'mov', 'wmv'].includes(ext || '')) {
    return VideoPlay
  }
  if (['mp3', 'wav', 'flac'].includes(ext || '')) {
    return Headset
  }
  if (['zip', 'rar', '7z', 'tar', 'gz'].includes(ext || '')) {
    return FolderOpened
  }
  return Document
}

// 格式化文件大小
const formatFileSize = (bytes: number) => {
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / 1024 / 1024).toFixed(1) + ' MB'
}

// 清空文件列表
const clearFiles = () => {
  uploadRef.value?.clearFiles()
  emit('update:modelValue', [])
}

defineExpose({
  clearFiles
})
</script>

<style scoped lang="scss">
.file-uploader {
  width: 100%;

  &.is-readonly {
    :deep(.el-upload) {
      display: none;
    }
  }

  .file-item {
    display: flex;
    align-items: center;
    padding: 8px 12px;
    background: #f5f7fa;
    border-radius: 4px;
    margin-bottom: 8px;

    &.is-error {
      background: #fef0f0;
    }

    .file-icon {
      font-size: 20px;
      color: #909399;
      margin-right: 8px;
    }

    .file-name {
      flex: 1;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
      font-size: 13px;
      color: #303133;
    }

    .file-size {
      font-size: 12px;
      color: #909399;
      margin: 0 12px;
    }

    .file-actions {
      display: flex;
      gap: 4px;
    }

    .el-progress {
      position: absolute;
      bottom: 0;
      left: 0;
      right: 0;
    }
  }

  :deep(.el-upload-dragger) {
    padding: 30px 20px;

    .el-icon--upload {
      font-size: 48px;
      color: #c0c4cc;
      margin-bottom: 10px;
    }
  }

  :deep(.el-upload__tip) {
    color: #909399;
    font-size: 12px;
    margin-top: 8px;
  }
}
</style>
