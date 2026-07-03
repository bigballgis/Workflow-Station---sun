<template>
  <!-- 遮罩必须挂到 body：dialog 已 append-to-body，若遮罩留在子表/Tab 内会因祖先 transform 导致 fixed 错位或裁剪 -->
  <Teleport to="body">
    <div
      v-if="visible"
      class="sub-table-backdrop"
      role="presentation"
      aria-hidden="true"
      @click="handleClose"
    />
  </Teleport>
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
      :key="dialogKey"
      ref="formRef"
      class="form-readonly-surface"
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
        :error="columnErrors[col.field]?.join('; ')"
      >
        <!-- text -->
        <el-input
          v-if="(!col.type || col.type === 'text') && !isUploadColumn(col, formData[col.field])"
          v-model="formData[col.field]"
          :placeholder="col.placeholder || col.label"
          :maxlength="col.props?.maxlength"
          :disabled="isColDisabled(col)"
          :clearable="!isColDisabled(col)"
        />

        <!-- textarea -->
        <el-input
          v-else-if="col.type === 'textarea'"
          v-model="formData[col.field]"
          type="textarea"
          :rows="col.props?.rows || 3"
          :placeholder="col.placeholder || col.label"
          :maxlength="col.props?.maxlength"
          :disabled="isColDisabled(col)"
        />

        <!-- number -->
        <el-input-number
          v-else-if="col.type === 'number'"
          v-model="formData[col.field]"
          :precision="col.props?.precision"
          :min="col.props?.min"
          :max="col.props?.max"
          :placeholder="col.placeholder || col.label"
          :disabled="isColDisabled(col)"
          style="width: 100%"
        />

        <!-- select -->
        <el-select
          v-else-if="col.type === 'select'"
          v-model="formData[col.field]"
          :placeholder="col.placeholder || col.label"
          :multiple="col.props?.multiple"
          :clearable="!isColDisabled(col)"
          :disabled="isColDisabled(col)"
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
          :disabled="isColDisabled(col)"
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
          :disabled="isColDisabled(col)"
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
          :disabled="isColDisabled(col)"
          :clearable="!isColDisabled(col)"
        />

        <!-- timerange -->
        <el-time-picker
          v-else-if="col.type === 'timerange'"
          v-model="formData[col.field]"
          is-range
          value-format="HH:mm:ss"
          :start-placeholder="col.props?.startPlaceholder || t('subTable.startTime')"
          :end-placeholder="col.props?.endPlaceholder || t('subTable.endTime')"
          :disabled="isColDisabled(col)"
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
          :clearable="!isColDisabled(col)"
          :disabled="isColDisabled(col)"
          :teleported="true"
          style="width: 100%"
        />

        <!-- tree -->
        <el-tree
          v-else-if="col.type === 'tree'"
          :data="col.props?.treeData || []"
          :props="col.props?.labelProps || { label: 'label', children: 'children' }"
          :node-key="col.props?.nodeKey || 'id'"
          :show-checkbox="col.props?.showCheckbox !== false && !isColDisabled(col)"
          :class="{ 'tree-readonly': isColDisabled(col) }"
          @check="(node: any, state: any) => { if (!isColDisabled(col)) formData[col.field] = state.checkedKeys }"
        />

        <!-- switch -->
        <el-switch
          v-else-if="col.type === 'switch'"
          v-model="formData[col.field]"
          :disabled="isColDisabled(col)"
        />

        <!-- date -->
        <el-date-picker
          v-else-if="col.type === 'date'"
          v-model="formData[col.field]"
          type="date"
          value-format="YYYY-MM-DD"
          :placeholder="col.placeholder || col.label"
          :disabled="isColDisabled(col)"
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
          :disabled="isColDisabled(col)"
          :teleported="true"
          popper-class="sub-table-date-popper"
          style="width: 100%"
        />

        <!-- upload (readonly) -->
        <div
          v-else-if="isUploadColumn(col, formData[col.field]) && isColDisabled(col)"
          class="ro-value"
        >
          <a v-if="formData[col.field]" :href="formData[col.field]" target="_blank" class="upload-download-link">{{ getFilenameFromUrl(formData[col.field], uploadNames[col.field]) }}</a>
          <span v-else>-</span>
        </div>
        <!-- upload -->
        <div
          v-else-if="isUploadColumn(col, formData[col.field])"
          style="display: flex; flex-direction: column; gap: 4px;"
        >
          <el-upload
            :action="col.props?.action && col.props.action !== '/' ? col.props.action : (uploadUrl || '/api/v1/upload')"
            :accept="col.props?.accept || '.jpg,.jpeg,.png,.pdf,.docx,.xlsx'"
            :show-file-list="false"
            :on-success="(res: any, file: any) => handleUploadSuccess(res, file, col)"
            :on-error="() => handleUploadError(col)"
          >
            <el-button
              size="small"
              type="primary"
            >
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
          :disabled="isColDisabled(col)"
          popper-class="sub-table-color-popper"
        />

        <!-- rate -->
        <el-rate
          v-else-if="col.type === 'rate'"
          v-model="formData[col.field]"
          :max="col.props?.max || 5"
          :allow-half="col.props?.allowHalf || false"
          :disabled="isColDisabled(col)"
        />

        <!-- slider -->
        <el-slider
          v-else-if="col.type === 'slider'"
          v-model="formData[col.field]"
          :min="col.props?.min ?? 0"
          :max="col.props?.max ?? 100"
          :step="col.props?.step || 1"
          :disabled="isColDisabled(col)"
          style="width: 100%"
        />

        <!-- editor (readonly) -->
        <div
          v-else-if="col.type === 'editor' && isColDisabled(col)"
          class="editor-readonly-ro"
          v-html="sanitizeHtml(formData[col.field] || '')"
        />
        <!-- editor -->
        <div
          v-else-if="col.type === 'editor'"
          class="sub-table-editor-wrapper"
        >
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

        <!-- signature (readonly) -->
        <img
          v-else-if="col.type === 'signature' && isColDisabled(col) && formData[col.field]"
          :src="formData[col.field]"
          class="signature-preview-ro"
          alt="Signature"
        >
        <span
          v-else-if="col.type === 'signature' && isColDisabled(col)"
          class="ro-value"
        >-</span>
        <!-- signature -->
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
            @touchstart.prevent="startSignTouch($event, col.field)"
            @touchmove.prevent="drawSignTouch($event, col.field)"
            @touchend="endSign(col.field)"
          />
          <div style="margin-top: 4px;">
            <el-button
              size="small"
              @click="clearSignature(col.field)"
            >
              {{ t('fieldRenderer.clear') }}
            </el-button>
          </div>
        </div>

        <!-- transfer -->
        <el-transfer
          v-else-if="col.type === 'transfer'"
          v-model="formData[col.field]"
          :data="(col.props?.options ?? col.options ?? []).map((o: any) => ({ key: o.value, label: o.label }))"
          :titles="[col.props?.leftTitle || t('subTable.transferSource'), col.props?.rightTitle || t('subTable.transferTarget')]"
          :filterable="!isColDisabled(col)"
          :disabled="isColDisabled(col)"
        />

        <!-- cascader -->
        <el-cascader
          v-else-if="col.type === 'cascader'"
          v-model="formData[col.field]"
          :options="col.props?.options ?? col.options ?? []"
          :props="col.props?.cascaderProps"
          :placeholder="col.placeholder || col.label"
          :clearable="!isColDisabled(col)"
          :disabled="isColDisabled(col)"
          :teleported="true"
          popper-class="sub-table-date-popper"
          style="width: 100%"
        />

        <!-- lookup -->
        <div
          v-else-if="col.type === 'lookup'"
          class="lookup-field-wrapper"
        >
          <LookupField
            v-model="formData[col.field]"
            :table-id="Number(col.props?.tableId || 0)"
            :search-fields="col.props?.searchFields || []"
            :display-field="col.props?.displayField || ''"
            :display-fields="col.props?.displayFields || []"
            :selected-display-field="getLookupSelectedDisplayField(col)"
            :filter-conditions="col.props?.filterConditions || []"
            :lookup-config="col.props?.lookupConfig"
            :view-fields="col.props?.viewFields || []"
            :placeholder="col.placeholder || col.label"
            :readonly="isColDisabled(col)"
            @select="(row: Record<string, any>) => onLookupSelect(col.field, row)"
            @view-fields-loaded="(fields: any[]) => onLookupViewFieldsLoaded(col.field, fields)"
          />
          <LookupViewDisplay
            v-if="col.props?.showBackfillView !== false && effectiveLookupSelectedRow(col.field)"
            :selected-data="effectiveLookupSelectedRow(col.field)"
            :view-fields="effectiveLookupViewFieldsForDialog(col)"
          />
        </div>

        <!-- user -->
        <el-select
          v-else-if="col.type === 'user'"
          v-model="formData[col.field]"
          :placeholder="col.placeholder || t('subTable.selectUser')"
          filterable
          remote
          :remote-method="(query: string) => handleUserSearch(query, col.field)"
          :loading="userSearchLoading[col.field]"
          :clearable="!isColDisabled(col)"
          :disabled="isColDisabled(col)"
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

        <!-- department -->
        <el-tree-select
          v-else-if="col.type === 'department'"
          v-model="formData[col.field]"
          :data="departmentTreeData"
          :props="({ label: 'name', value: 'id', children: 'children' } as any)"
          :placeholder="col.placeholder || t('subTable.selectDepartment')"
          :loading="departmentLoading"
          check-strictly
          :clearable="!isColDisabled(col)"
          :disabled="isColDisabled(col)"
          filterable
          :teleported="true"
          style="width: 100%"
        />

        <!-- fallback -->
        <el-input
          v-else-if="col.type && !HANDLED_TYPES.has(col.type)"
          v-model="formData[col.field]"
          :placeholder="col.placeholder || col.label"
          :disabled="isColDisabled(col)"
          :clearable="!isColDisabled(col)"
        />
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="handleClose">
        {{ t('common.cancel') }}
      </el-button>
      <el-button
        type="primary"
        :loading="saving"
        @click="handleSave"
      >
        {{ t('common.save') }}
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { Upload } from '@element-plus/icons-vue'
import { Editor, Toolbar } from '@wangeditor/editor-for-vue'
import '@wangeditor/editor/dist/css/style.css'
import { isUploadColumn, getLookupSelectedDisplayField } from './subTableAddDialogHelpers'
import type { DialogColumn } from './subTableAddDialogHelpers'
import type { RowFormulaRule, ValidationRule } from './formRendererHelpers'
import DOMPurify from 'dompurify'
import LookupField from './lookup/LookupField.vue'
import LookupViewDisplay from './lookup/LookupViewDisplay.vue'
import { useSubTableDialogLookup } from '@/composables/subTableAddDialog/useSubTableDialogLookup'
import { useSubTableDialogSignature } from '@/composables/subTableAddDialog/useSubTableDialogSignature'
import { useSubTableDialogEditor } from '@/composables/subTableAddDialog/useSubTableDialogEditor'
import { useSubTableDialogRelations } from '@/composables/subTableAddDialog/useSubTableDialogRelations'
import { useSubTableDialogUpload } from '@/composables/subTableAddDialog/useSubTableDialogUpload'
import { useSubTableDialogForm } from '@/composables/subTableAddDialog/useSubTableDialogForm'

