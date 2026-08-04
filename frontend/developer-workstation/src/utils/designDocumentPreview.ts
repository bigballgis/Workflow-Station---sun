/**
 * 从 DESIGN 文档（Markdown）里解析出流程与数据模型，供聊天区的设计预览渲染。
 *
 * 设计阶段还没有 BPMN XML（那是 GENERATION 阶段的产物），但设计文档里已经用两张矩阵
 * 把流程说清楚了：`Process Node Matrix`（节点）+ `Sequence Flow Matrix`（连线）；
 * 数据模型同理由 `Table Structure`（表）+ `Field Design`（字段）两节组成。
 * 这里把它们读成结构化数据，用户就不必在一份上万字的 Markdown 里靠肉眼拼流程图。
 *
 * 取列一律按"表头包含这些关键词"来找，与后端 AiResponseParser 的 `cell(row, ...)` 同策略：
 * 模型给的表头措辞会漂（`Node ID` / `Node Id` / `节点 ID`），钉死列名会让预览动不动就空白。
 * 认不出的整节一律返回空，由调用方回落到原文视图——**预览缺失可以接受，预览错误不行**。
 */

export interface DesignProcessNode {
  id: string
  /** BPMN 类型原文（startEvent / userTask / exclusiveGateway…），未声明时为空串 */
  type: string
  name: string
  /** 绑定的任务表单，矩阵里用 – 表示无 */
  form: string
  /** 该节点上的动作名，已按逗号拆开 */
  actions: string[]
}

export interface DesignProcessFlow {
  id: string
  source: string
  target: string
  /** 条件表达式或 default 说明，无则空串 */
  condition: string
}

export interface DesignProcess {
  nodes: DesignProcessNode[]
  flows: DesignProcessFlow[]
}

export interface DesignTableField {
  name: string
  dataType: string
  details: string
}

export interface DesignTable {
  name: string
  type: string
  description: string
  fields: DesignTableField[]
}

/**
 * 矩阵里表示"无"的写法。前半段与后端 DESIGN_EMPTY_CELL_WORDS 对齐；
 * `unconditional` / `always` 是模型在"条件"列写"没有条件"的说法——把它画到连线上纯属噪声，
 * 每条主干线都会顶着一个 unconditional 标签。注意 `default` 不在此列：
 * 默认分支是有意义的信息（`default (REJECTED)`），要照常显示。
 */
const EMPTY_CELL_WORDS = new Set([
  '', '-', '–', '—', 'n/a', 'na', 'none', 'no', 'nil', 'null', 'not applicable', '无', '不适用',
  'unconditional', 'always', 'no condition', '无条件'
])

/**
 * 去掉 Markdown 行内标记。模型很爱把 ID 写成 `` `StartEvent_1` ``、把表名写成 `` `apartment` ``，
 * 带着反引号既会原样渲染到图上，也会让"节点 ID"与"连线端点"在别处对不上。
 */
