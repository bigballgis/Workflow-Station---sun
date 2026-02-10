<template>
  <div class="node-properties-panel">
    <div class="panel-header">
      <el-icon v-if="elementIcon"><component :is="elementIcon" /></el-icon>
      <span class="panel-title">{{ panelTitle }}</span>
    </div>
    
    <div class="panel-content">
      <!-- Process properties -->
      <ProcessProperties 
        v-if="!selectedElement || isProcessElement"
        :modeler="modeler"
        :element="processElement"
      />
      
      <!-- User task properties -->
      <UserTaskProperties
        v-else-if="isUserTaskElement"
        :modeler="modeler"
        :element="selectedElement"
        :function-unit-id="functionUnitId"
      />
      
      <!-- Service task properties -->
      <ServiceTaskProperties
        v-else-if="isServiceTaskElement"
        :modeler="modeler"
        :element="selectedElement"
        :function-unit-id="functionUnitId"
      />
      
      <!-- Other task properties (generic task, script task, etc.) -->
      <TaskProperties
        v-else-if="isTaskElement"
        :modeler="modeler"
        :element="selectedElement"
        :function-unit-id="functionUnitId"
      />
      
      <!-- Gateway properties -->
      <GatewayProperties
        v-else-if="isGatewayElement"
        :modeler="modeler"
        :element="selectedElement"
      />
      
      <!-- Sequence flow properties -->
      <SequenceFlowProperties
        v-else-if="isSequenceFlowElement"
        :modeler="modeler"
        :element="selectedElement"
      />
      
      <!-- Event properties -->
      <EventProperties
        v-else-if="isEventElement"
        :modeler="modeler"
        :element="selectedElement"
        :function-unit-id="functionUnitId"
      />
      
      <!-- Other element basic properties -->
      <div v-else class="basic-properties">
        <el-form label-position="top" size="small">
          <el-form-item label="ID">
            <el-input :model-value="basicProps.id" disabled />
          </el-form-item>
          <el-form-item :label="t('properties.name')">
            <el-input 
              :model-value="basicProps.name" 
              @update:model-value="updateName"
              :placeholder="t('properties.namePlaceholder')"
            />
          </el-form-item>
        </el-form>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, shallowRef, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { User, Setting, Share, Connection, Flag } from '@element-plus/icons-vue'
import type { BpmnElement, BpmnModeler } from '@/types/bpmn'
import {
  isUserTask,
  isServiceTask,
  isGateway,
  isSequenceFlow,
  isEvent,
  isProcess,
  isTask,
  getBasicProperties,
  setBasicProperties,
  getElementType
} from '@/utils/bpmnExtensions'
import ProcessProperties from './ProcessProperties.vue'
import TaskProperties from './TaskProperties.vue'
import UserTaskProperties from './UserTaskProperties.vue'
import ServiceTaskProperties from './ServiceTaskProperties.vue'
import GatewayProperties from './GatewayProperties.vue'
import SequenceFlowProperties from './SequenceFlowProperties.vue'
import EventProperties from './EventProperties.vue'

const { t } = useI18n()

const props = defineProps<{
  modeler: BpmnModeler
  functionUnitId: number
}>()

const selectedElement = shallowRef<BpmnElement | null>(null)
const processElement = shallowRef<BpmnElement | null>(null)

// Debug: output selected element type
watch(selectedElement, (el) => {
  if (el) {
    console.log('[NodePropertiesPanel] Selected element type:', getElementType(el), el)
  }
})

// Computed properties
const isProcessElement = computed(() => 
  selectedElement.value && isProcess(selectedElement.value)
)

const isUserTaskElement = computed(() => 
  selectedElement.value && isUserTask(selectedElement.value)
)

const isServiceTaskElement = computed(() => 
  selectedElement.value && isServiceTask(selectedElement.value)
)

const isTaskElement = computed(() => {
  if (!selectedElement.value) return false
  // Exclude UserTask and ServiceTask, they have dedicated components
  if (isUserTask(selectedElement.value) || isServiceTask(selectedElement.value)) {
    return false
  }
  const type = getElementType(selectedElement.value)
  const id = selectedElement.value.id || ''
  
  // Support other task types
  return type === 'bpmn:Task' || 
         type === 'bpmn:Activity' ||
         type === 'bpmn:ScriptTask' ||
         type === 'bpmn:ManualTask' ||
         type === 'bpmn:SendTask' ||
         type === 'bpmn:ReceiveTask' ||
         type === 'bpmn:BusinessRuleTask' ||
         id.startsWith('Activity_') ||
         type.includes('Task')
})

const isGatewayElement = computed(() => 
  selectedElement.value && isGateway(selectedElement.value)
)

const isSequenceFlowElement = computed(() => 
  selectedElement.value && isSequenceFlow(selectedElement.value)
)

const isEventElement = computed(() => 
  selectedElement.value && isEvent(selectedElement.value)
)

