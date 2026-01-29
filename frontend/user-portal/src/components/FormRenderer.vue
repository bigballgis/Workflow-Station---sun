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
      <!-- Tab 布局模式 -->
      <template v-if="hasTabs">
        <el-tabs v-model="activeTab" type="border-card">
          <el-tab-pane
            v-for="tab in tabs"
            :key="tab.name"
            :label="tab.label"
            :name="tab.name"
          >
            <el-row :gutter="20">
              <el-col
                v-for="field in tab.fields"
                :key="field.key"
                :span="field.span || 24"
              >
                <el-form-item
                  :label="field.label"
                  :prop="field.key"
                  :required="field.required"
                >
                  <!-- 渲染字段 -->
                  <template v-if="field.type === 'text' || field.type === 'input'">
                    <el-input
                      v-model="formData[field.key]"
                      :placeholder="field.placeholder"
                      :maxlength="field.maxLength"
                      :show-word-limit="!!field.maxLength"
                      clearable
                    />
                  </template>
                  <template v-else-if="field.type === 'textarea'">
                    <el-input
                      v-model="formData[field.key]"
                      type="textarea"
                      :rows="field.rows || 3"
                      :placeholder="field.placeholder"
                      :maxlength="field.maxLength"
                      :show-word-limit="!!field.maxLength"
                    />
                  </template>
                  <template v-else-if="field.type === 'number'">
                    <el-input-number
                      v-model="formData[field.key]"
                      :min="field.min"
                      :max="field.max"
                      :step="field.step || 1"
                      :precision="field.precision"
                      style="width: 100%"
                    />
                  </template>
                  <template v-else-if="field.type === 'select'">
                    <el-select
                      v-model="formData[field.key]"
                      :placeholder="field.placeholder"
                      :multiple="field.multiple"
                      :filterable="field.filterable"
                      clearable
                      style="width: 100%"
                      popper-class="form-renderer-popper"
                    >
                      <el-option
                        v-for="opt in field.options"
                        :key="opt.value"
                        :label="opt.label"
                        :value="opt.value"
                      />
                    </el-select>
                  </template>
                  <template v-else-if="field.type === 'radio'">
                    <el-radio-group v-model="formData[field.key]">
                      <el-radio
                        v-for="opt in field.options"
                        :key="opt.value"
                        :label="opt.value"
                      >
                        {{ opt.label }}
                      </el-radio>
                    </el-radio-group>
                  </template>
                  <template v-else-if="field.type === 'checkbox'">
                    <el-checkbox-group v-model="formData[field.key]">
                      <el-checkbox
                        v-for="opt in field.options"
                        :key="opt.value"
                        :label="opt.value"
                      >
                        {{ opt.label }}
                      </el-checkbox>
                    </el-checkbox-group>
                  </template>
                  <template v-else-if="field.type === 'switch'">
                    <el-switch
                      v-model="formData[field.key]"
                      :active-text="field.activeText"
                      :inactive-text="field.inactiveText"
                    />
                  </template>
                  <template v-else-if="field.type === 'date'">
                    <el-date-picker
                      v-model="formData[field.key]"
                      type="date"
                      :placeholder="field.placeholder"
                      value-format="YYYY-MM-DD"
                      style="width: 100%"
                      popper-class="form-renderer-popper"
                    />
                  </template>
                  <template v-else-if="field.type === 'datetime'">
                    <el-date-picker
                      v-model="formData[field.key]"
                      type="datetime"
                      :placeholder="field.placeholder"
                      value-format="YYYY-MM-DD HH:mm:ss"
                      style="width: 100%"
                      popper-class="form-renderer-popper"
                    />
                  </template>
                  <template v-else-if="field.type === 'daterange'">
                    <el-date-picker
                      v-model="formData[field.key]"
                      type="daterange"
                      :range-separator="t('common.to')"
                      :start-placeholder="t('common.startDate')"
                      :end-placeholder="t('common.endDate')"
                      value-format="YYYY-MM-DD"
                      style="width: 100%"
                      popper-class="form-renderer-popper"
                    />
                  </template>
                  <template v-else-if="field.type === 'time'">
                    <el-time-picker
                      v-model="formData[field.key]"
                      :placeholder="field.placeholder"
                      value-format="HH:mm:ss"
                      style="width: 100%"
                      popper-class="form-renderer-popper"
                    />
                  </template>
                  <template v-else-if="field.type === 'cascader'">
                    <el-cascader
                      v-model="formData[field.key]"
                      :options="field.options"
                      :props="field.cascaderProps"
                      :placeholder="field.placeholder"
                      clearable
                      style="width: 100%"
                      popper-class="form-renderer-popper"
                    />
                  </template>
                  <template v-else-if="field.type === 'user'">
                    <el-select
                      v-model="formData[field.key]"
                      :placeholder="field.placeholder"
                      :multiple="field.multiple"
                      filterable
                      remote
                      :remote-method="(query: string) => searchUsers(query, field)"
                      clearable
                      style="width: 100%"
                      popper-class="form-renderer-popper"
                    >
                      <el-option
                        v-for="user in field.userOptions || []"
                        :key="user.id"
                        :label="user.name"
                        :value="user.id"
                      />
                    </el-select>
                  </template>
                  <template v-else-if="field.type === 'businessUnit'">
                    <el-tree-select
                      v-model="formData[field.key]"
                      :data="field.buOptions || []"
                      :props="{ label: 'name', children: 'children' }"
                      :placeholder="field.placeholder"
                      check-strictly
                      clearable
                      style="width: 100%"
                      popper-class="form-renderer-popper"
                    />
                  </template>
                  <template v-else-if="field.type === 'money'">
                    <el-input
                      v-model="formData[field.key]"
                      :placeholder="field.placeholder"
                      clearable
                    >
                      <template #prepend>{{ field.currency || '¥' }}</template>
                    </el-input>
                  </template>
                  <template v-else-if="field.type === 'readonly'">
                    <span class="readonly-text">{{ formData[field.key] || '-' }}</span>
                  </template>
                  <template v-else-if="field.type === 'divider'">
                    <el-divider />
                  </template>
                  <template v-else-if="field.type === 'alert'">
                    <el-alert
                      :title="field.alertTitle"
                      :type="field.alertType || 'info'"
                      :closable="false"
                      show-icon
                    />
                  </template>
                  <template v-else>
                    <el-input
                      v-model="formData[field.key]"
                      :placeholder="field.placeholder"
                      clearable
                    />
                  </template>
                </el-form-item>
              </el-col>
            </el-row>
          </el-tab-pane>
        </el-tabs>
      </template>
      
      <!-- 普通平铺模式 -->
      <template v-else>
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
                popper-class="form-renderer-popper"
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
                popper-class="form-renderer-popper"
              />

              <!-- 日期时间选择 -->
              <el-date-picker
                v-else-if="field.type === 'datetime'"
                v-model="formData[field.key]"
                type="datetime"
                :placeholder="field.placeholder"
                value-format="YYYY-MM-DD HH:mm:ss"
                style="width: 100%"
                popper-class="form-renderer-popper"
              />

              <!-- 日期范围 -->
              <el-date-picker
                v-else-if="field.type === 'daterange'"
                v-model="formData[field.key]"
                type="daterange"
                :range-separator="t('common.to')"
                :start-placeholder="t('common.startDate')"
                :end-placeholder="t('common.endDate')"
                value-format="YYYY-MM-DD"
                style="width: 100%"
                popper-class="form-renderer-popper"
              />

              <!-- 时间选择 -->
              <el-time-picker
                v-else-if="field.type === 'time'"
                v-model="formData[field.key]"
                :placeholder="field.placeholder"
                value-format="HH:mm:ss"
                style="width: 100%"
                popper-class="form-renderer-popper"
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
                popper-class="form-renderer-popper"
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
                popper-class="form-renderer-popper"
              >
                <el-option
                  v-for="user in field.userOptions || []"
                  :key="user.id"
                  :label="user.name"
                  :value="user.id"
                />
              </el-select>

              <!-- 业务单元选择器 -->
              <el-tree-select
                v-else-if="field.type === 'businessUnit'"
                v-model="formData[field.key]"
                :data="field.buOptions || []"
                :props="{ label: 'name', children: 'children' }"
                :placeholder="field.placeholder"
                check-strictly
                clearable
                style="width: 100%"
                popper-class="form-renderer-popper"
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
              
              <!-- 默认文本输入 -->
              <el-input
                v-else
                v-model="formData[field.key]"
                :placeholder="field.placeholder"
                clearable
              />
            </el-form-item>
          </el-col>
        </el-row>
      </template>
    </el-form>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import type { FormInstance, FormRules } from 'element-plus'

