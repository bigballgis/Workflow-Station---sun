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
      :label-width="stableLabelWidth"
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
            v-for="item in group.items"
            :key="item.key"
          >
            <!-- Assignment Mode block: routing this row to a person or to a role pool.
             The mode cards and the picker the chosen mode needs live in one box, so
             the block is never an empty frame (see mi-assignment-block CSS). -->
            <div
              v-if="item.type === 'miAssignment' && showAssignmentBlock"
              class="mi-assignment-block__head"
            >
              <div class="mi-assignment-block__title">
                {{ t('subTable.assignMode') }}
              </div>
              <div
                class="mi-assignment-block__modes"
                role="radiogroup"
                :aria-label="t('subTable.assignMode')"
              >
                <button
                  v-for="option in assignModeOptions"
                  :key="option.value"
                  type="button"
                  role="radio"
                  :aria-checked="assignMode === option.value"
                  :aria-disabled="isModeCardDisabled(option.value)"
                  class="mi-assignment-mode-card"
                  :class="{
                    'is-selected': assignMode === option.value,
                    'is-disabled': isModeCardDisabled(option.value),
                  }"
                  @click="selectAssignMode(option.value)"
                >
                  <span class="mi-assignment-mode-card__dot" />
                  <span class="mi-assignment-mode-card__text">
                    <span class="mi-assignment-mode-card__name">{{ t(option.label) }}</span>
                    <span class="mi-assignment-mode-card__hint">{{ t(option.hint) }}</span>
                  </span>
                </button>
              </div>
            </div>

            <template v-else-if="item.type === 'column'">
              <el-form-item
                :label="item.column.label"
                :prop="item.column.field"
                :error="columnErrorMessages[item.column.field]"
                :class="{
                  'mi-assignment-block__field': !!item.assignmentSlot,
                  'mi-assignment-block__field--last': item.assignmentSlot === 'last',
                }"
              >
                <!-- Keep the existing control branches scoped to the ordered column item. -->
                <template
                  v-for="col in [item.column]"
                  :key="col.field"
                >
                  <!-- MI 按角色分派：BU 级联树选择（父 BU 可展开子 BU，与 admin 一致；按 field 名抢先匹配） -->
                  <el-cascader
                    v-if="col.field === configuredBuField"
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
                    v-else-if="configuredBuField && col.field === configuredRoleField"
                    v-model="formData[col.field]"
                    :placeholder="col.placeholder || t('subTable.selectRole')"
                    :loading="roleLoading"
                    :clearable="!isColDisabled(col)"
                    :disabled="isColDisabled(col) || !formData[configuredBuField]"
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

                  <!-- text (sensitive mask is display-only; formData stays plaintext) -->
                  <el-input
                    v-else-if="(!col.type || col.type === 'text') && !isUploadColumn(col, formData[col.field])"
                    :model-value="textDisplay(col)"
                    :placeholder="col.placeholder || col.label"
                    :maxlength="col.props?.maxlength"
                    :disabled="isColDisabled(col)"
                    :readonly="showMasked(col) && !isColDisabled(col)"
                    :clearable="!isColDisabled(col) && !showMasked(col)"
                    @update:model-value="(v: string) => onTextUpdate(col, v)"
                    @change="() => onDialogFieldChange(col.field)"
                    @focus="onTextFocus(col)"
                    @blur="() => { onTextBlur(col); onDialogFieldBlur(col.field) }"
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
                    @change="() => onDialogFieldChange(col.field)"
                    @blur="() => onDialogFieldBlur(col.field)"
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
                    @change="(v: number | undefined) => onDialogFieldChange(col.field, v)"
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
                    @change="(v: unknown) => onDialogFieldChange(col.field, v)"
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
                    @change="(v: unknown) => onDialogFieldChange(col.field, v)"
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
                    @change="(v: unknown) => onDialogFieldChange(col.field, v)"
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
                    @change="() => onDialogFieldChange(col.field)"
                    @blur="() => onDialogFieldBlur(col.field)"
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
                    @change="(v: unknown) => onDialogFieldChange(col.field, v)"
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
                    @change="(v: unknown) => onDialogFieldChange(col.field, v)"
                  />

                  <!-- tree -->
                  <el-tree
                    v-else-if="col.type === 'tree'"
                    :data="col.props?.treeData || []"
                    :props="col.props?.labelProps || { label: 'label', children: 'children' }"
                    :node-key="col.props?.nodeKey || 'id'"
                    :show-checkbox="col.props?.showCheckbox !== false && !isColDisabled(col)"
                    :class="{ 'tree-readonly': isColDisabled(col) }"
                    @check="(_node: any, state: any) => {
                      if (isColDisabled(col)) return
                      formData[col.field] = state.checkedKeys
                      onDialogFieldChange(col.field, state.checkedKeys)
                    }"
                  />

                  <!-- switch -->
                  <el-switch
                    v-else-if="col.type === 'switch'"
                    v-model="formData[col.field]"
                    :disabled="isColDisabled(col)"
                    @change="(v: unknown) => onDialogFieldChange(col.field, v)"
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
                    @change="(v: unknown) => onDialogFieldChange(col.field, v)"
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
                    @change="(v: unknown) => onDialogFieldChange(col.field, v)"
                  />

                  <!-- upload (readonly) -->
                  <div
                    v-else-if="isUploadColumn(col, formData[col.field]) && isColDisabled(col)"
                    class="ro-value"
                  >
                    <span
                      v-if="formData[col.field]"
                      class="upload-download-link"
                      @click="previewDialogFile(col)"
                    >{{ getFilenameFromUrl(formData[col.field], uploadNames[col.field]) }}</span>
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
                      class="upload-filename-tag"
                      closable
                      @click="previewDialogFile(col)"
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
                    @change="(v: unknown) => onDialogFieldChange(col.field, v)"
                  />

                  <!-- rate -->
                  <el-rate
                    v-else-if="col.type === 'rate'"
                    v-model="formData[col.field]"
                    :max="col.props?.max || 5"
                    :allow-half="col.props?.allowHalf || false"
                    :disabled="isColDisabled(col)"
                    @change="(v: unknown) => onDialogFieldChange(col.field, v)"
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
                    @change="(v: unknown) => onDialogFieldChange(col.field, v)"
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
                    @change="(v: unknown) => onDialogFieldChange(col.field, v)"
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
                    @change="(v: unknown) => onDialogFieldChange(col.field, v)"
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
                      @select="(row: Record<string, any>) => onLookupSelectWithEvents(col.field, row)"
                      @clear="() => onLookupClearWithEvents(col.field)"
                      @view-fields-loaded="(fields: any[]) => onLookupViewFieldsLoaded(col.field, fields)"
                    />
                    <LookupViewDisplay
                      v-if="col.props?.showBackfillView !== false && effectiveLookupSelectedRow(col.field)"
                      :selected-data="effectiveLookupSelectedRow(col.field)"
                      :view-fields="effectiveLookupViewFieldsForDialog(col)"
                    />
                  </div>

                  <!-- owner: readonly; backend fills Creator / Current Assignee -->
                  <OwnerField
                    v-else-if="col.type === 'owner'"
                    :model-value="formData[col.field]"
                    :owner-config="col.props?.ownerConfig as string | undefined"
                    :display="typeof formData[`${col.field}__display`] === 'string' ? formData[`${col.field}__display`] : ''"
                    readonly
                  />

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
                    @change="(v: unknown) => onDialogFieldChange(col.field, v)"
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
                    @change="(v: unknown) => onDialogFieldChange(col.field, v)"
                  />

                  <!-- fallback -->
                  <el-input
                    v-else-if="col.type && !HANDLED_TYPES.has(col.type)"
                    v-model="formData[col.field]"
                    :placeholder="col.placeholder || col.label"
                    :disabled="isColDisabled(col)"
                    :clearable="!isColDisabled(col)"
                  />
                </template>
              </el-form-item>
            </template>
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
        :form-options="nested.formOptions"
        :assignment-config="nested.assignmentConfig"
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
        :binding-id="nested.bindingId"
        :field-permissions="fieldPermissions"
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
import { computed, defineAsyncComponent, nextTick, ref, toRef, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { Upload } from '@element-plus/icons-vue'
import { Editor, Toolbar } from '@wangeditor/editor-for-vue'
import '@wangeditor/editor/dist/css/style.css'
import { isUploadColumn, getLookupSelectedDisplayField } from './subTableAddDialogHelpers'
import type { DialogColumn } from './subTableAddDialogHelpers'
import { getFilenameFromUrl } from '@/composables/subTableField/useSubTableFileDownload'
import { openFilePreview } from '@/composables/filePreview/useFilePreview'
import { uploadPropsBlockDownload } from '@/utils/filePreview'
import {
  buildDialogLayoutGroups,
  groupAssignmentFieldsUnderMarker,
} from './subTableAddDialogHelpers/dialogFormLayout'
import type { FormField, RowFormulaRule, ValidationRule } from './formRendererHelpers'
import { resolveRowStableId } from './formRendererHelpers/recordNoteFields'
import RecordNoteField from './RecordNoteField.vue'
import DOMPurify from 'dompurify'
import LookupField from './lookup/LookupField.vue'
import OwnerField from './owner/OwnerField.vue'
import LookupViewDisplay from './lookup/LookupViewDisplay.vue'
import { useSubTableDialogLookup } from '@/composables/subTableAddDialog/useSubTableDialogLookup'
import { useSubTableBuRoleCascade } from '@/composables/subTableAddDialog/useSubTableBuRoleCascade'
import { useSubTableDialogSignature } from '@/composables/subTableAddDialog/useSubTableDialogSignature'
import { useSubTableDialogEditor } from '@/composables/subTableAddDialog/useSubTableDialogEditor'
import { useSubTableDialogRelations } from '@/composables/subTableAddDialog/useSubTableDialogRelations'
import { useSubTableDialogUpload } from '@/composables/subTableAddDialog/useSubTableDialogUpload'
import { useSubTableDialogForm } from '@/composables/subTableAddDialog/useSubTableDialogForm'
import { useSubTableDialogComponentEvents } from '@/composables/subTableAddDialog/useSubTableDialogComponentEvents'
import { useSubTableDialogSensitiveMask } from '@/composables/subTableAddDialog/useSubTableDialogSensitiveMask'
import { mergeNestedSubTableRowsIntoSto } from './formRendererHelpers'
import { pullNestedRowsForBindingFromParentRows } from '@/composables/tasks/subTableNestedRows'
import type { NestedSubTableDescriptor, SubTableBinding } from '@/composables/subTableField/subTableFieldTypes'
import type { BindingFieldDefinition } from '@/utils/subTableRowRuntime'
import {
  fieldsHiddenByMode,
  fieldsOwnedByMode,
  isAssignModeSwitchable,
  isAssignmentConfigured,
  lockedAssignMode,
  resolveAssignModeFromRow,
  type AssignmentConfig,
  type AssignmentMode,
} from '@/utils/miAssignmentConfig'

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
  'signature', 'transfer', 'cascader', 'lookup', 'user', 'department', 'owner',
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
  /** Sub-form Form Design options (onCreated / onMounted / …). */
  formOptions?: Record<string, unknown> | null
  /** BPMN-derived MI assignment contract for this binding. */
  assignmentConfig?: AssignmentConfig
  title?: string
  mode: 'add' | 'edit'
  initialData?: Record<string, any>
  rowFormulas?: RowFormulaRule[]
  /** This binding's own table columns — supplies the computed (formula) column definitions. */
  fieldDefinitions?: BindingFieldDefinition[]
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
  /** This dialog's own hosting binding id — resolves this binding's `${bindingId}:${fieldName}` entries in fieldPermissions. */
  bindingId?: number | null
  /**
   * Task-node field permissions. Gates this dialog's own top-level columns (via isColDisabled in
   * useSubTableDialogForm) AND is forwarded to nested sub-tables-in-sub-tables so their fields get
   * the same enforcement.
   */
  fieldPermissions?: Record<string, string> | null
}>()

