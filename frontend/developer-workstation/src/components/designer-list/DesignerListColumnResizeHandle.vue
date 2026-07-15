<script setup lang="ts">
import { ref } from 'vue'
import { clampColumnWidth, DWL_COL_RESIZE_CURSOR } from '@/utils/designerListGridRuntime'

const props = defineProps<{
  initialWidth: number
}>()

const emit = defineEmits<{
  resize: [width: number]
  resizeEnd: []
}>()

const isResizing = ref(false)

function onMouseDown(event: MouseEvent) {
  event.preventDefault()
  event.stopPropagation()

  const startX = event.clientX
  const startWidth = props.initialWidth
  isResizing.value = true
  document.body.classList.add('dwl-column-resizing')

  function onMouseMove(ev: MouseEvent) {
    emit('resize', clampColumnWidth(startWidth + ev.clientX - startX))
  }

  function onMouseUp() {
    document.removeEventListener('mousemove', onMouseMove)
    document.removeEventListener('mouseup', onMouseUp)
    document.body.style.cursor = ''
    document.body.style.userSelect = ''
    document.body.classList.remove('dwl-column-resizing')
    isResizing.value = false
    emit('resizeEnd')
  }

  document.body.style.cursor = DWL_COL_RESIZE_CURSOR
  document.body.style.userSelect = 'none'
  document.addEventListener('mousemove', onMouseMove)
  document.addEventListener('mouseup', onMouseUp)
}
</script>

<template>
  <span
    class="col-resize-handle"
    :class="{ 'is-active': isResizing }"
    @mousedown="onMouseDown"
    @click.stop
  />
</template>

<style lang="scss">
@import '@/styles/designerListColumnResize.scss';
</style>
