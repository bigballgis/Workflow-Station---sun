/**
 * BusinessLogicEngine — Runtime engine for form business logic evaluation.
 *
 * Covers: condition visibility, calculation formulas, field linkage,
 * validation, cross-field validation, sub-table calculation & validation,
 * and dependency graph optimization.
 *
 * Security: NO eval(), NO new Function(). All expressions evaluated through
 * mathjs (restricted instance) or the custom condition evaluator.
 *
 * This module was split into ./businessLogicEngine/* for maintainability.
 * It is now a barrel that re-exports the original public surface verbatim —
 * all export names and import paths remain unchanged for consumers.
 */

// ─── Result Interfaces ──────────────────────────────────────────────────────
export type {
  EvaluationResult,
  SummaryEvaluationResult,
  FormValidationResult,
  CrossFieldValidationResult,
  SubTableValidationResult,
  LinkageResult,
} from './businessLogicEngine/types'

// ─── evaluateCondition (Task 4.2) ───────────────────────────────────────────
export { evaluateCondition } from './businessLogicEngine/conditions'

// ─── evaluateFormula (Task 4.5) ─────────────────────────────────────────────
export { containsDangerousKeyword, evaluateFormula } from './businessLogicEngine/formula'

// ─── processLinkage (Task 4.8) ──────────────────────────────────────────────
export { processLinkage } from './businessLogicEngine/linkage'

// ─── validateField / validateCrossFields (Tasks 4.11, 4.13) ─────────────────
export { validateField, validateCrossFields } from './businessLogicEngine/validation'

// ─── Sub-table calculation & validation (Tasks 4.15, 4.18) ──────────────────
export {
  calculateSubTableRow,
  calculateSummary,
  validateSubTable,
} from './businessLogicEngine/subTable'

// ─── DependencyGraph (Task 4.20) ────────────────────────────────────────────
export { DependencyGraph } from './businessLogicEngine/dependencyGraph'

// ─── BusinessLogicEngine class (Task 4.1) ───────────────────────────────────
export { BusinessLogicEngine } from './businessLogicEngine/engine'
