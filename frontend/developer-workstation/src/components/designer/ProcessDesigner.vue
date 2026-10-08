<template>
  <div class="process-designer">
    <div class="designer-toolbar">
      <el-button-group>
        <el-button
          data-read-only-allowed
          :disabled="!modelerReady"
          @click="handleZoomIn"
        >
          <el-icon><ZoomIn /></el-icon>
        </el-button>
        <el-button
          data-read-only-allowed
          :disabled="!modelerReady"
          @click="handleZoomOut"
        >
          <el-icon><ZoomOut /></el-icon>
        </el-button>
        <el-button
          data-read-only-allowed
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
          v-else-if="autoSaveBlocked"
          class="auto-save-blocked"
        >
          <el-icon><WarningFilled /></el-icon>
          {{
            diagramIsFallback
              ? t('process.fallbackDiagramAutoSaveBlockedShort')
              : t('process.emptyDiagramAutoSaveBlockedShort')
          }}
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
        <!-- Call relations open on demand rather than occupying the canvas: the
             chain matters when you go looking for it, not on every edit. -->
        <el-button
          v-if="showCallRelationButton"
          data-read-only-allowed
          class="call-relation-button"
          :disabled="!modelerReady"
          @click="showCallRelations = true"
        >
          <el-icon><Connection /></el-icon>
          {{ t('callRelation.buttonLabel') }}
          <el-icon class="call-relation-button__external"><TopRight /></el-icon>
          <span
            v-if="outdatedPinCount > 0"
            class="call-relation-button__badge"
            :title="t('callRelation.outdatedPinsHint', { count: outdatedPinCount })"
          >{{ outdatedPinCount }}</span>
        </el-button>
        <el-button
          data-read-only-allowed
          :disabled="!modelerReady"
          @click="handleValidate"
        >
          {{ t('process.validate') }}
        </el-button>
        <el-button
          data-read-only-allowed
          :disabled="!modelerReady"
          @click="handleExportSVG"
        >
          {{ t('process.exportSVG') }}
        </el-button>
        <el-button
          data-read-only-allowed
          :disabled="!modelerReady"
          @click="handleExportXML"
        >
          {{ t('process.exportXML') }}
        </el-button>
        <el-button
          data-read-only-allowed
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
      <div
        class="properties-panel-container"
        :class="{ collapsed: propertiesCollapsed }"
      >
        <!-- Collapses the panel to give the diagram the full width. -->
        <button
          type="button"
          class="properties-panel-toggle"
          :title="propertiesCollapsed ? t('process.expandProperties') : t('process.collapseProperties')"
          :aria-label="propertiesCollapsed ? t('process.expandProperties') : t('process.collapseProperties')"
          :aria-expanded="!propertiesCollapsed"
          @click="togglePropertiesPanel"
        >
          <el-icon>
            <ArrowLeft v-if="propertiesCollapsed" />
            <ArrowRight v-else />
          </el-icon>
        </button>
        <div
          v-show="!propertiesCollapsed"
          class="properties-panel-body"
        >
          <NodePropertiesPanel
            v-if="bpmnModelerRef"
            :modeler="bpmnModelerRef"
            :function-unit-id="functionUnitId"
          />
        </div>
      </div>
    </div>
    
    <!-- Function Unit call relations, opened from the toolbar -->
    <CallRelationDialog
      v-model="showCallRelations"
      :function-unit-id="functionUnitId"
      :modeler="bpmnModelerRef"
      :read-only="designerReadOnly"
      @repinned="loadCallRelations"
    />

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
import { ref, computed, watch, onMounted, onUnmounted, nextTick } from 'vue'
import { useI18n } from 'vue-i18n'
import ProcessImportDialog from './process-designer/ProcessImportDialog.vue'
import { ZoomIn, ZoomOut, Monitor, RefreshLeft, RefreshRight, Loading, CircleCheck, WarningFilled, Connection, TopRight, ArrowLeft, ArrowRight } from '@element-plus/icons-vue'
import { useFunctionUnitStore } from '@/stores/functionUnit'
import { functionUnitApi, type FunctionUnitCallRelations } from '@/api/functionUnit'
import { isFunctionUnitReadOnly } from '@/utils/permission'
import ProcessDebugPanel from '@/components/debug/ProcessDebugPanel.vue'
import NodePropertiesPanel from '@/components/designer/properties/NodePropertiesPanel.vue'
import CallRelationDialog from '@/components/designer/CallRelationDialog.vue'
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

/** Bumped on every diagram change so call-step-dependent UI re-evaluates. */
const diagramRevision = ref(0)
const debugDrawerExpanded = ref(false)
const showImportDialog = ref(false)
const importXml = ref('')

// Modeler lifecycle: owns the bpmn-js instance and exposes a live accessor.
const {
  modelerReady,
  bpmnModelerRef,
  diagramIsFallback,
  getModeler,
  initModeler,
  destroyModeler,
} = useProcessModeler({
  functionUnitId: props.functionUnitId,
  canvasRef,
  store,
  // Wrapper closure breaks the cycle: scheduleAutoSave is defined below in useProcessActions.
  onCommandStackChanged: () => {
    scheduleAutoSave()
    // Also re-evaluate whether the diagram now has a call step, so the call
    // relations button appears or disappears as one is added or deleted.
    diagramRevision.value++
  },
  t,
})

