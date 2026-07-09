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
                <div class="lookup-filter-list">
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
                      :placeholder="t('form.lookupJoinFrom')"
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
                      v-model="jn.toColumn"
                      filterable
                      size="small"
                      style="flex: 1.2;"
                      :placeholder="t('form.lookupJoinTo')"
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
import { computed, reactive, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { Search, Delete, Plus } from '@element-plus/icons-vue'
import type {
  FieldDefinitionResponse,
  LookupConfig,
  LookupMatchType,
  RelationTableResponse,
} from '@/api/relationTable'

interface FieldRowLike {
  fieldName: string
  displayName?: string
  dataType: string
  lookupConfig?: LookupConfig
}

const props = defineProps<{
  modelValue?: LookupConfig
  /** DEPLOYED tables available as reference targets (with fieldDefinitions). */
  refTables: RelationTableResponse[]
  /** All field rows of the table being edited (to pick parent lookup / auto-fill targets). */
  allFields: FieldRowLike[]
  /** The field name of the row this editor belongs to (excluded from parent options). */
  currentFieldName?: string
  disabled?: boolean
}>()

const emit = defineEmits<{
  'update:modelValue': [value: LookupConfig]
}>()

const { t } = useI18n()

const matchTypes: LookupMatchType[] = ['eq', 'contains', 'startsWith', 'endsWith']

function emptyConfig(): LookupConfig {
  return {
    refTableId: undefined,
    searchFields: [],
    displayFields: [],
    selectedDisplayField: undefined,
    filterConditions: [],
    showBackfillView: true,
    multiple: false,
    derivedFrom: undefined,
  }
}

const cfg = reactive<LookupConfig>({ ...emptyConfig(), ...(props.modelValue || {}) })
// Ensure arrays exist even if partial config arrived.
cfg.searchFields = cfg.searchFields || []
cfg.displayFields = cfg.displayFields || []
cfg.filterConditions = cfg.filterConditions || []
if (cfg.showBackfillView === undefined) cfg.showBackfillView = true
if (cfg.multiple === undefined) cfg.multiple = false

const derivedParentField = ref<string>(props.modelValue?.derivedFrom?.parentField || '')
const derivedMode = ref<'autofill' | 'filter'>(props.modelValue?.derivedFrom?.derivedMode || 'autofill')
const derivedJoins = reactive(
  (props.modelValue?.derivedFrom?.joins || []).map(j => ({ ...j })),
)

watch(
  () => props.modelValue,
  (v) => {
    Object.assign(cfg, emptyConfig(), v || {})
    cfg.searchFields = cfg.searchFields || []
    cfg.displayFields = cfg.displayFields || []
    cfg.filterConditions = cfg.filterConditions || []
    if (cfg.showBackfillView === undefined) cfg.showBackfillView = true
    if (cfg.multiple === undefined) cfg.multiple = false
    derivedParentField.value = v?.derivedFrom?.parentField || ''
    derivedMode.value = v?.derivedFrom?.derivedMode || 'autofill'
    derivedJoins.splice(0, derivedJoins.length, ...(v?.derivedFrom?.joins || []).map(j => ({ ...j })))
  },
)

const refTable = computed<RelationTableResponse | undefined>(() =>
  props.refTables.find(tb => tb.id === cfg.refTableId),
)

const refFieldOptions = computed<FieldDefinitionResponse[]>(() =>
  (refTable.value?.fieldDefinitions ?? []).filter(f => f.fieldName?.trim()),
)

// Sibling LOOKUP fields (this table) that can drive a derived lookup, minus self.
const parentFieldOptions = computed<FieldRowLike[]>(() =>
  props.allFields.filter(
    f => f.dataType === 'LOOKUP'
      && f.fieldName?.trim()
      && f.fieldName !== props.currentFieldName,
  ),
)

// The parent lookup's referenced table fields (fromColumn options).
const parentRefFieldOptions = computed<FieldDefinitionResponse[]>(() => {
  const parent = props.allFields.find(f => f.fieldName === derivedParentField.value)
  const parentRefTableId = parent?.lookupConfig?.refTableId
  const parentRefTable = props.refTables.find(tb => tb.id === parentRefTableId)
  return (parentRefTable?.fieldDefinitions ?? []).filter(f => f.fieldName?.trim())
})

const isConfigured = computed(() => !!cfg.refTableId && (cfg.searchFields?.length ?? 0) > 0)

const summaryLabel = computed(() => {
  if (!cfg.refTableId) return t('form.lookupConfigure')
  const name = refTable.value?.displayName || refTable.value?.tableName || '?'
  return derivedParentField.value ? `${name} · ⇐ ${derivedParentField.value}` : name
})

function onRefTableChange() {
  // Reset ref-dependent selections when the table changes.
  cfg.searchFields = []
  cfg.displayFields = []
  cfg.selectedDisplayField = undefined
  cfg.filterConditions = []
  derivedJoins.splice(0, derivedJoins.length)
  emitChange()
}

function onParentFieldChange() {
  if (!derivedParentField.value) {
    derivedJoins.splice(0, derivedJoins.length)
  }
  emitChange()
}

function addFilter() {
  cfg.filterConditions = cfg.filterConditions || []
  cfg.filterConditions.push({ fieldName: '', value: '', matchType: 'eq' })
  emitChange()
}
function removeFilter(i: number) {
  cfg.filterConditions?.splice(i, 1)
  emitChange()
}

function addJoin() {
  derivedJoins.push({ fromColumn: '', toColumn: '', matchType: 'eq' })
  emitChange()
}
function removeJoin(i: number) {
  derivedJoins.splice(i, 1)
  emitChange()
}

function emitChange() {
  const out: LookupConfig = {
    refTableId: cfg.refTableId,
    refTableName: refTable.value?.tableName,
    searchFields: [...(cfg.searchFields || [])],
    displayFields: [...(cfg.displayFields || [])],
    selectedDisplayField: cfg.selectedDisplayField || undefined,
    filterConditions: (cfg.filterConditions || []).map(f => ({ ...f })),
    showBackfillView: cfg.showBackfillView !== false,
    multiple: !!cfg.multiple,
    derivedFrom: derivedParentField.value
      ? {
          parentField: derivedParentField.value,
          derivedMode: derivedMode.value,
          joins: derivedJoins.map(j => ({ ...j })),
        }
      : undefined,
  }
  emit('update:modelValue', out)
}
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
</style>
