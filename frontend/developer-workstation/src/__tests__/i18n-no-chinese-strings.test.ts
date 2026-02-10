import { describe, it, expect } from 'vitest'
import * as fs from 'fs'
import * as path from 'path'

/**
 * Property-Based Test for Frontend I18n Hardcoded Chinese Feature
 * Feature: frontend-i18n-hardcoded-chinese
 *
 * Property 1: 源文件无硬编码中文字符串
 * Scans all non-locale .vue and .ts files under admin-center/src and
 * developer-workstation/src, and verifies that string literals do not
 * contain Chinese characters (Unicode range \u4e00-\u9fff).
 *
 * **Validates: Requirements 1.1-7.3**
 */

// Chinese character regex (CJK Unified Ideographs)
const CHINESE_CHAR_REGEX = /[\u4e00-\u9fff]/

// Root paths for the two frontend projects (relative to this test file)
const FRONTEND_ROOT = path.resolve(__dirname, '..', '..', '..')
const ADMIN_CENTER_SRC = path.join(FRONTEND_ROOT, 'admin-center', 'src')
const DEV_WORKSTATION_SRC = path.join(FRONTEND_ROOT, 'developer-workstation', 'src')

/**
 * In-scope files per the design document's "受影响的文件清单" section.
 * Only these files are expected to have been fully i18n-ized for string literals.
 * Other files may still contain Chinese strings and are out of scope.
 */
const IN_SCOPE_FILES: string[] = [
  // Admin Center - user visible strings
  path.join(ADMIN_CENTER_SRC, 'views', 'user', 'UserList.vue'),
  path.join(ADMIN_CENTER_SRC, 'views', 'role', 'RoleList.vue'),
  path.join(ADMIN_CENTER_SRC, 'views', 'role', 'PermissionConfig.vue'),
  path.join(ADMIN_CENTER_SRC, 'views', 'user', 'components', 'UserImportDialog.vue'),
  path.join(ADMIN_CENTER_SRC, 'views', 'user', 'UserImport.vue'),
  // Developer Workstation - user visible strings
  path.join(DEV_WORKSTATION_SRC, 'utils', 'tagStorage.ts'),
  path.join(DEV_WORKSTATION_SRC, 'api', 'index.ts'),
  path.join(DEV_WORKSTATION_SRC, 'api', 'functionUnit.ts'),
]

/**
 * Recursively collect all .vue and .ts files under a directory,
 * excluding files inside i18n/locales/ directories.
 */
function collectSourceFiles(dir: string): string[] {
  const results: string[] = []

  function walk(currentDir: string) {
    if (!fs.existsSync(currentDir)) return
    const entries = fs.readdirSync(currentDir, { withFileTypes: true })
    for (const entry of entries) {
      const fullPath = path.join(currentDir, entry.name)
      if (entry.isDirectory()) {
        // Skip node_modules and __tests__ directories
        if (entry.name === 'node_modules' || entry.name === '__tests__') continue
        walk(fullPath)
      } else if (entry.isFile()) {
        const ext = path.extname(entry.name)
        if (ext === '.vue' || ext === '.ts') {
          // Exclude locale files (i18n/locales/ directory)
          const relativePath = fullPath.replace(/\\/g, '/')
          if (!relativePath.includes('i18n/locales/')) {
            results.push(fullPath)
          }
        }
      }
    }
  }

  walk(dir)
  return results
}

/**
 * Extract string literals from source code content.
 * Handles single-quoted, double-quoted, and template literal strings.
 * Returns an array of { line, content } for each string containing Chinese.
 */
