<template>
  <!-- 占满表单项内容区，否则 el-select 等 width:100% 会相对收缩父级计算，出现仅显示箭头 -->
  <div v-show="visible" class="field-renderer-root">
    <!-- text / input -->
    <template v-if="field.type === 'text' || field.type === 'input'">
      <el-input
        :model-value="modelValue"
        :placeholder="field.placeholder"
        :maxlength="field.maxLength"
        :show-word-limit="!!field.maxLength"
        :disabled="disabled"
        clearable
        @update:model-value="onUpdate"
      />
    </template>

    <!-- password -->
    <template v-else-if="field.type === 'password'">
      <el-input
        :model-value="modelValue"
        type="password"
        show-password
        :placeholder="field.placeholder"
        :disabled="disabled"
        clearable
        @update:model-value="onUpdate"
      />
    </template>

    <!-- textarea -->
    <template v-else-if="field.type === 'textarea'">
      <el-input
        :model-value="modelValue"
        type="textarea"
        :rows="field.rows || 3"
        :placeholder="field.placeholder"
        :maxlength="field.maxLength"
        :show-word-limit="!!field.maxLength"
        :disabled="disabled"
        @update:model-value="onUpdate"
      />
    </template>

    <!-- number -->
    <template v-else-if="field.type === 'number'">
      <el-input-number
        :model-value="modelValue"
        :min="field.min"
        :max="field.max"
        :step="field.step || 1"
        :precision="field.precision"
        :disabled="disabled"
        style="width: 100%"
        @update:model-value="onUpdate"
      />
    </template>

    <!-- select -->
    <template v-else-if="field.type === 'select'">
      <el-select
        :model-value="modelValue"
        :placeholder="field.placeholder"
        :multiple="field.multiple"
        :filterable="field.filterable"
        :disabled="disabled"
        clearable
        style="width: 100%"
        popper-class="form-renderer-popper"
        @update:model-value="onUpdate"
      >
        <el-option
          v-for="opt in resolvedOptions"
          :key="opt.value"
          :label="opt.label"
          :value="opt.value"
        />
      </el-select>
    </template>

    <!-- radio -->
    <template v-else-if="field.type === 'radio'">
      <el-radio-group
        :model-value="modelValue"
        :disabled="disabled"
        @update:model-value="onUpdate"
      >
        <el-radio
          v-for="opt in resolvedOptions"
          :key="opt.value"
          :label="opt.value"
        >
          {{ opt.label }}
        </el-radio>
      </el-radio-group>
    </template>

    <!-- checkbox -->
    <template v-else-if="field.type === 'checkbox'">
      <el-checkbox-group
        :model-value="modelValue"
        :disabled="disabled"
        @update:model-value="onUpdate"
      >
        <el-checkbox
          v-for="opt in resolvedOptions"
          :key="opt.value"
          :label="opt.value"
        >
          {{ opt.label }}
        </el-checkbox>
      </el-checkbox-group>
    </template>

    <!-- switch -->
    <template v-else-if="field.type === 'switch'">
      <el-switch
        :model-value="modelValue"
        :active-text="field.activeText"
        :inactive-text="field.inactiveText"
        :disabled="disabled"
        @update:model-value="onUpdate"
      />
    </template>

    <!-- date -->
    <template v-else-if="field.type === 'date'">
      <el-date-picker
        :model-value="modelValue"
        type="date"
        :placeholder="field.placeholder"
        :disabled="disabled"
        value-format="YYYY-MM-DD"
        style="width: 100%"
        popper-class="form-renderer-popper"
        @update:model-value="onUpdate"
      />
    </template>

    <!-- datetime -->
    <template v-else-if="field.type === 'datetime'">
      <el-date-picker
        :model-value="modelValue"
        type="datetime"
        :placeholder="field.placeholder"
        :disabled="disabled"
        value-format="YYYY-MM-DD HH:mm:ss"
        style="width: 100%"
        popper-class="form-renderer-popper"
        @update:model-value="onUpdate"
      />
    </template>

    <!-- daterange -->
    <template v-else-if="field.type === 'daterange'">
      <el-date-picker
        :model-value="modelValue"
        type="daterange"
        :range-separator="t('common.to')"
        :start-placeholder="t('common.startDate')"
        :end-placeholder="t('common.endDate')"
        :disabled="disabled"
        value-format="YYYY-MM-DD"
        style="width: 100%"
        popper-class="form-renderer-popper"
        @update:model-value="onUpdate"
      />
    </template>

    <!-- time -->
    <template v-else-if="field.type === 'time'">
      <el-time-picker
        :model-value="modelValue"
        :placeholder="field.placeholder"
        :disabled="disabled"
        value-format="HH:mm:ss"
        style="width: 100%"
        popper-class="form-renderer-popper"
        @update:model-value="onUpdate"
      />
    </template>

    <!-- timerange -->
    <template v-else-if="field.type === 'timerange'">
      <el-time-picker
        :model-value="modelValue"
        is-range
        value-format="HH:mm:ss"
        :start-placeholder="(field as any).startPlaceholder || t('common.startDate')"
        :end-placeholder="(field as any).endPlaceholder || t('common.endDate')"
        :disabled="disabled"
        style="width: 100%"
        popper-class="form-renderer-popper"
        @update:model-value="onUpdate"
      />
    </template>

    <!-- cascader -->
    <template v-else-if="field.type === 'cascader'">
      <el-cascader
        :model-value="modelValue"
        :options="field.options"
        :props="field.cascaderProps"
        :placeholder="field.placeholder"
        :disabled="disabled"
        clearable
        style="width: 100%"
        popper-class="form-renderer-popper"
        @update:model-value="onUpdate"
      />
    </template>

    <!-- user -->
    <template v-else-if="field.type === 'user'">
      <el-select
        :model-value="modelValue"
        :placeholder="field.placeholder"
        :multiple="field.multiple"
        :disabled="disabled"
        filterable
        remote
        :remote-method="(query: string) => searchUsers(query, field)"
        clearable
        style="width: 100%"
        popper-class="form-renderer-popper"
        @update:model-value="onUpdate"
      >
        <el-option
          v-for="user in (userSearchResults || field.userOptions || [])"
          :key="user.id"
          :label="user.name"
          :value="user.id"
        />
      </el-select>
    </template>

    <!-- businessUnit -->
    <template v-else-if="field.type === 'businessUnit'">
      <el-tree-select
        :model-value="modelValue"
        :data="field.buOptions || []"
        :props="({ label: 'name', value: 'id', children: 'children' } as any)"
        :placeholder="field.placeholder"
        :disabled="disabled"
        check-strictly
        clearable
        style="width: 100%"
        popper-class="form-renderer-popper"
        @update:model-value="onUpdate"
      />
    </template>

    <!-- treeselect -->
    <template v-else-if="field.type === 'treeselect'">
      <el-tree-select
        :model-value="modelValue"
        :data="(field as any).treeData || []"
        :multiple="field.multiple"
        :check-strictly="(field as any).checkStrictly !== false"
        :placeholder="field.placeholder"
        :disabled="disabled"
        clearable
        style="width: 100%"
        popper-class="form-renderer-popper"
        @update:model-value="onUpdate"
      />
    </template>

    <!-- money -->
    <template v-else-if="field.type === 'money'">
      <el-input
        :model-value="modelValue"
        :placeholder="field.placeholder"
        :disabled="disabled"
        clearable
        @update:model-value="onUpdate"
      >
        <template #prepend>{{ field.currency || '¥' }}</template>
      </el-input>
    </template>

    <!-- rate -->
    <template v-else-if="field.type === 'rate'">
      <el-rate
        :model-value="modelValue"
        :max="field.max || 5"
        :disabled="disabled"
        @update:model-value="onUpdate"
      />
    </template>

    <!-- slider -->
    <template v-else-if="field.type === 'slider'">
      <el-slider
        :model-value="modelValue"
        :min="field.min || 0"
        :max="field.max || 100"
        :step="field.step || 1"
        :disabled="disabled"
        style="width: 100%"
        @update:model-value="onUpdate"
      />
    </template>

    <!-- colorPicker -->
    <template v-else-if="field.type === 'colorPicker'">
      <span
        v-if="readonly && modelValue"
        class="color-swatch"
        :style="{ backgroundColor: modelValue }"
        :title="modelValue"
      />
      <span v-else-if="readonly">-</span>
      <el-color-picker
        v-else
        :model-value="modelValue"
        :disabled="disabled"
        @update:model-value="onUpdate"
      />
    </template>

    <!-- editor (Task 6.2) -->
    <template v-else-if="field.type === 'editor'">
      <div v-if="readonly && modelValue" v-html="sanitize(modelValue)" class="editor-readonly" />
      <span v-else-if="readonly">-</span>
      <div v-else class="editor-wrapper">
        <Toolbar
          :editor="editorInstance"
          :default-config="editorToolbarConfig"
          mode="default"
          style="border-bottom: 1px solid #ccc"
        />
        <Editor
          :model-value="modelValue || ''"
          :default-config="editorConfig"
          mode="default"
          style="height: 300px; overflow-y: hidden"
          @on-created="onEditorCreated"
          @on-change="onEditorChange"
        />
      </div>
    </template>

    <!-- signature (Task 6.3) -->
    <template v-else-if="field.type === 'signature'">
      <img
        v-if="readonly && modelValue"
        :src="modelValue"
        class="signature-preview"
        :alt="t('fieldRenderer.signature')"
      />
      <span v-else-if="readonly">-</span>
      <div v-else class="signature-pad">
        <canvas
          ref="signatureCanvasRef"
          class="signature-canvas"
          @mousedown="onSigDown"
          @mousemove="onSigMove"
          @mouseup="onSigUp"
          @mouseleave="onSigUp"
          @touchstart.prevent="onTouchStart"
          @touchmove.prevent="onTouchMove"
          @touchend.prevent="onSigUp"
        />
        <div class="signature-actions">
          <el-button size="small" :disabled="disabled || signatureHistory.length === 0" @click="undoSignature">
            {{ t('fieldRenderer.undo') }}
          </el-button>
          <el-button size="small" :disabled="disabled" @click="clearSignature">
            {{ t('fieldRenderer.clear') }}
          </el-button>
        </div>
      </div>
    </template>

    <!-- transfer -->
    <template v-else-if="field.type === 'transfer'">
      <span v-if="readonly">
        {{ Array.isArray(modelValue) ? modelValue.join(', ') : (modelValue || '-') }}
      </span>
      <el-transfer
        v-else
        :model-value="modelValue"
        :data="(field.options || []).map((o: any) => ({ key: o.value, label: o.label }))"
        :disabled="disabled"
        filterable
        @update:model-value="onUpdate"
      />
    </template>

    <!-- upload (Task 6.8) -->
    <template v-else-if="field.type === 'upload'">
      <el-upload
        v-if="!readonly"
        :action="resolvedUploadUrl"
        :accept="field.uploadAccept || '.jpg,.jpeg,.png,.pdf,.docx,.xlsx'"
        :limit="field.uploadLimit || 1"
        :multiple="false"
        :disabled="disabled"
        :file-list="fileList"
        :on-success="onUploadSuccess"
        :on-remove="onUploadRemove"
        list-type="text"
      >
        <el-button type="primary" :disabled="disabled">
          <el-icon><Upload /></el-icon>
          {{ t('upload.selectFile') }}
        </el-button>
        <template #tip>
          <div class="el-upload__tip">
            {{ field.uploadAccept || '.jpg/.png/.pdf/.docx/.xlsx' }}
          </div>
        </template>
      </el-upload>
      <div v-else>
        <a v-if="modelValue" :href="modelValue" target="_blank">
          {{ fileList[0]?.name || modelValue }}
        </a>
        <span v-else>-</span>
      </div>
    </template>

    <!-- readonly -->
    <template v-else-if="field.type === 'readonly'">
      <span class="readonly-text">{{ modelValue || '-' }}</span>
    </template>

    <!-- divider -->
    <template v-else-if="field.type === 'divider'">
      <el-divider />
    </template>

    <!-- alert -->
    <template v-else-if="field.type === 'alert'">
      <el-alert
        :title="field.alertTitle"
        :type="field.alertType || 'info'"
        :closable="false"
        show-icon
      />
    </template>

    <!-- department (Task 6.4) -->
    <template v-else-if="field.type === 'department'">
      <span v-if="readonly" class="readonly-text">
        {{ departmentDisplayName || modelValue || '-' }}
      </span>
      <el-tree-select
        v-else
        :model-value="modelValue"
        :data="departmentTreeData"
        :props="({ label: 'name', value: 'id', children: 'children' } as any)"
        :placeholder="field.placeholder || t('fieldRenderer.selectDepartment')"
        :disabled="disabled"
        :loading="departmentLoading"
        check-strictly
        clearable
        filterable
        style="width: 100%"
        popper-class="form-renderer-popper"
        @update:model-value="onUpdate"
      />
    </template>

    <!-- default fallback -->
    <template v-else>
      <el-input
        :model-value="modelValue"
        :placeholder="field.placeholder"
        :disabled="disabled"
        clearable
        @update:model-value="onUpdate"
      />
    </template>
  </div>