// ─── Component ────────────────────────────────────────────────────────────────

const { t } = useI18n()

/** All field types that have explicit rendering above the fallback.
 *  The fallback el-input / readonly span must NOT render for these types
 *  to avoid double controls for the same field. */
const HANDLED_TYPES = new Set([
  'text', 'textarea', 'number', 'select', 'radio', 'checkbox',
  'password', 'timerange', 'treeselect', 'tree', 'switch', 'date',
  'datetime', 'upload', 'colorPicker', 'rate', 'slider', 'editor',
  'signature', 'transfer', 'cascader', 'lookup', 'user', 'department',
])

/** Sanitize HTML content to prevent XSS */
function sanitizeHtml(html: string): string {
  return DOMPurify.sanitize(html, {
    ALLOWED_TAGS: ['p', 'br', 'strong', 'em', 'u', 's', 'ol', 'ul', 'li',
      'h1', 'h2', 'h3', 'h4', 'h5', 'h6', 'a', 'img', 'table', 'tr', 'td', 'th', 'span', 'div'],
    ALLOWED_ATTR: ['href', 'src', 'alt', 'class', 'style', 'target', 'rel'],
  })
}

const props = defineProps<{
  visible: boolean
  columns: DialogColumn[]
  title?: string
  mode: 'add' | 'edit'
  initialData?: Record<string, any>
  rowFormulas?: RowFormulaRule[]
  columnValidationRules?: Record<string, ValidationRule[]>
  uploadUrl?: string
  /** When set, awaited before closing (supports async PK allocate on Save). */
  saveRow?: (row: Record<string, unknown>) => void | Promise<void>
}>()

