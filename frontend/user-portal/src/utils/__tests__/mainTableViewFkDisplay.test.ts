import { describe, expect, it } from 'vitest'
import { resolveFkDisplayAttribute } from '../mainTableViewFkDisplay'

describe('resolveFkDisplayAttribute', () => {
  const mainVars = {
    case_number: 'CASE-100',
    legal_hold: 'Yes',
    status: 'Open',
  }

  it('matches FK scalar to MAIN PK and returns attribute', () => {
    expect(resolveFkDisplayAttribute(mainVars, 'CASE-100', ['case_number'], 'legal_hold'))
      .toBe('Yes')
    expect(resolveFkDisplayAttribute(mainVars, 'CASE-100', ['case_number'], 'status'))
      .toBe('Open')
  })

  it('returns undefined when FK does not match', () => {
    expect(resolveFkDisplayAttribute(mainVars, 'CASE-999', ['case_number'], 'legal_hold'))
      .toBeUndefined()
  })

  it('PK 元数据缺失时不匹配 —— 调用方显示原始 FK 值', () => {
    // 此前这里会兜底去试 'id' / 'id_idw'：主键恰好叫这两个名字的表看似正常，而主键是别的
    // 名字、行里又恰好有 id 列的表会**匹配到错误的行**并显示错误的关联属性。猜列名比不显示更糟。
    // 与后端 MainTableViewFkDisplaySupportTest 保持镜像一致。
    expect(resolveFkDisplayAttribute(
      { id: 'abc', title: 'Doc' },
      'abc',
      [],
      'title',
    )).toBeUndefined()
  })

  it('配置了主键就按主键匹配，不被同行的 id 列干扰', () => {
    const mainVars = { id: 'abc', row_id: 'R-7', title: 'Doc' }
    expect(resolveFkDisplayAttribute(mainVars, 'R-7', ['row_id'], 'title')).toBe('Doc')
    // 'abc' 是 id 列的值、不是配置的主键的值 —— 不得匹配
    expect(resolveFkDisplayAttribute(mainVars, 'abc', ['row_id'], 'title')).toBeUndefined()
  })
})
