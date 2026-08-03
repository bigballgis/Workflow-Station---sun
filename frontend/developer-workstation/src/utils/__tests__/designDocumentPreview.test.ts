import { describe, it, expect } from 'vitest'
import {
  parseDesignProcess,
  parseDesignTables,
  layoutDesignProcess
} from '@/utils/designDocumentPreview'

/**
 * 取自真实的 DESIGN 文档（deepseek-v4-pro 产出，2026-08-02），只裁掉与预览无关的章节。
 * 用真产物而不是手写理想格式：表头措辞、`–` 表示空、条件表达式里的反引号
 * 都是模型实际会写出来的样子，手写样例测不到这些。
 */
const DESIGN_DOC = `# Design Document

## Data Model
### Table Structure
| Table Name      | Table Type | Description                           |
|-----------------|------------|---------------------------------------|
| ExpenseReport   | MAIN       | Core expense reimbursement request    |
| ExpenseItem     | SUB        | One line item per expense             |

### Field Design
**ExpenseReport**

| Field Name     | Data Type     | Constraints / Details                  |
|----------------|---------------|----------------------------------------|
| id             | BIGINT        | AUTO_INCREMENT, PRIMARY KEY            |
| amount         | DECIMAL(12,2) | NOT NULL                               |

**ExpenseItem**

| Field Name     | Data Type     | Constraints / Details                  |
|----------------|---------------|----------------------------------------|
| report_id      | BIGINT        | FK to ExpenseReport                    |

## Process Design
### Process Node Matrix
| Node ID            | BPMN Type         | Name                 | Bound TASK Form      | Actions                    |
|--------------------|-------------------|----------------------|----------------------|----------------------------|
| StartEvent_1       | startEvent        | Start                | –                    | –                          |
| SubmitExpenseReport| userTask          | Submit Expense Report| Expense Report Form  | PROCESS_SUBMIT, CANCEL     |
| ManagerDecision    | exclusiveGateway  | Manager Decision     | –                    | –                          |
| EndApproved        | endEvent          | Approved             | –                    | –                          |

### Sequence Flow Matrix
| Flow ID  | Source Node ID      | Target Node ID      | Condition / Default        |
|----------|---------------------|---------------------|----------------------------|
| Flow_1   | StartEvent_1        | SubmitExpenseReport | –                          |
| Flow_2   | SubmitExpenseReport | ManagerDecision     | –                          |
| Flow_3   | ManagerDecision     | EndApproved         | \`\${outcome == 'APPROVED'}\` |
`

/**
 * 第二种真实写法（FU 50035，同一个模型的另一轮）：ID/表名包反引号、字段小节用
 * `#### \`name\` (TYPE)` 四级标题、条件列写 `unconditional`、并且有一条回边（打回重提交）。
 * 第一版预览就是因为只按上面那份文档的格式写死，在这份文档上表全空、流程排成一条直线。
 */
