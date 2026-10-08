<template>
  <div class="call-activity-properties">
    <el-collapse v-model="activeGroups">
      <!-- Basic info -->
      <el-collapse-item
        :title="t('properties.basic')"
        name="basic"
      >
        <el-form
          label-position="top"
          size="small"
        >
          <el-form-item :label="t('properties.taskId')">
            <el-input
              :model-value="basicProps.id"
              disabled
            />
          </el-form-item>
          <el-form-item :label="t('properties.callActivityName')">
            <el-input
              v-model="activityName"
              :placeholder="t('properties.callActivityNamePlaceholder')"
              @change="updateName"
            />
          </el-form-item>
        </el-form>
      </el-collapse-item>

      <!-- Which Function Unit to call -->
      <el-collapse-item
        :title="t('properties.callTargetConfig')"
        name="callTarget"
      >
        <el-form
          label-position="top"
          size="small"
        >
          <el-form-item :label="t('properties.calledFunctionUnit')">
            <el-select
              v-model="calledFunctionUnitCode"
              filterable
              clearable
              :loading="loadingUnits"
              :placeholder="t('properties.calledFunctionUnitPlaceholder')"
              class="full-width"
              @change="handleCalledUnitChange"
            >
              <el-option
                v-for="unit in callableUnits"
                :key="unit.code"
                :label="unit.name"
                :value="unit.code!"
              />
            </el-select>
            <div class="form-tip">
              {{ t('properties.calledFunctionUnitTip') }}
            </div>
          </el-form-item>

          <el-alert
            v-if="!loadingUnits && callableUnits.length === 0"
            :title="t('properties.noCallableFunctionUnits')"
            type="info"
            :closable="false"
            show-icon
          />

          <!-- Which published version of the callee this call is bound to.
               Pinning matters because the callee belongs to someone else: without
               it, their next publish silently changes what this process does. -->
          <el-form-item
            v-if="calledFunctionUnitCode"
            :label="t('properties.pinnedVersion')"
          >
            <el-select
              v-model="pinnedVersion"
              clearable
              :loading="loadingVersions"
              :placeholder="t('properties.followsLatestVersion')"
              class="full-width"
              @change="handlePinnedVersionChange"
            >
              <el-option
                v-for="version in availableVersions"
                :key="version"
                :label="version"
                :value="version"
              />
            </el-select>
            <div class="form-tip">
              {{ pinnedVersion
                ? t('properties.pinnedVersionTip')
                : t('properties.followsLatestVersionTip') }}
            </div>
            <!-- The pin is behind the callee's current version: offer the move,
                 but leave it a choice — staying on a tested version is legitimate. -->
            <div
              v-if="newerVersionAvailable"
              class="newer-version-hint"
            >
              <span>{{ t('callRelation.newerAvailable', { version: calledUnit?.currentVersion }) }}</span>
              <el-button
                link
                type="warning"
                size="small"
                @click="repinToCurrent"
              >
                {{ t('callRelation.updatePin', { version: calledUnit?.currentVersion }) }}
              </el-button>
            </div>
          </el-form-item>

          <!-- Jumps to the exact version this call runs, read-only. Opening the
               callee's live draft instead would show something the caller does not
               actually invoke. -->
          <el-form-item v-if="calledUnit">
            <el-button
              type="primary"
              class="full-width"
              @click="openCalledUnit"
            >
              {{ t('properties.openCalledUnit', { name: calledUnit.name }) }} ↗
            </el-button>
          </el-form-item>

          <!-- Read-only view of the called unit's data, rendered with its own form -->
          <el-form-item
            v-if="calledFunctionUnitCode"
            :label="t('properties.childDataForm')"
          >
            <el-select
              v-model="childFormName"
              filterable
              clearable
              :loading="loadingForms"
              :placeholder="t('properties.childDataFormPlaceholder')"
              class="full-width"
              @change="handleChildFormChange"
            >
              <el-option
                v-for="form in childForms"
                :key="form.id"
                :label="form.formName"
                :value="form.formName"
              />
            </el-select>
            <div class="form-tip">
              {{ t('properties.childDataFormTip') }}
            </div>
          </el-form-item>

          <!-- A call always waits for the called unit(s) to finish; what is chosen here is
               whether it runs once, or once per row of a sub-table (then picked under
               Data passing → Rows from). -->
          <el-form-item
            v-if="calledFunctionUnitCode"
            :label="t('properties.callExecutionMode')"
          >
            <el-radio-group
              :model-value="perRow ? 'perRow' : 'once'"
              class="execution-mode-group"
              @change="(value: string | number | boolean | undefined) => handleExecutionModeChange(value === 'perRow')"
            >
              <el-radio value="once">
                {{ t('properties.executionModeOnce') }}
              </el-radio>
              <el-radio value="perRow">
                {{ t('properties.executionModePerRow') }}
              </el-radio>
            </el-radio-group>
          </el-form-item>
        </el-form>
      </el-collapse-item>

      <!-- What data goes into the called unit, and what comes back. Stored as platform
           properties; the engine compiles them into Flowable's in/out parameters on deploy.
           Each side is picked table first, then field, so it is always visible which table a
           value comes from or goes to. -->
      <el-collapse-item
        v-if="calledFunctionUnitCode"
        :title="t('properties.dataPassing')"
        name="dataPassing"
      >
        <el-form
          label-position="top"
          size="small"
        >
          <el-form-item
            v-if="isMultiInstance"
            required
          >
            <template #label>
              <span class="label-with-tip">
                {{ t('properties.callRowsTable') }}
                <el-tooltip
                  :content="t('properties.callRowsTableTip')"
                  placement="top"
                >
                  <el-icon class="tip-icon"><InfoFilled /></el-icon>
                </el-tooltip>
              </span>
            </template>
            <el-select
              v-model="rowsTable"
              clearable
              :placeholder="t('properties.callRowsTablePlaceholder')"
              class="full-width"
              @change="handleRowsTableChange"
            >
              <el-option
                v-for="table in ownSubTables"
                :key="table.id"
                :label="table.tableDisplayName || table.tableName"
                :value="table.tableName"
              />
            </el-select>
          </el-form-item>

          <el-form-item>
            <template #label>
              <span class="label-with-tip">
                {{ t('properties.inputMapping') }}
                <el-tooltip
                  :content="t('properties.inputMappingTip')"
                  placement="top"
                >
                  <el-icon class="tip-icon"><InfoFilled /></el-icon>
                </el-tooltip>
              </span>
            </template>
            <div
              v-for="(entry, index) in inputMapping"
              :key="`inputMapping-${index}`"
              class="mapping-card"
            >
              <span class="mapping-tag">{{ t('properties.mappingFrom') }}</span>
                <el-cascader
                  v-model="entry.from"
                  class="mapping-picker"
                  :options="inputSourceOptions"
                  :props="leafValueProps"
                  filterable
                  :placeholder="t('properties.mappingFromRequest')"
                  @change="persistInputMapping"
                >
                  <template #default="{ data }">
                    <span>{{ data.label }}</span>
                    <span
                      v-if="data.hint"
                      class="option-hint"
                    >{{ data.hint }}</span>
                  </template>
                </el-cascader>
              <el-button
                class="mapping-remove"
                link
                type="danger"
                :aria-label="t('common.delete')"
                @click="removeMapping(inputMapping, index, persistInputMapping)"
              >
                ✕
              </el-button>
              <span class="mapping-tag">{{ t('properties.mappingTo') }}</span>
                <el-cascader
                  v-model="entry.to"
                  class="mapping-picker"
                  :options="calledFieldOptions"
                  :props="leafValueProps"
                  filterable
                  :placeholder="t('properties.mappingToCalled')"
                  @change="persistInputMapping"
                >
                  <template #default="{ data }">
                    <span>{{ data.label }}</span>
                    <span
                      v-if="data.hint"
                      class="option-hint"
                    >{{ data.hint }}</span>
                  </template>
                </el-cascader>
            </div>
            <el-button
              link
              type="primary"
              @click="inputMapping.push({ from: '', to: '' })"
            >
              + {{ t('properties.addMapping') }}
            </el-button>
          </el-form-item>

          <el-form-item>
            <template #label>
              <span class="label-with-tip">
                {{ t('properties.outputMapping') }}
                <el-tooltip
                  :content="isMultiInstance ? t('properties.outputMappingPerRow') : t('properties.outputMappingTip')"
                  placement="top"
                >
                  <el-icon class="tip-icon"><InfoFilled /></el-icon>
                </el-tooltip>
              </span>
            </template>
            <div
              v-for="(entry, index) in outputMapping"
              :key="`outputMapping-${index}`"
              class="mapping-card"
            >
              <span class="mapping-tag">{{ t('properties.mappingFrom') }}</span>
                <el-cascader
                  v-model="entry.from"
                  class="mapping-picker"
                  :options="calledFieldOptions"
                  :props="leafValueProps"
                  filterable
                  :placeholder="t('properties.mappingFromCalled')"
                  @change="persistOutputMapping"
                >
                  <template #default="{ data }">
                    <span>{{ data.label }}</span>
                    <span
                      v-if="data.hint"
                      class="option-hint"
                    >{{ data.hint }}</span>
                  </template>
                </el-cascader>
              <el-button
                class="mapping-remove"
                link
                type="danger"
                :aria-label="t('common.delete')"
                @click="removeMapping(outputMapping, index, persistOutputMapping)"
              >
                ✕
              </el-button>
              <span class="mapping-tag">{{ t('properties.mappingTo') }}</span>
                <el-cascader
                  v-model="entry.to"
                  class="mapping-picker"
                  :options="outputTargetOptions"
                  :props="leafValueProps"
                  filterable
                  :placeholder="t('properties.mappingToRequest')"
                  @change="persistOutputMapping"
                >
                  <template #default="{ data }">
                    <span>{{ data.label }}</span>
                    <span
                      v-if="data.hint"
                      class="option-hint"
                    >{{ data.hint }}</span>
                  </template>
                </el-cascader>
            </div>
            <el-button
              link
              type="primary"
              @click="outputMapping.push({ from: '', to: '' })"
            >
              + {{ t('properties.addMapping') }}
            </el-button>
          </el-form-item>
        </el-form>
      </el-collapse-item>
    </el-collapse>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage, type CascaderOption } from 'element-plus'
