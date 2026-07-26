<script setup lang="ts">
import { lookupStore } from './lookupStore'
import {
  isBooleanDataType,
  isDateDataType,
  isNumericDataType,
  isTimestampDataType,
  serializeLookupFilterValue,
} from '@/utils/lookupFilterConditions'
import { useLookupBindingSelect } from '@/composables/formDesigner/useLookupBindingSelect'

const props = defineProps<{
  modelValue?: string
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', val: string): void
}>()

const {
  t,
  selectedBindingId,
  searchFields,
  displayFields,
  selectedDisplayField,
  filterConditions,
  showBackfillView,
  allowMultiple,
  derivedParentField,
  derivedMode,
  derivedJoins,
  derivedMatchTypes,
  fieldsLoading,
  relationBindings,
  availableFields,
  parentFieldOptions,
  parentRefFieldOptions,
  parentLookupFieldLabel,
  parentRefTableLabel,
  thisLookupFieldLabel,
  thisRefTableLabel,
  parentJoinSourceLabel,
  thisJoinSourceLabel,
  bindingOptionLabel,
  goToViewDesign,
  emitUpdate,
  getFieldLabel,
  handleBindingChange,
  handleSearchFieldsChange,
  handleDisplayFieldsChange,
  handleSelectedDisplayFieldChange,
  getFieldInfo,
  getMatchOptionsForField,
  addFilterCondition,
  handleFilterFieldChange,
  parseFilterNumberValue,
  getNumericPrecision,
  removeFilterCondition,
  handleFilterConditionChange,
  handleShowBackfillViewChange,
  handleAllowMultipleChange,
  onParentFieldChange,
  addDerivedJoin,
  removeDerivedJoin,
  handleDerivedJoinChange,
} = useLookupBindingSelect(props, emit)
</script>

