<template>
  <!-- 自定义遮罩：z-index 固定低于 picker popper，点击关闭 dialog -->
  <div v-if="visible" class="sub-table-backdrop" @click="handleClose" />
  <el-dialog
    :model-value="visible"
    :title="title || (mode === 'edit' ? t('subTable.editRecord') : t('subTable.addRecord'))"
    width="600px"
    :close-on-click-modal="false"
    :modal="false"
    :z-index="2010"
    append-to-body
    @update:model-value="handleClose"
    @close="handleClose"
  >
    <el-form
      ref="formRef"
      :model="formData"
      :rules="formRules"
      label-width="120px"
      label-position="left"
    >
      <el-form-item
        v-for="col in columns"
        :key="col.field"
        :label="col.label"
        :prop="col.field"
        :error="columnErrors[col.field]?.join('; ')"
      >
        <!-- text -->
        <el-input
          v-if="!col.type || col.type === 'text'"
          v-model="formData[col.field]"
          :placeholder="col.placeholder || col.label"
          :maxlength="col.props?.maxlength"
          :disabled="calculatedColumns.has(col.field)"
          clearable
        />

        <!-- textarea -->
        <el-input
          v-else-if="col.type === 'textarea'"
          v-model="formData[col.field]"
          type="textarea"
          :rows="col.props?.rows || 3"
          :placeholder="col.placeholder || col.label"
          :maxlength="col.props?.maxlength"
        />

        <!-- number -->
        <el-input-number
          v-else-if="col.type === 'number'"
          v-model="formData[col.field]"
          :precision="col.props?.precision"
          :min="col.props?.min"
          :max="col.props?.max"
          :placeholder="col.placeholder || col.label"
          :disabled="calculatedColumns.has(col.field)"
          style="width: 100%"
        />

        <!-- select -->
        <el-select
          v-else-if="col.type === 'select'"
          v-model="formData[col.field]"
          :placeholder="col.placeholder || col.label"
          :multiple="col.props?.multiple"
          clearable
          :teleported="true"
          style="width: 100%"
        >
          <el-option
            v-for="opt in (col.props?.options ?? col.options ?? [])"
            :key="opt.value"
            :label="opt.label"
            :value="opt.value"
          />
        </el-select>

        <!-- radio -->
        <el-radio-group
          v-else-if="col.type === 'radio'"
          v-model="formData[col.field]"
        >
          <el-radio
            v-for="opt in (col.props?.options ?? col.options ?? [])"
            :key="opt.value"
            :value="opt.value"
          >{{ opt.label }}</el-radio>
        </el-radio-group>

        <!-- checkbox -->
        <el-checkbox-group
          v-else-if="col.type === 'checkbox'"
          v-model="formData[col.field]"
        >
          <el-checkbox
            v-for="opt in (col.props?.options ?? col.options ?? [])"
            :key="opt.value"
            :value="opt.value"
          >{{ opt.label }}</el-checkbox>
        </el-checkbox-group>

        <!-- password -->
        <el-input
          v-else-if="col.type === 'password'"
          v-model="formData[col.field]"
          type="password"
          show-password
          :placeholder="col.placeholder || col.label"
          clearable
        />

        <!-- timerange -->
        <el-time-picker
          v-else-if="col.type === 'timerange'"
          v-model="formData[col.field]"
          is-range
          value-format="HH:mm:ss"
          :start-placeholder="col.props?.startPlaceholder || t('subTable.startTime')"
          :end-placeholder="col.props?.endPlaceholder || t('subTable.endTime')"
          :teleported="true"
          popper-class="sub-table-date-popper"
          style="width: 100%"
        />

        <!-- treeselect -->
        <el-tree-select
          v-else-if="col.type === 'treeselect'"
          v-model="formData[col.field]"
          :data="col.props?.treeData || []"
          :multiple="col.props?.multiple"
          :check-strictly="col.props?.checkStrictly !== false"
          :placeholder="col.placeholder || col.label"
          clearable
          :teleported="true"
          style="width: 100%"
        />

        <!-- tree (el-tree with checkbox, uses id/label node format) -->
        <el-tree
          v-else-if="col.type === 'tree'"
          :data="col.props?.treeData || []"
          :props="col.props?.labelProps || { label: 'label', children: 'children' }"
          :node-key="col.props?.nodeKey || 'id'"
          :show-checkbox="col.props?.showCheckbox !== false"
          @check="(node: any, state: any) => { formData[col.field] = state.checkedKeys }"
        />

        <!-- switch -->
        <el-switch
          v-else-if="col.type === 'switch'"
          v-model="formData[col.field]"
        />

        <!-- date -->
        <el-date-picker
          v-else-if="col.type === 'date'"
          v-model="formData[col.field]"
          type="date"
          value-format="YYYY-MM-DD"
          :placeholder="col.placeholder || col.label"
          :teleported="true"
          popper-class="sub-table-date-popper"
          style="width: 100%"
        />

        <!-- datetime -->
        <el-date-picker
          v-else-if="col.type === 'datetime'"
          v-model="formData[col.field]"
          type="datetime"
          value-format="YYYY-MM-DD HH:mm:ss"
          :placeholder="col.placeholder || col.label"
          :teleported="true"
          popper-class="sub-table-date-popper"
          style="width: 100%"
        />

        <!-- upload -->
        <div v-else-if="col.type === 'upload'" style="display: flex; flex-direction: column; gap: 4px;">
          <el-upload
            :action="col.props?.action && col.props.action !== '/' ? col.props.action : (uploadUrl || '/api/v1/upload')"
            :accept="col.props?.accept || '.jpg,.jpeg,.png,.pdf,.docx,.xlsx'"
            :show-file-list="false"
            :on-success="(res: any, file: any) => handleUploadSuccess(res, file, col)"
            :on-error="() => handleUploadError(col)"
          >
            <el-button size="small" type="primary">
              <el-icon><Upload /></el-icon> {{ t('subTable.upload') }}
            </el-button>
          </el-upload>
          <el-tag
            v-if="uploadNames[col.field]"
            size="small"
            type="success"
            closable
            @close="clearUpload(col)"
          >
            {{ uploadNames[col.field] }}
          </el-tag>
        </div>

        <!-- colorPicker -->
        <el-color-picker
          v-else-if="col.type === 'colorPicker'"
          v-model="formData[col.field]"
          :show-alpha="col.props?.showAlpha || false"
          popper-class="sub-table-color-popper"
        />

        <!-- rate -->
        <el-rate
          v-else-if="col.type === 'rate'"
          v-model="formData[col.field]"
          :max="col.props?.max || 5"
          :allow-half="col.props?.allowHalf || false"
        />

        <!-- slider -->
        <el-slider
          v-else-if="col.type === 'slider'"
          v-model="formData[col.field]"
          :min="col.props?.min ?? 0"
          :max="col.props?.max ?? 100"
          :step="col.props?.step || 1"
          style="width: 100%"
        />

        <!-- editor (rich text — wangeditor, consistent with FieldRenderer) -->
        <div v-else-if="col.type === 'editor'" class="sub-table-editor-wrapper">
          <Toolbar
            :editor="editorInstances[col.field]"
            :default-config="{}"
            mode="simple"
            style="border-bottom: 1px solid #ccc"
          />
          <Editor
            :model-value="formData[col.field] || ''"
            :default-config="{ placeholder: col.placeholder || col.label }"
            mode="simple"
            style="height: 200px; overflow-y: hidden"
            @on-created="(editor: any) => onEditorCreated(editor, col.field)"
            @on-change="(editor: any) => onEditorChange(editor, col.field)"
          />
        </div>

        <!-- signature (base64 image URL input) -->
        <div v-else-if="col.type === 'signature'" style="width: 100%;">
          <canvas
            :ref="(el: any) => { if (el) signatureCanvasRefs[col.field] = el }"
            class="signature-canvas"
            @mousedown="startSign($event, col.field)"
            @mousemove="drawSign($event, col.field)"
            @mouseup="endSign(col.field)"
            @mouseleave="endSign(col.field)"
            @touchstart.prevent="startSignTouch($event, col.field)"
            @touchmove.prevent="drawSignTouch($event, col.field)"
            @touchend="endSign(col.field)"
          />
          <div style="margin-top: 4px;">
            <el-button size="small" @click="clearSignature(col.field)">{{ t('fieldRenderer.clear') }}</el-button>
          </div>
        </div>

        <!-- transfer -->
        <el-transfer
          v-else-if="col.type === 'transfer'"
          v-model="formData[col.field]"
          :data="(col.props?.options ?? col.options ?? []).map((o: any) => ({ key: o.value, label: o.label }))"
          :titles="[col.props?.leftTitle || t('subTable.transferSource'), col.props?.rightTitle || t('subTable.transferTarget')]"
          filterable
        />

        <!-- cascader -->
        <el-cascader
          v-else-if="col.type === 'cascader'"
          v-model="formData[col.field]"
          :options="col.props?.options ?? col.options ?? []"
          :props="col.props?.cascaderProps"
          :placeholder="col.placeholder || col.label"
          clearable
          :teleported="true"
          popper-class="sub-table-date-popper"
          style="width: 100%"
        />

        <!-- user — remote search select (consistent with FieldRenderer) -->
        <el-select
          v-else-if="col.type === 'user'"
          v-model="formData[col.field]"
          :placeholder="col.placeholder || t('subTable.selectUser')"
          filterable
          remote
          :remote-method="(query: string) => handleUserSearch(query, col.field)"
          :loading="userSearchLoading[col.field]"
          clearable
          :teleported="true"
          style="width: 100%"
        >
          <el-option
            v-for="user in (userSearchOptions[col.field] || [])"
            :key="user.id"
            :label="user.name"
            :value="user.id"
          />
        </el-select>

        <!-- department — tree select (consistent with FieldRenderer) -->
        <el-tree-select
          v-else-if="col.type === 'department'"
          v-model="formData[col.field]"
          :data="departmentTreeData"
          :props="({ label: 'name', value: 'id', children: 'children' } as any)"
          :placeholder="col.placeholder || t('subTable.selectDepartment')"
          :loading="departmentLoading"
          check-strictly
          clearable
          filterable
          :teleported="true"
          style="width: 100%"
        />

        <!-- fallback -->
        <el-input
          v-else
          v-model="formData[col.field]"
          :placeholder="col.placeholder || col.label"
          clearable
        />
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="handleClose">{{ t('common.cancel') }}</el-button>
      <el-button type="primary" @click="handleSave">{{ t('common.save') }}</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, watch, computed, nextTick, shallowRef, onBeforeUnmount, inject } from 'vue'
