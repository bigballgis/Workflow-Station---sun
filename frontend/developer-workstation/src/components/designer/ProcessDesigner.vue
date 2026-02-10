<template>
  <div class="process-designer">
    <div class="designer-toolbar">
      <el-button-group>
        <el-button @click="handleZoomIn" :disabled="!modelerReady">
          <el-icon><ZoomIn /></el-icon>
        </el-button>
        <el-button @click="handleZoomOut" :disabled="!modelerReady">
          <el-icon><ZoomOut /></el-icon>
        </el-button>
        <el-button @click="handleFitViewport" :disabled="!modelerReady">{{ t('process.fitCanvas') }}</el-button>
        <el-button @click="handleUndo" :disabled="!modelerReady">
          <el-icon><RefreshLeft /></el-icon>
        </el-button>
        <el-button @click="handleRedo" :disabled="!modelerReady">
          <el-icon><RefreshRight /></el-icon>
        </el-button>
      </el-button-group>
      <el-button-group>
        <el-button @click="handleValidate" :disabled="!modelerReady">{{ t('process.validate') }}</el-button>
        <el-button @click="handleExportSVG" :disabled="!modelerReady">{{ t('process.exportSVG') }}</el-button>
        <el-button @click="handleExportXML" :disabled="!modelerReady">{{ t('process.exportXML') }}</el-button>
        <el-button @click="showDebugPanel = !showDebugPanel" :type="showDebugPanel ? 'primary' : ''">
          <el-icon><Monitor /></el-icon> {{ t('process.debug') }}
        </el-button>
        <el-button type="primary" @click="handleSave" :loading="saving" :disabled="!modelerReady">
          {{ t('process.save') }}
        </el-button>
      </el-button-group>
    </div>
    
    <div class="designer-content">
      <div ref="canvasRef" class="bpmn-canvas"></div>
      <div class="properties-panel-container">
        <NodePropertiesPanel 
          v-if="bpmnModelerRef" 
          :modeler="bpmnModelerRef" 
          :function-unit-id="functionUnitId" 
        />
      </div>
    </div>
    
    <!-- Debug Panel Drawer -->
    <el-drawer v-model="showDebugPanel" :title="t('process.processDebug')" direction="btt" size="50%">
      <ProcessDebugPanel :function-unit-id="functionUnitId" />
    </el-drawer>

    <!-- Import XML Dialog -->
    <el-dialog v-model="showImportDialog" :title="t('process.importBpmnXml')" width="600px">
      <el-input v-model="importXml" type="textarea" :rows="15" :placeholder="t('process.pasteBpmnXml')" />
      <template #footer>
        <el-button @click="showImportDialog = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" @click="handleImportXML">{{ t('process.import') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, nextTick, shallowRef } from 'vue'
import { useI18n } from 'vue-i18n'
import { ZoomIn, ZoomOut, Monitor, RefreshLeft, RefreshRight } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { useFunctionUnitStore } from '@/stores/functionUnit'
import { functionUnitApi } from '@/api/functionUnit'
import ProcessDebugPanel from '@/components/debug/ProcessDebugPanel.vue'
import NodePropertiesPanel from '@/components/designer/properties/NodePropertiesPanel.vue'
import customModdleDescriptor from '@/utils/customModdle'
import { customTranslateModule } from '@/utils/customTranslate'

// @ts-ignore - bpmn-js types
import BpmnModeler from 'bpmn-js/lib/Modeler'

const { t } = useI18n()
const props = defineProps<{ functionUnitId: number }>()

const store = useFunctionUnitStore()
const canvasRef = ref<HTMLElement>()
const modelerReady = ref(false)
const bpmnModelerRef = shallowRef<any>(null)
const showDebugPanel = ref(false)
const showImportDialog = ref(false)
const importXml = ref('')
const saving = ref(false)
const currentZoom = ref(1)

let bpmnModeler: any = null

// Default empty BPMN diagram
const defaultBpmnXml = `<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
  xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
  xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI"
  xmlns:dc="http://www.omg.org/spec/DD/20100524/DC"
  xmlns:di="http://www.omg.org/spec/DD/20100524/DI"
  id="Definitions_1"
  targetNamespace="http://bpmn.io/schema/bpmn">
  <bpmn:process id="Process_1" isExecutable="true">
    <bpmn:startEvent id="StartEvent_1" name="Start">
      <bpmn:outgoing>Flow_1</bpmn:outgoing>
    </bpmn:startEvent>
    <bpmn:endEvent id="EndEvent_1" name="End">
      <bpmn:incoming>Flow_1</bpmn:incoming>
    </bpmn:endEvent>
    <bpmn:sequenceFlow id="Flow_1" sourceRef="StartEvent_1" targetRef="EndEvent_1" />
  </bpmn:process>
  <bpmndi:BPMNDiagram id="BPMNDiagram_1">
    <bpmndi:BPMNPlane id="BPMNPlane_1" bpmnElement="Process_1">
      <bpmndi:BPMNShape id="StartEvent_1_di" bpmnElement="StartEvent_1">
        <dc:Bounds x="180" y="160" width="36" height="36" />
        <bpmndi:BPMNLabel>
          <dc:Bounds x="187" y="203" width="22" height="14" />
        </bpmndi:BPMNLabel>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="EndEvent_1_di" bpmnElement="EndEvent_1">
        <dc:Bounds x="400" y="160" width="36" height="36" />
        <bpmndi:BPMNLabel>
          <dc:Bounds x="407" y="203" width="22" height="14" />
        </bpmndi:BPMNLabel>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNEdge id="Flow_1_di" bpmnElement="Flow_1">
        <di:waypoint x="216" y="178" />
        <di:waypoint x="400" y="178" />
      </bpmndi:BPMNEdge>
    </bpmndi:BPMNPlane>
  </bpmndi:BPMNDiagram>
</bpmn:definitions>`

async function initModeler() {
  if (!canvasRef.value) return
  
  try {
    bpmnModeler = new BpmnModeler({
      container: canvasRef.value,
      keyboard: {
        bindTo: document
      },
      moddleExtensions: {
        custom: customModdleDescriptor
      },
      additionalModules: [
        customTranslateModule
      ]
    })

    // Load existing process or default
    await store.fetchProcess(props.functionUnitId)
    const xml = store.process?.bpmnXml || defaultBpmnXml
    
    console.log('Loading BPMN XML:', xml)
    
    const result = await bpmnModeler.importXML(xml)
    console.log('Import result:', result)
    
    // Check if connections exist
    const elementRegistry = bpmnModeler.get('elementRegistry')
    const connections = elementRegistry.filter((element: any) => element.type === 'bpmn:SequenceFlow')
    console.log('Connections found:', connections.length, connections)
    
    bpmnModelerRef.value = bpmnModeler
    modelerReady.value = true
    
    // Fit to viewport after import
    const canvas = bpmnModeler.get('canvas')
    canvas.zoom('fit-viewport')
    
    // Listen for changes
    bpmnModeler.on('commandStack.changed', () => {
      // Mark as dirty
    })
    
  } catch (err: any) {
    console.error('Failed to initialize BPMN modeler:', err)
    ElMessage.error(t('process.initializationFailed') + ': ' + (err.message || t('common.error')))
  }
}

function handleZoomIn() {
  if (!bpmnModeler) return
  const canvas = bpmnModeler.get('canvas')
  currentZoom.value = Math.min(currentZoom.value + 0.1, 3)
  canvas.zoom(currentZoom.value)
}

function handleZoomOut() {
  if (!bpmnModeler) return
  const canvas = bpmnModeler.get('canvas')
  currentZoom.value = Math.max(currentZoom.value - 0.1, 0.3)
  canvas.zoom(currentZoom.value)
}

function handleFitViewport() {
  if (!bpmnModeler) return
  const canvas = bpmnModeler.get('canvas')
  canvas.zoom('fit-viewport')
  currentZoom.value = 1
}

function handleUndo() {
  if (!bpmnModeler) return
  const commandStack = bpmnModeler.get('commandStack')
  commandStack.undo()
}

function handleRedo() {
  if (!bpmnModeler) return
  const commandStack = bpmnModeler.get('commandStack')
  commandStack.redo()
}

async function handleValidate() {
  try {
    const res = await functionUnitApi.validateProcess?.(props.functionUnitId)
    if (res?.data?.valid) {
      ElMessage.success(t('process.validationPassed'))
    } else {
      const errors = res?.data?.errors || []
      const warnings = res?.data?.warnings || []
      if (errors.length) {
        ElMessage.error(`${t('process.validationError')}: ${errors.join(', ')}`)
      } else if (warnings.length) {
        ElMessage.warning(`${t('process.validationWarning')}: ${warnings.join(', ')}`)
      }
    }
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || t('process.validationError'))
  }
}

