/**
 * Computed (formula) columns at Portal runtime — read-only marking and live preview.
 *
 * The server is the authority: {@code ComputedFieldRecalculator} recomputes every computed column
 * on write, and whatever it stores is what the record holds. Everything here exists so the user
 * sees the number move while typing instead of only after saving, so a preview that cannot be
 * trusted MUST be skipped rather than guessed — a wrong preview is worse than a stale one.
 *
 * Two ways a preview is skipped:
 *  - an aggregate formula whose sub-table rows are not loaded on this screen (the caller keeps the
 *    value the server sent);
 *  - an evaluation error, reported through `errors` so the field can render #ERR instead of a
 *    plausible-looking wrong value.
 * A computed flag with no usable formula JSON is also reported through `errors` (same as the
 * server's COMPUTED_FIELD_DEFINITION_INVALID), not skipped, so the screen cannot keep a stale value.
 */
import {
  evaluateAst,
  toDecimalString,
  type AstNode,
  type ComputedValue,
  type EvaluationContext,
} from '@platform-shared/computedField'
import {
  parseComputedFieldFromApi,
  type ComputedFieldDefinition,
} from '@platform-shared/computedFieldConfig'
import type { BindingFieldDefinition } from './subTableRowRuntime/types'

export interface ComputedColumn {
  fieldName: string
  definition: ComputedFieldDefinition | null
  /** Lower-cased names of sub-tables this formula aggregates over; empty for row scope. */
  referencedTables: string[]
  /** Set when isComputed is true but the stored JSON is not a usable formula. */
  parseError?: string
}

export interface ComputedPreview {
  /**
   * fieldName → previewed value. A column whose formula failed under `onError: 'null'` appears
   * here as null, because that is exactly what the server will store for it.
   */
  values: Record<string, unknown>
  /**
   * fieldName → error code, only for columns whose formula failed under `onError: 'fail'`.
   * The server rejects that write, so these must block submission rather than show a value.
   */
  errors: Record<string, string>
  /** fieldNames left untouched because their inputs are not available on this screen. */
  skipped: string[]
}

const EMPTY_PREVIEW: ComputedPreview = Object.freeze({ values: {}, errors: {}, skipped: [] })

export function emptyComputedPreview(): ComputedPreview {
  return EMPTY_PREVIEW
}

/**
 * Reads the computed columns out of a binding's field definitions, in evaluation order.
 *
 * Ordering mirrors ComputedFieldRowEvaluation.orderByDependency on the server so a formula reading
 * another formula previews the same value the server will store. A dependency cycle cannot come
 * from the designer (the validator rejects it) but is tolerated here by leaving the offending
 * column out rather than looping.
 */
export function collectComputedColumns(
  fields: BindingFieldDefinition[] | null | undefined,
): ComputedColumn[] {
  if (!fields?.length) return []
  const columns: ComputedColumn[] = []
  for (const field of fields) {
    if (field.isComputed !== true) continue
    const definition = parseComputedFieldFromApi(field.computedField)
    if (!definition) {
      columns.push({
        fieldName: field.fieldName,
        definition: null,
        referencedTables: [],
        parseError: 'COMPUTED_FIELD_DEFINITION_INVALID',
      })
      continue
    }
    columns.push({
      fieldName: field.fieldName,
      definition,
      referencedTables: collectAggregateTables(definition.ast),
    })
  }
  return orderByDependency(columns)
}

/** Names of the sub-tables an aggregate formula reaches into, lower-cased. */
function collectAggregateTables(ast: AstNode): string[] {
  const found = new Set<string>()
  const walk = (node: AstNode): void => {
    switch (node.type) {
      case 'aggregate':
        found.add(node.table.toLowerCase())
        return
      case 'unary':
        walk(node.operand)
        return
      case 'binary':
        walk(node.left)
        walk(node.right)
        return
      case 'call':
        node.args.forEach(walk)
        return
      default:
    }
  }
  walk(ast)
  return [...found]
}

function orderByDependency(columns: ComputedColumn[]): ComputedColumn[] {
  const byName = new Map<string, ComputedColumn>()
  for (const column of columns) byName.set(column.fieldName.toLowerCase(), column)

  const ordered: ComputedColumn[] = []
  const placed = new Set<string>()
  const visiting = new Set<string>()

  const visit = (column: ComputedColumn): boolean => {
    const key = column.fieldName.toLowerCase()
    if (placed.has(key)) return true
    if (visiting.has(key)) return false
    visiting.add(key)
    for (const dependency of column.definition?.dependsOn ?? []) {
      if (dependency.includes('.')) continue
      const upstream = byName.get(dependency.toLowerCase())
      if (upstream && !visit(upstream)) {
        visiting.delete(key)
        return false
      }
    }
    visiting.delete(key)
    placed.add(key)
    ordered.push(column)
    return true
  }

  for (const column of columns) visit(column)
  return ordered
}

