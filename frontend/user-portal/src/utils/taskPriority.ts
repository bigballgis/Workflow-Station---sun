/**
 * Maps Flowable / API priority values onto the four To Do ENUM bands.
 * Must stay aligned with TaskQueryColumnFilters.priorityBand and
 * EngineTaskPushdown.priorityBandBounds (URGENT ≥75, HIGH ≥50, NORMAL ≥25, else LOW).
 * The engine stores an int (default 50); the portal API often serializes it as "50".
 */
export type TaskPriorityBand = 'URGENT' | 'HIGH' | 'NORMAL' | 'LOW'

const NAMED_BANDS = new Set<string>(['URGENT', 'HIGH', 'NORMAL', 'LOW'])

function bandFromNumber(n: number): TaskPriorityBand {
  if (n >= 75) return 'URGENT'
  if (n >= 50) return 'HIGH'
  if (n >= 25) return 'NORMAL'
  return 'LOW'
}

export function taskPriorityBand(raw: string | number | null | undefined): TaskPriorityBand {
  if (raw === null || raw === undefined) {
    return 'NORMAL'
  }
  if (typeof raw === 'number') {
    if (!Number.isFinite(raw)) {
      return 'NORMAL'
    }
    return bandFromNumber(raw)
  }
  const trimmed = raw.trim()
  if (!trimmed) {
    return 'NORMAL'
  }
  const upper = trimmed.toUpperCase()
  if (NAMED_BANDS.has(upper)) {
    return upper as TaskPriorityBand
  }
  if (/^-?\d+$/.test(trimmed)) {
    return bandFromNumber(Number.parseInt(trimmed, 10))
  }
  return 'NORMAL'
}

export function taskPriorityCssClass(raw: string | number | null | undefined): string {
  return taskPriorityBand(raw).toLowerCase()
}
