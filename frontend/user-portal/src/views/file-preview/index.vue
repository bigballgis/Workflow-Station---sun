<template>
  <div
    class="file-preview-page"
    data-test="file-preview-page"
  >
    <FilePreviewPane
      v-if="state.url"
      standalone
    />
    <div
      v-else
      class="file-preview-page-empty"
    >
      <el-empty :description="t('filePreview.empty')" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { onBeforeUnmount, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import FilePreviewPane from '@/components/filePreview/FilePreviewPane.vue'
import {
  applyFilePreviewPayload,
  hydrateFilePreviewFromStorage,
  useFilePreviewState,
} from '@/composables/filePreview/useFilePreview'
import { subscribeFilePreviewBroadcast } from '@/composables/filePreview/filePreviewSync'

const { t } = useI18n()
const state = useFilePreviewState()
hydrateFilePreviewFromStorage()
const stopBroadcast = subscribeFilePreviewBroadcast((payload) => {
  applyFilePreviewPayload(payload, true)
})

watch(
  () => state.name,
  (name) => {
    if (name) document.title = name
  },
  { immediate: true },
)

onBeforeUnmount(stopBroadcast)
</script>

<style scoped>
.file-preview-page {
  height: 100vh;
  display: flex;
  flex-direction: column;
  padding: 12px 20px 16px;
  box-sizing: border-box;
  background: var(--el-bg-color);
}

.file-preview-page-empty {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
}
</style>
