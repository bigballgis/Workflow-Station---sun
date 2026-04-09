<template>
  <div class="sub-table-field">
    <div class="sub-table-header">
      <span class="title">{{ title }}</span>
      <el-button v-if="editable" type="primary" size="small" @click="handleAdd">
        <el-icon><Plus /></el-icon> {{ t('subTable.add') }}
      </el-button>
    </div>

    <div class="sub-table-scroll-wrapper">
    <el-table :data="rows" size="small" border :max-height="400" v-loading="loading" style="width: 100%" :show-summary="hasSummary" :summary-method="getSummaryMethod">
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
            <span v-if="scope.row[col.field]" v-html="sanitizeHtml(scope.row[col.field])" class="editor-preview" />
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

      <!-- Task status column (multi-instance subtask completion) -->
      <el-table-column v-if="showTaskStatus" :label="t('subTable.taskStatus')" width="120" align="center">
        <template #default="scope">
          <el-tag
            :type="scope.row.task_status === 'COMPLETED' ? 'success' : 'warning'"
            size="small"
          >
            {{ scope.row.task_status === 'COMPLETED' ? t('subTable.taskCompleted') : t('subTable.taskPending') }}
          </el-tag>
        </template>
      </el-table-column>

      <el-table-column v-if="editable" :label="t('subTable.actions')" width="120">
        <template #default="scope">
          <el-button link type="primary" size="small" @click="openEditDialog(scope.$index)">{{ t('subTable.edit') }}</el-button>
          <el-button link type="danger" size="small" @click="deleteRow(scope.$index)">{{ t('subTable.delete') }}</el-button>
        </template>
      </el-table-column>

      <!-- View subtask detail button (read-only mode) -->
      <el-table-column v-if="showViewDetail" :label="t('subTable.actions')" width="100" align="center">
        <template #default="scope">
          <el-button
            link
            type="primary"
            size="small"
            :disabled="scope.row.task_status !== 'COMPLETED'"
            @click="emit('viewDetail', scope.row, scope.$index)"
          >
            {{ t('subTable.viewDetail') }}
          </el-button>
        </template>
      </el-table-column>

      <!-- Fill form button for multi-instance subtask (todo mode) -->
      <el-table-column v-if="showFillButton" :label="t('subTable.actions')" width="100" align="center">
        <template #default="scope">
          <el-button
            link
            type="primary"
            size="small"
            @click="emit('fillForm', scope.row, scope.$index)"
          >
            {{ t('subTable.add') }}
          </el-button>
        </template>
      </el-table-column>

      <!-- Multi-instance assignment column -->
      <el-table-column v-if="showAssigneeColumn" :label="t('subTable.assignee')" width="180">
        <template #default="scope">
          <div class="assignee-cell">
            <span v-if="scope.row.assignee_display_name" class="assignee-name">
              {{ scope.row.assignee_display_name }}
            </span>
            <span v-else-if="assigneeField && scope.row[assigneeField]" class="assignee-name">
              {{ getUserDisplayName(scope.row[assigneeField]) }}
            </span>
            <span v-else class="text-muted">{{ t('subTable.unassigned') }}</span>
            <el-button 
              v-if="canAssign"
              link 
              type="primary" 
              size="small" 
              @click="openAssignDialog(scope.row, scope.$index)"
              class="assign-btn">
              {{ scope.row[assigneeField] ? t('subTable.reassign') : t('subTable.assign') }}
            </el-button>
          </div>
        </template>
      </el-table-column>

      <template #empty>
        <el-empty :description="t('subTable.noData')" :image-size="40" />
      </template>
    </el-table>
    </div>

    <SubTableAddDialog
      :visible="dialogVisible"
      :columns="columns"
      :mode="dialogMode"
      :initialData="dialogInitialData"
      :row-formulas="rowFormulas"
      :column-validation-rules="validationConfig?.columnRules"
      :upload-url="uploadUrl"
      @update:visible="dialogVisible = $event"
      @save="handleDialogSave"
    />

    <!-- User picker dialog for assignment -->
    <el-dialog 
      v-model="assignDialogVisible" 
      :title="t('subTable.selectAssignee')" 
      width="500px"
      @opened="onAssignDialogOpened">
      <el-form label-width="100px">
        <el-form-item :label="t('subTable.user')">
          <el-select 
            v-model="selectedAssigneeId" 
            :placeholder="t('subTable.searchUser')" 
            filterable
            remote
            :remote-method="searchUsers"
            :loading="userSearchLoading"
            style="width: 100%;">
            <el-option
              v-for="user in userOptions"
              :key="user.id"
              :label="`${user.name} (${user.username})`"
              :value="user.id"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="assignDialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" @click="confirmAssignment" :loading="assigning">
          {{ t('common.confirm') }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { Plus, Document, Loading } from '@element-plus/icons-vue'
import { ElMessageBox, ElMessage } from 'element-plus'
import DOMPurify from 'dompurify'
import SubTableAddDialog from './SubTableAddDialog.vue'
import { resolveDisplayValue } from './subTableAddDialogHelpers'
import type { DialogColumn } from './subTableAddDialogHelpers'
import type { RowFormulaRule, SubTableValidationConfig } from './formRendererHelpers'
import { calculateSummary } from './businessLogicEngine'
import type { AssignSubTableRowResponse } from '@/api/task'
import { assignSubTableRow, assignSubTableRowByIdentity, getSubTableData, getTaskDetail } from '@/api/task'
import {
  pickHttpErrorBodyMessage,
  unwrapPortalApiPayload,
  resolveUserFacingHttpMessage
} from '@/utils/httpErrorMessage'
import { userApi } from '@/api/user'
import { onMounted, onBeforeUnmount } from 'vue'
import { useSubTableWebSocket, type SubTableUpdateMessage } from '@/composables/useSubTableWebSocket'

const { t } = useI18n()

/** Sanitize HTML content to prevent XSS */
function sanitizeHtml(html: string): string {
  return DOMPurify.sanitize(html, {
    ALLOWED_TAGS: ['p', 'br', 'strong', 'em', 'u', 's', 'ol', 'ul', 'li',
      'h1', 'h2', 'h3', 'h4', 'h5', 'h6', 'a', 'img', 'table', 'tr', 'td', 'th', 'span', 'div'],
    ALLOWED_ATTR: ['href', 'src', 'alt', 'class', 'style', 'target', 'rel'],
  })
}

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
  rowFormulas?: RowFormulaRule[]
  summaryColumns?: string[]
  summaryAggregations?: Record<string, 'SUM' | 'AVG' | 'COUNT' | 'MIN' | 'MAX'>
  validationConfig?: SubTableValidationConfig
  uploadUrl?: string
  // Multi-instance assignment props
  taskId?: string
  assigneeField?: string
  canAssign?: boolean
  showAssignButton?: boolean
  // Real-time sync props
  enablePolling?: boolean
  pollingInterval?: number
  enableWebSocket?: boolean
  // View detail props (application detail read-only mode)
  showViewDetail?: boolean
  showTaskStatus?: boolean
  // Fill form button (todo detail for MI subtask)
  showFillButton?: boolean
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', val: any[]): void
  (e: 'assignmentChanged'): void
  (e: 'dataRefreshed', rows: any[]): void
  (e: 'viewDetail', row: any, index: number): void
  (e: 'fillForm', row: any, index: number): void
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

// Assignee column: show when assign buttons are active, OR when data already has assignee values (read-only completed tasks)
const showAssigneeColumn = computed(() => {
  if (props.showAssignButton && props.assigneeField) return true
  if (!props.assigneeField) return false
  return rows.value.some(r =>
    r && (r.assignee_display_name || r[props.assigneeField!])
  )
})

// Summary row support
const hasSummary = computed(() => (props.summaryColumns?.length ?? 0) > 0)

function getSummaryMethod({ columns: tableCols }: { columns: any[] }) {
  const sums: string[] = []
  tableCols.forEach((col: any, index: number) => {
    if (index === 0) {
      sums[index] = t('subTable.summary')
      return
    }
    const prop = col.property
    if (!prop || !props.summaryColumns?.includes(prop) || !props.summaryAggregations?.[prop]) {
      sums[index] = ''
      return
    }
    const agg = props.summaryAggregations[prop]
    const val = calculateSummary(rows.value, prop, agg)
    sums[index] = `${val}`
  })
  return sums
}

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
  const msg = ElMessage({ message: t('common.downloading'), type: 'info', duration: 0 })

  try {
    const response = await fetch(url)
    if (!response.ok) {
      msg.close()
      ElMessage.error(response.status === 404 ? t('common.fileNotFound') : t('common.downloadFailed'))
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
    ElMessage.error(t('common.downloadFailed'))
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
  await ElMessageBox.confirm(t('subTable.deleteConfirm'), t('common.confirm'), { type: 'warning' })
  rows.value.splice(i, 1)
  emit('update:modelValue', [...rows.value])
}

// Assignment functionality
const assignDialogVisible = ref(false)
const selectedAssigneeId = ref('')
const currentAssignRow = ref<any>(null)
const currentAssignRowIndex = ref<number | null>(null)
const assigning = ref(false)
const userOptions = ref<any[]>([])
const userSearchLoading = ref(false)
const userNameCache = ref<Record<string, string>>({})

function openAssignDialog(row: any, rowIndex: number) {
  currentAssignRow.value = row
  currentAssignRowIndex.value = rowIndex
  selectedAssigneeId.value = row[props.assigneeField || ''] || ''
  assignDialogVisible.value = true
}

function onAssignDialogOpened() {
  searchUsers('')
}

async function searchUsers(keyword: string) {
  userSearchLoading.value = true
  try {
    const result = await userApi.searchUsers(keyword || '')
    userOptions.value = [...result]
    // Cache user names
    result.forEach((user: any) => {
      userNameCache.value[user.id] = user.name
    })
  } catch (e) {
    console.error('Failed to search users:', e)
    userOptions.value = []
  } finally {
    userSearchLoading.value = false
  }
}

function getUserDisplayName(userId: string): string {
  if (userNameCache.value[userId]) return userNameCache.value[userId]
  return userId.startsWith('user-') ? userId.substring(5) : userId
}

/**
 * 子表行主键：引擎分配 API 需要关系表数字主键（如 participants.id）。
 * 兼容仅带 participant_id / 大小写变体、或表单序列化后的字段名。
 */
function resolveSubTableRowPk(row: Record<string, unknown> | null | undefined): string | number | null {
  if (!row) return null
  const r = row as Record<string, unknown>
  const candidates: unknown[] = [
    r.id,
    r.rowId,
    r.participant_id,
    r.participantId,
    (r as { ID?: unknown }).ID,
    (r as { RowId?: unknown }).RowId
  ]
  for (const v of candidates) {
    if (v != null && v !== '') return v as string | number
  }
  return null
}

function sameValue(a: unknown, b: unknown): boolean {
  const sa = a == null ? '' : String(a).trim().toLowerCase()
  const sb = b == null ? '' : String(b).trim().toLowerCase()
  return sa !== '' && sb !== '' && sa === sb
}

async function resolveMissingRowIdFromServer(
  taskId: string,
  localRow: Record<string, unknown>,
  rowIndex: number | null
): Promise<number | null> {
  try {
    const response = await getSubTableData(taskId)
    const payload = (response as Record<string, unknown>).data as Record<string, unknown> | undefined
    const rowsFromServer = Array.isArray(payload?.rows) ? (payload!.rows as Record<string, unknown>[]) : []
    if (!rowsFromServer.length) return null

    const byEmail = rowsFromServer.find(r => sameValue(r.email, localRow.email))
    const byNameAndDept = rowsFromServer.find(
      r => sameValue(r.name, localRow.name) && sameValue(r.department, localRow.department)
    )
    const byIndex =
      rowIndex != null && rowIndex >= 0 && rowIndex < rowsFromServer.length
        ? rowsFromServer[rowIndex]
        : null
    const match = byEmail || byNameAndDept || byIndex || null
    if (!match) return null

    const pk = resolveSubTableRowPk(match)
    const rowId = pk != null ? Number(pk) : NaN
    return Number.isNaN(rowId) ? null : rowId
  } catch (error: unknown) {
    return null
  }
}

async function resolveMissingRowIdFromTaskDetail(
  taskId: string,
  localRow: Record<string, unknown>,
  rowIndex: number | null
): Promise<{
  rowId: number | null
  effectiveTaskId?: string
  meetingHints?: { topic?: string; location?: string; organizerName?: string }
}> {
  try {
    const detailRes = await getTaskDetail(taskId)
    const detail = (detailRes as Record<string, unknown>).data as Record<string, unknown> | undefined
    const effectiveTaskId =
      detail && typeof detail.taskId === 'string' && detail.taskId.trim().length > 0 ? detail.taskId : taskId
    const vars = (detail?.variables as Record<string, unknown> | undefined) || {}
    const subTables = (vars.__subTables__ as Record<string, unknown> | undefined) || {}
    const allRows: Record<string, unknown>[] = []
    Object.values(subTables).forEach(v => {
      if (Array.isArray(v)) {
        v.forEach(r => {
          if (r && typeof r === 'object') allRows.push(r as Record<string, unknown>)
        })
      }
    })
    const meetingHints = {
      topic: typeof vars.topic === 'string' ? vars.topic : undefined,
      location: typeof vars.location === 'string' ? vars.location : undefined,
      organizerName: typeof vars.organizer_name === 'string' ? vars.organizer_name : undefined
    }
    if (!allRows.length) return { rowId: null, effectiveTaskId, meetingHints }
    const byEmail = allRows.find(r => sameValue(r.email, localRow.email))
    const byNameAndDept = allRows.find(
      r => sameValue(r.name, localRow.name) && sameValue(r.department, localRow.department)
    )
    const byIndex = rowIndex != null && rowIndex >= 0 && rowIndex < allRows.length ? allRows[rowIndex] : null
    const match = byEmail || byNameAndDept || byIndex || null
    if (!match) return { rowId: null, effectiveTaskId, meetingHints }
    const pk = resolveSubTableRowPk(match)
    const rowId = pk != null ? Number(pk) : NaN
    return { rowId: Number.isNaN(rowId) ? null : rowId, effectiveTaskId, meetingHints }
  } catch {
    return { rowId: null, effectiveTaskId: taskId }
  }
}

async function confirmAssignment() {
  if (!selectedAssigneeId.value) {
    ElMessage.warning(t('subTable.pleaseSelectUser'))
    return
  }

  const row = currentAssignRow.value as Record<string, unknown> | null | undefined
  const rowPk = resolveSubTableRowPk(row)
  let effectiveTaskId = props.taskId
  let meetingHints: { topic?: string; location?: string; organizerName?: string } | undefined
  let rowIdNum = rowPk != null ? Number(rowPk) : NaN
  if (props.taskId && (rowPk == null || Number.isNaN(rowIdNum)) && row) {
    let recovered = await resolveMissingRowIdFromServer(props.taskId, row, currentAssignRowIndex.value)
    if (recovered == null) {
      const fromDetail = await resolveMissingRowIdFromTaskDetail(props.taskId, row, currentAssignRowIndex.value)
      recovered = fromDetail.rowId
      meetingHints = fromDetail.meetingHints
      if (fromDetail.effectiveTaskId && fromDetail.effectiveTaskId.trim()) {
        effectiveTaskId = fromDetail.effectiveTaskId
      }
    }
    if (recovered != null) {
      rowIdNum = recovered
    }
  }
  if (!props.taskId) {
    ElMessage.error(t('subTable.assignmentFailed'))
    return
  }

  assigning.value = true
  try {
    let response: unknown
    if (!Number.isNaN(rowIdNum)) {
      response = await assignSubTableRow(
        props.taskId,
        rowIdNum,
        selectedAssigneeId.value
      )
    } else {
      const identityRow = row || {}
      response = await assignSubTableRowByIdentity(props.taskId, {
        // taskId may differ from route param in some task detail payloads
        assigneeId: selectedAssigneeId.value,
        email: typeof (identityRow as Record<string, unknown>).email === 'string'
          ? String((identityRow as Record<string, unknown>).email)
          : undefined,
        name: typeof (identityRow as Record<string, unknown>).name === 'string'
          ? String((identityRow as Record<string, unknown>).name)
          : undefined,
        department: typeof (identityRow as Record<string, unknown>).department === 'string'
          ? String((identityRow as Record<string, unknown>).department)
          : undefined,
        topic: meetingHints?.topic,
        location: meetingHints?.location,
        organizerName: meetingHints?.organizerName
      })
      // retry with effective task id from detail if route task id is stale
      if (effectiveTaskId !== props.taskId) {
        response = await assignSubTableRowByIdentity(effectiveTaskId, {
          assigneeId: selectedAssigneeId.value,
          email: typeof (identityRow as Record<string, unknown>).email === 'string'
            ? String((identityRow as Record<string, unknown>).email)
            : undefined,
          name: typeof (identityRow as Record<string, unknown>).name === 'string'
            ? String((identityRow as Record<string, unknown>).name)
            : undefined,
          department: typeof (identityRow as Record<string, unknown>).department === 'string'
            ? String((identityRow as Record<string, unknown>).department)
            : undefined,
          topic: meetingHints?.topic,
          location: meetingHints?.location,
          organizerName: meetingHints?.organizerName
        })
      }
    }

    const result = unwrapPortalApiPayload<AssignSubTableRowResponse>(response)
    const assigneePresent =
      result != null &&
      result.assigneeId != null &&
      String(result.assigneeId).trim().length > 0
    // success 缺省但已带回 assigneeId 时仍视为成功（兼容序列化差异）；success===false 时走失败提示
    const ok =
      result != null &&
      result.success !== false &&
      (result.success === true || assigneePresent)

    if (ok && result) {
      // Update the row data
      if (currentAssignRowIndex.value !== null && props.assigneeField) {
        rows.value[currentAssignRowIndex.value][props.assigneeField] = result.assigneeId
        // Cache the user name
        userNameCache.value[result.assigneeId] = result.assigneeName ?? result.assigneeId
        emit('update:modelValue', [...rows.value])
        emit('assignmentChanged')
      }

      ElMessage.success(t('subTable.assignmentSuccess'))
      assignDialogVisible.value = false
    } else {
      const r = result as Record<string, unknown> | null
      const hint =
        (r && typeof r.errorMessage === 'string' && r.errorMessage.trim()) ||
        (r && typeof r.message === 'string' && r.message.trim()) ||
        t('subTable.assignmentFailed')
      ElMessage.error(hint)
    }
  } catch (error: unknown) {
    console.error('Failed to assign sub-table row:', error)
    const ax = error as { response?: { status?: number; data?: unknown }; message?: string }
    try {
      const probe = await getTaskDetail(effectiveTaskId || props.taskId)
      const probeData = (probe as Record<string, unknown>).data as Record<string, unknown> | undefined
      void probeData
    } catch (probeError: unknown) {
      void probeError
    }
    const msg =
      pickHttpErrorBodyMessage(ax.response?.data) ||
      resolveUserFacingHttpMessage(error, t) ||
      (typeof ax.message === 'string' && ax.message.trim().length > 0 ? ax.message.trim() : undefined) ||
      t('subTable.assignmentFailed')
    ElMessage.error(msg)
  } finally {
    assigning.value = false
  }
}

// Real-time polling functionality
let pollingTimer: ReturnType<typeof setInterval> | null = null

// WebSocket functionality
const { connected: wsConnected, subscribe: wsSubscribe, unsubscribe: wsUnsubscribe } = useSubTableWebSocket()

async function refreshSubTableData() {
  if (!props.taskId) return
  
  try {
    const response = await getSubTableData(props.taskId)
    const result = response.data || response
    
    if (result.rows && Array.isArray(result.rows)) {
      // Merge the refreshed data with existing rows
      const updatedRows = rows.value.map(existingRow => {
        const refreshedRow = result.rows.find((r: any) => r.id === existingRow.id)
        if (refreshedRow) {
          // Update assignee and status fields while preserving other data
          return {
            ...existingRow,
            ...refreshedRow
          }
        }
        return existingRow
      })
      
      rows.value = updatedRows
      emit('update:modelValue', [...rows.value])
      emit('dataRefreshed', updatedRows)
    }
  } catch (error) {
    console.error('Failed to refresh sub-table data:', error)
    // Silently fail - don't show error message for background polling
  }
}

function startPolling() {
  if (!props.enablePolling || !props.taskId) return
  
  stopPolling()
  
  const interval = props.pollingInterval || 5000 // Default 5 seconds
  pollingTimer = setInterval(() => {
    refreshSubTableData()
  }, interval)
}

function stopPolling() {
  if (pollingTimer) {
    clearInterval(pollingTimer)
    pollingTimer = null
  }
}

// WebSocket subscription management
function startWebSocketSubscription() {
  if (!props.enableWebSocket || !props.taskId) return
  
  stopWebSocketSubscription()
  
  wsSubscribe(props.taskId, (message: SubTableUpdateMessage) => {
    console.log('[SubTableField] Received WebSocket update:', message)
    // Refresh data when receiving update notification
    refreshSubTableData()
  })
}

function stopWebSocketSubscription() {
  wsUnsubscribe()
}

// Lifecycle hooks for polling
onMounted(() => {
  if (props.enablePolling) {
    startPolling()
  }
  if (props.enableWebSocket) {
    startWebSocketSubscription()
  }
})

onBeforeUnmount(() => {
  stopPolling()
  stopWebSocketSubscription()
})

// Watch for enablePolling changes
watch(() => props.enablePolling, (enabled) => {
  if (enabled) {
    startPolling()
  } else {
    stopPolling()
  }
})

// Watch for enableWebSocket changes
watch(() => props.enableWebSocket, (enabled) => {
  if (enabled) {
    startWebSocketSubscription()
  } else {
    stopWebSocketSubscription()
  }
})

// Watch for taskId changes
watch(() => props.taskId, () => {
  if (props.enablePolling) {
    stopPolling()
    startPolling()
  }
  if (props.enableWebSocket) {
    stopWebSocketSubscription()
    startWebSocketSubscription()
  }
})
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

  .assignee-cell {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 8px;

    .assignee-name {
      font-size: 13px;
      color: #303133;
    }

    .text-muted {
      font-size: 13px;
      color: #909399;
    }

    .assign-btn {
      flex-shrink: 0;
    }
  }
}
</style>
