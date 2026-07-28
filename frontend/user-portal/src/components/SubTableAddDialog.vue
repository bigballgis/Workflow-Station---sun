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
    :width="dialogWidth"
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
      <template
        v-for="group in dialogLayoutGroups"
        :key="group.key"
      >
        <component
          :is="group.title !== null ? 'el-card' : 'div'"
          v-bind="group.title !== null ? { shadow: 'never' } : {}"
          :class="group.title !== null ? 'sub-table-dialog-card' : undefined"
        >
          <template
            v-if="group.title"
            #header
          >
            <span class="sub-table-dialog-card-title">{{ group.title }}</span>
          </template>
          <template
            v-for="col in group.columns"
            :key="col.field"
          >
        <!-- MI 场景 C：分派方式 radio 插在分派字段组（Assignee/BU/Role）正上方 -->
        <el-form-item
          v-if="showAssignModeRadio && col.field === firstAssignField"
          :label="t('subTable.assignMode')"
        >
          <el-radio-group
            v-model="assignMode"
            @change="onAssignModeChange"
          >
            <el-radio value="person">
              {{ t('subTable.assignByPerson') }}
            </el-radio>
            <el-radio value="role">
              {{ t('subTable.assignByRole') }}
            </el-radio>
          </el-radio-group>
        </el-form-item>

        <el-form-item
          :label="col.label"
          :prop="col.field"
          :error="columnErrors[col.field]?.join('; ')"
        >
        <!-- MI 按角色分派：BU 级联树选择（父 BU 可展开子 BU，与 admin 一致；按 field 名抢先匹配） -->
        <el-cascader
          v-if="col.field === 'bu_code'"
          v-model="selectedBuId"
          :options="buTree"
          :props="(buCascaderProps as any)"
          :placeholder="col.placeholder || t('subTable.selectBusinessUnit')"
          :disabled="isColDisabled(col)"
          filterable
          clearable
          :teleported="true"
          style="width: 100%"
          @change="(v: any) => onBuChange(v)"
        />

        <!-- MI 按角色分派：Role 选择（选项随所选 BU 收敛） -->
        <el-select
          v-else-if="col.field === 'role_code'"
          v-model="formData[col.field]"
          :placeholder="col.placeholder || t('subTable.selectRole')"
          :loading="roleLoading"
          :clearable="!isColDisabled(col)"
          :disabled="isColDisabled(col) || !formData['bu_code']"
          filterable
          :teleported="true"
          style="width: 100%"
          @change="(v: string) => onRoleChange(v)"
        >
          <el-option
            v-for="opt in roleOptions"
            :key="opt.value"
            :label="opt.label"
            :value="opt.value"
          />
        </el-select>

        <!-- text -->
        <el-input
          v-else-if="(!col.type || col.type === 'text') && !isUploadColumn(col, formData[col.field])"
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
            :accept="col.props?.accept || ''"
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
            :filter-conditions="effectiveLookupFilterConditions(col)"
            :lookup-config="col.props?.lookupConfig"
            :view-fields="col.props?.viewFields || []"
            :placeholder="col.placeholder || col.label"
            :readonly="isColDisabled(col)"
            :multiple="col.props?.multiple === true"
            @select="(row: Record<string, any>) => onLookupSelect(col.field, row)"
            @clear="() => onLookupClear(col.field)"
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
          </template>
        </component>
      </template>
    </el-form>

    <!-- Nested sub-tables placed in this binding's form design (sub-table-in-sub-table) -->
    <div
      v-for="nested in nestedSubTables || []"
      :key="`nested-sub-table-${nested.bindingId}`"
      class="dialog-nested-sub-table"
    >
      <NestedSubTableField
        :title="nested.tableName"
        :columns="nested.columns"
        :dialog-columns="nested.dialogColumns"
        :form-fields="nested.formFields"
        :model-value="nestedRowsFor(nested)"
        :primary-key-fields="nested.primaryKeyFields"
        :upload-url="uploadUrl"
        editable
        :allow-add="nested.allowAdd"
        :allow-edit="nested.allowEdit"
        :allow-delete="nested.allowDelete"
        :table-id="nested.tableId ?? null"
        :field-definitions="nested.fieldDefinitions"
        :function-unit-id="hostFunctionUnitId"
        :task-id="hostTaskId"
        :binding-link-mode="nested.bindingMode"
        :binding-foreign-key-field="nested.foreignKeyField"
        :parent-row="formData"
        :parent-table-id="hostTableId ?? null"
        :parent-tables-by-id="nestedParentTablesById"
        :primary-form-data="hostPrimaryFormData"
        :primary-table-id="hostPrimaryTableId ?? null"
        :primary-table-display-name="hostPrimaryTableDisplayName"
        :sub-table-bindings-for-context="hostSubTableBindingsForContext"
        :linked-sub-table-bindings="hostLinkedSubTableBindings"
        @update:model-value="(nestedRows: unknown[]) => onNestedRowsUpdate(nested, nestedRows)"
        @update:parent-row="onNestedParentRowPatch"
      />
    </div>

    <!-- RecordNote panels from this binding's form design: RECORD scope binds the
         edited row (disabled until the row is saved), TABLE scope binds this
         sub-table's shared stream within the current process. -->
    <div
      v-for="rn in recordNoteFields || []"
      :key="rn.key"
      class="dialog-record-note"
    >
      <RecordNoteField
        :config="rn._recordNote"
        :table-id="recordNoteTableId ?? null"
        :record-id="editingRowStableId"
        :process-instance-id="recordNoteInstanceId ?? null"
        :function-unit-id="recordNoteFunctionUnitId ?? null"
      />
    </div>

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
import { computed, defineAsyncComponent, ref, toRef, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { Upload } from '@element-plus/icons-vue'
import { Editor, Toolbar } from '@wangeditor/editor-for-vue'
import '@wangeditor/editor/dist/css/style.css'
import { isUploadColumn, getLookupSelectedDisplayField } from './subTableAddDialogHelpers'
import type { DialogColumn } from './subTableAddDialogHelpers'
import { buildDialogLayoutGroups } from './subTableAddDialogHelpers/dialogFormLayout'
import type { FormField, RowFormulaRule, ValidationRule } from './formRendererHelpers'
import { resolveRowStableId } from './formRendererHelpers/recordNoteFields'
import RecordNoteField from './RecordNoteField.vue'
import DOMPurify from 'dompurify'
import LookupField from './lookup/LookupField.vue'
import LookupViewDisplay from './lookup/LookupViewDisplay.vue'
import { useSubTableDialogLookup } from '@/composables/subTableAddDialog/useSubTableDialogLookup'
import { useSubTableBuRoleCascade } from '@/composables/subTableAddDialog/useSubTableBuRoleCascade'
import { useSubTableDialogSignature } from '@/composables/subTableAddDialog/useSubTableDialogSignature'
import { useSubTableDialogEditor } from '@/composables/subTableAddDialog/useSubTableDialogEditor'
import { useSubTableDialogRelations } from '@/composables/subTableAddDialog/useSubTableDialogRelations'
import { useSubTableDialogUpload } from '@/composables/subTableAddDialog/useSubTableDialogUpload'
import { useSubTableDialogForm } from '@/composables/subTableAddDialog/useSubTableDialogForm'
import { mergeNestedSubTableRowsIntoSto } from './formRendererHelpers'
import { pullNestedRowsForBindingFromParentRows } from '@/composables/tasks/subTableNestedRows'
import type { NestedSubTableDescriptor, SubTableBinding } from '@/composables/subTableField/subTableFieldTypes'
import type { BindingFieldDefinition } from '@/utils/subTableRowRuntime'

// SubTableField hosts this dialog and the dialog hosts nested SubTableField — resolve the
// circular SFC pair lazily.
const NestedSubTableField = defineAsyncComponent(() => import('./SubTableField.vue'))

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
  /** List-view columns for audit auto-fill on save (created_at / updated_at etc.). */
  auditColumns?: DialogColumn[]
  /**
   * Designer form-field tree for this binding (includes elCard). When present with cards,
   * Add/Edit dialog wraps fields in the same card layout as DW Form Preview.
   */
  formFields?: FormField[]
  title?: string
  mode: 'add' | 'edit'
  initialData?: Record<string, any>
  rowFormulas?: RowFormulaRule[]
  columnValidationRules?: Record<string, ValidationRule[]>
  uploadUrl?: string
  /** Nested sub-tables from this binding's form design — rows save under the row's `__subTables__`. */
  nestedSubTables?: NestedSubTableDescriptor[]
  /**
   * Host binding's FK/PK runtime context, forwarded to nested sub-tables so a grandchild row
   * goes through the same allocate/seed path as a top-level one: `host*` describes the row this
   * dialog edits (it is the nested rows' parent), the rest is the host's own ancestor chain.
   */
  hostTableId?: number | null
  hostFieldDefinitions?: BindingFieldDefinition[]
  hostFunctionUnitId?: string
  hostTaskId?: string
  hostPrimaryFormData?: Record<string, unknown>
  hostPrimaryTableId?: number | null
  hostPrimaryTableDisplayName?: string
  hostSubTableBindingsForContext?: Array<{
    tableId?: number | null
    bindingType?: string
    tableName?: string
    tableDisplayName?: string
  }>
  hostParentTablesById?: Record<number, { fieldDefinitions: BindingFieldDefinition[] }>
  hostLinkedSubTableBindings?: SubTableBinding[]
  /** When set, awaited before closing (supports async PK allocate on Save). */
  saveRow?: (row: Record<string, unknown>) => void | Promise<void>
  /** RecordNote components placed in this binding's form design. */
  recordNoteFields?: FormField[]
  recordNoteTableId?: number | string | null
  /** Current process instance id — TABLE scope stream anchor. */
  recordNoteInstanceId?: string | null
  recordNoteFunctionUnitId?: string | number | null
  /** This sub-table's PK fields — resolve the edited row's stable id for RECORD scope. */
  primaryKeyFields?: string[]
}>()

