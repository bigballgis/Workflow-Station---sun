<template>
  <div class="lookup-editor">
    <el-tooltip
      :content="summaryLabel"
      placement="top"
      :show-after="400"
    >
      <el-popover
        :width="420"
        trigger="click"
        placement="bottom-end"
        popper-class="lookup-editor-popover"
      >
        <template #reference>
          <el-button
            size="small"
            circle
            class="lookup-config-btn"
            :type="isConfigured ? 'primary' : 'warning'"
            plain
          >
            <el-icon><Search /></el-icon>
          </el-button>
        </template>
        <div class="lookup-popover-body">
          <div class="lookup-popover-title">
            {{ t('form.lookupSettings') }}
          </div>
          <el-form
            label-position="top"
            size="small"
          >
            <!-- Referenced table -->
            <el-form-item :label="t('form.lookupRefTable')">
              <el-select
                :teleported="false"
                v-model="cfg.refTableId"
                clearable
                filterable
                style="width: 100%;"
                :placeholder="t('form.lookupRefTable')"
                @change="onRefTableChange"
              >
                <el-option
                  v-for="tb in refTables"
                  :key="tb.id"
                  :label="tb.displayName || tb.tableName"
                  :value="tb.id"
                />
              </el-select>
            </el-form-item>

            <!-- Search fields (first one is the stored PK) -->
            <el-form-item :label="t('form.lookupSearchFields')">
              <el-select
                :teleported="false"
                v-model="cfg.searchFields"
                multiple
                filterable
                collapse-tags
                collapse-tags-tooltip
                style="width: 100%;"
                :disabled="!cfg.refTableId"
                :placeholder="t('form.lookupSearchFields')"
                @change="emitChange"
              >
                <el-option
                  v-for="f in refFieldOptions"
                  :key="f.fieldName"
                  :label="f.displayName || f.fieldName"
                  :value="f.fieldName"
                />
              </el-select>
            </el-form-item>

            <!-- Display fields (dropdown columns) -->
            <el-form-item :label="t('form.lookupDisplayFields')">
              <el-select
                :teleported="false"
                v-model="cfg.displayFields"
                multiple
                filterable
                collapse-tags
                collapse-tags-tooltip
                style="width: 100%;"
                :disabled="!cfg.refTableId"
                :placeholder="t('form.lookupDisplayFields')"
                @change="emitChange"
              >
                <el-option
                  v-for="f in refFieldOptions"
                  :key="f.fieldName"
                  :label="f.displayName || f.fieldName"
                  :value="f.fieldName"
                />
              </el-select>
            </el-form-item>

            <!-- Selected display field (tag label) -->
            <el-form-item :label="t('form.lookupSelectedDisplayField')">
              <el-select
                :teleported="false"
                v-model="cfg.selectedDisplayField"
                clearable
                filterable
                style="width: 100%;"
                :disabled="!cfg.refTableId"
                :placeholder="t('form.lookupSelectedDisplayField')"
                @change="emitChange"
              >
                <el-option
                  v-for="f in refFieldOptions"
                  :key="f.fieldName"
                  :label="f.displayName || f.fieldName"
                  :value="f.fieldName"
                />
              </el-select>
            </el-form-item>

            <!-- Multiple / backfill toggles -->
            <div class="lookup-toggle-row">
              <span>{{ t('form.lookupMultiple') }}</span>
              <el-switch
                v-model="cfg.multiple"
                @change="emitChange"
              />
            </div>
            <div class="lookup-toggle-row">
              <span>{{ t('form.lookupShowBackfill') }}</span>
              <el-switch
                v-model="cfg.showBackfillView"
                @change="emitChange"
              />
            </div>

            <!-- Fixed filters -->
            <el-form-item :label="t('form.lookupFixedFilters')">
              <div class="lookup-filter-list">
                <div
                  v-for="(fc, i) in cfg.filterConditions"
                  :key="i"
                  class="lookup-filter-row"
                >
                  <el-select
                    :teleported="false"
                    v-model="fc.fieldName"
                    filterable
                    size="small"
                    style="flex: 1.2;"
                    :placeholder="t('form.lookupFilterField')"
                    @change="emitChange"
                  >
                    <el-option
                      v-for="f in refFieldOptions"
                      :key="f.fieldName"
                      :label="f.displayName || f.fieldName"
                      :value="f.fieldName"
                    />
                  </el-select>
                  <el-select
                    :teleported="false"
                    v-model="fc.matchType"
                    size="small"
                    style="flex: 1;"
                    @change="emitChange"
                  >
                    <el-option
                      v-for="m in matchTypes"
                      :key="m"
                      :label="t(`form.lookupMatch_${m}`)"
                      :value="m"
                    />
                  </el-select>
                  <el-input
                    v-model="fc.value"
                    size="small"
                    style="flex: 1.2;"
                    :placeholder="t('form.lookupFilterValue')"
                    @input="emitChange"
                  />
                  <el-button
                    link
                    type="danger"
                    size="small"
                    @click="removeFilter(i)"
                  >
                    <el-icon><Delete /></el-icon>
                  </el-button>
                </div>
                <el-button
                  link
                  type="primary"
                  size="small"
                  :disabled="!cfg.refTableId"
                  @click="addFilter"
                >
                  <el-icon><Plus /></el-icon>{{ t('form.lookupAddFilter') }}
                </el-button>
              </div>
            </el-form-item>

            <!-- Derived from (auto-fill / cascade) -->
            <el-divider content-position="left">
              {{ t('form.lookupDerived') }}
            </el-divider>
            <el-form-item :label="t('form.lookupParentField')">
              <el-select
                :teleported="false"
                v-model="derivedParentField"
                clearable
                style="width: 100%;"
                :placeholder="t('form.lookupParentFieldPlaceholder')"
                @change="onParentFieldChange"
              >
                <el-option
                  v-for="pf in parentFieldOptions"
                  :key="pf.fieldName"
                  :label="pf.displayName || pf.fieldName"
                  :value="pf.fieldName"
                />
              </el-select>
            </el-form-item>

            <template v-if="derivedParentField">
              <el-form-item :label="t('form.lookupDerivedMode')">
                <el-radio-group
                  v-model="derivedMode"
                  size="small"
                  @change="emitChange"
                >
                  <el-radio-button value="autofill">
                    {{ t('form.lookupDerivedAutofill') }}
                  </el-radio-button>
                  <el-radio-button value="filter">
                    {{ t('form.lookupDerivedFilter') }}
                  </el-radio-button>
                </el-radio-group>
              </el-form-item>

              <el-form-item :label="t('form.lookupJoins')">
                <p class="lookup-joins-hint">
                  {{ t('form.lookupJoinsHint', {
                    parentLabel: parentJoinSourceLabel,
                    thisLabel: thisJoinSourceLabel,
                  }) }}
                </p>
                <div class="lookup-filter-list">
                  <div
                    v-if="derivedJoins.length"
                    class="lookup-filter-row lookup-join-headers"
                  >
                    <span class="lookup-join-header" style="flex: 1.2;">
                      <span class="lookup-join-header-title">{{ t('form.lookupJoinFromTitle') }}</span>
                      <span class="lookup-join-header-source">{{ parentLookupFieldLabel }} . {{ parentRefTableLabel }}</span>
                    </span>
                    <span class="lookup-join-header lookup-join-header--match" style="flex: 1;">
                      <span class="lookup-join-header-title">{{ t('form.lookupJoinMatch') }}</span>
                    </span>
                    <span class="lookup-join-header" style="flex: 1.2;">
                      <span class="lookup-join-header-title">{{ t('form.lookupJoinToTitle') }}</span>
                      <span class="lookup-join-header-source">{{ thisLookupFieldLabel }} . {{ thisRefTableLabel }}</span>
                    </span>
                    <span class="lookup-join-header-spacer" />
                  </div>
                  <div
                    v-for="(jn, i) in derivedJoins"
                    :key="i"
                    class="lookup-filter-row"
                  >
                    <el-select
                      :teleported="false"
                      v-model="jn.fromColumn"
                      filterable
                      size="small"
                      style="flex: 1.2;"
                      :placeholder="t('form.lookupJoinFrom', {
                        field: parentLookupFieldLabel,
                        table: parentRefTableLabel,
                      })"
                      @change="emitChange"
                    >
                      <el-option
                        v-for="f in parentRefFieldOptions"
                        :key="f.fieldName"
                        :label="f.displayName || f.fieldName"
                        :value="f.fieldName"
                      />
                    </el-select>
                    <el-select
                      :teleported="false"
                      v-model="jn.matchType"
                      size="small"
                      style="flex: 1;"
                      @change="emitChange"
                    >
                      <el-option
                        v-for="m in matchTypes"
                        :key="m"
                        :label="t(`form.lookupMatch_${m}`)"
                        :value="m"
                      />
                    </el-select>
                    <el-select
                      :teleported="false"
                      v-model="jn.toColumn"
                      filterable
                      size="small"
                      style="flex: 1.2;"
                      :placeholder="t('form.lookupJoinTo', {
                        field: thisLookupFieldLabel,
                        table: thisRefTableLabel,
                      })"
                      @change="emitChange"
                    >
                      <el-option
                        v-for="f in refFieldOptions"
                        :key="f.fieldName"
                        :label="f.displayName || f.fieldName"
                        :value="f.fieldName"
                      />
                    </el-select>
                    <el-button
                      link
                      type="danger"
                      size="small"
                      @click="removeJoin(i)"
                    >
                      <el-icon><Delete /></el-icon>
                    </el-button>
                  </div>
                  <el-button
                    link
                    type="primary"
                    size="small"
                    @click="addJoin"
                  >
                    <el-icon><Plus /></el-icon>{{ t('form.lookupAddJoin') }}
                  </el-button>
                </div>
              </el-form-item>
            </template>
          </el-form>
        </div>
      </el-popover>
    </el-tooltip>
  </div>
