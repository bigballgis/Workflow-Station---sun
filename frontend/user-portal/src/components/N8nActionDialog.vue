<template>
  <el-dialog
    :model-value="visible"
    :title="t('n8nAction.title')"
    width="600px"
    :close-on-click-modal="false"
    :close-on-press-escape="!executing"
    @update:model-value="handleVisibilityChange"
  >
    <!-- Workflow Info -->
    <div class="n8n-workflow-info" v-if="workflowName">
      <el-descriptions :column="1" border size="small">
        <el-descriptions-item :label="t('n8nAction.workflowName')">
          {{ workflowName }}
        </el-descriptions-item>
        <el-descriptions-item v-if="workflowDescription" :label="t('n8nAction.workflowDescription')">
          {{ workflowDescription }}
        </el-descriptions-item>
      </el-descriptions>
    </div>

    <!-- Input Form (initial state) -->
    <div v-if="state === 'initial'" class="n8n-input-form">
      <el-form
        v-if="inputMappingList.length > 0"
        ref="formRef"
        :model="formData"
        :rules="formRules"
        label-position="top"
      >
        <el-form-item
          v-for="param in inputMappingList"
          :key="param.paramName"
          :label="param.paramLabel || param.paramName"
          :prop="param.paramName"
        >
          <!-- string -->
          <el-input
            v-if="param.paramType === 'string'"
            v-model="formData[param.paramName]"
            :placeholder="t('common.pleaseInput', { label: param.paramLabel || param.paramName })"
          />
          <!-- number -->
          <el-input-number
            v-else-if="param.paramType === 'number'"
            v-model="formData[param.paramName]"
            controls-position="right"
            style="width: 100%"
          />
          <!-- boolean -->
          <el-switch
            v-else-if="param.paramType === 'boolean'"
            v-model="formData[param.paramName]"
          />
          <!-- select -->
          <el-select
            v-else-if="param.paramType === 'select'"
            v-model="formData[param.paramName]"
            :placeholder="t('common.pleaseSelect', { label: param.paramLabel || param.paramName })"
            style="width: 100%"
          >
            <el-option
              v-for="opt in (param.options || [])"
              :key="opt"
              :label="opt"
              :value="opt"
            />
          </el-select>
          <!-- fallback to text input -->
          <el-input
            v-else
            v-model="formData[param.paramName]"
            :placeholder="t('common.pleaseInput', { label: param.paramLabel || param.paramName })"
          />
        </el-form-item>
      </el-form>
      <el-empty v-else :description="t('n8nAction.noInputParams')" :image-size="60" />
    </div>

    <!-- Executing state -->
    <div v-else-if="state === 'executing'" class="n8n-executing">
      <el-result icon="info" :title="t('n8nAction.executing')">
        <template #extra>
          <el-progress :percentage="50" :indeterminate="true" status="warning" />
        </template>
      </el-result>
    </div>

    <!-- Success state -->
    <div v-else-if="state === 'success'" class="n8n-result">
      <el-result icon="success" :title="t('n8nAction.success')">
        <template #extra>
          <div v-if="resultData && Object.keys(resultData).length > 0" class="result-data">
            <h4>{{ t('n8nAction.resultData') }}</h4>
            <el-descriptions :column="1" border size="small">
              <el-descriptions-item
                v-for="(value, key) in resultData"
                :key="String(key)"
                :label="String(key)"
              >
                {{ typeof value === 'object' ? JSON.stringify(value) : value }}
              </el-descriptions-item>
            </el-descriptions>
          </div>
          <el-empty v-else :description="t('n8nAction.noResultData')" :image-size="40" />
        </template>
      </el-result>
    </div>

    <!-- Failed/Timeout state -->
    <div v-else-if="state === 'failed' || state === 'timeout'" class="n8n-error">
      <el-result
        icon="error"
        :title="state === 'timeout' ? t('n8nAction.timeout') : t('n8nAction.failed')"
      >
        <template #sub-title>
          <span>{{ errorMessage || (state === 'timeout' ? t('n8nAction.timeoutMessage') : t('n8nAction.failedMessage')) }}</span>
        </template>
      </el-result>
    </div>

    <!-- Footer -->
    <template #footer>
      <div class="dialog-footer">
        <!-- Initial state: Execute + Close -->
        <template v-if="state === 'initial'">
          <el-button @click="handleClose">{{ t('n8nAction.close') }}</el-button>
          <el-button type="primary" @click="handleExecute">
            {{ t('n8nAction.execute') }}
          </el-button>
        </template>

        <!-- Executing state: disabled button -->
        <template v-else-if="state === 'executing'">
          <el-button type="primary" :loading="true" disabled>
            {{ t('n8nAction.executing') }}
          </el-button>
        </template>

        <!-- Success state: Close -->
        <template v-else-if="state === 'success'">
          <el-button type="primary" @click="handleClose">{{ t('n8nAction.close') }}</el-button>
        </template>

        <!-- Failed/Timeout state: Retry + Close -->
        <template v-else>
          <el-button @click="handleClose">{{ t('n8nAction.close') }}</el-button>
          <el-button type="primary" @click="handleRetry">{{ t('n8nAction.retry') }}</el-button>
        </template>
      </div>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import type { FormInstance, FormRules } from 'element-plus'