const emit = defineEmits<{
  (e: 'update:visible', val: boolean): void
  (e: 'save', rowData: Record<string, any>): void
}>()

// Shared model owned by the SFC and threaded through the composables below.
const formData = ref<Record<string, any>>({})

const {
  onDialogFieldChange,
  onDialogFieldBlur,
  isDialogFieldVisible,
  resetDialogEventVisibility,
  bootstrapDialogFormLifecycle,
  runFormOnReload,
  runFormBeforeSubmit,
  runFormOnSubmit,
  runFormOnReset,
} = useSubTableDialogComponentEvents(
  formData,
  () => props.columns,
  () => props.formOptions,
)

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

async function onLookupSelectWithEvents(field: string, row: Record<string, any>) {
  await onLookupSelect(field, row)
  onDialogFieldChange(field, formData.value[field])
}

function onLookupClearWithEvents(field: string) {
  onLookupClear(field)
  onDialogFieldChange(field, formData.value[field])
}

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

function previewDialogFile(col: DialogColumn) {
  const url = String(formData.value[col.field] || '')
  if (!url) return
  openFilePreview({
    url,
    name: getFilenameFromUrl(url, uploadNames.value[col.field]),
    cannotDownload: uploadPropsBlockDownload(col.props),
  })
}

// ─── BU→Role 级联（MI 子任务「按角色分派」）+ 与 assignee 行级互斥 ──────────────
const {
  buTree,
  buCascaderProps,
  selectedBuId,
  roleOptions,
  roleLoading,
  loadBusinessUnits,
  onBuChange,
  onRoleChange,
  primeFromExistingRow,
} = useSubTableBuRoleCascade(formData, toRef(props, 'assignmentConfig'))

