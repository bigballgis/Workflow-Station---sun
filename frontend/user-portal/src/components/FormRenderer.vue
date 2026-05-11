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
      :validate-on-rule-change="false"
    >
      <!-- Tab layout mode -->
      <template v-if="hasTabs">
        <el-tabs
          v-model="activeTab"
          type="border-card"
        >
          <el-tab-pane
            v-for="(tab, tabIdx) in tabs"
            :key="`tab-${tabIdx}-${String(tab.name)}`"
            :label="tab.label"
            :name="tab.name"
          >
            <el-row :gutter="20">
              <template
                v-for="field in tab.fields"
                :key="field.key"
              >
                <template v-if="field.type === 'card'">
                  <el-col :span="field.span || 24">
                    <el-card
                      shadow="never"
                      class="form-layout-card"
                    >
                      <template
                        v-if="field.label"
                        #header
                      >
                        <span class="form-layout-card-title">{{ field.label }}</span>
                      </template>
                      <el-row :gutter="20">
                        <template
                          v-for="child in field.children || []"
                          :key="child.key"
                        >
                          <template v-if="child.type === 'subTable'">
                            <el-col
                              :span="24"
                              style="padding: 0;"
                            >
                              <SubTableField
                                v-if="resolveBinding(child._bindingId)"
                                :title="resolveBinding(child._bindingId)!.tableName"
                                :columns="resolveBinding(child._bindingId)!.columns"
                                :model-value="resolveBinding(child._bindingId)!.data"
                                :editable="isSubTableEditable(child._bindingId)"
                                :row-formulas="getSubFormRowFormulas(child._bindingId)"
                                :summary-columns="getSummaryColumns(child._bindingId)"
                                :summary-aggregations="getSummaryAggregations(child._bindingId)"
                                :validation-config="getSubTableValidation(child._bindingId)"
                                :upload-url="uploadUrl"
                                :task-id="taskId"
                                :assignee-field="subTableAssigneeField(child._bindingId)"
                                :show-assign-button="showSubTableAssignColumn(child._bindingId)"
                                :can-assign="!readonly && showSubTableAssignColumn(child._bindingId)"
                                :enable-polling="enableSubTablePolling"
                                :polling-interval="subTablePollingInterval"
                                :linked-sub-table-bindings="linkableSubTableBindings"
                                :suppress-link-form-initial-data="suppressLinkFormInitialData"
                                :show-link-form-dialog-footer="showLinkFormDialogFooter"
                                :show-task-status="subTableShowTaskStatusInitiator(child)"
                                :show-view-detail="subTableShowViewDetailInitiator(child)"
                                style="margin-bottom: 16px;"
                                @update:model-value="(rows: any[]) => handleSubTableUpdate(child._bindingId!, rows)"
                                @update:linked-sub-table-data="handleSubTableUpdate"
                                @view-detail="(row: any) => emit('viewSubtaskDetail', row)"
                              />
                              <SubTableInlineForm
                                v-if="resolveBinding(child._bindingId) && subTableMode(child) === 'formBelowTable'"
                                :title="resolveBinding(child._bindingId)!.tableName"
                                :fields="resolveInlineFormFields(child)"
                                :current-row="getCurrentRowForInlineForm(child)"
                                :readonly="readonly"
                                :label-width="labelWidth"
                                @update:row="(row: Record<string, any>) => handleInlineFormUpdate(child, row)"
                              />
                            </el-col>
                          </template>
                          <template v-else-if="child.type === 'lookup'">
                            <el-col :span="child.span || 24">
                              <el-form-item
                                :prop="child.key"
                                class="lookup-form-item"
                              >
                                <template #label>
                                  <span class="lookup-label-text">
                                    <el-icon class="lookup-label-icon"><Search /></el-icon>
                                    {{ child.label }}
                                  </span>
                                </template>
                                <div class="lookup-field-wrapper">
                                  <LookupField
                                    v-model="formData[child.key]"
                                    :table-id="(child as any)._lookupTableId"
                                    :search-fields="(child as any)._lookupSearchFields || []"
                                    :display-field="(child as any)._lookupDisplayField || ''"
                                    :display-fields="(child as any)._lookupDisplayFields || []"
                                    :selected-display-field="(child as any)._lookupSelectedDisplayField || ''"
                                    :filter-conditions="(child as any)._lookupFilterConditions || []"
                                    :view-fields="(child as any)._lookupViewFields || []"
                                    :placeholder="child.placeholder"
                                    :readonly="readonly"
                                    @select="(row: any) => handleLookupSelect(child.key, row)"
                                    @clear="() => handleLookupClear(child.key)"
                                    @view-fields-loaded="(fields: any[]) => lookupLoadedViewFields[child.key] = fields"
                                  />
                                  <LookupViewDisplay
                                    v-if="lookupSelectedData[child.key]"
                                    :selected-data="lookupSelectedData[child.key]"
                                    :view-fields="(child as any)._lookupViewFields?.length ? (child as any)._lookupViewFields : (lookupLoadedViewFields[child.key] || [])"
                                  />
                                </div>
                              </el-form-item>
                            </el-col>
                          </template>
                          <el-col
                            v-else
                            v-show="engineVisibility.get(child.key) ?? true"
                            :span="child.span || 24"
                          >
                            <el-form-item
                              :label="child.label"
                              :prop="child.key"
                              :required="child.required"
                            >
                              <FieldRenderer
                                :field="child"
                                :model-value="formData[child.key]"
                                :form-data="formData"
                                :readonly="readonly"
                                :disabled="engineFieldStates.get(child.key)?.disabled || false"
                                :visible="engineVisibility.get(child.key) ?? true"
                                :options="engineOptions.get(child.key)"
                                :upload-url="uploadUrl"
                                :user-search-results="userSearchResults.get(child.key)"
                                @update:model-value="(val: any) => handleFieldChange(child.key, val)"
                                @upload:success="(res: any, file: any, key: string) => handleUploadSuccess(res, file, key)"
                                @upload:remove="(file: any, key: string) => handleUploadRemove(file, key)"
                                @search:users="handleUserSearch"
                              />
                            </el-form-item>
                          </el-col>
                        </template>
                      </el-row>
                    </el-card>
                  </el-col>
                </template>
                <template v-else-if="field.type === 'subTable'">
                  <el-col
                    :span="24"
                    style="padding: 0;"
                  >
                    <SubTableField
                      v-if="resolveBinding(field._bindingId)"
                      :title="resolveBinding(field._bindingId)!.tableName"
                      :columns="resolveBinding(field._bindingId)!.columns"
                      :model-value="resolveBinding(field._bindingId)!.data"
                      :editable="isSubTableEditable(field._bindingId)"
                      :row-formulas="getSubFormRowFormulas(field._bindingId)"
                      :summary-columns="getSummaryColumns(field._bindingId)"
                      :summary-aggregations="getSummaryAggregations(field._bindingId)"
                      :validation-config="getSubTableValidation(field._bindingId)"
                      :upload-url="uploadUrl"
                      :task-id="taskId"
                      :assignee-field="subTableAssigneeField(field._bindingId)"
                      :show-assign-button="showSubTableAssignColumn(field._bindingId)"
                      :can-assign="!readonly && showSubTableAssignColumn(field._bindingId)"
                      :enable-polling="enableSubTablePolling"
                      :polling-interval="subTablePollingInterval"
                      :linked-sub-table-bindings="linkableSubTableBindings"
                      :suppress-link-form-initial-data="suppressLinkFormInitialData"
                      :show-link-form-dialog-footer="showLinkFormDialogFooter"
                      :show-task-status="subTableShowTaskStatusInitiator(field)"
                      :show-view-detail="subTableShowViewDetailInitiator(field)"
                      style="margin-bottom: 16px;"
                      @update:model-value="(rows: any[]) => handleSubTableUpdate(field._bindingId!, rows)"
                      @update:linked-sub-table-data="handleSubTableUpdate"
                      @view-detail="(row: any) => emit('viewSubtaskDetail', row)"
                    />
                    <SubTableInlineForm
                      v-if="resolveBinding(field._bindingId) && subTableMode(field) === 'formBelowTable'"
                      :title="resolveBinding(field._bindingId)!.tableName"
                      :fields="resolveInlineFormFields(field)"
                      :current-row="getCurrentRowForInlineForm(field)"
                      :readonly="readonly"
                      :label-width="labelWidth"
                      @update:row="(row: Record<string, any>) => handleInlineFormUpdate(field, row)"
                    />
                  </el-col>
                </template>
                <template v-else-if="field.type === 'lookup'">
                  <el-col :span="field.span || 24">
                    <el-form-item
                      :prop="field.key"
                      class="lookup-form-item"
                    >
                      <template #label>
                        <span class="lookup-label-text">
                          <el-icon class="lookup-label-icon"><Search /></el-icon>
                          {{ field.label }}
                        </span>
                      </template>
                      <div class="lookup-field-wrapper">
                        <LookupField
                          v-model="formData[field.key]"
                          :table-id="(field as any)._lookupTableId"
                          :search-fields="(field as any)._lookupSearchFields || []"
                          :display-field="(field as any)._lookupDisplayField || ''"
                          :display-fields="(field as any)._lookupDisplayFields || []"
                          :selected-display-field="(field as any)._lookupSelectedDisplayField || ''"
                          :filter-conditions="(field as any)._lookupFilterConditions || []"
                          :view-fields="(field as any)._lookupViewFields || []"
                          :placeholder="field.placeholder"
                          :readonly="readonly"
                          @select="(row: any) => handleLookupSelect(field.key, row)"
                          @clear="() => handleLookupClear(field.key)"
                          @view-fields-loaded="(fields: any[]) => lookupLoadedViewFields[field.key] = fields"
                        />
                        <LookupViewDisplay
                          v-if="lookupSelectedData[field.key]"
                          :selected-data="lookupSelectedData[field.key]"
                          :view-fields="(field as any)._lookupViewFields?.length ? (field as any)._lookupViewFields : (lookupLoadedViewFields[field.key] || [])"
                        />
                      </div>
                    </el-form-item>
                  </el-col>
                </template>
                <el-col
                  v-else
                  v-show="engineVisibility.get(field.key) ?? true"
                  :span="field.span || 24"
                >
                  <el-form-item
                    :label="field.label"
                    :prop="field.key"
                    :required="field.required"
                  >
                    <FieldRenderer
                      :field="field"
                      :model-value="formData[field.key]"
                      :form-data="formData"
                      :readonly="readonly"
                      :disabled="engineFieldStates.get(field.key)?.disabled || false"
                      :visible="engineVisibility.get(field.key) ?? true"
                      :options="engineOptions.get(field.key)"
                      :upload-url="uploadUrl"
                      :user-search-results="userSearchResults.get(field.key)"
                      @update:model-value="(val: any) => handleFieldChange(field.key, val)"
                      @upload:success="(res: any, file: any, key: string) => handleUploadSuccess(res, file, key)"
                      @upload:remove="(file: any, key: string) => handleUploadRemove(file, key)"
                      @search:users="handleUserSearch"
                    />
                  </el-form-item>
                </el-col>
              </template>
            </el-row>
          </el-tab-pane>
        </el-tabs>
      </template>

      <!-- Flat layout mode -->
      <template v-else>
        <el-row :gutter="20">
          <template
            v-for="field in fields"
            :key="field.key"
          >
            <template v-if="field.type === 'card'">
              <el-col :span="field.span || 24">
                <el-card
                  shadow="never"
                  class="form-layout-card"
                >
                  <template
                    v-if="field.label"
                    #header
                  >
                    <span class="form-layout-card-title">{{ field.label }}</span>
                  </template>
                  <el-row :gutter="20">
                    <template
                      v-for="child in field.children || []"
                      :key="child.key"
                    >
                      <template v-if="child.type === 'subTable'">
                        <el-col
                          :span="24"
                          style="padding: 0;"
                        >
                          <SubTableField
                            v-if="resolveBinding(child._bindingId)"
                            :title="resolveBinding(child._bindingId)!.tableName"
                            :columns="resolveBinding(child._bindingId)!.columns"
                            :model-value="resolveBinding(child._bindingId)!.data"
                            :editable="isSubTableEditable(child._bindingId)"
                            :row-formulas="getSubFormRowFormulas(child._bindingId)"
                            :summary-columns="getSummaryColumns(child._bindingId)"
                            :summary-aggregations="getSummaryAggregations(child._bindingId)"
                            :validation-config="getSubTableValidation(child._bindingId)"
                            :upload-url="uploadUrl"
                            :task-id="taskId"
                            :assignee-field="subTableAssigneeField(child._bindingId)"
                            :show-assign-button="showSubTableAssignColumn(child._bindingId)"
                            :can-assign="!readonly && showSubTableAssignColumn(child._bindingId)"
                            :enable-polling="enableSubTablePolling"
                            :polling-interval="subTablePollingInterval"
                            :linked-sub-table-bindings="linkableSubTableBindings"
                            :suppress-link-form-initial-data="suppressLinkFormInitialData"
                            :show-link-form-dialog-footer="showLinkFormDialogFooter"
                            :show-task-status="subTableShowTaskStatusInitiator(child)"
                            :show-view-detail="subTableShowViewDetailInitiator(child)"
                            style="margin-bottom: 16px;"
                            @update:model-value="(rows: any[]) => handleSubTableUpdate(child._bindingId!, rows)"
                            @update:linked-sub-table-data="handleSubTableUpdate"
                            @view-detail="(row: any) => emit('viewSubtaskDetail', row)"
                          />
                          <SubTableInlineForm
                            v-if="resolveBinding(child._bindingId) && subTableMode(child) === 'formBelowTable'"
                            :title="resolveBinding(child._bindingId)!.tableName"
                            :fields="resolveInlineFormFields(child)"
                            :current-row="getCurrentRowForInlineForm(child)"
                            :readonly="readonly"
                            :label-width="labelWidth"
                            @update:row="(row: Record<string, any>) => handleInlineFormUpdate(child, row)"
                          />
                        </el-col>
                      </template>
                      <template v-else-if="child.type === 'lookup'">
                        <el-col :span="child.span || 24">
                          <el-form-item
                            :prop="child.key"
                            class="lookup-form-item"
                          >
                            <template #label>
                              <span class="lookup-label-text">
                                <el-icon class="lookup-label-icon"><Search /></el-icon>
                                {{ child.label }}
                              </span>
                            </template>
                            <div class="lookup-field-wrapper">
                              <LookupField
                                v-model="formData[child.key]"
                                :table-id="(child as any)._lookupTableId"
                                :search-fields="(child as any)._lookupSearchFields || []"
                                :display-field="(child as any)._lookupDisplayField || ''"
                                :display-fields="(child as any)._lookupDisplayFields || []"
                                :selected-display-field="(child as any)._lookupSelectedDisplayField || ''"
                                :filter-conditions="(child as any)._lookupFilterConditions || []"
                                :view-fields="(child as any)._lookupViewFields || []"
                                :placeholder="child.placeholder"
                                :readonly="readonly"
                                @select="(row: any) => handleLookupSelect(child.key, row)"
                                @clear="() => handleLookupClear(child.key)"
                                @view-fields-loaded="(fields: any[]) => lookupLoadedViewFields[child.key] = fields"
                              />
                              <LookupViewDisplay
                                v-if="lookupSelectedData[child.key]"
                                :selected-data="lookupSelectedData[child.key]"
                                :view-fields="(child as any)._lookupViewFields?.length ? (child as any)._lookupViewFields : (lookupLoadedViewFields[child.key] || [])"
                              />
                            </div>
                          </el-form-item>
                        </el-col>
                      </template>
                      <el-col
                        v-else
                        v-show="engineVisibility.get(child.key) ?? true"
                        :span="child.span || 24"
                      >
                        <el-form-item
                          :label="child.label"
                          :prop="child.key"
                          :required="child.required"
                        >
                          <FieldRenderer
                            :field="child"
                            :model-value="formData[child.key]"
                            :form-data="formData"
                            :readonly="readonly"
                            :disabled="engineFieldStates.get(child.key)?.disabled || false"
                            :visible="engineVisibility.get(child.key) ?? true"
                            :options="engineOptions.get(child.key)"
                            :upload-url="uploadUrl"
                            :user-search-results="userSearchResults.get(child.key)"
                            @update:model-value="(val: any) => handleFieldChange(child.key, val)"
                            @upload:success="(res: any, file: any, key: string) => handleUploadSuccess(res, file, key)"
                            @upload:remove="(file: any, key: string) => handleUploadRemove(file, key)"
                            @search:users="handleUserSearch"
                          />
                        </el-form-item>
                      </el-col>
                    </template>
                  </el-row>
                </el-card>
              </el-col>
            </template>
            <template v-else-if="field.type === 'subTable'">
              <el-col
                :span="24"
                style="padding: 0;"
              >
                <SubTableField
                  v-if="resolveBinding(field._bindingId)"
                  :title="resolveBinding(field._bindingId)!.tableName"
                  :columns="resolveBinding(field._bindingId)!.columns"
                  :model-value="resolveBinding(field._bindingId)!.data"
                  :editable="isSubTableEditable(field._bindingId)"
                  :row-formulas="getSubFormRowFormulas(field._bindingId)"
                  :summary-columns="getSummaryColumns(field._bindingId)"
                  :summary-aggregations="getSummaryAggregations(field._bindingId)"
                  :validation-config="getSubTableValidation(field._bindingId)"
                  :upload-url="uploadUrl"
                  :task-id="taskId"
                  :assignee-field="subTableAssigneeField(field._bindingId)"
                  :show-assign-button="showSubTableAssignColumn(field._bindingId)"
                  :can-assign="!readonly && showSubTableAssignColumn(field._bindingId)"
                  :enable-polling="enableSubTablePolling"
                  :polling-interval="subTablePollingInterval"
                  :linked-sub-table-bindings="linkableSubTableBindings"
                  :suppress-link-form-initial-data="suppressLinkFormInitialData"
                  :show-link-form-dialog-footer="showLinkFormDialogFooter"
                  :show-task-status="subTableShowTaskStatusInitiator(field)"
                  :show-view-detail="subTableShowViewDetailInitiator(field)"
                  style="margin-bottom: 16px;"
                  @update:model-value="(rows: any[]) => handleSubTableUpdate(field._bindingId!, rows)"
                  @update:linked-sub-table-data="handleSubTableUpdate"
                  @view-detail="(row: any) => emit('viewSubtaskDetail', row)"
                />
                <SubTableInlineForm
                  v-if="resolveBinding(field._bindingId) && subTableMode(field) === 'formBelowTable'"
                  :title="resolveBinding(field._bindingId)!.tableName"
                  :fields="resolveInlineFormFields(field)"
                  :current-row="getCurrentRowForInlineForm(field)"
                  :readonly="readonly"
                  :label-width="labelWidth"
                  @update:row="(row: Record<string, any>) => handleInlineFormUpdate(field, row)"
                />
              </el-col>
            </template>
            <template v-else-if="field.type === 'lookup'">
              <el-col :span="field.span || 24">
                <el-form-item
                  :prop="field.key"
                  class="lookup-form-item"
                >
                  <template #label>
                    <span class="lookup-label-text">
                      <el-icon class="lookup-label-icon"><Search /></el-icon>
                      {{ field.label }}
                    </span>
                  </template>
                  <div class="lookup-field-wrapper">
                    <LookupField
                      v-model="formData[field.key]"
                      :table-id="(field as any)._lookupTableId"
                      :search-fields="(field as any)._lookupSearchFields || []"
                      :display-field="(field as any)._lookupDisplayField || ''"
                      :display-fields="(field as any)._lookupDisplayFields || []"
                      :selected-display-field="(field as any)._lookupSelectedDisplayField || ''"
                      :filter-conditions="(field as any)._lookupFilterConditions || []"
                      :view-fields="(field as any)._lookupViewFields || []"
                      :placeholder="field.placeholder"
                      :readonly="readonly"
                      @select="(row: any) => handleLookupSelect(field.key, row)"
                      @clear="() => handleLookupClear(field.key)"
                      @view-fields-loaded="(fields: any[]) => lookupLoadedViewFields[field.key] = fields"
                    />
                    <LookupViewDisplay
                      v-if="lookupSelectedData[field.key]"
                      :selected-data="lookupSelectedData[field.key]"
                      :view-fields="(field as any)._lookupViewFields?.length ? (field as any)._lookupViewFields : (lookupLoadedViewFields[field.key] || [])"
                    />
                  </div>
                </el-form-item>
              </el-col>
            </template>
            <el-col
              v-else
              v-show="engineVisibility.get(field.key) ?? true"
              :span="field.span || 24"
            >
              <el-form-item
                :label="field.label"
                :prop="field.key"
                :required="field.required"
              >
                <FieldRenderer
                  :field="field"
                  :model-value="formData[field.key]"
                  :form-data="formData"
                  :readonly="readonly"
                  :disabled="engineFieldStates.get(field.key)?.disabled || false"
                  :visible="engineVisibility.get(field.key) ?? true"
                  :options="engineOptions.get(field.key)"
                  :upload-url="uploadUrl"
                  :user-search-results="userSearchResults.get(field.key)"
                  @update:model-value="(val: any) => handleFieldChange(field.key, val)"
                  @upload:success="(res: any, file: any, key: string) => handleUploadSuccess(res, file, key)"
                  @upload:remove="(file: any, key: string) => handleUploadRemove(file, key)"
                  @search:users="handleUserSearch"
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
import { ref, computed, watch, onMounted, onBeforeUnmount, nextTick, provide } from 'vue'
import { watchThrottled } from '@vueuse/core'
import { useI18n } from 'vue-i18n'
import { isEqual } from 'lodash-es'
import { ElMessageBox } from 'element-plus'
import { Upload, Search } from '@element-plus/icons-vue'
import type { FormInstance, FormRules } from 'element-plus'
import SubTableField from './SubTableField.vue'
import SubTableInlineForm from './SubTableInlineForm.vue'
import LookupField from './lookup/LookupField.vue'
import LookupViewDisplay from './lookup/LookupViewDisplay.vue'
import FieldRenderer from './FieldRenderer.vue'
import { BusinessLogicEngine } from './businessLogicEngine'
import { userApi } from '@/api/user'
import { resolveAssigneeFieldForBinding } from '@/utils/subTableAssignment'
import type {
  FormField,
  FormTab,
  FormBusinessLogicConfig,
  PortalViewContext
} from './formRendererHelpers'
import { extractFieldsRecursive, resolveSubTableDisplayMode } from './formRendererHelpers'

