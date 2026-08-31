import { describe, expect, it, beforeEach } from 'vitest'
import {
  TODO_TOOLBAR_QUERY_KEY,
  clearTodoToolbarQuery,
  readTodoToolbarQuery,
  writeTodoToolbarQuery,
} from '../todoToolbarQuery'

describe('todoToolbarQuery', () => {
  beforeEach(() => {
    sessionStorage.clear()
  })

  it('returns empty filters when nothing is stored', () => {
    expect(readTodoToolbarQuery()).toEqual({
      keyword: '',
      assignmentTypes: [],
      priorities: [],
    })
  })

  it('round-trips toolbar query for the same session key', () => {
    writeTodoToolbarQuery({
      keyword: '请假',
      assignmentTypes: ['USER', 'DELEGATED'],
      priorities: ['HIGH'],
    })
    expect(sessionStorage.getItem(TODO_TOOLBAR_QUERY_KEY)).toContain('请假')
    expect(readTodoToolbarQuery()).toEqual({
      keyword: '请假',
      assignmentTypes: ['USER', 'DELEGATED'],
      priorities: ['HIGH'],
    })
  })

  it('drops a corrupt session entry instead of crashing the list', () => {
    sessionStorage.setItem(TODO_TOOLBAR_QUERY_KEY, '{not-json')
    expect(readTodoToolbarQuery()).toEqual({
      keyword: '',
      assignmentTypes: [],
      priorities: [],
    })
  })

  it('ignores non-string filter arrays', () => {
    sessionStorage.setItem(
      TODO_TOOLBAR_QUERY_KEY,
      JSON.stringify({ keyword: 12, assignmentTypes: ['USER', 1], priorities: 'HIGH' }),
    )
    expect(readTodoToolbarQuery()).toEqual({
      keyword: '',
      assignmentTypes: [],
      priorities: [],
    })
  })

  it('clears the stored query on Reset', () => {
    writeTodoToolbarQuery({ keyword: '请假', assignmentTypes: ['USER'], priorities: [] })
    clearTodoToolbarQuery()
    expect(sessionStorage.getItem(TODO_TOOLBAR_QUERY_KEY)).toBeNull()
    expect(readTodoToolbarQuery().keyword).toBe('')
  })
})