<template>
  <div class="lookup-binding-select">
    <div class="lookup-field-group">
      <label class="lookup-label">Relation Table</label>
      <el-select
        :model-value="selectedBindingId"
        clearable
        placeholder="Select relation table"
        style="width: 100%"
        @change="handleBindingChange"
      >
        <el-option
          v-for="b in relationBindings"
          :key="b.bindingId"
          :value="b.bindingId"
          :label="bindingOptionLabel(b)"
        />
        <template
          v-if="relationBindings.length === 0"
          #empty
        >
          <span class="el-select-dropdown__empty">No relation tables bound</span>
        </template>
      </el-select>
      <a
        v-if="selectedBindingId && lookupStore.switchToBinding"
        class="binding-nav-link"
        href="#"
        @click.prevent="goToViewDesign"
      >{{ t('form.lookupGoToViewDesign') }}</a>
    </div>

    <template v-if="selectedBindingId">
      <div class="lookup-field-group">
        <label class="lookup-label">Search Fields</label>
        <el-select
          :model-value="searchFields"
          multiple
          clearable
          collapse-tags
          collapse-tags-tooltip
          placeholder="Select search fields"
          style="width: 100%"
          :loading="fieldsLoading"
          @change="handleSearchFieldsChange"
        >
          <el-option
            v-for="f in availableFields"
            :key="f.fieldName"
            :value="f.fieldName"
            :label="getFieldLabel(f)"
          >
            <span>{{ getFieldLabel(f) }}</span>
          </el-option>
        </el-select>
      </div>

      <div class="lookup-field-group">
        <label class="lookup-label">Display Fields</label>
        <el-select
          :model-value="displayFields"
          multiple
          clearable
          collapse-tags
          collapse-tags-tooltip
          placeholder="Select display fields"
          style="width: 100%"
          :loading="fieldsLoading"
          @change="handleDisplayFieldsChange"
        >
          <el-option
            v-for="f in availableFields"
            :key="f.fieldName"
            :value="f.fieldName"
            :label="getFieldLabel(f)"
          >
            <span>{{ getFieldLabel(f) }}</span>
          </el-option>
        </el-select>
      </div>

      <div class="lookup-field-group">
        <label class="lookup-label">Selected Display Field</label>
        <el-select
          :model-value="selectedDisplayField"
          clearable
          placeholder="Field shown after selecting a row"
          style="width: 100%"
          :loading="fieldsLoading"
          @change="handleSelectedDisplayFieldChange"
        >
          <el-option
            v-for="f in availableFields"
            :key="f.fieldName"
            :value="f.fieldName"
            :label="getFieldLabel(f)"
          >
            <span>{{ getFieldLabel(f) }}</span>
          </el-option>
        </el-select>
      </div>

      <div class="lookup-field-group">
        <div class="lookup-label-row">
          <label class="lookup-label">Fixed Filters</label>
          <el-button
            link
            type="primary"
            size="small"
            @click="addFilterCondition"
          >
            Add
          </el-button>
        </div>
        <div
          v-if="filterConditions.length === 0"
          class="lookup-empty-hint"
        >
          Optional: pre-filter rows by field value (exact or fuzzy) before lookup search.
        </div>
        <div
          v-for="(condition, index) in filterConditions"
          :key="index"
          class="lookup-filter-row"
        >
          <el-select
            v-model="condition.fieldName"
            filterable
            clearable
            placeholder="Field"
            :loading="fieldsLoading"
            @change="handleFilterFieldChange(condition)"
          >
            <el-option
              v-for="f in availableFields"
              :key="f.fieldName"
              :value="f.fieldName"
              :label="getFieldLabel(f)"
            />
          </el-select>
          <el-select
            v-model="condition.matchType"
            placeholder="Match"
            :disabled="!condition.fieldName"
            @change="handleFilterConditionChange"
          >
            <el-option
              v-for="option in getMatchOptionsForField(condition.fieldName)"
              :key="option.value"
              :value="option.value"
              :label="option.label"
            />
          </el-select>
          <el-select
            v-if="condition.fieldName && isBooleanDataType(getFieldInfo(condition.fieldName)?.dataType)"
            v-model="condition.value"
            clearable
            placeholder="Value"
            @change="handleFilterConditionChange"
          >
            <el-option
              label="True"
              value="true"
            />
            <el-option
              label="False"
              value="false"
            />
          </el-select>
          <el-input-number
            v-else-if="condition.fieldName && isNumericDataType(getFieldInfo(condition.fieldName)?.dataType)"
            :model-value="parseFilterNumberValue(condition.value)"
            :precision="getNumericPrecision(getFieldInfo(condition.fieldName))"
            controls-position="right"
            style="width: 100%"
            @update:model-value="(val: number | undefined) => { condition.value = serializeLookupFilterValue(val); handleFilterConditionChange() }"
          />
          <el-date-picker
            v-else-if="condition.fieldName && isDateDataType(getFieldInfo(condition.fieldName)?.dataType)"
            v-model="condition.value"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="Value"
            style="width: 100%"
            @change="handleFilterConditionChange"
          />
          <el-date-picker
            v-else-if="condition.fieldName && isTimestampDataType(getFieldInfo(condition.fieldName)?.dataType)"
            v-model="condition.value"
            type="datetime"
            value-format="YYYY-MM-DD HH:mm:ss"
            placeholder="Value"
            style="width: 100%"
            @change="handleFilterConditionChange"
          />
          <el-input
            v-else
            v-model="condition.value"
            placeholder="Value"
            clearable
            :disabled="!condition.fieldName"
            @input="handleFilterConditionChange"
          />
          <el-button
            link
            type="danger"
            size="small"
            @click="removeFilterCondition(index)"
          >
            Remove
          </el-button>
        </div>
      </div>

      <div class="lookup-field-group lookup-derived-section">
        <label class="lookup-label">{{ t('form.lookupDerived') }}</label>
        <el-select
          v-model="derivedParentField"
          clearable
          filterable
          :placeholder="t('form.lookupParentFieldPlaceholder')"
          style="width: 100%"
          @change="onParentFieldChange"
          @visible-change="(open: boolean) => { if (open) lookupStore.refreshSiblingLookups?.() }"
        >
          <el-option
            v-for="pf in parentFieldOptions"
            :key="pf.field"
            :value="pf.field"
            :label="pf.title || pf.field"
          />
        </el-select>

        <template v-if="derivedParentField">
          <div class="lookup-derived-mode">
            <span class="lookup-label">{{ t('form.lookupDerivedMode') }}</span>
            <el-radio-group
              v-model="derivedMode"
              size="small"
              @change="emitUpdate"
            >
              <el-radio-button value="autofill">
                {{ t('form.lookupDerivedAutofill') }}
              </el-radio-button>
              <el-radio-button value="filter">
                {{ t('form.lookupDerivedFilter') }}
              </el-radio-button>
            </el-radio-group>
          </div>

          <div class="lookup-label-row">
            <label class="lookup-label">{{ t('form.lookupJoins') }}</label>
            <el-button
              link
              type="primary"
              size="small"
              @click="addDerivedJoin"
            >
              Add
            </el-button>
          </div>
          <p class="lookup-joins-hint">
            {{ t('form.lookupJoinsHint', { parentLabel: parentJoinSourceLabel, thisLabel: thisJoinSourceLabel }) }}
          </p>
          <div
            v-if="derivedJoins.length"
            class="lookup-filter-row lookup-join-headers"
          >
            <span class="lookup-join-header">
              <span class="lookup-join-header-title">{{ t('form.lookupJoinFromTitle') }}</span>
              <span class="lookup-join-header-source">{{ parentLookupFieldLabel }} . {{ parentRefTableLabel }}</span>
            </span>
            <span class="lookup-join-header lookup-join-header--match">
              <span class="lookup-join-header-title">{{ t('form.lookupJoinMatch') }}</span>
            </span>
            <span class="lookup-join-header">
              <span class="lookup-join-header-title">{{ t('form.lookupJoinToTitle') }}</span>
              <span class="lookup-join-header-source">{{ thisLookupFieldLabel }} . {{ thisRefTableLabel }}</span>
            </span>
            <span class="lookup-join-header-spacer" />
          </div>
          <div
            v-for="(jn, index) in derivedJoins"
            :key="index"
            class="lookup-filter-row"
          >
            <el-select
              v-model="jn.fromColumn"
              filterable
              clearable
              placeholder="Field"
              :loading="fieldsLoading"
              @change="handleDerivedJoinChange"
            >
              <el-option
                v-for="f in parentRefFieldOptions"
                :key="f.fieldName"
                :value="f.fieldName"
                :label="getFieldLabel(f)"
              />
            </el-select>
            <el-select
              v-model="jn.matchType"
              @change="handleDerivedJoinChange"
            >
              <el-option
                v-for="m in derivedMatchTypes"
                :key="m"
                :label="t(`form.lookupMatch_${m}`)"
                :value="m"
              />
            </el-select>
            <el-select
              v-model="jn.toColumn"
              filterable
              clearable
              placeholder="Field"
              :loading="fieldsLoading"
              @change="handleDerivedJoinChange"
            >
              <el-option
                v-for="f in availableFields"
                :key="f.fieldName"
                :value="f.fieldName"
                :label="getFieldLabel(f)"
              />
            </el-select>
            <el-button
              link
              type="danger"
              size="small"
              @click="removeDerivedJoin(index)"
            >
              Remove
            </el-button>
          </div>
        </template>
      </div>

      <div class="lookup-field-group lookup-toggle-row">
        <label class="lookup-label">{{ t('form.lookupMultiple') }}</label>
        <el-switch
          :model-value="allowMultiple"
          @change="handleAllowMultipleChange"
        />
      </div>

      <div class="lookup-field-group lookup-toggle-row">
        <label class="lookup-label">{{ t('form.lookupShowBackfill') }}</label>
        <el-switch
          :model-value="showBackfillView"
          @change="handleShowBackfillViewChange"
        />
      </div>
    </template>
  </div>
