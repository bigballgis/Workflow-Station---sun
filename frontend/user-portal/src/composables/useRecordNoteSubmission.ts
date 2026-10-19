/** One idempotency key survives a network retry; editing the pending payload creates a new submission. */
export function useRecordNoteSubmission(): { reset: () => void; keyFor: (signature: string) => string } {
  let key = crypto.randomUUID()
  let previous: string | undefined
  return {
    reset(): void { key = crypto.randomUUID(); previous = undefined },
    keyFor(signature: string): string {
      if (previous !== undefined && previous !== signature) key = crypto.randomUUID()
      previous = signature
      return key
    },
  }
}
