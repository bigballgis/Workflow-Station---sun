export interface AiTemplate {
  id: string
  nameKey: string
  descriptionKey: string
  icon: string
  promptTemplate: string
}

export function useAiTemplates() {
  const templates: AiTemplate[] = [
    {
      id: 'crud',
      nameKey: 'ai.template.crud.name',
      descriptionKey: 'ai.template.crud.description',
      icon: 'Grid',
      promptTemplate: '创建一个完整的CRUD功能单元，包含：主表（含常用字段如名称、描述、状态、创建时间）、子表、主表单（PROCESS类型）、子表单（TASK类型）、增删改查动作、简单的审批流程'
    },
    {
      id: 'approval',
      nameKey: 'ai.template.approval.name',
      descriptionKey: 'ai.template.approval.description',
      icon: 'Stamp',
      promptTemplate: '创建一个多级审批流程功能单元，包含：申请表（含申请人、申请原因、金额等字段）、审批表单（含审批意见、审批结果字段，配置fieldPermissions）、审批动作（同意/拒绝/退回，配置visibilityCondition）、包含会签和或签网关的BPMN流程'
    },
    {
      id: 'data-entry',
      nameKey: 'ai.template.dataEntry.name',
      descriptionKey: 'ai.template.dataEntry.description',
      icon: 'EditPen',
      promptTemplate: '创建一个数据录入功能单元，包含：主数据表、多个明细子表（含数值字段）、带公式计算（formulas）和字段联动（linkages）的复杂表单、汇总规则（summaryRules使用SUM和AVG）、数据导入导出动作'
    },
    {
      id: 'report',
      nameKey: 'ai.template.report.name',
      descriptionKey: 'ai.template.report.description',
      icon: 'DataAnalysis',
      promptTemplate: '创建一个报表仪表板功能单元，包含：汇总统计表（含数值聚合字段）、明细数据表、带summaryRules（SUM/AVG/COUNT/MIN/MAX）的表单、数据查询和导出动作、表间关系（tableRelations）'
    }
  ]

  return { templates }
}