</template>

<script setup lang="ts">
import {
  ref,
  computed,
  onMounted,
  onBeforeUnmount,
  shallowRef,
  nextTick,
  watch,
  inject,
} from 'vue'
import { useI18n } from 'vue-i18n'
import { Upload } from '@element-plus/icons-vue'
import DOMPurify from 'dompurify'
import { Editor, Toolbar } from '@wangeditor/editor-for-vue'
import '@wangeditor/editor/dist/css/style.css'
import type { FormField } from './formRendererHelpers'
import api from '@/api/request'

// ---------------------------------------------------------------------------
// i18n
// ---------------------------------------------------------------------------
const { t } = useI18n()

// ---------------------------------------------------------------------------
// Props & Emits
// ---------------------------------------------------------------------------
interface Props {
  field: FormField
  modelValue: any
  readonly?: boolean
  disabled?: boolean
  visible?: boolean
  options?: Array<{ label: string; value: any }>
  uploadUrl?: string
  userSearchResults?: Array<{ id: string; name: string }>
}

const props = withDefaults(defineProps<Props>(), {
  readonly: false,
  disabled: false,
  visible: true,
})

const emit = defineEmits<{
  (e: 'update:modelValue', value: any): void
  (e: 'upload:success', response: any, file: any, fieldKey: string): void
  (e: 'upload:remove', file: any, fieldKey: string): void
  (e: 'search:users', query: string, fieldKey: string): void
}>()