function stripInlineMarkup(text: string): string {
  return text
    .replace(/`([^`]*)`/g, '$1')
    .replace(/\*\*([^*]*)\*\*/g, '$1')
    .replace(/^\s*\*([^*]+)\*\s*$/, '$1')
    .trim()
}

type MatrixRow = Record<string, string>

function isEmptyCell(value: string): boolean {
  return EMPTY_CELL_WORDS.has(stripInlineMarkup(value).toLowerCase())
}

/** 归一化后的单元格：视作"无"的写法一律变成空串。 */
function meaningful(value: string | undefined): string {
  if (!value) return ''
  return isEmptyCell(value) ? '' : stripInlineMarkup(value)
}

function splitRow(line: string): string[] {
  return line
    .replace(/^\s*\|/, '')
    .replace(/\|\s*$/, '')
    .split('|')
    .map(cell => cell.trim())
}

function isSeparatorRow(line: string): boolean {
  return /^\s*\|?[\s:|-]+\|[\s:|-]*$/.test(line) && line.includes('-')
}

/**
 * 读取某个标题下的第一张 Markdown 表格。
 *
 * @param content  文档全文
 * @param heading  标题中必须出现的文字（大小写不敏感），例如 `Process Node Matrix`
 */
function parseMatrix(content: string, heading: string): MatrixRow[] {
  const lines = content.split('\n')
  const headingIndex = lines.findIndex(
    line => /^#{1,6}\s/.test(line) && line.toLowerCase().includes(heading.toLowerCase())
  )
  if (headingIndex < 0) return []

  let index = headingIndex + 1
  // 标题与表格之间常夹着说明文字；遇到下一个标题就说明这一节没有表格。
  while (index < lines.length && !lines[index].trim().startsWith('|')) {
    if (/^#{1,6}\s/.test(lines[index])) return []
    index++
  }
  if (index + 1 >= lines.length || !isSeparatorRow(lines[index + 1])) return []

  const headers = splitRow(lines[index]).map(h => h.toLowerCase())
  const rows: MatrixRow[] = []
  for (let i = index + 2; i < lines.length; i++) {
    const line = lines[i]
    if (!line.trim().startsWith('|')) break
    const cells = splitRow(line)
    const row: MatrixRow = {}
    headers.forEach((header, position) => {
      row[header] = cells[position] ?? ''
    })
    rows.push(row)
  }
  return rows
}

/** 按"表头包含全部关键词"取列，容忍模型在表头措辞上的漂移。 */
function cell(row: MatrixRow, ...keywords: string[]): string {
  const key = Object.keys(row).find(header =>
    keywords.every(keyword => header.includes(keyword.toLowerCase()))
  )
  return key ? stripInlineMarkup(row[key]) : ''
}

/**
 * 表头与关键词完全相同才取——给 `| Field |` / `| 字段 |` 这种光杆表头兜底。
 *
 * 不能用 `cell()` 的子串规则去找 `field`：同一行的 `Field Type`、`Field Description`
 * 都含 field，名字列会取到类型或说明；中文的 `字段` 撞 `字段类型`、`字段说明` 同理。
 * 所以光杆词只认独占一列的写法。
 */
function exactCell(row: MatrixRow, keyword: string): string {
  const key = Object.keys(row).find(header => stripInlineMarkup(header) === keyword)
  return key ? stripInlineMarkup(row[key]) : ''
}

interface NameHeaders {
  /** 子串匹配：关键词组内每个词都要出现在表头里 */
  contains: string[][]
  /** 精确匹配：整列表头就是这个词 */
  exact: string[]
}

/**
 * "这一列是名字"的表头候选，按优先级从稳妥到激进排列。
 *
 * prompt（`ai-prompts/design.txt`）只规定了小节标题，没规定列名，措辞完全由模型发挥，
 * 实际见过 `Field Name` / `Column Name` / `字段名` / 光杆 `Field`。认不出的后果不是报错，
 * 而是整行被跳过——表清单全丢、或"表都在、字段全空"，预览看着正常却什么也没说。
 *
 * 带限定词的写法走子串匹配（`Field Name (EN)`、`字段名称` 都能命中）；
 * 光杆词有歧义，只能走 `exactCell`，且必须排在最后，否则 `Field Type` 会被当成名字列。
 */
const FIELD_NAME_HEADERS: NameHeaders = {
  contains: [['field', 'name'], ['column', 'name'], ['name'], ['字段名'], ['列名']],
  exact: ['field', 'column', '字段', '列']
}

const TABLE_NAME_HEADERS: NameHeaders = {
  contains: [['table', 'name'], ['name'], ['表名']],
  exact: ['table', '表', '数据表']
}

/** 依次试各种表头写法，取第一个有值的列。 */
function nameCell(row: MatrixRow, headers: NameHeaders): string {
  for (const keywords of headers.contains) {
    const value = cell(row, ...keywords)
    if (value) return value
  }
  for (const keyword of headers.exact) {
    const value = exactCell(row, keyword)
    if (value) return value
  }
  return ''
}

/**
 * 认出字段小节里"这张表叫什么"的小标题，两种写法都收：
 * `**lease_application**` 与 `#### \`lease_application\` (MAIN)`。
 * 后者括号里的类型顺手收下——表清单漏声明这张表时，它就是唯一的类型来源。
 */
