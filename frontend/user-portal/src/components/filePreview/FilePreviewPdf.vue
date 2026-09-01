<template>
  <div
    ref="host"
    class="file-preview-pdf"
  />
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'

const props = defineProps<{ blob: Blob }>()
const emit = defineEmits<{ error: [] }>()
const host = ref<HTMLElement | null>(null)
let cancelled = false

async function hostContentWidth(el: HTMLElement): Promise<number> {
  for (let i = 0; i < 20; i++) {
    if (el.clientWidth >= 200) return el.clientWidth - 24
    await new Promise<void>((resolve) => requestAnimationFrame(() => resolve()))
  }
  return Math.max(320, el.clientWidth - 24)
}

async function renderPdf() {
  cancelled = false
  const el = host.value
  if (!el) return
  el.replaceChildren()
  try {
    const pdfjs = await import('pdfjs-dist')
    const workerSrc = (await import('pdfjs-dist/build/pdf.worker.min.mjs?url')).default
    pdfjs.GlobalWorkerOptions.workerSrc = workerSrc
    const data = new Uint8Array(await props.blob.arrayBuffer())
    const pdf = await pdfjs.getDocument({ data }).promise
    const width = await hostContentWidth(el)
    for (let i = 1; i <= pdf.numPages; i++) {
      if (cancelled) return
      const page = await pdf.getPage(i)
      const viewport = page.getViewport({ scale: width / page.getViewport({ scale: 1 }).width })
      const canvas = document.createElement('canvas')
      canvas.width = viewport.width
      canvas.height = viewport.height
      canvas.className = 'file-preview-pdf-page'
      const ctx = canvas.getContext('2d')
      if (!ctx) continue
      await page.render({ canvasContext: ctx, viewport }).promise
      el.appendChild(canvas)
    }
  } catch {
    emit('error')
  }
}

watch(() => props.blob, () => { void renderPdf() })
onMounted(() => { void renderPdf() })
onBeforeUnmount(() => { cancelled = true })
</script>

<style scoped>
.file-preview-pdf {
  width: 100%;
  height: var(--file-preview-pane-height, 72vh);
  overflow: auto;
  background: #fff;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  padding: 12px 0;
}
.file-preview-pdf :deep(.file-preview-pdf-page) {
  max-width: 100%;
  height: auto;
  box-shadow: 0 1px 4px rgb(0 0 0 / 12%);
}
</style>
