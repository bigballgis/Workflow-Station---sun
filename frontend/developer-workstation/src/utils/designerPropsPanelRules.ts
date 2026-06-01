/**
 * fc-designer field panel: Hidden + Readonly live under Basis (not Props) for every component.
 */

export const DESIGNER_READONLY_PROP_RULE = {
  type: 'switch',
  field: 'formCreateProps>readonly',
  title: 'Readonly',
} as const

export const DESIGNER_HIDDEN_PROP_RULE = {
  type: 'switch',
  field: 'hidden',
  title: 'Hidden',
} as const

type PanelRule = Record<string, unknown>

const BASIS_FIELD_KEYS = new Set(['hidden', 'readonly', 'formCreateProps>readonly'])

function isHiddenOrReadonlyPanelRule(rule: PanelRule): boolean {
  const field = rule.field != null ? String(rule.field) : ''
  return BASIS_FIELD_KEYS.has(field)
}

/** Remove Hidden / Readonly from Props panel rules (Basis prepend supplies them). */
export function stripHiddenAndReadonlyFromPropsRules(rules: PanelRule[]): PanelRule[] {
  if (!Array.isArray(rules)) return []
  return rules.filter((rule) => !isHiddenOrReadonlyPanelRule(rule))
}

/** fc-designer config.baseRule — prepended at the top of the Basis section. */
export function buildDesignerBaseRulePrependHiddenReadonly(): {
  prepend: true
  rule: () => PanelRule[]
} {
  return {
    prepend: true,
    rule: () => [
      { ...DESIGNER_HIDDEN_PROP_RULE },
      { ...DESIGNER_READONLY_PROP_RULE },
    ],
  }
}

type ActiveDesignerRule = {
  _menu?: {
    props?: ((rule: ActiveDesignerRule, ctx: unknown) => PanelRule[]) | PanelRule[]
  }
}

/**
 * fc-designer `componentRule.default` as a function replaces the merged Props list;
 * re-run the menu props() and strip Hidden/Readonly (Basis owns them).
 */
export function buildDesignerComponentRuleDefaultStripHiddenReadonly(): (
  rule: ActiveDesignerRule,
  ctx: unknown,
) => PanelRule[] {
  return function designerComponentRuleDefault(rule, ctx) {
    const menuProps = rule?._menu?.props
    if (!menuProps) return []
    const raw =
      typeof menuProps === 'function' ? menuProps(rule, ctx) : menuProps
    const list = Array.isArray(raw) ? raw : []
    return stripHiddenAndReadonlyFromPropsRules(list)
  }
}

/** Hide duplicate built-in Basis rows when drag rules define their own hidden/readonly. */
export const DESIGNER_DRAG_RULE_HIDDEN_BASE_FIELDS = ['hidden', 'readonly'] as const

type DragRuleConfig = {
  props?: (...args: unknown[]) => PanelRule[]
  hiddenBaseField?: string[]
  [key: string]: unknown
}

type FcDesignerWithDragRule = {
  addDragRule: (config: DragRuleConfig) => void
}

/** Wrap addDragRule: Basis-only Hidden/Readonly; strip them from Props. */
export function installDesignerFieldPanelRules(FcDesigner: FcDesignerWithDragRule): void {
  const original = FcDesigner.addDragRule.bind(FcDesigner)
  FcDesigner.addDragRule = (config: DragRuleConfig) => {
    const hiddenBase = config.hiddenBaseField ?? []
    config.hiddenBaseField = [
      ...hiddenBase,
      ...DESIGNER_DRAG_RULE_HIDDEN_BASE_FIELDS.filter((f) => !hiddenBase.includes(f)),
    ]
    if (typeof config.props === 'function') {
      const originalProps = config.props
      config.props = function (this: unknown, ...args: unknown[]) {
        const raw = originalProps.apply(this, args) ?? []
        return stripHiddenAndReadonlyFromPropsRules(raw as PanelRule[])
      }
    }
    return original(config)
  }
}

/** @deprecated Use installDesignerFieldPanelRules */
export const installDesignerPropsReadonlyFirst = installDesignerFieldPanelRules

/** @deprecated Basis now owns Hidden/Readonly */
export function buildDesignerComponentRulePrependReadonlyHide(): undefined {
  return undefined
}

/** @deprecated Use stripHiddenAndReadonlyFromPropsRules */
export const stripReadonlyFromPropsRules = stripHiddenAndReadonlyFromPropsRules

/** @deprecated Use stripHiddenAndReadonlyFromPropsRules */
export function reorderDesignerPropsReadonlyFirst(rules: PanelRule[]): PanelRule[] {
  return stripHiddenAndReadonlyFromPropsRules(rules)
}
