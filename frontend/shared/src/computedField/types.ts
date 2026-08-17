/**
 * Computed (formula) field — AST and value contracts.
 *
 * CANONICAL copy, consumed by developer-workstation (compile + preview) and user-portal
 * (preview only) through the @platform-shared vite alias. The backend Java interpreter in
 * user-portal / developer-workstation mirrors these node kinds and MUST stay semantically
 * identical; goldenVectors.json is the contract test shared by both sides.
 *
 * The AST — not the source text — is the evaluation authority. Source is stored only so the
 * designer can redisplay what the author typed.
 */

/** Aggregate functions, the only node kind that reaches into sub-table rows. */
export type AggregateFn = 'SUM' | 'AVG' | 'MIN' | 'MAX' | 'COUNT'

export const AGGREGATE_FNS: readonly AggregateFn[] = ['SUM', 'AVG', 'MIN', 'MAX', 'COUNT']

export type BinaryOp = '+' | '-' | '*' | '/' | '=' | '<>' | '<' | '<=' | '>' | '>='

export type UnaryOp = '-'

/**
 * Numeric literals keep their SOURCE TEXT rather than a JS number: `0.1` parsed into a double
 * is already lossy, and the backend must read the exact same digits to build its BigDecimal.
 */
export interface NumberLiteralNode {
  type: 'number'
  /** Decimal digits exactly as authored, e.g. "0.1", "1000", "12.3400". */
  text: string
}

export interface TextLiteralNode {
  type: 'text'
  value: string
}

export interface BooleanLiteralNode {
  type: 'boolean'
  value: boolean
}

/**
 * Reference to a field. Unqualified (`name`) reads the same row. Qualified
 * (`table.name`) reads the Function Unit MAIN row from a SUB-table formula —
 * not an aggregate, and not another sub-table.
 */
export interface FieldRefNode {
  type: 'field'
  name: string
  /** Physical MAIN table name when this is a parent-row lookup. */
  table?: string
}

/**
 * Aggregate over a sub-table, referenced BY TABLE NAME (not bindingId): computed fields are
 * defined in Table Design, which is upstream of Form Design where bindings are created.
 * `column` is omitted for COUNT(table) — counting rows needs no column.
 */
export interface AggregateNode {
  type: 'aggregate'
  fn: AggregateFn
  table: string
  column?: string
}

export interface UnaryNode {
  type: 'unary'
  op: UnaryOp
  operand: AstNode
}

export interface BinaryNode {
  type: 'binary'
  op: BinaryOp
  left: AstNode
  right: AstNode
}

export interface CallNode {
  type: 'call'
  /** Always upper-case; the parser normalizes so `if(...)` and `IF(...)` share one AST. */
  fn: string
  args: AstNode[]
}

export type AstNode =
  | NumberLiteralNode
  | TextLiteralNode
  | BooleanLiteralNode
  | FieldRefNode
  | AggregateNode
  | UnaryNode
  | BinaryNode
  | CallNode

/** Fixed-point decimal mirroring java.math.BigDecimal (unscaledValue + scale). */
export interface Decimal {
  unscaled: bigint
  scale: number
}

/**
 * Runtime value. `blank` is a first-class kind rather than null/0 so ISBLANK can distinguish
 * "user left it empty" from "it is zero" — conflating them is exactly how Power Platform's
 * calculated columns produce misleading results.
 */
export type ComputedValue =
  | { kind: 'number'; value: Decimal }
  | { kind: 'text'; value: string }
  | { kind: 'boolean'; value: boolean }
  | { kind: 'blank' }

export const BLANK: ComputedValue = { kind: 'blank' }

export type ComputedFieldErrorCode =
  | 'SYNTAX_ERROR'
  | 'UNKNOWN_FUNCTION'
  | 'UNKNOWN_FIELD'
  | 'UNKNOWN_TABLE'
  | 'WRONG_ARG_COUNT'
  | 'TYPE_MISMATCH'
  | 'DIVISION_BY_ZERO'
  | 'NEGATIVE_SQRT'
  | 'NON_INTEGER_EXPONENT'
  | 'BUDGET_EXCEEDED'
  | 'UNSUPPORTED_NODE'

export interface ComputedFieldError {
  code: ComputedFieldErrorCode
  /** Developer-facing detail. UI surfaces an i18n message keyed by `code` plus this as tooltip. */
  message: string
  /** Zero-based offset into the source text, when known — drives editor error positioning. */
  position?: number
}

/**
 * Never returns a silent 0. Callers must branch on `ok`; `error-handling-governance.mdc`
 * red line 1 forbids collapsing a failure into a plausible-looking number.
 */
export type EvalResult =
  | { ok: true; value: ComputedValue }
  | { ok: false; error: ComputedFieldError }

export type ParseResult =
  | { ok: true; ast: AstNode; dependsOn: string[] }
  | { ok: false; error: ComputedFieldError }

export function evalOk(value: ComputedValue): EvalResult {
  return { ok: true, value }
}

export function evalErr(
  code: ComputedFieldErrorCode,
  message: string,
  position?: number,
): EvalResult & { ok: false } {
  return { ok: false, error: position === undefined ? { code, message } : { code, message, position } }
}

/** Formula scope. `aggregate` formulas may contain AggregateNode; `row` ones may not. */
export type ComputedFieldScope = 'row' | 'aggregate'

/** Behaviour when evaluation fails at write time. */
export type ComputedFieldOnError = 'fail' | 'null'

/** Persisted shape of dw_field_definitions.computed_field_json. */
export interface ComputedFieldDefinition {
  version: number
  scope: ComputedFieldScope
  source: string
  ast: AstNode
  dependsOn: string[]
  onError: ComputedFieldOnError
}

export const COMPUTED_FIELD_VERSION = 1
