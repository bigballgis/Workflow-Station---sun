<template>
  <div class="form-renderer">
    <el-form
      ref="formRef"
      :model="formData"
      :rules="formRules"
      :label-width="labelWidth"
      :disabled="readonly"
      :size="size"
    >
      <el-row :gutter="20">
        <el-col
          v-for="field in fields"
          :key="field.key"
          :span="field.span || 24"
        >
          <el-form-item
            :label="field.label"
            :prop="field.key"
            :required="field.required"
          >
            <!-- 文本输入 -->
            <el-input
              v-if="field.type === 'text' || field.type === 'input'"
              v-model="formData[field.key]"
              :placeholder="field.placeholder"
              :maxlength="field.maxLength"
              :show-word-limit="!!field.maxLength"
              clearable
            />

            <!-- 多行文本 -->
            <el-input
              v-else-if="field.type === 'textarea'"
              v-model="formData[field.key]"
              type="textarea"
              :rows="field.rows || 3"
              :placeholder="field.placeholder"
              :maxlength="field.maxLength"
              :show-word-limit="!!field.maxLength"
            />

            <!-- 数字输入 -->
            <el-input-number
              v-else-if="field.type === 'number'"
              v-model="formData[field.key]"
              :min="field.min"
              :max="field.max"
              :step="field.step || 1"
              :precision="field.precision"
              style="width: 100%"
            />

            <!-- 下拉选择 -->
            <el-select
              v-else-if="field.type === 'select'"
              v-model="formData[field.key]"
              :placeholder="field.placeholder"
              :multiple="field.multiple"
              :filterable="field.filterable"
              clearable
              style="width: 100%"
            >
              <el-option
                v-for="opt in field.options"
                :key="opt.value"
                :label="opt.label"
                :value="opt.value"
              />
            </el-select>

            <!-- 单选框 -->
            <el-radio-group
              v-else-if="field.type === 'radio'"
              v-model="formData[field.key]"
            >
              <el-radio
                v-for="opt in field.options"
                :key="opt.value"
                :label="opt.value"
              >
                {{ opt.label }}
              </el-radio>
            </el-radio-group>

            <!-- 复选框 -->
            <el-checkbox-group
              v-else-if="field.type === 'checkbox'"
              v-model="formData[field.key]"
            >
              <el-checkbox
                v-for="opt in field.options"
                :key="opt.value"
                :label="opt.value"
              >
                {{ opt.label }}
              </el-checkbox>
            </el-checkbox-group>

            <!-- 开关 -->
            <el-switch
              v-else-if="field.type === 'switch'"
              v-model="formData[field.key]"
              :active-text="field.activeText"
              :inactive-text="field.inactiveText"
            />

            <!-- 日期选择 -->
            <el-date-picker
              v-else-if="field.type === 'date'"
              v-model="formData[field.key]"
              type="date"
              :placeholder="field.placeholder"
              value-format="YYYY-MM-DD"
              style="width: 100%"
            />

            <!-- 日期时间选择 -->
            <el-date-picker
              v-else-if="field.type === 'datetime'"
              v-model="formData[field.key]"
              type="datetime"
              :placeholder="field.placeholder"
              value-format="YYYY-MM-DD HH:mm:ss"
              style="width: 100%"
            />

            <!-- 日期范围 -->
            <el-date-picker
              v-else-if="field.type === 'daterange'"
              v-model="formData[field.key]"
              type="daterange"
              range-separator="至"
              start-placeholder="开始日期"
              end-placeholder="结束日期"
              value-format="YYYY-MM-DD"
              style="width: 100%"
            />

            <!-- 时间选择 -->
            <el-time-picker
              v-else-if="field.type === 'time'"
              v-model="formData[field.key]"
              :placeholder="field.placeholder"
              value-format="HH:mm:ss"
              style="width: 100%"
            />

            <!-- 级联选择 -->
            <el-cascader
              v-else-if="field.type === 'cascader'"
              v-model="formData[field.key]"
              :options="field.options"
              :props="field.cascaderProps"
              :placeholder="field.placeholder"
              clearable
              style="width: 100%"
            />

            <!-- 用户选择器 -->
            <el-select
              v-else-if="field.type === 'user'"
              v-model="formData[field.key]"
              :placeholder="field.placeholder"
              :multiple="field.multiple"
              filterable
              remote
              :remote-method="(query: string) => searchUsers(query, field)"
              clearable
              style="width: 100%"
            >
              <el-option
                v-for="user in field.userOptions || []"
                :key="user.id"
                :label="user.name"
                :value="user.id"
              />
            </el-select>

            <!-- 部门选择器 -->
            <el-tree-select
              v-else-if="field.type === 'department'"
              v-model="formData[field.key]"
              :data="field.deptOptions || []"
              :props="{ label: 'name', value: 'id', children: 'children' }"
              :placeholder="field.placeholder"
              check-strictly
              clearable
              style="width: 100%"
            />

            <!-- 金额输入 -->
            <el-input
              v-else-if="field.type === 'money'"
              v-model="formData[field.key]"
              :placeholder="field.placeholder"
              clearable
            >
              <template #prepend>{{ field.currency || '¥' }}</template>
            </el-input>

            <!-- 只读文本 -->
            <span v-else-if="field.type === 'readonly'" class="readonly-text">
              {{ formData[field.key] || '-' }}
            </span>

            <!-- 分隔线 -->
            <el-divider v-else-if="field.type === 'divider'" />

            <!-- 提示信息 -->
            <el-alert
              v-else-if="field.type === 'alert'"
              :title="field.alertTitle"
              :type="field.alertType || 'info'"
              :closable="false"
              show-icon
            />
          </el-form-item>
        </el-col>
      </el-row>
    </el-form>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'

