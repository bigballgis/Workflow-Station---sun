/**
 * Multi-instance sub-process scope from Developer Workstation Process Design (BPMN extensions).
 * Uses configured Sub-table name + designer primary key — not hard-coded column names.
 */

import { getCachedBpmnDocument } from '@/utils/bpmnParseCache'

function rowHasAttachmentFile(row: unknown): boolean {
  if (!row || typeof row !== 'object') return false
  const rec = row as Record<string, unknown>
  const file = rec.file
  if (file == null || file === '') return false
  if (typeof file === 'string') return file.trim().length > 0
  if (Array.isArray(file)) return file.length > 0
  if (typeof file === 'object') return Object.keys(file as object).length > 0
  return true
}

function normalizeSubTableNameLocal(name?: string): string {
  return String(name || '').trim().toLowerCase()
}

function stripLinkFormDesignerTableLabelLocal(raw?: string): string {
  return String(raw || '').trim().replace(/^ADD\s*\+\s*/i, '').trim()
}

function compactTableKey(name?: string | null): string {
  return normalizeSubTableNameLocal(stripLinkFormDesignerTableLabelLocal(String(name || ''))).replace(/\s+/g, '')
}

export interface MiSubProcessScopeConfig {
  /** Physical / designer table name from BPMN {@code subTableName} (e.g. HMDC_Transaction). */
  subTableName: string
  /** BPMN {@code assigneeField} on the MI user task (e.g. assignee_id). */
  assigneeField: string | null
  /** JUEL path for current collection row id (default currentItem.rowId). */
  rowIdVariable: string
  /** Sub-process extension {@code miTaskStatusField}. */
  miTaskStatusField: string | null
  /** Sub-process extension {@code miTaskCurrentNodeField}. */
  miTaskCurrentNodeField: string | null
  /** Flowable collection variable (e.g. multiInstance_HMDC_Transaction_collection). */
  collectionVariable: string | null
  /** Flowable element variable (e.g. currentItem). */
  elementVariable: string | null
}

export type MiParticipantRowId = string | number

export type SubTableBindingLike = {
  bindingId?: number | string
  tableName?: string
  physicalTableName?: string
  primaryKeyFields?: string[] | null
  columns?: Array<{ field?: string }> | null
  foreignKeyField?: string | null
}

/** Flowable {@code _currentItem.rowId} — numeric or string (UUID) primary key value. */
export function normalizeMiParticipantRowId(raw: unknown): MiParticipantRowId | null {
  if (raw == null) return null
  if (typeof raw === 'number' && !Number.isNaN(raw)) return raw
  if (typeof raw === 'string') {
    const s = raw.trim()
    return s.length > 0 ? s : null
  }
  const s = String(raw).trim()
  return s.length > 0 ? s : null
}

export function hasConfiguredPrimaryKeyFields(primaryKeyFields?: string[] | null): boolean {
  return (primaryKeyFields ?? []).some(f => String(f).trim().length > 0)
}

export function describeSubTableBindingLabel(binding: SubTableBindingLike): string {
  return (
    binding.physicalTableName?.trim() ||
    binding.tableName?.trim() ||
    (binding.bindingId != null ? String(binding.bindingId) : '')
  )
}

const localName = (el: Element): string => el.localName || el.nodeName.split(':').pop() || ''

/** Compare binding labels to BPMN {@code subTableName}. */
export function bindingMatchesMiSubTableName(
  binding: SubTableBindingLike,
  subTableName: string | null | undefined,
): boolean {
  if (!subTableName || !String(subTableName).trim()) return false
  const want = compactTableKey(subTableName)
  if (!want) return false
  const candidates = [binding.physicalTableName, binding.tableName].filter(Boolean) as string[]
  return candidates.some(c => compactTableKey(c) === want)
}

export function findBindingForMiSubTableName<T extends SubTableBindingLike>(
  bindings: T[],
  subTableName: string | null | undefined,
): T | undefined {
  if (!subTableName) return undefined
  return bindings.find(b => bindingMatchesMiSubTableName(b, subTableName))
}

