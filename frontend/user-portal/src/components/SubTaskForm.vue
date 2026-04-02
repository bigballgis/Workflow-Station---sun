<template>
  <div class="sub-task-form">
    <el-card v-if="loading" v-loading="loading" :body-style="{ padding: '60px' }">
      <div style="text-align: center; color: #909399;">{{ t('task.loadingFormData') }}</div>
    </el-card>

    <template v-else-if="formData">
      <!-- 主任务信息区域（只读，灰色背景） -->
      <el-card class="main-task-section" shadow="never">
        <template #header>
          <div class="section-header">
            <el-icon><Document /></el-icon>
            <span>{{ t('task.mainTaskInfo') }}</span>
          </div>
        </template>
        <el-descriptions :column="2" border>
          <el-descriptions-item
            v-for="field in formData.mainFormFields"
            :key="field.name"
            :label="field.label"
          >
            <span class="readonly-value">{{ formatFieldValue(formData.mainFormData[field.name], field) }}</span>
          </el-descriptions-item>
        </el-descriptions>
      </el-card>

      <!-- 子任务表单区域（可编辑，蓝色标题） -->
      <el-card class="sub-task-section" shadow="never">
        <template #header>
          <div class="section-header sub-task-header">
            <el-icon><Edit /></el-icon>
            <span>{{ t('task.yourTaskInfo') }}</span>
          </div>
        </template>
        <el-form
          ref="formRef"
          :model="subFormData"
          :rules="formRules"
          label-width="140px"
          label-position="right"
        >
          <el-row :gutter="20">
            <el-col
              v-for="field in formData.subFormFields"
              :key="field.name"
              :span="field.span || 24"
            >
              <el-form-item
                :label="field.label"
                :prop="field.name"
                :required="field.required"
              >
                <FieldRenderer
                  :field="field"
                  :model-value="subFormData[field.name]"
                  :readonly="field.readonly"
                  :upload-url="uploadUrl"
                  @update:model-value="(val: any) => handleFieldChange(field.name, val)"
                />
              </el-form-item>
            </el-col>
          </el-row>

          <el-form-item class="form-actions">
            <el-button type="primary" :loading="submitting" @click="handleSubmit">
              {{ t('common.submit') }}
            </el-button>
            <el-button @click="handleCancel">
              {{ t('common.cancel') }}
            </el-button>
          </el-form-item>
        </el-form>
      </el-card>
    </template>

    <el-empty v-else :description="t('task.noFormData')" />
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, type FormInstance } from 'element-plus'
import { Document, Edit } from '@element-plus/icons-vue'
import FieldRenderer from './FieldRenderer.vue'
import { getSubTaskFormData, completeTask } from '@/api/task'

interface FormField {
  name: string
  label: string
  type: string
  required?: boolean
  readonly?: boolean
  span?: number
  options?: any[]
  [key: string]: any
}

interface SubTaskFormData {
  taskId: string
  mainFormData: Record<string, any>
  mainFormFields: FormField[]
  subTableRowData: Record<string, any>
  subFormFields: FormField[]
  rowVersion: number
}

const props = defineProps<{
  taskId: string
}>()

const emit = defineEmits<{
  (e: 'submit-success'): void
  (e: 'cancel'): void
}>()

const { t } = useI18n()
const router = useRouter()

const loading = ref(false)
const submitting = ref(false)
const formData = ref<SubTaskFormData | null>(null)
const subFormData = ref<Record<string, any>>({})
const formRef = ref<FormInstance>()

const uploadUrl = computed(() => {
  return import.meta.env.VITE_API_BASE_URL + '/api/files/upload'
})

// 动态生成表单验证规则
const formRules = computed(() => {
  const rules: Record<string, any[]> = {}
  if (formData.value) {
    formData.value.subFormFields.forEach(field => {
      if (field.required) {
        rules[field.name] = [
          {
            required: true,
            message: t('validation.required', { field: field.label }),
            trigger: ['blur', 'change']
          }
        ]
      }
    })
  }
  return rules
})

