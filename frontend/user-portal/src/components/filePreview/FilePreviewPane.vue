<template>
  <div
    class="file-preview-shell"
    :class="{ 'is-standalone': standalone }"
    data-test="file-preview-shell"
  >
    <div class="file-preview-header">
      <span
        class="file-preview-title"
        :title="state.name"
      >{{ state.name }}</span>
      <div class="file-preview-actions">
        <template v-if="state.items.length > 1">
          <el-button
            size="small"
            data-test="file-preview-prev-file"
            :disabled="state.index <= 0"
            @click="showPrev"
          >
            {{ t('filePreview.prevFile') }}
          </el-button>
          <span class="file-preview-file-of">{{ t('filePreview.fileOf', { current: state.index + 1, total: state.items.length }) }}</span>
          <el-button
            size="small"
            data-test="file-preview-next-file"
            :disabled="state.index >= state.items.length - 1"
            @click="showNext"
          >
            {{ t('filePreview.nextFile') }}
          </el-button>
        </template>
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
        <PortalHelpLink
          path="/up-tasks-to-claim#file-preview"
          :ariaLabel="t('filePreview.guideLinkAria')"
          test-id="file-preview-guide-link"
        />
        <el-button
          size="small"
          data-test="file-preview-close"
          @click="handleClose"
        >
          <el-icon><Close /></el-icon>
          {{ t('filePreview.closeWindow') }}
        </el-button>
      </div>
    </div>

    <div
      v-loading="loading"
      class="file-preview-body"
      :class="{ 'is-fill': standalone }"
    >
      <el-empty
        v-if="error"
        :description="error"
      />
      <FilePreviewImage
        v-else-if="kind === 'image' && imageUrl"
        :src="imageUrl"
        :alt="state.name"
      />
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
  </div>
</template>

<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { Download, Close } from '@element-plus/icons-vue'
import { useFilePreviewContent } from '@/composables/filePreview/useFilePreviewContent'
import PortalHelpLink from '@/components/PortalHelpLink.vue'
import FilePreviewPdf from '@/components/filePreview/FilePreviewPdf.vue'
import FilePreviewText from '@/components/filePreview/FilePreviewText.vue'
import FilePreviewTable from '@/components/filePreview/FilePreviewTable.vue'
import FilePreviewTiff from '@/components/filePreview/FilePreviewTiff.vue'
import FilePreviewOfficeFrame from '@/components/filePreview/FilePreviewOfficeFrame.vue'
import FilePreviewImage from '@/components/filePreview/FilePreviewImage.vue'

const props = withDefaults(defineProps<{ standalone?: boolean }>(), { standalone: false })
const { t } = useI18n()
const {
  state,
  loading,
  error,
  kind,
  previewBlob,
  downloading,
  downloadCurrent,
  showPrev,
  showNext,
  close,
} = useFilePreviewContent()

const imageUrl = ref('')

function onParseError() {
  error.value = t('filePreview.parseFailed')
}

function handleClose() {
  if (props.standalone) {
    window.close()
    return
  }
  close()
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
.file-preview-shell {
  display: flex;
  flex-direction: column;
  min-height: 0;
  min-width: 0;
}

.file-preview-shell.is-standalone {
  height: 100%;
}

.file-preview-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  min-width: 0;
  padding: 4px 0 12px;
  flex-shrink: 0;
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
  flex-wrap: wrap;
}

.file-preview-file-of {
  font-size: 13px;
  color: var(--el-text-color-secondary);
  white-space: nowrap;
}

.file-preview-body {
  --file-preview-pane-height: 72vh;
  min-height: 60vh;
  max-height: 75vh;
  display: flex;
  align-items: stretch;
  justify-content: center;
  background: var(--el-fill-color-lighter);
  border-radius: 4px;
  overflow: hidden;
}

.file-preview-body.is-fill {
  --file-preview-pane-height: 100%;
  flex: 1;
  min-height: 0;
  max-height: none;
}
</style>