/**
 * The BPMN contract supplies the block's CONTENT (which modes, which fields); the
 * designer's `miAssignment` marker decides whether it renders at all and where.
 * A sub-form that never placed the component shows no block — the design is the
 * only truth, same rule the dialog's column set follows.
 */
const effectiveAssignmentConfig = computed(() =>
  isAssignmentConfigured(props.assignmentConfig) ? props.assignmentConfig : undefined)
const configuredBuField = computed(() => effectiveAssignmentConfig.value?.buField || '')
const configuredRoleField = computed(() => effectiveAssignmentConfig.value?.roleField || '')

const hasConfiguredBuRoleColumns = () =>
  !!configuredBuField.value
  && !!configuredRoleField.value
  && props.columns.some(c => c.field === configuredBuField.value)
  && props.columns.some(c => c.field === configuredRoleField.value)

// 弹窗打开时，仅在 AssignmentConfig 指定 BU/Role 字段后加载目录；编辑态预热已选 BU 的 role。
watch(
  () => props.visible,
  (visible) => {
    if (!visible || !hasConfiguredBuRoleColumns()) return
    if (props.mode === 'edit') {
      primeFromExistingRow()
    } else {
      loadBusinessUnits()
    }
  },
  { immediate: true }
)

// ─── MI 分派方式卡片（个人 / 角色，两张卡片恒显示，按 BPMN 单/双模式锁定切换）──────
const assignModeSwitchable = computed(() =>
  isAssignModeSwitchable(effectiveAssignmentConfig.value))