const BACKTICKED_DOC = `# Design Document

## Data Model
### Table Structure

| Table Name | Table Type | Description |
|------------|------------|-------------|
| \`apartment\` | SUB | Reference list of apartments |
| \`lease_application\` | MAIN | Lease application records |

### Field Design

#### \`apartment\` (SUB)
| Field Name | Type | Constraints | Description |
|------------|------|-------------|-------------|
| \`id\` | BIGINT AUTO_INCREMENT | PRIMARY KEY | Unique apartment identifier |
| \`floor\` | INT | | Floor number |

#### \`lease_application\` (MAIN)
| Field Name | Type | Constraints | Description |
|------------|------|-------------|-------------|
| \`apartment_id\` | BIGINT | NOT NULL, FOREIGN KEY | Selected apartment |

## Process Design

### Process Node Matrix

| Node ID | BPMN Type | Name | Bound TASK Form | Actions |
|---------|-----------|------|-----------------|---------|
| \`StartEvent_1\` | startEvent | Start | – | – |
| \`UserTask_Submit\` | userTask | Submit Lease Application | Lease Application Form | Submit, Save Draft |
| \`UserTask_Review\` | userTask | Property Manager Review | Review Form | Approve, Reject |
| \`Gateway_Decision\` | exclusiveGateway | Review Decision | – | – |
| \`UserTask_Resubmit\` | userTask | Resubmit Application | Resubmit Form | Resubmit |
| \`EndEvent_Approved\` | endEvent | Application Approved | – | – |

### Sequence Flow Matrix

| Flow ID | Source Node ID | Target Node ID | Condition / Default |
|---------|----------------|----------------|---------------------|
| \`Flow_1\` | \`StartEvent_1\` | \`UserTask_Submit\` | unconditional |
| \`Flow_2\` | \`UserTask_Submit\` | \`UserTask_Review\` | unconditional |
| \`Flow_3\` | \`UserTask_Review\` | \`Gateway_Decision\` | unconditional |
| \`Flow_4\` | \`Gateway_Decision\` | \`EndEvent_Approved\` | \`\${reviewDecision == 'APPROVE'}\` |
| \`Flow_5\` | \`Gateway_Decision\` | \`UserTask_Resubmit\` | \`\${reviewDecision == 'CLARIFY'}\` |
| \`Flow_6\` | \`UserTask_Resubmit\` | \`UserTask_Review\` | unconditional |
`

describe('parseDesignProcess', () => {
  it('reads nodes and flows out of the two design matrices', () => {
    const process = parseDesignProcess(DESIGN_DOC)

    expect(process.nodes.map(n => n.id)).toEqual([
      'StartEvent_1', 'SubmitExpenseReport', 'ManagerDecision', 'EndApproved'
    ])
    expect(process.flows.map(f => f.id)).toEqual(['Flow_1', 'Flow_2', 'Flow_3'])
  })

  it('keeps the node payload the diagram needs', () => {
    const task = parseDesignProcess(DESIGN_DOC).nodes[1]

    expect(task).toMatchObject({
      type: 'userTask',
      name: 'Submit Expense Report',
      form: 'Expense Report Form'
    })
    expect(task.actions).toEqual(['PROCESS_SUBMIT', 'CANCEL'])
  })

  /** `–` / `N/A` 这类占位不能当成真值渲染成节点标签或条件。 */
  it('treats dash placeholders as empty rather than as content', () => {
    const process = parseDesignProcess(DESIGN_DOC)

    expect(process.nodes[0].form).toBe('')
    expect(process.nodes[0].actions).toEqual([])
    expect(process.flows[0].condition).toBe('')
    expect(process.flows[2].condition).toContain("outcome == 'APPROVED'")
  })

  it('returns an empty process when the document has no matrices', () => {
    expect(parseDesignProcess('# Design Document\n\n## Overview\njust prose')).toEqual({
      nodes: [], flows: []
    })
  })

  /** 表头措辞会漂，取列是"包含关键词"而不是精确匹配。 */
  it('tolerates alternative matrix headers', () => {
    const doc = `### Process Node Matrix
| Node Id | Type     | Node Name |
|---------|----------|-----------|
| A       | userTask | Do a thing|

### Sequence Flow Matrix
| Id  | Source Ref | Target Ref |
|-----|------------|------------|
| f1  | A          | A          |
`
    const process = parseDesignProcess(doc)

    expect(process.nodes[0]).toMatchObject({ id: 'A', type: 'userTask', name: 'Do a thing' })
    expect(process.flows[0]).toMatchObject({ source: 'A', target: 'A' })
  })
})