import { InfoFilled } from '@element-plus/icons-vue'
import type { BpmnElement, BpmnModeler } from '@/types/bpmn'
import {
  getBasicProperties,
  setBasicProperties,
  getExtensionProperties,
  setExtensionProperty,
  getCalledElement,
  setCalledElement,
  setRunsPerItem,
} from '@/utils/bpmnExtensions'
import {
  functionUnitApi,
  type FunctionUnitResponse,
  type FormDefinition,
  type TableDefinition,
} from '@/api/functionUnit'

const { t } = useI18n()
const router = useRouter()

const props = defineProps<{
  modeler: BpmnModeler
  element: BpmnElement
  functionUnitId?: number
}>()

const activeGroups = ref(['basic', 'callTarget', 'dataPassing'])

const basicProps = computed(() => getBasicProperties(props.element))
const activityName = ref('')
const calledFunctionUnitCode = ref('')
const childFormName = ref('')

const allUnits = ref<FunctionUnitResponse[]>([])
const loadingUnits = ref(false)
const childForms = ref<FormDefinition[]>([])
const loadingForms = ref(false)
const pinnedVersion = ref('')
const availableVersions = ref<string[]>([])
const loadingVersions = ref(false)

/**
 * Only units that declare they may be called are offered, and never this unit itself —
 * a self-call would start sub-processes endlessly. Longer cycles are caught at deploy time.
 */
