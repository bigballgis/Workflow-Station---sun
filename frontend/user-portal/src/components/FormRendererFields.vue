<script setup lang="ts">
import { inject, ref, defineAsyncComponent } from 'vue'
import { Search } from '@element-plus/icons-vue'
import FieldRenderer from './FieldRenderer.vue'
const SubTableField = defineAsyncComponent({
  loader: () => import('./SubTableField.vue'),
  delay: 0,
})
import SubTableInlineForm from './SubTableInlineForm.vue'
import LookupField from './lookup/LookupField.vue'
import LookupViewDisplay from './lookup/LookupViewDisplay.vue'
import type { FormField } from './formRendererHelpers'
import { FORM_RENDERER_FIELDS_CTX } from './formRendererFieldsContext'
import { isDisplayOnlyLayoutField } from './formRendererHelpers'

defineOptions({ name: 'FormRendererFields' })

const props = withDefaults(
  defineProps<{
    fields: FormField[]
    /** Inside fcCol — stack fields vertically without grid el-col wrappers. */
    inColumn?: boolean
    /** Inside fcRow — expect fcCol children rendered as el-col. */
    rowColumns?: boolean
  }>(),
  {
    inColumn: false,
    rowColumns: false,
  },
)

const ctx = inject(FORM_RENDERER_FIELDS_CTX)
if (!ctx) {
  throw new Error('FormRendererFields requires FORM_RENDERER_FIELDS_CTX from FormRenderer')
}

const collapseActiveByKey = ref<Record<string, string[]>>({})

function collapseActiveNames(field: FormField): string[] {
  const key = field.key
  const existing = collapseActiveByKey.value[key]
  if (existing) return existing
  const first = field.collapsePanels?.[0]?.name
  const initial = first != null ? [String(first)] : []
  collapseActiveByKey.value[key] = initial
  return initial
}

function onCollapseActiveChange(fieldKey: string, names: string | string[]) {
  collapseActiveByKey.value[fieldKey] = Array.isArray(names) ? names.map(String) : [String(names)]
}
</script>