function extractChineseStringLiterals(
  content: string
): Array<{ line: number; literal: string }> {
  const results: Array<{ line: number; literal: string }> = []
  const lines = content.split('\n')
  for (let i = 0; i < lines.length; i++) {
    const line = lines[i]
    const lineNum = i + 1
    const trimmedLine = line.trim()

    // Skip comment lines (JS single-line comments, HTML comments)
    if (trimmedLine.startsWith('//')) continue
    if (trimmedLine.startsWith('*')) continue // multi-line comment body
    if (trimmedLine.startsWith('/*')) continue
    if (trimmedLine.startsWith('<!--')) continue

    // For .vue files, check template attribute strings and script strings
    // For .ts files, check all string literals

    // Match single-quoted strings: 'content'
    const singleQuoteRegex = /'([^'\\]*(?:\\.[^'\\]*)*)'/g
    let match: RegExpExecArray | null
    while ((match = singleQuoteRegex.exec(line)) !== null) {
      const literal = match[1]
      if (CHINESE_CHAR_REGEX.test(literal)) {
        // Skip if this string is inside a comment on this line
        const beforeMatch = line.substring(0, match.index)
        if (beforeMatch.includes('//') || beforeMatch.includes('<!--')) continue
        results.push({ line: lineNum, literal })
      }
    }

    // Match double-quoted strings: "content"
    const doubleQuoteRegex = /"([^"\\]*(?:\\.[^"\\]*)*)"/g
    while ((match = doubleQuoteRegex.exec(line)) !== null) {
      const literal = match[1]
      if (CHINESE_CHAR_REGEX.test(literal)) {
        const beforeMatch = line.substring(0, match.index)
        if (beforeMatch.includes('//') || beforeMatch.includes('<!--')) continue
        results.push({ line: lineNum, literal })
      }
    }

    // Match template literals: `content` (single-line only for simplicity)
    const templateRegex = /`([^`\\]*(?:\\.[^`\\]*)*)`/g
    while ((match = templateRegex.exec(line)) !== null) {
      const literal = match[1]
      if (CHINESE_CHAR_REGEX.test(literal)) {
        const beforeMatch = line.substring(0, match.index)
        if (beforeMatch.includes('//') || beforeMatch.includes('<!--')) continue
        results.push({ line: lineNum, literal })
      }
    }
  }

  return results
}

/**
 * Normalize a file path for display (relative to frontend root).
 */
function displayPath(filePath: string): string {
  return path.relative(FRONTEND_ROOT, filePath).replace(/\\/g, '/')
}

describe('Property 1: 源文件无硬编码中文字符串', () => {
  /**
   * Property 1: Source files have no hardcoded Chinese strings
   *
   * For any non-locale .vue or .ts source file in admin-center/src and
   * developer-workstation/src that is in scope per the design document,
   * scanning its string literals (single-quoted, double-quoted, template literals)
   * should find no Chinese characters (Unicode range \u4e00-\u9fff).
   *
   * **Validates: Requirements 1.1-7.3**
   */
  it('should not contain Chinese characters in string literals of in-scope files', () => {
    // Collect all non-locale source files from both projects
    const allFiles = [
      ...collectSourceFiles(ADMIN_CENTER_SRC),
      ...collectSourceFiles(DEV_WORKSTATION_SRC),
    ]

    // Normalize in-scope file paths for comparison
    const inScopeNormalized = new Set(
      IN_SCOPE_FILES.map((f) => path.resolve(f).replace(/\\/g, '/'))
    )

    const violations: Array<{
      file: string
      line: number
      literal: string
    }> = []

    for (const filePath of allFiles) {
      const normalizedPath = path.resolve(filePath).replace(/\\/g, '/')

      // Only check files that are in scope
      if (!inScopeNormalized.has(normalizedPath)) continue

      const content = fs.readFileSync(filePath, 'utf-8')
      const chineseStrings = extractChineseStringLiterals(content)

      for (const { line, literal } of chineseStrings) {
        violations.push({
          file: displayPath(filePath),
          line,
          literal,
        })
      }
    }

    if (violations.length > 0) {
      const report = violations
        .map(
          (v) =>
            `  ${v.file}:${v.line} => "${v.literal}"`
        )
        .join('\n')
      expect.fail(
        `Found ${violations.length} Chinese string literal(s) in in-scope files:\n${report}`
      )
    }

    // Also verify that all in-scope files actually exist
    for (const filePath of IN_SCOPE_FILES) {
      expect(
        fs.existsSync(filePath),
        `In-scope file should exist: ${displayPath(filePath)}`
      ).toBe(true)
    }
  })

  it('should scan all non-locale files and report any Chinese strings (informational)', () => {
    // This test scans ALL non-locale files and reports Chinese strings found.
    // It does NOT fail for out-of-scope files, but provides visibility.
    const allFiles = [
      ...collectSourceFiles(ADMIN_CENTER_SRC),
      ...collectSourceFiles(DEV_WORKSTATION_SRC),
    ]

    const inScopeNormalized = new Set(
      IN_SCOPE_FILES.map((f) => path.resolve(f).replace(/\\/g, '/'))
    )

    const inScopeViolations: Array<{ file: string; line: number; literal: string }> = []
    const outOfScopeViolations: Array<{ file: string; line: number; literal: string }> = []

    for (const filePath of allFiles) {
      const normalizedPath = path.resolve(filePath).replace(/\\/g, '/')
      const content = fs.readFileSync(filePath, 'utf-8')
      const chineseStrings = extractChineseStringLiterals(content)

      const isInScope = inScopeNormalized.has(normalizedPath)

      for (const { line, literal } of chineseStrings) {
        const entry = { file: displayPath(filePath), line, literal }
        if (isInScope) {
          inScopeViolations.push(entry)
        } else {
          outOfScopeViolations.push(entry)
        }
      }
    }

    // Fail only for in-scope violations
    if (inScopeViolations.length > 0) {
      const report = inScopeViolations
        .map((v) => `  ${v.file}:${v.line} => "${v.literal}"`)
        .join('\n')
      expect.fail(
        `Found ${inScopeViolations.length} Chinese string literal(s) in in-scope files:\n${report}`
      )
    }

    // Log out-of-scope violations for informational purposes (does not fail)
    if (outOfScopeViolations.length > 0) {
      console.log(
        `\n[INFO] Found ${outOfScopeViolations.length} Chinese string(s) in out-of-scope files (not failing):`
      )
      for (const v of outOfScopeViolations) {
        console.log(`  ${v.file}:${v.line} => "${v.literal}"`)
      }
    }
  })
})
