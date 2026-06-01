<template>
  <!-- 占满表单项内容区，否则 el-select 等 width:100% 会相对收缩父级计算，出现仅显示箭头 -->
  <div
    v-show="visible"
    class="field-renderer-root form-readonly-surface"
  >
    <!-- text / input -->
    <template v-if="field.type === 'text' || field.type === 'input'">
      <el-input
        :model-value="modelValue"
        :placeholder="field.placeholder"
        :maxlength="field.maxLength"
        :show-word-limit="!!field.maxLength"
        :disabled="isDisabled"
        clearable
        @update:model-value="onUpdate"
        @blur="onBlur"
      />
    </template>

    <!-- password -->
    <template v-else-if="field.type === 'password'">
      <el-input
        :model-value="modelValue"
        type="password"
        show-password
        :placeholder="field.placeholder"
        :disabled="isDisabled"
        clearable
        @update:model-value="onUpdate"
        @blur="onBlur"
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
        :disabled="isDisabled"
        @update:model-value="onUpdate"
        @blur="onBlur"
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
        :disabled="isDisabled"
        style="width: 100%"
        @update:model-value="onUpdate"
        @blur="onBlur"
      />
    </template>

    <!-- select -->
    <template v-else-if="field.type === 'select'">
      <el-select
        :model-value="modelValue"
        :placeholder="field.placeholder"
        :multiple="field.multiple"
        :filterable="field.filterable"
        :disabled="isDisabled"
        clearable
        style="width: 100%"
        popper-class="form-renderer-popper"
        @update:model-value="onUpdate"
        @blur="onBlur"
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
        :disabled="isDisabled"
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
        :disabled="isDisabled"
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
        :disabled="isDisabled"
        @update:model-value="onUpdate"
      />
    </template>

    <!-- date -->
    <template v-else-if="field.type === 'date'">
      <el-date-picker
        :model-value="modelValue"
        type="date"
        :placeholder="field.placeholder"
        :disabled="isDisabled"
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
        :disabled="isDisabled"
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
        :disabled="isDisabled"
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
        :disabled="isDisabled"
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
        :disabled="isDisabled"
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
        :disabled="isDisabled"
        clearable
        style="width: 100%"
        popper-class="form-renderer-popper"
        @update:model-value="onUpdate"
        @blur="onBlur"
      />
    </template>

    <!-- user -->
    <template v-else-if="field.type === 'user'">
      <el-select
        :model-value="modelValue"
        :placeholder="field.placeholder"
        :multiple="field.multiple"
        :disabled="isDisabled"
        filterable
        remote
        :remote-method="(query: string) => searchUsers(query, field)"
        clearable
        style="width: 100%"
        popper-class="form-renderer-popper"
        @update:model-value="onUpdate"
        @blur="onBlur"
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
        :disabled="isDisabled"
        check-strictly
        clearable
        style="width: 100%"
        popper-class="form-renderer-popper"
        @update:model-value="onUpdate"
        @blur="onBlur"
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
        :disabled="isDisabled"
        clearable
        style="width: 100%"
        popper-class="form-renderer-popper"
        @update:model-value="onUpdate"
        @blur="onBlur"
      />
    </template>

    <!-- money -->
    <template v-else-if="field.type === 'money'">
      <el-input
        :model-value="modelValue"
        :placeholder="field.placeholder"
        :disabled="isDisabled"
        clearable
        @update:model-value="onUpdate"
        @blur="onBlur"
      >
        <template #prepend>
          {{ field.currency || '¥' }}
        </template>
      </el-input>
    </template>

    <!-- rate -->
    <template v-else-if="field.type === 'rate'">
      <el-rate
        :model-value="modelValue"
        :max="field.max || 5"
        :disabled="isDisabled"
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
        :disabled="isDisabled"
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
        :disabled="isDisabled"
        @update:model-value="onUpdate"
      />
    </template>

    <!-- editor (Task 6.2) -->
    <template v-else-if="field.type === 'editor'">
      <div
        v-if="readonly && modelValue"
        class="editor-readonly"
        v-html="sanitize(modelValue)"
      />
      <span v-else-if="readonly">-</span>
      <div
        v-else
        class="editor-wrapper"
      >
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
      >
      <span v-else-if="readonly">-</span>
      <div
        v-else
        class="signature-pad"
      >
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
          <el-button
            size="small"
            :disabled="disabled || signatureHistory.length === 0"
            @click="undoSignature"
          >
            {{ t('fieldRenderer.undo') }}
          </el-button>
          <el-button
            size="small"
            :disabled="disabled"
            @click="clearSignature"
          >
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
        :disabled="isDisabled"
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
        :disabled="isDisabled"
        :file-list="fileList"
        :on-success="onUploadSuccess"
        :on-remove="onUploadRemove"
        list-type="text"
      >
        <el-button
          type="primary"
          :disabled="disabled"
        >
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
        <a
          v-if="modelValue"
          :href="modelValue"
          target="_blank"
        >
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

    <!-- title (fcTitle) -->
    <template v-else-if="field.type === 'title'">
      <div
        class="form-layout-title"
        :class="`form-layout-title--${field.titleSize || 'default'}`"
      >
        {{ field.label }}
      </div>
    </template>

    <!-- static text -->
    <template v-else-if="field.type === 'staticText'">
      <div class="form-layout-static-text">
        {{ field.label }}
      </div>
    </template>

    <!-- html block -->
    <template v-else-if="field.type === 'html'">
      <div
        class="form-layout-html"
        v-html="sanitize(String(field.htmlContent || ''))"
      />
    </template>

    <!-- tag -->
    <template v-else-if="field.type === 'tag'">
      <el-tag type="info">
        {{ field.label }}
      </el-tag>
    </template>

    <!-- button (read-only display) -->
    <template v-else-if="field.type === 'button'">
      <el-button disabled>
        {{ field.label }}
      </el-button>
    </template>

    <!-- vertical spacer -->
    <template v-else-if="field.type === 'space'">
      <div
        class="form-layout-space"
        :style="{ height: `${field.step ?? 16}px` }"
      />
    </template>

    <!-- image -->
    <template v-else-if="field.type === 'image'">
      <el-image
        v-if="field.defaultValue"
        :src="String(field.defaultValue)"
        fit="contain"
        style="max-width: 100%; max-height: 240px;"
      />
      <span
        v-else
        class="readonly-text"
      >-</span>
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
      <span
        v-if="readonly"
        class="readonly-text"
      >
        {{ departmentDisplayName || modelValue || '-' }}
      </span>
      <el-tree-select
        v-else
        :model-value="modelValue"
        :data="departmentTreeData"
        :props="({ label: 'name', value: 'id', children: 'children' } as any)"
        :placeholder="field.placeholder || t('fieldRenderer.selectDepartment')"
        :disabled="isDisabled"
        :loading="departmentLoading"
        check-strictly
        clearable
        filterable
        style="width: 100%"
        popper-class="form-renderer-popper"
        @update:model-value="onUpdate"
      />
    </template>

    <!--
      lookup — match FormRenderer's top-level branch so PortalFormFields / SubTableInlineForm
      / SubTaskForm don't fall through to the default <el-input>, which stringifies the
      assignee object as "[object Object]". Backfill view (LookupViewDisplay) is rendered
      below the field when `lookupConfig.showBackfillView !== false` (carried via
      `_lookupShowBackfillView` from extractFieldsRecursive); falls back to runtime-loaded
      view fields when designer did not configure them.
    -->
    <template v-else-if="field.type === 'lookup'">
      <div class="lookup-field-wrapper">
        <LookupField
          :model-value="modelValue"
          :table-id="Number((field as any)._lookupTableId || 0)"
          :search-fields="(field as any)._lookupSearchFields || []"
          :display-field="(field as any)._lookupDisplayField || ''"
          :display-fields="(field as any)._lookupDisplayFields || []"
          :selected-display-field="(field as any)._lookupSelectedDisplayField || ''"
          :filter-conditions="(field as any)._lookupFilterConditions || []"
          :lookup-config="(field as any)._lookupConfig"
          :view-fields="(field as any)._lookupViewFields || []"
          :placeholder="field.placeholder"
          :readonly="isDisabled"
          @update:model-value="onUpdate"
          @select="(row: Record<string, any>) => onLookupSelect(row)"
          @clear="onLookupClear"
          @view-fields-loaded="(vfs: any[]) => onLookupViewFieldsLoaded(vfs)"
        />
        <LookupViewDisplay
          v-if="lookupShowBackfillView && lookupSelectedRow"
          :selected-data="lookupSelectedRow"
          :view-fields="effectiveLookupViewFields"
        />
      </div>
    </template>

    <!-- default fallback -->
    <template v-else>
      <el-input
        :model-value="modelValue"
        :placeholder="field.placeholder"
        :disabled="isDisabled"
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
import type { Ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { Upload } from '@element-plus/icons-vue'
import DOMPurify from 'dompurify'
import { Editor, Toolbar } from '@wangeditor/editor-for-vue'
import '@wangeditor/editor/dist/css/style.css'
import type { FormField } from './formRendererHelpers'
import api from '@/api/request'
import LookupField from './lookup/LookupField.vue'
import LookupViewDisplay from './lookup/LookupViewDisplay.vue'

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
  formData?: Record<string, any>
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
  (e: 'field-blur', fieldKey: string): void
  (e: 'upload:success', response: any, file: any, fieldKey: string): void
  (e: 'upload:remove', file: any, fieldKey: string): void
  (e: 'search:users', query: string, fieldKey: string): void
}>()

