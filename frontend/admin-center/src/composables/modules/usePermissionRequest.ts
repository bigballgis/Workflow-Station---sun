/**
 * 权限申请列表业务逻辑 composable
 */
import { ref, reactive, watch, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { AppErrorCode } from '@/types/errors'
import { errorTranslator } from '@/utils/errorTranslator'
import { notifyError } from '@/utils/notify'
import { permissionRequestApi, type PermissionRequest, type PermissionRequestStatus } from '@/api/permissionRequest'

export function usePermissionRequest() {
  const { t } = useI18n()

  const loading = ref(false)
  const requests = ref<PermissionRequest[]>([])
  const total = ref(0)
  const dateRange = ref<[string, string] | null>(null)

  const query = reactive({
    status: '' as PermissionRequestStatus | '',
    requestType: '' as 'VIRTUAL_GROUP' | 'BUSINESS_UNIT_ROLE' | '',
    startDate: '',
    endDate: '',
    page: 1,
    size: 20
  })

  watch(dateRange, (val) => {
    if (val) { query.startDate = val[0]; query.endDate = val[1] }
    else { query.startDate = ''; query.endDate = '' }
  })

  const statusType = (status: PermissionRequestStatus): 'warning' | 'success' | 'danger' | 'info' => {
    const map: Record<PermissionRequestStatus, 'warning' | 'success' | 'danger' | 'info'> = {
      PENDING: 'warning', APPROVED: 'success', REJECTED: 'danger', CANCELLED: 'info'
    }
    return map[status] || 'info'
  }

  const statusText = (status: PermissionRequestStatus) => {
    const map: Record<PermissionRequestStatus, string> = {
      PENDING: t('permissionRequest.pending'), APPROVED: t('permissionRequest.approved'),
      REJECTED: t('permissionRequest.rejected'), CANCELLED: t('permissionRequest.cancelled')
    }
    return map[status] || status
  }

  const formatDate = (dateStr: string) => {
    if (!dateStr) return '-'
    return new Date(dateStr).toLocaleString('zh-CN', {
      year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit'
    })
  }

  const handleSearch = async () => {
    loading.value = true
    try {
      const params = {
        status: query.status || undefined, requestType: query.requestType || undefined,
        startDate: query.startDate || undefined, endDate: query.endDate || undefined,
        page: query.page - 1, size: query.size
      }
      const result = await permissionRequestApi.list(params)
      requests.value = result.content
      total.value = result.totalElements
    } catch {
      notifyError(t(errorTranslator(AppErrorCode.PERMISSION_REQUEST_FAILED)))
    } finally { loading.value = false }
  }

  const handleReset = () => {
    Object.assign(query, { status: '', requestType: '', startDate: '', endDate: '', page: 1 })
    dateRange.value = null
    handleSearch()
  }

  return { loading, requests, total, dateRange, query, statusType, statusText, formatDate, handleSearch, handleReset }
}