function tableTitleOf(line: string): { name: string; type: string } | null {
  const trimmed = line.trim()
  const heading = trimmed.match(/^#{4,6}\s+(.+)$/)
  const bold = trimmed.match(/^\*\*(.+?)\*\*$/)
  const raw = heading?.[1] ?? bold?.[1]
  if (!raw) return null

  const withType = stripInlineMarkup(raw).match(/^(.*?)\s*\(([^)]+)\)\s*$/)
  const name = stripInlineMarkup(withType ? withType[1] : raw)
  if (!name) return null
  return { name, type: withType ? withType[2].trim() : '' }
}

/** 解析流程：节点矩阵 + 连线矩阵。任一为空都返回空流程，由调用方回落原文。 */
export function parseDesignProcess(content: string): DesignProcess {
  const nodes: DesignProcessNode[] = []
  for (const row of parseMatrix(content, 'Process Node Matrix')) {
    const id = cell(row, 'node', 'id') || cell(row, 'id')
    if (!id || isEmptyCell(id)) continue
    nodes.push({
      id,
      type: cell(row, 'type'),
      name: meaningful(cell(row, 'name')) || id,
      form: meaningful(cell(row, 'form')),
      actions: meaningful(cell(row, 'action'))
        .split(',')
        .map(action => action.trim())
        .filter(Boolean)
    })
  }

  const flows: DesignProcessFlow[] = []
  for (const row of parseMatrix(content, 'Sequence Flow Matrix')) {
    const source = cell(row, 'source')
    const target = cell(row, 'target')
    if (!source || !target || isEmptyCell(source) || isEmptyCell(target)) continue
    flows.push({
      id: cell(row, 'flow', 'id') || `${source}->${target}`,
      source,
      target,
      condition: meaningful(cell(row, 'condition'))
    })
  }

  return { nodes, flows }
}

/**
 * 解析数据模型：`Table Structure` 给表清单，`Field Design` 给每张表的字段。
 *
 * 字段小节里每张表的小标题有三种情况，都要认：`**TableName**`、
 * `#### \`table_name\` (MAIN)` 这种带类型后缀的四级标题，以及**只有一张表时干脆不写小标题**、
 * `### Field Design` 底下直接跟一张字段表。任何一种没认出来，后果都不是报错，
 * 而是"表都在、字段全空"——预览看着正常却什么也没说，比报错更难发现。
 *
 * 字段表出现了但表清单里没声明的表照样收下——宁可多显示一张，也不要静默吞掉设计内容。
 */