describe('parseDesignTables', () => {
  it('joins the table list with the per-table field matrices', () => {
    const tables = parseDesignTables(DESIGN_DOC)

    expect(tables.map(t => t.name)).toEqual(['ExpenseReport', 'ExpenseItem'])
    expect(tables[0]).toMatchObject({ type: 'MAIN', description: 'Core expense reimbursement request' })
    expect(tables[0].fields.map(f => f.name)).toEqual(['id', 'amount'])
    expect(tables[0].fields[1]).toMatchObject({ dataType: 'DECIMAL(12,2)', details: 'NOT NULL' })
    expect(tables[1].fields.map(f => f.name)).toEqual(['report_id'])
  })

  /** 字段小节里出现、表清单里漏掉的表也要显示——设计内容不能被静默吞掉。 */
  it('keeps a table that only appears in the field section', () => {
    const doc = `### Table Structure
| Table Name | Table Type | Description |
|------------|------------|-------------|
| Declared   | MAIN       | listed      |

### Field Design
**Undeclared**

| Field Name | Data Type | Constraints / Details |
|------------|-----------|-----------------------|
| code       | VARCHAR   | NOT NULL              |
`
    const tables = parseDesignTables(doc)

    expect(tables.map(t => t.name)).toEqual(['Declared', 'Undeclared'])
    expect(tables[1].fields.map(f => f.name)).toEqual(['code'])
  })

  it('returns an empty list when the document has no data model section', () => {
    expect(parseDesignTables('# Design Document\n\n## Overview\njust prose')).toEqual([])
  })

  /**
   * 光杆表头 `| Table |` / `| Field |`：prompt 没有钉死列名，模型省掉 Name 是真会发生的。
   * 少了这条兜底就不是报错而是整节静默为空——表清单全丢、或表在字段全空。
   */
  it('accepts bare Table and Field headers without the word name', () => {
    const doc = `### Table Structure
| Table   | Type | Description |
|---------|------|-------------|
| invoice | MAIN | Invoices    |

### Field Design
| Field  | Data Type | Constraints |
|--------|-----------|-------------|
| id     | BIGINT    | PRIMARY KEY |
| amount | DECIMAL   | NOT NULL    |
`
    const tables = parseDesignTables(doc)

    expect(tables.map(t => t.name)).toEqual(['invoice'])
    expect(tables[0]).toMatchObject({ type: 'MAIN', description: 'Invoices' })
    expect(tables[0].fields.map(f => f.name)).toEqual(['id', 'amount'])
    expect(tables[0].fields[0].dataType).toBe('BIGINT')
  })

  /** 建表语境下模型常把字段列叫 Column；`Column Name` 早就能认，光杆 `Column` 也要认。 */
  it('accepts Column as the field name header', () => {
    const doc = `### Table Structure
| Table Name | Table Type | Description |
|------------|------------|-------------|
| invoice    | MAIN       | Invoices    |

### Field Design
| Column | Data Type | Constraints |
|--------|-----------|-------------|
| id     | BIGINT    | PRIMARY KEY |
`
    expect(parseDesignTables(doc)[0].fields.map(f => f.name)).toEqual(['id'])
  })

  /**
   * 中文表头：小节标题由 prompt 钉死（`### Table Structure` / `### Field Design`）不会变，
   * 但用户用中文提需求时模型会把列名写成中文，于是标题认得出、列一个都认不出。
   */
  it('accepts Chinese name headers under the English section titles', () => {
    const doc = `### Table Structure
| 表名 | 表类型 | 说明 |
|------|--------|------|
| invoice | MAIN | 发票主表 |

### Field Design
| 字段名 | 数据类型 | 约束 |
|--------|----------|------|
| id | BIGINT | 主键 |
| amount | DECIMAL | 非空 |
`
    const tables = parseDesignTables(doc)

    expect(tables.map(t => t.name)).toEqual(['invoice'])
    expect(tables[0].fields.map(f => f.name)).toEqual(['id', 'amount'])
  })

  /** 中文光杆表头同样要认，且 `字段` 不许被 `字段类型` / `字段说明` 抢走。 */
  it('accepts bare Chinese headers without taking the type column', () => {
    const doc = `### Table Structure
| 表 | 类型 | 说明 |
|----|------|------|
| invoice | MAIN | 发票主表 |

### Field Design
| 字段 | 字段类型 | 字段说明 |
|------|----------|----------|
| id | BIGINT | 主键 |
`
    const tables = parseDesignTables(doc)

    expect(tables.map(t => t.name)).toEqual(['invoice'])
    expect(tables[0].fields.map(f => f.name)).toEqual(['id'])
  })

  /** 兜底不能抢在正主前面：有 Field Name 时 `Field Type` 之流不许被当成名字列。 */
  it('still prefers the name column when other Field columns exist', () => {
    const doc = `### Table Structure
| Table Name | Table Type | Description |
|------------|------------|-------------|
| invoice    | MAIN       | Invoices    |

### Field Design
| Field Type | Field Name | Field Description |
|------------|------------|-------------------|
| BIGINT     | id         | Primary key       |
`
    const field = parseDesignTables(doc)[0].fields[0]

    expect(field.name).toBe('id')
    expect(field.dataType).toBe('BIGINT')
  })
})

