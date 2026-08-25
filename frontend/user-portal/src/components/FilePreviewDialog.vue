<template>
  <el-dialog
    :model-value="state.visible"
    class="file-preview-dialog"
    append-to-body
    align-center
    width="80%"
    :show-close="false"
    destroy-on-close
    @close="close"
  >
    <template #header>
      <div class="file-preview-header">
        <span
          class="file-preview-title"
          :title="state.name"
        >{{ state.name }}</span>
        <div class="file-preview-actions">
          <el-button
            v-if="!state.cannotDownload"
            type="primary"
            size="small"
            :loading="downloading"
            @click="downloadCurrent"
          >
            <el-icon><Download /></el-icon>
            {{ t('filePreview.download') }}
          </el-button>
          <el-button
            size="small"
            @click="close"
          >
            <el-icon><Close /></el-icon>
            {{ t('filePreview.closeWindow') }}
          </el-button>
        </div>
      </div>
    </template>

    <div
      v-loading="loading"
      class="file-preview-body"
    >
      <el-empty
        v-if="error"
        :description="error"
      />
      <img
        v-else-if="kind === 'image' && objectUrl"
        class="file-preview-image"
        :src="objectUrl"
        :alt="state.name"
      >
      <iframe
        v-else-if="kind === 'pdf' && objectUrl"
        class="file-preview-frame"
        :src="objectUrl"
        :title="state.name"
      />
      <pre
        v-else-if="kind === 'text'"
        class="file-preview-text"
      >{{ textContent }}</pre>
      <el-empty
        v-else-if="!loading"
        :description="t('filePreview.unsupported')"
      />
    </div>
  </el-dialog>
</template>

<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { Download, Close } from '@element-plus/icons-vue'
import { useFilePreviewContent } from '@/composables/filePreview/useFilePreviewContent'

const { t } = useI18n()
const {
  state,
  loading,
  error,
  kind,
  objectUrl,
  textContent,
  downloading,
  downloadCurrent,
  close,
} = useFilePreviewContent()
</script>

<style scoped>
.file-preview-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  min-width: 0;
}

.file-preview-title {
  flex: 1;
  min-width: 0;
  font-size: 16px;
  font-weight: 600;
  color: var(--el-text-color-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.file-preview-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.file-preview-body {
  min-height: 60vh;
  max-height: 75vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--el-fill-color-lighter);
  border-radius: 4px;
  overflow: auto;
}

.file-preview-image {
  max-width: 100%;
  max-height: 72vh;
  object-fit: contain;
}

.file-preview-frame {
  width: 100%;
  height: 72vh;
  border: 0;
  background: #fff;
}

.file-preview-text {
  width: 100%;
  height: 72vh;
  margin: 0;
  padding: 16px;
  overflow: auto;
  background: #fff;
  color: var(--el-text-color-regular);
  font-size: 13px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
}
</style>
