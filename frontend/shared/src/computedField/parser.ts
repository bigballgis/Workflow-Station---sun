/**
 * Source text -> AST compiler for computed fields.
 *
 * Hand-written tokenizer + precedence-climbing parser: no new dependency, and critically no
 * eval/new Function/mathjs. The previous implementation handed user text to a full mathjs
 * instance guarded by a 6-keyword regex blacklist, which is not a sandbox.
 *
 * Runs in the DESIGNER only. At runtime both interpreters consume the stored AST, so this file
 * is never on a hot path and the backend never needs a parser — only an AST validator.
 */
import {
  AGGREGATE_FNS,
  type AggregateFn,
  type AstNode,
  type BinaryOp,
  type ComputedFieldError,
  type ParseResult,
} from './types'

/** Guards against pathological input; the backend validator enforces its own budget too. */
export const MAX_SOURCE_LENGTH = 2000
export const MAX_AST_NODES = 200

type TokenType = 'number' | 'text' | 'identifier' | 'operator' | 'lparen' | 'rparen' | 'comma' | 'dot'

interface Token {
  type: TokenType
  value: string
  position: number
}

const OPERATOR_STARTS = new Set(['+', '-', '*', '/', '=', '<', '>'])

/** Binary precedence; higher binds tighter. Comparisons sit below arithmetic, as in Excel. */
const PRECEDENCE: Record<string, number> = {
  '=': 1, '<>': 1, '<': 1, '<=': 1, '>': 1, '>=': 1,
  '+': 2, '-': 2,
  '*': 3, '/': 3,
}

function err(code: ComputedFieldError['code'], message: string, position: number): ParseResult {
  return { ok: false, error: { code, message, position } }
}

function tokenize(source: string): Token[] | ParseResult {
  const tokens: Token[] = []
  let i = 0
  while (i < source.length) {
    const ch = source[i]
    if (ch === ' ' || ch === '\t' || ch === '\n' || ch === '\r') {
      i++
      continue
    }
    const start = i
    if (ch >= '0' && ch <= '9') {
      while (i < source.length && source[i] >= '0' && source[i] <= '9') i++
      if (source[i] === '.') {
        i++
        while (i < source.length && source[i] >= '0' && source[i] <= '9') i++
      }
      tokens.push({ type: 'number', value: source.slice(start, i), position: start })
      continue
    }
    if (ch === '"') {
      i++
      let text = ''
      while (i < source.length && source[i] !== '"') {
        // "" is an escaped quote, as in Excel and Power Fx.
        if (source[i] === '\\' && source[i + 1] === '"') {
          text += '"'
          i += 2
          continue
        }
        text += source[i]
        i++
      }
      if (i >= source.length) {
        return err('SYNTAX_ERROR', 'Unterminated string literal', start)
      }
      i++
      tokens.push({ type: 'text', value: text, position: start })
      continue
    }
    if (/[A-Za-z_]/.test(ch)) {
      while (i < source.length && /[A-Za-z0-9_]/.test(source[i])) i++
      tokens.push({ type: 'identifier', value: source.slice(start, i), position: start })
      continue
    }
    if (OPERATOR_STARTS.has(ch)) {
      const two = source.slice(i, i + 2)
      if (two === '<=' || two === '>=' || two === '<>') {
        tokens.push({ type: 'operator', value: two, position: start })
        i += 2
        continue
      }
      tokens.push({ type: 'operator', value: ch, position: start })
      i++
      continue
    }
    if (ch === '(') { tokens.push({ type: 'lparen', value: ch, position: start }); i++; continue }
    if (ch === ')') { tokens.push({ type: 'rparen', value: ch, position: start }); i++; continue }
    if (ch === ',') { tokens.push({ type: 'comma', value: ch, position: start }); i++; continue }
    if (ch === '.') { tokens.push({ type: 'dot', value: ch, position: start }); i++; continue }
    return err('SYNTAX_ERROR', `Unexpected character '${ch}'`, start)
  }
  return tokens
}

function isParseFailure(value: Token[] | ParseResult): value is ParseResult {
  return !Array.isArray(value)
}