const emit = defineEmits<{
  (e: 'update:visible', val: boolean): void
  (e: 'save', rowData: Record<string, any>): void
}>()

// Shared model owned by the SFC and threaded through the composables below.
const formData = ref<Record<string, any>>({})

// Stable identity of the row being edited — RECORD-scope note anchor. Resolution
// mirrors subTableRowMerge: declared PK first, then rowId, then the platform
// id / id_idw alias pair. New (unsaved) rows have none: the panel shows its
// "available after save" hint.
const editingRowStableId = computed<string | null>(() =>
  props.mode === 'edit' ? resolveRowStableId(formData.value, props.primaryKeyFields) : null)

// ─── Nested sub-tables (sub-table-in-sub-table inside the row dialog) ────────
/**
 * A nested sub-table brings a whole table (plus its Operation column) into the dialog, which
 * a 600px form-sized shell cannot fit — its rightmost columns land outside the viewport.
 * Widen the shell whenever nested tables are present; plain field-only rows keep 600px.
 */
const dialogWidth = computed(() =>
  props.nestedSubTables?.length ? 'min(1100px, calc(100vw - 48px))' : '600px')

/** Rows for one nested table, read from the edited row's `__subTables__` (alias keys). */
function nestedRowsFor(nested: NestedSubTableDescriptor): Record<string, unknown>[] {
  return pullNestedRowsForBindingFromParentRows(
    {
      bindingId: nested.bindingId,
      tableName: nested.tableName,
      physicalTableName: nested.physicalTableName,
      tableId: nested.tableId ?? null,
    },
    [formData.value],
  )
}