/** Compare sub-table cell value to MI participant row id (numeric or string / UUID). */
export function miParticipantRowIdsEqual(a: unknown, b: MiParticipantRowId): boolean {
  if (a == null || a === '') return false
  const bs = String(b).trim()
  if (bs !== '' && String(a).trim() === bs) return true
  const an = Number(a)
  const bn = Number(b)
  return !Number.isNaN(an) && !Number.isNaN(bn) && an === bn
}

/**
 * Match a sub-table row to a MI participant id using designer {@code primaryKeyFields} only.
 * When PK metadata is missing, returns {@code false} — callers must surface a configuration error.
 */
export function rowMatchesSubTablePrimaryKey(
  row: unknown,
  participantRowId: MiParticipantRowId,
  primaryKeyFields?: string[] | null,
): boolean {
  if (!row || typeof row !== 'object') return false
  const rec = row as Record<string, unknown>
  const pks = (primaryKeyFields ?? []).map(f => String(f).trim()).filter(Boolean)
  if (pks.length === 0) return false

  if (pks.length === 1) {
    return miParticipantRowIdsEqual(subTableRowPkValue(rec, pks[0]!), participantRowId)
  }
  const parts = String(participantRowId).split('|').map(s => s.trim())
  if (parts.length === pks.length) {
    return pks.every((pk, i) => miParticipantRowIdsEqual(subTableRowPkValue(rec, pk), parts[i]!))
  }
  return pks.every(pk => miParticipantRowIdsEqual(subTableRowPkValue(rec, pk), participantRowId))
}

/** Match MI participant row via designer {@code primaryKeyFields} (no legacy column fallbacks). */
export function expansionKeyMatchesParticipantRow(
  row: unknown,
  myRowId: MiParticipantRowId,
  primaryKeyFields?: string[] | null,
): boolean {
  return rowMatchesSubTablePrimaryKey(row, myRowId, primaryKeyFields)
}

function readExtensionProperties(el: Element): Record<string, string> {
  const out: Record<string, string> = {}
  const props = el.getElementsByTagName('*')
  for (let i = 0; i < props.length; i++) {
    const p = props[i]!
    const ln = localName(p)
    if (ln !== 'property' && ln !== 'values') continue
    const n = p.getAttribute('name')
    const v = p.getAttribute('value')
    if (n && v != null && v !== '') out[n] = v
  }
  return out
}

function elementHasMultiInstanceLoop(el: Element): boolean {
  const desc = el.getElementsByTagName('*')
  for (let i = 0; i < desc.length; i++) {
    if (localName(desc[i]!) === 'multiInstanceLoopCharacteristics') return true
  }
  return false
}

function findAncestorSubProcess(from: Element): Element | null {
  let node: Node | null = from.parentNode
  while (node && node.nodeType === 1) {
    const wrap = node as Element
    if (localName(wrap) === 'subProcess') return wrap
    if (localName(wrap) === 'process' || localName(wrap) === 'definitions') break
    node = wrap.parentNode
  }
  return null
}

function findUserTaskByRef(doc: Document, userTaskId?: string, userTaskName?: string): Element | null {
  const idTrim = String(userTaskId ?? '').trim()
  const nameNorm = String(userTaskName ?? '').trim().replace(/\s+/g, ' ')
  const all = doc.getElementsByTagName('*')
  for (let i = 0; i < all.length; i++) {
    const el = all[i]!
    if (localName(el) !== 'userTask') continue
    const uid = (el.getAttribute('id') || '').trim()
    const uname = (el.getAttribute('name') || '').trim().replace(/\s+/g, ' ')
    if (idTrim && uid === idTrim) return el
    if (nameNorm && uname === nameNorm) return el
  }
  return null
}

function firstSubTableNameInSubProcess(sp: Element): string | null {
  const desc = sp.getElementsByTagName('*')
  for (let i = 0; i < desc.length; i++) {
    const el = desc[i]!
    if (localName(el) !== 'userTask') continue
    const props = readExtensionProperties(el)
    const st = props.subTableName?.trim()
    if (st) return st
  }
  return null
}

