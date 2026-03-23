<template>
  <div class="sub-table-field">
    <div class="sub-table-header">
      <span class="title">{{ title }}</span>
      <el-button v-if="editable" type="primary" size="small" @click="handleAdd">
        <el-icon><Plus /></el-icon> Add
      </el-button>
    </div>

    <div class="sub-table-scroll-wrapper">
    <el-table :data="rows" size="small" border :max-height="400" v-loading="loading" style="width: 100%">
      <el-table-column
        v-for="col in columns"
        :key="col.field"
        :prop="col.field"
        :label="col.label"
        :min-width="columnMinWidth(col)"
        :show-overflow-tooltip="false"
      >
        <template #default="scope">
          <!-- 只读展示 -->
          <template v-if="col.type === 'upload'">
            <span
              v-if="scope.row[col.field]"
              class="file-download-link"
              :class="{ downloading: downloadingKeys[scope.$index + '_' + col.field] }"
              @click="downloadFile(scope.row[col.field], uploadNames[scope.$index + '_' + col.field], scope.$index, col.field)"
            >
              <el-icon v-if="downloadingKeys[scope.$index + '_' + col.field]" class="is-loading"><Loading /></el-icon>
              <el-icon v-else><Document /></el-icon>
              {{ getFilenameFromUrl(scope.row[col.field], uploadNames[scope.$index + '_' + col.field]) }}
            </span>
            <span v-else class="no-file">-</span>
          </template>
          <template v-else-if="col.type === 'colorPicker'">
            <span v-if="scope.row[col.field]" class="color-swatch" :style="{ backgroundColor: scope.row[col.field] }" :title="scope.row[col.field]" />
            <span v-else>-</span>
          </template>
          <template v-else-if="col.type === 'editor'">
            <span v-if="scope.row[col.field]" v-html="scope.row[col.field]" class="editor-preview" />
            <span v-else>-</span>
          </template>
          <template v-else-if="col.type === 'signature'">
            <img v-if="scope.row[col.field]" :src="scope.row[col.field]" class="signature-preview" alt="Signature" />
            <span v-else>-</span>
          </template>
          <template v-else-if="col.type === 'slider'">
            <el-slider
              v-if="scope.row[col.field] != null"
              :model-value="Number(scope.row[col.field])"
              :min="col.props?.min ?? 0"
              :max="col.props?.max ?? 100"
              disabled
              style="width: 100%; padding: 0 10px;"
            />
            <span v-else>-</span>
          </template>
          <template v-else-if="col.type === 'password'">
            <span>••••••</span>
          </template>
          <template v-else-if="col.type === 'rate'">
            <el-rate
              v-if="scope.row[col.field] != null"
              :model-value="Number(scope.row[col.field])"
              :max="col.props?.max || 5"
              disabled
              style="display: inline-flex;"
            />
            <span v-else>-</span>
          </template>
          <span v-else>{{ resolveDisplayValue(col, scope.row[col.field]) }}</span>
        </template>
      </el-table-column>

      <el-table-column v-if="editable" label="Actions" width="120">
        <template #default="scope">
          <el-button link type="primary" size="small" @click="openEditDialog(scope.$index)">Edit</el-button>
          <el-button link type="danger" size="small" @click="deleteRow(scope.$index)">Delete</el-button>
        </template>
      </el-table-column>

      <template #empty>
        <el-empty description="No Data" :image-size="40" />
      </template>
    </el-table>
    </div>

    <SubTableAddDialog
      :visible="dialogVisible"
      :columns="columns"
      :mode="dialogMode"
      :initialData="dialogInitialData"
      @update:visible="dialogVisible = $event"
      @save="handleDialogSave"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { Plus, Document, Loading } from '@element-plus/icons-vue'
import { ElMessageBox, ElMessage } from 'element-plus'
import SubTableAddDialog from './SubTableAddDialog.vue'
import { resolveDisplayValue } from './subTableAddDialogHelpers'
import type { DialogColumn } from './subTableAddDialogHelpers'

type Column = DialogColumn

/** 根据字段类型返回合理的最小列宽 */
function columnMinWidth(col: Column): number {
  if (col.minWidth) return col.minWidth
  switch (col.type) {
    case 'upload':       return 180
    case 'timerange':    return 200
    case 'datetime':     return 180
    case 'date':         return 130
    case 'tree':         return 180
    case 'checkbox':     return 160
    case 'treeselect':   return 160
    case 'colorPicker':  return 100
    case 'rate':         return 140
    case 'editor':       return 200
    case 'signature':    return 150
    case 'transfer':     return 180
    case 'cascader':     return 180
    case 'slider':       return 160
    case 'password':     return 120
    default:             return 120
  }
}