/** Write edited nested rows back into formData so Save carries them on the row. */
function onNestedRowsUpdate(nested: NestedSubTableDescriptor, nestedRows: unknown[]) {
  formData.value = {
    ...formData.value,
    __subTables__: mergeNestedSubTableRowsIntoSto([formData.value], nested, nestedRows),
  }
}

/**
 * The edited row is the nested rows' parent. Saving a grandchild forces this row's auto PK to
 * be allocated early (the child's FK needs it); adopt that value here so Save persists the row
 * under the very key the child now references instead of allocating a second one.
 */
function onNestedParentRowPatch(patch: Record<string, unknown>) {
  const next = { ...formData.value }
  let changed = false
  for (const [key, value] of Object.entries(patch)) {
    if (key === '__subTables__') continue
    const current = next[key]
    if (current != null && String(current).trim() !== '') continue
    if (value == null || String(value).trim() === '') continue
    next[key] = value
    changed = true
  }
  if (changed) formData.value = next
}

/** Host row's own table joins the ancestor pool so a grandchild FK to it can be auto-filled. */
const nestedParentTablesById = computed(() => {
  const base = { ...(props.hostParentTablesById ?? {}) }
  if (props.hostTableId != null && props.hostFieldDefinitions?.length) {
    base[Number(props.hostTableId)] = { fieldDefinitions: props.hostFieldDefinitions }
  }
  return base
})