const lockedMode = computed(() =>
  lockedAssignMode(effectiveAssignmentConfig.value))
/** BPMN configured only one mode — the other card renders but is not selectable. */
function isModeCardDisabled(value: AssignmentMode): boolean {
  return !assignModeSwitchable.value && lockedMode.value !== value
}
/**
 * Render the Assignment Mode block for any configured MI sub-table — including
 * single-mode setups, where both cards still show (one locked) framing the
 * assignee (or BU + role) picker the block owns.
 */
const showAssignmentBlock = computed(() =>
  isAssignmentConfigured(effectiveAssignmentConfig.value))
const assignMode = ref<AssignmentMode>('person')

const assignModeOptions = [
  { value: 'person' as const, label: 'subTable.assignByPerson', hint: 'subTable.assignByPersonHint' },
  { value: 'role' as const, label: 'subTable.assignByRole', hint: 'subTable.assignByRoleHint' },
]

function selectAssignMode(mode: AssignmentMode) {
  if (isModeCardDisabled(mode)) return
  if (assignMode.value === mode) return
  assignMode.value = mode
  onAssignModeChange(mode)
}

// 打开/切数据时确定初始卡片：单模式恒定到 BPMN 配置的那一种；双模式时已填 role/bu → role，否则 person。
watch(
  () => [props.visible, props.mode, props.initialData] as const,
  ([visible]) => {
    if (!visible || !effectiveAssignmentConfig.value) return
    if (!assignModeSwitchable.value) {
      assignMode.value = lockedMode.value ?? 'person'
      return
    }
    const d = (props.initialData || {}) as Record<string, any>
    assignMode.value = resolveAssignModeFromRow(d, effectiveAssignmentConfig.value)
  },
  { immediate: true }
)

