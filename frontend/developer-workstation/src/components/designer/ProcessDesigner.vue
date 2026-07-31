<template>
  <div class="process-designer">
    <div class="designer-toolbar">
      <el-button-group>
        <el-button
          :disabled="!modelerReady"
          @click="handleZoomIn"
        >
          <el-icon><ZoomIn /></el-icon>
        </el-button>
        <el-button
          :disabled="!modelerReady"
          @click="handleZoomOut"
        >
          <el-icon><ZoomOut /></el-icon>
        </el-button>
        <el-button
          :disabled="!modelerReady"
          @click="handleFitViewport"
        >
          {{ t('process.fitCanvas') }}
        </el-button>
        <el-button
          :disabled="!modelerReady"
          @click="handleUndo"
        >
          <el-icon><RefreshLeft /></el-icon>
        </el-button>
        <el-button
          :disabled="!modelerReady"
          @click="handleRedo"
        >
          <el-icon><RefreshRight /></el-icon>
        </el-button>
      </el-button-group>
      <div class="auto-save-status">
        <span
          v-if="autoSaving"
          class="auto-saving"
        >
          <el-icon class="is-loading"><Loading /></el-icon>
          {{ t('process.autoSaving') }}
        </span>
        <span
          v-else-if="lastAutoSaveTime"
          class="auto-saved"
        >
          <el-icon><CircleCheck /></el-icon>
          {{ t('process.autoSaved') }} {{ formatAutoSaveTime(lastAutoSaveTime) }}
        </span>
      </div>
      <el-button-group>
        <el-button
          :disabled="!modelerReady"
          @click="handleValidate"
        >
          {{ t('process.validate') }}
        </el-button>
        <el-button
          :disabled="!modelerReady"
          @click="handleExportSVG"
        >
          {{ t('process.exportSVG') }}
        </el-button>
        <el-button
          :disabled="!modelerReady"
          @click="handleExportXML"
        >
          {{ t('process.exportXML') }}
        </el-button>
        <el-button
          :type="showDebugPanel ? 'primary' : ''"
          @click="showDebugPanel = !showDebugPanel"
        >
          <el-icon><Monitor /></el-icon> {{ t('process.debug') }}
        </el-button>
        <el-button
          type="primary"
          :loading="saving"
          :disabled="!modelerReady"
          @click="handleSave(false)"
        >
          {{ t('process.save') }}
        </el-button>
      </el-button-group>
    </div>
    
    <div class="designer-content">
      <!-- tabindex: the canvas must be focusable so diagram-js keyboard shortcuts
           (copy/paste/undo/delete) reach it — they are bound here, not on document. -->
      <div
        ref="canvasRef"
        class="bpmn-canvas"
        tabindex="0"
      />
      <div class="properties-panel-container">
        <NodePropertiesPanel 
          v-if="bpmnModelerRef" 
          :modeler="bpmnModelerRef" 
          :function-unit-id="functionUnitId" 
        />
      </div>
    </div>
    
    <!-- Debug Panel Drawer -->
    <el-drawer
      v-model="showDebugPanel"
      direction="btt"
      :size="debugDrawerExpanded ? '92%' : '50%'"
      :with-header="false"
      class="process-debug-drawer"
      destroy-on-close
    >
      <ProcessDebugPanel
        v-model:expanded="debugDrawerExpanded"
        :function-unit-id="functionUnitId"
        :get-bpmn-xml="exportCurrentBpmnXml"
        @close="showDebugPanel = false"
        @current-node-change="handleDebugNodeChange"
      />
    </el-drawer>

    <!-- Import XML Dialog -->
    <ProcessImportDialog
      v-model="showImportDialog"
      v-model:import-xml="importXml"
      @import="handleImportXML"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, nextTick } from 'vue'
import { useI18n } from 'vue-i18n'
import ProcessImportDialog from './process-designer/ProcessImportDialog.vue'
import { ZoomIn, ZoomOut, Monitor, RefreshLeft, RefreshRight, Loading, CircleCheck } from '@element-plus/icons-vue'
import { useFunctionUnitStore } from '@/stores/functionUnit'
import ProcessDebugPanel from '@/components/debug/ProcessDebugPanel.vue'
import NodePropertiesPanel from '@/components/designer/properties/NodePropertiesPanel.vue'
import { useProcessModeler } from '@/composables/processDesigner/useProcessModeler'
import { useProcessCanvasControls } from '@/composables/processDesigner/useProcessCanvasControls'
import { useProcessActions } from '@/composables/processDesigner/useProcessActions'

// bpmn-js CSS must be imported in JS for Vite bundling compatibility
import 'bpmn-js/dist/assets/diagram-js.css'
import 'bpmn-js/dist/assets/bpmn-js.css'
import 'bpmn-js/dist/assets/bpmn-font/css/bpmn-embedded.css'

const { t } = useI18n()
const props = defineProps<{ functionUnitId: number }>()

const store = useFunctionUnitStore()
const canvasRef = ref<HTMLElement>()
const showDebugPanel = ref(false)
const debugDrawerExpanded = ref(false)
const showImportDialog = ref(false)
const importXml = ref('')