const emit = defineEmits<{
  (e: 'update:visible', val: boolean): void
  (e: 'save', rowData: Record<string, any>): void
}>()

// Shared model owned by the SFC and threaded through the composables below.
const formData = ref<Record<string, any>>({})

// ─── Lookup backfill ────────────────────────────────────────────────────────
const {
  effectiveLookupViewFieldsForDialog,
  onLookupViewFieldsLoaded,
  onLookupSelect,
  effectiveLookupSelectedRow,
  resetLookupState,
} = useSubTableDialogLookup(formData)

// ─── Signature canvas ─────────────────────────────────────────────────────────
const {
  signatureCanvasRefs,
  startSign,
  drawSign,
  endSign,
  clearSignature,
  startSignTouch,
  drawSignTouch,
} = useSubTableDialogSignature(formData)

// ─── Editor (wangeditor) ──────────────────────────────────────────────────────
const {
  editorInstances,
  onEditorCreated,
  onEditorChange,
  destroyEditors,
} = useSubTableDialogEditor(formData)

// ─── User search + department tree ────────────────────────────────────────────
const {
  userSearchOptions,
  userSearchLoading,
  handleUserSearch,
  departmentTreeData,
  departmentLoading,
  fetchDepartmentTree,
} = useSubTableDialogRelations()

