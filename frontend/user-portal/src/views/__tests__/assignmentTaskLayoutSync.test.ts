/**
 * Regression: assignment task (Activity_0hwtl8v) load pipeline must not throw
 * "n.has is not a function" during sub-table layout sync / hydration.
 *
 * Fixtures live in ./fixtures (captured 2026-07 from the dev-seed
 * Multi-Instance Subtask Demo FU, key fu-20260422-23tfag): the Assign Task form
 * places sub-table bindings 50066 (Participants, tableId 50020) and 50103
 * (attachment), while the initiator Main form binds the same tableId 50020 as
 * binding 50064 — the engine stores MI collection rows under the legacy alias
 * key "64", so binding 50066 must resolve its rows via the sibling binding id.
 * (The original capture used pre-reseed ids 66/103/64; only the raw id constants
 * changed.) Previously these fixtures were read from os.tmpdir() dumps that only
 * existed on the capturing machine, so the suite failed everywhere else.
 */
import { describe, expect, it } from 'vitest'
import fuContentFixture from './fixtures/kk-assign-task-fu-content.json'
import taskVariablesFixture from './fixtures/kk-assign-task-variables.json'
import {
  collectPlacedSubTableBindingIds,
  collectSubTableFieldsFromLayout,
  ensureSubTableBindingsOnFormLayout,
  extractFieldsRecursive,
  legacyBindingIdAliases,
  mergeMissingSubTableFieldsIntoLayout,
  parseFormRulesLayout,
} from '@/components/formRendererHelpers'
import {
  applySharedAttachmentFinalizeAndMaterialize,
  buildBindingIdToRelationTableIdMap,
  coerceSubTablesVariableToMap,
  enrichChildBindingRowsFromParentsNestedSubTables,
  finalizeSharedProcessSubTableBindingRows,
  flattenNestedSubTableRowsIntoPayload,
  hydrateBindingsRowsFromVariablesBySharedRelationTableId,
  hydrateChildSubTablesFromParentsNestedRows,
  isMiDashboardSubTableBinding,
  mergeAllSlicesForSharedProcessSubTableBinding,
  resolveSubTableRowsForBinding,
} from '@/composables/tasks/shared'

function loadFuContent(): any {
  return fuContentFixture as any
}

function loadTaskVariables(): Record<string, unknown> {
  return (taskVariablesFixture as any).variables as Record<string, unknown>
}