function readMiLoopAttributes(sp: Element): { collection: string | null; elementVariable: string | null } {
  const desc = sp.getElementsByTagName('*')
  for (let i = 0; i < desc.length; i++) {
    const el = desc[i]!
    if (localName(el) !== 'multiInstanceLoopCharacteristics') continue
    return {
      collection: el.getAttribute('flowable:collection') || el.getAttribute('collection') || null,
      elementVariable: el.getAttribute('flowable:elementVariable') || el.getAttribute('elementVariable') || null,
    }
  }
  return { collection: null, elementVariable: null }
}

function buildScopeFromElements(userTaskEl: Element, miSubProcess: Element): MiSubProcessScopeConfig | null {
  const taskProps = readExtensionProperties(userTaskEl)
  const spProps = readExtensionProperties(miSubProcess)
  const subTableName = (taskProps.subTableName || firstSubTableNameInSubProcess(miSubProcess) || '').trim()
  if (!subTableName) return null

  const loop = readMiLoopAttributes(miSubProcess)
  const rowIdVar = (taskProps.rowIdVariable || 'currentItem.rowId').trim()

  return {
    subTableName,
    assigneeField: taskProps.assigneeField?.trim() || null,
    rowIdVariable: rowIdVar,
    miTaskStatusField: spProps.miTaskStatusField?.trim() || null,
    miTaskCurrentNodeField: spProps.miTaskCurrentNodeField?.trim() || null,
    collectionVariable: loop.collection,
    elementVariable: loop.elementVariable,
  }
}

/**
 * Resolve MI collection scope for the active user task from deployed BPMN (Process Design extensions).
 */
export function resolveMiSubProcessScopeFromBpmn(
  xml: string | null | undefined,
  options?: { userTaskId?: string | null; userTaskName?: string | null },
): MiSubProcessScopeConfig | null {
  if (!xml) return null
  try {
    const doc = getCachedBpmnDocument(xml)
    if (!doc) return null

    const userTaskEl = findUserTaskByRef(
      doc,
      options?.userTaskId ?? undefined,
      options?.userTaskName ?? undefined,
    )

    if (userTaskEl) {
      const sp = findAncestorSubProcess(userTaskEl)
      if (sp && elementHasMultiInstanceLoop(sp)) {
        return buildScopeFromElements(userTaskEl, sp)
      }
    }

    // Running process on My Request: current node name only — pick first MI subProcess whose inner task matches or any MI scope.
    const all = doc.getElementsByTagName('*')
    for (let i = 0; i < all.length; i++) {
      const el = all[i]!
      if (localName(el) !== 'subProcess' || !elementHasMultiInstanceLoop(el)) continue
      const innerTasks: Element[] = []
      const desc = el.getElementsByTagName('*')
      for (let j = 0; j < desc.length; j++) {
        if (localName(desc[j]!) === 'userTask') innerTasks.push(desc[j]!)
      }
      if (innerTasks.length === 0) continue
      const nameNorm = String(options?.userTaskName ?? '').trim().replace(/\s+/g, ' ')
      const matchTask =
        (nameNorm
          ? innerTasks.find(t => (t.getAttribute('name') || '').trim().replace(/\s+/g, ' ') === nameNorm)
          : null) ?? innerTasks[0]!
      return buildScopeFromElements(matchTask, el)
    }
  } catch {
    /* ignore parse errors */
  }
  return null
}

function readJuelPath(obj: Record<string, unknown>, path: string): unknown {
  const parts = path.split('.').filter(Boolean)
  let cur: unknown = obj
  for (const p of parts) {
    if (!cur || typeof cur !== 'object') return undefined
    cur = (cur as Record<string, unknown>)[p]
  }
  return cur
}

/** Case-insensitive field read on sub-table / Flowable collection rows. */
export function getSubTableRowValueIgnoreCase(
  row: Record<string, unknown>,
  field: string,
): unknown {
  if (Object.prototype.hasOwnProperty.call(row, field)) return row[field]
  const fl = field.toLowerCase()
  for (const k of Object.keys(row)) {
    if (k.toLowerCase() === fl) return row[k]
  }
  return undefined
}

