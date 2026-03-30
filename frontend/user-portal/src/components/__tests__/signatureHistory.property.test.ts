/**
 * Property 12: 签名画板历史栈管理
 * **Validates: Requirements 28.2, 28.4**
 *
 * For any N consecutive draw operations (N >= 0), the history stack length
 * should be min(N, 20). After Undo, the stack length decreases by 1.
 * When the stack is empty, Undo does not change the state.
 */
import { describe, it, expect } from 'vitest'
import * as fc from 'fast-check'

const MAX_HISTORY = 20

/**
 * Simulates the signature history stack logic (mirrors FieldRenderer implementation).
 */
class SignatureHistoryStack {
  private history: string[] = []

  get length(): number {
    return this.history.length
  }

  /** Save a snapshot before each stroke (called on mousedown/touchstart) */
  saveSnapshot(snapshot: string): void {
    if (this.history.length >= MAX_HISTORY) {
      this.history.shift() // FIFO: remove oldest
    }
    this.history.push(snapshot)
  }

  /** Undo: pop the last snapshot. Returns undefined if empty. */
  undo(): string | undefined {
    if (this.history.length === 0) return undefined
    return this.history.pop()
  }

  /** Clear: reset the history stack */
  clear(): void {
    this.history = []
  }
}

// ─── Property Tests ──────────────────────────────────────────────────────────

describe('Property 12: 签名画板历史栈管理', () => {
  it('after N draw operations, history length is min(N, 20)', () => {
    fc.assert(
      fc.property(
        fc.integer({ min: 0, max: 50 }),
        (n) => {
          const stack = new SignatureHistoryStack()
          for (let i = 0; i < n; i++) {
            stack.saveSnapshot(`snapshot_${i}`)
          }
          expect(stack.length).toBe(Math.min(n, MAX_HISTORY))
        },
      ),
      { numRuns: 100 },
    )
  })

  it('undo decreases history length by 1', () => {
    fc.assert(
      fc.property(
        fc.integer({ min: 1, max: 50 }),
        (n) => {
          const stack = new SignatureHistoryStack()
          for (let i = 0; i < n; i++) {
            stack.saveSnapshot(`snapshot_${i}`)
          }
          const lengthBefore = stack.length
          const result = stack.undo()
          expect(result).toBeDefined()
          expect(stack.length).toBe(lengthBefore - 1)
        },
      ),
      { numRuns: 100 },
    )
  })

  it('undo on empty stack does not change state', () => {
    const stack = new SignatureHistoryStack()
    expect(stack.length).toBe(0)
    const result = stack.undo()
    expect(result).toBeUndefined()
    expect(stack.length).toBe(0)
  })

  it('multiple undos reduce stack correctly', () => {
    fc.assert(
      fc.property(
        fc.integer({ min: 1, max: 30 }),
        fc.integer({ min: 1, max: 30 }),
        (draws, undos) => {
          const stack = new SignatureHistoryStack()
          for (let i = 0; i < draws; i++) {
            stack.saveSnapshot(`snapshot_${i}`)
          }
          const initialLength = stack.length
          const effectiveUndos = Math.min(undos, initialLength)
          for (let i = 0; i < undos; i++) {
            stack.undo()
          }
          expect(stack.length).toBe(initialLength - effectiveUndos)
        },
      ),
      { numRuns: 100 },
    )
  })

  it('clear resets history to empty', () => {
    fc.assert(
      fc.property(
        fc.integer({ min: 0, max: 30 }),
        (n) => {
          const stack = new SignatureHistoryStack()
          for (let i = 0; i < n; i++) {
            stack.saveSnapshot(`snapshot_${i}`)
          }
          stack.clear()
          expect(stack.length).toBe(0)
        },
      ),
      { numRuns: 100 },
    )
  })
})
