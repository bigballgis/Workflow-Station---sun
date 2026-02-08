<template>
  <div class="gateway-properties">
    <el-collapse v-model="activeGroups">
      <!-- Basic info -->
      <el-collapse-item :title="t('properties.basic')" name="basic">
        <el-form label-position="top" size="small">
          <el-form-item :label="t('properties.gatewayId')">
            <el-input :model-value="basicProps.id" disabled />
          </el-form-item>
          <el-form-item :label="t('properties.gatewayType')">
            <el-input :model-value="gatewayTypeLabel" disabled />
          </el-form-item>
          <el-form-item :label="t('properties.gatewayName')">
            <el-input v-model="gatewayName" @change="updateBasicProp('name', gatewayName)" :placeholder="t('properties.gatewayNamePlaceholder')" />
          </el-form-item>
        </el-form>
      </el-collapse-item>
      
      <!-- Exclusive gateway config -->
      <el-collapse-item v-if="isExclusive" :title="t('properties.branchConfig')" name="branch">
        <el-form label-position="top" size="small">
          <el-form-item :label="t('properties.defaultBranch')">
            <el-select v-model="defaultFlow" @change="updateDefaultFlow" :placeholder="t('properties.selectDefaultBranch')" clearable>
              <el-option 
                v-for="flow in outgoingFlows" 
                :key="flow.id" 
                :label="flow.name || flow.id" 
                :value="flow.id" 
              />
            </el-select>
            <div class="form-tip">{{ t('properties.defaultBranchTip') }}</div>
          </el-form-item>
          
          <div class="branch-list">
            <div class="branch-title">{{ t('properties.outgoingBranches') }}</div>
            <div v-for="flow in outgoingFlows" :key="flow.id" class="branch-item">
              <div class="branch-name">
                <el-tag v-if="flow.id === defaultFlow" type="success" size="small">{{ t('properties.defaultTag') }}</el-tag>
                {{ flow.name || flow.id }}
              </div>
              <div class="branch-condition">
                {{ flow.conditionExpression || t('properties.noCondition') }}
              </div>
            </div>
            <el-empty v-if="!outgoingFlows.length" :description="t('properties.noOutgoingBranches')" :image-size="60" />
          </div>
        </el-form>
      </el-collapse-item>
      
      <!-- Parallel gateway info -->
      <el-collapse-item v-if="isParallel" :title="t('properties.gatewayInfo')" name="info">
        <div class="gateway-info">
          <el-alert type="info" :closable="false">
            <template #title>{{ t('properties.parallelGatewayTitle') }}</template>
            <p>{{ t('properties.parallelGatewayDesc') }}</p>
            <ul>
              <li>{{ t('properties.parallelGatewayBranch') }}</li>
              <li>{{ t('properties.parallelGatewayMerge') }}</li>
            </ul>
          </el-alert>
        </div>
      </el-collapse-item>
      
      <!-- Inclusive gateway info -->
      <el-collapse-item v-if="isInclusive" :title="t('properties.gatewayInfo')" name="info">
        <div class="gateway-info">
          <el-alert type="info" :closable="false">
            <template #title>{{ t('properties.inclusiveGatewayTitle') }}</template>
            <p>{{ t('properties.inclusiveGatewayDesc') }}</p>
            <ul>
              <li>{{ t('properties.inclusiveGatewayBranch') }}</li>
              <li>{{ t('properties.inclusiveGatewayMerge') }}</li>
            </ul>
          </el-alert>
        </div>
      </el-collapse-item>
      
      <!-- Event-based gateway info -->
      <el-collapse-item v-if="isEventBased" :title="t('properties.gatewayInfo')" name="info">
        <div class="gateway-info">
          <el-alert type="info" :closable="false">
            <template #title>{{ t('properties.eventBasedGatewayTitle') }}</template>
            <p>{{ t('properties.eventBasedGatewayDesc') }}</p>
            <ul>
              <li>{{ t('properties.eventBasedGatewayBranch') }}</li>
              <li>{{ t('properties.eventBasedGatewayExec') }}</li>
              <li>{{ t('properties.eventBasedGatewayCancel') }}</li>
            </ul>
          </el-alert>
        </div>
      </el-collapse-item>
      
      <!-- Complex gateway info -->
      <el-collapse-item v-if="isComplex" :title="t('properties.gatewayInfo')" name="info">
        <div class="gateway-info">
          <el-alert type="info" :closable="false">
            <template #title>{{ t('properties.complexGatewayTitle') }}</template>
            <p>{{ t('properties.complexGatewayDesc') }}</p>
            <ul>
              <li>{{ t('properties.complexGatewayBranch') }}</li>
              <li>{{ t('properties.complexGatewayMerge') }}</li>
              <li>{{ t('properties.complexGatewayUseCase') }}</li>
            </ul>
          </el-alert>
        </div>
      </el-collapse-item>
    </el-collapse>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import type { BpmnElement, BpmnModeler } from '@/types/bpmn'
import {
  getBasicProperties,
  setBasicProperties,
  getExtensionProperties,
  setExtensionProperty,
  getElementType
} from '@/utils/bpmnExtensions'

const { t } = useI18n()

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
    'bpmn:ExclusiveGateway': t('properties.gatewayTypeExclusive'),
    'bpmn:ParallelGateway': t('properties.gatewayTypeParallel'),
    'bpmn:InclusiveGateway': t('properties.gatewayTypeInclusive'),
    'bpmn:EventBasedGateway': t('properties.gatewayTypeEventBased'),
    'bpmn:ComplexGateway': t('properties.gatewayTypeComplex')
  }
  return names[gatewayType.value] || t('properties.gatewayTypeGateway')
})

const isExclusive = computed(() => gatewayType.value === 'bpmn:ExclusiveGateway')
const isParallel = computed(() => gatewayType.value === 'bpmn:ParallelGateway')
const isInclusive = computed(() => gatewayType.value === 'bpmn:InclusiveGateway')
const isEventBased = computed(() => gatewayType.value === 'bpmn:EventBasedGateway')
const isComplex = computed(() => gatewayType.value === 'bpmn:ComplexGateway')

function loadProperties() {
  if (!props.element) return
  
  const currentType = getElementType(props.element)
  const ext = getExtensionProperties(props.element)
  
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
