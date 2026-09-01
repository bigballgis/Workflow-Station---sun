<template>
  <iframe
    ref="frameRef"
    class="file-preview-office-frame"
    sandbox="allow-scripts allow-same-origin"
    :title="title"
  />
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch, nextTick } from 'vue'

const props = defineProps<{ blob: Blob; mode: 'docx' | 'pptx'; title: string }>()
const emit = defineEmits<{ error: [] }>()
const frameRef = ref<HTMLIFrameElement | null>(null)
let cancelled = false

function officeDocument(): Document | null {
  return frameRef.value?.contentDocument ?? null
}

async function waitForFrameWidth(frame: HTMLIFrameElement): Promise<number> {
  for (let i = 0; i < 30; i++) {
    if (frame.clientWidth >= 200) return frame.clientWidth
    await new Promise<void>((resolve) => requestAnimationFrame(() => resolve()))
  }
  return Math.max(frame.clientWidth, 960)
}

function pptxViewport(frameWidth: number): { width: number; height: number } {
  const width = Math.max(480, frameWidth - 32)
  return { width, height: Math.round(width * 9 / 16) }
}

async function renderDocx(doc: Document) {
  const { renderAsync } = await import('docx-preview')
  if (cancelled) return
  await renderAsync(props.blob, doc.body, doc.head, {
    breakPages: true,
    renderHeaders: true,
    renderFooters: true,
    renderFootnotes: true,
    renderAltChunks: false,
    useBase64URL: true,
  })
}

async function renderPptx(doc: Document, frame: HTMLIFrameElement) {
  const { init } = await import('pptx-preview')
  if (cancelled || !doc.body) return
  const size = pptxViewport(await waitForFrameWidth(frame))
  const viewer = init(doc.body, { width: size.width, height: size.height, mode: 'list' })
  await viewer.preview(await props.blob.arrayBuffer())
}

async function renderOffice() {
  cancelled = false
  await nextTick()
  const frame = frameRef.value
  const doc = officeDocument()
  if (!frame || !doc) return
  doc.open()
  doc.write(
    '<!DOCTYPE html><html><head><style>'
    + 'html,body{margin:0;width:100%;background:#eef0f3;}'
    + '.pptx-preview-slide-wrapper{margin:12px auto !important;box-shadow:0 1px 8px rgb(0 0 0 / 16%);}'
    + '</style></head><body></body></html>',
  )
  doc.close()
  try {
    if (props.mode === 'docx') {
      await renderDocx(doc)
      return
    }
    await renderPptx(doc, frame)
  } catch {
    emit('error')
  }
}

watch(
  () => [props.blob, props.mode] as const,
  () => { void renderOffice() },
)
onMounted(() => { void renderOffice() })
onBeforeUnmount(() => {
  cancelled = true
  const frame = frameRef.value
  if (frame) frame.src = 'about:blank'
})
</script>

<style scoped>
.file-preview-office-frame {
  width: 100%;
  height: var(--file-preview-pane-height, 72vh);
  border: 0;
  background: #eef0f3;
  align-self: stretch;
}
</style>