const isDisabled = computed(() => props.readonly || props.disabled)

function onUpdate(value: any) {
  if (props.readonly) return
  emit('update:modelValue', value)
}

function onBlur() {
  if (props.readonly) return
  emit('field-blur', props.field.key)
}

// ---------------------------------------------------------------------------
// Lookup state — mirrors FormRenderer's lookupSelectedData / lookupLoadedViewFields
// so the LookupViewDisplay backfill panel shows up inside SubTableInlineForm /
// Link Form modal / SubTaskForm (which all go through FieldRenderer, not FormRenderer).
// ---------------------------------------------------------------------------
const lookupSelectedRow = ref<Record<string, any> | null>(null)
const lookupLoadedViewFields = ref<any[]>([])

const lookupShowBackfillView = computed<boolean>(() => {
  if (props.field.type !== 'lookup') return false
  return (props.field as any)._lookupShowBackfillView !== false
})

const effectiveLookupViewFields = computed(() => {
  const configured = (props.field as any)._lookupViewFields
  if (Array.isArray(configured) && configured.length > 0) return configured
  return lookupLoadedViewFields.value
})

function onLookupSelect(row: Record<string, any>) {
  lookupSelectedRow.value = row && typeof row === 'object' ? row : null
}

function onLookupClear() {
  lookupSelectedRow.value = null
}

