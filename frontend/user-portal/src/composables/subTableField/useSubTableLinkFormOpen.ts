import {
  collectNestedChildRowsFromPeerBindings,
  mergeSubTableRowsByRowId,
  pullNestedRowsForBindingFromParentRows
} from '@/composables/tasks/shared'
import type { Column, SubTableBinding, SubTableFieldEmit, SubTableFieldProps } from './subTableFieldTypes'
import {
  collapseMiLinkFormRowsForParent,
  filterLinkedChildRowsByMiTaskStatus,
  filterLinkedChildRowsByParentIdIdw,
  isMiStyleParentRowForLinkForm,
  isTerminalMiParticipantRow,
  parentChildTaskStatusesMatch
} from './subTableLinkFormRowMatch'
import {
  collectNestedSavedRowsForLinkForm,
  deepCollectLinkFormFieldRows,
  enrichLinkFormRowsFromNestedSubTables,
  linkFormBindingDef,
  linkFormRowsLackFormPayload,
  peerSubTableDataByFormFieldOverlap,
  scoreRowForLinkedFormFields
} from './subTableLinkFormFields'
import type { useSubTableLinkFormDialog } from './useSubTableLinkFormDialog'
import type { useSubTableLinkFormScope } from './useSubTableLinkFormScope'

/** Open flow for the Link Form detail modal: resolve the child rows that belong to the clicked parent row. */
export function useSubTableLinkFormOpen(
  props: SubTableFieldProps,
  emit: SubTableFieldEmit,
  dialog: ReturnType<typeof useSubTableLinkFormDialog>,
  scope: ReturnType<typeof useSubTableLinkFormScope>,
) {
  const {
    activeLinkColumn,
    activeLinkRowIndex,
    linkedSubTableRows,
    linkedFormData,
    linkFormDialogSnapshot,
    linkFormDialogVisible,
    resolveLinkBindingForColumn,
    resolveLinkedFallbackRows,
    buildLinkFormPeerMap,
    collectLinkFormRowsFromProcessVariables,
    buildLinkedFormData
  } = dialog
  const {
    filterLinkedChildRowsByParentAssignee,
    pickBestLinkedChildRowsForParentRow,
    filterLinkedChildRowsForParentRow,
    filterRowsByMiLinkFormParent,
    preferLinkedChildRowMatchingParent,
    strictChildRowsForParentByFk,
    promoteBestRowForLinkFormModal,
    miLinkFormChildRowMatchesParent,
    backfillMiLinkFormModalFieldsFromParent
  } = scope

  function handleLinkFormClick(col: Column, row: Record<string, any>, rowIndex: number) {
    if (props.linkFormClickScrollToInline) {
      emit('linkFormScrollToInline')
      return
    }
    activeLinkColumn.value = col
    activeLinkRowIndex.value = rowIndex
    const binding = resolveLinkBindingForColumn(col)
    const boundId = col.props?.boundSubTableBindingId
    const boundName = col.props?.boundSubTableName || binding?.tableName
    const rowSub = row?.__subTables__ && typeof row.__subTables__ === 'object' ? (row.__subTables__ as Record<string, any>) : {}
    const miIsolateTodo = !!(
      props.suppressLinkFormInitialData
      && row
      && isMiStyleParentRowForLinkForm(row as Record<string, unknown>)
    )
    let linkFormNestedOnlyMi = false
    let savedRows: any[] = []
    if (miIsolateTodo && binding) {
      let nestedOnly = collectNestedSavedRowsForLinkForm(rowSub, binding, boundId, boundName)
      if (nestedOnly.length > 1) {
        nestedOnly = collapseMiLinkFormRowsForParent(row, nestedOnly)
      }
      if (nestedOnly.length > 0) {
        linkFormNestedOnlyMi = true
        savedRows = filterLinkedChildRowsByParentAssignee(row, nestedOnly)
        if (savedRows.length > 1) {
          const picked = pickBestLinkedChildRowsForParentRow(row, savedRows, binding)
          if (picked.length > 0) savedRows = picked
        }
      }
    }
    if (!linkFormNestedOnlyMi) {
      savedRows = collectNestedSavedRowsForLinkForm(rowSub, binding, boundId, boundName)
      if (savedRows.length > 1) {
        savedRows = collapseMiLinkFormRowsForParent(row, savedRows)
      }
      if (binding && row) {
        const peerMap = buildLinkFormPeerMap()
        const fromParent = pullNestedRowsForBindingFromParentRows(
          linkFormBindingDef(binding),
          [row],
          peerMap.size > 0 ? peerMap : undefined
        )
        if (fromParent.length > 0) {
          savedRows = mergeSubTableRowsByRowId(savedRows, fromParent, binding.primaryKeyFields ?? null)
        }
        if (savedRows.length > 1) {
          const picked = pickBestLinkedChildRowsForParentRow(row, savedRows, binding)
          if (picked.length > 0) savedRows = picked
        }
      }
    }
    /**
     * My Request (read-only): parent row has no real nested link-form payload for this binding — do not merge in
     * binding-wide / peer fallback rows then "promote best", or another MI participant's values show in the modal.
     */
    const readOnlyIsolateLinkForm =
      !props.editable &&
      !isTerminalMiParticipantRow(row) &&
      (savedRows.length === 0 ||
        !!(binding?.formFields?.length && linkFormRowsLackFormPayload(savedRows, binding.formFields)))
    const baseFallbackRows = resolveLinkedFallbackRows(binding)
    const fallbackRows =
      baseFallbackRows.length > 0
        ? baseFallbackRows
        : peerSubTableDataByFormFieldOverlap(binding, props.linkedSubTableBindings ?? [])
    /**
     * MI 待办 `suppressLinkFormInitialData=true` 时仍优先用行内嵌套 `__subTables__`；
     * 若为空则回退到绑定数据（已在上层做多实例行隔离），并按父行主键收窄子表行，避免空白/错误。
     * 非 MI 行为不变：无 suppress 时仍可用全量 fallback + 父行过滤。
     */
    let effectiveSavedRows: any[] = []
    if (props.suppressLinkFormInitialData) {
      if (linkFormNestedOnlyMi) {
        effectiveSavedRows = [...savedRows]
      } else if (miIsolateTodo) {
        // Parent row has no nested slice: do not fall back to global __subTables__[90] (other participants).
        effectiveSavedRows = savedRows.length > 0 ? savedRows : []
      } else if (savedRows.length > 0) {
        effectiveSavedRows = savedRows
      } else if (fallbackRows.length > 0) {
        effectiveSavedRows = row
          ? filterLinkedChildRowsForParentRow(row, [...fallbackRows], binding)
          : [...fallbackRows]
      } else {
        effectiveSavedRows = []
      }
    } else if (readOnlyIsolateLinkForm && row) {
      let pool: any[] = savedRows.length > 0 ? [...savedRows] : []
      if (fallbackRows.length > 0) {
        let narrowed = filterLinkedChildRowsForParentRow(row, [...fallbackRows], binding)
        if (isMiStyleParentRowForLinkForm(row as Record<string, unknown>)) {
          narrowed = filterRowsByMiLinkFormParent(
            row,
            narrowed.length > 0 ? narrowed : fallbackRows,
            binding,
          )
        }
        if (narrowed.length > 0 && binding) {
          pool = mergeSubTableRowsByRowId(pool, narrowed, binding.primaryKeyFields ?? null)
        }
      }
      effectiveSavedRows = pool
    } else {
      effectiveSavedRows = savedRows.length > 0 ? savedRows : fallbackRows
      if (savedRows.length === 0 && effectiveSavedRows.length > 0 && row) {
        effectiveSavedRows = filterLinkedChildRowsForParentRow(row, effectiveSavedRows, binding)
      }
    }
    /**
     * Parent row.__subTables__[child] may be [{}] / assignee-only placeholders. That makes savedRows non-empty so we
     * never took binding.data fallback; merge in fallback so Link Form modal matches To Do / variables.
     */
    if (
      !miIsolateTodo &&
      !linkFormNestedOnlyMi &&
      binding?.formFields?.length &&
      fallbackRows.length > 0 &&
      effectiveSavedRows.length > 0 &&
      linkFormRowsLackFormPayload(effectiveSavedRows, binding.formFields)
    ) {
      effectiveSavedRows = mergeSubTableRowsByRowId(
        [...effectiveSavedRows],
        [...fallbackRows],
        binding.primaryKeyFields ?? null
      )
    }
    if (
      !miIsolateTodo &&
      !linkFormNestedOnlyMi &&
      effectiveSavedRows.length === 0 &&
      binding &&
      Array.isArray(props.linkedSubTableBindings) &&
      props.linkedSubTableBindings.length > 0
    ) {
      const nested = collectNestedChildRowsFromPeerBindings(
        binding,
        props.linkedSubTableBindings as SubTableBinding[],
        null
      )
      if (nested.length > 0) {
        effectiveSavedRows = row
          ? filterLinkedChildRowsForParentRow(row, [...nested], binding)
          : [...nested]
      }
    }

    /** My Request / read-only: child slice often lives only under this parent row's {@code __subTables__} (key variants), not in {@code binding.data}. */
    if (
      !linkFormNestedOnlyMi
      && binding
      && row
      && Array.isArray(props.linkedSubTableBindings)
      && props.linkedSubTableBindings.length > 0
    ) {
      const peerMap = new Map<number, number | null>()
      for (const b of props.linkedSubTableBindings) {
        const tid = b.tableId != null ? Number(b.tableId) : null
        if (tid != null && Number.isFinite(tid)) peerMap.set(Number(b.bindingId), tid)
      }
      const fromClickedParent = pullNestedRowsForBindingFromParentRows(
        {
          bindingId: Number(binding.bindingId),
          tableName: String(binding.tableName ?? ''),
          physicalTableName: (binding as { physicalTableName?: string }).physicalTableName,
          tableId: binding.tableId ?? null
        },
        [row],
        peerMap
      )
      if (fromClickedParent.length > 0) {
        effectiveSavedRows = mergeSubTableRowsByRowId(
          effectiveSavedRows.length > 0 ? [...effectiveSavedRows] : [],
          collapseMiLinkFormRowsForParent(row, fromClickedParent),
          binding.primaryKeyFields ?? null
        )
      }
    }

    const rowDataKeyCount = (r: unknown) =>
      r && typeof r === 'object' ? Object.keys(r as object).filter(k => !k.startsWith('__')).length : 0
    if (
      !miIsolateTodo &&
      binding &&
      effectiveSavedRows.length > 0 &&
      rowDataKeyCount(effectiveSavedRows[0]) <= 2 &&
      Array.isArray(props.linkedSubTableBindings) &&
      props.linkedSubTableBindings.length > 0
    ) {
      const nestedPeers = collectNestedChildRowsFromPeerBindings(
        binding,
        props.linkedSubTableBindings as SubTableBinding[],
        null
      )
      if (nestedPeers.length > 0) {
        effectiveSavedRows = mergeSubTableRowsByRowId(
          [...effectiveSavedRows],
          nestedPeers,
          binding.primaryKeyFields ?? null
        )
      }
    }

    if (row && binding && effectiveSavedRows.length > 0) {
      const idIdwScoped = filterLinkedChildRowsByParentIdIdw(row, effectiveSavedRows)
      if (idIdwScoped.length > 0) {
        const narrowed = isMiStyleParentRowForLinkForm(row as Record<string, unknown>)
          ? filterRowsByMiLinkFormParent(row, idIdwScoped, binding)
          : idIdwScoped
        if (narrowed.length > 0) {
          effectiveSavedRows = narrowed
        }
      }
    }

    if (!linkFormNestedOnlyMi && row && binding && effectiveSavedRows.length > 0) {
      const narrowed = filterLinkedChildRowsForParentRow(row, [...effectiveSavedRows], binding)
      if (narrowed.length > 0) effectiveSavedRows = narrowed
    }

    if (!props.editable && row && binding && effectiveSavedRows.length > 1) {
      effectiveSavedRows = preferLinkedChildRowMatchingParent(row, effectiveSavedRows, binding)
    }

    if (!props.editable && row && binding && effectiveSavedRows.length > 1) {
      const strict = strictChildRowsForParentByFk(row, effectiveSavedRows, binding)
      if (
        strict !== null
        && strict.length > 0
        && !linkFormRowsLackFormPayload(strict, binding.formFields)
      ) {
        effectiveSavedRows = strict
      }
    }

    if (
      !miIsolateTodo
      && !linkFormNestedOnlyMi
      && row
      && binding?.formFields?.length
      && (effectiveSavedRows.length === 0 || linkFormRowsLackFormPayload(effectiveSavedRows, binding.formFields))
    ) {
      const statusPool = filterLinkedChildRowsByMiTaskStatus(row, fallbackRows)
      const pkPool = filterLinkedChildRowsForParentRow(
        row,
        statusPool.length > 0 ? statusPool : fallbackRows,
        binding
      )
      if (savedRows.length > 0 && pkPool.length > 0) {
        effectiveSavedRows = mergeSubTableRowsByRowId(
          [...savedRows],
          pkPool,
          binding.primaryKeyFields ?? null
        )
      }
      if (
        effectiveSavedRows.length === 0
        || linkFormRowsLackFormPayload(effectiveSavedRows, binding.formFields)
      ) {
        const pool = mergeSubTableRowsByRowId(
          [],
          [...effectiveSavedRows, ...savedRows, ...fallbackRows],
          binding.primaryKeyFields ?? null
        )
        const pickPool = pool.length > 0 ? pool : [...effectiveSavedRows, ...savedRows, ...fallbackRows]
        const miPool = filterLinkedChildRowsByMiTaskStatus(row, pickPool)
        const scopedPool = filterLinkedChildRowsForParentRow(
          row,
          miPool.length > 0 ? miPool : pickPool,
          binding
        )
        const candidates =
          scopedPool.length > 0 ? scopedPool : (miPool.length > 0 ? miPool : pickPool)
        const best = pickBestLinkedChildRowsForParentRow(row, candidates, binding)
        if (best.length > 0) {
          const bestScore = scoreRowForLinkedFormFields(best[0], binding.formFields)
          const curScore =
            effectiveSavedRows.length > 0
              ? scoreRowForLinkedFormFields(effectiveSavedRows[0], binding.formFields)
              : 0
          const miOk =
            !isMiStyleParentRowForLinkForm(row as Record<string, unknown>)
            || parentChildTaskStatusesMatch(row, best[0])
            || !String((best[0] as { task_status?: unknown })?.task_status ?? '').trim()
          if (miOk && bestScore > curScore) effectiveSavedRows = best
        }
      }
    }

    /**
     * {@code buildLinkedFormData} only reads {@code data[0]}. For editable tables we promote so the richest row is first;
     * readonly + completed MI row must do the same when {@code effectiveSavedRows} still has multiple rows (placeholders
     * from other iterations often sort first). Readonly + non-terminal uses {@code readOnlyIsolateLinkForm} instead.
     */
    const shouldPromoteForReadonlyTerminal =
      !props.editable
      && !!binding?.formFields?.length
      && effectiveSavedRows.length > 1
      && isTerminalMiParticipantRow(row)
    if (
      binding?.formFields?.length
      && effectiveSavedRows.length > 1
      && (props.editable || shouldPromoteForReadonlyTerminal)
    ) {
      const pr = promoteBestRowForLinkFormModal(effectiveSavedRows, binding.formFields, row, binding)
      effectiveSavedRows = pr.rows
    }

    if (binding && effectiveSavedRows.length > 1 && isTerminalMiParticipantRow(row)) {
      effectiveSavedRows = collapseMiLinkFormRowsForParent(row, effectiveSavedRows)
    }
    if (binding && effectiveSavedRows.length > 0) {
      const peerMap = buildLinkFormPeerMap()
      if (!linkFormNestedOnlyMi) {
        effectiveSavedRows = enrichLinkFormRowsFromNestedSubTables(effectiveSavedRows, binding, peerMap)
      }
      if (
        !linkFormNestedOnlyMi
        && row
        && binding.formFields?.length
        && linkFormRowsLackFormPayload(effectiveSavedRows, binding.formFields)
      ) {
        const fromParent = pullNestedRowsForBindingFromParentRows(
          linkFormBindingDef(binding),
          [row],
          peerMap.size > 0 ? peerMap : undefined
        )
        if (fromParent.length > 0) {
          const parentScoped = filterLinkedChildRowsByMiTaskStatus(row, fromParent)
          const collapsed = collapseMiLinkFormRowsForParent(
            row,
            parentScoped.length > 0 ? parentScoped : fromParent
          )
          const merged = mergeSubTableRowsByRowId(
            [...effectiveSavedRows],
            collapsed,
            binding.primaryKeyFields ?? null
          )
          if (merged.length > 0) {
            effectiveSavedRows = pickBestLinkedChildRowsForParentRow(row, merged, binding)
          }
        }
        if (linkFormRowsLackFormPayload(effectiveSavedRows, binding.formFields)) {
          const deepRoots: unknown[] = [row?.__subTables__, ...effectiveSavedRows.map(r => r?.__subTables__)]
          for (const fb of fallbackRows) {
            deepRoots.push(fb?.__subTables__)
          }
          let deepHits: any[] = []
          for (const root of deepRoots) {
            deepHits = mergeSubTableRowsByRowId(
              deepHits,
              deepCollectLinkFormFieldRows(root, binding),
              binding.primaryKeyFields ?? null
            )
          }
          if (deepHits.length > 0) {
            const statusScoped = filterLinkedChildRowsByMiTaskStatus(row, deepHits)
            const scoped = filterLinkedChildRowsForParentRow(
              row,
              statusScoped.length > 0 ? statusScoped : deepHits,
              binding
            )
            const pickPool = scoped.length > 0 ? scoped : (statusScoped.length > 0 ? statusScoped : deepHits)
            const best = pickBestLinkedChildRowsForParentRow(row, pickPool, binding)
            if (best.length > 0) {
              const curScore = scoreRowForLinkedFormFields(effectiveSavedRows[0], binding.formFields)
              const bestScore = scoreRowForLinkedFormFields(best[0], binding.formFields)
              const stillLacking = linkFormRowsLackFormPayload(effectiveSavedRows, binding.formFields)
              if (bestScore > curScore || (stillLacking && bestScore > 0)) {
                effectiveSavedRows = best
              }
            }
          }
        }
      }
    }
    if (
      !props.editable
      && row
      && binding?.formFields?.length
      && linkFormRowsLackFormPayload(effectiveSavedRows, binding.formFields)
    ) {
      const fromVariables = collectLinkFormRowsFromProcessVariables(binding, row)
      if (fromVariables.length > 0) {
        const best = pickBestLinkedChildRowsForParentRow(row, fromVariables, binding)
        if (best.length > 0) {
          effectiveSavedRows = best
        }
      }
      if (linkFormRowsLackFormPayload(effectiveSavedRows, binding.formFields)) {
        const allPeerRows = (props.linkedSubTableBindings ?? []).flatMap(b =>
          Array.isArray(b.data) ? b.data : [],
        )
        const scoped = allPeerRows.filter(r =>
          miLinkFormChildRowMatchesParent(row as Record<string, unknown>, r, binding),
        )
        if (scoped.length > 0) {
          const best = pickBestLinkedChildRowsForParentRow(row, scoped, binding)
          if (best.length > 0) {
            effectiveSavedRows = best
          }
        }
      }
    }
    linkedSubTableRows.value = [...effectiveSavedRows]
    linkedFormData.value = buildLinkedFormData(
      { ...(binding || ({} as any)), data: effectiveSavedRows },
      { readonly: !props.editable },
    )
    if (row && binding?.formFields?.length && !linkFormNestedOnlyMi) {
      backfillMiLinkFormModalFieldsFromParent(
        linkedFormData.value,
        row as Record<string, unknown>,
        binding.formFields,
        effectiveSavedRows[0] as Record<string, unknown> | undefined,
        !props.editable,
      )
    }
    const bindingForFooter = resolveLinkBindingForColumn(col)
    const formFieldsLen = bindingForFooter?.formFields?.length ?? 0
    const useDetailFooter =
      !!props.showLinkFormDialogFooter &&
      props.editable &&
      bindingForFooter?.bindingMode === 'EDITABLE' &&
      formFieldsLen > 0
    if (useDetailFooter) {
      linkFormDialogSnapshot.value = {
        linkedFormData: JSON.parse(JSON.stringify(linkedFormData.value)) as Record<string, any>,
        linkedSubTableRows: JSON.parse(JSON.stringify(linkedSubTableRows.value)) as any[]
      }
    } else {
      linkFormDialogSnapshot.value = null
    }
    linkFormDialogVisible.value = true
  }

  return { handleLinkFormClick }
}