const { t } = useI18n()

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
  buOptions?: any[]
  rules?: any[]
  defaultValue?: any
  tabName?: string  // 所属 Tab 名称
}

export interface FormTab {
  name: string
  label: string
  fields: FormField[]
}

interface Props {
  fields: FormField[]
  tabs?: FormTab[]  // Tab 配置
  modelValue?: Record<string, any>
  readonly?: boolean
  labelWidth?: string
  size?: 'large' | 'default' | 'small'
}

const props = withDefaults(defineProps<Props>(), {
  modelValue: () => ({}),
  tabs: () => [],
  readonly: false,
  labelWidth: '120px',
  size: 'default'
})

// 是否有 Tab 布局
const hasTabs = computed(() => props.tabs && props.tabs.length > 0)

// 当前激活的 Tab
const activeTab = ref('')

// 初始化激活的 Tab
watch(() => props.tabs, (newTabs) => {
  if (newTabs && newTabs.length > 0 && !activeTab.value) {
    activeTab.value = newTabs[0].name
  }
}, { immediate: true })

const emit = defineEmits<{
  (e: 'update:modelValue', value: Record<string, any>): void
  (e: 'change', key: string, value: any): void
}>()

const formRef = ref<FormInstance>()
const formData = ref<Record<string, any>>({})
let isInternalUpdate = false

