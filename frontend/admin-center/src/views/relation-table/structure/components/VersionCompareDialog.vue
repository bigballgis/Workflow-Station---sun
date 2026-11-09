<template>
  <el-dialog
    :model-value="modelValue"
    :title="`${t('form.titleVersionCompare')} - ${tableName || ''}`"
    width="950px"
    @update:model-value="emit('update:modelValue', $event)"
    @opened="init"
  >
    <div style="display: flex; gap: 16px; margin-bottom: 16px;">
      <div style="flex: 1;">
        <span style="margin-right: 8px;">{{ t('form.labelFrom') }}:</span>
        <el-select
          v-model="leftVersion"
          :placeholder="t('form.placeholderSelectVersion')"
          style="width: 200px;"
          @change="computeDiff"
        >
          <el-option
            v-for="v in versions"
            :key="v.id"
            :label="'v' + v.versionNumber"
            :value="v.id"
          />
        </el-select>
      </div>
      <div style="flex: 1;">
        <span style="margin-right: 8px;">{{ t('form.labelTo') }}:</span>
        <el-select
          v-model="rightVersion"
          :placeholder="t('form.placeholderSelectVersion')"
          style="width: 200px;"
          @change="computeDiff"
        >
          <el-option
            v-for="v in versions"
            :key="v.id"
            :label="'v' + v.versionNumber"
            :value="v.id"
          />
        </el-select>
      </div>
    </div>

    <el-table
      v-loading="loading"
      :data="diffRows"
      stripe
      :empty-text="t('relationTable.compare.empty')"
      border
    >
      <el-table-column
        :label="t('form.labelFieldName')"
        prop="fieldName"
        width="160"
      />
      <el-table-column
        :label="t('form.labelChange')"
        width="100"
        align="center"
      >
        <template #default="{ row }">
          <el-tag
            v-if="row.change === 'added'"
            type="success"
            size="small"
          >
            {{ t('relationTable.compare.added') }}
          </el-tag>
          <el-tag
            v-else-if="row.change === 'removed'"
            type="danger"
            size="small"
          >
            {{ t('relationTable.compare.removed') }}
          </el-tag>
          <el-tag
            v-else-if="row.change === 'modified'"
            type="warning"
            size="small"
          >
            {{ t('relationTable.compare.modified') }}
          </el-tag>
          <el-tag
            v-else
            type="info"
            size="small"
          >
            {{ t('relationTable.compare.unchanged') }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column
        :label="t('form.labelFrom')"
        min-width="300"
      >
        <template #default="{ row }">
          <span
            v-if="row.left"
            :style="row.change === 'removed' ? 'color: #F56C6C;' : ''"
          >
            {{ row.left.dataType }}{{ row.left.length ? '(' + row.left.length + ')' : '' }}
            {{ row.left.nullable ? ', ' + t('relationTable.compare.nullable') : ', NOT NULL' }}
            {{ row.left.isPrimaryKey ? ', PK' : '' }}
            {{ row.left.defaultValue ? ', ' + t('relationTable.compare.defaultValue', { value: row.left.defaultValue }) : '' }}
            {{ row.left.displayName ? ' — ' + row.left.displayName : '' }}
          </span>
          <span
            v-else
            style="color: #ccc;"
          >—</span>
        </template>
      </el-table-column>
      <el-table-column
        :label="t('form.labelTo')"
        min-width="300"
      >
        <template #default="{ row }">
          <span
            v-if="row.right"
            :style="row.change === 'added' ? 'color: #67C23A;' : ''"
          >
            {{ row.right.dataType }}{{ row.right.length ? '(' + row.right.length + ')' : '' }}
            {{ row.right.nullable ? ', ' + t('relationTable.compare.nullable') : ', NOT NULL' }}
            {{ row.right.isPrimaryKey ? ', PK' : '' }}
            {{ row.right.defaultValue ? ', ' + t('relationTable.compare.defaultValue', { value: row.right.defaultValue }) : '' }}
            {{ row.right.displayName ? ' — ' + row.right.displayName : '' }}
          </span>
          <span
            v-else
            style="color: #ccc;"
          >—</span>
        </template>
      </el-table-column>
    </el-table>

    <template #footer>
      <el-button @click="emit('update:modelValue', false)">
        {{ t('common.close') }}
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  relationTableStructureApi,
  type RelationTableVersionResponse
} from '@/api/relationTable'

interface SnapshotField {
  fieldName: string
  dataType: string
  length?: number
  precision?: number
  scale?: number
  nullable?: boolean
  isPrimaryKey?: boolean
  defaultValue?: string
  displayName?: string
}

interface DiffRow {
  fieldName: string
  change: 'added' | 'removed' | 'modified' | 'unchanged'
  left: SnapshotField | null
  right: SnapshotField | null
}

const props = defineProps<{
  modelValue: boolean
  tableId?: number
  tableName?: string
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
}>()

const { t } = useI18n()
const loading = ref(false)
const versions = ref<RelationTableVersionResponse[]>([])
const leftVersion = ref<number | null>(null)
const rightVersion = ref<number | null>(null)
const diffRows = ref<DiffRow[]>([])

const init = async () => {
  if (!props.tableId) return
  diffRows.value = []
  leftVersion.value = null
  rightVersion.value = null
  loading.value = true
  try {
    versions.value = await relationTableStructureApi.getVersionHistory(props.tableId)
    if (versions.value.length >= 2) {
      leftVersion.value = versions.value[1].id
      rightVersion.value = versions.value[0].id
      computeDiff()
    }
  } catch (e) {
    console.error('Failed to load versions:', e)
  } finally {
    loading.value = false
  }
}

const parseSnapshot = (data: string): SnapshotField[] => {
  try { return JSON.parse(data) } catch { return [] }
}

const fieldsEqual = (a: SnapshotField, b: SnapshotField): boolean => {
  return a.dataType === b.dataType
    && a.length === b.length
    && a.precision === b.precision
    && a.scale === b.scale
    && a.nullable === b.nullable
    && a.isPrimaryKey === b.isPrimaryKey
    && (a.defaultValue || '') === (b.defaultValue || '')
    && (a.displayName || '') === (b.displayName || '')
}

const computeDiff = () => {
  if (!leftVersion.value || !rightVersion.value) { diffRows.value = []; return }
  const lv = versions.value.find(v => v.id === leftVersion.value)
  const rv = versions.value.find(v => v.id === rightVersion.value)
  if (!lv || !rv) { diffRows.value = []; return }

  const leftFields = parseSnapshot(lv.snapshotData)
  const rightFields = parseSnapshot(rv.snapshotData)
  const leftMap = new Map(leftFields.map(f => [f.fieldName, f]))
  const rightMap = new Map(rightFields.map(f => [f.fieldName, f]))
  const allNames = new Set([...leftMap.keys(), ...rightMap.keys()])

  const rows: DiffRow[] = []
  for (const name of allNames) {
    const l = leftMap.get(name) || null
    const r = rightMap.get(name) || null
    let change: DiffRow['change'] = 'unchanged'
    if (!l) change = 'added'
    else if (!r) change = 'removed'
    else if (!fieldsEqual(l, r)) change = 'modified'
    rows.push({ fieldName: name, change, left: l, right: r })
  }
  // Sort: modified/added/removed first, then unchanged
  const order = { added: 0, removed: 1, modified: 2, unchanged: 3 }
  rows.sort((a, b) => order[a.change] - order[b.change])
  diffRows.value = rows
}
</script>
