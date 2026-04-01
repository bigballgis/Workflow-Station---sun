<template>
  <div class="decision-designer">
    <div class="designer-toolbar">
      <el-button-group>
        <el-button @click="handleSwitchView" :disabled="!modelerReady">
          <el-icon><Switch /></el-icon>
          {{ currentView === 'drd' ? t('decision.tableView') : t('decision.drdView') }}
        </el-button>
        <el-button @click="handleZoomIn" :disabled="!modelerReady">
          <el-icon><ZoomIn /></el-icon>
        </el-button>
        <el-button @click="handleZoomOut" :disabled="!modelerReady">
          <el-icon><ZoomOut /></el-icon>
        </el-button>
      </el-button-group>
      <el-button-group>
        <el-button @click="handleValidate" :disabled="!modelerReady" :loading="validating">
          {{ t('decision.validate') }}
        </el-button>
        <el-button @click="handleExportXml" :disabled="!modelerReady">
          {{ t('decision.exportXml') }}
        </el-button>
        <el-button type="primary" @click="handleSave" :loading="saving" :disabled="!modelerReady">
          {{ t('decision.save') }}
        </el-button>
      </el-button-group>
    </div>

    <div ref="canvasRef" class="dmn-canvas"></div>

    <!-- Validation results -->
    <el-dialog v-model="showValidation" :title="t('decision.validate')" width="500px">
      <el-result v-if="validationResult?.valid" icon="success" :title="t('decision.validationPassed')" />
      <template v-else-if="validationResult">
        <el-alert v-for="(err, i) in validationResult.errors" :key="'e' + i" type="error" :title="err" :closable="false" show-icon class="validation-item" />
        <el-alert v-for="(warn, i) in validationResult.warnings" :key="'w' + i" type="warning" :title="warn" :closable="false" show-icon class="validation-item" />
      </template>
      <template #footer>
        <el-button @click="showValidation = false">{{ t('common.close') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, shallowRef } from 'vue'
import { useI18n } from 'vue-i18n'
import { ZoomIn, ZoomOut, Switch } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { decisionApi } from '@/api/decision'
import type { DecisionDefinition, DecisionValidationResult } from '@/api/decision'

// @ts-ignore - dmn-js types
import DmnJS from 'dmn-js/lib/Modeler'

// dmn-js CSS imports
import 'dmn-js/dist/assets/diagram-js.css'
import 'dmn-js/dist/assets/dmn-js-shared.css'
import 'dmn-js/dist/assets/dmn-js-drd.css'
import 'dmn-js/dist/assets/dmn-js-decision-table.css'
import 'dmn-js/dist/assets/dmn-js-literal-expression.css'
import 'dmn-js/dist/assets/dmn-font/css/dmn.css'

const { t } = useI18n()
const emit = defineEmits<{
  saved: []
}>()

const props = defineProps<{
  functionUnitId: number
  decisionId: number
}>()

const canvasRef = ref<HTMLElement>()
const modelerReady = ref(false)
const dmnModeler = shallowRef<InstanceType<typeof DmnJS> | null>(null)
const saving = ref(false)
const validating = ref(false)
const currentView = ref<'drd' | 'table'>('drd')
const showValidation = ref(false)
const validationResult = ref<DecisionValidationResult | null>(null)
const decisionData = ref<DecisionDefinition | null>(null)

const DEFAULT_DMN_XML = `<?xml version="1.0" encoding="UTF-8"?>
<definitions xmlns="https://www.omg.org/spec/DMN/20191111/MODEL/"
  xmlns:dmndi="https://www.omg.org/spec/DMN/20191111/DMNDI/"
  xmlns:dc="http://www.omg.org/spec/DMN/20180521/DC/"
  id="definitions" name="definitions" namespace="http://camunda.org/schema/1.0/dmn">
  <decision id="decision_1" name="Decision 1">
    <decisionTable id="decisionTable_1" hitPolicy="FIRST">
      <input id="input_1" label="Input">
        <inputExpression id="inputExpression_1" typeRef="string"><text></text></inputExpression>
      </input>
      <output id="output_1" label="Output" typeRef="string" />
    </decisionTable>
  </decision>
  <dmndi:DMNDI>
    <dmndi:DMNDiagram>
      <dmndi:DMNShape dmnElementRef="decision_1">
        <dc:Bounds height="80" width="180" x="160" y="100" />
      </dmndi:DMNShape>
    </dmndi:DMNDiagram>
  </dmndi:DMNDI>
</definitions>`

async function initModeler() {
  if (!canvasRef.value) return

  const modeler = new DmnJS({ container: canvasRef.value })
  dmnModeler.value = modeler

  try {
    // Load decision data from API
    const response = await decisionApi.getById(props.functionUnitId, props.decisionId) as unknown as { data: DecisionDefinition }
    decisionData.value = response.data
    const xml = response.data?.dmnXml || DEFAULT_DMN_XML
    await modeler.importXML(xml)
    modelerReady.value = true
  } catch (err) {
    // If loading fails, use default XML
    try {
      await modeler.importXML(DEFAULT_DMN_XML)
      modelerReady.value = true
    } catch (importErr) {
      ElMessage.error(t('decision.saveFailed'))
    }
  }
}

function handleSwitchView() {
  if (!dmnModeler.value) return
  const views = dmnModeler.value.getViews()
  if (!views || views.length === 0) return

  if (currentView.value === 'drd') {
    // Switch to decision table view
    const tableView = views.find((v: Record<string, string>) => v.type === 'decisionTable')
    if (tableView) {
      dmnModeler.value.open(tableView)
      currentView.value = 'table'
    }
  } else {
    // Switch to DRD view
    const drdView = views.find((v: Record<string, string>) => v.type === 'drd')
    if (drdView) {
      dmnModeler.value.open(drdView)
      currentView.value = 'drd'
    }
  }
}

function handleZoomIn() {
  if (!dmnModeler.value) return
  const activeViewer = dmnModeler.value.getActiveViewer()
  if (activeViewer) {
    const canvas = activeViewer.get('canvas')
    if (canvas) canvas.zoom(canvas.zoom() * 1.1)
  }
}

function handleZoomOut() {
  if (!dmnModeler.value) return
  const activeViewer = dmnModeler.value.getActiveViewer()
  if (activeViewer) {
    const canvas = activeViewer.get('canvas')
    if (canvas) canvas.zoom(canvas.zoom() * 0.9)
  }
}

async function handleSave() {
  if (!dmnModeler.value || !decisionData.value) return
  saving.value = true
  try {
    const { xml } = await dmnModeler.value.saveXML({ format: true })
    await decisionApi.update(props.functionUnitId, props.decisionId, {
      decisionKey: decisionData.value.decisionKey,
      decisionName: decisionData.value.decisionName,
      dmnXml: xml,
      hitPolicy: decisionData.value.hitPolicy,
      description: decisionData.value.description
    })
    ElMessage.success(t('decision.saveSuccess'))
    emit('saved')
  } catch (err) {
    ElMessage.error(t('decision.saveFailed'))
  } finally {
    saving.value = false
  }
}

async function handleValidate() {
  validating.value = true
  try {
    const response = await decisionApi.validate(props.functionUnitId, props.decisionId) as unknown as { data: DecisionValidationResult }
    validationResult.value = response.data
    showValidation.value = true
    if (response.data.valid) {
      ElMessage.success(t('decision.validationPassed'))
    }
  } catch (err) {
    ElMessage.error(t('decision.validationFailed'))
  } finally {
    validating.value = false
  }
}

async function handleExportXml() {
  if (!dmnModeler.value) return
  try {
    const { xml } = await dmnModeler.value.saveXML({ format: true })
    const blob = new Blob([xml], { type: 'application/xml' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `${decisionData.value?.decisionKey || 'decision'}.dmn`
    a.click()
    URL.revokeObjectURL(url)
  } catch (err) {
    ElMessage.error(t('decision.saveFailed'))
  }
}

onMounted(() => {
  initModeler()
})

onUnmounted(() => {
  if (dmnModeler.value) {
    dmnModeler.value.destroy()
    dmnModeler.value = null
  }
})
</script>

<style scoped lang="scss">
.decision-designer {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.designer-toolbar {
  display: flex;
  justify-content: space-between;
  padding: 8px 12px;
  border-bottom: 1px solid var(--el-border-color-light);
  background: var(--el-bg-color);
}

.dmn-canvas {
  flex: 1;
  min-height: 400px;
}

.validation-item {
  margin-bottom: 8px;
}
</style>
