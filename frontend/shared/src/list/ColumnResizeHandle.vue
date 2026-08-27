<script setup lang="ts">
import { onBeforeUnmount, ref } from 'vue'
import {
  attachColumnResizeGuide,
  clampDisplayWidth,
  COL_RESIZE_CURSOR,
  startWidthFromHandle,
  type ColumnResizeGuide,
} from './columnResizeCursor'

const props = defineProps<{
  initialWidth: number
}>()

const emit = defineEmits<{
  resize: [width: number]
  resizeEnd: []
}>()

const isResizing = ref(false)
let detachActiveDrag: (() => void) | null = null

function onMouseDown(event: MouseEvent) {
  event.preventDefault()
  event.stopPropagation()

  const handle = event.currentTarget as HTMLElement
  const startX = event.clientX
  const startWidth = startWidthFromHandle(handle, props.initialWidth)
  const guide: ColumnResizeGuide = attachColumnResizeGuide(handle, startWidth)
  isResizing.value = true
  document.body.classList.add('is-column-resizing')

  function onMouseMove(ev: MouseEvent) {
    const width = clampDisplayWidth(startWidth + ev.clientX - startX)
    emit('resize', width)
    guide.move(width)
  }

  function detach() {
    document.removeEventListener('mousemove', onMouseMove)
    document.removeEventListener('mouseup', onMouseUp)
    document.body.style.cursor = ''
    document.body.style.userSelect = ''
    document.body.classList.remove('is-column-resizing')
    guide.detach()
    isResizing.value = false
    detachActiveDrag = null
  }

  function onMouseUp() {
    detach()
    emit('resizeEnd')
  }

  document.body.style.cursor = COL_RESIZE_CURSOR
  document.body.style.userSelect = 'none'
  document.addEventListener('mousemove', onMouseMove)
  document.addEventListener('mouseup', onMouseUp)
  detachActiveDrag = detach
}

onBeforeUnmount(() => {
  detachActiveDrag?.()
})
</script>

<template>
  <span
    class="col-resize-handle"
    :class="{ 'is-active': isResizing }"
    @mousedown="onMouseDown"
  />
</template>

<style lang="scss">
@import './columnResizeCursor.scss';
</style>
