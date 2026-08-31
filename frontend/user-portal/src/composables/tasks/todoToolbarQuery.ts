export const TODO_TOOLBAR_QUERY_KEY = 'portal-list-query:todo-tasks'

export type TodoToolbarQuery = {
  keyword: string
  assignmentTypes: string[]
  priorities: string[]
}

const EMPTY: TodoToolbarQuery = {
  keyword: '',
  assignmentTypes: [],
  priorities: [],
}

function isStringArray(value: unknown): value is string[] {
  return Array.isArray(value) && value.every((item) => typeof item === 'string')
}

export function emptyTodoToolbarQuery(): TodoToolbarQuery {
  return {
    keyword: EMPTY.keyword,
    assignmentTypes: [],
    priorities: [],
  }
}

export function readTodoToolbarQuery(): TodoToolbarQuery {
  try {
    const raw = sessionStorage.getItem(TODO_TOOLBAR_QUERY_KEY)
    if (!raw) return emptyTodoToolbarQuery()
    const parsed = JSON.parse(raw) as Partial<TodoToolbarQuery>
    return {
      keyword: typeof parsed.keyword === 'string' ? parsed.keyword : '',
      assignmentTypes: isStringArray(parsed.assignmentTypes) ? [...parsed.assignmentTypes] : [],
      priorities: isStringArray(parsed.priorities) ? [...parsed.priorities] : [],
    }
  } catch {
    // FALLBACK(ux): corrupt session query costs remembered filters only; the list still loads.
    return emptyTodoToolbarQuery()
  }
}

export function writeTodoToolbarQuery(query: TodoToolbarQuery): void {
  try {
    sessionStorage.setItem(
      TODO_TOOLBAR_QUERY_KEY,
      JSON.stringify({
        keyword: query.keyword,
        assignmentTypes: [...query.assignmentTypes],
        priorities: [...query.priorities],
      }),
    )
  } catch {
    // FALLBACK(ux): quota errors must not block Search or opening a task.
  }
}

export function clearTodoToolbarQuery(): void {
  try {
    sessionStorage.removeItem(TODO_TOOLBAR_QUERY_KEY)
  } catch {
    // FALLBACK(ux): storage failure must not block Reset.
  }
}