const props = defineProps<{
  title: string
  columns: Column[]
  modelValue?: any[]
  editable?: boolean
  loading?: boolean
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', val: any[]): void
}>()

const rows = ref<any[]>([])
// key = "{rowIndex}_{field}" → 原始文件名（本次会话上传时记录）
const uploadNames = ref<Record<string, string>>({})
// 正在下载的 key 集合
const downloadingKeys = ref<Record<string, boolean>>({})

// Dialog state
const dialogVisible = ref(false)
const dialogMode = ref<'add' | 'edit'>('add')
const editingRowIndex = ref<number | null>(null)
const dialogInitialData = ref<Record<string, any> | undefined>(undefined)

watch(() => props.modelValue, (v) => { rows.value = v ? [...v] : [] }, { immediate: true, deep: true })

/** 从 URL 中提取文件名，优先使用本次会话记录的原始文件名 */
function getFilenameFromUrl(url: string, savedName?: string): string {
  if (savedName) return savedName
  if (!url) return '未知文件'
  const last = url.split('/').pop()
  return last || '未知文件'
}

/** 点击文件名触发下载，使用 fetch+Blob 避免新标签页跳转 */
async function downloadFile(url: string, savedName: string | undefined, rowIndex: number, field: string) {
  if (!url) return
  const key = `${rowIndex}_${field}`
  if (downloadingKeys.value[key]) return

  const filename = getFilenameFromUrl(url, savedName)
  downloadingKeys.value = { ...downloadingKeys.value, [key]: true }
  const msg = ElMessage({ message: '正在下载文件...', type: 'info', duration: 0 })

  try {
    const response = await fetch(url)
    if (!response.ok) {
      msg.close()
      ElMessage.error(response.status === 404 ? '文件不存在，无法下载' : '文件下载失败，请重试')
      return
    }
    const blob = await response.blob()
    const blobUrl = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = blobUrl
    a.download = filename
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    URL.revokeObjectURL(blobUrl)
    msg.close()
  } catch {
    msg.close()
    ElMessage.error('文件下载失败，请重试')
  } finally {
    const next = { ...downloadingKeys.value }
    delete next[key]
    downloadingKeys.value = next
  }
}

function handleAdd() {
  dialogMode.value = 'add'
  dialogInitialData.value = undefined
  editingRowIndex.value = null
  dialogVisible.value = true
}

function openEditDialog(i: number) {
  dialogMode.value = 'edit'
  editingRowIndex.value = i
  dialogInitialData.value = { ...rows.value[i] }
  dialogVisible.value = true
}

function handleDialogSave(rowData: Record<string, any>) {
  if (dialogMode.value === 'add') {
    rows.value.push(rowData)
  } else if (dialogMode.value === 'edit' && editingRowIndex.value !== null) {
    rows.value[editingRowIndex.value] = rowData
  }
  emit('update:modelValue', [...rows.value])
}

async function deleteRow(i: number) {
  await ElMessageBox.confirm('Are you sure to delete this record?', 'Confirm', { type: 'warning' })
  rows.value.splice(i, 1)
  emit('update:modelValue', [...rows.value])
}
</script>

<style scoped lang="scss">
.sub-table-field {
  border: 1px solid #e6e6e6;
  border-radius: 4px;
  padding: 12px;
  background: #fafafa;

  .sub-table-scroll-wrapper {
    width: 100%;
    overflow-x: auto;
  }

  :deep(.el-table .cell) {
    white-space: nowrap;
  }

  .sub-table-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 10px;

    .title {
      font-weight: 500;
      font-size: 14px;
      color: #303133;
    }
  }

  .file-download-link {
    display: inline-flex;
    align-items: center;
    gap: 4px;
    color: #165DFF;
    text-decoration: underline;
    font-size: 12px;
    cursor: pointer;
    transition: color 0.2s;

    &:hover { color: #0e44cc; }
    &.downloading { color: #909399; cursor: wait; }
  }

  .no-file {
    color: #909399;
    font-size: 12px;
  }

  .color-swatch {
    display: inline-block;
    width: 20px;
    height: 20px;
    border-radius: 3px;
    border: 1px solid #dcdfe6;
    vertical-align: middle;
  }

  .editor-preview {
    display: inline-block;
    max-width: 200px;
    max-height: 60px;
    overflow: hidden;
    text-overflow: ellipsis;
    font-size: 12px;
    line-height: 1.4;
  }

  .signature-preview {
    max-width: 120px;
    max-height: 40px;
    object-fit: contain;
    vertical-align: middle;
  }
}
</style>