async function handleExportSVG() {
  if (!bpmnModeler) return
  try {
    const { svg } = await bpmnModeler.saveSVG()
    downloadFile(svg, 'process.svg', 'image/svg+xml')
    ElMessage.success(t('process.svgExportSuccess'))
  } catch (err) {
    ElMessage.error(t('process.svgExportFailed'))
  }
}

async function handleExportXML() {
  if (!bpmnModeler) return
  try {
    const { xml } = await bpmnModeler.saveXML({ format: true })
    downloadFile(xml, 'process.bpmn', 'application/xml')
    ElMessage.success(t('process.xmlExportSuccess'))
  } catch (err) {
    ElMessage.error(t('process.xmlExportFailed'))
  }
}

function downloadFile(content: string, filename: string, type: string) {
  const blob = new Blob([content], { type })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  a.click()
  URL.revokeObjectURL(url)
}

async function handleImportXML() {
  if (!bpmnModeler || !importXml.value.trim()) return
  try {
    await bpmnModeler.importXML(importXml.value)
    showImportDialog.value = false
    importXml.value = ''
    ElMessage.success(t('process.importSuccess'))
  } catch (err: any) {
    ElMessage.error(t('process.importFailed') + ': ' + (err.message || t('process.importFailed')))
  }
}

async function handleSave() {
  if (!bpmnModeler) return
  saving.value = true
  try {
    const { xml } = await bpmnModeler.saveXML({ format: true })
    await store.saveProcess(props.functionUnitId, { bpmnXml: xml })
    ElMessage.success(t('process.saveSuccess'))
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || t('process.saveFailed'))
  } finally {
    saving.value = false
  }
}

onMounted(async () => {
  await nextTick()
  await initModeler()
})

onUnmounted(() => {
  if (bpmnModeler) {
    bpmnModeler.destroy()
    bpmnModeler = null
  }
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
  padding: 10px;
  border-bottom: 1px solid #e6e6e6;
  background: #fff;
  flex-shrink: 0;
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
</style>

<style>
/* Global styles for bpmn-js */
@import 'bpmn-js/dist/assets/diagram-js.css';
@import 'bpmn-js/dist/assets/bpmn-js.css';
@import 'bpmn-js/dist/assets/bpmn-font/css/bpmn-embedded.css';

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
</style>
