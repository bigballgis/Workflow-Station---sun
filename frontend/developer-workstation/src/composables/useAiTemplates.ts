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
      promptTemplate: 'Create a complete CRUD function unit that includes: a main table (with common fields such as name, description, status, created time), a sub table, a main form (PROCESS type), a sub form (TASK type), create/read/update/delete actions, and a simple approval process'
    },
    {
      id: 'approval',
      nameKey: 'ai.template.approval.name',
      descriptionKey: 'ai.template.approval.description',
      icon: 'Stamp',
      promptTemplate: 'Create a multi-level approval function unit that includes: an application table (with fields such as applicant, reason, amount), an approval form (with approval comment and approval result fields, configured with fieldPermissions), approval actions (Approve / Reject / Return, configured with visibilityCondition), and a BPMN process containing both parallel (all-must-sign) and inclusive (any-can-sign) gateways'
    },
    {
      id: 'data-entry',
      nameKey: 'ai.template.dataEntry.name',
      descriptionKey: 'ai.template.dataEntry.description',
      icon: 'EditPen',
      promptTemplate: 'Create a data entry function unit that includes: a main data table, multiple detail sub tables (with numeric fields), a complex form with formula calculations (formulas) and field linkages (linkages), summary rules (summaryRules using SUM and AVG), and data import/export actions'
    },
    {
      id: 'report',
      nameKey: 'ai.template.report.name',
      descriptionKey: 'ai.template.report.description',
      icon: 'DataAnalysis',
      promptTemplate: 'Create a report dashboard function unit that includes: a summary statistics table (with numeric aggregation fields), a detail data table, a form with summaryRules (SUM/AVG/COUNT/MIN/MAX), data query and export actions, and table relations (tableRelations)'
    }
  ]

  return { templates }
}
