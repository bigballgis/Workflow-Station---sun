<template>
  <div class="node-properties-panel">
    <div class="panel-header">
      <el-icon v-if="elementIcon"><component :is="elementIcon" /></el-icon>
      <span class="panel-title">{{ panelTitle }}</span>
    </div>
    
    <div class="panel-content">
      <!-- 流程属性 -->
      <ProcessProperties 
        v-if="!selectedElement || isProcessElement"
        :modeler="modeler"
        :element="processElement"
      />
      
      <!-- 用户任务属性 -->
      <UserTaskProperties
        v-else-if="isUserTaskElement"
        :modeler="modeler"
        :element="selectedElement"
        :function-unit-id="functionUnitId"
      />
      
      <!-- 服务任务属性 -->
      <ServiceTaskProperties
        v-else-if="isServiceTaskElement"
        :modeler="modeler"
        :element="selectedElement"
        :function-unit-id="functionUnitId"
      />
      
      <!-- 其他任务属性（通用任务、脚本任务等） -->
      <TaskProperties
        v-else-if="isTaskElement"
        :modeler="modeler"
        :element="selectedElement"
        :function-unit-id="functionUnitId"
      />
      
      <!-- 网关属性 -->
      <GatewayProperties
        v-else-if="isGatewayElement"
        :modeler="modeler"
        :element="selectedElement"
      />
      
      <!-- 连接线属性 -->
      <SequenceFlowProperties
        v-else-if="isSequenceFlowElement"
        :modeler="modeler"
        :element="selectedElement"
      />
      
      <!-- 事件属性 -->
      <EventProperties
        v-else-if="isEventElement"
        :modeler="modeler"
        :element="selectedElement"
        :function-unit-id="functionUnitId"
      />
      
      <!-- 其他元素的基本属性 -->
      <div v-else class="basic-properties">
        <el-form label-position="top" size="small">
          <el-form-item label="ID">
            <el-input :model-value="basicProps.id" disabled />
          </el-form-item>
          <el-form-item label="名称">
            <el-input 
              :model-value="basicProps.name" 
              @update:model-value="updateName"
              placeholder="输入名称"
            />
          </el-form-item>
        </el-form>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, shallowRef, watch } from 'vue'
import { User, Setting, Share, Connection, Flag } from '@element-plus/icons-vue'
import type { BpmnElement, BpmnModeler } from '@/types/bpmn'
import {
  isUserTask,
  isServiceTask,
  isGateway,
  isSequenceFlow,
  isEvent,
  isProcess,
  // isTask,
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

const props = defineProps<{
  modeler: BpmnModeler
  functionUnitId: number
}>()

const selectedElement = shallowRef<BpmnElement | null>(null)
const processElement = shallowRef<BpmnElement | null>(null)

// 调试：输出当前选中元素的类型
watch(selectedElement, (el) => {
  if (el) {
    console.log('[NodePropertiesPanel] Selected element type:', getElementType(el), el)
  }
})

// 计算属性
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
  // 排除 UserTask 和 ServiceTask，它们有专门的组件
  if (isUserTask(selectedElement.value) || isServiceTask(selectedElement.value)) {
    return false
  }
  const type = getElementType(selectedElement.value)
  const id = selectedElement.value.id || ''
  
  // 支持其他任务类型
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
  if (!selectedElement.value) return '流程属性'
  const type = selectedElement.value.businessObject?.$type || ''
  // const id = selectedElement.value.id || ''
  
  // 用户任务
  if (isUserTaskElement.value) {
    return '用户任务配置'
  }
  
  // 服务任务
  if (isServiceTaskElement.value) {
    return '服务任务配置'
  }
  
  // 其他任务类型
  if (isTaskElement.value) {
    return '任务配置'
  }
  
  const typeMap: Record<string, string> = {
    'bpmn:Process': '流程属性',
    'bpmn:ExclusiveGateway': '排他网关',
    'bpmn:ParallelGateway': '并行网关',
    'bpmn:InclusiveGateway': '包容网关',
    'bpmn:EventBasedGateway': '事件网关',
    'bpmn:ComplexGateway': '复杂网关',
    'bpmn:SequenceFlow': '连接线',
    'bpmn:StartEvent': '开始事件',
    'bpmn:EndEvent': '结束事件',
    'bpmn:IntermediateCatchEvent': '中间捕获事件',
    'bpmn:IntermediateThrowEvent': '中间抛出事件',
    'bpmn:BoundaryEvent': '边界事件',
    'bpmn:SubProcess': '子流程',
    'bpmn:CallActivity': '调用活动'
  }
  
  return typeMap[type] || '元素属性'
})

const elementIcon = computed(() => {
  if (!selectedElement.value) return Setting
  const type = selectedElement.value.businessObject?.$type || ''
  
  // 用户任务
  if (isUserTaskElement.value) {
    return User
  }
  
  // 服务任务
  if (isServiceTaskElement.value) {
    return Setting
  }
  
  // 其他任务类型
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
    // 多选时显示流程属性
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
    
    // 监听导入完成事件
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
