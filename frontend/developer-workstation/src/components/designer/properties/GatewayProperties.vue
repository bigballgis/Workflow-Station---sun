<template>
  <div class="gateway-properties">
    <el-collapse v-model="activeGroups">
      <!-- 基本信息 -->
      <el-collapse-item title="基本信息" name="basic">
        <el-form label-position="top" size="small">
          <el-form-item label="网关ID">
            <el-input :model-value="basicProps.id" disabled />
          </el-form-item>
          <el-form-item label="网关类型">
            <el-input :model-value="gatewayTypeLabel" disabled />
          </el-form-item>
          <el-form-item label="网关名称">
            <el-input v-model="gatewayName" @change="updateBasicProp('name', gatewayName)" placeholder="网关名称" />
          </el-form-item>
        </el-form>
      </el-collapse-item>
      
      <!-- 排他网关配置 -->
      <el-collapse-item v-if="isExclusive" title="分支配置" name="branch">
        <el-form label-position="top" size="small">
          <el-form-item label="默认分支">
            <el-select v-model="defaultFlow" @change="updateDefaultFlow" placeholder="选择默认分支" clearable>
              <el-option 
                v-for="flow in outgoingFlows" 
                :key="flow.id" 
                :label="flow.name || flow.id" 
                :value="flow.id" 
              />
            </el-select>
            <div class="form-tip">当所有条件都不满足时走默认分支</div>
          </el-form-item>
          
          <div class="branch-list">
            <div class="branch-title">出口分支</div>
            <div v-for="flow in outgoingFlows" :key="flow.id" class="branch-item">
              <div class="branch-name">
                <el-tag v-if="flow.id === defaultFlow" type="success" size="small">默认</el-tag>
                {{ flow.name || flow.id }}
              </div>
              <div class="branch-condition">
                {{ flow.conditionExpression || '无条件' }}
              </div>
            </div>
            <el-empty v-if="!outgoingFlows.length" description="暂无出口分支" :image-size="60" />
          </div>
        </el-form>
      </el-collapse-item>
      
      <!-- 并行网关说明 -->
      <el-collapse-item v-if="isParallel" title="网关说明" name="info">
        <div class="gateway-info">
          <el-alert type="info" :closable="false">
            <template #title>并行网关</template>
            <p>并行网关用于创建并行执行路径。</p>
            <ul>
              <li>分支：所有出口分支将同时执行</li>
              <li>汇聚：等待所有入口分支完成后继续</li>
            </ul>
          </el-alert>
        </div>
      </el-collapse-item>
      
      <!-- 包容网关说明 -->
      <el-collapse-item v-if="isInclusive" title="网关说明" name="info">
        <div class="gateway-info">
          <el-alert type="info" :closable="false">
            <template #title>包容网关</template>
            <p>包容网关根据条件选择一个或多个分支执行。</p>
            <ul>
              <li>分支：满足条件的分支都会执行</li>
              <li>汇聚：等待所有激活的分支完成</li>
            </ul>
          </el-alert>
        </div>
      </el-collapse-item>
      
      <!-- 事件网关说明 -->
      <el-collapse-item v-if="isEventBased" title="网关说明" name="info">
        <div class="gateway-info">
          <el-alert type="info" :closable="false">
            <template #title>事件网关</template>
            <p>事件网关根据后续事件选择执行路径。</p>
            <ul>
              <li>分支：等待后续事件触发</li>
              <li>执行：第一个触发的事件决定执行路径</li>
              <li>其他分支将被取消</li>
            </ul>
          </el-alert>
        </div>
      </el-collapse-item>
      
      <!-- 复杂网关说明 -->
      <el-collapse-item v-if="isComplex" title="网关说明" name="info">
        <div class="gateway-info">
          <el-alert type="info" :closable="false">
            <template #title>复杂网关</template>
            <p>复杂网关支持自定义复杂的分支条件。</p>
            <ul>
              <li>分支：根据自定义表达式决定执行路径</li>
              <li>汇聚：根据激活条件决定何时继续</li>
              <li>适用于复杂业务场景</li>
            </ul>
          </el-alert>
        </div>
      </el-collapse-item>
    </el-collapse>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue'
import type { BpmnElement, BpmnModeler } from '@/types/bpmn'
import {
  getBasicProperties,
  setBasicProperties,
  getExtensionProperties,
  // setExtensionProperty,
  getElementType
} from '@/utils/bpmnExtensions'

const props = defineProps<{
  modeler: BpmnModeler
  element: BpmnElement
}>()

const activeGroups = ref(['basic', 'branch', 'info'])

const gatewayType = ref('bpmn:ExclusiveGateway')
const gatewayName = ref('')
const defaultFlow = ref('')

interface OutgoingFlow {
  id: string
  name: string
  conditionExpression?: string
}

