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
        v-else-if="kind === 'image' && imageUrl"
        class="file-preview-image"
        :src="imageUrl"
        :alt="state.name"
      >
      <FilePreviewPdf
        v-else-if="kind === 'pdf' && previewBlob"
        :blob="previewBlob"
        @error="onParseError"
      />
      <FilePreviewText
        v-else-if="(kind === 'text' || kind === 'doc') && previewBlob"
        :blob="previewBlob"
        :mode="kind === 'doc' ? 'doc' : 'text'"
        @error="onParseError"
      />
      <FilePreviewTable
        v-else-if="kind === 'spreadsheet' && previewBlob"
        :blob="previewBlob"
        @error="onParseError"
      />
      <FilePreviewTiff
        v-else-if="kind === 'tiff' && previewBlob"
        :blob="previewBlob"
        @error="onParseError"
      />
      <FilePreviewOfficeFrame
        v-else-if="(kind === 'docx' || kind === 'pptx') && previewBlob"
        :blob="previewBlob"
        :mode="kind === 'docx' ? 'docx' : 'pptx'"
        :title="state.name"
        @error="onParseError"
      />
      <el-empty
        v-else-if="!loading"
        :description="t('filePreview.unsupported')"
      />
    </div>
  </el-dialog>
</template>

<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { Download, Close } from '@element-plus/icons-vue'
import { useFilePreviewContent } from '@/composables/filePreview/useFilePreviewContent'
import FilePreviewPdf from '@/components/filePreview/FilePreviewPdf.vue'
import FilePreviewText from '@/components/filePreview/FilePreviewText.vue'
import FilePreviewTable from '@/components/filePreview/FilePreviewTable.vue'
import FilePreviewTiff from '@/components/filePreview/FilePreviewTiff.vue'
import FilePreviewOfficeFrame from '@/components/filePreview/FilePreviewOfficeFrame.vue'

const { t } = useI18n()
const {
  state,
  loading,
  error,
  kind,
  previewBlob,
  downloading,
  downloadCurrent,
  close,
} = useFilePreviewContent()

const imageUrl = ref('')

function onParseError() {
  error.value = t('filePreview.parseFailed')
}

watch(
  () => [kind.value, previewBlob.value] as const,
  () => {
    if (imageUrl.value) {
      URL.revokeObjectURL(imageUrl.value)
      imageUrl.value = ''
    }
    if (kind.value === 'image' && previewBlob.value) {
      imageUrl.value = URL.createObjectURL(previewBlob.value)
    }
  },
)

onBeforeUnmount(() => {
  if (imageUrl.value) URL.revokeObjectURL(imageUrl.value)
})
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
  align-items: stretch;
  justify-content: center;
  background: var(--el-fill-color-lighter);
  border-radius: 4px;
  overflow: auto;
}

.file-preview-image {
  max-width: 100%;
  max-height: 72vh;
  object-fit: contain;
  margin: auto;
}
</style>