// radio 切换：清掉另一种方式的值，避免残留导致两种并存。
function onAssignModeChange(mode: string | number | boolean | undefined) {
  const config = effectiveAssignmentConfig.value
  if (!config) return
  // Defense in depth: selectAssignMode already blocks the locked card's click,
  // but this setter must not trust an unexpected call either.
  if ((mode === 'person' || mode === 'role') && isModeCardDisabled(mode)) return
  if (mode === 'person') {
    if (config.buField) formData.value[config.buField] = ''
    if (config.roleField) formData.value[config.roleField] = ''
  } else if (mode === 'role' && config.assigneeField) {
    formData.value[config.assigneeField] = ''
  }
}

// Also drop fields hidden by Form Design component events (api.hidden / api.display).
const visibleColumns = computed(() => {
  const config = effectiveAssignmentConfig.value
  const byAssign = !config
    ? props.columns
    : props.columns.filter(column => !fieldsHiddenByMode(assignMode.value, config).has(column.field))
  return byAssign.filter(c => isDialogFieldVisible(c.field))
})

/**
 * Switching assignment mode swaps which fields exist, and `label-width="auto"` then
 * re-measures against a different set — "Business Unit" is wider than "Assignee", so
 * every other row's input edge jumped ~29px sideways on each toggle.
 *
 * Fix: after each render, take the widest label the form has EVER shown and pin the
 * column to it. Measuring real rendered labels (rather than estimating from character
 * widths) keeps the repo's "labels never wrap" rule intact across fonts and locales,
 * and the high-water mark means the width only ever grows — so toggling modes cannot
 * move anything. See portal-dialog-form-labels.
 */
const stableLabelWidth = ref<string>('auto')

/**
 * Measure every label the dialog can show — including the ones the current mode hides —
 * against a detached span using the real label font. Measuring the rendered labels does
 * not work: with `label-width: auto` Element Plus writes an inline width onto each
 * label, so their scrollWidth reports the constrained value, never the natural one.
 */
function syncStableLabelWidth() {
  const el = formRef.value?.$el as HTMLElement | undefined
  if (!el) return
  const sample = el.querySelector<HTMLElement>('.el-form-item__label')
  const texts = props.columns.map(c => c.label || c.field).filter(Boolean)
  if (texts.length === 0) return

  const ruler = document.createElement('span')
  const font = sample ? getComputedStyle(sample) : null
  ruler.style.cssText =
    `position:absolute;visibility:hidden;white-space:nowrap;left:-9999px;top:-9999px;`
    + `font:${font ? font.font || `${font.fontSize} ${font.fontFamily}` : '14px sans-serif'};`
  document.body.appendChild(ruler)
  let widest = 0
  for (const text of texts) {
    ruler.textContent = text
    widest = Math.max(widest, ruler.getBoundingClientRect().width)
  }
  ruler.remove()
  if (widest <= 0) return

  // Element Plus adds the label's right padding (and the required asterisk gutter)
  // on top of the text itself.
  const pad = sample
    ? Number.parseFloat(getComputedStyle(sample).paddingRight || '0') || 12
    : 12
  stableLabelWidth.value = `${Math.ceil(widest + pad + 8)}px`
}