export type { FormField, FormTab }

const { t } = useI18n()

// ---------------------------------------------------------------------------
// Types
// ---------------------------------------------------------------------------
interface SubTableBinding {
  bindingId: number
  tableId?: number | null
  bindingType: string
  bindingMode: string
  tableName: string
  physicalTableName?: string
  tableType: string
  tableDescription: string
  columns: any[]
  data: any[]
  formFields?: FormField[]
  formOptions?: Record<string, any>
  /**
   * Per-binding portalViews loaded from form configJson.subTablePortalViews[bindingId].
   * Used as the fallback when a placed `subTable` rule node has no `props.portalViews`,
   * and as the primary source for unplaced bindings (e.g. sub-tables accessed only via Link Form).
   */
  portalViews?: Partial<import('./formRendererHelpers').SubTablePortalViews> | null
}

interface Props {
  fields: FormField[]
  tabs?: FormTab[]
  modelValue?: Record<string, any>
  readonly?: boolean
  labelWidth?: string
  labelPosition?: 'left' | 'right' | 'top'
  size?: 'large' | 'default' | 'small'
  subTableBindings?: SubTableBinding[]
  linkedSubTableBindings?: SubTableBinding[]
  previewSubTables?: boolean
  uploadUrl?: string
  // Task 7.2: BusinessLogicEngine config
  config?: FormBusinessLogicConfig
  // Task 7.5: Auto-save props
  functionUnitId?: string
  formId?: string
  // Task 16: Real-time sync props
  taskId?: string
  enableSubTablePolling?: boolean
  subTablePollingInterval?: number
  /** When false, hides the sub-table Assign button (only the "Assign Participants" task node allows assignment) */
  allowSubTableAssign?: boolean
  /** In MI todo mode, link-form Details should open blank instead of reusing row-level historical child data. */
  suppressLinkFormInitialData?: boolean
  /** Task To Do only: Link Form field-layout detail shows Cancel/Save (completed / My Request use header close only). */
  showLinkFormDialogFooter?: boolean
  /**
   * Portal view context — drives how subTable nodes are rendered based on their `portalViews` config:
   * - `assigneeTodo`: To Do detail page (办理人待办)
   * - `initiatorRequest`: My Request / process detail page (发起人我的申请)
   * Defaults to `assigneeTodo` for safety; consumers should pass the value matching their route.
   */
  viewContext?: PortalViewContext
  /**
   * When `viewContext` is `initiatorRequest`, Completed Tasks snapshot treats task-status rows
   * like `applicationDetail` (only COMPLETED rows count for Details visibility heuristics).
   */
  initiatorSnapshotMode?: boolean
  /**
   * Current MI participant row id (typically `variables._currentItem.rowId`). When set, the
   * inline form-below-table binds to that row; otherwise it falls back to the first sub-table row.
   */
  currentMiRowId?: number | string | null
}