// ─── Upload helpers ───────────────────────────────────────────────────────────
const {
  uploadNames,
  resetUploadNames,
  backfillUploadNames,
  handleUploadSuccess,
  handleUploadError,
  clearUpload,
} = useSubTableDialogUpload(formData, () => props.columns, t)

// ─── Form core (state / rules / formulas / validation / open / save) ───────────
const {
  formRef,
  saving,
  dialogKey,
  formRules,
  isColDisabled,
  columnErrors,
  handleClose,
  handleSave,
} = useSubTableDialogForm(props, emit, t, {
  formData,
  resetUploadNames,
  backfillUploadNames,
  resetLookupState,
  destroyEditors,
  fetchDepartmentTree,
})
</script>

<style>
@use '@/styles/form-readonly.scss';

/* 遮罩经 Teleport 挂 body；z-index 2009 低于 dialog(2010)，picker 用 popper-class 抬到 2050 */
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

.lookup-field-wrapper {
  width: 100%;
}

/* ── Read-only display in dialog ────────────────────────────────────── */
.ro-value {
  display: flex;
  align-items: center;
  width: 100%;
  box-sizing: border-box;
  min-height: 32px;
  padding: 0 11px;
  color: var(--el-disabled-text-color, #a8abb2);
  font-size: 14px;
  line-height: 1.5;
  word-break: break-word;
  background: var(--el-disabled-bg-color, #f5f7fa);
  border-radius: 4px;
  border: 1px solid var(--el-disabled-border-color, #e4e7ed);
  cursor: not-allowed;
  pointer-events: none;
}

.tree-readonly {
  opacity: 0.7;
  pointer-events: none;
  cursor: not-allowed;
}

.color-swatch-ro {
  display: inline-block;
  width: 24px;
  height: 24px;
  border-radius: 4px;
  border: 1px solid #dcdfe6;
  vertical-align: middle;
}

.signature-preview-ro {
  max-width: 200px;
  max-height: 80px;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
}

.editor-readonly-ro {
  padding: 8px 12px;
  min-height: 60px;
  max-height: 200px;
  overflow-y: auto;
  background: #f5f7fa;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  line-height: 1.6;
  color: #606266;
}

.upload-download-link {
  color: #409eff;
  text-decoration: none;
}
.upload-download-link:hover {
  text-decoration: underline;
}
</style>
