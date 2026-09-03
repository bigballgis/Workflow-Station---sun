import type { MainTableViewQueryRequest } from '@/api/mainTableView'

/**
 * The view-detail page loads one list row by its issued {@code rowKey}.
 * That key is not a keyword — ILIKE against it cannot find a SUB row.
 */
export function viewDetailRowQuery(rowKey: string): MainTableViewQueryRequest {
  return { page: 0, size: 1, rowKey }
}