const props = withDefaults(defineProps<Props>(), {
  modelValue: () => ({}),
  tabs: () => [],
  readonly: false,
  labelWidth: '160px',
  labelPosition: 'left',
  size: 'default',
  subTableBindings: () => [],
  linkedSubTableBindings: undefined,
  previewSubTables: false,
  allowSubTableAssign: true,
  suppressLinkFormInitialData: false,
  showLinkFormDialogFooter: false,
  viewContext: 'assigneeTodo',
  initiatorSnapshotMode: false,
  currentMiRowId: null,
})

const emit = defineEmits<{
  (e: 'update:modelValue', value: Record<string, any>): void
  (e: 'change', key: string, value: any): void
  (e: 'update:subTableData', bindingId: number, rows: any[]): void
  (e: 'viewSubtaskDetail', row: any): void
}>()

// ---------------------------------------------------------------------------
// Core refs
// ---------------------------------------------------------------------------
const formRef = ref<FormInstance>()
const formData = ref<Record<string, any>>({})
let isInternalUpdate = false

// Department data cache shared via provide/inject (Req 27)
const departmentTreeData = ref<any[]>([])
const departmentTreeLoading = ref(false)
provide('departmentTreeData', departmentTreeData)
provide('departmentTreeLoading', departmentTreeLoading)

