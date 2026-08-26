<template>
  <div class="file-preview-tiff">
    <div class="file-preview-tiff-bar">
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
    <canvas
      ref="canvasRef"
      class="file-preview-tiff-canvas"
    />
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'

const props = defineProps<{ blob: Blob }>()
const emit = defineEmits<{ error: [] }>()
const { t } = useI18n()
const canvasRef = ref<HTMLCanvasElement | null>(null)
const index = ref(0)
const pageCount = ref(1)
let pages: Array<{ width: number; height: number; rgba: Uint8Array }> = []

function paint() {
  const canvas = canvasRef.value
  const page = pages[index.value]
  if (!canvas || !page) return
  canvas.width = page.width
  canvas.height = page.height
  const ctx = canvas.getContext('2d')
  if (!ctx) return
  const image = ctx.createImageData(page.width, page.height)
  image.data.set(page.rgba)
  ctx.putImageData(image, 0, 0)
}

async function loadTiff() {
  try {
    const mod = await import('utif')
    const UTIF = (mod as { default?: typeof mod }).default ?? mod
    const buffer = await props.blob.arrayBuffer()
    const ifds = UTIF.decode(buffer)
    pages = ifds.map((ifd) => {
      UTIF.decodeImage(buffer, ifd, ifds)
      const width = Number(ifd.width || ifd.t256?.[0] || 0)
      const height = Number(ifd.height || ifd.t257?.[0] || 0)
      return { width, height, rgba: UTIF.toRGBA8(ifd) }
    }).filter((p) => p.width > 0 && p.height > 0)
    pageCount.value = Math.max(pages.length, 1)
    index.value = 0
    if (pages.length === 0) {
      emit('error')
      return
    }
    paint()
  } catch {
    emit('error')
  }
}

watch(() => props.blob, () => { void loadTiff() })
onMounted(() => { void loadTiff() })
watch(index, paint)
</script>

<style scoped>
.file-preview-tiff {
  width: 100%;
  height: 72vh;
  display: flex;
  flex-direction: column;
  background: #fff;
}
.file-preview-tiff-bar {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  padding: 8px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}
.file-preview-tiff-canvas {
  max-width: 100%;
  max-height: 100%;
  margin: auto;
  object-fit: contain;
}
</style>
