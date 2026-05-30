<template>
  <div
    class="form-designer-canvas-toolbar"
    :class="{ 'form-designer-canvas-toolbar--designer-bar': inDesignerBar }"
  >
    <div class="toolbar-group">
      <span class="toolbar-label">{{ t('form.canvasShowHidden') }}</span>
      <el-switch
        :model-value="showHidden"
        size="small"
        @update:model-value="emit('update:showHidden', $event)"
      />
    </div>
    <div class="toolbar-group toolbar-zoom">
      <el-button
        size="small"
        text
        :disabled="zoomPercent <= minZoom"
        :aria-label="t('form.canvasZoomOut')"
        @click="stepZoom(-10)"
      >
        −
      </el-button>
      <el-slider
        :model-value="zoomPercent"
        class="zoom-slider"
        :min="minZoom"
        :max="maxZoom"
        :step="5"
        :show-tooltip="false"
        @update:model-value="emit('update:zoomPercent', $event)"
      />
      <el-button
        size="small"
        text
        :disabled="zoomPercent >= maxZoom"
        :aria-label="t('form.canvasZoomIn')"
        @click="stepZoom(10)"
      >
        +
      </el-button>
      <span class="zoom-value">{{ zoomPercent }}%</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

const props = withDefaults(
  defineProps<{
    showHidden: boolean
    zoomPercent: number
    /** 嵌入 form-create 顶栏（画布上方工具条中间空白区） */
    inDesignerBar?: boolean
    minZoom?: number
    maxZoom?: number
  }>(),
  {
    inDesignerBar: false,
    minZoom: 50,
    maxZoom: 150,
  },
)

const emit = defineEmits<{
  'update:showHidden': [value: boolean]
  'update:zoomPercent': [value: number]
}>()

function stepZoom(delta: number) {
  const next = Math.min(props.maxZoom, Math.max(props.minZoom, props.zoomPercent + delta))
  emit('update:zoomPercent', next)
}
</script>

<style scoped lang="scss">
.form-designer-canvas-toolbar {
  display: inline-flex;
  align-items: center;
  gap: 16px;
  padding: 6px 12px;
  background: #fff;
  border: 1px solid #e4e7ed;
  border-radius: 6px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.06);
}

/* 嵌入 fc-designer 顶栏中间（布局/撤销 与 Preview/Clear 之间） */
.form-designer-canvas-toolbar--designer-bar {
  gap: 12px;
  padding: 4px 14px;
  height: 32px;
  background: #f5f7fa;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  box-shadow: none;
}

.toolbar-group {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.toolbar-label {
  font-size: 13px;
  color: #606266;
  white-space: nowrap;
}

.form-designer-canvas-toolbar--designer-bar .toolbar-zoom {
  min-width: 0;
}

.toolbar-zoom {
  min-width: 200px;
}

.form-designer-canvas-toolbar--designer-bar .zoom-slider {
  width: 88px;
}

.form-designer-canvas-toolbar--designer-bar .toolbar-label {
  font-size: 12px;
  color: #606266;
}

.zoom-slider {
  width: 120px;
  margin: 0 4px;
}

.zoom-value {
  font-size: 12px;
  color: #909399;
  min-width: 36px;
  text-align: right;
}
</style>
