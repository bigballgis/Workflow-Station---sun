/**
 * BusinessLogicEngine — Dependency graph for incremental rule evaluation (Task 4.20).
 */

import type { FormulaRule, LinkageRule } from '../formRendererHelpers'

// ─── DependencyGraph (Task 4.20) ────────────────────────────────────────────

export interface RuleNode {
  type: 'formula' | 'linkage' | 'visibility'
  ruleIndex: number
  dependsOn: string[]
  targetField: string
}

/**
 * Dependency graph for business logic rules.
 * Builds a graph from formulas, linkages, and visibility rules.
 * Supports incremental evaluation: getAffectedRules(fieldKey) returns
 * only rules that depend on the changed field.
 * Circular dependency detection: max 10 iterations, then warn and stop.
 */
export class DependencyGraph {
  private fieldToRules = new Map<string, RuleNode[]>()
  private allRules: RuleNode[] = []

  static readonly MAX_ITERATIONS = 10

  /**
   * Build the dependency graph from config rules.
   */
  build(
    formulas: FormulaRule[],
    linkages: LinkageRule[],
    visibilityRules: Array<{ field: string; dependsOn: string[] }>,
  ): void {
    this.fieldToRules.clear()
    this.allRules = []

    // Register formula rules
    for (let i = 0; i < formulas.length; i++) {
      const formula = formulas[i]
      const node: RuleNode = {
        type: 'formula',
        ruleIndex: i,
        dependsOn: formula.dependsOn,
        targetField: formula.targetField,
      }
      this.allRules.push(node)
      this.registerNode(node)
    }

    // Register linkage rules
    for (let i = 0; i < linkages.length; i++) {
      const linkage = linkages[i]
      const node: RuleNode = {
        type: 'linkage',
        ruleIndex: i,
        dependsOn: [linkage.sourceField],
        targetField: linkage.targetField,
      }
      this.allRules.push(node)
      this.registerNode(node)
    }

    // Register visibility rules
    for (let i = 0; i < visibilityRules.length; i++) {
      const vis = visibilityRules[i]
      const node: RuleNode = {
        type: 'visibility',
        ruleIndex: i,
        dependsOn: vis.dependsOn,
        targetField: vis.field,
      }
      this.allRules.push(node)
      this.registerNode(node)
    }
  }

  private registerNode(node: RuleNode): void {
    for (const dep of node.dependsOn) {
      if (!this.fieldToRules.has(dep)) {
        this.fieldToRules.set(dep, [])
      }
      this.fieldToRules.get(dep)!.push(node)
    }
  }

  /**
   * Get all rules affected by a field change, including transitive dependencies.
   * Uses BFS with circular dependency detection (max 10 iterations).
   */
  getAffectedRules(fieldKey: string): RuleNode[] {
    const affected: RuleNode[] = []
    const visited = new Set<string>()
    const queue: string[] = [fieldKey]
    let iterations = 0

    while (queue.length > 0 && iterations < DependencyGraph.MAX_ITERATIONS) {
      iterations++
      const currentField = queue.shift()!

      if (visited.has(currentField)) continue
      visited.add(currentField)

      const rules = this.fieldToRules.get(currentField) ?? []
      for (const rule of rules) {
        affected.push(rule)
        // If this rule's target field triggers further rules, enqueue it
        if (!visited.has(rule.targetField)) {
          queue.push(rule.targetField)
        }
      }
    }

    if (iterations >= DependencyGraph.MAX_ITERATIONS) {
      console.warn(
        `[DependencyGraph] Circular dependency detected for field "${fieldKey}". ` +
        `Stopped after ${DependencyGraph.MAX_ITERATIONS} iterations.`,
      )
    }

    return affected
  }

  /** Check if the graph has any rules registered */
  hasRules(): boolean {
    return this.allRules.length > 0
  }

  /** Get all registered rules */
  getAllRules(): RuleNode[] {
    return this.allRules
  }
}