import { useI18n } from 'vue-i18n'
import { Upload } from '@element-plus/icons-vue'
import type { FormInstance } from 'element-plus'
import { ElMessage } from 'element-plus'
import { Editor, Toolbar } from '@wangeditor/editor-for-vue'
import '@wangeditor/editor/dist/css/style.css'
import { buildInitialRow, buildRules } from './subTableAddDialogHelpers'
import type { DialogColumn } from './subTableAddDialogHelpers'
import type { RowFormulaRule, ValidationRule } from './formRendererHelpers'
import { evaluateFormula, validateField } from './businessLogicEngine'

// ─── Component ────────────────────────────────────────────────────────────────

const { t } = useI18n()

const props = defineProps<{
  visible: boolean
  columns: DialogColumn[]
  title?: string
  mode: 'add' | 'edit'
  initialData?: Record<string, any>
  rowFormulas?: RowFormulaRule[]
  columnValidationRules?: Record<string, ValidationRule[]>
  uploadUrl?: string
}>()

const emit = defineEmits<{
  (e: 'update:visible', val: boolean): void
  (e: 'save', rowData: Record<string, any>): void
}>()

const formRef = ref<FormInstance>()
const formData = ref<Record<string, any>>({})
const uploadNames = ref<Record<string, string>>({})

