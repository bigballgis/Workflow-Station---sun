<template>
  <el-dialog
    :model-value="state.visible"
    class="file-preview-dialog"
    append-to-body
    align-center
    width="80%"
    :show-close="false"
    destroy-on-close
    @close="closeFilePreview"
  >
    <FilePreviewPane />
  </el-dialog>
</template>

<script setup lang="ts">
import { closeFilePreview, useFilePreviewState } from '@/composables/filePreview/useFilePreview'
import FilePreviewPane from '@/components/filePreview/FilePreviewPane.vue'

const state = useFilePreviewState()
</script>

<style>
.file-preview-dialog.el-dialog .el-dialog__header {
  display: none;
}

.file-preview-dialog.el-dialog .el-dialog__body {
  padding: 8px 16px 16px;
}
</style>
