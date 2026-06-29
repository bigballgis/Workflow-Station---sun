import { functionUnitAxios } from './functionUnit'

export interface EmailTemplate {
  id: number
  name: string
  subject?: string
  bodyHtml?: string
  enabled: boolean
}

export interface EmailTemplateRequest {
  name: string
  subject?: string
  bodyHtml?: string
  enabled?: boolean
}

export const emailTemplateApi = {
  list(functionUnitId: number) {
    return functionUnitAxios.get<any, { data: EmailTemplate[] }>(
      `/api/v1/function-units/${functionUnitId}/email-templates`
    )
  },
  get(functionUnitId: number, templateId: number) {
    return functionUnitAxios.get<any, { data: EmailTemplate }>(
      `/api/v1/function-units/${functionUnitId}/email-templates/${templateId}`
    )
  },
  create(functionUnitId: number, data: EmailTemplateRequest) {
    return functionUnitAxios.post<any, { data: EmailTemplate }>(
      `/api/v1/function-units/${functionUnitId}/email-templates`,
      data
    )
  },
  update(functionUnitId: number, templateId: number, data: EmailTemplateRequest) {
    return functionUnitAxios.put<any, { data: EmailTemplate }>(
      `/api/v1/function-units/${functionUnitId}/email-templates/${templateId}`,
      data
    )
  },
  delete(functionUnitId: number, templateId: number) {
    return functionUnitAxios.delete(
      `/api/v1/function-units/${functionUnitId}/email-templates/${templateId}`
    )
  }
}