</template>

<style scoped>
.lookup-binding-select {
  width: 100%;
}
.lookup-field-group {
  margin-bottom: 8px;
}
.lookup-toggle-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.lookup-toggle-row .lookup-label {
  margin-bottom: 0;
}
.lookup-label {
  display: block;
  font-size: 12px;
  color: #606266;
  margin-bottom: 4px;
}
.lookup-label-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 4px;
}
.lookup-label-row .lookup-label {
  margin-bottom: 0;
}
.lookup-filter-row {
  display: grid;
  grid-template-columns: minmax(0, 1.2fr) minmax(0, 0.9fr) minmax(0, 1fr) auto;
  gap: 6px;
  align-items: center;
  margin-bottom: 6px;
}
.lookup-empty-hint {
  font-size: 12px;
  color: #909399;
  line-height: 1.4;
}
.binding-nav-link {
  display: inline-block;
  margin-top: 4px;
  font-size: 12px;
  color: var(--el-color-primary);
  text-decoration: none;
}
.binding-nav-link:hover {
  text-decoration: underline;
}
.lookup-derived-section {
  margin-top: 4px;
  padding-top: 4px;
  border-top: 1px solid var(--el-border-color-lighter);
}
.lookup-derived-mode {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin: 8px 0;
}
.lookup-joins-hint {
  margin: 0 0 8px;
  font-size: 12px;
  line-height: 1.45;
  color: var(--el-text-color-secondary);
}
.lookup-join-headers {
  align-items: flex-end;
  margin-bottom: 2px;
}
.lookup-join-header {
  display: flex;
  flex-direction: column;
  gap: 2px;
  font-size: 11px;
  line-height: 1.3;
  color: var(--el-text-color-regular);
  word-break: break-word;
}
.lookup-join-header-title {
  font-weight: 600;
}
.lookup-join-header-source {
  font-weight: 500;
  color: var(--el-text-color-secondary);
}
.lookup-join-header--match {
  justify-content: flex-end;
}
.lookup-join-header-spacer {
  width: 24px;
  flex-shrink: 0;
}
</style>