const callableUnits = computed(() =>
  allUnits.value.filter(
    (unit) =>
      !!unit.code &&
      unit.id !== props.functionUnitId &&
      (unit.startupMode === 'CALLABLE' || unit.startupMode === 'BOTH'),
  ),
)

const calledUnit = computed(() =>
  allUnits.value.find((unit) => unit.code === calledFunctionUnitCode.value),
)

/** True when this step repeats once per row of a collection. */
/** True when this step repeats once per row; kept as state so the panel follows a switch at once. */
const perRow = ref(Boolean(props.element?.businessObject?.loopCharacteristics))
const isMultiInstance = computed(() => perRow.value)

/**
 * Switches between one call and one call per row. Settings that only make sense in the mode
 * being left are dropped, as deploy would refuse them: the rows table and row fields when going
 * back to a single call; copy-back into request fields when going per row (each row's call
 * copies into its own row instead).
 */
function handleExecutionModeChange(toPerRow: boolean) {
  if (toPerRow === perRow.value) return
  setRunsPerItem(props.modeler, props.element, toPerRow)
  perRow.value = toPerRow
  if (toPerRow) {
    dropMappingEntries(outputMapping, (entry) => !!entry.to && !entry.to.startsWith('row.'), persistOutputMapping)
    return
  }
  rowsTable.value = ''
  setExtensionProperty(props.modeler, props.element, 'callRowsTable', '')
  dropMappingEntries(inputMapping, (entry) => entry.from.startsWith('row.'), persistInputMapping)
  dropMappingEntries(outputMapping, (entry) => entry.to.startsWith('row.'), persistOutputMapping)
}