const hasTabs = computed(() => props.tabs && props.tabs.length > 0)
const activeTab = ref('')

watch(
  () => props.tabs?.map(t => String(t.name)).join('\u0001') ?? '',
  () => {
    const newTabs = props.tabs
    if (!newTabs?.length) {
      activeTab.value = ''
      return
    }
    const names = newTabs.map(t => t.name)
    const current = activeTab.value
    const stillValid =
      current !== '' &&
      current !== undefined &&
      names.some(n => String(n) === String(current))
    if (!stillValid) {
      activeTab.value = names[0]!
    }
  },
  { immediate: true },
)

const bindingMap = computed(() => {
  const map = new Map<number, SubTableBinding>()
  for (const b of (props.subTableBindings ?? [])) map.set(b.bindingId, b)
  return map
})
const linkableSubTableBindings = computed(() => props.linkedSubTableBindings ?? props.subTableBindings)
const resolveBinding = (id?: number) => {
  const binding = id != null ? bindingMap.value.get(id) : undefined
  return binding
}

function isSubTableEditable(bindingId?: number): boolean {
  const binding = resolveBinding(bindingId)
  if (!binding || props.readonly) return false
  return props.previewSubTables || binding.bindingMode === 'EDITABLE'
}

function subTableAssigneeField(bindingId?: number): string | undefined {
  const b = resolveBinding(bindingId)
  if (!b) return undefined
  return resolveAssigneeFieldForBinding(
    b.columns as Array<{ field?: string }>,
    b.tableName
  )
}

function showSubTableAssignColumn(bindingId?: number): boolean {
  if (props.allowSubTableAssign === false) {
    return false
  }
  return !!(props.taskId && subTableAssigneeField(bindingId))
}