// Modeler lifecycle: owns the bpmn-js instance and exposes a live accessor.
const {
  modelerReady,
  bpmnModelerRef,
  getModeler,
  initModeler,
  destroyModeler,
} = useProcessModeler({
  functionUnitId: props.functionUnitId,
  canvasRef,
  store,
  // Wrapper closure breaks the cycle: scheduleAutoSave is defined below in useProcessActions.
  onCommandStackChanged: () => scheduleAutoSave(),
  t,
})

// Canvas viewport controls + debug-node highlight marker.
const {
  handleZoomIn,
  handleZoomOut,
  handleFitViewport,
  handleUndo,
  handleRedo,
  handleDebugNodeChange,
} = useProcessCanvasControls({ getModeler })

// Validation / export / import / save / auto-save.
const {
  saving,
  autoSaving,
  lastAutoSaveTime,
  exportCurrentBpmnXml,
  handleValidate,
  handleExportSVG,
  handleExportXML,
  handleImportXML,
  handleSave,
  scheduleAutoSave,
  clearAutoSaveTimer,
  formatAutoSaveTime,
} = useProcessActions({
  functionUnitId: props.functionUnitId,
  getModeler,
  store,
  showImportDialog,
  importXml,
  t,
})

onMounted(async () => {
  await nextTick()
  await initModeler()
})

onUnmounted(() => {
  handleDebugNodeChange(null)
  // Clear auto-save timer
  clearAutoSaveTimer()

  destroyModeler()
})
</script>

<style lang="scss" scoped>
.process-designer {
  height: calc(100vh - 280px);
  min-height: 500px;
  display: flex;
  flex-direction: column;
}

.designer-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  padding: 10px;
  border-bottom: 1px solid #e6e6e6;
  background: #fff;
  flex-shrink: 0;
}

.auto-save-status {
  font-size: 14px;
  color: #909399;
  display: flex;
  align-items: center;
  min-width: 150px;
  
  .auto-saving {
    display: flex;
    align-items: center;
    gap: 6px;
    color: #409eff;
  }
  
  .auto-saved {
    display: flex;
    align-items: center;
    gap: 6px;
    color: #67c23a;
  }
}

.designer-content {
  flex: 1;
  display: flex;
  overflow: hidden;
  position: relative;
  min-height: 0;
}

.bpmn-canvas {
  flex: 1;
  min-width: 0;
  position: relative;
  background: #fafafa;

  // Focusable for keyboard shortcuts, but no ring on plain mouse clicks.
  &:focus {
    outline: none;
  }

  &:focus-visible {
    outline: 2px solid var(--el-color-primary);
    outline-offset: -2px;
  }

  :deep(.djs-container) {
    width: 100% !important;
    height: 100% !important;
  }
  
  :deep(.djs-palette) {
    background: #fff;
    border: 1px solid #e6e6e6;
    border-radius: 4px;
    
    .entry {
      &:hover {
        background: rgba(219, 0, 17, 0.1);
      }
    }
  }
  
  :deep(.djs-context-pad) {
    .entry {
      &:hover {
        background: rgba(219, 0, 17, 0.1);
      }
    }
  }
  
  :deep(.bjs-powered-by) {
    display: none;
  }
}

.properties-panel-container {
  width: 320px;
  border-left: 1px solid #e6e6e6;
  background: #fff;
  overflow-y: auto;
  flex-shrink: 0;
}

:deep(.process-debug-drawer.el-drawer) {
  .el-drawer__body {
    padding: 0;
    overflow: hidden;
    display: flex;
    flex-direction: column;
  }
}
</style>

<style>
/* Global styles for bpmn-js */

/* Palette styles */
.djs-palette {
  width: 48px !important;
  left: 10px !important;
  top: 10px !important;
  background: #fff !important;
  border: 1px solid #e6e6e6 !important;
  border-radius: 4px !important;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1) !important;
}

.djs-palette .entry {
  width: 100% !important;
  height: 40px !important;
  display: flex !important;
  align-items: center !important;
  justify-content: center !important;
}

.djs-palette .entry:hover {
  background: rgba(219, 0, 17, 0.1) !important;
}

.djs-palette .group {
  display: block !important;
}

.djs-palette .separator {
  margin: 5px 0 !important;
  border-bottom: 1px solid #e6e6e6 !important;
}

/* Context pad styles */
.djs-context-pad {
  display: flex !important;
  flex-direction: row !important;
  flex-wrap: wrap !important;
  width: auto !important;
  max-width: 150px !important;
  background: white !important;
  border-radius: 4px !important;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15) !important;
  padding: 4px !important;
}

.djs-context-pad .entry {
  display: inline-flex !important;
  align-items: center !important;
  justify-content: center !important;
  width: 28px !important;
  height: 28px !important;
  margin: 2px !important;
  border-radius: 3px !important;
  cursor: pointer !important;
}

.djs-context-pad .entry:hover {
  background: rgba(219, 0, 17, 0.1) !important;
}

/* Popup menu styles */
.djs-popup {
  background: white !important;
  border-radius: 4px !important;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.15) !important;
  max-height: 400px !important;
  overflow-y: auto !important;
}

.djs-popup .entry {
  padding: 8px 12px !important;
  cursor: pointer !important;
}

.djs-popup .entry:hover {
  background: rgba(219, 0, 17, 0.1) !important;
}

.djs-element.debug-current .djs-visual > :nth-child(1) {
  stroke: #f56c6c !important;
  stroke-width: 4px !important;
}
</style>
