import { computed } from 'vue'
import type { WritableComputedRef } from 'vue'
import { resolveInlineFormBelowDesign } from '@/components/designer/formPreviewTypes'
import type {
  SubTableListColumnDTO,
  SubTableListViewProps,
  TFn,
} from './types'
import type { PreviewColumn } from '@/components/designer/sub-table-list/SubTablePreviewDialog.vue'

const MOCK_ROW_COUNT = 3

interface UsePortalPreviewOptions {
  props: SubTableListViewProps
  viewColumns: WritableComputedRef<SubTableListColumnDTO[]>
  isLinkColumn: (column: SubTableListColumnDTO) => boolean
  isLookupColumn: (column: SubTableListColumnDTO) => boolean
  getLinkText: (column: SubTableListColumnDTO) => string
  getColumnLabel: (column: SubTableListColumnDTO) => string
  getMockValue: (field: SubTableListColumnDTO) => string
  t: TFn
}

/**
 * User Portal 双视图（To Do / My Requests）预览：分栏判定、内联表单（form below table）
 * 设计与只读 form-create 选项，以及预览弹层的行数据。
 */
export function usePortalPreview(options: UsePortalPreviewOptions) {
  const {
    props,
    viewColumns,
    isLinkColumn,
    isLookupColumn,
    getLinkText,
    getColumnLabel,
    getMockValue,
    t,
  } = options

  /** Read-only form-create option for the assignee "form below table" strip in dual list preview. */
  const inlineFormBelowDesign = computed(() =>
    resolveInlineFormBelowDesign({
      ownBindingId: props.binding.bindingId,
      ownRule: props.formRule || [],
      ownOption: props.formOption,
      columns: viewColumns.value,
      portalViews: props.portalViews,
      resolveSubTableFormDesign: props.resolveSubTableFormDesign,
    }),
  )

  const inlineFormPreviewOption = computed(() => {
    const saved = { ...((inlineFormBelowDesign.value.option || props.formOption || {}) as Record<string, unknown>) }
    delete saved.title
    return {
      showMsg: true,
      form: {
        labelPosition: 'left',
        labelWidth: '140px',
        disabled: true,
      },
      language: {
        en: {
          clickToUpload: t('form.clickToUpload'),
        },
      },
      ...saved,
      resetBtn: false,
      submitBtn: false,
    }
  })

  const dualPortalListPreview = computed(() => {
    const v = props.portalViews
    if (!v || typeof v !== 'object') return false
    const init = v.initiatorRequest
    if (init == null || init === 'mirrorTodo') return false
    return true
  })

  const assigneeTodoIsFormBelow = computed(() => props.portalViews?.assigneeTodo === 'formBelowTable')

  const initiatorIsSummary = computed(() => props.portalViews?.initiatorRequest === 'summaryWithLinkFormModal')

  const dualPreviewPanes = computed(() => [
    { key: 'todo' as const, title: t('form.portalViews.toDoDisplay') },
    { key: 'initiator' as const, title: t('form.portalViews.myRequestsDisplay') },
  ])

  function cellMockValue(col: SubTableListColumnDTO, pane: 'todo' | 'initiator'): string {
    if (isLinkColumn(col)) return getLinkText(col)
    if (isLookupColumn(col)) {
      if (pane === 'initiator' && initiatorIsSummary.value) {
        return t('subTableView.previewLookupSummaryCell')
      }
      return 'Lookup'
    }
    return getMockValue(col)
  }

  function buildPreviewColumns(pane: 'todo' | 'initiator'): PreviewColumn[] {
    return viewColumns.value.map(col => ({
      key: col.fieldName ?? String(col.componentId ?? Math.random()),
      label: getColumnLabel(col),
      mockValues: Array.from({ length: MOCK_ROW_COUNT }, () => cellMockValue(col, pane)),
    }))
  }

  const previewColumns = computed(() => buildPreviewColumns('todo'))

  const splitPreviewColumns = computed(() =>
    dualPortalListPreview.value
      ? { todo: buildPreviewColumns('todo'), myRequest: buildPreviewColumns('initiator') }
      : null
  )

  return {
    inlineFormBelowDesign,
    inlineFormPreviewOption,
    dualPortalListPreview,
    assigneeTodoIsFormBelow,
    initiatorIsSummary,
    dualPreviewPanes,
    previewColumns,
    splitPreviewColumns,
  }
}