// ---------------------------------------------------------------------------
// Portal-views driven rendering helpers (designer → Portal contract)
// ---------------------------------------------------------------------------
/**
 * Effective sub-table display mode at the current view context. Returns one of:
 *   - 'tableOnly': just the SubTableField, nothing else
 *   - 'formBelowTable': SubTableField + inline form below (binds to current row)
 *   - 'summaryWithLinkFormModal': SubTableField; Details modal flow handled by existing
 *      Link Form column logic inside SubTableField (no inline form below)
 *
 * Resolution precedence:
 *   1. Rule-level `field.portalViews` (set on the SubTable widget in the main form designer)
 *   2. Binding-level `binding.portalViews` (set on the sub-table tab → "portalViews" bar)
 *   3. DEFAULT_PORTAL_VIEWS — tableOnly + mirrorTodo (preserves legacy behavior)
 */
function subTableMode(field: FormField): 'tableOnly' | 'formBelowTable' | 'summaryWithLinkFormModal' {
  if (field.portalViews) {
    return resolveSubTableDisplayMode(field.portalViews, props.viewContext)
  }
  const binding = resolveBinding(field._bindingId)
  return resolveSubTableDisplayMode(binding?.portalViews ?? undefined, props.viewContext)
}

function effectiveInitiatorRequestPortalMode(field: FormField): string | undefined {
  const ir = field.portalViews?.initiatorRequest
  if (typeof ir === 'string' && ir.length > 0) return ir
  const binding = resolveBinding(field._bindingId)
  const bir = binding?.portalViews?.initiatorRequest
  return typeof bir === 'string' ? bir : undefined
}

/** Aligns with application-detail heuristics for MI / snapshot task-status rows. */
function bindingHasMiTaskStatusRowsForInitiator(rows: any[]): boolean {
  if (!Array.isArray(rows) || rows.length === 0) return false
  if (props.initiatorSnapshotMode) {
    return rows.some(r => r && r.task_status === 'COMPLETED')
  }
  return rows.some(r => r && r.task_status !== undefined)
}

function subTableShowTaskStatusInitiator(field: FormField): boolean {
  if (props.viewContext !== 'initiatorRequest') return false
  if (subTableMode(field) !== 'summaryWithLinkFormModal') return false
  const binding = resolveBinding(field._bindingId)
  return bindingHasMiTaskStatusRowsForInitiator(binding?.data || [])
}

function subTableShowViewDetailInitiator(field: FormField): boolean {
  if (props.viewContext !== 'initiatorRequest') return false
  if (subTableMode(field) !== 'summaryWithLinkFormModal') return false
  const binding = resolveBinding(field._bindingId)
  if (!binding) return false
  const mode = effectiveInitiatorRequestPortalMode(field)
  const rows = binding.data || []
  if (mode === 'summaryWithLinkFormModal') {
    return bindingHasMiTaskStatusRowsForInitiator(rows)
  }
  if (mode === 'tableOnly' || mode === 'mirrorTodo') return false
  return bindingHasMiTaskStatusRowsForInitiator(rows)
}

/**
 * Resolve the effective form-source config from rule-level or binding-level portalViews.
 * Rule-level wins (more specific); binding-level is the per-binding default; finally
 * fall back to `subForm` for legacy/unconfigured forms.
 */
function resolveAssigneeTodoFormSource(field: FormField): {
  type: 'subForm' | 'linkForm' | 'formId'
  formId?: number | string | null
} {
  const placed = field.portalViews?.assigneeTodoFormSource
  if (placed && typeof placed === 'object' && placed.type) return placed
  const binding = resolveBinding(field._bindingId)
  const bindingLevel = (binding?.portalViews as any)?.assigneeTodoFormSource
  if (bindingLevel && typeof bindingLevel === 'object' && bindingLevel.type) return bindingLevel
  return { type: 'subForm', formId: null }
}

/**
 * For a placed sub-table `field`, resolve which Link Form column on the binding's
 * list view drives the inline form-below-table when `assigneeTodoFormSource.type === 'linkForm'`,
 * then return that column's target sub-table binding.
 *
 * Selection precedence:
 *   1. Explicit `assigneeTodoFormSource.linkFormColumnId` (designer pick) — matches the
 *      column whose `props.componentId` equals the configured id.
 *   2. Legacy fallback — the first `type='linkForm'` column on the binding.
 *
 * Returns null when no Link Form column is configured or the target binding isn't loaded;
 * caller falls back to the binding's own subForm in that case.
 */
function findLinkFormTargetBinding(field: FormField): SubTableBinding | null {
  const binding = resolveBinding(field._bindingId)
  if (!binding) return null
  const cols = Array.isArray(binding.columns) ? binding.columns : []
  const source = resolveAssigneeTodoFormSource(field)
  const picked = source.linkFormColumnId
  const pickedKey = picked != null && String(picked).trim() !== '' ? String(picked) : null

  // Helper: read `componentId` off a column regardless of whether it's nested under
  // `props` (live designer state) or hoisted directly (some serialized shapes).
  const componentIdOf = (col: any): string | null => {
    const cid = col?.props?.componentId ?? col?.componentId
    return cid != null ? String(cid) : null
  }
  const targetBindingIdOf = (col: any): number | null => {
    const t = col?.props?.boundSubTableBindingId ?? col?.boundSubTableBindingId
    return t != null ? Number(t) : null
  }

  if (pickedKey) {
    for (const col of cols) {
      if (!col || col.type !== 'linkForm') continue
      if (componentIdOf(col) !== pickedKey) continue
      const targetId = targetBindingIdOf(col)
      if (targetId == null) continue
      const target = resolveBinding(targetId)
      if (target) return target
    }
    // Picked id no longer exists (e.g. column was removed) — fall through to legacy first-match.
  }

  for (const col of cols) {
    if (!col || col.type !== 'linkForm') continue
    const targetId = targetBindingIdOf(col)
    if (targetId == null) continue
    const target = resolveBinding(targetId)
    if (target) return target
  }
  return null
}

/**
 * Decide which binding's data should actually back the inline form-below-table.
 * - `subForm` (or unsupported `formId`): keep the field's own binding.
 * - `linkForm`: switch to the Link Form's target binding so the inline form mirrors
 *   exactly what would show in the Link Form modal — keeping designer and runtime
 *   contracts aligned. Falls back to the own binding when no Link Form column exists,
 *   so a misconfiguration never produces an empty section.
 */
function resolveInlineFormSourceBinding(field: FormField): SubTableBinding | null {
  const own = resolveBinding(field._bindingId)
  if (!own) return null
  const source = resolveAssigneeTodoFormSource(field)
  if (source.type === 'linkForm') {
    const target = findLinkFormTargetBinding(field)
    if (target) return target
  }
  // `formId` is not yet runtime-resolved here (would need cross-form schema lookup); fall through.
  return own
}

/**
 * Resolve the form schema for the inline form-below-table. Per the designer contract:
 *   - `subForm` (default): use the binding's own `formFields`
 *   - `linkForm`: use the Link Form target binding's `formFields`
 *   - `formId`: not yet runtime-supported; falls back to `subForm`
 */
