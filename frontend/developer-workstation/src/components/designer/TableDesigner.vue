<template>
  <div class="table-designer">
    <div class="designer-toolbar">
      <el-button
        type="primary"
        @click="openCreateDialog"
      >
        {{ t('table.title') }}
      </el-button>
      <el-button
        :loading="loading"
        @click="loadTables"
      >
        <el-icon><Refresh /></el-icon> {{ t('common.refresh') }}
      </el-button>
      <el-button
        :disabled="store.tables.length < 2"
        @click="showRelationDialog = true"
      >
        {{ t('table.relations') }}
      </el-button>
      <el-button @click="handleValidate">
        {{ t('table.validateTables') }}
      </el-button>
      <el-button
        :loading="importing"
        @click="handleImportClick"
      >
        {{ t('table.importTemplate') }}
      </el-button>
      <input
        ref="fileInputRef"
        type="file"
        accept=".json"
        style="display: none"
        @change="handleImportFile"
      />
    </div>
    
    <div
      v-if="!selectedTable"
      class="table-list table-scroll-wrap"
    >
      <el-table
        v-loading="loading"
        :data="store.tables"
        stripe
        @row-click="handleSelectTable"
      >
        <el-table-column
          prop="tableDisplayName"
          :label="t('table.tableDisplayName')"
          min-width="180"
          show-overflow-tooltip
        />
        <el-table-column
          prop="tableName"
          :label="t('table.tableName')"
          min-width="150"
          show-overflow-tooltip
        />
        <el-table-column
          prop="tableType"
          :label="t('table.tableType')"
          min-width="120"
        >
          <template #default="{ row }">
            <el-tag :type="row.tableType === 'MAIN' ? 'primary' : 'info'">
              {{ tableTypeLabel(row.tableType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column
          prop="description"
          :label="t('table.description')"
          min-width="200"
          show-overflow-tooltip
        />
        <el-table-column
          :label="t('table.fieldCount')"
          min-width="100"
        >
          <template #default="{ row }">
            {{ row.fieldDefinitions?.length || 0 }}
          </template>
        </el-table-column>
        <el-table-column
          :label="t('table.relations')"
          min-width="100"
        >
          <template #default="{ row }">
            <el-tag
              v-if="getTableRelations(row.id).length"
              type="success"
              size="small"
            >
              {{ getTableRelations(row.id).length }}
            </el-tag>
            <span
              v-else
              class="text-muted"
            >-</span>
          </template>
        </el-table-column>
        <el-table-column
          :label="t('common.actions')"
          min-width="240"
          fixed="right"
        >
          <template #default="{ row }">
            <div class="table-row-actions">
              <el-button
                link
                type="primary"
                @click.stop="handleSelectTable(row)"
              >
                {{ t('common.edit') }}
              </el-button>
              <el-button
                link
                type="success"
                @click.stop="handleExportTable(row)"
              >
                {{ t('table.exportTemplate') }}
              </el-button>
              <el-button
                link
                type="danger"
                @click.stop="handleDeleteTable(row)"
              >
                {{ t('common.delete') }}
              </el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <div
      v-else
      class="table-editor"
    >
      <div class="editor-header">
        <div class="editor-header-left">
          <el-button
            text
            class="back-btn"
            @click="handleBackToList"
          >
            <el-icon><ArrowLeft /></el-icon> {{ t('table.backToList') }}
          </el-button>
          <span class="table-name">{{ selectedTable.tableDisplayName || selectedTable.tableName }}</span>
          <el-tag
            size="small"
            type="info"
            effect="plain"
          >
            {{ tableTypeLabel(selectedTable.tableType) }}
          </el-tag>
        </div>
        <div class="editor-header-actions">
          <el-button
            size="small"
            @click="handleGenerateDDL"
          >
            {{ t('table.ddlPreview') }}
          </el-button>
          <el-button
            type="primary"
            size="small"
            @click="handleSaveTable"
          >
            {{ t('table.save') }}
          </el-button>
        </div>
      </div>

      <el-card
        shadow="never"
        class="table-meta-card"
      >
        <div class="table-meta-grid">
          <div class="meta-field">
            <label class="meta-label">{{ t('table.tableDisplayName') }}</label>
            <el-input
              v-model="selectedTable.tableDisplayName"
              @input="onTableDisplayNameInput"
            />
          </div>
          <div class="meta-field">
            <label class="meta-label">{{ t('table.tableName') }}</label>
            <el-input
              v-model="selectedTable.tableName"
              @input="onTableNameManualInput"
            />
          </div>
          <div class="meta-field meta-field--type">
            <label class="meta-label">{{ t('table.tableType') }}</label>
            <el-select
              v-model="selectedTable.tableType"
              class="table-type-select"
            >
              <el-option
                :label="t('table.mainTable')"
                value="MAIN"
              />
              <el-option
                :label="t('table.subTable')"
                value="SUB"
              />
              <el-option
                :label="t('table.actionTable')"
                value="ACTION"
              />
              <el-option
                :label="t('table.relationTable')"
                value="RELATION"
              />
            </el-select>
          </div>
          <div class="meta-field meta-field--description">
            <label class="meta-label">{{ t('table.description') }}</label>
            <el-input
              v-model="selectedTable.description"
              type="textarea"
              :rows="2"
              autosize
            />
          </div>
        </div>
      </el-card>

      <el-card
        shadow="never"
        class="table-fields-card"
      >
        <div class="fields-card-header">
          <div class="fields-header-left">
            <h4 class="fields-title">
              {{ t('table.fields') }}
            </h4>
            <el-tag
              size="small"
              round
              type="info"
              effect="plain"
            >
              {{ selectedTable.fieldDefinitions.length }}
            </el-tag>
            <el-tooltip
              :content="t('table.fieldsHint')"
              placement="top"
            >
              <el-icon class="fields-info-icon">
                <InfoFilled />
              </el-icon>
            </el-tooltip>
          </div>
          <el-button
            type="primary"
            plain
            size="small"
            @click="handleAddField"
          >
            {{ t('table.addField') }}
          </el-button>
        </div>
        <div class="table-scroll-wrap table-fields-wrap">
          <el-table
            :data="selectedTable.fieldDefinitions"
            size="small"
            stripe
            row-key="__uid"
            class="table-fields-grid"
          >
            <el-table-column
              width="36"
              align="center"
              class-name="col-order"
            >
              <template #default="{ $index }">
                <div class="field-order-btns">
                  <el-button
                    v-if="$index > 0"
                    link
                    size="small"
                    class="order-btn"
                    @click="moveFieldUp($index)"
                  >
                    <el-icon><CaretTop /></el-icon>
                  </el-button>
                  <el-button
                    v-if="$index < selectedTable.fieldDefinitions.length - 1"
                    link
                    size="small"
                    class="order-btn"
                    @click="moveFieldDown($index)"
                  >
                    <el-icon><CaretBottom /></el-icon>
                  </el-button>
                </div>
              </template>
            </el-table-column>
            <el-table-column
              prop="displayName"
              :label="t('table.fieldDisplayName')"
              min-width="128"
            >
              <template #default="{ row, $index }">
                <el-input
                  v-model="row.displayName"
                  size="small"
                  @update:model-value="onFieldDisplayNameInput(row, $index)"
                />
              </template>
            </el-table-column>
            <el-table-column
              prop="fieldName"
              :label="t('table.fieldName')"
              min-width="100"
            >
              <template #default="{ row }">
                <el-input
                  v-model="row.fieldName"
                  size="small"
                  @input="onFieldNameManualInput(row)"
                />
              </template>
            </el-table-column>
            <el-table-column
              prop="dataType"
              :label="t('table.dataType')"
              width="100"
            >
              <template #default="{ row }">
                <el-select
                  v-model="row.dataType"
                  size="small"
                >
                  <el-option
                    label="VARCHAR"
                    value="VARCHAR"
                  />
                  <el-option
                    label="INTEGER"
                    value="INTEGER"
                  />
                  <el-option
                    label="BIGINT"
                    value="BIGINT"
                  />
                  <el-option
                    label="DECIMAL"
                    value="DECIMAL"
                  />
                  <el-option
                    label="BOOLEAN"
                    value="BOOLEAN"
                  />
                  <el-option
                    label="DATE"
                    value="DATE"
                  />
                  <el-option
                    label="TIMESTAMP"
                    value="TIMESTAMP"
                  />
                  <el-option
                    label="TEXT"
                    value="TEXT"
                  />
                  <el-option
                    label="FILE"
                    value="FILE"
                  />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column
              prop="length"
              :label="t('table.length')"
              width="88"
            >
              <template #default="{ row }">
                <el-input-number
                  v-model="row.length"
                  size="small"
                  :min="0"
                  controls-position="right"
                  class="compact-number length-number"
                />
              </template>
            </el-table-column>
            <el-table-column
              :label="t('table.nullable')"
              width="72"
              align="center"
            >
              <template #default="{ row }">
                <el-checkbox v-model="row.nullable" />
              </template>
            </el-table-column>
            <el-table-column
              :label="t('table.primaryKey')"
              min-width="120"
              align="center"
              class-name="col-pk"
            >
              <template #default="{ row }">
                <div class="constraint-cell">
                  <el-checkbox
                    v-model="row.isPrimaryKey"
                    @change="(val: boolean) => onPrimaryKeyChange(row, val)"
                  />
                  <PkGenerationEditor
                    v-if="row.isPrimaryKey"
                    v-model="row.pkGeneration"
                    :enabled="true"
                    variant="popover"
                  />
                </div>
              </template>
            </el-table-column>
            <el-table-column
              :label="t('table.foreignKey')"
              min-width="100"
              align="center"
            >
              <template #default="{ row }">
                <FieldForeignKeyEditor
                  :is-foreign-key="row.isForeignKey"
                  :ref-table-id="row.refTableId"
                  :ref-primary-key-fields="row.refPrimaryKeyFields"
                  :ref-tables="otherTables"
                  :ref-pk-field-options="getTableFields(row.refTableId).filter(f => f.isPrimaryKey)"
                  @update:is-foreign-key="row.isForeignKey = $event"
                  @update:ref-table-id="row.refTableId = $event"
                  @update:ref-primary-key-fields="row.refPrimaryKeyFields = $event"
                />
              </template>
            </el-table-column>
            <el-table-column
              prop="defaultValue"
              :label="t('table.defaultValue')"
              min-width="88"
              show-overflow-tooltip
            >
              <template #default="{ row }">
                <el-input
                  v-model="row.defaultValue"
                  size="small"
                  :placeholder="t('common.inputPlaceholder')"
                />
              </template>
            </el-table-column>
            <el-table-column
              v-if="hasDecimalFields"
              prop="precision"
              :label="t('table.precision')"
              width="76"
            >
              <template #default="{ row }">
                <el-input-number
                  v-if="row.dataType === 'DECIMAL'"
                  v-model="row.precision"
                  size="small"
                  :min="1"
                  :max="38"
                  controls-position="right"
                  class="compact-number"
                />
                <span
                  v-else
                  class="text-muted"
                >—</span>
              </template>
            </el-table-column>
            <el-table-column
              v-if="hasDecimalFields"
              prop="scale"
              :label="t('table.scale')"
              width="76"
            >
              <template #default="{ row }">
                <el-input-number
                  v-if="row.dataType === 'DECIMAL'"
                  v-model="row.scale"
                  size="small"
                  :min="0"
                  :max="20"
                  controls-position="right"
                  class="compact-number"
                />
                <span
                  v-else
                  class="text-muted"
                >—</span>
              </template>
            </el-table-column>
            <el-table-column
              width="48"
              align="center"
              fixed="right"
            >
              <template #header>
                <span class="col-header-short">&nbsp;</span>
              </template>
              <template #default="{ $index }">
                <el-tooltip
                  :content="t('table.delete')"
                  placement="top"
                >
                  <el-button
                    link
                    type="danger"
                    class="delete-btn"
                    @click="handleRemoveField($index)"
                  >
                    <el-icon><Delete /></el-icon>
                  </el-button>
                </el-tooltip>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-card>
    </div>

    <!-- Create Table Dialog -->
    <el-dialog
      v-model="showCreateDialog"
      :title="t('table.title')"
      width="500px"
    >
      <el-form
        :model="createForm"
        label-width="140px"
        label-position="left"
      >
        <el-form-item
          :label="t('table.tableDisplayName')"
          required
        >
          <el-input
            v-model="createForm.tableDisplayName"
            @input="onCreateTableDisplayNameInput"
          />
        </el-form-item>
        <el-form-item
          :label="t('table.tableName')"
          required
        >
          <el-input
            v-model="createForm.tableName"
            @input="onCreateTableNameManualInput"
          />
        </el-form-item>
        <el-form-item :label="t('table.tableType')">
          <el-select v-model="createForm.tableType">
            <el-option
              :label="t('table.mainTable')"
              value="MAIN"
            />
            <el-option
              :label="t('table.subTable')"
              value="SUB"
            />
            <el-option
              :label="t('table.actionTable')"
              value="ACTION"
            />
            <el-option
              :label="t('table.relationTable')"
              value="RELATION"
            />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('table.description')">
          <el-input
            v-model="createForm.description"
            type="textarea"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreateDialog = false">
          {{ t('common.cancel') }}
        </el-button>
        <el-button
          type="primary"
          @click="handleCreateTable"
        >
          {{ t('common.confirm') }}
        </el-button>
      </template>
    </el-dialog>

    <!-- DDL Dialog -->
    <el-dialog
      v-model="showDDLDialog"
      :title="t('table.ddlPreview')"
      width="700px"
    >
      <el-select
        v-model="ddlDialect"
        style="margin-bottom: 16px;"
      >
        <el-option
          label="PostgreSQL"
          value="POSTGRESQL"
        />
        <el-option
          label="MySQL"
          value="MYSQL"
        />
        <el-option
          label="Oracle"
          value="ORACLE"
        />
      </el-select>
      <el-input
        v-model="ddlContent"
        type="textarea"
        :rows="15"
        readonly
      />
      <template #footer>
        <el-button @click="handleCopyDDL">
          {{ t('table.copy') }}
        </el-button>
        <el-button @click="showDDLDialog = false">
          {{ t('table.close') }}
        </el-button>
      </template>
    </el-dialog>

    <!-- Relation Config Dialog -->
    <el-dialog
      v-model="showRelationDialog"
      :title="t('table.relationConfig')"
      width="80%"
      top="6vh"
      class="relation-diagram-dialog"
    >
      <RelationDiagramEditor
        v-if="showRelationDialog"
        v-model="relations"
        :tables="store.tables"
      />
      <template #footer>
        <el-button @click="showRelationDialog = false">
          {{ t('common.cancel') }}
        </el-button>
        <el-button
          type="primary"
          @click="handleSaveRelations"
        >
          {{ t('table.save') }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, nextTick } from 'vue'
import { useI18n } from 'vue-i18n'
import { ArrowLeft, Refresh, InfoFilled, CaretTop, CaretBottom, Delete } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useFunctionUnitStore } from '@/stores/functionUnit'
import { functionUnitApi, type TableDefinition, type FieldDefinition, type ForeignKeyDTO } from '@/api/functionUnit'
import { suggestFieldName, suggestTableName } from '@/utils/fieldNameSlug'
import RelationDiagramEditor from '@/components/designer/RelationDiagramEditor.vue'
import PkGenerationEditor from '@/components/designer/PkGenerationEditor.vue'
import FieldForeignKeyEditor from '@/components/designer/FieldForeignKeyEditor.vue'
import { serializePkGeneration } from '@/utils/pkGenerationConfig'

const { t } = useI18n()

type FieldRow = FieldDefinition & {
  __uid?: number
  fieldNameTouched?: boolean
  autoFieldName?: string
}

interface TableRelation {
  id?: number
  sourceTableId: number | null
  sourceFieldName: string
  relationType: string
  targetTableId: number | null
  targetFieldName: string
}

const props = defineProps<{ functionUnitId: number }>()

const store = useFunctionUnitStore()
const loading = ref(false)
const selectedTable = ref<TableDefinition | null>(null)
const tableNameTouched = ref(false)
const showCreateDialog = ref(false)
const showDDLDialog = ref(false)
const showRelationDialog = ref(false)
const ddlDialect = ref('POSTGRESQL')
const ddlContent = ref('')
const createForm = reactive({ tableName: '', tableDisplayName: '', tableType: 'MAIN', description: '' })
const createTableNameTouched = ref(false)
const relations = ref<TableRelation[]>([])
const foreignKeys = ref<ForeignKeyDTO[]>([])
const importing = ref(false)
const fileInputRef = ref<HTMLInputElement | null>(null)

const NAME_REGEX = /^[a-zA-Z][a-zA-Z0-9_]*$/

const hasDecimalFields = computed(() => {
  return selectedTable.value?.fieldDefinitions?.some(f => f.dataType === 'DECIMAL') ?? false
})

const otherTables = computed(() => {
  const currentId = selectedTable.value?.id
  return store.tables.filter(t => t.id !== currentId)
})

function validateName(name: string): boolean {
  return NAME_REGEX.test(name)
}

const tableTypeLabel = (type: string) => {
  const map: Record<string, string> = { MAIN: t('table.mainTable'), SUB: t('table.subTable'), ACTION: t('table.actionTable'), RELATION: t('table.relationTable') }
  return map[type] || type
}

function getTableFields(tableId: number | null): FieldDefinition[] {
  if (!tableId) return []
  const table = store.tables.find(t => t.id === tableId)
  return table?.fieldDefinitions || []
}

function getTableRelations(tableId: number): (TableRelation | ForeignKeyDTO)[] {
  // Combine local relations and database foreign keys
  const localRelations = relations.value.filter(r => r.sourceTableId === tableId || r.targetTableId === tableId)
  const dbRelations = foreignKeys.value.filter(fk => fk.sourceTableId === tableId || fk.targetTableId === tableId)
  return [...localRelations, ...dbRelations]
}

async function loadTables() {
  loading.value = true
  try {
    const tables = await store.fetchTables(props.functionUnitId)
    console.log('[TableDesigner] Loaded tables:', tables)
    tables.forEach(table => {
      console.log(`[TableDesigner] Table ${table.tableName} has ${table.fieldDefinitions?.length || 0} fields`)
    })
    await loadRelations()
    // 如果当前选中的表还在，更新选中表的数据
    if (selectedTable.value) {
      const updatedTable = tables.find(t => t.id === selectedTable.value!.id)
      if (updatedTable) {
        selectedTable.value = { 
          ...updatedTable, 
          fieldDefinitions: [...(updatedTable.fieldDefinitions || []).map(f => normalizeFieldRow(f))] 
        }
        console.log('[TableDesigner] Updated selected table with', selectedTable.value.fieldDefinitions?.length || 0, 'fields')
      }
    }
  } finally {
    loading.value = false
  }
}

async function loadRelations() {
  // Load table relations from backend API
  try {
    const res = await functionUnitApi.getTableRelations(props.functionUnitId)
    relations.value = res?.data || []
  } catch {
    relations.value = []
  }

  // Load DB foreign keys from API
  try {
    const res = await functionUnitApi.getForeignKeys(props.functionUnitId)
    foreignKeys.value = res?.data || []
  } catch {
    foreignKeys.value = []
  }
}

function existingTableNames(excludeId?: number): string[] {
  return store.tables
    .filter(t => t.id !== excludeId)
    .map(t => t.tableName)
    .filter(Boolean)
}

function onTableDisplayNameInput() {
  if (!selectedTable.value || tableNameTouched.value) return
  selectedTable.value.tableName = suggestTableName(
    selectedTable.value.tableDisplayName || '',
    existingTableNames(selectedTable.value.id),
  )
}

function onTableNameManualInput() {
  tableNameTouched.value = true
}

async function assertTableNameAvailable(tableName: string, excludeTableId?: number): Promise<boolean> {
  const trimmed = tableName?.trim()
  if (!trimmed) return false
  try {
    const res = await functionUnitApi.checkTableNameAvailable(props.functionUnitId, trimmed, excludeTableId)
    if (!res?.data?.available) {
      ElMessage.warning(t('table.nameAlreadyExists', { name: trimmed }))
      return false
    }
    return true
  } catch {
    ElMessage.error(t('common.error'))
    return false
  }
}

function handleSelectTable(row: TableDefinition) {
  tableNameTouched.value = !!row.id
  selectedTable.value = {
    ...row,
    fieldDefinitions: [...(row.fieldDefinitions || []).map(f => normalizeFieldRow(f))],
  }
}

function existingFieldNames(excludeIndex?: number): string[] {
  if (!selectedTable.value) return []
  return selectedTable.value.fieldDefinitions
    .map((f, i) => (excludeIndex === i ? '' : f.fieldName))
    .filter(Boolean)
}

let _fieldUidCounter = 0
let _autoSyncingFieldName = false

function normalizeFieldRow(f: FieldDefinition): FieldRow {
  const row = { ...f } as FieldRow
  row.__uid = row.__uid ?? ++_fieldUidCounter
  row.autoFieldName = row.fieldName || suggestFieldName(row.displayName || '', [])
  // Only lock auto-generation when the field already has a persisted technical name.
  row.fieldNameTouched = !!(row.id && row.fieldName?.trim())
  row.isForeignKey = row.isForeignKey || false
  row.refPrimaryKeyFields = row.refPrimaryKeyFields || []
  row.fkDisplayMode = row.fkDisplayMode || 'readonly'
  row.pkGeneration = row.pkGeneration ?? row.pkGenerationJson
  if (row.isPrimaryKey && !row.pkGeneration) {
    row.pkGeneration = { strategy: 'uuid' }
  }
  return row
}

function onPrimaryKeyChange(row: FieldRow, checked: boolean) {
  if (!checked) {
    row.pkGeneration = undefined
    return
  }
  if (!row.pkGeneration) {
    row.pkGeneration = { strategy: 'uuid' }
  }
}

function onFieldDisplayNameInput(row: FieldRow, index: number) {
  if (row.fieldNameTouched) return
  const suggested = suggestFieldName(row.displayName || '', existingFieldNames(index))
  _autoSyncingFieldName = true
  row.fieldName = suggested
  row.autoFieldName = suggested
  nextTick(() => {
    _autoSyncingFieldName = false
  })
}

function onFieldNameManualInput(row: FieldRow) {
  if (_autoSyncingFieldName) return
  row.fieldNameTouched = !!row.fieldName?.trim()
}

function resetCreateForm() {
  Object.assign(createForm, { tableName: '', tableDisplayName: '', tableType: 'MAIN', description: '' })
  createTableNameTouched.value = false
}

function openCreateDialog() {
  resetCreateForm()
  showCreateDialog.value = true
}

function onCreateTableDisplayNameInput() {
  if (createTableNameTouched.value) return
  createForm.tableName = suggestTableName(createForm.tableDisplayName || '', existingTableNames())
}

function onCreateTableNameManualInput() {
  createTableNameTouched.value = true
}

function handleBackToList() {
  selectedTable.value = null
  tableNameTouched.value = false
}

async function handleCreateTable() {
  if (!createForm.tableDisplayName?.trim()) {
    ElMessage.warning(t('table.displayNameRequired'))
    return
  }
  // Validate table name
  if (!validateName(createForm.tableName)) {
    ElMessage.warning(t('table.invalidTableName'))
    return
  }
  if (!await assertTableNameAvailable(createForm.tableName)) {
    return
  }
  try {
    await store.createTable(props.functionUnitId, createForm)
    ElMessage.success(t('functionUnit.createSuccess'))
    showCreateDialog.value = false
    resetCreateForm()
    loadTables()
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || t('common.error'))
  }
}

async function handleSaveTable() {
  if (!selectedTable.value) return
  // Validate table name
  if (!validateName(selectedTable.value.tableName)) {
    ElMessage.warning(t('table.invalidTableName'))
    return
  }
  if (!await assertTableNameAvailable(selectedTable.value.tableName, selectedTable.value.id)) {
    return
  }
  // Validate field names
  const invalidField = selectedTable.value.fieldDefinitions.find(f => f.fieldName && !validateName(f.fieldName))
  if (invalidField) {
    ElMessage.warning(t('table.invalidFieldName', { name: invalidField.fieldName }))
    return
  }
  // Validate PK-Nullable constraint
  const pkFields = selectedTable.value.fieldDefinitions.filter(
    f => f.isPrimaryKey && f.fieldName && f.fieldName.trim()
  )
  if (pkFields.length === 1) {
    // 只有一个主键：该字段的Nullable必须为未勾选
    if (pkFields[0].nullable !== false) {
      ElMessage.warning(t('table.pkNotNullable'))
      return
    }
  } else if (pkFields.length >= 2) {
    // 联合主键：至少有一个主键字段的Nullable不能勾选
    const notNullableCount = pkFields.filter(f => f.nullable !== true).length
    if (notNullableCount === 0) {
      ElMessage.warning(t('table.compositePkNotNullable'))
      return
    }
  }
  try {
    // 转换数据格式：将 fieldDefinitions 转换为 fields
    // 后端期望的是 TableDefinitionRequest，包含 fields 而不是 fieldDefinitions
    const fields = (selectedTable.value.fieldDefinitions || [])
      .filter(f => f.fieldName && f.fieldName.trim()) // 过滤空字段名
      .map((f: any, index: number) => ({
        // Preserve original id so backend can diff fieldName / description (Display Name)
        // and propagate renames to Form Designer rule + fieldPermissions.
        id: f.id,
        fieldName: f.fieldName,
        dataType: f.dataType, // 确保 dataType 是有效的枚举值
        length: f.length,
        precision: f.precision,
        scale: f.scale,
        nullable: f.nullable !== undefined ? f.nullable : true,
        defaultValue: f.defaultValue,
        isPrimaryKey: f.isPrimaryKey || false,
        displayName: f.displayName,
        isForeignKey: f.isForeignKey || false,
        refTableId: f.refTableId,
        refPrimaryKeyFields: f.refPrimaryKeyFields,
        pkGeneration: serializePkGeneration(f.pkGeneration, f.isPrimaryKey),
        fkDisplayMode: f.fkDisplayMode || 'readonly',
        relationCardinality: f.relationCardinality,
        sortOrder: index
      }))
    
    const requestData = {
      tableName: selectedTable.value.tableName,
      tableDisplayName: selectedTable.value.tableDisplayName,
      tableType: selectedTable.value.tableType,
      description: selectedTable.value.description,
      fields: fields
    }
    
    console.log('[TableDesigner] Saving table with fields:', {
      tableId: selectedTable.value.id,
      tableName: requestData.tableName,
      fieldCount: fields.length,
      fields: fields,
      requestData: JSON.stringify(requestData, null, 2)
    })
    
    const result = await store.updateTable(props.functionUnitId, selectedTable.value.id, requestData)
    console.log('[TableDesigner] Save result:', result)
    console.log('[TableDesigner] Result fieldDefinitions:', result?.fieldDefinitions?.length || 0)
    console.log('[TableDesigner] Result fieldDefinitions array:', result?.fieldDefinitions)
    
    // 更新当前选中的表，使用返回的数据
    // result 已经是 TableDefinition（store.updateTable 返回 res.data，而 res 是 ApiResponse）
    if (result) {
      selectedTable.value = { 
        ...result, 
        fieldDefinitions: [...(result.fieldDefinitions || []).map(f => normalizeFieldRow(f))] 
      }
      console.log('[TableDesigner] Updated selected table after save with', selectedTable.value.fieldDefinitions?.length || 0, 'fields')
    } else {
      console.warn('[TableDesigner] Save result is null or undefined')
    }
    
    ElMessage.success(t('common.success'))
    
    // Delay loading list to ensure transaction is committed
    setTimeout(() => {
    loadTables()
    }, 500)
  } catch (e: any) {
    console.error('[TableDesigner] Save failed:', e)
    ElMessage.error(e.response?.data?.message || t('common.error'))
  }
}

async function handleDeleteTable(row: TableDefinition) {
  await ElMessageBox.confirm(t('functionUnit.deleteConfirm'), t('functionUnit.confirmTitle'), { type: 'warning' })
  try {
    await store.deleteTable(props.functionUnitId, row.id)
    ElMessage.success(t('functionUnit.deleteSuccess'))
    loadTables()
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || t('common.error'))
  }
}