export interface FormField {
  key: string
  label: string
  type: string
  required?: boolean
  placeholder?: string
  span?: number
  options?: Array<{ label: string; value: any }>
  multiple?: boolean
  filterable?: boolean
  maxLength?: number
  min?: number
  max?: number
  step?: number
  precision?: number
  rows?: number
  activeText?: string
  inactiveText?: string
  cascaderProps?: object
  currency?: string
  alertTitle?: string
  alertType?: 'success' | 'warning' | 'info' | 'error'
  userOptions?: Array<{ id: string; name: string }>
  deptOptions?: any[]
  rules?: any[]
  defaultValue?: any
}

interface Props {
  fields: FormField[]
  modelValue?: Record<string, any>
  readonly?: boolean
  labelWidth?: string
  size?: 'large' | 'default' | 'small'
}

const props = withDefaults(defineProps<Props>(), {
  modelValue: () => ({}),
  readonly: false,
  labelWidth: '120px',
  size: 'default'
})

const emit = defineEmits<{
  (e: 'update:modelValue', value: Record<string, any>): void
  (e: 'change', key: string, value: any): void
}>()

const formRef = ref<FormInstance>()
const formData = ref<Record<string, any>>({})

// 初始化表单数据
const initFormData = () => {
  const data: Record<string, any> = {}
  props.fields.forEach(field => {
    if (props.modelValue[field.key] !== undefined) {
      data[field.key] = props.modelValue[field.key]
    } else if (field.defaultValue !== undefined) {
      data[field.key] = field.defaultValue
    } else if (field.type === 'checkbox') {
      data[field.key] = []
    } else {
      data[field.key] = null
    }
  })
  formData.value = data
}

// 生成表单验证规则
const formRules = computed<FormRules>(() => {
  const rules: FormRules = {}
  props.fields.forEach(field => {
    if (field.required || field.rules) {
      rules[field.key] = []
      if (field.required) {
        rules[field.key].push({
          required: true,
          message: `请输入${field.label}`,
          trigger: field.type === 'select' ? 'change' : 'blur'
        })
      }
      if (field.rules) {
        rules[field.key].push(...field.rules)
      }
    }
  })
  return rules
})

// 监听表单数据变化
watch(formData, (newVal) => {
  emit('update:modelValue', newVal)
}, { deep: true })

// 监听外部数据变化
watch(() => props.modelValue, () => {
  initFormData()
}, { deep: true })

// 用户搜索
const searchUsers = async (query: string, field: FormField) => {
  if (query.length < 2) return
  // 这里可以调用API搜索用户
  // const users = await userApi.search(query)
  // field.userOptions = users
}

// 表单验证
const validate = async (): Promise<boolean> => {
  if (!formRef.value) return false
  try {
    await formRef.value.validate()
    return true
  } catch {
    return false
  }
}

// 重置表单
const resetForm = () => {
  formRef.value?.resetFields()
  initFormData()
}

// 获取表单数据
const getFormData = () => {
  return { ...formData.value }
}

// 设置字段值
const setFieldValue = (key: string, value: any) => {
  formData.value[key] = value
}

onMounted(() => {
  initFormData()
})

defineExpose({
  validate,
  resetForm,
  getFormData,
  setFieldValue
})
</script>

<style scoped lang="scss">
.form-renderer {
  .readonly-text {
    color: #606266;
    line-height: 32px;
  }

  :deep(.el-form-item__label) {
    font-weight: 500;
  }
}
</style>
