<template>
  <div class="form-renderer">
    <el-form
      ref="formRef"
      :model="formData"
      :rules="formRules"
      :label-width="labelWidth"
      :label-position="labelPosition"
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
              <template v-for="field in tab.fields" :key="field.key">
                <template v-if="field.type === 'subTable'">
                  <el-col :span="24" style="padding: 0;">
                  <SubTableField
                    v-if="resolveBinding(field._bindingId)"
                    :title="resolveBinding(field._bindingId)!.tableName"
                    :columns="resolveBinding(field._bindingId)!.columns"
                    :model-value="resolveBinding(field._bindingId)!.data"
                    :editable="!readonly && resolveBinding(field._bindingId)!.bindingMode === 'EDITABLE'"
                    @update:model-value="(rows: any[]) => emit('update:subTableData', field._bindingId!, rows)"
                    style="margin-bottom: 16px;"
                  />
                  </el-col>
                </template>
                <el-col v-else :key="field.key" :span="field.span || 24">
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
                  <template v-else-if="field.type === 'password'">
                    <el-input
                      v-model="formData[field.key]"
                      type="password"
                      show-password
                      :placeholder="field.placeholder"
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
                  <template v-else-if="field.type === 'timerange'">
                    <el-time-picker
                      v-model="formData[field.key]"
                      is-range
                      value-format="HH:mm:ss"
                      :start-placeholder="(field as any).startPlaceholder || t('common.startDate')"
                      :end-placeholder="(field as any).endPlaceholder || t('common.endDate')"
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
                      :props="{ label: 'name', value: 'id', children: 'children' }"
                      :placeholder="field.placeholder"
                      check-strictly
                      clearable
                      style="width: 100%"
                      popper-class="form-renderer-popper"
                    />
                  </template>
                  <template v-else-if="field.type === 'treeselect'">
                    <el-tree-select
                      v-model="formData[field.key]"
                      :data="(field as any).treeData || []"
                      :multiple="field.multiple"
                      :check-strictly="(field as any).checkStrictly !== false"
                      :placeholder="field.placeholder"
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
                  <template v-else-if="field.type === 'rate'">
                    <el-rate v-model="formData[field.key]" :max="field.max || 5" />
                  </template>
                  <template v-else-if="field.type === 'slider'">
                    <el-slider v-model="formData[field.key]" :min="field.min || 0" :max="field.max || 100" :step="field.step || 1" style="width: 100%" />
                  </template>
                  <template v-else-if="field.type === 'colorPicker'">
                    <span v-if="readonly && formData[field.key]" class="color-swatch" :style="{ backgroundColor: formData[field.key] }" :title="formData[field.key]" />
                    <span v-else-if="readonly">-</span>
                    <el-color-picker v-else v-model="formData[field.key]" />
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
                  <template v-else-if="field.type === 'upload'">
                    <el-upload
                      v-if="!readonly"
                      :action="(field.uploadUrl && field.uploadUrl !== '/') ? field.uploadUrl : '/api/v1/upload'"
                      :accept="field.uploadAccept || '.jpg,.jpeg,.png,.pdf,.docx,.xlsx'"
                      :limit="field.uploadLimit || 1"
                      :multiple="false"
                      :file-list="uploadFileLists[field.key] || []"
                      :on-success="(res: any, file: any) => handleUploadSuccess(res, file, field.key)"
                      :on-remove="(file: any) => handleUploadRemove(file, field.key)"
                      list-type="text"
                    >
                      <el-button type="primary">
                        <el-icon><Upload /></el-icon>
                        {{ $t('upload.selectFile') }}
                      </el-button>
                    </el-upload>
                    <div v-else>
                      <a v-if="formData[field.key]" :href="formData[field.key]" target="_blank">
                        {{ uploadFileLists[field.key]?.[0]?.name || formData[field.key] }}
                      </a>
                      <span v-else>-</span>
                    </div>
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
              </template>
            </el-row>
          </el-tab-pane>
        </el-tabs>
      </template>
      
      <!-- 普通平铺模式 -->
      <template v-else>
        <el-row :gutter="20">
          <template v-for="field in fields" :key="field.key">
            <template v-if="field.type === 'subTable'">
              <el-col :span="24" style="padding: 0;">
              <SubTableField
                v-if="resolveBinding(field._bindingId)"
                :title="resolveBinding(field._bindingId)!.tableName"
                :columns="resolveBinding(field._bindingId)!.columns"
                :model-value="resolveBinding(field._bindingId)!.data"
                :editable="!readonly && resolveBinding(field._bindingId)!.bindingMode === 'EDITABLE'"
                @update:model-value="(rows: any[]) => emit('update:subTableData', field._bindingId!, rows)"
                style="margin-bottom: 16px;"
              />
              </el-col>
            </template>
            <el-col v-else :key="field.key" :span="field.span || 24">
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

              <!-- 密码输入 -->
              <el-input
                v-else-if="field.type === 'password'"
                v-model="formData[field.key]"
                type="password"
                show-password
                :placeholder="field.placeholder"
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

              <!-- 时间范围 -->
              <el-time-picker
                v-else-if="field.type === 'timerange'"
                v-model="formData[field.key]"
                is-range
                value-format="HH:mm:ss"
                :start-placeholder="(field as any).startPlaceholder || t('common.startDate')"
                :end-placeholder="(field as any).endPlaceholder || t('common.endDate')"
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
                :props="{ label: 'name', value: 'id', children: 'children' }"
                :placeholder="field.placeholder"
                check-strictly
                clearable
                style="width: 100%"
                popper-class="form-renderer-popper"
              />

              <!-- 树形下拉选择器 -->
              <el-tree-select
                v-else-if="field.type === 'treeselect'"
                v-model="formData[field.key]"
                :data="(field as any).treeData || []"
                :multiple="field.multiple"
                :check-strictly="(field as any).checkStrictly !== false"
                :placeholder="field.placeholder"
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

              <!-- 评分 -->
              <el-rate
                v-else-if="field.type === 'rate'"
                v-model="formData[field.key]"
                :max="field.max || 5"
              />

              <!-- 滑块 -->
              <el-slider
                v-else-if="field.type === 'slider'"
                v-model="formData[field.key]"
                :min="field.min || 0"
                :max="field.max || 100"
                :step="field.step || 1"
                style="width: 100%"
              />

              <!-- 颜色选择器 -->
              <template v-else-if="field.type === 'colorPicker'">
                <span v-if="readonly && formData[field.key]" class="color-swatch" :style="{ backgroundColor: formData[field.key] }" :title="formData[field.key]" />
                <span v-else-if="readonly">-</span>
                <el-color-picker v-else v-model="formData[field.key]" />
              </template>

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

              <!-- 文件上传 -->
              <template v-else-if="field.type === 'upload'">
                <el-upload
                  v-if="!readonly"
                  :action="(field.uploadUrl && field.uploadUrl !== '/') ? field.uploadUrl : '/api/v1/upload'"
                  :accept="field.uploadAccept || '.jpg,.jpeg,.png,.pdf,.docx,.xlsx'"
                  :limit="field.uploadLimit || 1"
                  :multiple="false"
                  :file-list="uploadFileLists[field.key] || []"
                  :on-success="(res: any, file: any) => handleUploadSuccess(res, file, field.key)"
                  :on-remove="(file: any) => handleUploadRemove(file, field.key)"
                  list-type="text"
                >
                  <el-button type="primary">
                    <el-icon><Upload /></el-icon>
                    {{ $t('upload.selectFile') }}
                  </el-button>
                  <template #tip>
                    <div class="el-upload__tip">{{ field.uploadAccept || '.jpg/.png/.pdf/.docx/.xlsx' }}</div>
                  </template>
                </el-upload>
                <div v-else>
                  <a v-if="formData[field.key]" :href="formData[field.key]" target="_blank">
                    {{ uploadFileLists[field.key]?.[0]?.name || formData[field.key] }}
                  </a>
                  <span v-else>-</span>
                </div>
              </template>

              <!-- 默认文本输入 -->
              <el-input
                v-else
                v-model="formData[field.key]"
                :placeholder="field.placeholder"
                clearable
              />
            </el-form-item>
            </el-col>
          </template>
        </el-row>
      </template>
    </el-form>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { Upload } from '@element-plus/icons-vue'