// ─── Signature canvas state ───────────────────────────────────────────────────
const signatureCanvasRefs = ref<Record<string, HTMLCanvasElement>>({})
const signingField = ref<string | null>(null)

// ─── Editor (wangeditor) state ────────────────────────────────────────────────
const editorInstances = shallowRef<Record<string, any>>({})

function onEditorCreated(editor: any, field: string) {
  editorInstances.value = { ...editorInstances.value, [field]: editor }
}

function onEditorChange(editor: any, field: string) {
  formData.value[field] = editor.getHtml()
}

function destroyEditors() {
  for (const editor of Object.values(editorInstances.value)) {
    if (editor && typeof editor.destroy === 'function') {
      editor.destroy()
    }
  }
  editorInstances.value = {}
}

onBeforeUnmount(() => {
  destroyEditors()
})

// ─── User search state (Req 37.1) ────────────────────────────────────────────
const userSearchOptions = ref<Record<string, Array<{ id: string; name: string }>>>({})
const userSearchLoading = ref<Record<string, boolean>>({})

async function handleUserSearch(query: string, field: string) {
  if (query.length < 2) return
  userSearchLoading.value = { ...userSearchLoading.value, [field]: true }
  try {
    const { userApi } = await import('@/api/user')
    const results = await userApi.searchUsers(query)
    userSearchOptions.value = { ...userSearchOptions.value, [field]: results }
  } catch {
    userSearchOptions.value = { ...userSearchOptions.value, [field]: [] }
  } finally {
    userSearchLoading.value = { ...userSearchLoading.value, [field]: false }
  }
}