// ---- Data passing -----------------------------------------------------------
// Each mapping row is one value handed across: `from` a field (or `row.<field>` for the row a
// per-row call runs for) `to` a field on the other side. Stored as JSON platform properties.

interface MappingEntry {
  from: string
  to: string
}

const ownTables = ref<TableDefinition[]>([])
const calledTables = ref<TableDefinition[]>([])
const rowsTable = ref('')
const inputMapping = ref<MappingEntry[]>([])
const outputMapping = ref<MappingEntry[]>([])

/** Sub-tables of this unit: the only things a per-row call can run over. */
const ownSubTables = computed(() => ownTables.value.filter((table) => table.tableType === 'SUB'))

/**
 * A table and its fields as one cascader branch; leaf values are what the mapping stores.
 * Labels are display names so the selected "Table / Field" stays short enough to read; the
 * technical name is shown beside each option in the dropdown (`hint`).
 */
function tableOption(table: TableDefinition | undefined, prefix = ''): CascaderOption[] {
  if (!table) return []
  return [{
    value: `__table:${prefix}${table.tableName}`,
    label: table.tableDisplayName || table.tableName,
    hint: table.tableDisplayName ? table.tableName : undefined,
    children: (table.fieldDefinitions ?? []).map((field) => ({
      value: `${prefix}${field.fieldName}`,
      label: field.displayName || field.fieldName,
      hint: field.displayName ? field.fieldName : undefined,
    })),
  }]
}

/** The stored value is the field alone (`row.<f>` for a row field), so only leaves are emitted. */
const leafValueProps = { emitPath: false }

const ownMainTable = computed(() => ownTables.value.find((table) => table.tableType === 'MAIN'))
const calledMainTable = computed(() => calledTables.value.find((table) => table.tableType === 'MAIN'))
const rowsTableDefinition = computed(() => ownSubTables.value.find((table) => table.tableName === rowsTable.value))

/** This request's side: its main table, plus — for a per-row call — the table the rows come from. */
const requestFieldOptions = computed(() => tableOption(ownMainTable.value))
const inputSourceOptions = computed(() => [
  ...(isMultiInstance.value ? tableOption(rowsTableDefinition.value, 'row.') : []),
  ...requestFieldOptions.value,
])
/**
 * The called unit's side: its main table only. Its values arrive as plain fields of the new
 * instance; a sub-table of the called unit is a list of rows, which one value cannot fill.
 */
