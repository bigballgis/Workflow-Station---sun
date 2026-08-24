/**
 * Automation flow 导出包 → 简易结构树的纯解析层(无 UI 依赖,便于单测)。
 *
 * 数据契约对照 vendored AP 源码 automation/packages/core/execution/src/lib/flows/actions/action.ts:
 * 链靠 nextAction 串联;ROUTER 的分支在 children(与 settings.branches 按下标对齐,
 * null 元素 = 空分支);LOOP_ON_ITEMS 的循环体在 firstLoopAction。
 * 未知 type 一律降级为 unknown 节点,解析器不 throw——schemaVersion 升级时最多丢展示细节。
 */

/** 导出包里的原始步骤节点(只声明解析需要的骨架字段,其余字段忽略) */
export interface RawFlowStep {
  name?: string
  displayName?: string
  type?: string
  settings?: {
    pieceName?: string
    actionName?: string
    triggerName?: string
    branches?: { branchName?: string }[]
  }
  nextAction?: RawFlowStep | null
  firstLoopAction?: RawFlowStep | null
  children?: (RawFlowStep | null)[]
}

/** hermesFlowExport 导出包(admin-center /automation/flows/{id}/export) */
export interface FlowExportPackage {
  hermesFlowExport?: number
  flowKey?: string | null
  sourceFlowId?: string
  displayName?: string
  schemaVersion?: string
  fromPublished?: boolean
  trigger?: RawFlowStep | null
}

export type FlowStepKind = 'trigger' | 'piece' | 'code' | 'router' | 'loop' | 'unknown'

export interface FlowBranchView {
  /** settings.branches[i].branchName;缺失为 null,展示层给「分支 N」默认文案 */
  label: string | null
  steps: FlowStepView[]
}

export interface FlowStepView {
  name: string
  displayName: string
  kind: FlowStepKind
  /** piece 短名(@scope/piece-x → piece-x);非 piece 节点为 null */
  pieceName: string | null
  /** actionName / triggerName;未知类型时为原始 type */
  detail: string | null
  /** 仅 router 有内容,与导出包 children 等长 */
  branches: FlowBranchView[]
  /** 仅 loop 有内容 */
  loopSteps: FlowStepView[]
}

/** \@activepieces/piece-x → piece-x,自研短名原样 */
export const shortPieceName = (name: string): string =>
  name.includes('/') ? name.split('/')[1] : name

const STEP_KIND_BY_TYPE: Record<string, FlowStepKind> = {
  PIECE_TRIGGER: 'trigger',
  EMPTY: 'trigger',
  PIECE: 'piece',
  CODE: 'code',
  ROUTER: 'router',
  LOOP_ON_ITEMS: 'loop'
}

/** 恶意/损坏数据护栏:总节点数超限即停走,防 nextAction 自引用死循环 */
const MAX_STEPS = 300

interface WalkBudget {
  left: number
}

const toView = (raw: RawFlowStep, budget: WalkBudget, isTrigger: boolean): FlowStepView => {
  const kind = isTrigger ? 'trigger' : (STEP_KIND_BY_TYPE[raw.type ?? ''] ?? 'unknown')
  const settings = raw.settings ?? {}
  return {
    name: raw.name ?? '',
    displayName: raw.displayName || raw.name || raw.type || '—',
    kind,
    pieceName: settings.pieceName ? shortPieceName(settings.pieceName) : null,
    detail: settings.actionName
      ?? settings.triggerName
      ?? (kind === 'unknown' ? raw.type ?? null : null),
    branches: kind === 'router'
      ? (raw.children ?? []).map((child, i) => ({
        label: settings.branches?.[i]?.branchName ?? null,
        steps: walkChain(child ?? null, budget, false)
      }))
      : [],
    loopSteps: kind === 'loop' ? walkChain(raw.firstLoopAction ?? null, budget, false) : []
  }
}

const walkChain = (
  start: RawFlowStep | null,
  budget: WalkBudget,
  isTrigger: boolean
): FlowStepView[] => {
  const out: FlowStepView[] = []
  let cur: RawFlowStep | null = start
  let first = isTrigger
  while (cur && budget.left > 0) {
    budget.left--
    out.push(toView(cur, budget, first))
    first = false
    cur = cur.nextAction ?? null
  }
  return out
}

/** 主链(trigger 起)展开为节点列表;嵌套分支/循环体挂在各节点上 */
export const parseFlowSteps = (pkg: FlowExportPackage | null | undefined): FlowStepView[] =>
  walkChain(pkg?.trigger ?? null, { left: MAX_STEPS }, true)
