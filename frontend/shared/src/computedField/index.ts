/**
 * Computed (formula) field engine — shared entry point.
 *
 * Import from '@platform-shared/computedField' in developer-workstation (compile + preview) and
 * user-portal (preview only). The authoritative recalculation on write happens in the Java
 * mirror of this code; goldenVectors.json keeps the two in lockstep.
 */
export * from './types'
export { DIVISION_SCALE, parseDecimal, toDecimalString, roundTo } from './decimal'
export { parseFormula, collectDependencies, MAX_AST_NODES, MAX_SOURCE_LENGTH } from './parser'
export { fromRowValue, toText, isBlank } from './coerce'
export {
  evaluateAst,
  normalizeSubTables,
  isKnownFunction,
  knownFunctionNames,
  LAZY_FUNCTIONS,
  type EvaluationContext,
  type SliceIdentity,
} from './evaluator'
