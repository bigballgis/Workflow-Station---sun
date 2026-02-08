<template>
  <div class="process-properties">
    <el-collapse v-model="activeGroups">
      <el-collapse-item :title="t('properties.basic')" name="basic">
        <el-form label-position="top" size="small">
          <el-form-item :label="t('properties.processId')">
            <el-input v-model="processId" @change="updateProcessId" :placeholder="t('properties.processIdPlaceholder')" />
          </el-form-item>
          <el-form-item :label="t('properties.processName')">
            <el-input v-model="processName" @change="updateProcessName" :placeholder="t('properties.processNamePlaceholder')" />
          </el-form-item>
          <el-form-item :label="t('properties.isExecutable')">
            <el-switch v-model="isExecutable" @change="updateExecutable" />
          </el-form-item>
        </el-form>
      </el-collapse-item>
      
      <el-collapse-item :title="t('properties.documentation')" name="documentation">
        <el-form label-position="top" size="small">
          <el-form-item :label="t('properties.processDescription')">
            <el-input 
              v-model="documentation" 
              type="textarea" 
              :rows="4"
              @change="updateDocumentation"
              :placeholder="t('properties.processDescriptionPlaceholder')"
            />
          </el-form-item>
        </el-form>
      </el-collapse-item>
    </el-collapse>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import type { BpmnElement, BpmnModeler } from '@/types/bpmn'

const { t } = useI18n()

const props = defineProps<{
  modeler: BpmnModeler
  element: BpmnElement | null
}>()

const activeGroups = ref(['basic', 'documentation'])
const processId = ref('')
const processName = ref('')
const isExecutable = ref(true)
const documentation = ref('')

function loadProperties() {
  if (!props.element?.businessObject) return
  
  const bo = props.element.businessObject
  processId.value = bo.id || ''
  processName.value = bo.name || ''
  isExecutable.value = bo.isExecutable !== false
  
  // Read documentation
  const docs = bo.documentation
  if (docs && docs.length > 0) {
    documentation.value = docs[0].text || ''
  } else {
    documentation.value = ''
  }
}

function updateProcessId() {
  if (!props.element || !props.modeler) return
  const modeling = props.modeler.get('modeling')
  modeling.updateProperties(props.element, { id: processId.value })
}

function updateProcessName() {
  if (!props.element || !props.modeler) return
  const modeling = props.modeler.get('modeling')
  modeling.updateProperties(props.element, { name: processName.value })
}

function updateExecutable() {
  if (!props.element || !props.modeler) return
  const modeling = props.modeler.get('modeling')
  modeling.updateProperties(props.element, { isExecutable: isExecutable.value })
}

function updateDocumentation() {
  if (!props.element || !props.modeler) return
  const modeling = props.modeler.get('modeling')
  const moddle = props.modeler.get('moddle')
  
  if (documentation.value) {
    const doc = moddle.create('bpmn:Documentation', { text: documentation.value })
    modeling.updateProperties(props.element, { documentation: [doc] })
  } else {
    modeling.updateProperties(props.element, { documentation: [] })
  }
}

watch(() => props.element, loadProperties, { immediate: true })

onMounted(loadProperties)
</script>

<style lang="scss" scoped>
.process-properties {
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
}
</style>
