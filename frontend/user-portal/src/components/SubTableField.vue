<template>
  <div class="sub-table-field">
    <div class="sub-table-header">
      <span class="title">{{ title }}</span>
      <el-button v-if="editable" type="primary" size="small" @click="handleAdd">
        <el-icon><Plus /></el-icon> Add
      </el-button>
    </div>

    <el-table :data="rows" size="small" border :max-height="400" v-loading="loading">
      <el-table-column
        v-for="col in columns"
        :key="col.field"
        :prop="col.field"
        :label="col.label"
        :min-width="col.type === 'upload' ? 180 : (col.minWidth || 100)"
      >
        <template #default="scope">
          <template v-if="editable && editingRow === scope.$index">
            <el-input-number
              v-if="col.type === 'number'"
              v-model="scope.row[col.field]"
              size="small"
              :controls="false"
              style="width:100%"
            />
            <el-date-picker
              v-else-if="col.type === 'date'"
              v-model="scope.row[col.field]"
              type="date"
              size="small"
              value-format="YYYY-MM-DD"
              style="width:100%"
            />
            <!-- 文件上传 -->
            <div v-else-if="col.type === 'upload'" style="display:flex;flex-direction:column;gap:4px;">
              <el-upload
                :key="getUploadKey(scope.$index, col.field)"
                :action="getUploadAction(col)"
                :accept="col.props?.accept || '.jpg,.jpeg,.png,.pdf,.docx,.xlsx'"
                :show-file-list="false"
                :on-success="(res: any, file: any) => handleUploadSuccess(res, file, scope.$index, col.field)"
                :on-error="(err: any, file: any) => handleUploadError(err, file, scope.$index, col.field)"
              >
                <el-button size="small" type="primary">
                  <el-icon><Upload /></el-icon> Upload
                </el-button>
              </el-upload>
              <el-tag
                v-if="uploadNames[scope.$index + '_' + col.field]"
                size="small"
                type="success"
                closable
                @close="clearUpload(scope.$index, col.field)"
              >
                {{ uploadNames[scope.$index + '_' + col.field] }}
              </el-tag>
            </div>
            <el-input v-else v-model="scope.row[col.field]" size="small" />
          </template>
          <!-- 只读展示 -->
          <template v-else>
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
            <span v-else>{{ scope.row[col.field] ?? '-' }}</span>
          </template>
        </template>
      </el-table-column>

      <el-table-column v-if="editable" label="Actions" width="120">
        <template #default="scope">
          <template v-if="editingRow === scope.$index">
            <el-button link type="primary" size="small" @click="saveRow(scope.$index)">Save</el-button>
            <el-button link size="small" @click="cancelEdit(scope.$index)">Cancel</el-button>
          </template>
          <template v-else>
            <el-button link type="primary" size="small" @click="editRow(scope.$index)">Edit</el-button>
            <el-button link type="danger" size="small" @click="deleteRow(scope.$index)">Delete</el-button>
          </template>
        </template>
      </el-table-column>

      <template #empty>
        <el-empty description="No Data" :image-size="40" />
      </template>
    </el-table>
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { Plus, Upload, Document, Loading } from '@element-plus/icons-vue'
import { ElMessageBox, ElMessage } from 'element-plus'

interface Column {
  field: string
  label: string
  type?: 'text' | 'number' | 'date' | 'upload'
  minWidth?: number
  props?: Record<string, any>
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
const editingRow = ref<number | null>(null)
const backup = ref<any>(null)
// key = "{rowIndex}_{field}" → 原始文件名（本次会话上传时记录）
const uploadNames = ref<Record<string, string>>({})
// 每次清除文件时递增，强制 el-upload 重新挂载以重置内部文件计数
const uploadResetKeys = ref<Record<string, number>>({})
// 正在下载的 key 集合
const downloadingKeys = ref<Record<string, boolean>>({})

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

function getUploadAction(col: Column): string {
  const action = col.props?.action
  return (action && action !== '/') ? action : '/api/v1/upload'
}

function getUploadKey(rowIndex: number, field: string): string {
  const n = uploadResetKeys.value[`${rowIndex}_${field}`] || 0
  return `upload_${rowIndex}_${field}_${n}`
}

function handleUploadSuccess(res: any, file: any, rowIndex: number, field: string) {
  const url = res?.data?.url || ''
  rows.value[rowIndex][field] = url
  uploadNames.value = { ...uploadNames.value, [`${rowIndex}_${field}`]: file.name }
  // Auto-fill filename to the configured target column (if any)
  const uploadCol = props.columns.find(c => c.field === field)
  const fileNameTarget = uploadCol?.props?.fileNameTargetField
  if (fileNameTarget && props.columns.some(c => c.field === fileNameTarget)) {
    rows.value[rowIndex][fileNameTarget] = file.name
  }
}

function handleUploadError(_err: any, _file: any, rowIndex: number, field: string) {
  ElMessage.error(`File upload failed for row ${rowIndex + 1}, field "${field}"`)
}

function clearUpload(rowIndex: number, field: string) {
  rows.value[rowIndex][field] = ''
  const nextNames = { ...uploadNames.value }
  delete nextNames[`${rowIndex}_${field}`]
  uploadNames.value = nextNames
  // 递增 key 强制 el-upload 重新挂载，重置其内部文件列表和计数
  const k = `${rowIndex}_${field}`
  uploadResetKeys.value = { ...uploadResetKeys.value, [k]: (uploadResetKeys.value[k] || 0) + 1 }
}

function handleAdd() {
  const newRow: any = {}
  props.columns.forEach(c => { newRow[c.field] = c.type === 'number' ? 0 : '' })
  rows.value.push(newRow)
  editingRow.value = rows.value.length - 1
  backup.value = { ...newRow }
}

function editRow(i: number) {
  editingRow.value = i
  backup.value = { ...rows.value[i] }
}

function saveRow(i: number) {
  editingRow.value = null
  backup.value = null
  emit('update:modelValue', [...rows.value])
}

function cancelEdit(i: number) {
  if (backup.value !== null) {
    const isNew = Object.values(backup.value).every(v => v === '' || v === 0)
    if (isNew) rows.value.splice(i, 1)
    else rows.value[i] = { ...backup.value }
  }
  editingRow.value = null
  backup.value = null
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
}
</style>