describe('assignment task layout sync (Process_1_KK / Activity_0hwtl8v)', () => {
  it('hydration + layout sync pipeline completes without TypeError', () => {
    const content = loadFuContent()
    const variables = loadTaskVariables()
    const assignForm = content.forms.find((f: { name: string }) => f.name === 'Assign Task')
    expect(assignForm).toBeTruthy()

    const cfg = typeof assignForm.data === 'string' ? JSON.parse(assignForm.data) : assignForm.data
    const layout = parseFormRulesLayout(cfg.rule, items => extractFieldsRecursive(items))
    const fuSubTables = collectSubTableFieldsFromLayout(layout.fields, layout.tabs, layout.fieldsAfterTabs)
    const placed = collectPlacedSubTableBindingIds(layout.fields, layout.tabs, layout.fieldsAfterTabs)
    expect(placed.has(50066)).toBe(true)
    expect(placed.has(50103)).toBe(true)

    const nativeIds = (assignForm.tableBindings || [])
      .filter((b: { bindingType: string }) => b.bindingType !== 'PRIMARY')
      .map((b: { bindingId: number }) => Number(b.bindingId))
      .filter((n: number) => Number.isFinite(n))

    const bindings: any[] = []
    for (const b of assignForm.tableBindings || []) {
      if (b.bindingType === 'PRIMARY') continue
      bindings.push({
        bindingId: b.bindingId,
        tableId: b.tableId ?? null,
        bindingType: b.bindingType,
        bindingMode: b.bindingMode,
        foreignKeyField: b.foreignKeyField ?? null,
        // 生产的 binding 构建都透传它 —— MI collection 的判据就是它，漏传会判成非 MI。
        bindingLinkMode: b.bindingLinkMode ?? null,
        fieldDefinitions: b.fieldDefinitions ?? null,
        tableName: b.tableDisplayName || b.tableName,
        designerTableName: b.tableName,
        columns:
          cfg.subListViews?.[String(b.bindingId)]?.columns?.map((c: { fieldName: string; displayName?: string }) => ({
            field: c.fieldName,
            label: c.displayName || c.fieldName,
            type: 'text',
          })) ?? [],
        primaryKeyFields: b.primaryKeyFields ?? null,
        data: [],
      })
    }

    const rtMap = buildBindingIdToRelationTableIdMap(content.forms)
    const savedMap = coerceSubTablesVariableToMap(variables.__subTables__)
    expect(savedMap).toBeTruthy()
    const flattened = structuredClone(savedMap) as Record<string, unknown>
    flattenNestedSubTableRowsIntoPayload(flattened)

    for (const binding of bindings) {
      const saved = resolveSubTableRowsForBinding(flattened, binding, {
        bindingTableById: rtMap,
        mergeSiblingSlices: isMiDashboardSubTableBinding(binding),
      })
      if (saved?.length) binding.data = [...saved]
    }

    hydrateChildSubTablesFromParentsNestedRows(bindings, flattened, rtMap)
    hydrateBindingsRowsFromVariablesBySharedRelationTableId(bindings, flattened, rtMap)
    enrichChildBindingRowsFromParentsNestedSubTables(bindings)

    for (const binding of bindings) {
      if (isMiDashboardSubTableBinding(binding)) {
        const merged = mergeAllSlicesForSharedProcessSubTableBinding(flattened, binding, rtMap)
        if (merged.length > 0) binding.data = merged
      } else if (Array.isArray(binding.data)) {
        binding.data = finalizeSharedProcessSubTableBindingRows(binding.data, binding)
      }
    }
    applySharedAttachmentFinalizeAndMaterialize(bindings, variables, {
      flattened,
      bindingTableById: rtMap,
    })

    // binding 50066 should resolve rows from sibling slice keyed "64" (legacy alias of the
    // initiator Main form's binding 50064, same tableId), incl. assignee snapshots.
    const subTask = bindings.find(b => b.bindingId === 50066)
    expect(subTask?.data?.length).toBe(3)
    expect(subTask?.data?.[0]?.assignee).toBeTruthy()
    expect(subTask?.data?.[0]?.assignee_display_name).toBe('Developer Tester')

    // taskNativeSubTableBindings filter logic
    const nativeIdSet = new Set(nativeIds.map(Number).filter(Number.isFinite))
    const taskNative = bindings.filter(b => {
      if (b.bindingType === 'PRIMARY') return false
      const bid = Number(b.bindingId)
      const isNative =
        nativeIdSet.has(bid) || legacyBindingIdAliases(bid).some(alias => nativeIdSet.has(alias))
      const isPlaced = legacyBindingIdAliases(bid).some(alias => placed.has(alias))
      if (!isNative && !isPlaced) return false
      return (b.columns?.length ?? 0) > 0 || (b.data?.length ?? 0) > 0
    })
    expect(taskNative.map((b: { bindingId: number }) => b.bindingId).sort((a, c) => a - c)).toEqual([50066, 50103])

    // syncFormLayoutWithSubTableBindings equivalent
    const layoutBuckets = {
      fields: [...layout.fields],
      tabs: [...layout.tabs],
      fieldsAfterTabs: [...layout.fieldsAfterTabs],
    }
    const activeIds = new Set(bindings.map((b: { bindingId: number }) => Number(b.bindingId)).filter(Number.isFinite))
    mergeMissingSubTableFieldsIntoLayout(layoutBuckets, fuSubTables, activeIds)
    ensureSubTableBindingsOnFormLayout(layoutBuckets, bindings, cfg)

    const placedAfter = collectPlacedSubTableBindingIds(
      layoutBuckets.fields,
      layoutBuckets.tabs,
      layoutBuckets.fieldsAfterTabs,
    )
    // Design parity: Sub Task / Attachment stay inside Title card in FormRenderer layout
    expect(placedAfter.has(50066)).toBe(true)
    expect(placedAfter.has(50103)).toBe(true)
  })
})
