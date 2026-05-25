<template>
  <SubTableNestedModalShell
    :visible="visibleModel"
    :title="title || (mode === 'edit' ? 'Edit Record' : 'Add Record')"
    width="min(600px, calc(100vw - 48px))"
    @update:visible="visibleModel = $event"
    @closed="onShellClosed"
  >
    <el-form
      ref="formRef"
      :model="formData"
      :rules="formRules"
      label-width="auto"
      label-position="left"
    >
      <el-form-item
        v-for="col in columns"
        :key="col.field"
        :label="col.label"
        :prop="col.field"
      >
        <!-- text -->
        <el-input
          v-if="!col.type || col.type === 'text'"
          v-model="formData[col.field]"
          :placeholder="col.placeholder || col.label"
          :maxlength="col.props?.maxlength"
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
          style="width: 100%"
        />

        <!-- select -->
        <el-select
          v-else-if="col.type === 'select'"
          v-model="formData[col.field]"
          :placeholder="col.placeholder || col.label"
          :multiple="col.props?.multiple"
          clearable
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
          >
            {{ opt.label }}
          </el-radio>
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
          >
            {{ opt.label }}
          </el-checkbox>
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
          :start-placeholder="col.props?.startPlaceholder || 'Start time'"
          :end-placeholder="col.props?.endPlaceholder || 'End time'"
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
          style="width: 100%"
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
          style="width: 100%"
        />

        <!-- datetime -->
        <el-date-picker
          v-else-if="col.type === 'datetime'"
          v-model="formData[col.field]"
          type="datetime"
          value-format="YYYY-MM-DD HH:mm:ss"
          :placeholder="col.placeholder || col.label"
          style="width: 100%"
        />

        <!-- upload -->
        <div
          v-else-if="col.type === 'upload'"
          style="display: flex; flex-direction: column; gap: 4px;"
        >
          <el-upload
            :action="col.props?.action && col.props.action !== '/' ? col.props.action : '/api/v1/upload'"
            :accept="col.props?.accept || '.jpg,.jpeg,.png,.pdf,.docx,.xlsx'"
            :show-file-list="false"
            :on-success="(res: any, file: any) => handleUploadSuccess(res, file, col)"
            :on-error="() => handleUploadError(col)"
          >
            <el-button
              size="small"
              type="primary"
            >
              <el-icon><Upload /></el-icon> Upload
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

        <!-- tree (el-tree with checkbox, uses id/label node format) -->
        <el-tree
          v-else-if="col.type === 'tree'"
          :data="col.props?.treeData || []"
          :props="col.props?.labelProps || { label: 'label', children: 'children' }"
          :node-key="col.props?.nodeKey || 'id'"
          :show-checkbox="col.props?.showCheckbox !== false"
          @check="(node: any, state: any) => { formData[col.field] = state.checkedKeys }"
        />

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

        <!-- editor (rich text) -->
        <el-input
          v-else-if="col.type === 'editor'"
          v-model="formData[col.field]"
          type="textarea"
          :rows="col.props?.rows || 5"
          :placeholder="col.placeholder || col.label"
          :maxlength="col.props?.maxlength"
        />

        <!-- signature (base64 image URL input) -->
        <div
          v-else-if="col.type === 'signature'"
          style="width: 100%;"
        >
          <canvas
            :ref="(el: any) => { if (el) signatureCanvasRefs[col.field] = el }"
            class="signature-canvas"
            @mousedown="startSign($event, col.field)"
            @mousemove="drawSign($event, col.field)"
            @mouseup="endSign(col.field)"
            @mouseleave="endSign(col.field)"
          />
          <div style="margin-top: 4px;">
            <el-button
              size="small"
              @click="clearSignature(col.field)"
            >
              Clear
            </el-button>
          </div>
        </div>

        <!-- transfer -->
        <el-transfer
          v-else-if="col.type === 'transfer'"
          v-model="formData[col.field]"
          :data="(col.props?.options ?? col.options ?? []).map((o: any) => ({ key: o.value, label: o.label }))"
          :titles="[col.props?.leftTitle || 'Source', col.props?.rightTitle || 'Target']"
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
          style="width: 100%"
        />

        <!-- user / department — rendered as plain input (placeholder) -->
        <el-input
          v-else-if="col.type === 'user' || col.type === 'department'"
          v-model="formData[col.field]"
          :placeholder="col.placeholder || (col.type === 'user' ? 'Select user' : 'Select department')"
          clearable
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
      <el-button @click="requestClose">
        Cancel
      </el-button>
      <el-button
        type="primary"
        @click="handleSave"
      >
        Save
      </el-button>
    </template>
  </SubTableNestedModalShell>