describe('layoutDesignProcess', () => {
  it('lays nodes out in flow order and connects every known edge', () => {
    const layout = layoutDesignProcess(parseDesignProcess(DESIGN_DOC))

    const byId = new Map(layout.nodes.map(n => [n.id, n]))
    expect(byId.get('StartEvent_1')!.x).toBeLessThan(byId.get('SubmitExpenseReport')!.x)
    expect(byId.get('SubmitExpenseReport')!.x).toBeLessThan(byId.get('ManagerDecision')!.x)
    expect(byId.get('ManagerDecision')!.x).toBeLessThan(byId.get('EndApproved')!.x)
    expect(layout.edges).toHaveLength(3)
    expect(layout.width).toBeGreaterThan(0)
    expect(layout.height).toBeGreaterThan(0)
  })

  it('maps BPMN types onto the shapes the diagram draws', () => {
    const shapes = new Map(layoutDesignProcess(parseDesignProcess(DESIGN_DOC))
      .nodes.map(n => [n.id, n.shape]))

    expect(shapes.get('StartEvent_1')).toBe('event')
    expect(shapes.get('SubmitExpenseReport')).toBe('task')
    expect(shapes.get('ManagerDecision')).toBe('gateway')
  })

  /** 打回重提交会形成环：布局必须收敛，不能把预览挂死。 */
  it('terminates on a cyclic process', () => {
    const cyclic = {
      nodes: [
        { id: 'a', type: 'userTask', name: 'A', form: '', actions: [] },
        { id: 'b', type: 'userTask', name: 'B', form: '', actions: [] }
      ],
      flows: [
        { id: 'f1', source: 'a', target: 'b', condition: '' },
        { id: 'f2', source: 'b', target: 'a', condition: '' }
      ]
    }

    const layout = layoutDesignProcess(cyclic)

    expect(layout.nodes).toHaveLength(2)
    expect(layout.edges).toHaveLength(2)
  })

  /** 连线指向不存在的节点时只丢那一条，其余照画。 */
  it('drops edges that point at unknown nodes', () => {
    const layout = layoutDesignProcess({
      nodes: [{ id: 'a', type: 'userTask', name: 'A', form: '', actions: [] }],
      flows: [{ id: 'f1', source: 'a', target: 'ghost', condition: '' }]
    })

    expect(layout.nodes).toHaveLength(1)
    expect(layout.edges).toHaveLength(0)
  })

  it('returns an empty layout for an empty process', () => {
    expect(layoutDesignProcess({ nodes: [], flows: [] })).toEqual({
      nodes: [], edges: [], width: 0, height: 0
    })
  })
})

