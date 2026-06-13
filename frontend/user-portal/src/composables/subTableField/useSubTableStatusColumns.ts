import { computed, type Ref } from 'vue'
import type { Column, SubTableFieldProps, SubTableFieldT } from './subTableFieldTypes'

export function normalizeColumnHeaderLabel(s: string): string {
  return String(s || '').trim().toLowerCase()
}

/**
 * List view already carries MI / task progress (often long i18n labels like "Multi-instance subtask status"
 * with a field name other than {@code task_status}) — suppress the runtime "Status" column.
 */
function columnRepresentsMiOrTaskStatusList(col: Column): boolean {
  const f = String(col.field || '').toLowerCase()
  if (f === 'task_status' || f.endsWith('_task_status')) return true
  if (/\btask[_-]?status\b/i.test(f) || f.includes('taskstatus')) return true
  const lab = normalizeColumnHeaderLabel(String(col.label || ''))
  if (!lab) return false
  if (lab.includes('task status') || lab.includes('subtask status')) return true
  if (lab.includes('sub-task') && lab.includes('status')) return true
  if ((lab.includes('multi-instance') || lab.includes('multi instance')) && lab.includes('status')) return true
  if (lab.includes('multiinstance') && lab.includes('status')) return true
  return false
}

/** English/legacy headers often use "Status" while i18n runtime column uses another locale — still one conceptual column. */
function columnHeaderIsGenericStatusLabel(col: Column): boolean {
  const lab = normalizeColumnHeaderLabel(String(col.label || ''))
  return lab === 'status' || lab === '状态' || lab === '狀態'
}

/** Runtime Status / Actions columns: suppress when the designer list already provides them. */
export function useSubTableStatusColumns(props: SubTableFieldProps, rows: Ref<any[]>, t: SubTableFieldT) {
  function formatTaskStatus(status: unknown): string {
    if (status === 'COMPLETED') return t('subTable.taskCompleted')
    if (status === 'IN_PROGRESS' || status === 'ASSIGNED') return t('subTable.taskInProgress')
    return t('subTable.taskPending')
  }

  /**
   * Row carries Flowable MI task_status; list already has a column whose header reads like a task/MI status
   * (even when {@link columnRepresentsMiOrTaskStatusList} missed due to unusual wording).
   */
  function listViewLikelyAlreadyShowsTaskStatus(rowsSample: unknown[]): boolean {
    const r0 = rowsSample?.[0]
    if (!r0 || typeof r0 !== 'object') return false
    if ((r0 as Record<string, unknown>).task_status === undefined) return false
    if ((props.columns || []).some(columnHeaderIsGenericStatusLabel)) return true
    return (props.columns || []).some(c => {
      const lab = normalizeColumnHeaderLabel(String(c.label || ''))
      if (!lab.includes('status')) return false
      return /task|subtask|sub-task|multi|instance|parallel|loop|progress|assignee|participant|办理|子任务|多实例|進度|狀態/.test(lab)
    })
  }

  /** Designer list may already include task_status / an "Actions" column; avoid duplicating MI summary extras. */
  const effectiveShowTaskStatus = computed(() => {
    if (!props.showTaskStatus) return false
    if (props.columns.some(columnRepresentsMiOrTaskStatusList)) return false
    const statusHeader = normalizeColumnHeaderLabel(t('subTable.taskStatus'))
    if (statusHeader && props.columns.some(c => normalizeColumnHeaderLabel(c.label) === statusHeader)) {
      return false
    }
    if (listViewLikelyAlreadyShowsTaskStatus(rows.value)) return false
    return true
  })

  const effectiveShowViewDetail = computed(() => {
    if (!props.showViewDetail) return false
    if (props.columns.some(c => String(c.field).toLowerCase() === 'actions')) return false
    const actionsHeader = normalizeColumnHeaderLabel(t('subTable.actions'))
    if (actionsHeader && props.columns.some(c => normalizeColumnHeaderLabel(c.label) === actionsHeader)) {
      return false
    }
    /**
     * Read-only (e.g. My Request): Link Form already provides a row-level Details affordance; the extra
     * Actions/Detail column duplicates UX for common MI+linkForm list designs.
     */
    if (!props.editable && props.columns.some(c => c.type === 'linkForm')) {
      return false
    }
    /** Same locale/header mismatch pattern as Status: designer "Actions" vs i18n. */
    const r0 = rows.value?.[0]
    if (
      !props.editable &&
      r0 &&
      typeof r0 === 'object' &&
      (r0 as Record<string, unknown>).task_status !== undefined &&
      (props.columns || []).some(c => {
        const lab = normalizeColumnHeaderLabel(String(c.label || ''))
        return lab === 'actions' || lab === '操作'
      })
    ) {
      return false
    }
    return true
  })

  return { formatTaskStatus, effectiveShowTaskStatus, effectiveShowViewDetail }
}