const calledFieldOptions = computed(() => tableOption(calledMainTable.value))
/**
 * Where a result is copied to: a field of this request for a call that runs once; for a per-row
 * call, a field of the row that call ran for (each row gets its own call's result).
 */
const outputTargetOptions = computed(() => (isMultiInstance.value
  ? tableOption(rowsTableDefinition.value, 'row.')
  : requestFieldOptions.value))

function parseMapping(raw: unknown): MappingEntry[] {
  let list: unknown = raw
  if (typeof raw === 'string') {
    try {
      list = raw.trim() ? JSON.parse(raw) : []
    } catch {
      list = []
    }
  }
  return (Array.isArray(list) ? list : [])
    .map((entry: Partial<MappingEntry>) => ({ from: String(entry?.from ?? ''), to: String(entry?.to ?? '') }))
}

/** Only complete rows are saved; a half-filled row would be refused at deploy anyway. */
function serializeMapping(entries: MappingEntry[]) {
  const complete = entries.filter((entry) => entry.from && entry.to)
  return complete.length ? JSON.stringify(complete) : ''
}

function persistInputMapping() {
  setExtensionProperty(props.modeler, props.element, 'callInputMapping', serializeMapping(inputMapping.value))
}

function persistOutputMapping() {
  setExtensionProperty(props.modeler, props.element, 'callOutputMapping', serializeMapping(outputMapping.value))
}

function removeMapping(entries: MappingEntry[], index: number, persist: () => void) {
  entries.splice(index, 1)
  persist()
}

function handleRowsTableChange() {
  setExtensionProperty(props.modeler, props.element, 'callRowsTable', rowsTable.value || '')
  // Only row fields the newly chosen table does not have are dropped; clearing and re-picking
  // the same table (or one with the same columns) keeps the mapping. Nothing is dropped while
  // no table is chosen — that is a transient state, not a decision about the mapping.
  if (!rowsTableDefinition.value) return
  const columns = new Set((rowsTableDefinition.value.fieldDefinitions ?? []).map((f) => f.fieldName))
  dropMappingEntries(inputMapping, (entry) => entry.from.startsWith('row.') && !columns.has(entry.from.slice(4)),
    persistInputMapping)
  dropMappingEntries(outputMapping, (entry) => entry.to.startsWith('row.') && !columns.has(entry.to.slice(4)),
    persistOutputMapping)
}

/** Removes entries matching `stale` and saves, only if any were removed. */
function dropMappingEntries(
  list: { value: MappingEntry[] },
  stale: (entry: MappingEntry) => boolean,
  persist: () => void,
) {
  const kept = list.value.filter((entry) => !stale(entry))
  if (kept.length !== list.value.length) {
    list.value = kept
    persist()
  }
}

/**
 * After the called unit changes and its tables have loaded: drops only mapping entries naming
 * fields the new unit does not have. Re-picking the same unit, or a unit with the same fields,
 * keeps everything.
 */
function pruneMappingsForCalledUnit() {
  if (!calledMainTable.value) return
  const fields = new Set((calledMainTable.value.fieldDefinitions ?? []).map((f) => f.fieldName))
  dropMappingEntries(inputMapping, (entry) => !!entry.to && !fields.has(entry.to), persistInputMapping)
  dropMappingEntries(outputMapping, (entry) => !!entry.from && !fields.has(entry.from), persistOutputMapping)
}

async function loadOwnTables() {
  if (!props.functionUnitId) return
  try {
    const res = await functionUnitApi.getTables(props.functionUnitId)
    ownTables.value = res.data ?? []
  } catch (error) {
    console.warn('Could not load this unit\'s tables for call data passing', error)
    ownTables.value = []
  }
}

