/**
 * N8N_ACTION input assembly — extracted verbatim from useCustomActions so the
 * composable entry stays thin. Pure data shaping; no Vue/UI side effects.
 */

/**
 * Build the auto-populated input payload for an N8N action from its parsed
 * config and the current sub-table bindings. Returns an object with `data`
 * (sub-table rows) and optional `files` (collected attachment urls); returns
 * an empty object when the config does not request sub-table input.
 */
export function buildN8nAutoData(config: any, subTableBindings: any[]): Record<string, any> {
  const n8nAutoData: Record<string, any> = {}
  if (config.inputMapping?.source === 'sub_table') {
    const bindingName = config.inputMapping.subTableName
    if (bindingName) {
      const binding = subTableBindings.find(
        (b: any) => b.tableName === bindingName || String(b.bindingId) === bindingName
      )
      if (binding) {
        const rows = Array.isArray(binding.data) ? binding.data : []
        n8nAutoData.data = rows
        const fileFields = (config.inputMapping.fileFields || []) as string[]
        const fileUrls: string[] = []
        rows.forEach((row: any) => {
          fileFields.forEach((field: string) => {
            const cell = row?.[field]
            if (Array.isArray(cell)) {
              cell.forEach((f: any) => { if (f?.url) fileUrls.push(f.url) })
            } else if (cell?.url) {
              fileUrls.push(cell.url)
            }
          })
        })
        if (fileUrls.length > 0) n8nAutoData.files = fileUrls
      }
    }
  }
  return n8nAutoData
}
