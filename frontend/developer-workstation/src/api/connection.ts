import { functionUnitAxios } from './functionUnit'

export interface EmailConnection {
  id: number
  connectionUid: string
  name: string
  connectionType: string
  host: string
  port: number
  username?: string
  fromEmail: string
  fromName?: string
  useTls: boolean
  enabled: boolean
  hasPassword: boolean
}

export interface EmailConnectionRequest {
  name: string
  connectionType?: string
  host?: string
  port?: number
  username?: string
  password?: string
  fromName?: string
  useTls?: boolean
  enabled?: boolean
}

export const connectionApi = {
  list(functionUnitId: number) {
    return functionUnitAxios.get<any, { data: EmailConnection[] }>(
      `/api/v1/function-units/${functionUnitId}/connections`
    )
  },
  create(functionUnitId: number, data: EmailConnectionRequest) {
    return functionUnitAxios.post<any, { data: EmailConnection }>(
      `/api/v1/function-units/${functionUnitId}/connections`,
      data
    )
  },
  update(functionUnitId: number, connectionId: number, data: EmailConnectionRequest) {
    return functionUnitAxios.put<any, { data: EmailConnection }>(
      `/api/v1/function-units/${functionUnitId}/connections/${connectionId}`,
      data
    )
  },
  delete(functionUnitId: number, connectionId: number) {
    return functionUnitAxios.delete(
      `/api/v1/function-units/${functionUnitId}/connections/${connectionId}`
    )
  },
  test(functionUnitId: number, connectionId: number, testRecipient: string) {
    return functionUnitAxios.post<any, { data: { success: boolean; message: string } }>(
      `/api/v1/function-units/${functionUnitId}/connections/${connectionId}/test`,
      { testRecipient }
    )
  }
}