import type { FormInstance, FormRules } from 'element-plus'
import SubTableField from './SubTableField.vue'
import type { FormField, FormTab } from './formRendererHelpers'
import { extractFieldsRecursive } from './formRendererHelpers'

export type { FormField, FormTab }

const { t } = useI18n()

interface SubTableBinding {
  bindingId: number
  bindingType: string
  bindingMode: string
  tableName: string
  tableType: string
  tableDescription: string
  columns: Array<{ field: string; label: string; type?: string; [key: string]: any }>
  data: any[]
}

interface Props {
  fields: FormField[]
  tabs?: FormTab[]  // Tab 配置
  modelValue?: Record<string, any>
  readonly?: boolean
  labelWidth?: string
  labelPosition?: 'left' | 'right' | 'top'
  size?: 'large' | 'default' | 'small'
  subTableBindings?: SubTableBinding[]
}

const props = withDefaults(defineProps<Props>(), {
  modelValue: () => ({}),
  tabs: () => [],
  readonly: false,
  labelWidth: '160px',
  labelPosition: 'left',
  size: 'default',
  subTableBindings: () => []
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
  (e: 'update:subTableData', bindingId: number, rows: any[]): void
}>()

const formRef = ref<FormInstance>()

const bindingMap = computed(() => {
  const map = new Map<number, SubTableBinding>()
  for (const b of (props.subTableBindings ?? [])) map.set(b.bindingId, b)
  return map
})
const resolveBinding = (id?: number) => id != null ? bindingMap.value.get(id) : undefined

const formData = ref<Record<string, any>>({})
let isInternalUpdate = false

// 独立管理文件上传列表，避免从 formData 派生导致的重渲染问题
const uploadFileLists = ref<Record<string, Array<{ name: string; url: string; uid?: number }>>>({})

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
    // 初始化文件上传列表（外部传入已有值时，从 URL 提取文件名显示）
    if (field.type === 'upload' && data[field.key]) {
      const url = data[field.key]
      const fileName = decodeURIComponent(url.split('/').pop() || url)
      uploadFileLists.value[field.key] = [{ name: fileName, url }]
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
      rules[field.key] = []
      if (field.required) {
        rules[field.key].push({
          required: true,
          message: t('common.pleaseInput', { label: field.label }),
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

// 监听表单数据变化 - 只在非内部更新且非只读时 emit
// readonly 模式下绝不 emit，防止多个 FormRenderer 共享同一 v-model 时
// 各自 initFormData 只含自身字段的子集，emit 回去会覆盖掉其他表单的数据
watch(formData, (newVal) => {
  if (!isInternalUpdate && !props.readonly) {
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

// 监听字段变化 - 当 fields 在 modelValue 之后加载时（如任务详情先设置数据再解析表单），重新初始化
watch(allFields, (newFields, oldFields) => {
  if (newFields.length !== oldFields.length || JSON.stringify(newFields.map(f => f.key)) !== JSON.stringify(oldFields.map(f => f.key))) {
    initFormData()
  }
})

// 用户搜索
const searchUsers = async (query: string, field: FormField) => {
  if (query.length < 2) return
  // 这里可以调用API搜索用户
  // const users = await userApi.search(query)
  // field.userOptions = users
}

// 文件上传成功
const handleUploadSuccess = (response: any, file: any, fieldKey: string) => {
  const url = response?.data?.url || ''
  formData.value[fieldKey] = url
  // 用原始文件名 + 服务器 URL 更新文件列表，不从 formData 派生避免重渲染清除列表
  uploadFileLists.value[fieldKey] = [{ name: file.name, url, uid: file.uid }]
  emit('update:modelValue', { ...formData.value })
}

// 文件删除
const handleUploadRemove = (_file: any, fieldKey: string) => {
  formData.value[fieldKey] = ''
  uploadFileLists.value[fieldKey] = []
  emit('update:modelValue', { ...formData.value })
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
    white-space: nowrap;
    padding-right: 16px;
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

  .color-swatch {
    display: inline-block;
    width: 20px;
    height: 20px;
    border-radius: 3px;
    border: 1px solid #dcdfe6;
    vertical-align: middle;
  }
}
</style>

<style lang="scss">
/* 全局样式，确保弹出框正确显示（包括在 el-dialog 内） */
.form-renderer-popper {
  z-index: 9999 !important;
}
</style>
