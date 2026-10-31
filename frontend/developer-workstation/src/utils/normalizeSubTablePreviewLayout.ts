/** Match Portal whole-item hiding and initialize named tabs before form-create mounts. */
export function normalizeSubTablePreviewLayout(rules: unknown[]): void {
  for (const value of rules) {
    if (!value || typeof value !== 'object' || Array.isArray(value)) continue
    const rule = value as Record<string, unknown>
    const props = (rule.props ?? {}) as Record<string, unknown>
    if (props.hidden === true) rule.hidden = true
    if (['el-tabs', 'elTabs', 'tabs'].includes(String(rule.type)) && props.modelValue == null && Array.isArray(rule.children)) {
      const first = rule.children[0] as { props?: { name?: unknown } } | undefined
      if (first?.props?.name != null) rule.props = { ...props, modelValue: first.props.name }
    }
    if (Array.isArray(rule.children)) normalizeSubTablePreviewLayout(rule.children)
  }
}