function onUpdate(value: any) {
  emit('update:modelValue', value)
}

// ---------------------------------------------------------------------------
// Resolved options — linkage override takes priority (Task 6.1)
// ---------------------------------------------------------------------------
const resolvedOptions = computed(() => {
  return props.options ?? props.field.options ?? []
})

// ---------------------------------------------------------------------------
// XSS sanitization (Task 6.5)
// ---------------------------------------------------------------------------
const SAFE_TAGS = [
  'p', 'br', 'strong', 'em', 'u', 's',
  'ol', 'ul', 'li',
  'h1', 'h2', 'h3', 'h4', 'h5', 'h6',
  'a', 'img',
  'table', 'tr', 'td', 'th',
  'span', 'div',
]

function sanitize(html: string): string {
  return DOMPurify.sanitize(html, {
    ALLOWED_TAGS: SAFE_TAGS,
    ALLOWED_ATTR: [
      'href', 'target', 'rel',
      'src', 'alt', 'width', 'height',
      'class', 'style',
      'colspan', 'rowspan',
    ],
  })
}

// ---------------------------------------------------------------------------
// Editor — wangeditor (Task 6.2)
// ---------------------------------------------------------------------------
const editorInstance = shallowRef<any>(null)

const editorToolbarConfig = {}

const editorConfig = computed(() => ({
  placeholder: props.field.placeholder || t('fieldRenderer.editorPlaceholder'),
  readOnly: props.disabled,
}))

