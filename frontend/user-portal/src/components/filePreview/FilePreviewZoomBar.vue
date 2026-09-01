<template>
  <div class="file-preview-zoom-bar">
    <el-button
      size="small"
      data-test="file-preview-zoom-out"
      @click="$emit('zoom-out')"
    >
      {{ t('filePreview.zoomOut') }}
    </el-button>
    <el-button
      size="small"
      data-test="file-preview-zoom-actual"
      @click="$emit('actual')"
    >
      {{ t('filePreview.zoomActual') }}
    </el-button>
    <el-button
      size="small"
      data-test="file-preview-zoom-in"
      @click="$emit('zoom-in')"
    >
      {{ t('filePreview.zoomIn') }}
    </el-button>
    <el-button
      size="small"
      type="primary"
      plain
      data-test="file-preview-zoom-fit"
      @click="$emit('fit')"
    >
      {{ t('filePreview.zoomFit') }}
    </el-button>
    <span class="file-preview-zoom-pct">{{ t('filePreview.zoomPercent', { n: percent }) }}</span>
    <span class="file-preview-zoom-hint">{{ t('filePreview.zoomWheelHint') }}</span>
  </div>
</template>

<script setup lang="ts">
import { useI18n } from 'vue-i18n'

defineProps<{ percent: number }>()
defineEmits<{
  'zoom-in': []
  'zoom-out': []
  actual: []
  fit: []
}>()

const { t } = useI18n()
</script>

<style scoped>
.file-preview-zoom-bar {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-wrap: wrap;
  gap: 8px;
  padding: 8px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.file-preview-zoom-pct {
  font-size: 13px;
  color: var(--el-text-color-secondary);
  min-width: 3.5em;
}

.file-preview-zoom-hint {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>