// 获取所有字段（包括 tabs 中的字段）
const allFields = computed(() => {
  if (hasTabs.value && props.tabs) {
    return props.tabs.flatMap(tab => tab.fields)
  }
  return props.fields
})

// 初始化表单数据
const initFormData = () => {
  const data: Record<string, any> = {}
  allFields.value.forEach(field => {
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
  isInternalUpdate = true
  formData.value = data
  // 使用 nextTick 确保更新完成后再重置标志
  setTimeout(() => {
    isInternalUpdate = false
  }, 0)
}

// 生成表单验证规则
const formRules = computed<FormRules>(() => {
  const rules: FormRules = {}
  allFields.value.forEach(field => {
    if (field.required || field.rules) {
      const fieldRules: any[] = []
      if (field.required) {
        fieldRules.push({
          required: true,
          message: `请输入${field.label}`,
          trigger: field.type === 'select' ? 'change' : 'blur'
        })
      }
      if (field.rules) {
        const rulesArray = Array.isArray(field.rules) ? field.rules : [field.rules]
        fieldRules.push(...(rulesArray as any[]))
      }
      rules[field.key] = fieldRules
    }
  })
  return rules
})

// 监听表单数据变化 - 只在非内部更新时 emit
watch(formData, (newVal) => {
  if (!isInternalUpdate) {
    emit('update:modelValue', { ...newVal })
  }
}, { deep: true })

// 监听外部数据变化 - 只在有实际变化时更新
watch(() => props.modelValue, (newVal, oldVal) => {
  // 避免不必要的更新
  if (JSON.stringify(newVal) !== JSON.stringify(oldVal)) {
    initFormData()
  }
}, { deep: true })

// 用户搜索
const searchUsers = async (query: string, _field: FormField) => {
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
  width: 100%;
  
  .readonly-text {
    color: #606266;
    line-height: 32px;
  }

  :deep(.el-form-item__label) {
    font-weight: 500;
  }
  
  :deep(.el-tabs--border-card) {
    border-radius: 4px;
    width: 100%;
    
    .el-tabs__header {
      background-color: #f5f7fa;
    }
    
    .el-tabs__content {
      padding: 20px;
    }
  }
  
  :deep(.el-form) {
    width: 100%;
  }
}
</style>

<style lang="scss">
/* 全局样式，确保弹出框正确显示 */
.form-renderer-popper {
  z-index: 3000 !important;
}
</style>
