<template>
  <el-upload
    drag
    class="form-upload-drop"
    :class="{ 'is-compact': compact }"
    data-testid="form-upload-drop"
    :action="action"
    :accept="accept || ''"
    :limit="limit"
    :multiple="multiple"
    :disabled="disabled"
    :file-list="fileList"
    :http-request="httpRequest"
    :on-success="handleSuccess"
    :on-change="handleChange"
    :on-remove="handleRemove"
    :on-exceed="handleExceed"
    :on-error="handleError"
    :on-preview="handlePreview"
    list-type="text"
  >
    <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
    <div class="el-upload__text">
      {{ dragText }}<em>{{ clickText }}</em>
    </div>
    <template
      v-if="tip"
      #tip
    >
      <div class="el-upload__tip">{{ tip }}</div>
    </template>
  </el-upload>
</template>

<script setup lang="ts">
import { UploadFilled } from '@element-plus/icons-vue'
import type { UploadRequestOptions, UploadUserFile } from 'element-plus'

defineProps<{
  action: string
  dragText: string
  clickText: string
  accept?: string
  limit?: number
  multiple?: boolean
  disabled?: boolean
  compact?: boolean
  tip?: string
  fileList?: UploadUserFile[]
  httpRequest?: (options: UploadRequestOptions) => XMLHttpRequest | Promise<unknown> | void
  handleSuccess?: (...args: unknown[]) => void
  handleChange?: (...args: unknown[]) => void
  handleRemove?: (...args: unknown[]) => void
  handleExceed?: (...args: unknown[]) => void
  handleError?: (...args: unknown[]) => void
  handlePreview?: (...args: unknown[]) => void
}>()
</script>

<style scoped>
.form-upload-drop {
  width: 100%;
}
.form-upload-drop :deep(.el-upload) {
  width: 100%;
}
.form-upload-drop :deep(.el-upload-dragger) {
  width: 100%;
  padding: 18px 12px;
}
.form-upload-drop.is-compact :deep(.el-upload-dragger) {
  padding: 10px 8px;
}
.form-upload-drop.is-compact :deep(.el-icon--upload) {
  font-size: 28px;
  margin-bottom: 4px;
}
</style>