/** PK column value from row envelope and/or nested {@code rowKey} (aligns with backend rowKeyFromVariableRow). */
export function subTableRowPkValue(row: Record<string, unknown>, pkField: string): unknown {
  let v = getSubTableRowValueIgnoreCase(row, pkField)
  if (v != null && String(v).trim() !== '') return v
  const rk = row.rowKey
  if (rk && typeof rk === 'object' && !Array.isArray(rk)) {
    v = getSubTableRowValueIgnoreCase(rk as Record<string, unknown>, pkField)
  }
  if (v != null && String(v).trim() !== '') return v
  if (pkField.toLowerCase() !== 'rowid') {
    v = getSubTableRowValueIgnoreCase(row, 'rowId')
  }
  return v
}

/**
 * Build MI participant row id from a PK map using designer column order.
 * Single PK → scalar; composite → {@code v1|v2|...}.
 */
export function participantRowIdFromPkMap(
  map: Record<string, unknown>,
  primaryKeyFields: string[],
): MiParticipantRowId | null {
  const pks = primaryKeyFields.map(f => String(f).trim()).filter(Boolean)
  if (pks.length === 0) return null

  if (pks.length === 1) {
    return normalizeMiParticipantRowId(subTableRowPkValue(map, pks[0]!))
  }

  const parts: string[] = []
  for (const pk of pks) {
    const v = subTableRowPkValue(map, pk)
    if (v == null || String(v).trim() === '') return null
    parts.push(String(v).trim())
  }
  return parts.join('|')
}

/** Build participant row id from a hydrated sub-table row + designer PK fields. */
export function buildParticipantRowIdFromSubTableRow(
  row: unknown,
  primaryKeyFields?: string[] | null,
): MiParticipantRowId | null {
  if (!row || typeof row !== 'object') return null
  const pks = (primaryKeyFields ?? []).map(f => String(f).trim()).filter(Boolean)
  if (pks.length === 0) return null
  return participantRowIdFromPkMap(row as Record<string, unknown>, pks)
}

/**
 * Resolve the current MI participant row id from Flowable {@code _currentItem} using designer PK fields.
 * Mirrors {@code SubTableRowKeySupport.rowKeyFromCurrentItem}: prefer {@code rowKey} map; single PK may use {@code rowId}.
 */
export function extractMiParticipantRowIdFromCurrentItem(
  currentItem: Record<string, unknown> | null | undefined,
  primaryKeyFields?: string[] | null,
  options?: { rowIdVariable?: string | null },
): MiParticipantRowId | null {
  if (!currentItem || typeof currentItem !== 'object') return null
  const pks = (primaryKeyFields ?? []).map(f => String(f).trim()).filter(Boolean)
  const rowIdVar = (options?.rowIdVariable ?? 'currentItem.rowId').trim()
  const juelPath = rowIdVar.replace(/^currentItem\./, '')

  const rawRowKey = currentItem.rowKey
  if (rawRowKey && typeof rawRowKey === 'object' && !Array.isArray(rawRowKey) && pks.length > 0) {
    const fromRowKey = participantRowIdFromPkMap(rawRowKey as Record<string, unknown>, pks)
    if (fromRowKey != null) return fromRowKey
  }

  if (juelPath && juelPath !== 'rowId') {
    const fromPath = readJuelPath(currentItem, juelPath)
    if (fromPath != null) {
      if (typeof fromPath === 'object' && !Array.isArray(fromPath) && pks.length > 0) {
        const fromMap = participantRowIdFromPkMap(fromPath as Record<string, unknown>, pks)
        if (fromMap != null) return fromMap
      }
      const scalar = normalizeMiParticipantRowId(fromPath)
      if (scalar != null) return scalar
    }
  }

  if (pks.length === 1) {
    const col = pks[0]!
    let v = getSubTableRowValueIgnoreCase(currentItem, 'rowId')
    if (v == null) v = getSubTableRowValueIgnoreCase(currentItem, col)
    return normalizeMiParticipantRowId(v)
  }

  if (pks.length > 1) {
    return participantRowIdFromPkMap(currentItem, pks)
  }

  return normalizeMiParticipantRowId(currentItem.rowId)
}

