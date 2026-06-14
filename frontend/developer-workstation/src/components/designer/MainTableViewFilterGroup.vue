<script setup lang="ts">
import { Close, Plus, ArrowDown } from '@element-plus/icons-vue'
import { useI18n } from 'vue-i18n'
import {
  FILTER_OPERATOR_KEYS,
  createEmptyFilterGroup,
  nextFilterNodeId,
  operatorNeedsValue,
  type FilterFieldOption,
  type FilterGroupEditorNode,
} from '@/utils/mainTableViewFilter'

const props = defineProps<{
  group: FilterGroupEditorNode
  depth?: number
  fieldOptions: FilterFieldOption[]
}>()

const emit = defineEmits<{
  removeSelf: []
}>()

const { t } = useI18n()

function formatOperatorLabel(op: string): string {
  const key = `mainTableView.op${op.charAt(0).toUpperCase()}${op.slice(1)}`
  const translated = t(key)
  return translated !== key ? translated : op
}

function onFieldChange(row: { fieldName: string; systemField?: boolean }) {
  const opt = props.fieldOptions.find(o => o.fieldName === row.fieldName)
  row.systemField = opt?.systemField
}

function addRow() {
  const first = props.fieldOptions[0]
  props.group.conditions.push({
    id: nextFilterNodeId('fc'),
    fieldName: first?.fieldName || 'process_status',
    operator: 'eq',
    value: '',
    systemField: first?.systemField,
  })
}

function addNestedGroup() {
  props.group.groups.push(createEmptyFilterGroup('and'))
}

function removeRow(index: number) {
  props.group.conditions.splice(index, 1)
}

function removeNestedGroup(index: number) {
  props.group.groups.splice(index, 1)
}
</script>

<template>
  <div :class="['filter-group-panel', (depth ?? 0) > 0 ? 'filter-group-panel--nested' : '']">
    <div class="filter-group-header">
      <el-select
        v-model="group.logic"
        size="small"
        class="logic-select"
      >
        <el-option
          :label="t('mainTableView.logicAnd')"
          value="and"
        />
        <el-option
          :label="t('mainTableView.logicOr')"
          value="or"
        />
      </el-select>
      <button
        v-if="(depth ?? 0) > 0"
        type="button"
        class="filter-group-remove"
        @click="emit('removeSelf')"
      >
        <el-icon><Close /></el-icon>
      </button>
    </div>

    <div class="filter-group-body">
      <div
        v-for="(row, rowIdx) in group.conditions"
        :key="row.id"
        class="filter-condition-row"
      >
        <span class="filter-tree-line" />
        <el-select
          v-model="row.fieldName"
          size="small"
          class="filter-field-select"
          @change="onFieldChange(row)"
        >
          <el-option
            v-for="opt in fieldOptions"
            :key="opt.fieldName"
            :label="opt.label"
            :value="opt.fieldName"
          />
        </el-select>
        <el-select
          v-model="row.operator"
          size="small"
          class="filter-op-select"
        >
          <el-option
            v-for="op in FILTER_OPERATOR_KEYS"
            :key="op"
            :label="formatOperatorLabel(op)"
            :value="op"
          />
        </el-select>
        <el-input
          v-if="operatorNeedsValue(row.operator)"
          v-model="row.value"
          size="small"
          class="filter-value-input"
          :placeholder="t('mainTableView.filterValuePlaceholder')"
        />
        <span
          v-else
          class="filter-value-spacer"
        />
        <button
          type="button"
          class="filter-row-remove"
          @click="removeRow(rowIdx)"
        >
          <el-icon><Close /></el-icon>
        </button>
      </div>

      <div
        v-for="(child, childIdx) in group.groups"
        :key="child.id"
        class="filter-nested-wrap"
      >
        <span class="filter-tree-line filter-tree-line--group" />
        <MainTableViewFilterGroup
          :group="child"
          :depth="(depth ?? 0) + 1"
          :field-options="fieldOptions"
          @remove-self="removeNestedGroup(childIdx)"
        />
      </div>

      <div class="filter-add-bar">
        <el-dropdown
          trigger="click"
          @command="(cmd: string) => (cmd === 'row' ? addRow() : addNestedGroup())"
        >
          <el-button
            size="small"
            type="primary"
            plain
          >
            <el-icon><Plus /></el-icon>
            {{ t('mainTableView.addFilter') }}
            <el-icon class="el-icon--right">
              <ArrowDown />
            </el-icon>
          </el-button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="row">
                {{ t('mainTableView.addFilterRow') }}
              </el-dropdown-item>
              <el-dropdown-item command="group">
                {{ t('mainTableView.addFilterGroup') }}
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </div>
  </div>
</template>

<style scoped>
.filter-group-panel {
  position: relative;
}

.filter-group-panel--nested {
  margin: 8px 0 8px 20px;
  padding: 12px 12px 8px;
  background: #f5f6f8;
  border: 1px solid #e4e7ed;
  border-radius: 6px;
}

.filter-group-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}

.logic-select {
  width: 88px;
}

.filter-group-remove {
  border: none;
  background: transparent;
  cursor: pointer;
  color: #909399;
  padding: 4px;
}

.filter-group-body {
  position: relative;
  padding-left: 18px;
}

.filter-group-body::before {
  content: '';
  position: absolute;
  left: 6px;
  top: 0;
  bottom: 28px;
  width: 2px;
  background: #c8c9cc;
  border-radius: 1px;
}

.filter-condition-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
  position: relative;
}

.filter-tree-line {
  position: absolute;
  left: -12px;
  top: 50%;
  width: 12px;
  height: 2px;
  background: #c8c9cc;
}

.filter-field-select {
  width: 150px;
  flex-shrink: 0;
}

.filter-op-select {
  width: 168px;
  flex-shrink: 0;
}

.filter-value-input {
  width: 160px;
  flex-shrink: 0;
}

.filter-value-spacer {
  width: 160px;
  flex-shrink: 0;
}

.filter-row-remove {
  border: none;
  background: transparent;
  cursor: pointer;
  color: #909399;
  padding: 4px;
  flex-shrink: 0;
}

.filter-nested-wrap {
  position: relative;
  margin-bottom: 8px;
}

.filter-tree-line--group {
  top: 24px;
}

.filter-add-bar {
  margin-top: 4px;
  padding-top: 4px;
}
</style>
