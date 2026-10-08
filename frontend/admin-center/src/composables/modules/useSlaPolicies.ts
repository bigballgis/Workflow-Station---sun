/**
 * SLA policy list: lead time per Function Unit, edit + manual recalculation, history and jobs.
 * HTTP errors are already surfaced by the request interceptor; only success toasts are raised here.
 */
import { computed, reactive, ref, type CSSProperties } from 'vue'
import { useI18n } from 'vue-i18n'
import { notifyConfirm, notifySuccess, notifyWarning } from '@/utils/notify'
import { hasPermission } from '@/utils/permission'
import {
  slaPolicyApi,
  type SlaPolicyHistory,
  type SlaPolicyRow,
  type SlaRecalcJob,
  type SlaRecalcJobItem,
} from '@/api/slaPolicy'
import { useAdminListGrid } from '@/composables/list/useAdminListGrid'

export const SLA_ACTIONS_COL_WIDTH = 220
export const SLA_MIN_DAYS = 1
export const SLA_MAX_DAYS = 3650

export function useSlaPolicies() {
  const { t } = useI18n()

  const loading = ref(false)
  const canEdit = computed(() => hasPermission('sla:policy:edit'))
  const grid = useAdminListGrid<SlaPolicyRow>({
    storageKey: 'admin-list-layout:sla-policies',
    extraWidth: SLA_ACTIONS_COL_WIDTH,
  })

  const loadPolicies = async () => {
    const seq = grid.beginQuery()
    loading.value = true
    try {
      const page = await slaPolicyApi.query(grid.buildQuery())
      if (!grid.isCurrentQuery(seq)) return
      grid.applyPage(page, 'sla-policies/query response is missing its column declaration')
    } finally {
      if (grid.isCurrentQuery(seq)) loading.value = false
    }
  }

  // Edit dialog
  const editDialogVisible = ref(false)
  const saving = ref(false)
  const editForm = reactive({
    functionUnitCode: '',
    functionUnitName: '',
    currentDays: null as number | null,
    leadTimeDays: undefined as number | undefined,
    changeReason: '',
  })

  const showEditDialog = (row: SlaPolicyRow) => {
    editForm.functionUnitCode = row.functionUnitCode
    editForm.functionUnitName = row.functionUnitName
    editForm.currentDays = row.leadTimeDays
    editForm.leadTimeDays = row.leadTimeDays ?? undefined
    editForm.changeReason = ''
    editDialogVisible.value = true
  }

  const submitEdit = async () => {
    const days = editForm.leadTimeDays
    if (days == null || !Number.isInteger(days) || days < SLA_MIN_DAYS || days > SLA_MAX_DAYS) return
    saving.value = true
    try {
      const result = await slaPolicyApi.update(editForm.functionUnitCode, {
        leadTimeDays: days,
        changeReason: editForm.changeReason.trim() || undefined,
      })
      editDialogVisible.value = false
      if (result.dispatchStatus === 'DISPATCH_FAILED') {
        notifyWarning(t('sla.savedDispatchFailed'))
      } else {
        notifySuccess(t('sla.savedAndDispatched'))
      }
      void loadPolicies()
    } finally {
      saving.value = false
    }
  }

  const recalculate = async (row: SlaPolicyRow) => {
    try {
      await notifyConfirm(
        t('sla.recalcConfirmMsg', { name: row.functionUnitName || row.functionUnitCode, days: row.leadTimeDays }),
        t('sla.recalcConfirmTitle'),
        { confirmButtonText: t('sla.recalculate'), cancelButtonText: t('common.cancel'), type: 'warning' },
      )
    } catch {
      return
    }
    await slaPolicyApi.recalculate(row.functionUnitCode)
    notifySuccess(t('sla.recalcStarted'))
    void loadPolicies()
  }

  // Detail drawer: history + jobs + items of the selected job
  const detailVisible = ref(false)
  const detailLoading = ref(false)
  const detailRow = ref<SlaPolicyRow | null>(null)
  const history = ref<SlaPolicyHistory[]>([])
  const jobs = ref<SlaRecalcJob[]>([])
  const selectedJobId = ref<string | null>(null)
  const jobItems = ref<SlaRecalcJobItem[]>([])
  const itemsLoading = ref(false)

  const showDetail = async (row: SlaPolicyRow) => {
    detailRow.value = row
    detailVisible.value = true
    selectedJobId.value = null
    jobItems.value = []
    await refreshDetail()
  }

  const refreshDetail = async () => {
    const row = detailRow.value
    if (!row) return
    detailLoading.value = true
    try {
      const [h, j] = await Promise.all([
        slaPolicyApi.history(row.functionUnitCode),
        slaPolicyApi.jobs(row.functionUnitCode),
      ])
      history.value = h
      jobs.value = j
    } finally {
      detailLoading.value = false
    }
  }

  const selectJob = async (jobId: string) => {
    const row = detailRow.value
    if (!row) return
    selectedJobId.value = jobId
    itemsLoading.value = true
    try {
      jobItems.value = await slaPolicyApi.jobItems(row.functionUnitCode, jobId)
    } finally {
      itemsLoading.value = false
    }
  }

  return {
    loading,
    canEdit,
    loadPolicies,
    editDialogVisible,
    saving,
    editForm,
    showEditDialog,
    submitEdit,
    recalculate,
    detailVisible,
    detailLoading,
    detailRow,
    history,
    jobs,
    selectedJobId,
    jobItems,
    itemsLoading,
    showDetail,
    refreshDetail,
    selectJob,
    ...grid,
    // Re-wrapped with this app's own computed: the shared list package's refs carry its own copy of
    // the vue types, which vue-tsc does not unwrap in templates (every list page shows the same two
    // TS2345/TS2322 errors on :style / :height). Runtime values are identical.
    gridInnerStyle: computed<CSSProperties>(() => grid.gridInnerStyle.value),
    gridTableHeight: computed<number | undefined>(() => grid.gridTableHeight.value),
  }
}
