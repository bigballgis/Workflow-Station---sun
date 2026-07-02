import { describe, expect, it } from 'vitest'
import type { FormDefinition } from '@/api/functionUnit'
import { sortFormsByType } from '@/utils/formDesigner'

function form(id: number, formName: string, formType: FormDefinition['formType']): FormDefinition {
  return { id, formName, formType } as FormDefinition
}

describe('sortFormsByType', () => {
  it('orders Process, then Task, then Action', () => {
    const sorted = sortFormsByType([
      form(4, 'action_b', 'ACTION'),
      form(2, 'task_a', 'TASK'),
      form(1, 'process', 'PROCESS'),
      form(3, 'task_b', 'TASK'),
      form(5, 'action_a', 'ACTION'),
    ])

    expect(sorted.map(f => f.formName)).toEqual([
      'process',
      'task_a',
      'task_b',
      'action_a',
      'action_b',
    ])
  })

  it('does not mutate the input array', () => {
    const input = [form(2, 'b', 'TASK'), form(1, 'a', 'PROCESS')]
    const copy = [...input]
    sortFormsByType(input)
    expect(input).toEqual(copy)
  })
})