// ─── Department tree state (Req 37.2) ─────────────────────────────────────────

const departmentTreeData = ref<any[]>([])
const departmentLoading = ref(false)

// Use injected shared cache from FormRenderer if available
const sharedDepartmentData = inject<typeof departmentTreeData>('departmentTreeData', undefined)
const sharedDepartmentLoading = inject<typeof departmentLoading>('departmentTreeLoading', undefined)

async function fetchDepartmentTree() {
  if (sharedDepartmentData?.value && sharedDepartmentData.value.length > 0) {
    departmentTreeData.value = sharedDepartmentData.value
    return
  }
  if (departmentTreeData.value.length > 0) return
  departmentLoading.value = true
  if (sharedDepartmentLoading) sharedDepartmentLoading.value = true
  try {
    const api = (await import('@/api/request')).default
    const res = await api.get('/api/portal/departments/tree')
    const data = res.data?.data ?? res.data ?? []
    departmentTreeData.value = data
    if (sharedDepartmentData) sharedDepartmentData.value = data
  } catch (err) {
    console.warn('[SubTableAddDialog] Failed to fetch department tree:', err)
  } finally {
    departmentLoading.value = false
    if (sharedDepartmentLoading) sharedDepartmentLoading.value = false
  }
}

function startSign(e: MouseEvent, field: string) {
  signingField.value = field
  const canvas = signatureCanvasRefs.value[field]
  if (!canvas) return
  const ctx = canvas.getContext('2d')
  if (!ctx) return
  const rect = canvas.getBoundingClientRect()
  ctx.beginPath()
  ctx.moveTo(e.clientX - rect.left, e.clientY - rect.top)
}

function drawSign(e: MouseEvent, field: string) {
  if (signingField.value !== field) return
  const canvas = signatureCanvasRefs.value[field]
  if (!canvas) return
  const ctx = canvas.getContext('2d')
  if (!ctx) return
  const rect = canvas.getBoundingClientRect()
  ctx.lineWidth = 2
  ctx.lineCap = 'round'
  ctx.strokeStyle = '#000'
  ctx.lineTo(e.clientX - rect.left, e.clientY - rect.top)
  ctx.stroke()
}

function endSign(field: string) {
  if (signingField.value !== field) return
  signingField.value = null
  const canvas = signatureCanvasRefs.value[field]
  if (!canvas) return
  formData.value[field] = canvas.toDataURL('image/png')
}

function clearSignature(field: string) {
  const canvas = signatureCanvasRefs.value[field]
  if (!canvas) return
  const ctx = canvas.getContext('2d')
  if (!ctx) return
  ctx.clearRect(0, 0, canvas.width, canvas.height)
  formData.value[field] = ''
}

// Touch event handlers for mobile signature support (Req 42)
function startSignTouch(e: TouchEvent, field: string) {
  signingField.value = field
  const canvas = signatureCanvasRefs.value[field]
  if (!canvas) return
  const ctx = canvas.getContext('2d')
  if (!ctx) return
  const rect = canvas.getBoundingClientRect()
  const touch = e.touches[0]
  ctx.beginPath()
  ctx.moveTo(touch.clientX - rect.left, touch.clientY - rect.top)
}

function drawSignTouch(e: TouchEvent, field: string) {
  if (signingField.value !== field) return
  const canvas = signatureCanvasRefs.value[field]
  if (!canvas) return
  const ctx = canvas.getContext('2d')
  if (!ctx) return
  const rect = canvas.getBoundingClientRect()
  const touch = e.touches[0]
  ctx.lineWidth = 2
  ctx.lineCap = 'round'
  ctx.strokeStyle = '#000'
  ctx.lineTo(touch.clientX - rect.left, touch.clientY - rect.top)
  ctx.stroke()
}

const formRules = computed(() => buildRules(props.columns))

// ─── Row formula calculation (Task 8.6) ───────────────────────────────────────
const calculatedColumns = computed(() => {
  if (!props.rowFormulas?.length) return new Set<string>()
  return new Set(props.rowFormulas.map(f => f.targetColumn))
})

