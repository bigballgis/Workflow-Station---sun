<template>
  <el-dialog
    :model-value="modelValue"
    :title="t('automationFlow.structureTitle', { name: flowName })"
    width="560px"
    @update:model-value="emit('update:modelValue', $event)"
    @closed="reset"
  >
    <div
      v-loading="loading"
      class="structure-body"
    >
      <el-alert
        v-if="pkg && pkg.fromPublished === false"
        :title="t('automationFlow.structureDraftHint')"
        type="warning"
        :closable="false"
        show-icon
        class="structure-alert"
      />
      <div
        v-if="loadFailed"
        class="structure-empty"
      >
        {{ t('automationFlow.structureLoadFailed') }}
      </div>
      <div
        v-else-if="!loading && steps.length === 0"
        class="structure-empty"
      >
        {{ t('automationFlow.structureEmpty') }}
      </div>
      <template v-else>
        <template
          v-for="(step, i) in steps"
          :key="step.name || i"
        >
          <div
            v-if="i > 0"
            class="structure-connector"
          />
          <FlowStepNode :step="step" />
        </template>
      </template>
    </div>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { automationFlowApi } from '@/api/automationFlow'
import { parseFlowSteps, type FlowExportPackage, type FlowStepView } from '@/utils/flowStructure'
import FlowStepNode from './FlowStepNode.vue'

const props = defineProps<{
  modelValue: boolean
  flowId: string
  flowName: string
}>()

const emit = defineEmits<{ 'update:modelValue': [value: boolean] }>()

const { t } = useI18n()

const loading = ref(false)
const loadFailed = ref(false)
const pkg = ref<FlowExportPackage | null>(null)
const steps = ref<FlowStepView[]>([])

const fetchStructure = async () => {
  if (!props.flowId) return
  loading.value = true
  loadFailed.value = false
  try {
    const data = await automationFlowApi.getFlowExport(props.flowId)
    pkg.value = data
    steps.value = parseFlowSteps(data)
  } catch {
    // request.ts 拦截器已 notify 具体错误,这里只留弹窗内兜底文案
    loadFailed.value = true
  } finally {
    loading.value = false
  }
}

watch(() => props.modelValue, (visible) => {
  if (visible) void fetchStructure()
})

const reset = () => {
  pkg.value = null
  steps.value = []
  loadFailed.value = false
}
</script>

<style scoped>
.structure-body {
  max-height: 60vh;
  overflow-y: auto;
  min-height: 80px;
}

.structure-alert {
  margin-bottom: 12px;
}

.structure-empty {
  color: var(--el-text-color-secondary);
  font-size: 13px;
  padding: 16px 0;
  text-align: center;
}

.structure-connector {
  width: 1px;
  height: 14px;
  margin-left: 20px;
  background: var(--el-border-color);
}
</style>