describe('backticked design documents', () => {
  /** 反引号必须剥掉：否则图上画出 \`StartEvent_1\`，表名显示成 \`apartment\`。 */
  it('strips inline code markers from ids, names and conditions', () => {
    const process = parseDesignProcess(BACKTICKED_DOC)

    expect(process.nodes.map(n => n.id)).toContain('StartEvent_1')
    expect(process.nodes.map(n => n.id).some(id => id.includes('`'))).toBe(false)
    expect(process.flows.map(f => f.source).some(s => s.includes('`'))).toBe(false)
    expect(process.flows[3].condition).toBe("${reviewDecision == 'APPROVE'}")
  })

  /** `unconditional` 是"没有条件"，画在每条主干线上纯属噪声。 */
  it('treats unconditional as no condition', () => {
    expect(parseDesignProcess(BACKTICKED_DOC).flows[0].condition).toBe('')
  })

  /** 字段小节用 `#### \`name\` (TYPE)` 时也要认，否则表都在、字段全空。 */
  it('reads fields from heading-style table titles', () => {
    const tables = parseDesignTables(BACKTICKED_DOC)

    expect(tables.map(t => t.name)).toEqual(['apartment', 'lease_application'])
    expect(tables[0].fields.map(f => f.name)).toEqual(['id', 'floor'])
    expect(tables[1].fields[0]).toMatchObject({ name: 'apartment_id', dataType: 'BIGINT' })
  })

  /** 约束与描述分列时都要留下，外键指向哪张表这类信息常写在 Description 里。 */
  it('keeps both the constraints and the description column', () => {
    const field = parseDesignTables(BACKTICKED_DOC)[0].fields[0]

    expect(field.details).toContain('PRIMARY KEY')
    expect(field.details).toContain('Unique apartment identifier')
  })

  /** 回边不能参与分层：否则六个节点排成一条直线，分支被拉平。 */
  it('ignores back edges so branches stack instead of stretching', () => {
    const layout = layoutDesignProcess(parseDesignProcess(BACKTICKED_DOC))
    const byId = new Map(layout.nodes.map(n => [n.id, n]))

    // 事件比任务窄，槽内居中后 x 自然不同，比的是列中心。
    const centerX = (id: string) => byId.get(id)!.x + byId.get(id)!.width / 2
    expect(centerX('EndEvent_Approved')).toBe(centerX('UserTask_Resubmit'))
    expect(byId.get('EndEvent_Approved')!.y).not.toBe(byId.get('UserTask_Resubmit')!.y)
    expect(layout.edges).toHaveLength(6)
  })
})

describe('field sections without per-table titles', () => {
  /**
   * 第三种真实写法（FU 50036）：只有一张表时模型干脆不写每表小标题，
   * `### Field Design` 底下直接跟字段表；说明列的表头也从 Description 换成了 Remarks。
   */
  const SINGLE_TABLE_DOC = `## Data Model
### Table Structure
| Table Name | Table Type | Description |
|------------|------------|-------------|
| \`handover_request\` | MAIN | Stores the handover request details and status |

### Field Design
| Field Name | Data Type | Constraints | Remarks |
|------------|-----------|-------------|---------|
| \`id\` | UUID | PK, auto-generated | |
| \`status\` | Enum | NOT NULL, default \`DRAFT\` | Values: DRAFT, APPROVED |

## Interface Design
`

  it('attaches an untitled field table to the only declared table', () => {
    const tables = parseDesignTables(SINGLE_TABLE_DOC)

    expect(tables).toHaveLength(1)
    expect(tables[0].name).toBe('handover_request')
    expect(tables[0].fields.map(f => f.name)).toEqual(['id', 'status'])
  })

  it('reads the remarks column as field details', () => {
    const status = parseDesignTables(SINGLE_TABLE_DOC)[0].fields[1]

    expect(status.details).toContain('NOT NULL')
    expect(status.details).toContain('Values: DRAFT, APPROVED')
  })

  /** 多张表又没有小标题时无从判断字段属于谁，宁可不显示也不能猜错挂到别的表上。 */
  it('does not guess an owner when several tables are declared', () => {
    const doc = SINGLE_TABLE_DOC.replace(
      '| \`handover_request\` | MAIN | Stores the handover request details and status |',
      '| \`handover_request\` | MAIN | Stores the handover request |\n| \`audit_log\` | SUB | Audit trail |')

    const tables = parseDesignTables(doc)

    expect(tables.map(t => t.name)).toEqual(['handover_request', 'audit_log'])
    expect(tables.every(t => t.fields.length === 0)).toBe(true)
  })
})