export function parseDesignTables(content: string): DesignTable[] {
  const tables: DesignTable[] = []
  const byName = new Map<string, DesignTable>()

  for (const row of parseMatrix(content, 'Table Structure')) {
    const name = nameCell(row, TABLE_NAME_HEADERS)
    if (!name || isEmptyCell(name)) continue
    const table: DesignTable = {
      name,
      type: meaningful(cell(row, 'type')),
      description: meaningful(cell(row, 'description')),
      fields: []
    }
    tables.push(table)
    byName.set(name.toLowerCase(), table)
  }

  const lines = content.split('\n')
  const fieldSectionStart = lines.findIndex(
    line => /^#{1,6}\s/.test(line) && line.toLowerCase().includes('field design')
  )
  if (fieldSectionStart < 0) return tables

  // 无小标题的字段表只在"表清单里就一张表"时才敢认领——多于一张就无从判断属于谁，
  // 与其猜错把字段挂到别的表上，不如这一节不显示字段（预览缺失可以接受，预览错误不行）。
  let current: DesignTable | null = tables.length === 1 ? tables[0] : null
  for (let i = fieldSectionStart + 1; i < lines.length; i++) {
    const line = lines[i]
    // 只在同级或更高级标题处停止：字段小节内部还会有 #### 级的每表小标题。
    if (/^#{1,3}\s/.test(line)) break

    const title = tableTitleOf(line)
    if (title) {
      current = byName.get(title.name.toLowerCase()) ?? null
      if (!current) {
        current = { name: title.name, type: title.type, description: '', fields: [] }
        tables.push(current)
        byName.set(title.name.toLowerCase(), current)
      } else if (!current.type && title.type) {
        current.type = title.type
      }
      continue
    }

    if (!current || !line.trim().startsWith('|') || !isSeparatorRow(lines[i + 1] ?? '')) continue

    const headers = splitRow(line).map(h => h.toLowerCase())
    let cursor = i + 2
    for (; cursor < lines.length; cursor++) {
      if (!lines[cursor].trim().startsWith('|')) break
      const cells = splitRow(lines[cursor])
      const row: MatrixRow = {}
      headers.forEach((header, position) => {
        row[header] = cells[position] ?? ''
      })
      const name = nameCell(row, FIELD_NAME_HEADERS)
      if (!name || isEmptyCell(name)) continue
      // 约束与描述分列时两列都要：模型常把"外键指向哪张表"这类关键信息写在 Description 里。
      const details = [
        meaningful(cell(row, 'constraint')) || meaningful(cell(row, 'detail')),
        // 说明列的表头模型换着写：Description / Remarks / Notes 都见过。
        meaningful(cell(row, 'description')) || meaningful(cell(row, 'remark'))
          || meaningful(cell(row, 'note'))
      ].filter(Boolean)
      current.fields.push({
        name,
        dataType: meaningful(cell(row, 'type')),
        details: [...new Set(details)].join(' — ')
      })
    }
    i = cursor - 1
  }

  return tables
}

// ==================== 流程图布局 ====================

export interface ProcessLayoutNode extends DesignProcessNode {
  x: number
  y: number
  width: number
  height: number
  /** 形状由 BPMN 类型决定，认不出时按任务画 */
  shape: 'event' | 'gateway' | 'task'
}

export interface ProcessLayoutEdge extends DesignProcessFlow {
  points: Array<{ x: number; y: number }>
  labelX: number
  labelY: number
}

export interface ProcessLayout {
  nodes: ProcessLayoutNode[]
  edges: ProcessLayoutEdge[]
  width: number
  height: number
}

const COLUMN_GAP = 190
const ROW_GAP = 96
const TASK_SIZE = { width: 148, height: 56 }
const GATEWAY_SIZE = { width: 46, height: 46 }
const EVENT_SIZE = { width: 38, height: 38 }
const PADDING = 24

function shapeOf(type: string): ProcessLayoutNode['shape'] {
  const normalized = type.toLowerCase()
  if (normalized.includes('gateway')) return 'gateway'
  if (normalized.includes('event')) return 'event'
  return 'task'
}

function sizeOf(shape: ProcessLayoutNode['shape']) {
  if (shape === 'gateway') return GATEWAY_SIZE
  if (shape === 'event') return EVENT_SIZE
  return TASK_SIZE
}

/**
 * 计算每个节点的列号：忽略回边后按最长路径分层。
 *
 * 回边必须先摘掉——"打回重提交"（Resubmit → Review）这类环在设计里很常见，
 * 若把回边也算进最长路径，环上每个节点都会被反复推远，七个节点排成一条一千多像素的直线，
 * 分支全被拉平、可视区里只看得见头两个节点。摘掉回边后分支才会在同一列上下并排。
 */
function longestPathDepth(ids: string[], flows: DesignProcessFlow[]): Map<string, number> {
  const outgoing = new Map<string, string[]>()
  flows.forEach(flow => outgoing.set(flow.source, [...(outgoing.get(flow.source) ?? []), flow.target]))

  // DFS 找回边：指向当前递归栈里的节点即为回边（环的闭合边）。
  const backEdges = new Set<string>()
  const state = new Map<string, 'visiting' | 'done'>()
  const walk = (id: string) => {
    state.set(id, 'visiting')
    for (const next of outgoing.get(id) ?? []) {
      const status = state.get(next)
      if (status === 'visiting') {
        backEdges.add(`${id}->${next}`)
      } else if (!status) {
        walk(next)
      }
    }
    state.set(id, 'done')
  }
  ids.forEach(id => {
    if (!state.has(id)) walk(id)
  })

  const forward = flows.filter(flow => !backEdges.has(`${flow.source}->${flow.target}`))
  const indegree = new Map<string, number>(ids.map(id => [id, 0]))
  const nextOf = new Map<string, string[]>()
  forward.forEach(flow => {
    nextOf.set(flow.source, [...(nextOf.get(flow.source) ?? []), flow.target])
    indegree.set(flow.target, (indegree.get(flow.target) ?? 0) + 1)
  })

  const depth = new Map<string, number>(ids.map(id => [id, 0]))
  const queue = ids.filter(id => (indegree.get(id) ?? 0) === 0)
  while (queue.length) {
    const id = queue.shift() as string
    for (const next of nextOf.get(id) ?? []) {
      depth.set(next, Math.max(depth.get(next) ?? 0, (depth.get(id) ?? 0) + 1))
      indegree.set(next, (indegree.get(next) ?? 0) - 1)
      if ((indegree.get(next) ?? 0) === 0) queue.push(next)
    }
  }
  return depth
}

/**
 * 分层布局：按"从起点出发的最长路径"决定列，同列内按出现顺序排行。
 *
 * 刻意不做真正的图布局（无交叉优化）——设计阶段的流程通常十来个节点，
 * 分层排布已经能一眼看懂走向；引入布局库只会让这个预览背上一个新依赖。
 * 环（打回重提交这类）用访问集合截断，不会死循环。
 */
export function layoutDesignProcess(process: DesignProcess): ProcessLayout {
  const nodes = process.nodes
  if (!nodes.length) return { nodes: [], edges: [], width: 0, height: 0 }

  const known = new Set(nodes.map(n => n.id))
  const flows = process.flows.filter(f => known.has(f.source) && known.has(f.target))

  const outgoing = new Map<string, string[]>()
  const indegree = new Map<string, number>()
  nodes.forEach(node => indegree.set(node.id, 0))
  flows.forEach(flow => {
    outgoing.set(flow.source, [...(outgoing.get(flow.source) ?? []), flow.target])
    indegree.set(flow.target, (indegree.get(flow.target) ?? 0) + 1)
  })

  const depth = longestPathDepth(nodes.map(n => n.id), flows)

  const columns = new Map<number, string[]>()
  nodes.forEach(node => {
    const column = depth.get(node.id) ?? 0
    columns.set(column, [...(columns.get(column) ?? []), node.id])
  })

  const positions = new Map<string, ProcessLayoutNode>()
  const tallest = Math.max(...[...columns.values()].map(ids => ids.length))
  const laneHeight = tallest * ROW_GAP

  nodes.forEach(node => {
    const column = depth.get(node.id) ?? 0
    const siblings = columns.get(column) ?? []
    const row = siblings.indexOf(node.id)
    const shape = shapeOf(node.type)
    const size = sizeOf(shape)
    const centerY = laneHeight / 2 + (row - (siblings.length - 1) / 2) * ROW_GAP
    positions.set(node.id, {
      ...node,
      shape,
      width: size.width,
      height: size.height,
      x: PADDING + column * COLUMN_GAP + (TASK_SIZE.width - size.width) / 2,
      y: PADDING + centerY - size.height / 2
    })
  })

  const edges: ProcessLayoutEdge[] = flows.map(flow => {
    const from = positions.get(flow.source) as ProcessLayoutNode
    const to = positions.get(flow.target) as ProcessLayoutNode
    const start = { x: from.x + from.width, y: from.y + from.height / 2 }
    const end = { x: to.x, y: to.y + to.height / 2 }
    const midX = (start.x + end.x) / 2
    // 折线走中点竖折，横平竖直，避免斜线在密集处糊成一团。
    const points = start.y === end.y
      ? [start, end]
      : [start, { x: midX, y: start.y }, { x: midX, y: end.y }, end]
    return { ...flow, points, labelX: midX, labelY: (start.y + end.y) / 2 - 6 }
  })

  const width = PADDING * 2 + (columns.size - 1) * COLUMN_GAP + TASK_SIZE.width
  const height = PADDING * 2 + laneHeight
  return { nodes: [...positions.values()], edges, width, height }
}
