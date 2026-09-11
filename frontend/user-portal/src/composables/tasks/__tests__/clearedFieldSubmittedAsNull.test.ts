import { describe, it, expect } from 'vitest'

/**
 * 「清空一个字段」必须能在提交链路上表达出来。
 *
 * <p>复现：把 Case Status 的值叉掉再 Save，返回 200，刷新后旧值还在。
 * 控件清空后写回 `undefined`，而 `JSON.stringify` **丢弃值为 undefined 的键** ——
 * 请求体里压根没有 `case_status`；后端合并是 `updatedVariables.putAll(inbound)`，
 * 只能覆盖出现过的键，所以「清空」这个意图在链路上彻底丢失（已在真机用
 * 「键缺失」的 payload 复现：DB 里 case_status 保持 Open 不变）。
 *
 * <p>修法是提交前把**本表单自己的字段**中「加载时有值、现在为 undefined」的键补成 `null`。
 * 判据用 baseline 而不是单看 undefined：本来就空的字段同样表现为 undefined，
 * 不能把它们也当成「刚被清空」而平白发一堆 null。
 */

/** 与 useTaskForm.withClearedFieldsAsNull 同一份判据。 */
function withClearedFieldsAsNull(
  data: Record<string, any>,
  formFieldKeys: string[],
  baseline: Record<string, any>,
): Record<string, any> {
  const out = { ...data }
  for (const key of formFieldKeys) {
    if (out[key] !== undefined) continue
    const had = baseline[key]
    if (had !== undefined && had !== null && had !== '') {
      out[key] = null
    }
  }
  return out
}

describe('cleared field is submitted as null', () => {
  it('sends null for a field that had a value and was cleared', () => {
    const formData: Record<string, any> = { case_status: undefined, card_number: '55' }
    const baseline = { case_status: { status_name: 'Open' }, card_number: '55' }

    const out = withClearedFieldsAsNull(formData, ['case_status', 'card_number'], baseline)

    expect(Object.prototype.hasOwnProperty.call(out, 'case_status')).toBe(true)
    expect(out.case_status).toBeNull()
    // 关键：序列化后这个键必须还在,否则后端 putAll 看不到它。
    expect(JSON.parse(JSON.stringify(out))).toHaveProperty('case_status', null)
  })

  it('survives JSON round-trip where a bare undefined would vanish', () => {
    const naive = JSON.parse(JSON.stringify({ case_status: undefined, card_number: '55' }))
    expect(naive).not.toHaveProperty('case_status')

    const fixed = JSON.parse(JSON.stringify(
      withClearedFieldsAsNull({ case_status: undefined, card_number: '55' },
        ['case_status'], { case_status: 'Open' }),
    ))
    expect(fixed).toHaveProperty('case_status', null)
  })

  it('does not invent nulls for fields that were already empty', () => {
    const out = withClearedFieldsAsNull(
      { card_number: '55' },
      ['case_status', 'remark', 'card_number'],
      { case_status: null, remark: '', card_number: '55' },
    )

    expect(out).not.toHaveProperty('case_status')
    expect(out).not.toHaveProperty('remark')
    expect(out.card_number).toBe('55')
  })

  it('leaves fields outside this form untouched', () => {
    const out = withClearedFieldsAsNull(
      { case_status: undefined, other_form_field: undefined },
      ['case_status'],
      { case_status: 'Open', other_form_field: 'keep' },
    )

    expect(out.case_status).toBeNull()
    // 不在本表单字段列表里 -> 不补 null,避免清掉别处的值。
    expect(out.other_form_field).toBeUndefined()
    expect(JSON.parse(JSON.stringify(out))).not.toHaveProperty('other_form_field')
  })

  it('keeps a real edited value unchanged', () => {
    const out = withClearedFieldsAsNull(
      { case_status: { status_name: 'Closed' } },
      ['case_status'],
      { case_status: { status_name: 'Open' } },
    )
    expect(out.case_status).toEqual({ status_name: 'Closed' })
  })
})