/**
 * Evaluates computed columns against the row currently on screen.
 *
 * Results feed back into `row` as they are produced, so a formula reading another formula sees the
 * freshly previewed value rather than the stale stored one.
 *
 * @param subTables rows keyed by lower-cased table name. Pass only the tables actually loaded:
 *   a table missing from this map makes its aggregates skip, which is what keeps the server value
 *   on screen instead of an aggregate computed over rows the user cannot see.
 * @param parents parent rows keyed by lower-cased table name. SUB-table formulas read MAIN
 *   columns as {@code table.column} from this map.
 */
export function previewComputedRow(
  columns: ComputedColumn[],
  row: Record<string, unknown>,
  subTables?: Record<string, Array<Record<string, unknown>>> | null,
  parents?: Record<string, Record<string, unknown>> | null,
): ComputedPreview {
  if (!columns.length) return EMPTY_PREVIEW

  const available = subTables ?? {}
  const parentRows = parents ?? {}
  const values: Record<string, unknown> = {}
  const errors: Record<string, string> = {}
  const skipped: string[] = []
  const working: Record<string, unknown> = { ...row }

  for (const column of columns) {
    if (column.parseError || !column.definition) {
      errors[column.fieldName] = column.parseError ?? 'COMPUTED_FIELD_DEFINITION_INVALID'
      continue
    }
    if (column.referencedTables.some(table => !(table in available))) {
      skipped.push(column.fieldName)
      continue
    }
    const context: EvaluationContext = { row: working, subTables: available, parents: parentRows }
    const outcome = evaluateAst(column.definition.ast, context)
    if (!outcome.ok) {
      // Mirrors ComputedFieldRowEvaluation.applyFields: onError=null stores a blank and lets the
      // write through, so previewing a blank is what the record will actually hold. onError=fail
      // makes the server reject the write, so the screen reports it instead of showing a value.
      if (column.definition.onError === 'null') {
        values[column.fieldName] = null
        working[column.fieldName] = null
      } else {
        errors[column.fieldName] = outcome.error.code
      }
      continue
    }
    const value = toModelValue(outcome.value)
    values[column.fieldName] = value
    working[column.fieldName] = value
  }

  return { values, errors, skipped }
}

/**
 * Converts an evaluated value into what the form model holds.
 *
 * Numbers become their exact decimal string rather than a JS number: the server stores a
 * BigDecimal, and routing the preview through a float would show a different tail of digits than
 * the value that lands in the record.
 */
export function toModelValue(value: ComputedValue): unknown {
  switch (value.kind) {
    case 'number':
      return toDecimalString(value.value)
    case 'text':
      return value.value
    case 'boolean':
      return value.value
    default:
      return null
  }
}

/** True when this column's value comes from a formula and must not be editable. */
export function isComputedColumn(field: BindingFieldDefinition | null | undefined): boolean {
  return field?.isComputed === true
}

/** Layout containers (card, row, col…) nest their inputs, so the walk has to descend. */
interface NestableFormField {
  key: string
  readonly?: boolean
  children?: NestableFormField[]
}

/**
 * Marks form fields backed by a computed column read-only.
 *
 * Narrower on purpose than applyFieldDefinitionsToFormFields, which also rewrites auto-PK inputs:
 * the main form has its own primary-key handling, so this only carries the computed decision.
 */
export function applyComputedReadonlyToFormFields<T extends NestableFormField>(
  fields: T[],
  fieldDefinitions: BindingFieldDefinition[] | null | undefined,
): T[] {
  const computed = computedColumnNames(fieldDefinitions)
  if (!computed.size) return fields

  const walk = <F extends NestableFormField>(list: F[]): F[] =>
    list.map(field => {
      const children = Array.isArray(field.children) ? walk(field.children) : undefined
      const isComputed = computed.has(field.key?.toLowerCase() ?? '')
      if (!isComputed && !children) return field
      return {
        ...field,
        ...(isComputed ? { readonly: true } : {}),
        ...(children ? { children } : {}),
      }
    })

  return walk(fields)
}

/** Lower-cased names of computed columns, for cheap membership checks while rendering. */
export function computedColumnNames(
  fields: BindingFieldDefinition[] | null | undefined,
): Set<string> {
  const names = new Set<string>()
  for (const field of fields ?? []) {
    if (field.isComputed === true) names.add(field.fieldName.toLowerCase())
  }
  return names
}
