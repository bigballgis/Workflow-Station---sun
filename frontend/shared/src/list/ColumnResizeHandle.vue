<script setup lang="ts">
import { onBeforeUnmount, ref } from 'vue'
import { clampColumnWidth, COL_RESIZE_CURSOR } from './columnResizeCursor'

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

  const startX = event.clientX
  const startWidth = props.initialWidth
  isResizing.value = true
  document.body.classList.add('is-column-resizing')

  function onMouseMove(ev: MouseEvent) {
    emit('resize', clampColumnWidth(startWidth + ev.clientX - startX))
  }

  function detach() {
    document.removeEventListener('mousemove', onMouseMove)
    document.removeEventListener('mouseup', onMouseUp)
    document.body.style.cursor = ''
    document.body.style.userSelect = ''
    document.body.classList.remove('is-column-resizing')
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

// Unmounting mid-drag (e.g. route change while the button is held) must not leak the
// document listeners or leave the body stuck in resize-cursor / no-select mode.
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
