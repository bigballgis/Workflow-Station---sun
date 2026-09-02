/**
 * Match an FK scalar against same-process MAIN variables and pick a related attribute.
 * Mirrors backend {@code MainTableViewFkDisplaySupport} for portal hydration / unit tests.
 */
export function resolveFkDisplayAttribute(
  mainVars: Record<string, unknown> | null | undefined,
  fkValue: unknown,
  primaryKeyFields: string[] | null | undefined,
  displayField: string | null | undefined,
): unknown {
  if (!mainVars || fkValue == null || !displayField?.trim()) return undefined
  const fkScalar = scalarString(fkValue)
  if (!fkScalar) return undefined

  // 只按被引用表配置的主键匹配（ref_primary_key_fields）。
  // 此前这里还有一段「PK 元数据缺失时试 id / id_idw」的兜底：主键恰好叫这两个名字的表看似正常，
  // 而主键是别的名字、且行里恰好也有 id 列的表会**匹配到错误的行**并显示错误的关联属性 ——
  // 猜列名比不显示更糟。配置缺失时返回 undefined，调用方显示原始 FK 值。
  const pkFields = primaryKeyFields?.filter(f => !!f?.trim()) || []
  for (const pk of pkFields) {
    if (fkEquals(mainVars[pk], fkScalar)) {
      return mainVars[displayField]
    }
  }
  return undefined
}

function scalarString(value: unknown): string | null {
  if (value == null) return null
  if (typeof value === 'object') return null
  const s = String(value).trim()
  return s || null
}

function fkEquals(mainPkValue: unknown, fkScalar: string): boolean {
  const main = scalarString(mainPkValue)
  return main != null && main === fkScalar
}