const basicProps = computed(() => 
  selectedElement.value ? getBasicProperties(selectedElement.value) : { id: '', name: '' }
)

const panelTitle = computed(() => {
  if (!selectedElement.value) return t('properties.processProperties')
  const type = selectedElement.value.businessObject?.$type || ''
  const id = selectedElement.value.id || ''
  
  // User task
  if (isUserTaskElement.value) {
    return t('properties.userTaskConfig')
  }
  
  // Service task
  if (isServiceTaskElement.value) {
    return t('properties.serviceTaskConfig')
  }
  
  // Other task types
  if (isTaskElement.value) {
    return t('properties.taskConfig')
  }
  
  const typeMap: Record<string, string> = {
    'bpmn:Process': t('properties.processProperties'),
    'bpmn:ExclusiveGateway': t('properties.gatewayTypeExclusive'),
    'bpmn:ParallelGateway': t('properties.gatewayTypeParallel'),
    'bpmn:InclusiveGateway': t('properties.gatewayTypeInclusive'),
    'bpmn:EventBasedGateway': t('properties.gatewayTypeEventBased'),
    'bpmn:ComplexGateway': t('properties.gatewayTypeComplex'),
    'bpmn:SequenceFlow': t('properties.flowName'),
    'bpmn:StartEvent': t('properties.eventTypeStartEvent'),
    'bpmn:EndEvent': t('properties.eventTypeEndEvent'),
    'bpmn:IntermediateCatchEvent': t('properties.eventTypeIntermediateCatchEvent'),
    'bpmn:IntermediateThrowEvent': t('properties.eventTypeIntermediateThrowEvent'),
    'bpmn:BoundaryEvent': t('properties.eventTypeBoundaryEvent'),
    'bpmn:SubProcess': t('properties.elementProperties'),
    'bpmn:CallActivity': t('properties.elementProperties')
  }
  
  return typeMap[type] || t('properties.elementProperties')
})

const elementIcon = computed(() => {
  if (!selectedElement.value) return Setting
  const type = selectedElement.value.businessObject?.$type || ''
  
  // User task
  if (isUserTaskElement.value) {
    return User
  }
  
  // Service task
  if (isServiceTaskElement.value) {
    return Setting
  }
  
  // Other task types
  if (isTaskElement.value) {
    return User
  }
  
  const iconMap: Record<string, any> = {
    'bpmn:Process': Setting,
    'bpmn:SubProcess': Setting,
    'bpmn:CallActivity': Setting,
    'bpmn:ExclusiveGateway': Share,
    'bpmn:ParallelGateway': Share,
    'bpmn:InclusiveGateway': Share,
    'bpmn:SequenceFlow': Connection,
    'bpmn:StartEvent': Flag,
    'bpmn:EndEvent': Flag,
    'bpmn:IntermediateCatchEvent': Flag,
    'bpmn:IntermediateThrowEvent': Flag,
    'bpmn:BoundaryEvent': Flag
  }
  
  return iconMap[type] || Setting
})

function updateName(name: string) {
  if (selectedElement.value && props.modeler) {
    setBasicProperties(props.modeler, selectedElement.value, { name })
  }
}

function handleSelectionChanged(e: any) {
  const selection = e.newSelection || []
  if (selection.length === 1) {
    selectedElement.value = selection[0]
  } else if (selection.length === 0) {
    selectedElement.value = null
  } else {
    // Multi-select shows process properties
    selectedElement.value = null
  }
}

function findProcessElement() {
  if (!props.modeler) return
  const elementRegistry = props.modeler.get('elementRegistry')
  const elements = elementRegistry.getAll()
  const process = elements.find((el: any) => el.businessObject?.$type === 'bpmn:Process')
  processElement.value = process || null
}

onMounted(() => {
  if (props.modeler) {
    props.modeler.on('selection.changed', handleSelectionChanged)
    findProcessElement()
    
    // Listen for import complete event
    props.modeler.on('import.done', findProcessElement)
  }
})

onUnmounted(() => {
  if (props.modeler) {
    props.modeler.off('selection.changed', handleSelectionChanged)
    props.modeler.off('import.done', findProcessElement)
  }
})
</script>

<style lang="scss" scoped>
.node-properties-panel {
  height: 100%;
  display: flex;
  flex-direction: column;
  background: #fff;
}

.panel-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 16px;
  border-bottom: 1px solid #e6e6e6;
  background: #f5f7fa;
  
  .el-icon {
    font-size: 18px;
    color: #DB0011;
  }
  
  .panel-title {
    font-size: 14px;
    font-weight: 600;
    color: #303133;
  }
}

.panel-content {
  flex: 1;
  overflow-y: auto;
  padding: 12px;
}

.basic-properties {
  :deep(.el-form-item) {
    margin-bottom: 12px;
    
    .el-form-item__label {
      font-size: 12px;
      color: #606266;
      padding-bottom: 4px;
    }
  }
}
</style>