// Watch dependent column values and compute target columns
watch(
  () => {
    if (!props.rowFormulas?.length) return null
    // Collect all dependent field values to trigger reactivity
    const deps: Record<string, unknown> = {}
    for (const formula of props.rowFormulas!) {
      for (const dep of formula.dependsOn) {
        deps[dep] = formData.value[dep]
      }
    }
    return deps
  },
  () => {
    if (!props.rowFormulas?.length) return
    for (const formula of props.rowFormulas!) {
      const fieldValues: Record<string, unknown> = {}
      for (const dep of formula.dependsOn) {
        fieldValues[dep] = formData.value[dep]
      }
      formData.value[formula.targetColumn] = evaluateFormula(formula.expression, fieldValues)
    }
  },
  { deep: true }
)

// ─── Column validation errors (Task 8.7) ──────────────────────────────────────
const columnErrors = ref<Record<string, string[]>>({})

function validateColumns(): boolean {
  columnErrors.value = {}
  if (!props.columnValidationRules) return true
  let allValid = true
  for (const [colName, rules] of Object.entries(props.columnValidationRules)) {
    const errors = validateField(formData.value[colName], rules)
    if (errors.length > 0) {
      columnErrors.value[colName] = errors
      allValid = false
    }
  }
  return allValid
}

// dialog 打开后，把它的 overlay z-index 降低到固定值，
// 确保 picker popper（动态分配更高 z-index）能显示在 overlay 上面

function fixOverlayZIndex() {
  // 找到本 dialog 的 overlay（class 包含 el-overlay-dialog）并降低其 z-index
  nextTick(() => {
    const overlays = document.querySelectorAll('.el-overlay-dialog')
    if (overlays.length > 0) {
      const lastOverlay = overlays[overlays.length - 1] as HTMLElement
      lastOverlay.style.setProperty('z-index', '2010', 'important')
    }
  })
}
// Initialise / reset form whenever dialog opens
watch(
  () => props.visible,
  (open) => {
    if (!open) return
    fixOverlayZIndex()
    uploadNames.value = {}
    columnErrors.value = {}
    // Fetch department tree if any column is of type 'department'
    if (props.columns.some(c => c.type === 'department')) {
      fetchDepartmentTree()
    }
    if (props.mode === 'edit' && props.initialData) {
      // Deep-clone to avoid mutating the original row
      formData.value = { ...buildInitialRow(props.columns), ...JSON.parse(JSON.stringify(props.initialData)) }
      // Back-fill upload file names from URL
      for (const col of props.columns) {
        if (col.type === 'upload' && formData.value[col.field]) {
          const url: string = formData.value[col.field]
          uploadNames.value[col.field] = url.split('/').pop() || url
        }
      }
    } else {
      formData.value = buildInitialRow(props.columns)
    }
  },
  { immediate: false }
)

function handleClose() {
  destroyEditors()
  formRef.value?.resetFields()
  emit('update:visible', false)
}

async function handleSave() {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  // Run column validation rules (Task 8.7)
  if (!validateColumns()) return
  emit('save', { ...formData.value })
  emit('update:visible', false)
}

// ─── Upload helpers ───────────────────────────────────────────────────────────

function handleUploadSuccess(res: any, file: any, col: DialogColumn) {
  const url: string = res?.data?.url || ''
  formData.value[col.field] = url
  uploadNames.value = { ...uploadNames.value, [col.field]: file.name }
  // Auto-fill filename to the configured target column (if any)
  const target = col.props?.fileNameTargetField
  if (target && props.columns.some(c => c.field === target)) {
    formData.value[target] = file.name
  }
}

function handleUploadError(col: DialogColumn) {
  ElMessage.error(t('subTable.uploadFailed', { field: col.label }))
}

function clearUpload(col: DialogColumn) {
  formData.value[col.field] = ''
  const next = { ...uploadNames.value }
  delete next[col.field]
  uploadNames.value = next
}
</script>

<style>
/* 自定义遮罩，z-index 固定在 2009，低于 dialog(2010) 和 picker popper(动态更高) */
.sub-table-backdrop {
  position: fixed;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  background: rgba(0, 0, 0, 0.5);
  z-index: 2009;
}

/* Scoped popper styles using popper-class (Req 31) */
.sub-table-date-popper {
  z-index: 2050;
}

.sub-table-color-popper {
  z-index: 2050;
}

.signature-canvas {
  width: 100%;
  height: 120px;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  cursor: crosshair;
  background: #fff;
}

.sub-table-editor-wrapper {
  border: 1px solid #ccc;
  border-radius: 4px;
  overflow: hidden;
  width: 100%;
}
</style>