</template>

<script setup lang="ts">
import { Search, Delete, Plus } from '@element-plus/icons-vue'
import type { LookupConfig, RelationTableResponse } from '@/api/relationTable'
import {
  useFieldLookupEditor,
  type FieldLookupEditorRowLike,
} from '@/composables/modules/useFieldLookupEditor'

const props = defineProps<{
  modelValue?: LookupConfig
  /** DEPLOYED tables available as reference targets (with fieldDefinitions). */
  refTables: RelationTableResponse[]
  /** All field rows of the table being edited (to pick parent lookup / auto-fill targets). */
  allFields: FieldLookupEditorRowLike[]
  /** The field name of the row this editor belongs to (excluded from parent options). */
  currentFieldName?: string
  disabled?: boolean
}>()

const emit = defineEmits<{
  'update:modelValue': [value: LookupConfig]
}>()

const {
  t,
  matchTypes,
  cfg,
  derivedParentField,
  derivedMode,
  derivedJoins,
  refFieldOptions,
  parentFieldOptions,
  parentRefFieldOptions,
  parentLookupFieldLabel,
  thisLookupFieldLabel,
  parentRefTableLabel,
  thisRefTableLabel,
  parentJoinSourceLabel,
  thisJoinSourceLabel,
  isConfigured,
  summaryLabel,
  onRefTableChange,
  onParentFieldChange,
  addFilter,
  removeFilter,
  addJoin,
  removeJoin,
  emitChange,
} = useFieldLookupEditor(props, emit)
</script>

<style scoped>
.lookup-editor {
  display: flex;
  align-items: center;
  justify-content: center;
}
.lookup-config-btn {
  width: 24px;
  height: 24px;
  padding: 0;
}
.lookup-popover-title {
  font-size: 13px;
  font-weight: 600;
  margin-bottom: 8px;
}
.lookup-toggle-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
  font-size: 13px;
}
.lookup-filter-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
  width: 100%;
}
.lookup-filter-row {
  display: flex;
  align-items: center;
  gap: 6px;
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
