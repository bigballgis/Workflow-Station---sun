<template>
  <div class="node-properties-panel">
    <div class="panel-header">
      <el-icon v-if="elementIcon">
        <component :is="elementIcon" />
      </el-icon>
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
      
      <!-- Sub-process properties (multi-instance config lives here) -->
      <SubProcessProperties
        v-else-if="isSubProcessElement"
        :key="selectedElement.id"
        :modeler="modeler"
        :element="selectedElement"
        :function-unit-id="functionUnitId"
      />

      <!-- Send task (email) — dedicated panel; avoids generic TaskProperties async/chunk issues -->
      <SendTaskProperties
        v-else-if="isSendTaskElement"
        :key="selectedElement.id"
        :modeler="modeler"
        :element="selectedElement"
        :function-unit-id="functionUnitId"
      />

      <!-- Other task properties (generic task, script task, etc.) -->
      <TaskProperties
        v-else-if="isTaskElement"
        :key="selectedElement.id"
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
      <div
        v-else
        class="basic-properties"
      >
        <el-form
          label-position="top"
          size="small"
        >
          <el-form-item label="ID">
            <el-input
              :model-value="basicProps.id"
              disabled
            />
          </el-form-item>
          <el-form-item :label="t('properties.name')">
            <el-input 
              :model-value="basicProps.name" 
              :placeholder="t('properties.namePlaceholder')"
              @update:model-value="updateName"
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
import { User, Setting, Share, Connection, Flag, Message } from '@element-plus/icons-vue'
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
  getElementType,
  getExtensionProperties
} from '@/utils/bpmnExtensions'
import ProcessProperties from './ProcessProperties.vue'
import TaskProperties from './TaskProperties.vue'
import UserTaskProperties from './UserTaskProperties.vue'
import ServiceTaskProperties from './ServiceTaskProperties.vue'
import SubProcessProperties from './SubProcessProperties.vue'
import SendTaskProperties from './SendTaskProperties.vue'
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

function getElementRefId(ref: any): string {
  return typeof ref === 'string' ? ref : (ref?.id || '')
}

function findFirstUserTaskInSubProcess(parentBo: any): any {
  const flowElements: any[] = parentBo?.flowElements || []
  const byId = new Map(flowElements.filter(fe => fe?.id).map(fe => [fe.id, fe]))
  const sequenceFlows = flowElements.filter(fe => fe?.$type === 'bpmn:SequenceFlow')
  const outgoingBySource = new Map<string, string[]>()

  for (const flow of sequenceFlows) {
    const sourceId = getElementRefId(flow.sourceRef)
    const targetId = getElementRefId(flow.targetRef)
    if (!sourceId || !targetId) continue
    const outgoing = outgoingBySource.get(sourceId) || []
    outgoing.push(targetId)
    outgoingBySource.set(sourceId, outgoing)
  }

  const startIds = flowElements
    .filter(fe => fe?.$type === 'bpmn:StartEvent')
    .map(fe => fe.id)
    .filter(Boolean)

  const queue = [...startIds]
  const visited = new Set<string>()
  while (queue.length > 0) {
    const id = queue.shift()
    if (!id || visited.has(id)) continue
    visited.add(id)

    for (const targetId of outgoingBySource.get(id) || []) {
      const target = byId.get(targetId)
      if (target?.$type === 'bpmn:UserTask') {
        return target
      }
      queue.push(targetId)
    }
  }

  return flowElements.find(fe => fe?.$type === 'bpmn:UserTask')
}

const isMultiInstanceSubTaskElement = computed(() => {
  if (!isUserTaskElement.value) return false
  const element = selectedElement.value as any
  const parentBo = element?.parent?.businessObject
  if (parentBo?.$type !== 'bpmn:SubProcess' || !parentBo.loopCharacteristics) {
    return false
  }
  const firstUserTask = findFirstUserTaskInSubProcess(parentBo)
  const currentId = element?.businessObject?.id || element?.id
  return !!firstUserTask && firstUserTask.id === currentId
})

const isServiceTaskElement = computed(() => 
  selectedElement.value && isServiceTask(selectedElement.value)
)

const isSubProcessElement = computed(() => {
  if (!selectedElement.value) return false
  const type = getElementType(selectedElement.value)
  return type === 'bpmn:SubProcess' || type === 'bpmn:AdHocSubProcess' || type === 'bpmn:Transaction'
})

const isSendTaskElement = computed(() => {
  if (!selectedElement.value) return false
  const type = getElementType(selectedElement.value)
  if (type === 'bpmn:SendTask') return true
  const ext = getExtensionProperties(selectedElement.value)
  return ext.sendMode === 'email' || Boolean(ext.connectionId)
})

const isTaskElement = computed(() => {
  if (!selectedElement.value) return false
  // Exclude UserTask, ServiceTask, SubProcess and SendTask — they have dedicated components
  if (
    isUserTask(selectedElement.value) ||
    isServiceTask(selectedElement.value) ||
    isSubProcessElement.value ||
    isSendTaskElement.value
  ) {
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
    if (isMultiInstanceSubTaskElement.value) {
      return t('properties.subTaskConfig')
    }
    return t('properties.userTaskConfig')
  }
  
  // Service task
  if (isServiceTaskElement.value) {
    return t('properties.serviceTaskConfig')
  }

  // Send task (email)
  if (isSendTaskElement.value) {
    return t('properties.sendTaskConfig')
  }
  
  // Other task types
  if (isTaskElement.value) {
    return t('properties.taskConfig')
  }

  if (isSubProcessElement.value) {
    return t('properties.subProcessConfig')
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
    'bpmn:SubProcess': t('properties.subProcessConfig'),
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

  // Send task (email)
  if (isSendTaskElement.value) {
    return Message
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
  min-height: 0;
  overflow-y: auto;
  padding: 12px;

  :deep(.send-task-properties),
  :deep(.task-properties),
  :deep(.user-task-properties),
  :deep(.service-task-properties) {
    min-height: 120px;
  }
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