async function loadCalledTables() {
  const unit = calledUnit.value
  if (!unit) {
    calledTables.value = []
    return
  }
  try {
    const res = await functionUnitApi.getTables(unit.id)
    calledTables.value = res.data ?? []
  } catch (error) {
    console.warn('Could not load the called unit\'s tables for call data passing', error)
    calledTables.value = []
  }
}

function syncFromElement() {
  perRow.value = Boolean(props.element?.businessObject?.loopCharacteristics)
  activityName.value = basicProps.value.name
  calledFunctionUnitCode.value = getCalledElement(props.element)
  const ext = getExtensionProperties(props.element)
  childFormName.value = ext.childFormName || ''
  pinnedVersion.value = ext.calledVersion || ''
  rowsTable.value = ext.callRowsTable || ''
  inputMapping.value = parseMapping(ext.callInputMapping)
  outputMapping.value = parseMapping(ext.callOutputMapping)
}

/**
 * The published versions of the callee that this call may be pinned to.
 *
 * Only versions that actually exist are offered: a pin to a version that was never
 * published fails at deploy time, which is far too late to find out.
 */
async function loadAvailableVersions() {
  const unit = calledUnit.value
  if (!unit) {
    availableVersions.value = []
    return
  }
  loadingVersions.value = true
  try {
    const res = await functionUnitApi.getVersions(unit.id)
    const versions = (res as { data?: Array<{ versionNumber?: string }> })?.data ?? []
    const numbers = versions
      .map((v) => v.versionNumber)
      .filter((v): v is string => Boolean(v))
    // The unit's current version may not have a snapshot row yet, but is still a
    // legitimate thing to pin to.
    if (unit.currentVersion && !numbers.includes(unit.currentVersion)) {
      numbers.unshift(unit.currentVersion)
    }
    availableVersions.value = numbers
  } catch (error) {
    console.warn('Could not load versions of the called function unit', error)
    availableVersions.value = []
  } finally {
    loadingVersions.value = false
  }
}

function handlePinnedVersionChange() {
  setExtensionProperty(props.modeler, props.element, 'calledVersion', pinnedVersion.value)
}

/**
 * True when this call is pinned to an older version than the callee now has.
 *
 * Uses the same numeric ordering as the server, so the panel and the call
 * relations dialog never disagree about whether a pin is behind.
 */
const newerVersionAvailable = computed(() => {
  const current = calledUnit.value?.currentVersion
  return Boolean(pinnedVersion.value && current && compareVersions(current, pinnedVersion.value) > 0)
})

function repinToCurrent() {
  const current = calledUnit.value?.currentVersion
  if (!current) return
  pinnedVersion.value = current
  handlePinnedVersionChange()
}

/**
 * Dotted versions compared part by part as numbers — a string comparison would
 * rank 1.10.0 below 1.9.0. Mirrors FunctionUnitCallRelationComponent.compareVersions.
 */
function compareVersions(a: string, b: string): number {
  const left = a.trim().split('.')
  const right = b.trim().split('.')
  for (let i = 0; i < Math.max(left.length, right.length); i++) {
    const l = parseInt(left[i] ?? '0', 10) || 0
    const r = parseInt(right[i] ?? '0', 10) || 0
    if (l !== r) return l - r
  }
  return 0
}

/**
 * Opens the called unit.
 *
 * Read-only by intent when a version is pinned: the caller runs that version, so
 * dropping the designer into the callee's live draft would show a process this
 * call does not actually invoke.
 */
function openCalledUnit() {
  const unit = calledUnit.value
  if (!unit) return
  router.push({
    path: `/function-units/${unit.id}`,
    // A version in the query makes the edit page show that published version
    // read-only; without one it opens the live design.
    query: pinnedVersion.value ? { version: pinnedVersion.value } : undefined,
  })
}