function resolveInlineFormFields(field: FormField): FormField[] {
  const source = resolveInlineFormSourceBinding(field)
  if (!source) return []
  return Array.isArray(source.formFields) ? source.formFields : []
}

/** FK candidates used to align a child (linkForm target) row to a parent row. */
function resolveLinkFkCandidates(target: SubTableBinding): string[] {
  const list: string[] = []
  const explicit = (target as any).foreignKeyField
  if (explicit && String(explicit).trim()) list.push(String(explicit))
  // Same heuristic used by SubTableField's Link Form modal so designer/runtime agree.
  for (const k of ['participant_id', 'participantId', 'parent_id', 'parentId']) {
    if (!list.includes(k)) list.push(k)
  }
  return list
}

/**
 * Find the "current row" for inline form-below-table binding.
 *
 * For `subForm` source (own binding):
 *   1. If `currentMiRowId` is provided, prefer the matching row (handles MI sub-task).
 *   2. Else fall back to the single available row (普通单任务 single-row table).
 *
 * For `linkForm` source (target binding, e.g. subtable2):
 *   1. If `currentMiRowId` is provided, find the target row whose FK === parent rowId.
 *   2. Else if target has a single row, use it.
 *   3. Else `null` — host renders with empty defaults; first edit creates a new row.
 */
function getCurrentRowForInlineForm(field: FormField): Record<string, any> | null {
  const own = resolveBinding(field._bindingId)
  if (!own) return null
  const target = resolveInlineFormSourceBinding(field) ?? own
  const isLinkTarget = target.bindingId !== own.bindingId
  const rows = Array.isArray(target.data) ? target.data : []
  const parentId = props.currentMiRowId

  if (isLinkTarget && parentId != null && String(parentId).trim() !== '') {
    const fkList = resolveLinkFkCandidates(target)
    const match = rows.find(r => {
      if (!r || typeof r !== 'object') return false
      const rec = r as Record<string, unknown>
      return fkList.some(k => {
        const v = rec[k]
        return v != null && v !== '' && String(v) === String(parentId)
      })
    })
    if (match) return { ...(match as Record<string, any>) }
    if (rows.length === 1) return { ...(rows[0] as Record<string, any>) }
    return null
  }

  // subForm path: row identity is the row's own id/rowId
  if (parentId != null && String(parentId).trim() !== '') {
    const match = rows.find(r => {
      if (!r || typeof r !== 'object') return false
      const rec = r as Record<string, unknown>
      return String(rec.id ?? rec.rowId ?? '') === String(parentId)
    })
    if (match) return { ...(match as Record<string, any>) }
  }
  if (rows.length === 1) return { ...(rows[0] as Record<string, any>) }
  return null
}

/**
 * When the inline form below is edited, merge the new values back into the matching
 * row in the EFFECTIVE source binding (own binding for `subForm`, link target for
 * `linkForm`) and emit `update:subTableData` so the host (tasks/detail or
 * applications/detail) can persist it via the existing data flow.
 *
 * When no matching child row exists in the link-target binding yet, a fresh row is
 * appended and the FK column is populated with `currentMiRowId` so persistence stays
 * within the existing dw_table_data → child-rows pipeline.
 */
function handleInlineFormUpdate(field: FormField, mergedRow: Record<string, any>) {
  const own = resolveBinding(field._bindingId)
  if (!own) return
  const target = resolveInlineFormSourceBinding(field) ?? own
  const isLinkTarget = target.bindingId !== own.bindingId
  const rows = Array.isArray(target.data) ? target.data.map(r => ({ ...(r as Record<string, any>) })) : []
  const parentId = props.currentMiRowId

  let idx = -1
  if (isLinkTarget && parentId != null && String(parentId).trim() !== '') {
    const fkList = resolveLinkFkCandidates(target)
    idx = rows.findIndex(r => {
      if (!r || typeof r !== 'object') return false
      const rec = r as Record<string, unknown>
      return fkList.some(k => {
        const v = rec[k]
        return v != null && v !== '' && String(v) === String(parentId)
      })
    })
  } else if (isLinkTarget && rows.length === 1) {
    idx = 0
  } else if (parentId != null && String(parentId).trim() !== '') {
    idx = rows.findIndex(r => {
      if (!r || typeof r !== 'object') return false
      const rec = r as Record<string, unknown>
      return String(rec.id ?? rec.rowId ?? '') === String(parentId)
    })
  } else if (rows.length === 1) {
    idx = 0
  }

  if (idx >= 0) {
    rows[idx] = { ...rows[idx], ...mergedRow }
  } else {
    const fresh: Record<string, any> = { ...mergedRow }
    if (isLinkTarget && parentId != null && String(parentId).trim() !== '') {
      // Seed the FK so the new child row aligns with the parent participant.
      const explicit = (target as any).foreignKeyField
      const fkField = explicit && String(explicit).trim() ? String(explicit) : 'parent_id'
      if (fresh[fkField] == null || fresh[fkField] === '') fresh[fkField] = parentId
    }
    rows.push(fresh)
  }
  handleSubTableUpdate(target.bindingId, rows)
}

// Lookup selected data state
const lookupSelectedData = ref<Record<string, Record<string, any>>>({})
const lookupLoadedViewFields = ref<Record<string, any[]>>({})
const handleLookupSelect = (fieldKey: string, row: Record<string, any>) => {
  lookupSelectedData.value[fieldKey] = row
}
const handleLookupClear = (fieldKey: string) => {
  delete lookupSelectedData.value[fieldKey]
}

// Manage file upload lists independently to avoid re-render issues when deriving from formData
const uploadFileLists = ref<Record<string, Array<{ name: string; url: string; uid?: number }>>>({})

// Get all fields (including fields in tabs)
const allFields = computed(() => {
  const flatten = (items: FormField[]): FormField[] =>
    items.flatMap(field => field.children?.length ? flatten(field.children) : [field])
  if (hasTabs.value && props.tabs) {
    return props.tabs.flatMap(tab => flatten(tab.fields))
  }
  return flatten(props.fields)
})

// ---------------------------------------------------------------------------
// Task 7.2: BusinessLogicEngine integration
// ---------------------------------------------------------------------------
const engine = new BusinessLogicEngine()
const engineVisibility = ref(new Map<string, boolean>())
const engineOptions = ref(new Map<string, Array<{ label: string; value: any }>>())
const engineFieldStates = ref(new Map<string, { disabled?: boolean; required?: boolean }>())
const engineCalculatedValues = ref(new Map<string, number>())

function initEngine() {
  if (props.config) {
    engine.init(props.config)
  }
}

// ---------------------------------------------------------------------------
// User search — listen to FieldRenderer search:users event (Req 11.2)
// ---------------------------------------------------------------------------
const userSearchResults = ref(new Map<string, Array<{ id: string; name: string }>>())

async function handleUserSearch(query: string, fieldKey: string) {
  try {
    const results = await userApi.searchUsers(query)
    userSearchResults.value.set(fieldKey, results)
    userSearchResults.value = new Map(userSearchResults.value)
  } catch {
    userSearchResults.value.set(fieldKey, [])
    userSearchResults.value = new Map(userSearchResults.value)
  }
}

