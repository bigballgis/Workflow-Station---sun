<template>
  <div class="variable-monitor">
    <div class="monitor-toolbar">
      <el-input
        v-model="searchText"
        :placeholder="t('debug.searchVariables')"
        size="small"
        clearable
        style="width: 200px;"
      >
        <template #prefix>
          <el-icon><Search /></el-icon>
        </template>
      </el-input>
      <el-button
        size="small"
        @click="handleRefresh"
      >
        <el-icon><Refresh /></el-icon> {{ t('common.refresh') }}
      </el-button>
      <el-button
        size="small"
        @click="handleExport"
      >
        <el-icon><Download /></el-icon> {{ t('common.export') }}
      </el-button>
    </div>

    <div class="variable-list">
      <el-table
        :data="filteredVariables"
        size="small"
        stripe
        max-height="400"
      >
        <el-table-column
          prop="name"
          :label="t('debug.variableName')"
          width="150"
        />
        <el-table-column
          prop="type"
          :label="t('common.type')"
          width="100"
        >
          <template #default="{ row }">
            <el-tag
              size="small"
              :type="typeTagType(row.type)"
            >
              {{ row.type }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column
          prop="value"
          :label="t('debug.value')"
          min-width="200"
        >
          <template #default="{ row }">
            <div
              v-if="editingKey === row.name && editable"
              class="edit-value"
            >
              <el-input
                v-model="editValue"
                size="small"
                @keyup.enter="saveEdit(row.name)"
              />
              <el-button
                size="small"
                type="primary"
                @click="saveEdit(row.name)"
              >
                {{ t('common.save') }}
              </el-button>
              <el-button
                size="small"
                @click="cancelEdit"
              >
                {{ t('common.cancel') }}
              </el-button>
            </div>
            <div
              v-else
              class="value-display"
              @dblclick="startEdit(row)"
            >
              <span
                v-if="row.type === 'object'"
                class="object-value"
              >
                <el-button
                  link
                  size="small"
                  @click="expandObject(row)"
                >
                  {{ row.expanded ? t('debug.collapse') : t('debug.expand') }} ({{ Object.keys(row.rawValue).length }} {{ t('debug.items') }})
                </el-button>
              </span>
              <span
                v-else-if="row.type === 'array'"
                class="array-value"
              >
                <el-button
                  link
                  size="small"
                  @click="expandObject(row)"
                >
                  {{ row.expanded ? t('debug.collapse') : t('debug.expand') }} [{{ row.rawValue.length }} {{ t('debug.items') }}]
                </el-button>
              </span>
              <span
                v-else
                :class="['value', row.type]"
              >{{ row.value }}</span>
              <el-icon
                v-if="editable"
                class="edit-icon"
              >
                <Edit />
              </el-icon>
            </div>
          </template>
        </el-table-column>
        <el-table-column
          v-if="editable"
          :label="t('common.actions')"
          width="80"
        >
          <template #default="{ row }">
            <el-button
              link
              type="primary"
              size="small"
              @click="startEdit(row)"
            >
              {{ t('common.edit') }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-empty
        v-if="!filteredVariables.length"
        :description="t('debug.noVariableData')"
      />
    </div>

    <!-- Object/Array Detail Dialog -->
    <el-dialog
      v-model="showDetailDialog"
      :title="t('debug.variableDetail', { name: detailVariable?.name })"
      width="600px"
    >
      <pre class="json-preview">{{ JSON.stringify(detailVariable?.rawValue, null, 2) }}</pre>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { Search, Refresh, Download, Edit } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'

const { t } = useI18n()

interface VariableItem {
  name: string
  type: string
  value: string
  rawValue: any
  expanded?: boolean
}

const props = defineProps<{
  variables: Record<string, any>
  editable?: boolean
}>()

const emit = defineEmits<{
  (e: 'update', key: string, value: any): void
  (e: 'refresh'): void
}>()

const searchText = ref('')
const editingKey = ref<string | null>(null)
const editValue = ref('')
const showDetailDialog = ref(false)
const detailVariable = ref<VariableItem | null>(null)

const variableList = computed<VariableItem[]>(() => {
  return Object.entries(props.variables).map(([name, value]) => {
    const type = getType(value)
    return {
      name,
      type,
      value: formatValue(value, type),
      rawValue: value,
      expanded: false
    }
  })
})

const filteredVariables = computed(() => {
  if (!searchText.value) return variableList.value
  const search = searchText.value.toLowerCase()
  return variableList.value.filter(v => 
    v.name.toLowerCase().includes(search) || 
    v.value.toLowerCase().includes(search)
  )
})

function getType(value: any): string {
  if (value === null) return 'null'
  if (value === undefined) return 'undefined'
  if (Array.isArray(value)) return 'array'
  return typeof value
}

function formatValue(value: any, type: string): string {
  if (type === 'null') return 'null'
  if (type === 'undefined') return 'undefined'
  if (type === 'object') return '{...}'
  if (type === 'array') return '[...]'
  if (type === 'string') return `"${value}"`
  return String(value)
}

function typeTagType(type: string): string {
  const map: Record<string, string> = {
    string: 'success',
    number: 'primary',
    boolean: 'warning',
    object: 'info',
    array: 'info',
    null: 'danger',
    undefined: 'danger'
  }
  return map[type] || 'info'
}

function startEdit(row: VariableItem) {
  if (!props.editable) return
  editingKey.value = row.name
  editValue.value = row.type === 'string' ? row.rawValue : JSON.stringify(row.rawValue)
}

function saveEdit(key: string) {
  try {
    let value: any = editValue.value
    // Try to parse as JSON
    try {
      value = JSON.parse(editValue.value)
    } catch {
      // Keep as string if not valid JSON
    }
    emit('update', key, value)
    editingKey.value = null
    ElMessage.success(t('debug.variableUpdated'))
  } catch (e) {
    ElMessage.error(t('debug.updateFailed'))
  }
}

function cancelEdit() {
  editingKey.value = null
  editValue.value = ''
}

function expandObject(row: VariableItem) {
  detailVariable.value = row
  showDetailDialog.value = true
}

function handleRefresh() {
  emit('refresh')
}

function handleExport() {
  const data = JSON.stringify(props.variables, null, 2)
  const blob = new Blob([data], { type: 'application/json' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `variables_${Date.now()}.json`
  a.click()
  URL.revokeObjectURL(url)
  ElMessage.success(t('debug.exportSuccess'))
}
</script>

<style lang="scss" scoped>
.variable-monitor {
  .monitor-toolbar {
    display: flex;
    gap: 10px;
    margin-bottom: 12px;
  }
  
  .value-display {
    display: flex;
    align-items: center;
    gap: 8px;
    cursor: pointer;
    
    .edit-icon {
      opacity: 0;
      transition: opacity 0.2s;
    }
    
    &:hover .edit-icon {
      opacity: 1;
    }
    
    .value {
      &.string { color: #67C23A; }
      &.number { color: #409EFF; }
      &.boolean { color: #E6A23C; }
      &.null, &.undefined { color: #909399; font-style: italic; }
    }
  }
  
  .edit-value {
    display: flex;
    gap: 8px;
    align-items: center;
  }
  
  .json-preview {
    background: #f5f7fa;
    padding: 16px;
    border-radius: 4px;
    max-height: 400px;
    overflow: auto;
    font-family: 'Monaco', 'Menlo', monospace;
    font-size: 12px;
    margin: 0;
  }
}
</style>