// ─── Export / Import ────────────────────────────────────────────────────────

const EXPORT_FORMAT = 'workflow-station-table-template'
const EXPORT_VERSION = 1

interface TableTemplate {
  format: string
  version: number
  tableName: string
  tableDisplayName?: string
  tableType: string
  description?: string
  fieldDefinitions: Omit<FieldDefinition, 'id'>[]
}

function handleExportTable(row: TableDefinition) {
  const template: TableTemplate = {
    format: EXPORT_FORMAT,
    version: EXPORT_VERSION,
    tableName: row.tableName,
    tableDisplayName: row.tableDisplayName,
    tableType: row.tableType,
    description: row.description,
    fieldDefinitions: (row.fieldDefinitions || []).map(f => ({
      fieldName: f.fieldName,
      dataType: f.dataType,
      length: f.length,
      precision: f.precision,
      scale: f.scale,
      nullable: f.nullable,
      isPrimaryKey: f.isPrimaryKey,
      defaultValue: f.defaultValue,
      displayName: f.displayName,
    })),
  }
  const json = JSON.stringify(template, null, 2)
  const blob = new Blob([json], { type: 'application/json' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `${row.tableName}.table-template.json`
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  URL.revokeObjectURL(url)
  ElMessage.success(t('table.exportSuccess'))
}

function handleImportClick() {
  fileInputRef.value?.click()
}

async function handleImportFile(event: Event) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return

  importing.value = true
  try {
    const text = await file.text()
    const template: TableTemplate = JSON.parse(text)

    // Validate format
    if (template.format !== EXPORT_FORMAT) {
      ElMessage.warning(t('table.importInvalidFormat'))
      return
    }
    if (!template.tableName || !validateName(template.tableName)) {
      ElMessage.warning(t('table.invalidTableName'))
      return
    }
    if (!await assertTableNameAvailable(template.tableName)) {
      return
    }
    if (!template.fieldDefinitions || !Array.isArray(template.fieldDefinitions)) {
      ElMessage.warning(t('table.importNoFields'))
      return
    }

    // Build create request
    const requestData = {
      tableName: template.tableName,
      tableDisplayName: template.tableDisplayName,
      tableType: template.tableType || 'MAIN',
      description: template.description,
      fields: template.fieldDefinitions
        .filter(f => f.fieldName && f.fieldName.trim())
        .map((f, index) => ({
          fieldName: f.fieldName,
          dataType: f.dataType || 'VARCHAR',
          length: f.length,
          precision: f.precision,
          scale: f.scale,
          nullable: f.nullable !== undefined ? f.nullable : true,
          defaultValue: f.defaultValue,
          isPrimaryKey: f.isPrimaryKey || false,
          displayName: f.displayName,
          pkGeneration: serializePkGeneration(f.pkGeneration, f.isPrimaryKey),
          sortOrder: index,
        })),
    }

    await store.createTable(props.functionUnitId, requestData as any)
    ElMessage.success(t('table.importSuccess'))
    loadTables()
  } catch (e: any) {
    if (e instanceof SyntaxError) {
      ElMessage.error(t('table.importParseError'))
    } else {
      const msg = e.response?.data?.error?.message
        || e.response?.data?.message
        || e.message
        || t('common.error')
      ElMessage.error(msg)
    }
  } finally {
    importing.value = false
    // Reset file input so the same file can be re-imported
    if (input) input.value = ''
  }
}

function handleAddField() {
  if (!selectedTable.value) return
  selectedTable.value.fieldDefinitions.push({
    __uid: ++_fieldUidCounter,
    fieldName: '',
    dataType: 'VARCHAR',
    length: 255,
    nullable: true,
    isPrimaryKey: false,
    isForeignKey: false,
    refPrimaryKeyFields: [],
    fkDisplayMode: 'readonly',
    displayName: '',
    fieldNameTouched: false,
  } as FieldRow)
}

function handleRemoveField(index: number) {
  if (!selectedTable.value) return
  selectedTable.value.fieldDefinitions.splice(index, 1)
}

/**
 * Move a field up in the list (swap with previous).
 * Exported for testing via assignSortOrder.
 */
function moveFieldUp(index: number) {
  if (!selectedTable.value || index <= 0) return
  const fields = selectedTable.value.fieldDefinitions
  const temp = fields[index]
  fields[index] = fields[index - 1]
  fields[index - 1] = temp
  // Trigger reactivity
  selectedTable.value.fieldDefinitions = [...fields]
}

function moveFieldDown(index: number) {
  if (!selectedTable.value || index >= selectedTable.value.fieldDefinitions.length - 1) return
  const fields = selectedTable.value.fieldDefinitions
  const temp = fields[index]
  fields[index] = fields[index + 1]
  fields[index + 1] = temp
  selectedTable.value.fieldDefinitions = [...fields]
}

async function handleGenerateDDL() {
  if (!selectedTable.value) return
  try {
    const res = await functionUnitApi.generateDDL?.(props.functionUnitId, selectedTable.value.id, ddlDialect.value)
    ddlContent.value = res?.data || ''
    showDDLDialog.value = true
  } catch {
    ElMessage.info(t('common.loading'))
  }
}

async function handleValidate() {
  try {
    const res = await functionUnitApi.validateTables?.(props.functionUnitId)
    if (res?.data?.valid) {
      ElMessage.success(t('common.success'))
    } else {
      ElMessage.warning(`${t('common.error')}: ${res?.data?.errors?.join(', ') || t('common.error')}`)
    }
  } catch {
    ElMessage.info(t('common.loading'))
  }
}

function handleCopyDDL() {
  navigator.clipboard.writeText(ddlContent.value)
  ElMessage.success(t('common.success'))
}

async function handleSaveRelations() {
  // Validate relations
  const validRelations = relations.value.filter(r => 
    r.sourceTableId && r.sourceFieldName && r.relationType && r.targetTableId && r.targetFieldName
  )
  
  // Save to backend API
  try {
    const res = await functionUnitApi.saveTableRelations(props.functionUnitId, validRelations as any)
    relations.value = res?.data || validRelations
    ElMessage.success(t('common.success'))
    showRelationDialog.value = false
    // Refresh tables so PK/FK badges reflect the applied field metadata
    loadTables()
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || t('common.error'))
  }
}

