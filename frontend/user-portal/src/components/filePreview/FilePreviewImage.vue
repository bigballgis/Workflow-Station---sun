<template>
  <div class="file-preview-image-wrap">
    <FilePreviewZoomBar
      :percent="percent"
      @zoom-in="zoomIn"
      @zoom-out="zoomOut"
      @actual="actualSize"
      @fit="fitWindow"
    />
    <div
      ref="host"
      class="file-preview-image-scroll"
      data-test="file-preview-image-scroll"
    >
      <img
        class="file-preview-image-el"
        :src="src"
        :alt="alt"
        :style="{ width: cssWidth, height: cssHeight }"
        @load="onLoad"
      >
    </div>
  </div>
</template>

<script setup lang="ts">
import { watch } from 'vue'
import FilePreviewZoomBar from './FilePreviewZoomBar.vue'
import { usePreviewZoom } from '@/composables/filePreview/usePreviewZoom'

const props = defineProps<{ src: string; alt: string }>()
const {
  host,
  percent,
  cssWidth,
  cssHeight,
  setNativeSize,
  zoomIn,
  zoomOut,
  actualSize,
  fitWindow,
} = usePreviewZoom()

function onLoad(event: Event) {
  const img = event.target as HTMLImageElement
  fitWindow()
  setNativeSize(img.naturalWidth, img.naturalHeight)
}

watch(() => props.src, () => fitWindow())
</script>

<style scoped>
.file-preview-image-wrap {
  width: 100%;
  height: var(--file-preview-pane-height, 72vh);
  display: flex;
  flex-direction: column;
  min-height: 0;
  background: #fff;
}

.file-preview-image-scroll {
  flex: 1;
  min-height: 0;
  overflow: auto;
  display: flex;
  align-items: flex-start;
  justify-content: flex-start;
  background: var(--el-fill-color-lighter);
}

.file-preview-image-el {
  display: block;
  flex-shrink: 0;
}
</style>