function onEditorCreated(editor: any) {
  editorInstance.value = editor
}

function onEditorChange(editor: any) {
  const html = editor.getHtml()
  emit('update:modelValue', html)
}

onBeforeUnmount(() => {
  if (editorInstance.value) {
    editorInstance.value.destroy()
    editorInstance.value = null
  }
})

// ---------------------------------------------------------------------------
// Signature canvas (Task 6.3)
// ---------------------------------------------------------------------------
const signatureCanvasRef = ref<HTMLCanvasElement | null>(null)
let signing = false
let sigObserver: ResizeObserver | null = null

// Signature history stack for Undo (Req 28, max 20 snapshots)
const signatureHistory = ref<string[]>([])
const MAX_SIGNATURE_HISTORY = 20

function getSigCtx() {
  return signatureCanvasRef.value?.getContext('2d') ?? null
}

function syncCanvasSize() {
  const canvas = signatureCanvasRef.value
  if (!canvas) return
  const w = canvas.parentElement?.clientWidth || canvas.offsetWidth || 400
  if (canvas.width !== w || canvas.height !== 120) {
    canvas.width = w
    canvas.height = 120
  }
}

function getCanvasPos(e: MouseEvent | Touch) {
  const canvas = signatureCanvasRef.value
  if (!canvas) return { x: 0, y: 0 }
  const r = canvas.getBoundingClientRect()
  return { x: e.clientX - r.left, y: e.clientY - r.top }
}