import { executeN8nAction } from '@/api/n8n'

export interface InputMappingParam {
  paramName: string
  paramLabel: string
  paramType: 'string' | 'number' | 'boolean' | 'select'
  required: boolean
  options?: string[]
}

export interface ActionDefinition {
  id: number
  actionName?: string
  configJson?: string
}

interface Props {
  visible: boolean
  actionDefinition: ActionDefinition
  taskId: string
  processInstanceId: string
  initialData?: Record<string, any>
}

const props = defineProps<Props>()

const emit = defineEmits<{
  (e: 'update:visible', value: boolean): void
  (e: 'executed', data: Record<string, any> | null): void
}>()

const { t } = useI18n()

const formRef = ref<FormInstance>()
const state = ref<'initial' | 'executing' | 'success' | 'failed' | 'timeout'>('initial')
const formData = ref<Record<string, any>>({})
const resultData = ref<Record<string, any> | null>(null)
const errorMessage = ref('')

// Parse configJson from actionDefinition
const parsedConfig = computed(() => {
  try {
    if (props.actionDefinition?.configJson) {
      return JSON.parse(props.actionDefinition.configJson)
    }
  } catch {
    // ignore parse errors
  }
  return {}
})

const workflowName = computed(() => parsedConfig.value.n8nWorkflowId || '')
const workflowDescription = computed(() => parsedConfig.value.description || '')

const inputMappingList = computed<InputMappingParam[]>(() => {
  const mapping = parsedConfig.value.inputMapping
  if (Array.isArray(mapping)) {
    return mapping
  }
  return []
})

const executing = computed(() => state.value === 'executing')

// Build form validation rules from inputMapping
const formRules = computed<FormRules>(() => {
  const rules: FormRules = {}
  for (const param of inputMappingList.value) {
    if (param.required) {
      rules[param.paramName] = [
        {
          required: true,
          message: t('n8nAction.requiredField', { label: param.paramLabel || param.paramName }),
          trigger: param.paramType === 'select' ? 'change' : 'blur'
        }
      ]
    }
  }
  return rules
})

// Initialize form data when dialog opens
watch(() => props.visible, (newVal) => {
  if (newVal) {
    resetState()
  }
})

function resetState() {
  state.value = 'initial'
  resultData.value = null
  errorMessage.value = ''
  // Initialize form data with defaults, then overlay initialData
  const data: Record<string, any> = {}
  for (const param of inputMappingList.value) {
    if (props.initialData && props.initialData[param.paramName] !== undefined) {
      data[param.paramName] = props.initialData[param.paramName]
    } else if (param.paramType === 'boolean') {
      data[param.paramName] = false
    } else if (param.paramType === 'number') {
      data[param.paramName] = undefined
    } else {
      data[param.paramName] = ''
    }
  }
  formData.value = data

  // If all required fields are pre-filled, auto-execute
  if (props.initialData) {
    const allFilled = inputMappingList.value
      .filter(p => p.required)
      .every(p => data[p.paramName] !== undefined && data[p.paramName] !== '' && 
        !(Array.isArray(data[p.paramName]) && data[p.paramName].length === 0))
    if (allFilled) {
      handleExecute()
    }
  }
}

async function handleExecute() {
  // Validate form if there are input params
  if (formRef.value) {
    try {
      await formRef.value.validate()
    } catch {
      return
    }
  }

  state.value = 'executing'

  try {
    const response = await executeN8nAction({
      actionDefinitionId: props.actionDefinition.id,
      taskId: props.taskId,
      processInstanceId: props.processInstanceId,
      inputData: { ...formData.value }
    })

    const result = (response as any)?.data ?? response
    if (result?.status === 'TIMEOUT') {
      state.value = 'timeout'
      errorMessage.value = result.errorMessage || ''
    } else if (result?.status === 'FAILED') {
      state.value = 'failed'
      errorMessage.value = result.errorMessage || ''
    } else {
      state.value = 'success'
      resultData.value = result?.data ?? result?.outputData ?? null
      emit('executed', resultData.value)
    }
  } catch (err: any) {
    state.value = 'failed'
    errorMessage.value = err?.message || t('n8nAction.failedMessage')
  }
}

function handleRetry() {
  state.value = 'initial'
  errorMessage.value = ''
  resultData.value = null
}

function handleClose() {
  emit('update:visible', false)
}

function handleVisibilityChange(val: boolean) {
  if (!val && !executing.value) {
    emit('update:visible', false)
  }
}
</script>

<style scoped lang="scss">
.n8n-workflow-info {
  margin-bottom: 16px;
}

.n8n-input-form {
  min-height: 80px;
}

.n8n-executing {
  text-align: center;
  padding: 20px 0;
}

.n8n-result {
  .result-data {
    text-align: left;
    width: 100%;

    h4 {
      margin: 0 0 8px 0;
      font-size: 14px;
      color: #606266;
    }
  }
}

.n8n-error {
  text-align: center;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
</style>