// A fresh dialog must not inherit the previous row's width.
watch(() => props.visible, (open) => { if (!open) stableLabelWidth.value = 'auto' })

/**
 * Fields the Assignment Mode block owns and renders inside its own box, in the
 * block's reading order (BU before Role, since BU narrows the role list).
 */
const assignmentOwnedFields = computed(() => {
  const config = effectiveAssignmentConfig.value
  if (!config) return [] as string[]
  return fieldsOwnedByMode(assignMode.value, config)
})

/** DW Form Preview parity: group columns under designer elCard titles when present. */
const dialogLayoutGroups = computed(() => {
  const groups = buildDialogLayoutGroups(props.formFields, visibleColumns.value).map(group => ({
    ...group,
    items: groupAssignmentFieldsUnderMarker(group.items, assignmentOwnedFields.value),
  }))
  return groups
})

// ─── Form core (state / rules / formulas / validation / open / save) ───────────
const {
  formRef,
  saving,
  dialogKey,
  formRules,
  isColDisabled,
  columnErrors,
  computedFieldErrors,
  handleClose,
  handleSave,
} = useSubTableDialogForm(props, emit, t, {
  formData,
  resetUploadNames,
  backfillUploadNames,
  resetLookupState,
  destroyEditors,
  fetchDepartmentTree,
  resetDialogEventVisibility,
  bootstrapDialogFormLifecycle: () => bootstrapDialogFormLifecycle(props.formOptions),
  runFormOnReload: () => runFormOnReload(props.formOptions),
  runFormBeforeSubmit: () => runFormBeforeSubmit(props.formOptions),
  runFormOnSubmit: () => runFormOnSubmit(props.formOptions),
  runFormOnReset: () => runFormOnReset(props.formOptions),
})

const {
  showMasked,
  textDisplay,
  onTextUpdate,
  onTextFocus,
  onTextBlur,
} = useSubTableDialogSensitiveMask(formData, isColDisabled)

/**
 * One inline error per field, from the two sources that can produce one. Precomputed as a map so
 * the form does not re-derive a message for every column on every render.
 */
const columnErrorMessages = computed<Record<string, string>>(() => {
  const messages: Record<string, string> = {}
  for (const [field, errors] of Object.entries(columnErrors.value)) {
    if (errors.length) messages[field] = errors.join('; ')
  }
  for (const [field, code] of Object.entries(computedFieldErrors.value)) {
    messages[field] = t('computedField.evaluationFailed', { code })
  }
  return messages
})

// Re-measure whenever the visible field set changes (open, mode switch, row change).
// Declared after formRef / dialogLayoutGroups exist, and flushed post-render so the
// labels being measured are the ones actually on screen.
watch(
  () => [props.visible, dialogLayoutGroups.value] as const,
  () => { void nextTick(syncStableLabelWidth) },
  { flush: 'post', immediate: true },
)
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

/* ── Assignment Mode block ────────────────────────────────────────────────────
   Routing a row has two destinations: a named person, or a role pool in a BU.
   The two modes are rendered as selectable cards rather than bare radios, and
   the picker the chosen mode needs sits directly beneath them — so the block
   always shows the consequence of the choice instead of an empty frame.

   __head and the owned fields are siblings (the shared column branches below
   can't be wrapped), so the frame is split across them: head draws top + sides,
   fields continue the sides, and --last closes the bottom. */
.mi-assignment-block__head {
  margin-top: 4px;
  padding: 12px 14px 4px;
  border: 1px solid #dcdfe6;
  border-bottom: none;
  border-radius: 6px 6px 0 0;
  background: #f7f9fc;
}

.mi-assignment-block__title {
  margin-bottom: 10px;
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.04em;
  text-transform: uppercase;
  color: #8a9099;
}

.mi-assignment-block__modes {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
  margin-bottom: 12px;
}

/* Mode card: the rail on the left is the only saturated element in the block. */
.mi-assignment-mode-card {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  position: relative;
  margin: 0;
  padding: 10px 12px 10px 14px;
  overflow: hidden;
  font: inherit;
  text-align: left;
  background: #fff;
  border: 1px solid #dcdfe6;
  border-radius: 5px;
  cursor: pointer;
  transition: border-color 0.15s ease, box-shadow 0.15s ease;
}