class Parser {
  private readonly tokens: Token[]
  private index = 0
  private nodeCount = 0
  readonly fields = new Set<string>()
  readonly aggregates = new Set<string>()

  constructor(tokens: Token[]) {
    this.tokens = tokens
  }

  private peek(): Token | undefined {
    return this.tokens[this.index]
  }

  private endPosition(): number {
    const last = this.tokens[this.tokens.length - 1]
    return last ? last.position + last.value.length : 0
  }

  private countNode(): ComputedFieldError | null {
    this.nodeCount++
    if (this.nodeCount > MAX_AST_NODES) {
      return { code: 'BUDGET_EXCEEDED', message: `Formula exceeds ${MAX_AST_NODES} nodes` }
    }
    return null
  }

  parseExpression(minPrecedence = 0): AstNode | ComputedFieldError {
    let left = this.parseUnary()
    if (isError(left)) return left
    for (;;) {
      const token = this.peek()
      if (!token || token.type !== 'operator') break
      const precedence = PRECEDENCE[token.value]
      if (precedence === undefined || precedence < minPrecedence) break
      this.index++
      // All operators are left-associative, hence precedence + 1 for the right operand.
      const right = this.parseExpression(precedence + 1)
      if (isError(right)) return right
      const budget = this.countNode()
      if (budget) return budget
      left = { type: 'binary', op: token.value as BinaryOp, left, right }
    }
    return left
  }

  private parseUnary(): AstNode | ComputedFieldError {
    const token = this.peek()
    if (token && token.type === 'operator' && (token.value === '-' || token.value === '+')) {
      this.index++
      const operand = this.parseUnary()
      if (isError(operand)) return operand
      if (token.value === '+') return operand
      const budget = this.countNode()
      if (budget) return budget
      return { type: 'unary', op: '-', operand }
    }
    return this.parsePrimary()
  }

  private parsePrimary(): AstNode | ComputedFieldError {
    const token = this.peek()
    if (!token) {
      return { code: 'SYNTAX_ERROR', message: 'Unexpected end of formula', position: this.endPosition() }
    }
    const budget = this.countNode()
    if (budget) return budget

    if (token.type === 'number') {
      this.index++
      return { type: 'number', text: token.value }
    }
    if (token.type === 'text') {
      this.index++
      return { type: 'text', value: token.value }
    }
    if (token.type === 'lparen') {
      this.index++
      const inner = this.parseExpression(0)
      if (isError(inner)) return inner
      const closing = this.peek()
      if (!closing || closing.type !== 'rparen') {
        return { code: 'SYNTAX_ERROR', message: "Missing ')'", position: token.position }
      }
      this.index++
      return inner
    }
    if (token.type === 'identifier') {
      return this.parseIdentifier(token)
    }
    return { code: 'SYNTAX_ERROR', message: `Unexpected token '${token.value}'`, position: token.position }
  }

  private parseIdentifier(token: Token): AstNode | ComputedFieldError {
    this.index++
    const upper = token.value.toUpperCase()
    if (upper === 'TRUE' || upper === 'FALSE') {
      return { type: 'boolean', value: upper === 'TRUE' }
    }
    const next = this.peek()
    if (next && next.type === 'lparen') {
      this.index++
      if ((AGGREGATE_FNS as readonly string[]).includes(upper)) {
        return this.parseAggregateArgument(upper as AggregateFn, token.position)
      }
      return this.parseCallArguments(upper, token.position)
    }
    // A bare `a.b` outside an aggregate is not a valid row-field reference.
    if (next && next.type === 'dot') {
      return {
        code: 'SYNTAX_ERROR',
        message: `Qualified reference '${token.value}.…' is only allowed inside an aggregate such as SUM(table.column)`,
        position: next.position,
      }
    }
    this.fields.add(token.value)
    return { type: 'field', name: token.value }
  }