function onLookupViewFieldsLoaded(vfs: any[]) {
  lookupLoadedViewFields.value = Array.isArray(vfs) ? vfs : []
}

watch(
  () => [props.modelValue, props.field?.type] as const,
  ([val, type]) => {
    if (type !== 'lookup') {
      lookupSelectedRow.value = null
      return
    }
    if (val && typeof val === 'object' && !Array.isArray(val) && Object.keys(val).length > 0) {
      lookupSelectedRow.value = val as Record<string, any>
    } else if (val == null || val === '') {
      lookupSelectedRow.value = null
    }
  },
  { immediate: true, deep: true },
)

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
const sharedDepartmentData = inject<Ref<DepartmentNode[]> | undefined>('departmentTreeData')
const sharedDepartmentLoading = inject<Ref<boolean> | undefined>('departmentTreeLoading')

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

function extractFileNameFromUrl(url: string): string {
  if (!url) return ''
  try {
    const parsed = new URL(url, window.location.origin)
    const fromQuery = parsed.searchParams.get('originalName')
      || parsed.searchParams.get('fileName')
      || parsed.searchParams.get('filename')
      || parsed.searchParams.get('name')
    if (fromQuery) return decodeURIComponent(fromQuery)
    const pathPart = parsed.pathname.split('/').pop() || url
    return decodeURIComponent(pathPart)
  } catch {
    const [pathPart] = String(url).split('?')
    return decodeURIComponent(pathPart.split('/').pop() || url)
  }
}

// Initialise file list from modelValue when it's a URL string
watch(
  () => props.modelValue,
  (val) => {
    if (props.field.type === 'upload' && val && fileList.value.length === 0) {
      const url = String(val)
      const targetField = (props.field as any).fileNameTargetField
      const targetName = targetField ? props.formData?.[targetField] : undefined
      const fileName = (typeof targetName === 'string' && targetName.trim().length > 0)
        ? targetName
        : extractFileNameFromUrl(url)
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
@import '@/styles/form-readonly.scss';

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

.form-layout-title {
  font-weight: 600;
  color: #303133;
  margin: 4px 0 12px;
  line-height: 1.4;
}

.form-layout-title--h1 {
  font-size: 20px;
}

.form-layout-title--h2,
.form-layout-title--default {
  font-size: 16px;
}

.form-layout-title--h3,
.form-layout-title--h4 {
  font-size: 14px;
}

.form-layout-static-text {
  color: #606266;
  line-height: 1.5;
  margin-bottom: 8px;
}

.form-layout-html {
  line-height: 1.5;
  margin-bottom: 8px;
}

.form-layout-space {
  width: 100%;
}

.lookup-field-wrapper {
  width: 100%;
  min-width: 0;
}
</style>