function applyEngineResult(result: {
  visibilityChanges: Map<string, boolean>
  calculatedValues: Map<string, number>
  optionChanges: Map<string, Array<{ label: string; value: any }>>
  stateChanges: Map<string, { disabled?: boolean; required?: boolean }>
}) {
  // Merge visibility changes
  for (const [k, v] of result.visibilityChanges) {
    engineVisibility.value.set(k, v)
  }
  // Merge calculated values and update formData
  for (const [k, v] of result.calculatedValues) {
    engineCalculatedValues.value.set(k, v)
    formData.value[k] = v
  }
  // Merge option changes
  for (const [k, v] of result.optionChanges) {
    engineOptions.value.set(k, v)
  }
  // Merge state changes
  for (const [k, v] of result.stateChanges) {
    engineFieldStates.value.set(k, v)
  }
  // Trigger reactivity
  engineVisibility.value = new Map(engineVisibility.value)
  engineOptions.value = new Map(engineOptions.value)
  engineFieldStates.value = new Map(engineFieldStates.value)
  engineCalculatedValues.value = new Map(engineCalculatedValues.value)
}

// ---------------------------------------------------------------------------
// Form data initialization
// ---------------------------------------------------------------------------
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
  setTimeout(() => { isInternalUpdate = false }, 0)
  // Element Plus AsyncValidator resolves as micro-tasks after nextTick;
  // use setTimeout (macro-task) to guarantee clearValidate runs last.
  setTimeout(() => {
    const el = formRef.value
    if (el && typeof (el as { clearValidate?: () => void }).clearValidate === 'function') {
      el.clearValidate()
    }
  }, 0)
}

// ---------------------------------------------------------------------------
// Form rules
// ---------------------------------------------------------------------------
const formRules = computed<FormRules>(() => {
  if (props.readonly) return {}
  const rules: FormRules = {}
  allFields.value.forEach(field => {
    if (field.required || field.rules) {
      const fieldRules: any[] = []
      if (field.required) {
        fieldRules.push({
          required: true,
          message: t('common.pleaseInput', { label: field.label }),
          trigger: field.type === 'select' ? 'change' : 'blur'
        })
      }
      if (field.rules) {
        fieldRules.push(...field.rules)
      }
      rules[field.key] = fieldRules
    }
  })
  return rules
})

// ---------------------------------------------------------------------------
// Field change handler (Task 7.1 + 7.2)
// ---------------------------------------------------------------------------
function handleFieldChange(key: string, value: any) {
  formData.value[key] = value
  emit('change', key, value)

  // Task 7.2: Trigger engine evaluation on field change
  if (props.config) {
    const result = engine.onFieldChange(key, value, formData.value)
    applyEngineResult(result)
  }
}

// ---------------------------------------------------------------------------
// Upload handlers
// ---------------------------------------------------------------------------
function handleUploadSuccess(response: any, _file: any, fieldKey: string) {
  const url = response?.data?.url || ''
  formData.value[fieldKey] = url
  emit('update:modelValue', { ...formData.value })
}

function handleUploadRemove(_file: any, fieldKey: string) {
  formData.value[fieldKey] = ''
  emit('update:modelValue', { ...formData.value })
}

// ---------------------------------------------------------------------------
// Task 7.4: Sub-table summary integration
// ---------------------------------------------------------------------------

// ---------------------------------------------------------------------------
// SubTableField config helpers (Req 10.1, 10.2, 10.3)
// ---------------------------------------------------------------------------
function getSubFormRowFormulas(bindingId?: number) {
  if (!bindingId || !props.config?.subForms) return undefined
  return props.config.subForms[String(bindingId)]?.rowFormulas
}

function getSummaryColumns(bindingId?: number) {
  if (!bindingId || !props.config?.summaryRules) return undefined
  return props.config.summaryRules
    .filter(r => r.sourceBindingId === bindingId)
    .map(r => r.sourceColumn)
}

function getSummaryAggregations(bindingId?: number) {
  if (!bindingId || !props.config?.summaryRules) return undefined
  const aggs: Record<string, 'SUM' | 'AVG' | 'COUNT' | 'MIN' | 'MAX'> = {}
  props.config.summaryRules
    .filter(r => r.sourceBindingId === bindingId)
    .forEach(r => { aggs[r.sourceColumn] = r.aggregation })
  return Object.keys(aggs).length > 0 ? aggs : undefined
}

function getSubTableValidation(bindingId?: number) {
  if (!bindingId || !props.config?.subTableValidation) return undefined
  return props.config.subTableValidation[String(bindingId)]
}

function handleSubTableUpdate(bindingId: number, rows: any[]) {
  emit('update:subTableData', bindingId, rows)

  // Trigger engine summary calculations
  if (props.config) {
    const summaryResult = engine.onSubTableChange(bindingId, rows, formData.value)
    for (const [targetField, value] of summaryResult.summaryValues) {
      formData.value[targetField] = value
      engineCalculatedValues.value.set(targetField, value)
    }
    engineCalculatedValues.value = new Map(engineCalculatedValues.value)
  }
}

// ---------------------------------------------------------------------------
// Watchers
// ---------------------------------------------------------------------------
watchThrottled(
  formData,
  (newVal) => {
    if (!isInternalUpdate && !props.readonly) {
      emit('update:modelValue', { ...newVal })
    }
  },
  { deep: true, throttle: 150 },
)

watch(() => props.modelValue, (newVal, oldVal) => {
  if (!isEqual(newVal, oldVal)) {
    initFormData()
  }
}, { deep: true })

watch(allFields, (newFields, oldFields) => {
  const hasChanged = newFields.length !== oldFields.length ||
    newFields.some((f, i) => f.key !== oldFields[i]?.key)
  if (hasChanged) {
    initFormData()
  }
})

watch(
  () => props.config,
  () => {
    initEngine()
  },
)

// ---------------------------------------------------------------------------
// Task 7.3: Form validation with engine integration
// ---------------------------------------------------------------------------

/**
 * Inject an engine validation error into an Element Plus form-item via DOM.
 * Adds the `is-error` class and appends an `.el-form-item__error` element.
 */
function injectFieldError(fieldKey: string, message: string) {
  const itemEl = document.querySelector(
    `.el-form-item[prop="${fieldKey}"]`
  ) as HTMLElement | null
  if (!itemEl) return
  itemEl.classList.add('is-error')
  const contentEl = itemEl.querySelector('.el-form-item__content')
  if (!contentEl) return
  // Remove any previously injected engine errors
  contentEl.querySelectorAll('.engine-error').forEach(el => el.remove())
  const errorDiv = document.createElement('div')
  errorDiv.className = 'el-form-item__error engine-error'
  errorDiv.textContent = message
  contentEl.appendChild(errorDiv)
}

/**
 * Clear all previously injected engine validation errors from the form.
 */
function clearEngineErrors() {
  document.querySelectorAll('.engine-error').forEach(el => el.remove())
  // Note: we don't remove is-error class here because Element Plus may have its own errors
}

