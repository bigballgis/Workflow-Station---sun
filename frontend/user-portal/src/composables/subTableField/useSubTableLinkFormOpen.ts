import {
  collectNestedChildRowsFromPeerBindings,
  mergeSubTableRowsByRowId,
  pullNestedRowsForBindingFromParentRows
} from '@/composables/tasks/shared'
import type { Column, SubTableBinding, SubTableFieldProps } from './subTableFieldTypes'
import {
  collapseMiLinkFormRowsForParent,
  filterLinkedChildRowsByMiTaskStatus,
  filterLinkedChildRowsByParentIdIdw,
  isLinkFormBoundToHostGrid,
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

  function commitLinkFormDialogOpen(
    col: Column,
    row: Record<string, any> | undefined,
    binding: SubTableBinding | undefined,
    effectiveSavedRows: any[],
    skipParentBackfill: boolean,
  ) {
    linkedSubTableRows.value = [...effectiveSavedRows]
    linkedFormData.value = buildLinkedFormData(
      { ...(binding || ({} as SubTableBinding)), data: effectiveSavedRows },
      { readonly: !props.editable },
    )
    if (row && binding?.formFields?.length && !skipParentBackfill) {
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

  function handleLinkFormClick(col: Column, row: Record<string, any>, rowIndex: number) {
    activeLinkColumn.value = col
    activeLinkRowIndex.value = rowIndex
    const binding = resolveLinkBindingForColumn(col)
    if (row && isLinkFormBoundToHostGrid(col, props, binding)) {
      commitLinkFormDialogOpen(col, row, binding, [{ ...row }], true)
      return
    }
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
          // `savedRows` is this row's own nested slice for exactly this binding (collectNestedSavedRowsForLinkForm
          // above); `fromParent` was resolved by scanning ALL keys that could plausibly match this binding,
          // including a peer binding's own snapshot of the same shared table. The own slice is authoritative —
          // merge it in second so its fields win over a peer's possibly-stale values.
          savedRows = mergeSubTableRowsByRowId(fromParent, savedRows, binding.primaryKeyFields ?? null)
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
          // `narrowed` may include peer-binding rows for the same table_id (e.g. another form's
          // snapshot of this participant). `pool` is this binding's own nested payload for the
          // clicked row and is authoritative — merge it in second so its non-empty fields win over
          // a peer's possibly-stale values instead of being overwritten by them.
          pool = mergeSubTableRowsByRowId(narrowed, pool, binding.primaryKeyFields ?? null)
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
      // effectiveSavedRows may be incomplete (e.g. missing one field), but any field it does have is
      // this row's own data and must win over fallbackRows (binding-wide / peer data) filling gaps.
      effectiveSavedRows = mergeSubTableRowsByRowId(
        [...fallbackRows],
        [...effectiveSavedRows],
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
        // Same rule as above: effectiveSavedRows already resolved down to this binding's own scope;
        // fromClickedParent was pulled by scanning any key that could match this binding, which can
        // include a peer binding's snapshot of the same shared table. Merge the own data in last so
        // it wins over a peer's possibly-stale values instead of being unconditionally overwritten.
        effectiveSavedRows = mergeSubTableRowsByRowId(
          collapseMiLinkFormRowsForParent(row, fromClickedParent),
          effectiveSavedRows.length > 0 ? [...effectiveSavedRows] : [],
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
        // effectiveSavedRows is thin here (<=2 keys) but whatever it does have is this row's own
        // data — still let it win over nestedPeers (other bindings' rows for the same table).
        effectiveSavedRows = mergeSubTableRowsByRowId(
          nestedPeers,
          [...effectiveSavedRows],
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
        // savedRows is this row's own nested slice for this binding; pkPool comes from fallbackRows
        // (binding-wide / peer data) narrowed by parent — savedRows must win where both have a value.
        effectiveSavedRows = mergeSubTableRowsByRowId(
          pkPool,
          [...savedRows],
          binding.primaryKeyFields ?? null
        )
      }
      if (
        effectiveSavedRows.length === 0
        || linkFormRowsLackFormPayload(effectiveSavedRows, binding.formFields)
      ) {
        // Own data first (effectiveSavedRows, savedRows), fallbackRows (peer/binding-wide) last so
        // mergeSubTableRowsByRowId's later-wins-for-non-empty-fields rule favors the row's own data.
        const pool = mergeSubTableRowsByRowId(
          [...fallbackRows],
          [...savedRows, ...effectiveSavedRows],
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
          // effectiveSavedRows is this row's own accumulated data; collapsed comes from fromParent,
          // which scans any key that could match this binding, including a peer binding's snapshot —
          // own data must win where both have a value for the same field.
          const merged = mergeSubTableRowsByRowId(
            collapsed,
            [...effectiveSavedRows],
            binding.primaryKeyFields ?? null
          )
          if (merged.length > 0) {
            effectiveSavedRows = pickBestLinkedChildRowsForParentRow(row, merged, binding)
          }
        }
        if (linkFormRowsLackFormPayload(effectiveSavedRows, binding.formFields)) {
          // Peer/fallback roots first, own row + own accumulated data last, so that if the same PK
          // shows up under both a peer root and an own root, the own root's fields win within deepHits.
          const deepRoots: unknown[] = fallbackRows.map(fb => fb?.__subTables__)
          deepRoots.push(row?.__subTables__, ...effectiveSavedRows.map(r => r?.__subTables__))
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
      // Both candidate pools below are scanned process-/peer-wide (not scoped to this exact binding),
      // so a same-shaped peer binding's row can score as a plausible match. Merge into effectiveSavedRows
      // (own data wins per-field) instead of replacing it outright, and only when the candidate actually
      // scores higher — a same-or-lower-scoring "best" is not worth risking a wholesale replacement.
      const curScoreBefore = effectiveSavedRows.length > 0
        ? scoreRowForLinkedFormFields(effectiveSavedRows[0], binding.formFields)
        : 0
      const fromVariables = collectLinkFormRowsFromProcessVariables(binding, row)
      if (fromVariables.length > 0) {
        const best = pickBestLinkedChildRowsForParentRow(row, fromVariables, binding)
        if (best.length > 0 && scoreRowForLinkedFormFields(best[0], binding.formFields) > curScoreBefore) {
          effectiveSavedRows = effectiveSavedRows.length > 0
            ? mergeSubTableRowsByRowId(best, [...effectiveSavedRows], binding.primaryKeyFields ?? null)
            : best
        }
      }
      if (linkFormRowsLackFormPayload(effectiveSavedRows, binding.formFields)) {
        const curScoreNow = effectiveSavedRows.length > 0
          ? scoreRowForLinkedFormFields(effectiveSavedRows[0], binding.formFields)
          : 0
        const allPeerRows = (props.linkedSubTableBindings ?? []).flatMap(b =>
          Array.isArray(b.data) ? b.data : [],
        )
        const scoped = allPeerRows.filter(r =>
          miLinkFormChildRowMatchesParent(row as Record<string, unknown>, r, binding),
        )
        if (scoped.length > 0) {
          const best = pickBestLinkedChildRowsForParentRow(row, scoped, binding)
          if (best.length > 0 && scoreRowForLinkedFormFields(best[0], binding.formFields) > curScoreNow) {
            effectiveSavedRows = effectiveSavedRows.length > 0
              ? mergeSubTableRowsByRowId(best, [...effectiveSavedRows], binding.primaryKeyFields ?? null)
              : best
          }
        }
      }
    }
    commitLinkFormDialogOpen(col, row, binding, effectiveSavedRows, linkFormNestedOnlyMi)
  }

  return { handleLinkFormClick }
}