  /** SUM(table.column) / AVG(table.column) / COUNT(table) or COUNT(table.column). */
  private parseAggregateArgument(fn: AggregateFn, position: number): AstNode | ComputedFieldError {
    const table = this.peek()
    if (!table || table.type !== 'identifier') {
      return {
        code: 'SYNTAX_ERROR',
        message: `${fn} expects a sub-table reference such as ${fn}(order_items.amount)`,
        position: table ? table.position : position,
      }
    }
    this.index++
    let column: string | undefined
    if (this.peek()?.type === 'dot') {
      this.index++
      const columnToken = this.peek()
      if (!columnToken || columnToken.type !== 'identifier') {
        return {
          code: 'SYNTAX_ERROR',
          message: `Expected a column name after '${table.value}.'`,
          position: columnToken ? columnToken.position : position,
        }
      }
      this.index++
      column = columnToken.value
    }
    if (fn !== 'COUNT' && !column) {
      return {
        code: 'SYNTAX_ERROR',
        message: `${fn} requires a column, e.g. ${fn}(${table.value}.amount)`,
        position: table.position,
      }
    }
    const closing = this.peek()
    if (!closing || closing.type !== 'rparen') {
      return { code: 'SYNTAX_ERROR', message: `Missing ')' after ${fn}(…`, position: position }
    }
    this.index++
    this.aggregates.add(column ? `${table.value}.${column}` : table.value)
    return column
      ? { type: 'aggregate', fn, table: table.value, column }
      : { type: 'aggregate', fn, table: table.value }
  }

  private parseCallArguments(fn: string, position: number): AstNode | ComputedFieldError {
    const args: AstNode[] = []
    if (this.peek()?.type === 'rparen') {
      this.index++
      return { type: 'call', fn, args }
    }
    for (;;) {
      const arg = this.parseExpression(0)
      if (isError(arg)) return arg
      args.push(arg)
      const separator = this.peek()
      if (separator?.type === 'comma') {
        this.index++
        continue
      }
      if (separator?.type === 'rparen') {
        this.index++
        return { type: 'call', fn, args }
      }
      return {
        code: 'SYNTAX_ERROR',
        message: `Missing ')' or ',' in ${fn}(…`,
        position: separator ? separator.position : position,
      }
    }
  }

  atEnd(): boolean {
    return this.index >= this.tokens.length
  }

  currentPosition(): number {
    return this.peek()?.position ?? this.endPosition()
  }
}

function isError(value: AstNode | ComputedFieldError): value is ComputedFieldError {
  return typeof (value as ComputedFieldError).code === 'string'
    && typeof (value as ComputedFieldError).message === 'string'
}

/**
 * Compile source text into an AST plus the dependency list.
 *
 * `dependsOn` mixes plain field names and `table.column` aggregate references. The backend
 * validator RE-DERIVES this from the AST and compares, so a tampered client list is rejected.
 */
export function parseFormula(source: string): ParseResult {
  if (source == null || source.trim() === '') {
    return { ok: false, error: { code: 'SYNTAX_ERROR', message: 'Formula is empty', position: 0 } }
  }
  if (source.length > MAX_SOURCE_LENGTH) {
    return {
      ok: false,
      error: { code: 'BUDGET_EXCEEDED', message: `Formula exceeds ${MAX_SOURCE_LENGTH} characters` },
    }
  }
  const tokenized = tokenize(source)
  if (isParseFailure(tokenized)) return tokenized
  if (tokenized.length === 0) {
    return { ok: false, error: { code: 'SYNTAX_ERROR', message: 'Formula is empty', position: 0 } }
  }
  const parser = new Parser(tokenized)
  const ast = parser.parseExpression(0)
  if (isError(ast)) return { ok: false, error: ast }
  if (!parser.atEnd()) {
    return {
      ok: false,
      error: {
        code: 'SYNTAX_ERROR',
        message: 'Unexpected trailing input',
        position: parser.currentPosition(),
      },
    }
  }
  return {
    ok: true,
    ast,
    dependsOn: [...parser.fields, ...parser.aggregates].sort(),
  }
}

/** Re-derive dependencies from an AST — the authority the backend compares against. */
export function collectDependencies(ast: AstNode): string[] {
  const found = new Set<string>()
  const walk = (node: AstNode): void => {
    switch (node.type) {
      case 'field':
        found.add(node.name)
        return
      case 'aggregate':
        found.add(node.column ? `${node.table}.${node.column}` : node.table)
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
        return
    }
  }
  walk(ast)
  return [...found].sort()
}