onMounted(loadTables)
</script>

<style lang="scss" scoped>
.table-designer {
  min-height: 400px;
  width: 100%;
  min-width: 0;
}

.table-meta-card {
  margin-bottom: 12px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;

  :deep(.el-card__body) {
    padding: 16px 20px;
  }
}

.table-meta-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.15fr) minmax(0, 1fr) minmax(180px, 220px);
  gap: 14px 20px;
  align-items: start;
}

.meta-field {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
}

.meta-field--description {
  grid-column: 1 / -1;
}

.meta-label {
  font-size: 12px;
  font-weight: 500;
  color: var(--el-text-color-secondary);
  line-height: 1.2;
}

.table-type-select {
  width: 100%;
}

@media (max-width: 960px) {
  .table-meta-grid {
    grid-template-columns: 1fr 1fr;
  }

  .meta-field--type {
    grid-column: 1 / -1;
    max-width: 280px;
  }
}

@media (max-width: 640px) {
  .table-meta-grid {
    grid-template-columns: 1fr;
  }

  .meta-field--type {
    max-width: none;
  }
}

.designer-toolbar {
  display: flex;
  gap: 10px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}

.editor-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
  padding: 8px 12px;
  background: var(--el-fill-color-lighter);
  border: 1px solid var(--el-border-color-extra-light);
  border-radius: 8px;
}

