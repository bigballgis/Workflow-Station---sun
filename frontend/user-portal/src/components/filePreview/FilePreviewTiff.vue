<template>
  <div class="file-preview-tiff">
    <div class="file-preview-tiff-toolbar">
      <div
        v-if="pageCount > 1"
        class="file-preview-tiff-pages"
      >
        <el-button
          size="small"
          :disabled="index <= 0"
          @click="index -= 1"
        >
          {{ t('filePreview.prevPage') }}
        </el-button>
        <span>{{ t('filePreview.pageOf', { current: index + 1, total: pageCount }) }}</span>
        <el-button
          size="small"
          :disabled="index >= pageCount - 1"
          @click="index += 1"
        >
          {{ t('filePreview.nextPage') }}
        </el-button>
      </div>
      <FilePreviewZoomBar
        :percent="percent"
        @zoom-in="zoomIn"
        @zoom-out="zoomOut"
        @actual="actualSize"
        @fit="fitWindow"
      />
    </div>
    <div
      ref="host"
      class="file-preview-tiff-scroll"
      data-test="file-preview-tiff-scroll"
    >
      <canvas
        ref="canvasRef"
        class="file-preview-tiff-canvas"
        :style="{ width: cssWidth, height: cssHeight }"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import FilePreviewZoomBar from './FilePreviewZoomBar.vue'
import { usePreviewZoom } from '@/composables/filePreview/usePreviewZoom'
import { selectTiffDisplayPages, type TiffDisplayPage } from '@/utils/filePreviewTiff'

const props = defineProps<{ blob: Blob }>()
const emit = defineEmits<{ error: [] }>()
const { t } = useI18n()
const canvasRef = ref<HTMLCanvasElement | null>(null)
const index = ref(0)
const pageCount = ref(1)
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

let buffer: ArrayBuffer | null = null
let rawIfds: unknown[] = []
let pages: TiffDisplayPage[] = []

function paintRgba(width: number, height: number, rgba: Uint8Array) {
  const canvas = canvasRef.value
  if (!canvas) return
  canvas.width = width
  canvas.height = height
  const ctx = canvas.getContext('2d')
  if (!ctx) return
  const image = ctx.createImageData(width, height)
  image.data.set(rgba)
  ctx.putImageData(image, 0, 0)
  setNativeSize(width, height)
}

async function paintCurrent() {
  const page = pages[index.value]
  if (!page || !buffer) return
  try {
    const mod = await import('utif')
    const UTIF = (mod as { default?: typeof mod }).default ?? mod
    const ifd = rawIfds[page.ifdIndex] as Parameters<typeof UTIF.decodeImage>[1]
    UTIF.decodeImage(buffer, ifd, rawIfds as Parameters<typeof UTIF.decodeImage>[2])
    paintRgba(page.width, page.height, UTIF.toRGBA8(ifd))
  } catch {
    emit('error')
  }
}

async function loadTiff() {
  try {
    const mod = await import('utif')
    const UTIF = (mod as { default?: typeof mod }).default ?? mod
    buffer = await props.blob.arrayBuffer()
    rawIfds = UTIF.decode(buffer)
    pages = selectTiffDisplayPages(rawIfds as Array<{ width?: number; height?: number; t256?: number[]; t257?: number[] }>)
    pageCount.value = Math.max(pages.length, 1)
    index.value = 0
    if (pages.length === 0) {
      emit('error')
      return
    }
    fitWindow()
    await paintCurrent()
  } catch {
    emit('error')
  }
}

watch(() => props.blob, () => { void loadTiff() })
onMounted(() => { void loadTiff() })
watch(index, () => { void paintCurrent() })
</script>

<style scoped>
.file-preview-tiff {
  width: 100%;
  height: var(--file-preview-pane-height, 72vh);
  display: flex;
  flex-direction: column;
  background: #fff;
  min-height: 0;
}

.file-preview-tiff-toolbar {
  flex-shrink: 0;
}

.file-preview-tiff-pages {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  padding: 8px 8px 0;
}

.file-preview-tiff-scroll {
  flex: 1;
  min-height: 0;
  overflow: auto;
  display: flex;
  align-items: flex-start;
  justify-content: flex-start;
  background: var(--el-fill-color-lighter);
}

.file-preview-tiff-canvas {
  display: block;
  flex-shrink: 0;
}
</style>
