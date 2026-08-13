/**
 * Compiles goldenVectors.json into goldenVectors.compiled.json (source text -> AST) and guards
 * that the committed artifact stays current.
 *
 * WHY THIS EXISTS: the TypeScript parser is the ONLY parser. The Java side deliberately has none —
 * refusing to turn user text into an executable structure on the server is a security property, not
 * an omission. The Java contract test still needs ASTs to evaluate, so the parser emits them here
 * and the result is committed alongside the source vectors.
 *
 * Regenerate after editing goldenVectors.json:
 *   cd frontend/user-portal && npm run vectors:build
 * Skipping that makes this test fail rather than letting the Java test run stale ASTs.
 */
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'
import vectors from '@platform-shared/computedField/goldenVectors.json'
import { knownFunctionNames, parseFormula, type AstNode } from '@platform-shared/computedField'

const COMPILED_PATH = resolve(process.cwd(), '../shared/src/computedField/goldenVectors.compiled.json')

interface RawCase {
  name: string
  formula: string
  [key: string]: unknown
}

const suite = vectors as unknown as {
  divisionScale: number
  cases: RawCase[]
  aliasDeduplicationCases: RawCase[]
}

/** Attach the compiled AST to a case, dropping the $comment noise the source file carries. */
function compileCase(testCase: RawCase): Record<string, unknown> {
  const parsed = parseFormula(testCase.formula)
  if (!parsed.ok) {
    // A formula that cannot compile belongs in syntaxErrorCases, not here.
    throw new Error(
      `Vector "${testCase.name}" failed to compile: ${parsed.error.code} ${parsed.error.message}`,
    )
  }
  const { $comment, ...rest } = testCase
  void $comment
  return { ...rest, ast: parsed.ast as AstNode, dependsOn: parsed.dependsOn }
}

describe('golden vector compilation', () => {
  it('keeps goldenVectors.compiled.json in sync with goldenVectors.json', async () => {
    const compiled = {
      $comment: [
        'GENERATED FILE - do not edit by hand.',
        'Source: goldenVectors.json. Regenerate: cd frontend/user-portal && npm run vectors:build',
        'Consumed by the Java ComputedFieldGoldenVectorTest, which has no parser of its own.',
      ],
      generatedFrom: 'goldenVectors.json',
      divisionScale: suite.divisionScale,
      // Java asserts this equals ComputedFieldFunctions.allNames(), so a function added on one
      // side cannot quietly go missing on the other.
      functionNames: knownFunctionNames(),
      cases: suite.cases.map(compileCase),
      aliasDeduplicationCases: suite.aliasDeduplicationCases.map(compileCase),
    }
    await expect(`${JSON.stringify(compiled, null, 2)}\n`).toMatchFileSnapshot(COMPILED_PATH)
  })

  it('compiles every evaluation case to an AST', () => {
    for (const testCase of [...suite.cases, ...suite.aliasDeduplicationCases]) {
      const parsed = parseFormula(testCase.formula)
      expect(parsed.ok, `${testCase.name} should compile`).toBe(true)
    }
  })
})