// Right-hand properties panel: collapsible, remembered per browser (a viewing preference).
const PROPERTIES_COLLAPSED_KEY = 'dw.processDesigner.propertiesCollapsed'
function readPropertiesCollapsed(): boolean {
  try {
    return localStorage.getItem(PROPERTIES_COLLAPSED_KEY) === '1'
  } catch {
    return false
  }
}
const propertiesCollapsed = ref(readPropertiesCollapsed())
function togglePropertiesPanel() {
  propertiesCollapsed.value = !propertiesCollapsed.value
  try {
    localStorage.setItem(PROPERTIES_COLLAPSED_KEY, propertiesCollapsed.value ? '1' : '0')
  } catch {
    // Storage unavailable (private window etc.): the toggle still works for this visit.
  }
  // The canvas changed width; bpmn-js caches its size and would otherwise draw distorted.
  nextTick(() => {
    try {
      getModeler()?.get('canvas').resized()
    } catch {
      // Modeler not ready yet; it measures itself when it mounts.
    }
  })
}

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
  autoSaveBlocked,
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
  diagramIsFallback,
  t,
})

const showCallRelations = ref(false)

/**
 * Whether this diagram calls another Function Unit, which decides if the call
 * relations button is offered at all.
 *
 * Read from the live canvas rather than the saved XML so the button appears the
 * moment a call step is added, without waiting for a save. Recomputed on every
 * diagram change via {@link diagramRevision}.
 */
const hasCallActivities = computed(() => {
  // Touch both so this re-evaluates when the modeler finishes loading and on
  // every subsequent diagram edit — neither is reachable through getModeler().
  void diagramRevision.value
  if (!modelerReady.value) return false
  const modeler = getModeler()
  if (!modeler) return false
  try {
    return modeler.get('elementRegistry')
      .filter((el: any) => el.businessObject?.$type === 'bpmn:CallActivity')
      .length > 0
  } catch {
    return false
  }
})

watch(() => store.process?.bpmnXml, () => {
  diagramRevision.value++
  void loadCallRelations()
})

/**
 * The call dialog is teleported to <body>, outside the editor's read-only
 * interaction blocker, so its one editing action has to be guarded explicitly.
 */
const designerReadOnly = computed(() => isFunctionUnitReadOnly(store.current))

/**
 * This unit's call relations, in both directions.
 *
 * Loaded for every unit, not only ones whose diagram has a call step: a unit that
 * is only ever *called* has no call step of its own, yet "who calls me" is exactly
 * what its designer needs before changing it. One request for this unit alone.
 */
const callRelations = ref<FunctionUnitCallRelations | null>(null)

async function loadCallRelations() {
  try {
    const res = await functionUnitApi.getCallRelations(props.functionUnitId)
    callRelations.value = (res as { data?: FunctionUnitCallRelations })?.data ?? null
  } catch {
    // Informational only; the designer works without it.
    callRelations.value = null
  }
}

/** Callers of this unit, from the server (a diagram cannot show them). */
const calledByCount = computed(() => callRelations.value?.calledBy?.length ?? 0)

/**
 * Whether to offer the call relations button: this unit calls something or is
 * called by something. The live canvas check keeps it responsive while a call step
 * is being added, before that edit has been saved and re-derived on the server.
 */
const showCallRelationButton = computed(() =>
  hasCallActivities.value || calledByCount.value > 0
)

/** Call steps pinned to a version older than their callee now has. */
const outdatedPinCount = computed(() =>
  (callRelations.value?.calls ?? []).filter((c) => c.newerVersionAvailable).length
)

watch(modelerReady, (ready) => {
  if (ready) void loadCallRelations()
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
/* Count of call steps pinned to a superseded version of their callee. */
.call-relation-button__badge {
  margin-left: 6px;
  min-width: 16px;
  height: 16px;
  padding: 0 4px;
  border-radius: 8px;
  background: var(--el-color-warning);
  color: #fff;
  font-size: 11px;
  line-height: 16px;
  text-align: center;
}

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

  .auto-save-blocked {
    display: flex;
    align-items: center;
    gap: 6px;
    color: #e6a23c;
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
  position: relative;
  display: flex;
  flex-direction: column;
  width: 320px;
  border-left: 1px solid #e6e6e6;
  background: #fff;
  flex-shrink: 0;
  transition: width 0.2s ease;

  &.collapsed {
    width: 12px;
  }
}

.properties-panel-body {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
}

/* A tab on the panel's left edge, half over the canvas so it stays reachable when collapsed. */
.properties-panel-toggle {
  position: absolute;
  top: 50%;
  left: -14px;
  z-index: 5;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 14px;
  height: 48px;
  padding: 0;
  transform: translateY(-50%);
  border: 1px solid #e6e6e6;
  border-right: none;
  border-radius: 6px 0 0 6px;
  background: #fff;
  color: var(--el-text-color-secondary);
  cursor: pointer;

  &:hover {
    color: var(--el-color-primary);
  }
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