const validate = async (): Promise<boolean> => {
  if (!formRef.value) return false

  // Clear previously injected engine errors before re-validating
  clearEngineErrors()

  let elPlusValid = true
  try {
    await formRef.value.validate()
  } catch {
    elPlusValid = false
  }

  // Engine validation (cross-field + custom rules)
  if (props.config) {
    const engineResult = engine.validateAll(formData.value)
    const crossResult = engine.validateCrossField(formData.value)

    if (!engineResult.valid || !crossResult.valid) {
      // Inject engine field errors into Element Plus form-item error state via DOM
      for (const [fieldKey, errors] of engineResult.fieldErrors) {
        if (errors.length > 0) {
          injectFieldError(fieldKey, errors[0])
        }
      }
      // Inject cross-field errors into targetField form-items
      for (const err of crossResult.errors) {
        injectFieldError(err.targetField, err.message)
      }
      // Scroll to first error field
      nextTick(() => {
        const firstError = document.querySelector('.el-form-item.is-error')
        firstError?.scrollIntoView({ behavior: 'smooth', block: 'center' })
      })
      return false
    }
  }

  if (!elPlusValid) {
    // Scroll to first Element Plus error
    nextTick(() => {
      const firstError = document.querySelector('.el-form-item.is-error')
      if (firstError) {
        firstError.scrollIntoView({ behavior: 'smooth', block: 'center' })
      }
    })
  }

  return elPlusValid
}

// ---------------------------------------------------------------------------
// Task 7.5: Auto-save to localStorage
// ---------------------------------------------------------------------------
const AUTO_SAVE_INTERVAL = 30_000 // 30 seconds
let autoSaveTimer: ReturnType<typeof setInterval> | null = null

function getAutoSaveKey(): string | null {
  if (props.functionUnitId && props.formId) {
    return `form_autosave_${props.functionUnitId}_${props.formId}`
  }
  return null
}

function autoSave() {
  const key = getAutoSaveKey()
  if (!key || props.readonly) return
  try {
    localStorage.setItem(key, JSON.stringify(formData.value))
  } catch (err) {
    console.warn('[FormRenderer] Auto-save to localStorage failed:', err)
  }
}

function startAutoSave() {
  stopAutoSave()
  if (getAutoSaveKey() && !props.readonly) {
    autoSaveTimer = setInterval(autoSave, AUTO_SAVE_INTERVAL)
  }
}

function stopAutoSave() {
  if (autoSaveTimer) {
    clearInterval(autoSaveTimer)
    autoSaveTimer = null
  }
}

function clearAutoSave() {
  const key = getAutoSaveKey()
  if (key) {
    try {
      localStorage.removeItem(key)
    } catch (err) {
      console.warn('[FormRenderer] Failed to clear auto-save:', err)
    }
  }
  stopAutoSave()
}

async function checkAutoSaveRestore() {
  const key = getAutoSaveKey()
  if (!key || props.readonly) return

  try {
    const saved = localStorage.getItem(key)
    if (!saved) return

    const savedData = JSON.parse(saved)
    if (!savedData || typeof savedData !== 'object') return

    await ElMessageBox.confirm(
      t('formRenderer.autoSaveRestorePrompt'),
      t('formRenderer.autoSaveTitle'),
      {
        confirmButtonText: t('formRenderer.restore'),
        cancelButtonText: t('formRenderer.discard'),
        type: 'info',
      }
    )
    // User chose to restore
    isInternalUpdate = true
    formData.value = { ...formData.value, ...savedData }
    setTimeout(() => { isInternalUpdate = false }, 0)
    emit('update:modelValue', { ...formData.value })

    // Trigger engine re-evaluation for all restored fields (Req 12.1, 12.2)
    if (props.config) {
      for (const [key, value] of Object.entries(formData.value)) {
        if (value != null && value !== '') {
          const result = engine.onFieldChange(key, value, formData.value)
          applyEngineResult(result)
        }
      }
    }
  } catch {
    // User chose to discard or parse error — clear saved data
    clearAutoSave()
  }
}

// ---------------------------------------------------------------------------
// Existing exposed methods
// ---------------------------------------------------------------------------
const resetForm = () => {
  formRef.value?.resetFields()
  initFormData()
}

const getFormData = () => {
  return { ...formData.value }
}

const setFieldValue = (key: string, value: any) => {
  formData.value[key] = value
}

// ---------------------------------------------------------------------------
// Lifecycle
// ---------------------------------------------------------------------------
onMounted(() => {
  initFormData()
  initEngine()
  // Task 7.5: Check for auto-saved data, then start auto-save timer
  checkAutoSaveRestore().then(() => {
    startAutoSave()
  })
})

onBeforeUnmount(() => {
  stopAutoSave()
})

// ---------------------------------------------------------------------------
// Expose (keep existing + add clearAutoSave)
// ---------------------------------------------------------------------------
defineExpose({
  validate,
  resetForm,
  getFormData,
  setFieldValue,
  clearAutoSave,
  // Exposed for testing (Req 10 property test)
  getSubFormRowFormulas,
  getSummaryColumns,
  getSummaryAggregations,
  getSubTableValidation,
})
</script>

<style scoped lang="scss">
.form-renderer {
  width: 100%;

  :deep(.el-form-item__label) {
    font-weight: 500;
    white-space: nowrap;
    padding-right: 16px;
  }

  /* Form item content in flex layout must be shrinkable and fill remaining width so dropdowns/date pickers render at 100% */
  :deep(.el-form-item__content) {
    flex: 1;
    min-width: 0;
    max-width: 100%;
  }

  :deep(.el-form-item__content .el-select),
  :deep(.el-form-item__content .el-tree-select),
  :deep(.el-form-item__content .el-cascader),
  :deep(.el-form-item__content .el-date-editor) {
    width: 100% !important;
  }

  :deep(.el-form-item__content .el-select .el-select__wrapper),
  :deep(.el-form-item__content .el-tree-select .el-select__wrapper) {
    width: 100%;
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

  .form-layout-card {
    width: 100%;
    margin-bottom: 18px;

    :deep(.el-card__header) {
      padding: 12px 16px;
      font-weight: 500;
      background: #fafafa;
    }
  }

  .form-layout-card-title {
    color: #303133;
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

  .signature-preview {
    max-width: 200px;
    max-height: 80px;
    object-fit: contain;
    border: 1px solid #e4e7ed;
    border-radius: 4px;
    background: #fff;
  }

  .lookup-form-item {
    margin-bottom: 18px;

    :deep(.el-form-item__label) {
      display: flex;
      align-items: center;
    }
  }

  .lookup-label-text {
    display: flex;
    align-items: center;
    gap: 4px;
    font-size: 14px;
    color: #606266;
  }

  .lookup-label-icon {
    color: #409eff;
    font-size: 14px;
  }

  .lookup-field-wrapper {
    width: 100%;
  }
}
</style>

<style lang="scss">
/* Scoped to .form-renderer container to prevent global style leak (Req 30) */
.form-renderer {
  .form-renderer-popper {
    z-index: 2050;
  }

  :deep(.el-select__popper) {
    z-index: 2050;
  }

  :deep(.el-picker__popper) {
    z-index: 2050;
  }

  :deep(.el-cascader__dropdown) {
    z-index: 2050;
  }

  :deep(.el-tree-select__popper) {
    z-index: 2050;
  }
}
</style>