function onSigDown(e: MouseEvent) {
  if (props.disabled) return
  syncCanvasSize()
  // Save snapshot before new stroke for Undo (Req 28)
  saveSignatureSnapshot()
  signing = true
  const ctx = getSigCtx()
  if (!ctx) return
  const pos = getCanvasPos(e)
  ctx.beginPath()
  ctx.moveTo(pos.x, pos.y)
}

function onSigMove(e: MouseEvent) {
  if (!signing) return
  const ctx = getSigCtx()
  if (!ctx) return
  const pos = getCanvasPos(e)
  ctx.lineWidth = 2
  ctx.lineCap = 'round'
  ctx.strokeStyle = '#000'
  ctx.lineTo(pos.x, pos.y)
  ctx.stroke()
}

function onSigUp() {
  if (!signing) return
  signing = false
  if (signatureCanvasRef.value) {
    emit('update:modelValue', signatureCanvasRef.value.toDataURL('image/png'))
  }
}

function onTouchStart(e: TouchEvent) {
  if (props.disabled || !e.touches.length) return
  syncCanvasSize()
  // Save snapshot before new stroke for Undo (Req 28)
  saveSignatureSnapshot()
  signing = true
  const ctx = getSigCtx()
  if (!ctx) return
  const pos = getCanvasPos(e.touches[0])
  ctx.beginPath()
  ctx.moveTo(pos.x, pos.y)
}

function onTouchMove(e: TouchEvent) {
  if (!signing || !e.touches.length) return
  const ctx = getSigCtx()
  if (!ctx) return
  const pos = getCanvasPos(e.touches[0])
  ctx.lineWidth = 2
  ctx.lineCap = 'round'
  ctx.strokeStyle = '#000'
  ctx.lineTo(pos.x, pos.y)
  ctx.stroke()
}

function saveSignatureSnapshot() {
  const canvas = signatureCanvasRef.value
  if (!canvas) return
  const snapshot = canvas.toDataURL('image/png')
  if (signatureHistory.value.length >= MAX_SIGNATURE_HISTORY) {
    signatureHistory.value.shift() // FIFO: remove oldest
  }
  signatureHistory.value.push(snapshot)
}

function undoSignature() {
  if (signatureHistory.value.length === 0) return
  const snapshot = signatureHistory.value.pop()!
  const canvas = signatureCanvasRef.value
  if (!canvas) return
  const ctx = canvas.getContext('2d')
  if (!ctx) return
  const img = new Image()
  img.onload = () => {
    ctx.clearRect(0, 0, canvas.width, canvas.height)
    ctx.drawImage(img, 0, 0)
    emit('update:modelValue', canvas.toDataURL('image/png'))
  }
  img.src = snapshot
}

function clearSignature() {
  const ctx = getSigCtx()
  if (ctx && signatureCanvasRef.value) {
    ctx.clearRect(0, 0, signatureCanvasRef.value.width, signatureCanvasRef.value.height)
  }
  signatureHistory.value = []
  emit('update:modelValue', '')
}

// ---------------------------------------------------------------------------
// Department tree-select (Task 6.4)
// ---------------------------------------------------------------------------
interface DepartmentNode {
  id: string
  name: string
  children?: DepartmentNode[]
}

const departmentTreeData = ref<DepartmentNode[]>([])
const departmentLoading = ref(false)

// Use injected shared cache from FormRenderer if available (Req 27)
const sharedDepartmentData = inject<typeof departmentTreeData>('departmentTreeData', undefined)
const sharedDepartmentLoading = inject<typeof departmentLoading>('departmentTreeLoading', undefined)

/** Recursively find a node by id to resolve display name */
function findDepartmentName(
  nodes: DepartmentNode[],
  id: string,
): string | undefined {
  for (const node of nodes) {
    if (node.id === id) return node.name
    if (node.children) {
      const found = findDepartmentName(node.children, id)
      if (found) return found
    }
  }
  return undefined
}

const departmentDisplayName = computed(() => {
  if (!props.modelValue || departmentTreeData.value.length === 0) return ''
  return findDepartmentName(departmentTreeData.value, props.modelValue) ?? ''
})

