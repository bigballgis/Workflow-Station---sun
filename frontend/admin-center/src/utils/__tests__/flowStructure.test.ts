import { describe, expect, it } from 'vitest'
import {
  parseFlowSteps,
  shortPieceName,
  type FlowExportPackage,
  type RawFlowStep
} from '../flowStructure'

const pkg = (trigger: RawFlowStep | null): FlowExportPackage => ({
  hermesFlowExport: 1,
  displayName: 'fixture',
  schemaVersion: '23',
  fromPublished: true,
  trigger
})

describe('parseFlowSteps', () => {
  it('线性链:trigger → piece,piece 名短名化', () => {
    const steps = parseFlowSteps(pkg({
      name: 'trigger',
      type: 'PIECE_TRIGGER',
      displayName: 'Catch Webhook',
      settings: { pieceName: '@activepieces/piece-webhook', triggerName: 'catch_webhook' },
      nextAction: {
        name: 'step_1',
        type: 'PIECE',
        displayName: 'Return Response',
        settings: { pieceName: '@activepieces/piece-webhook', actionName: 'return_response' }
      }
    }))
    expect(steps).toHaveLength(2)
    expect(steps[0]).toMatchObject({
      kind: 'trigger',
      displayName: 'Catch Webhook',
      pieceName: 'piece-webhook',
      detail: 'catch_webhook'
    })
    expect(steps[1]).toMatchObject({
      kind: 'piece',
      displayName: 'Return Response',
      detail: 'return_response'
    })
  })

  it('ROUTER:children 与 branches 按下标对齐,null 子链 = 空分支,缺 branchName 得 null label', () => {
    const steps = parseFlowSteps(pkg({
      name: 'trigger',
      type: 'PIECE_TRIGGER',
      nextAction: {
        name: 'step_router',
        type: 'ROUTER',
        displayName: 'Route',
        settings: { branches: [{ branchName: 'On approve' }, {}] },
        children: [
          null,
          {
            name: 'step_2',
            type: 'PIECE',
            displayName: 'Notify',
            settings: { pieceName: 'biz-calendar', actionName: 'notify' }
          }
        ]
      }
    }))
    expect(steps).toHaveLength(2)
    const router = steps[1]
    expect(router.kind).toBe('router')
    expect(router.branches).toHaveLength(2)
    expect(router.branches[0]).toEqual({ label: 'On approve', steps: [] })
    expect(router.branches[1].label).toBeNull()
    expect(router.branches[1].steps).toHaveLength(1)
    expect(router.branches[1].steps[0]).toMatchObject({
      kind: 'piece',
      displayName: 'Notify',
      pieceName: 'biz-calendar'
    })
  })

  it('LOOP_ON_ITEMS:firstLoopAction 链进 loopSteps,循环后主链继续', () => {
    const steps = parseFlowSteps(pkg({
      name: 'trigger',
      type: 'PIECE_TRIGGER',
      nextAction: {
        name: 'step_loop',
        type: 'LOOP_ON_ITEMS',
        displayName: 'Loop items',
        firstLoopAction: {
          name: 'step_2',
          type: 'CODE',
          displayName: 'Transform',
          nextAction: { name: 'step_3', type: 'PIECE', displayName: 'Send' }
        },
        nextAction: { name: 'step_4', type: 'PIECE', displayName: 'After loop' }
      }
    }))
    expect(steps.map(s => s.name)).toEqual(['trigger', 'step_loop', 'step_4'])
    const loop = steps[1]
    expect(loop.kind).toBe('loop')
    expect(loop.loopSteps.map(s => s.displayName)).toEqual(['Transform', 'Send'])
    expect(loop.loopSteps[0].kind).toBe('code')
  })

  it('未知 type 降级为 unknown 节点并保留原始 type,不 throw', () => {
    const steps = parseFlowSteps(pkg({
      name: 'trigger',
      type: 'PIECE_TRIGGER',
      nextAction: { name: 'step_x', type: 'FUTURE_STEP_TYPE', displayName: 'Mystery' }
    }))
    expect(steps[1]).toMatchObject({ kind: 'unknown', detail: 'FUTURE_STEP_TYPE' })
  })

  it('空 trigger / 空包返回空列表', () => {
    expect(parseFlowSteps(pkg(null))).toEqual([])
    expect(parseFlowSteps(null)).toEqual([])
    expect(parseFlowSteps(undefined)).toEqual([])
  })

  it('nextAction 自引用不死循环(节点数护栏截断)', () => {
    const cyclic: RawFlowStep = { name: 'trigger', type: 'PIECE_TRIGGER' }
    cyclic.nextAction = cyclic
    const steps = parseFlowSteps(pkg(cyclic))
    expect(steps.length).toBeGreaterThan(0)
    expect(steps.length).toBeLessThanOrEqual(300)
  })
})

describe('shortPieceName', () => {
  it('scope 前缀截掉,自研短名原样', () => {
    expect(shortPieceName('@activepieces/piece-webhook')).toBe('piece-webhook')
    expect(shortPieceName('biz-calendar')).toBe('biz-calendar')
  })
})
