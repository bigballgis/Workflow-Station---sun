/**
 * Reading and building the stored computed-field definition, shared by
 * developer-workstation (Function Unit tables) and admin-center (Relation Tables) via the
 * @platform-shared vite alias. Both apps keep a thin re-export in their own utils/ so imports
 * stay app-local.
 *
 * The shape produced here is exactly what the backend validators expect in
 * {@code computed_field_json}: the AST is the evaluation authority and `source` exists only so the
 * designer can redisplay what was typed.
 */
import {
  collectDependencies,
  parseFormula,
  type AstNode,
  type ComputedFieldDefinition,
  type ComputedFieldOnError,
  type ComputedFieldScope,
  COMPUTED_FIELD_VERSION,
} from './computedField'

export type { ComputedFieldDefinition, ComputedFieldScope, ComputedFieldOnError }

export function parseComputedFieldFromApi(
  raw?: Record<string, unknown> | ComputedFieldDefinition | null,
): ComputedFieldDefinition | undefined {
  if (!raw || typeof raw !== 'object') return undefined
  const source = typeof raw.source === 'string' ? raw.source : ''
  const ast = raw.ast
  if (!source.trim() || !ast || typeof ast !== 'object') return undefined
  return {
    version: typeof raw.version === 'number' ? raw.version : COMPUTED_FIELD_VERSION,
    scope: raw.scope === 'aggregate' ? 'aggregate' : 'row',
    source,
    ast: ast as ComputedFieldDefinition['ast'],
    dependsOn: Array.isArray(raw.dependsOn)
      ? raw.dependsOn.map(String)
      : collectDependencies(ast as ComputedFieldDefinition['ast']),
    onError: raw.onError === 'null' ? 'null' : 'fail',
  }
}

export function buildComputedFieldDefinition(
  source: string,
  scope: ComputedFieldScope,
  onError: ComputedFieldOnError,
): { ok: true; value: ComputedFieldDefinition } | { ok: false; message: string; position?: number } {
  const trimmed = source.trim()
  if (!trimmed) {
    return { ok: false, message: 'Formula is empty', position: 0 }
  }
  const parsed = parseFormula(trimmed)
  if (!parsed.ok) {
    return {
      ok: false,
      message: parsed.error.message,
      position: parsed.error.position,
    }
  }
  return {
    ok: true,
    value: {
      version: COMPUTED_FIELD_VERSION,
      scope,
      source: trimmed,
      ast: parsed.ast,
      dependsOn: collectDependencies(parsed.ast),
      onError,
    },
  }
}

export function computedFieldSummary(def?: ComputedFieldDefinition | null): string {
  if (!def?.source?.trim()) return ''
  const scopeLabel = def.scope === 'aggregate' ? 'Σ' : 'ƒ'
  return `${scopeLabel} ${def.source.trim()}`
}

const TEXT_COLUMN_TYPES = new Set(['VARCHAR', 'TEXT'])
const NUMERIC_CALLS = new Set(['ROUND', 'ABS', 'VALUE', 'LEN'])

export function isTextColumnDataType(dataType?: string | null): boolean {
  return !!dataType && TEXT_COLUMN_TYPES.has(dataType.trim().toUpperCase())
}

/** True when the AST is statically a number (SUM, arithmetic, ROUND, …). Field-only refs stay unknown. */
export function formulaProducesNumber(ast: AstNode | undefined | null): boolean {
  if (!ast || typeof ast !== 'object' || !('type' in ast)) return false
  switch (ast.type) {
    case 'number':
    case 'aggregate':
      return true
    case 'unary':
      return formulaProducesNumber(ast.operand)
    case 'binary':
      return ast.op === '+' || ast.op === '-' || ast.op === '*' || ast.op === '/'
    case 'call':
      return NUMERIC_CALLS.has(ast.fn)
    default:
      return false
  }
}

export interface ComputedFieldColumnLike {
  fieldName?: string
  displayName?: string
  dataType?: string
  isComputed?: boolean
  computedField?: Record<string, unknown> | ComputedFieldDefinition | null
  computedFieldJson?: Record<string, unknown> | null
}

/** First computed text column whose formula is a number — the save the backend rejects as TYPE_MISMATCH. */
export function findNumericFormulaOnTextColumn(
  fields: ComputedFieldColumnLike[] | undefined | null,
): ComputedFieldColumnLike | undefined {
  if (!fields) return undefined
  return fields.find((field) => {
    if (!field?.isComputed || !isTextColumnDataType(field.dataType)) return false
    const def = parseComputedFieldFromApi(field.computedField ?? field.computedFieldJson)
    return !!def && formulaProducesNumber(def.ast)
  })
}