// 加载子任务表单数据
async function loadFormData() {
  loading.value = true
  try {
    const response = await getSubTaskFormData(props.taskId)
    formData.value = response.data
    // 初始化子表单数据
    subFormData.value = { ...response.data.subTableRowData }
  } catch (error: any) {
    ElMessage.error(error.message || t('task.loadFormDataFailed'))
  } finally {
    loading.value = false
  }
}

// 格式化字段值用于只读显示
function formatFieldValue(value: any, field: FormField): string {
  if (value === null || value === undefined || value === '') {
    return '-'
  }
  
  // 根据字段类型格式化
  switch (field.type) {
    case 'date':
      return new Date(value).toLocaleDateString()
    case 'datetime':
      return new Date(value).toLocaleString()
    case 'boolean':
      return value ? t('common.yes') : t('common.no')
    case 'select':
    case 'radio':
      // 如果有选项列表，查找对应的标签
      if (field.options) {
        const option = field.options.find((opt: any) => opt.value === value)
        return option ? option.label : value
      }
      return value
    default:
      return String(value)
  }
}

// 处理字段变化
function handleFieldChange(fieldName: string, value: any) {
  subFormData.value[fieldName] = value
}

// 提交表单
async function handleSubmit() {
  if (!formRef.value) return

  try {
    // 验证表单
    await formRef.value.validate()

    // 确认提交
    await ElMessageBox.confirm(
      t('task.confirmSubmit'),
      t('common.confirm'),
      {
        confirmButtonText: t('common.confirm'),
        cancelButtonText: t('common.cancel'),
        type: 'warning'
      }
    )

    submitting.value = true

    // 提交任务
    await completeTask(props.taskId, {
      taskId: props.taskId,
      action: 'complete',
      formData: subFormData.value,
      variables: {
        rowVersion: formData.value!.rowVersion
      }
    })

    ElMessage.success(t('task.submitSuccess'))
    emit('submit-success')

    // 跳转到待办列表
    setTimeout(() => {
      router.push('/tasks')
    }, 1000)
  } catch (error: any) {
    if (error !== 'cancel') {
      // 处理乐观锁冲突
      if (error.code === 'OPTIMISTIC_LOCK_EXCEPTION') {
        ElMessage.error(t('task.dataModifiedPleaseRefresh'))
        // 重新加载表单数据
        await loadFormData()
      } else {
        ElMessage.error(error.message || t('task.submitFailed'))
      }
    }
  } finally {
    submitting.value = false
  }
}

// 取消操作
function handleCancel() {
  ElMessageBox.confirm(
    t('task.confirmCancel'),
    t('common.confirm'),
    {
      confirmButtonText: t('common.confirm'),
      cancelButtonText: t('common.cancel'),
      type: 'warning'
    }
  ).then(() => {
    emit('cancel')
    router.push('/tasks')
  }).catch(() => {
    // 用户取消
  })
}

onMounted(() => {
  loadFormData()
})
</script>

<style scoped lang="scss">
.sub-task-form {
  padding: 20px;
  max-width: 1200px;
  margin: 0 auto;
}

.main-task-section {
  margin-bottom: 24px;
  background-color: #f5f7fa;
  
  :deep(.el-card__header) {
    background-color: #e4e7ed;
    padding: 12px 20px;
  }
}

.sub-task-section {
  :deep(.el-card__header) {
    background-color: #ecf5ff;
    padding: 12px 20px;
  }
}

.section-header {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
  font-size: 16px;
  color: #303133;
  
  .el-icon {
    font-size: 18px;
  }
}

.sub-task-header {
  color: #1976d2;
  
  .el-icon {
    color: #1976d2;
  }
}

.readonly-value {
  color: #606266;
  word-break: break-word;
}

.form-actions {
  margin-top: 32px;
  text-align: center;
  
  .el-button {
    min-width: 120px;
  }
}

:deep(.el-descriptions__label) {
  font-weight: 500;
  color: #606266;
  background-color: #fafafa;
}

:deep(.el-descriptions__content) {
  background-color: #ffffff;
}
</style>
