import { functionUnitAxios } from './functionUnit'

export type EmailConnectionDirection = 'OUTBOUND' | 'INBOUND' | 'BOTH'

/** Writable directions when creating/updating connections (BOTH is legacy read-only). */
export type EmailConnectionDirectionInput = 'OUTBOUND' | 'INBOUND'

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
  passwordEnvKey?: string
  direction?: EmailConnectionDirection
  mailboxAddress?: string
  imapHost?: string
  imapPort?: number
  imapUseSsl?: boolean
}

export interface EmailConnectionTestResult {
  success: boolean
  message: string
  detail?: string
  causeChain?: string
}

export interface EmailConnectionRequest {
  name: string
  connectionType?: string
  host?: string
  port?: number
  username?: string
  passwordEnvKey?: string
  fromName?: string
  useTls?: boolean
  enabled?: boolean
  direction?: EmailConnectionDirectionInput
  mailboxAddress?: string
  imapHost?: string
  imapPort?: number
  imapUseSsl?: boolean
}

export interface VaultEnvOption {
  varKey: string
  displayName: string
}

export const connectionApi = {
  list(functionUnitId: number) {
    return functionUnitAxios.get<any, { data: EmailConnection[] }>(
      `/api/v1/function-units/${functionUnitId}/connections`
    )
  },
  vaultOptions(functionUnitId: number) {
    return functionUnitAxios.get<any, { data: VaultEnvOption[] }>(
      `/api/v1/function-units/${functionUnitId}/connections/vault-options`
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
    return functionUnitAxios.post<any, { data: EmailConnectionTestResult }>(
      `/api/v1/function-units/${functionUnitId}/connections/${connectionId}/test`,
      { testRecipient }
    )
  }
}
