/**
 * ACTION form canvas rule — which `rule` array Preview (and Portal FORM_POPUP) must render.
 *
 * Counterpart: frontend/user-portal/src/composables/taskDetail/useTaskDetailPopup.ts
 * (`preparePopupContext` ACTION branch). Keep both in lockstep: ACTION forms design
 * fields on the ACTION binding canvas (`configJson.subForms[bindingId].rule`); the
 * top-level `rule` stays empty / PRIMARY-bound and is not the authored popup.
 *
 * Prefer a non-empty ACTION canvas; fall back to top-level rule for legacy ACTION
 * forms saved before the ACTION-binding canvas existed.
 */

export interface ActionFormCanvasBinding {
  id?: number | null
  bindingId?: number | null
  bindingType?: string | null
  tableId?: number | null
}

export interface ResolveActionFormCanvasRuleInput {
  formType?: string | null
  tableBindings?: ActionFormCanvasBinding[] | null
  topLevelRule?: unknown
  subForms?: Record<string, { rule?: unknown } | undefined> | null
}

export interface ActionFormCanvasRuleResult {
  rule: unknown[]
  actionBindingId: number | null
  usedActionCanvas: boolean
}

export interface ActionCanvasBindingMapEntry<TField = unknown> {
  bindingType?: string
  rule?: unknown[]
  fieldDefinitions?: TField[]
  tableId?: number
}

function asRuleArray(value: unknown): unknown[] {
  return Array.isArray(value) ? value : []
}

function isActionBindingType(bindingType: string | null | undefined): boolean {
  return String(bindingType ?? '').toUpperCase() === 'ACTION'
}

function isPrimaryBindingType(bindingType: string | null | undefined): boolean {
  return String(bindingType ?? '').toUpperCase() === 'PRIMARY'
}

function actionBindingNumericId(binding: ActionFormCanvasBinding): number | null {
  const raw = binding.id ?? binding.bindingId
  if (raw == null) return null
  const id = Number(raw)
  return Number.isFinite(id) ? id : null
}

function subFormRule(
  subForms: Record<string, { rule?: unknown } | undefined> | null | undefined,
  bindingId: number,
): unknown[] {
  const entry = subForms?.[String(bindingId)]
  return asRuleArray(entry?.rule)
}

export function resolveActionFormCanvasRule(
  input: ResolveActionFormCanvasRuleInput,
): ActionFormCanvasRuleResult {
  const top = asRuleArray(input.topLevelRule)
  if (String(input.formType ?? '').toUpperCase() !== 'ACTION') {
    return { rule: top, actionBindingId: null, usedActionCanvas: false }
  }

  const action = (input.tableBindings ?? []).find((b) => isActionBindingType(b.bindingType))
  const actionBindingId = action ? actionBindingNumericId(action) : null
  if (actionBindingId == null) {
    return { rule: top, actionBindingId: null, usedActionCanvas: false }
  }

  const canvas = subFormRule(input.subForms, actionBindingId)
  if (canvas.length > 0) {
    return { rule: canvas, actionBindingId, usedActionCanvas: true }
  }
  return { rule: top, actionBindingId, usedActionCanvas: false }
}

/**
 * Overlay live designer ACTION rules onto saved subForms (Preview live-edit).
 * A live array — including `[]` — replaces saved fields. Missing `rule` leaves saved intact.
 */
export function overlayActionBindingRulesOnSubForms(
  subForms: Record<string, { rule?: unknown } | undefined> | null | undefined,
  bindingMap: Map<number, { bindingType?: string; rule?: unknown[] }>,
): Record<string, { rule?: unknown }> {
  const out: Record<string, { rule?: unknown }> = { ...(subForms ?? {}) }
  for (const [id, entry] of bindingMap) {
    // Skip empty live arrays: fc-designer getRule() is [] before hydration, which must not
    // wipe saved ACTION fields (Preview can open while the canvas is still mounting).
    if (!isActionBindingType(entry.bindingType) || !Array.isArray(entry.rule) || entry.rule.length === 0) continue
    const key = String(id)
    out[key] = { ...out[key], rule: entry.rule }
  }
  return out
}

/** Table whose fields Preview / FK-PK runtime must treat as the canvas table. */
export function selectPreviewCanvasTableBinding<T extends ActionFormCanvasBinding>(input: {
  tableBindings?: T[] | null
  usedActionCanvas: boolean
  actionBindingId: number | null
}): T | null {
  const bindings = input.tableBindings ?? []
  if (input.usedActionCanvas) {
    const byId = bindings.find((b) => actionBindingNumericId(b) === input.actionBindingId)
    if (byId) return byId
    const byType = bindings.find((b) => isActionBindingType(b.bindingType))
    if (byType) return byType
  }
  const primary = bindings.find((b) => isPrimaryBindingType(b.bindingType))
  if (primary) return primary
  return bindings.find((b) => isActionBindingType(b.bindingType)) ?? null
}

export function applyActionFormCanvasToPreview<TField>(input: {
  formType?: string | null
  tableBindings?: ActionFormCanvasBinding[] | null
  topLevelRule?: unknown
  savedSubForms?: Record<string, { rule?: unknown } | undefined> | null
  bindingMap: Map<number, ActionCanvasBindingMapEntry<TField>>
  primaryFieldDefs: TField[]
  getTableFieldDefinitions?: (tableId: number) => TField[]
}): {
  rule: unknown[]
  usedActionCanvas: boolean
  actionBindingId: number | null
  fieldDefs: TField[]
} {
  const resolved = resolveActionFormCanvasRule({
    formType: input.formType,
    tableBindings: input.tableBindings,
    topLevelRule: input.topLevelRule,
    subForms: overlayActionBindingRulesOnSubForms(input.savedSubForms, input.bindingMap),
  })
  if (!resolved.usedActionCanvas) {
    return {
      rule: asRuleArray(input.topLevelRule),
      usedActionCanvas: false,
      actionBindingId: resolved.actionBindingId,
      fieldDefs: input.primaryFieldDefs,
    }
  }
  return {
    rule: asRuleArray(resolved.rule),
    usedActionCanvas: true,
    actionBindingId: resolved.actionBindingId,
    fieldDefs: pickActionCanvasFieldDefs(resolved.actionBindingId, input),
  }
}

function pickActionCanvasFieldDefs<TField>(
  actionBindingId: number | null,
  input: {
    bindingMap: Map<number, ActionCanvasBindingMapEntry<TField>>
    primaryFieldDefs: TField[]
    getTableFieldDefinitions?: (tableId: number) => TField[]
  },
): TField[] {
  const actionEntry = actionBindingId != null ? input.bindingMap.get(actionBindingId) : undefined
  if (actionEntry?.fieldDefinitions?.length) return actionEntry.fieldDefinitions
  if (actionEntry?.tableId != null && input.getTableFieldDefinitions) {
    return input.getTableFieldDefinitions(actionEntry.tableId)
  }
  return input.primaryFieldDefs
}
