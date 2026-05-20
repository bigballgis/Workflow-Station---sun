<template>
  <Teleport to="body">
    <div
      v-if="visible"
      class="sub-table-nested-modal-overlay"
      :style="{ zIndex: overlayZ }"
      @mousedown.self="onOverlayMouseDown"
    >
      <div
        class="sub-table-nested-modal-panel"
        :style="panelStyle"
        role="dialog"
        aria-modal="true"
        @mousedown.stop
      >
        <div class="sub-table-nested-modal-header">
          <span class="sub-table-nested-modal-title">{{ title }}</span>
          <button
            type="button"
            class="sub-table-nested-modal-close"
            :aria-label="t('common.close')"
            @click="requestClose"
          >
            <el-icon :size="18">
              <Close />
            </el-icon>
          </button>
        </div>
        <div class="sub-table-nested-modal-body">
          <slot />
        </div>
        <div
          v-if="$slots.footer"
          class="sub-table-nested-modal-footer"
        >
          <slot name="footer" />
        </div>
      </div>
    </div>
  </Teleport>
</template>

<script setup lang="ts">
import { ref, watch, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { Close } from '@element-plus/icons-vue'
import { useZIndex } from 'element-plus'

const props = withDefaults(
  defineProps<{
    visible: boolean
    title?: string
    width?: string
  }>(),
  { width: 'min(700px, calc(100vw - 48px))' },
)

const emit = defineEmits<{
  (e: 'update:visible', val: boolean): void
  (e: 'closed'): void
}>()

const { t } = useI18n()
const { nextZIndex } = useZIndex()

const overlayZ = ref(2000)
const suppressBackdropClose = ref(false)

const panelStyle = computed(() => ({ width: props.width }))

function refreshZIndex() {
  overlayZ.value = nextZIndex()
  document.documentElement.style.setProperty('--sub-table-nested-popper-z', String(overlayZ.value + 50))
}

watch(
  () => props.visible,
  (open) => {
    if (!open) return
    refreshZIndex()
    suppressBackdropClose.value = true
    window.setTimeout(() => {
      suppressBackdropClose.value = false
    }, 400)
  },
)

function requestClose() {
  emit('update:visible', false)
  emit('closed')
}

function onOverlayMouseDown() {
  if (suppressBackdropClose.value) return
  requestClose()
}
</script>

<style>
.sub-table-nested-modal-overlay {
  position: fixed;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px 16px;
  background: rgba(0, 0, 0, 0.5);
}

.sub-table-nested-modal-panel {
  max-height: 84vh;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  border-radius: 4px;
  background: #fff;
  box-shadow: 0 12px 32px rgba(0, 0, 0, 0.18);
}

.sub-table-nested-modal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 16px;
  border-bottom: 1px solid #ebeef5;
}

.sub-table-nested-modal-title {
  flex: 1;
  min-width: 0;
  font-weight: 600;
  font-size: 16px;
  line-height: 1.4;
  color: #303133;
}

.sub-table-nested-modal-close {
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  margin: 0;
  padding: 0;
  border: none;
  border-radius: 50%;
  background: transparent;
  color: #909399;
  cursor: pointer;
}

.sub-table-nested-modal-close:hover {
  color: var(--el-color-primary);
  background: var(--el-fill-color-light);
}

.sub-table-nested-modal-body {
  flex: 1;
  min-height: 0;
  padding: 16px;
  overflow: auto;
}

.sub-table-nested-modal-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  flex-shrink: 0;
  padding: 12px 16px;
  border-top: 1px solid #ebeef5;
  background: #fafafa;
}
</style>