const outgoingFlows = ref<OutgoingFlow[]>([])

const basicProps = computed(() => getBasicProperties(props.element))

const gatewayTypeLabel = computed(() => {
  const names: Record<string, string> = {
    'bpmn:ExclusiveGateway': '排他网关',
    'bpmn:ParallelGateway': '并行网关',
    'bpmn:InclusiveGateway': '包容网关',
    'bpmn:EventBasedGateway': '事件网关',
    'bpmn:ComplexGateway': '复杂网关'
  }
  return names[gatewayType.value] || '网关'
})

const isExclusive = computed(() => gatewayType.value === 'bpmn:ExclusiveGateway')
const isParallel = computed(() => gatewayType.value === 'bpmn:ParallelGateway')
const isInclusive = computed(() => gatewayType.value === 'bpmn:InclusiveGateway')
const isEventBased = computed(() => gatewayType.value === 'bpmn:EventBasedGateway')
const isComplex = computed(() => gatewayType.value === 'bpmn:ComplexGateway')

function loadProperties() {
  if (!props.element) return
  
  // 获取当前网关类型
  const currentType = getElementType(props.element)
  const ext = getExtensionProperties(props.element)
  
  // 直接使用元素的实际类型
  if (currentType === 'bpmn:Gateway') {
    gatewayType.value = ext.gatewayType || 'bpmn:ExclusiveGateway'
  } else if (currentType.includes('Gateway')) {
    gatewayType.value = currentType
  } else {
    gatewayType.value = ext.gatewayType || 'bpmn:ExclusiveGateway'
  }
  
  const basic = getBasicProperties(props.element)
  gatewayName.value = basic.name
  
  const bo = props.element.businessObject
  defaultFlow.value = bo.default?.id || ''
  
  // 加载出口分支
  loadOutgoingFlows()
}

function loadOutgoingFlows() {
  const bo = props.element?.businessObject
  if (!bo?.outgoing) {
    outgoingFlows.value = []
    return
  }
  
  outgoingFlows.value = bo.outgoing.map((flow: any) => ({
    id: flow.id,
    name: flow.name || '',
    conditionExpression: flow.conditionExpression?.body || ''
  }))
}

function updateBasicProp(name: string, value: any) {
  if (!props.element || !props.modeler) return
  setBasicProperties(props.modeler, props.element, { [name]: value })
}

function updateDefaultFlow() {
  if (!props.element || !props.modeler) return
  
  const modeling = props.modeler.get('modeling')
  const elementRegistry = props.modeler.get('elementRegistry')
  
  if (defaultFlow.value) {
    const flowElement = elementRegistry.get(defaultFlow.value)
    if (flowElement) {
      modeling.updateProperties(props.element, { default: flowElement.businessObject })
    }
  } else {
    modeling.updateProperties(props.element, { default: undefined })
  }
}

watch(() => props.element, loadProperties, { immediate: true })

onMounted(loadProperties)
</script>

<style lang="scss" scoped>
.gateway-properties {
  :deep(.el-collapse) {
    border: none;
    
    .el-collapse-item__header {
      font-size: 13px;
      font-weight: 600;
      color: #303133;
      background: #fafafa;
      padding: 0 12px;
      height: 36px;
      line-height: 36px;
      border-radius: 4px;
      margin-bottom: 8px;
      
      &:hover {
        background: #f0f0f0;
      }
    }
    
    .el-collapse-item__wrap {
      border: none;
    }
    
    .el-collapse-item__content {
      padding: 0 4px 12px;
    }
  }
  
  :deep(.el-form-item) {
    margin-bottom: 12px;
    
    .el-form-item__label {
      font-size: 12px;
      color: #606266;
      padding-bottom: 4px;
    }
  }
  
  .form-tip {
    font-size: 11px;
    color: #909399;
    margin-top: 4px;
    line-height: 1.4;
  }
  
  .branch-list {
    margin-top: 12px;
    
    .branch-title {
      font-size: 12px;
      font-weight: 600;
      color: #606266;
      margin-bottom: 8px;
    }
    
    .branch-item {
      padding: 8px 12px;
      background: #f5f7fa;
      border-radius: 4px;
      margin-bottom: 8px;
      
      .branch-name {
        font-size: 13px;
        color: #303133;
        margin-bottom: 4px;
        display: flex;
        align-items: center;
        gap: 6px;
      }
      
      .branch-condition {
        font-size: 11px;
        color: #909399;
        font-family: monospace;
      }
    }
  }
  
  .gateway-info {
    :deep(.el-alert) {
      .el-alert__title {
        font-weight: 600;
      }
      
      p {
        margin: 8px 0;
        font-size: 13px;
      }
      
      ul {
        margin: 0;
        padding-left: 20px;
        font-size: 12px;
        
        li {
          margin: 4px 0;
        }
      }
    }
  }
}
</style>