</template>

<script setup lang="ts">
import { ref, watch, computed } from 'vue'
import { Upload } from '@element-plus/icons-vue'
import type { FormInstance } from 'element-plus'
import { ElMessage } from 'element-plus'
import { buildInitialRow, buildRules } from './subTableAddDialogHelpers'
import type { DialogColumn } from './subTableAddDialogHelpers'
import SubTableNestedModalShell from './SubTableNestedModalShell.vue'
import { getFilenameFromUrl, extractUploadUrlFromResponse, normalizeUploadFieldsInRow } from './uploadFieldUtils'

const props = defineProps<{
  visible: boolean
  columns: DialogColumn[]
  title?: string
  mode: 'add' | 'edit'
  initialData?: Record<string, any>
}>()

const emit = defineEmits<{
  (e: 'update:visible', val: boolean): void
  (e: 'save', rowData: Record<string, any>): void
}>()

const visibleModel = computed({
  get: () => props.visible,
  set: (val: boolean) => emit('update:visible', val),
})

const formRef = ref<FormInstance>()
const formData = ref<Record<string, any>>({})
const uploadNames = ref<Record<string, string>>({})

const signatureCanvasRefs = ref<Record<string, HTMLCanvasElement>>({})
const signingField = ref<string | null>(null)

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

const formRules = computed(() => buildRules(props.columns))

watch(
  () => props.visible,
  (open) => {
    if (!open) return
    uploadNames.value = {}
    if (props.mode === 'edit' && props.initialData) {
      formData.value = { ...buildInitialRow(props.columns), ...JSON.parse(JSON.stringify(props.initialData)) }
      for (const col of props.columns) {
        if (col.type === 'upload' && formData.value[col.field]) {
          const url: string = formData.value[col.field]
          uploadNames.value[col.field] = getFilenameFromUrl(url)
        }
      }
    } else {
      formData.value = buildInitialRow(props.columns)
    }
  },
  { immediate: false },
)

function onShellClosed() {
  formRef.value?.resetFields()
  uploadNames.value = {}
  formData.value = buildInitialRow(props.columns)
}

function requestClose() {
  formRef.value?.resetFields()
  visibleModel.value = false
}

async function handleSave() {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  const row = { ...formData.value }
  normalizeUploadFieldsInRow(row, props.columns)
  emit('save', row)
  visibleModel.value = false
}

function handleUploadSuccess(res: any, file: any, col: DialogColumn) {
  const url = extractUploadUrlFromResponse(res)
  formData.value[col.field] = url
  uploadNames.value = { ...uploadNames.value, [col.field]: file.name }
  const target = col.props?.fileNameTargetField
  if (target && props.columns.some(c => c.field === target)) {
    formData.value[target] = file.name
  }
}

function handleUploadError(col: DialogColumn) {
  ElMessage.error(`File upload failed for field "${col.label}"`)
}

function clearUpload(col: DialogColumn) {
  formData.value[col.field] = ''
  const next = { ...uploadNames.value }
  delete next[col.field]
  uploadNames.value = next
}
</script>

<style scoped>
:deep(.el-form-item__label) {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
</style>

<style>
.sub-table-color-popper {
  z-index: var(--sub-table-nested-popper-z, 10050) !important;
}

.signature-canvas {
  width: 100%;
  height: 120px;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  cursor: crosshair;
  background: #fff;
}
</style>