.mi-assignment-mode-card::before {
  content: '';
  position: absolute;
  top: 0;
  bottom: 0;
  left: 0;
  width: 3px;
  background: transparent;
  transition: background-color 0.15s ease;
}

.mi-assignment-mode-card:hover {
  border-color: #b6bcc4;
}

.mi-assignment-mode-card.is-selected {
  border-color: #c8102e;
  box-shadow: 0 1px 3px rgba(200, 16, 46, 0.12);
}

.mi-assignment-mode-card.is-selected::before {
  background: #c8102e;
}

/* BPMN configured only one mode — the other card stays visible but locked, so the
   reader sees the mode was deliberately fixed rather than the block being narrower. */
.mi-assignment-mode-card.is-disabled {
  cursor: not-allowed;
  opacity: 0.5;
}

.mi-assignment-mode-card.is-disabled:hover {
  border-color: #dcdfe6;
}

.mi-assignment-mode-card:focus-visible {
  outline: 2px solid #c8102e;
  outline-offset: 2px;
}

.mi-assignment-mode-card__dot {
  flex: none;
  width: 14px;
  height: 14px;
  margin-top: 2px;
  border: 1px solid #c0c4cc;
  border-radius: 50%;
  background: #fff;
  transition: border-color 0.15s ease, box-shadow 0.15s ease;
}

.mi-assignment-mode-card.is-selected .mi-assignment-mode-card__dot {
  border-color: #c8102e;
  box-shadow: inset 0 0 0 3px #c8102e;
}

.mi-assignment-mode-card__text {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.mi-assignment-mode-card__name {
  font-size: 13px;
  font-weight: 500;
  line-height: 1.3;
  color: #606266;
}

.mi-assignment-mode-card.is-selected .mi-assignment-mode-card__name {
  color: #1f2329;
}

.mi-assignment-mode-card__hint {
  font-size: 11px;
  line-height: 1.35;
  color: #9aa0a8;
  /* Wrap rather than clip — the hint is what tells you which picker you get. */
  white-space: normal;
  overflow-wrap: anywhere;
}

/* Owned fields continue the box: side borders only, no top border. Only the
   --last field closes the bottom — otherwise role mode's two stacked fields
   (BU, then Role) each draw a full border and BU shows a stray line above Role. */
.el-form-item.mi-assignment-block__field {
  margin-bottom: 0;
  padding: 8px 14px 0;
  border: 1px solid #dcdfe6;
  border-top: none;
  border-bottom: none;
  background: #f7f9fc;
}

/* Last owned field closes the box. */
.el-form-item.mi-assignment-block__field--last {
  margin-bottom: 18px;
  padding-bottom: 14px;
  border-bottom: 1px solid #dcdfe6;
  border-radius: 0 0 6px 6px;
}

/* "person" owns one picker, "role" owns two, so toggling modes resized the dialog.
   Reserve the taller branch's height on the block's LAST row only when it is also the
   first — i.e. the single-picker (person) branch — so that branch occupies the same
   height as the two-picker one and nothing below the block moves. */
.mi-assignment-block__head + .el-form-item.mi-assignment-block__field--last {
  min-height: 96px;
  box-sizing: border-box;
}

/* Narrow dialogs (mobile): stack the modes rather than crushing the hint text. */
@media (max-width: 560px) {
  .mi-assignment-block__modes {
    grid-template-columns: minmax(0, 1fr);
  }
}

@media (prefers-reduced-motion: reduce) {
  .mi-assignment-mode-card,
  .mi-assignment-mode-card::before,
  .mi-assignment-mode-card__dot {
    transition: none;
  }
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
  cursor: pointer;
}
.upload-download-link:hover {
  text-decoration: underline;
}
.upload-filename-tag {
  cursor: pointer;
}
.dialog-nested-sub-table {
  margin-top: 8px;
  margin-bottom: 8px;
}
</style>