async function fetchDepartmentTree() {
  // Use shared cache from FormRenderer if available (Req 27)
  if (sharedDepartmentData?.value && sharedDepartmentData.value.length > 0) {
    departmentTreeData.value = sharedDepartmentData.value
    return
  }
  if (departmentTreeData.value.length > 0) return // already cached locally
  departmentLoading.value = true
  if (sharedDepartmentLoading) sharedDepartmentLoading.value = true
  try {
    const res = await api.get('/api/portal/departments/tree')
    const data = res.data?.data ?? res.data ?? []
    departmentTreeData.value = data
    // Write back to shared cache
    if (sharedDepartmentData) sharedDepartmentData.value = data
  } catch (err) {
    console.warn('[FieldRenderer] Department API error:', err)
  } finally {
    departmentLoading.value = false
    if (sharedDepartmentLoading) sharedDepartmentLoading.value = false
  }
}

// ---------------------------------------------------------------------------
// Upload URL resolution (Task 6.8)
// Priority: props.uploadUrl → field.uploadUrl → default '/api/v1/upload'
// ---------------------------------------------------------------------------
const DEFAULT_UPLOAD_URL = '/api/v1/upload'

const resolvedUploadUrl = computed(() => {
  if (props.uploadUrl) return props.uploadUrl
  if (props.field.uploadUrl && props.field.uploadUrl !== '/') return props.field.uploadUrl
  return DEFAULT_UPLOAD_URL
})

// Upload file list (local state for display)
const fileList = ref<Array<{ name: string; url: string; uid?: number }>>([])

// Initialise file list from modelValue when it's a URL string
watch(
  () => props.modelValue,
  (val) => {
    if (props.field.type === 'upload' && val && fileList.value.length === 0) {
      const url = String(val)
      const fileName = decodeURIComponent(url.split('/').pop() || url)
      fileList.value = [{ name: fileName, url }]
    }
  },
  { immediate: true },
)

function onUploadSuccess(response: any, file: any) {
  const url = response?.data?.url || ''
  fileList.value = [{ name: file.name, url, uid: file.uid }]
  emit('update:modelValue', url)
  emit('upload:success', response, file, props.field.key)
}

function onUploadRemove(file: any) {
  fileList.value = []
  emit('update:modelValue', '')
  emit('upload:remove', file, props.field.key)
}

// ---------------------------------------------------------------------------
// User search — emit to parent FormRenderer (Req 11.1, 11.3)
// ---------------------------------------------------------------------------
function searchUsers(query: string, field: FormField) {
  if (query.length < 2) return
  emit('search:users', query, field.key)
}

// ---------------------------------------------------------------------------
// Lifecycle
// ---------------------------------------------------------------------------
onMounted(() => {
  // Signature canvas setup
  if (props.field.type === 'signature' && !props.readonly) {
    nextTick(() => {
      setTimeout(syncCanvasSize, 50)
      if (signatureCanvasRef.value) {
        sigObserver = new ResizeObserver(syncCanvasSize)
        sigObserver.observe(
          signatureCanvasRef.value.parentElement || signatureCanvasRef.value,
        )
      }
    })
  }

  // Department data fetch
  if (props.field.type === 'department') {
    fetchDepartmentTree()
  }
})

onBeforeUnmount(() => {
  sigObserver?.disconnect()
})
</script>

<style scoped lang="scss">
.field-renderer-root {
  width: 100%;
  min-width: 0;
  box-sizing: border-box;
}

.color-swatch {
  display: inline-block;
  width: 20px;
  height: 20px;
  border-radius: 3px;
  border: 1px solid #dcdfe6;
  vertical-align: middle;
}

.editor-readonly {
  padding: 8px;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  background: #f5f7fa;
  min-height: 40px;
  line-height: 1.5;
  word-break: break-word;
  width: 100%;
}

.editor-wrapper {
  border: 1px solid #ccc;
  border-radius: 4px;
  overflow: hidden;
  width: 100%;
}

.signature-preview {
  max-width: 200px;
  max-height: 80px;
  object-fit: contain;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  background: #fff;
}

.signature-pad {
  width: 100%;
}

.signature-canvas {
  display: block;
  width: 100%;
  height: 120px;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  cursor: crosshair;
  background: #fff;
}

.signature-actions {
  margin-top: 4px;
}

.readonly-text {
  color: #606266;
  line-height: 32px;
}
</style>