async function loadFunctionUnits() {
  loadingUnits.value = true
  try {
    const res = await functionUnitApi.list({ page: 0, size: 200 })
    allUnits.value = res.data?.content ?? []
  } catch (error) {
    console.error('Failed to load function units for call activity', error)
    ElMessage.error(t('properties.loadFunctionUnitsFailed'))
  } finally {
    loadingUnits.value = false
  }
}

async function loadChildForms() {
  const unit = calledUnit.value
  if (!unit) {
    childForms.value = []
    return
  }
  loadingForms.value = true
  try {
    const res = await functionUnitApi.getForms(unit.id)
    childForms.value = res.data ?? []
  } catch (error) {
    console.error('Failed to load forms of called function unit', error)
    childForms.value = []
  } finally {
    loadingForms.value = false
  }
}

function updateName() {
  setBasicProperties(props.modeler, props.element, { name: activityName.value })
}

function handleCalledUnitChange() {
  setCalledElement(props.modeler, props.element, calledFunctionUnitCode.value)
  // The previously chosen form and pinned version both belonged to the previous
  // unit — drop them rather than leaving references into a unit no longer called.
  childFormName.value = ''
  setExtensionProperty(props.modeler, props.element, 'childFormName', '')
  pinnedVersion.value = ''
  setExtensionProperty(props.modeler, props.element, 'calledVersion', '')
  loadChildForms()
  loadAvailableVersions()
  // Mappings name the previous unit's fields: keep the ones the new unit also has.
  void loadCalledTables().then(pruneMappingsForCalledUnit)
}

function handleChildFormChange() {
  // Stored by name, not id: form ids are remapped on import, so an id would dangle as soon
  // as the called unit is imported into another environment.
  setExtensionProperty(props.modeler, props.element, 'childFormName', childFormName.value)
}

watch(
  () => props.element,
  () => {
    syncFromElement()
    loadChildForms()
    loadAvailableVersions()
    loadCalledTables()
  },
)

onMounted(async () => {
  syncFromElement()
  void loadOwnTables()
  await loadFunctionUnits()
  await loadChildForms()
  await loadAvailableVersions()
  await loadCalledTables()
})
</script>

<style scoped lang="scss">
.call-activity-properties {
  .full-width {
    width: 100%;
  }

  .form-tip {
    font-size: 12px;
    color: var(--el-text-color-secondary);
    line-height: 1.4;
    margin-top: 4px;
  }

  .execution-mode-group {
    display: flex;
    flex-direction: column;
    align-items: flex-start;
    gap: 2px;
  }

  .execution-mode {
    font-size: 13px;
    color: var(--el-text-color-regular);
  }

  .label-with-tip {
    display: inline-flex;
    align-items: center;
    gap: 4px;
  }

  .tip-icon {
    color: var(--el-text-color-secondary);
    cursor: help;
  }

  /* One bordered card per mapping: "From" and "To" on their own lines, labels and pickers
     aligned, the remove button in the corner. */
  .mapping-card {
    display: grid;
    grid-template-columns: 34px minmax(0, 1fr) 20px;
    align-items: center;
    gap: 6px 6px;
    width: 100%;
    margin-bottom: 8px;
    padding: 8px 6px 8px 10px;
    border: 1px solid var(--el-border-color-lighter);
    border-radius: 6px;
    background: var(--el-fill-color-extra-light);
  }

  .mapping-tag {
    font-size: 12px;
    line-height: 1;
    color: var(--el-text-color-secondary);
  }

  .mapping-picker {
    width: 100%;
  }

  .mapping-remove {
    justify-self: end;
  }

  .newer-version-hint {
    display: flex;
    align-items: center;
    flex-wrap: wrap;
    gap: 6px;
    margin-top: 6px;
    font-size: 12px;
    color: var(--el-color-warning-dark-2);
  }
}

/* The cascader dropdown is rendered outside the panel, so this cannot sit under
   .call-activity-properties. */
.option-hint {
  margin-left: 8px;
  font-size: 12px;
  color: var(--el-text-color-placeholder);
}
</style>