// ─── Lookup backfill ────────────────────────────────────────────────────────
const {
  effectiveLookupViewFieldsForDialog,
  effectiveLookupFilterConditions,
  onLookupViewFieldsLoaded,
  onLookupSelect,
  onLookupClear,
  effectiveLookupSelectedRow,
  resetLookupState,
} = useSubTableDialogLookup(formData, toRef(props, 'columns'))

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

// ─── BU→Role 级联（MI 子任务「按角色分派」）+ 与 assignee 行级互斥 ──────────────
const {
  buTree,
  buCascaderProps,
  selectedBuId,
  roleOptions,
  buLoading,
  roleLoading,
  loadBusinessUnits,
  onBuChange,
  onRoleChange,
  primeFromExistingRow,
} = useSubTableBuRoleCascade(formData)

const hasBuRoleColumns = () =>
  props.columns.some(c => c.field === 'bu_code' || c.field === 'role_code')

// 弹窗打开时，若该子表含 bu_code/role_code 列，加载 BU 列表（编辑态再预热已选 BU 的 role）。
watch(
  () => props.visible,
  (visible) => {
    if (!visible || !hasBuRoleColumns()) return
    if (props.mode === 'edit') {
      primeFromExistingRow()
    } else {
      loadBusinessUnits()
    }
  },
  { immediate: true }
)

// ─── MI 分派方式 radio（个人 / 角色，二选一显隐，互斥）──────────────────────────
const hasAssigneeCol = computed(() => props.columns.some(c => c.field === 'assignee'))
const hasRoleCol = computed(() => props.columns.some(c => c.field === 'role_code' || c.field === 'bu_code'))
// 场景 C：同时提供个人与角色两种录入方式，才需要 radio 二选一。
const showAssignModeRadio = computed(() => hasAssigneeCol.value && hasRoleCol.value)
const assignMode = ref<'person' | 'role'>('person')

// 打开/切数据时确定初始 radio：已填 role/bu → role，否则 person。
watch(
  () => [props.visible, props.mode, props.initialData] as const,
  ([visible]) => {
    if (!visible || !showAssignModeRadio.value) return
    const d = (props.initialData || {}) as Record<string, any>
    const roleFilled = (d.role_code && String(d.role_code).trim()) || (d.bu_code && String(d.bu_code).trim())
    assignMode.value = roleFilled ? 'role' : 'person'
  },
  { immediate: true }
)

// radio 切换：清掉另一种方式的值，避免残留导致两种并存。
function onAssignModeChange(mode: string | number | boolean | undefined) {
  if (mode === 'person') {
    formData.value.bu_code = ''
    formData.value.role_code = ''
  } else {
    formData.value.assignee = ''
  }
}

// 按 radio 过滤要渲染的列：场景 C 下 person 隐藏 bu/role，role 隐藏 assignee；A/B 全显。
const ROLE_GROUP_FIELDS = ['bu_code', 'role_code']
const visibleColumns = computed(() => {
  if (!showAssignModeRadio.value) return props.columns
  return props.columns.filter(c => {
    if (assignMode.value === 'person') return !ROLE_GROUP_FIELDS.includes(c.field)
    return c.field !== 'assignee'
  })
})

/** DW Form Preview parity: group columns under designer elCard titles when present. */
const dialogLayoutGroups = computed(() =>
  buildDialogLayoutGroups(props.formFields, visibleColumns.value),
)

// 分派字段组的首列（radio 插在它正上方，与分派字段成一体）。
const ASSIGN_FIELDS = new Set(['assignee', 'bu_code', 'role_code'])
const firstAssignField = computed(() => {
  const c = visibleColumns.value.find(col => ASSIGN_FIELDS.has(col.field))
  return c?.field || ''
})

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

/* Adjacent designer cards — same 10px gap as FormRenderer / DW Preview */
.sub-table-dialog-card {
  width: 100%;
  margin-bottom: 10px;
}

.sub-table-dialog-card-title {
  font-weight: 500;
  color: #303133;
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
.dialog-nested-sub-table {
  margin-top: 8px;
  margin-bottom: 8px;
}
</style>