.editor-header-left {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.back-btn {
  padding-left: 4px;
  padding-right: 8px;
  flex-shrink: 0;
}

.editor-header-actions {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
}

.table-name {
  font-size: 15px;
  font-weight: 600;
  color: var(--el-text-color-primary);
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.table-editor {
  min-width: 0;
}

.table-fields-wrap {
  margin-top: 0;
}

.table-fields-card {
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;

  :deep(.el-card__body) {
    padding: 16px;
  }
}

.fields-card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.fields-header-left {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.fields-info-icon {
  font-size: 14px;
  color: var(--el-text-color-secondary);
  cursor: help;
}

.fields-title {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.col-header-short {
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.02em;
}

.table-fields-grid {
  :deep(.el-table__header th) {
    background: var(--el-fill-color-light);
    font-size: 12px;
    padding: 6px 0;
  }

  :deep(.el-table__cell) {
    vertical-align: middle;
    padding: 6px 0;
  }

  :deep(.col-pk .cell),
  :deep(.col-order .cell) {
    padding-left: 4px;
    padding-right: 4px;
  }

  :deep(.el-input-number.compact-number) {
    width: 100%;
  }

  :deep(.el-input-number.length-number) {
    width: 72px;

    .el-input__wrapper {
      padding-left: 6px;
      padding-right: 24px;
    }
  }
}

.constraint-cell {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  flex-wrap: nowrap;
}

.field-order-btns {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0;
}

.order-btn {
  height: 16px;
  padding: 0;
  margin: 0;

  .el-icon {
    font-size: 12px;
  }
}

.delete-btn {
  padding: 4px;
}

.text-muted {
  color: var(--el-text-color-placeholder);
  font-size: 12px;
}

.table-row-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}
</style>