export function extractParticipantRowIdFromVariables(
  variables: Record<string, unknown> | null | undefined,
  scope: MiSubProcessScopeConfig,
  primaryKeyFields?: string[] | null,
): MiParticipantRowId | null {
  if (!variables) return null
  const ci = (variables._currentItem ?? variables.currentItem) as Record<string, unknown> | undefined
  return extractMiParticipantRowIdFromCurrentItem(ci, primaryKeyFields, {
    rowIdVariable: scope.rowIdVariable,
  })
}

function extractUserIdFromAssigneeCell(raw: unknown): string | null {
  if (raw == null || raw === '') return null
  if (typeof raw === 'string' || typeof raw === 'number') {
    const s = String(raw).trim()
    return s.length > 0 ? s : null
  }
  if (typeof raw === 'object') {
    const uid =
      (raw as { userId?: unknown; id?: unknown }).userId ?? (raw as { id?: unknown }).id
    if (uid == null || uid === '') return null
    const s = String(uid).trim()
    return s.length > 0 ? s : null
  }
  return null
}

/**
 * On My Request: locate the viewer's MI participant row id via BPMN assigneeField + designer PK.
 */
export function resolveViewerParticipantRowIdFromCollectionBinding(
  scope: MiSubProcessScopeConfig,
  collectionBinding: SubTableBindingLike & { data?: unknown[] },
  viewerUserId: string,
): MiParticipantRowId | null {
  if (!viewerUserId.trim() || !scope.assigneeField) return null
  const pk = (collectionBinding.primaryKeyFields ?? [])
    .map(f => String(f).trim())
    .filter(Boolean)
  if (pk.length === 0) return null

  const rows = Array.isArray(collectionBinding.data) ? collectionBinding.data : []
  for (const row of rows) {
    if (!row || typeof row !== 'object') continue
    const rec = row as Record<string, unknown>
    if (extractUserIdFromAssigneeCell(rec[scope.assigneeField]) !== viewerUserId.trim()) continue
    const participantId = buildParticipantRowIdFromSubTableRow(rec, pk)
    if (participantId != null) return participantId
  }
  return null
}

/** Filter bindings to the single MI participant row using Process Design sub-table PK. */
export function filterBindingsToMiParticipantRow<T extends SubTableBindingLike & { data?: unknown[] }>(
  bindings: T[],
  scope: MiSubProcessScopeConfig,
  participantRowId: MiParticipantRowId,
  options?: { includeParticipantScopedChildren?: boolean },
): void {
  const collectionBinding = findBindingForMiSubTableName(bindings, scope.subTableName)
  const participantPk = collectionBinding?.primaryKeyFields ?? null
  if (!hasConfiguredPrimaryKeyFields(participantPk)) return
  const includeChildren = options?.includeParticipantScopedChildren !== false

  for (const binding of bindings) {
    const isCollection = bindingMatchesMiSubTableName(binding, scope.subTableName)
    if (!isCollection && !includeChildren) continue

    const rows = Array.isArray(binding.data) ? binding.data : []
    if (rows.length === 0) continue

    if (isCollection) {
      binding.data = rows.filter(row =>
        rowMatchesSubTablePrimaryKey(row, participantRowId, participantPk),
      ) as T['data']
      continue
    }

    if (!includeChildren) continue

    const fk = String(binding.foreignKeyField || '').trim()
    const fkIsOwnPk =
      participantPk?.some(p => String(p).trim() === fk) ||
      (fk.toLowerCase() === 'id' && !bindingMatchesMiSubTableName(binding, scope.subTableName))

    const filtered = rows.filter(row => {
      if (!row || typeof row !== 'object') return false
      const rec = row as Record<string, unknown>
      if (
        fk &&
        !fkIsOwnPk &&
        rec[fk] != null &&
        miParticipantRowIdsEqual(rec[fk], participantRowId)
      ) {
        return true
      }
      if (rowMatchesSubTablePrimaryKey(row, participantRowId, participantPk)) {
        return true
      }
      // HMDC-style child tables (FK = parent PK e.g. row_id): case-level attachment rows often
      // have file only — keep them visible for the active MI participant / initiator snapshot.
      if (fkIsOwnPk && rowHasAttachmentFile(row)) {
        return true
      }
      return false
    })
    if (filtered.length > 0) {
      binding.data = filtered as T['data']
    }
  }
}