<template>
  <template
    v-for="field in fields"
    :key="field.key"
  >
    <!-- fcCol inside fcRow -->
    <el-col
      v-if="rowColumns && field.type === 'col'"
      :span="field.span || 12"
    >
      <FormRendererFields
        :fields="field.children || []"
        in-column
      />
    </el-col>

    <!-- fcRow layout -->
    <template v-else-if="field.type === 'row'">
      <el-col
        v-if="!inColumn"
        :span="24"
      >
        <el-row :gutter="field.gutter ?? 20">
          <FormRendererFields
            :fields="field.children || []"
            row-columns
          />
        </el-row>
      </el-col>
      <el-row
        v-else
        :gutter="field.gutter ?? 20"
        class="form-row-in-column"
      >
        <FormRendererFields
          :fields="field.children || []"
          row-columns
        />
      </el-row>
    </template>

    <!-- Card layout -->
    <template v-else-if="field.type === 'card'">
      <el-col
        v-if="!inColumn"
        :span="field.span || 24"
      >
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
            <FormRendererFields :fields="field.children || []" />
          </el-row>
        </el-card>
      </el-col>
      <el-card
        v-else
        shadow="never"
        class="form-layout-card form-layout-card--stacked"
      >
        <template
          v-if="field.label"
          #header
        >
          <span class="form-layout-card-title">{{ field.label }}</span>
        </template>
        <FormRendererFields
          :fields="field.children || []"
          in-column
        />
      </el-card>
    </template>

    <!-- Nested el-tabs (inside tab pane / card) -->
    <template v-else-if="field.type === 'tabs' && field.tabs?.length">
      <el-col
        v-if="!inColumn"
        :span="24"
      >
        <el-tabs class="form-renderer-nested-tabs">
          <el-tab-pane
            v-for="(tab, tabIdx) in field.tabs"
            :key="`${field.key}-tab-${tabIdx}-${String(tab.name)}`"
            :label="tab.label"
            :name="tab.name"
          >
            <FormRendererFields :fields="tab.fields || []" />
          </el-tab-pane>
        </el-tabs>
      </el-col>
      <el-tabs
        v-else
        class="form-renderer-nested-tabs"
      >
        <el-tab-pane
          v-for="(tab, tabIdx) in field.tabs"
          :key="`${field.key}-tab-${tabIdx}-${String(tab.name)}`"
          :label="tab.label"
          :name="tab.name"
        >
          <FormRendererFields
            :fields="tab.fields || []"
            in-column
          />
        </el-tab-pane>
      </el-tabs>
    </template>

    <!-- Nested el-collapse -->
    <template v-else-if="field.type === 'collapse' && field.collapsePanels?.length">
      <el-col
        v-if="!inColumn"
        :span="24"
      >
        <el-collapse
          :model-value="collapseActiveNames(field)"
          class="form-renderer-collapse"
          @update:model-value="(names) => onCollapseActiveChange(field.key, names)"
        >
          <el-collapse-item
            v-for="(panel, panelIdx) in field.collapsePanels"
            :key="`${field.key}-collapse-${panelIdx}-${String(panel.name)}`"
            :title="panel.label"
            :name="panel.name"
          >
            <FormRendererFields :fields="panel.fields || []" />
          </el-collapse-item>
        </el-collapse>
      </el-col>
      <el-collapse
        v-else
        :model-value="collapseActiveNames(field)"
        class="form-renderer-collapse"
        @update:model-value="(names) => onCollapseActiveChange(field.key, names)"
      >
        <el-collapse-item
          v-for="(panel, panelIdx) in field.collapsePanels"
          :key="`${field.key}-collapse-${panelIdx}-${String(panel.name)}`"
          :title="panel.label"
          :name="panel.name"
        >
          <FormRendererFields
            :fields="panel.fields || []"
            in-column
          />
        </el-collapse-item>
      </el-collapse>
    </template>

    <!-- Display-only auxiliary widgets (Title, Divider, …) -->
    <template v-else-if="isDisplayOnlyLayoutField(field)">
      <el-col
        v-if="!inColumn"
        :span="field.span || 24"
        class="form-display-only-wrap"
      >
        <FieldRenderer
          :field="field"
          :model-value="ctx.formData[field.key]"
          :form-data="ctx.formData"
          :readonly="true"
        />
      </el-col>
      <div
        v-else
        class="form-display-only-wrap"
      >
        <FieldRenderer
          :field="field"
          :model-value="ctx.formData[field.key]"
          :form-data="ctx.formData"
          :readonly="true"
        />
      </div>
    </template>

    <!-- Sub-table -->
    <template v-else-if="field.type === 'subTable'">
      <el-col
        v-if="!inColumn"
        :span="24"
        style="padding: 0;"
      >
        <SubTableField
          v-if="ctx.resolveBinding(field._bindingId) && ctx.shouldRenderPlacedSubTableField(field)"
          :title="String(ctx.resolveBinding(field._bindingId)?.tableName ?? '')"
          :columns="(ctx.resolveBinding(field._bindingId)?.columns as any[]) || []"
          :model-value="(ctx.resolveBinding(field._bindingId)?.data as any[]) || []"
          :mi-participant-row-id="ctx.resolveMiParticipantSeedForSubTableAdd?.(field._bindingId).rowId ?? null"
          :mi-parent-participant-row="ctx.resolveMiParticipantSeedForSubTableAdd?.(field._bindingId).parentRow ?? null"
          :mi-parent-table-id="ctx.resolveMiParticipantSeedForSubTableAdd?.(field._bindingId).parentTableId ?? null"
          :editable="ctx.isSubTableEditable(field._bindingId)"
          :row-formulas="ctx.getSubFormRowFormulas(field._bindingId)"
          :summary-columns="ctx.getSummaryColumns(field._bindingId)"
          :summary-aggregations="ctx.getSummaryAggregations(field._bindingId)"
          :validation-config="ctx.getSubTableValidation(field._bindingId)"
          :upload-url="ctx.uploadUrl"
          :task-id="ctx.taskId"
          :assignee-field="ctx.subTableAssigneeField(field._bindingId)"
          :show-assign-button="ctx.showSubTableAssignColumn(field._bindingId)"
          :can-assign="!ctx.readonly && ctx.showSubTableAssignColumn(field._bindingId)"
          :enable-polling="ctx.enableSubTablePolling"
          :polling-interval="ctx.subTablePollingInterval"
          :linked-sub-table-bindings="ctx.linkableSubTableBindings"
          :suppress-link-form-initial-data="ctx.suppressLinkFormInitialData"
          :show-link-form-dialog-footer="ctx.showLinkFormDialogFooter"
          :link-form-click-scroll-to-inline="ctx.linkFormScrollToInlineEnabled(field)"
          :show-task-status="ctx.subTableShowTaskStatusInitiator(field)"
          :show-view-detail="ctx.subTableShowViewDetailInitiator(field)"
          :compact-lookup-cells="ctx.subTableCompactLookupCells(field)"
          :primary-key-fields="ctx.resolveBinding(field._bindingId)?.primaryKeyFields as string[] | undefined"
          :field-definitions="ctx.resolveBinding(field._bindingId)?.fieldDefinitions as any"
          :binding-link-mode="ctx.resolveBinding(field._bindingId)?.bindingLinkMode"
          :binding-foreign-key-field="ctx.resolveBinding(field._bindingId)?.foreignKeyField"
          :table-id="ctx.resolveBinding(field._bindingId)?.tableId"
          :function-unit-id="ctx.functionUnitId"
          :primary-form-data="ctx.primaryFormData"
          :sub-table-bindings-for-context="ctx.subTableBindingsForContext"
          :primary-table-display-name="ctx.primaryTableDisplayName"
          :primary-table-id="ctx.primaryTableId"
          :parent-tables-by-id="ctx.parentTablesById"
          style="margin-bottom: 16px;"
          @update:model-value="(rows: any[]) => ctx.handleSubTableUpdate(field._bindingId!, rows)"
          @update:primary-form-data="ctx.handlePrimaryFormDataPatch?.($event)"
          @update:linked-sub-table-data="ctx.handleSubTableUpdate"
          @view-detail="(row: any) => ctx.emitViewSubtaskDetail(row, ctx.resolveBinding(field._bindingId)?.data as any[])"
          @link-form-scroll-to-inline="ctx.scrollSubTableInlineIntoView(field._bindingId)"
        />
        <div
          v-if="ctx.resolveBinding(field._bindingId) && ctx.subTableMode(field) === 'formBelowTable'"
          class="sub-table-inline-anchor"
          :ref="(el) => ctx.setSubTableInlineAnchor(field._bindingId, el as HTMLElement | null)"
        >
          <SubTableInlineForm
            :title="ctx.resolveInlineFormTableTitle(field)"
            :fields="ctx.resolveInlineFormFields(field)"
            :current-row="ctx.getCurrentRowForInlineForm(field)"
            :readonly="ctx.inlineSubTableFormReadonly(field)"
            :label-width="ctx.labelWidth"
            :sub-table-bindings="ctx.subTableBindings as any[]"
            :linked-sub-table-bindings="ctx.linkableSubTableBindings as any[]"
            :suppress-link-only-standalone-sub-tables="ctx.viewContext === 'initiatorRequest'"
            @update:row="(row: Record<string, any>) => ctx.handleInlineFormUpdate(field, row)"
            @save="ctx.handleInlineFormSave?.()"
          />
        </div>
      </el-col>
      <div
        v-else
        class="form-col-subtable"
      >
        <SubTableField
          v-if="ctx.resolveBinding(field._bindingId) && ctx.shouldRenderPlacedSubTableField(field)"
          :title="String(ctx.resolveBinding(field._bindingId)?.tableName ?? '')"
          :columns="(ctx.resolveBinding(field._bindingId)?.columns as any[]) || []"
          :model-value="(ctx.resolveBinding(field._bindingId)?.data as any[]) || []"
          :mi-participant-row-id="ctx.resolveMiParticipantSeedForSubTableAdd?.(field._bindingId).rowId ?? null"
          :mi-parent-participant-row="ctx.resolveMiParticipantSeedForSubTableAdd?.(field._bindingId).parentRow ?? null"
          :mi-parent-table-id="ctx.resolveMiParticipantSeedForSubTableAdd?.(field._bindingId).parentTableId ?? null"
          :editable="ctx.isSubTableEditable(field._bindingId)"
          :row-formulas="ctx.getSubFormRowFormulas(field._bindingId)"
          :summary-columns="ctx.getSummaryColumns(field._bindingId)"
          :summary-aggregations="ctx.getSummaryAggregations(field._bindingId)"
          :validation-config="ctx.getSubTableValidation(field._bindingId)"
          :upload-url="ctx.uploadUrl"
          :task-id="ctx.taskId"
          :assignee-field="ctx.subTableAssigneeField(field._bindingId)"
          :show-assign-button="ctx.showSubTableAssignColumn(field._bindingId)"
          :can-assign="!ctx.readonly && ctx.showSubTableAssignColumn(field._bindingId)"
          :enable-polling="ctx.enableSubTablePolling"
          :polling-interval="ctx.subTablePollingInterval"
          :linked-sub-table-bindings="ctx.linkableSubTableBindings"
          :suppress-link-form-initial-data="ctx.suppressLinkFormInitialData"
          :show-link-form-dialog-footer="ctx.showLinkFormDialogFooter"
          :link-form-click-scroll-to-inline="ctx.linkFormScrollToInlineEnabled(field)"
          :show-task-status="ctx.subTableShowTaskStatusInitiator(field)"
          :show-view-detail="ctx.subTableShowViewDetailInitiator(field)"
          :compact-lookup-cells="ctx.subTableCompactLookupCells(field)"
          :primary-key-fields="ctx.resolveBinding(field._bindingId)?.primaryKeyFields as string[] | undefined"
          :field-definitions="ctx.resolveBinding(field._bindingId)?.fieldDefinitions as any"
          :binding-link-mode="ctx.resolveBinding(field._bindingId)?.bindingLinkMode"
          :binding-foreign-key-field="ctx.resolveBinding(field._bindingId)?.foreignKeyField"
          :table-id="ctx.resolveBinding(field._bindingId)?.tableId"
          :function-unit-id="ctx.functionUnitId"
          :primary-form-data="ctx.primaryFormData"
          :sub-table-bindings-for-context="ctx.subTableBindingsForContext"
          :primary-table-display-name="ctx.primaryTableDisplayName"
          :primary-table-id="ctx.primaryTableId"
          :parent-tables-by-id="ctx.parentTablesById"
          style="margin-bottom: 16px;"
          @update:model-value="(rows: any[]) => ctx.handleSubTableUpdate(field._bindingId!, rows)"
          @update:primary-form-data="ctx.handlePrimaryFormDataPatch?.($event)"
          @update:linked-sub-table-data="ctx.handleSubTableUpdate"
          @view-detail="(row: any) => ctx.emitViewSubtaskDetail(row, ctx.resolveBinding(field._bindingId)?.data as any[])"
          @link-form-scroll-to-inline="ctx.scrollSubTableInlineIntoView(field._bindingId)"
        />
      </div>
    </template>

    <!-- Lookup -->
    <template v-else-if="field.type === 'lookup'">
      <el-col
        v-if="!inColumn && ctx.isFieldVisible(field.key)"
        :span="field.span || 24"
      >
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
              v-model="ctx.formData[field.key]"
              :table-id="(field as any)._lookupTableId"
              :search-fields="(field as any)._lookupSearchFields || []"
              :display-field="(field as any)._lookupDisplayField || ''"
              :display-fields="(field as any)._lookupDisplayFields || []"
              :selected-display-field="(field as any)._lookupSelectedDisplayField || ''"
              :filter-conditions="(field as any)._lookupFilterConditions || []"
              :view-fields="(field as any)._lookupViewFields || []"
              :placeholder="field.placeholder"
              :readonly="ctx.isFieldReadonly(field)"
              @select="(row: any) => ctx.handleLookupSelect(field.key, row)"
              @clear="() => ctx.handleLookupClear(field.key)"
              @view-fields-loaded="(loaded: any[]) => { ctx.lookupLoadedViewFields[field.key] = loaded }"
            />
            <LookupViewDisplay
              v-if="ctx.lookupSelectedData[field.key] && ctx.lookupShowBackfillView(field)"
              :selected-data="ctx.lookupSelectedData[field.key]"
              :view-fields="(field as any)._lookupViewFields?.length ? (field as any)._lookupViewFields : (ctx.lookupLoadedViewFields[field.key] || [])"
            />
          </div>
        </el-form-item>
      </el-col>
      <div
        v-else-if="inColumn && ctx.isFieldVisible(field.key)"
        class="form-col-field"
      >
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
              v-model="ctx.formData[field.key]"
              :table-id="(field as any)._lookupTableId"
              :search-fields="(field as any)._lookupSearchFields || []"
              :display-field="(field as any)._lookupDisplayField || ''"
              :display-fields="(field as any)._lookupDisplayFields || []"
              :selected-display-field="(field as any)._lookupSelectedDisplayField || ''"
              :filter-conditions="(field as any)._lookupFilterConditions || []"
              :view-fields="(field as any)._lookupViewFields || []"
              :placeholder="field.placeholder"
              :readonly="ctx.isFieldReadonly(field)"
              @select="(row: any) => ctx.handleLookupSelect(field.key, row)"
              @clear="() => ctx.handleLookupClear(field.key)"
              @view-fields-loaded="(loaded: any[]) => { ctx.lookupLoadedViewFields[field.key] = loaded }"
            />
            <LookupViewDisplay
              v-if="ctx.lookupSelectedData[field.key] && ctx.lookupShowBackfillView(field)"
              :selected-data="ctx.lookupSelectedData[field.key]"
              :view-fields="(field as any)._lookupViewFields?.length ? (field as any)._lookupViewFields : (ctx.lookupLoadedViewFields[field.key] || [])"
            />
          </div>
        </el-form-item>
      </div>
    </template>

    <!-- Regular field -->
    <template v-else>
      <el-col
        v-if="!inColumn && ctx.isFieldVisible(field.key)"
        :span="field.span || 24"
      >
        <el-form-item
          :data-field-key="field.key"
          :label="field.label"
          :prop="field.key"
          :required="field.required"
          :class="{ 'is-error': !!ctx.scriptFieldErrors[field.key] }"
        >
          <FieldRenderer
            :field="field"
            :model-value="ctx.formData[field.key]"
            :form-data="ctx.formData"
            :readonly="ctx.isFieldReadonly(field)"
            :disabled="ctx.engineFieldStates.get(field.key)?.disabled || false"
            :visible="ctx.isFieldVisible(field.key)"
            :options="ctx.engineOptions.get(field.key)"
            :upload-url="ctx.uploadUrl"
            :user-search-results="ctx.userSearchResults.get(field.key)"
            @update:model-value="(val: any) => ctx.handleFieldChange(field.key, val)"
            @field-blur="() => ctx.handleFieldBlur(field.key)"
            @upload:success="(res: any, file: any, key: string) => ctx.handleUploadSuccess(res, file, key)"
            @upload:remove="(file: any, key: string) => ctx.handleUploadRemove(file, key)"
            @search:users="ctx.handleUserSearch"
          />
          <div
            v-if="ctx.scriptFieldErrors[field.key]"
            class="el-form-item__error script-field-error"
          >
            {{ ctx.scriptFieldErrors[field.key] }}
          </div>
        </el-form-item>
      </el-col>
      <div
        v-else-if="ctx.isFieldVisible(field.key)"
        class="form-col-field"
      >
        <el-form-item
          :data-field-key="field.key"
          :label="field.label"
          :prop="field.key"
          :required="field.required"
          :class="{ 'is-error': !!ctx.scriptFieldErrors[field.key] }"
        >
          <FieldRenderer
            :field="field"
            :model-value="ctx.formData[field.key]"
            :form-data="ctx.formData"
            :readonly="ctx.isFieldReadonly(field)"
            :disabled="ctx.engineFieldStates.get(field.key)?.disabled || false"
            :visible="ctx.isFieldVisible(field.key)"
            :options="ctx.engineOptions.get(field.key)"
            :upload-url="ctx.uploadUrl"
            :user-search-results="ctx.userSearchResults.get(field.key)"
            @update:model-value="(val: any) => ctx.handleFieldChange(field.key, val)"
            @field-blur="() => ctx.handleFieldBlur(field.key)"
            @upload:success="(res: any, file: any, key: string) => ctx.handleUploadSuccess(res, file, key)"
            @upload:remove="(file: any, key: string) => ctx.handleUploadRemove(file, key)"
            @search:users="ctx.handleUserSearch"
          />
          <div
            v-if="ctx.scriptFieldErrors[field.key]"
            class="el-form-item__error script-field-error"
          >
            {{ ctx.scriptFieldErrors[field.key] }}
          </div>
        </el-form-item>
      </div>
    </template>
  </template>
</template>

<style scoped lang="scss">
.form-layout-card--stacked {
  margin-bottom: 16px;
}

.form-col-field {
  width: 100%;
}

.form-row-in-column {
  width: 100%;
}

.form-col-subtable {
  width: 100%;
  margin-bottom: 16px;
}

.form-display-only-wrap {
  width: 100%;
  margin-bottom: 8px;
}

.form-renderer-nested-tabs {
  width: 100%;
  margin-bottom: 8px;
}

.form-renderer-collapse {
  width: 100%;
  margin-bottom: 18px;
}
</style>
