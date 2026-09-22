import { del, get, post, put } from './request'

interface ApiEnvelope<T> {
  success: boolean
  data: T
}

export type EnvironmentValueKind = 'TEXT' | 'VAULT'

export interface EnvironmentVariable {
  id: string
  varKey: string
  deployEnv: string
  valueKind: EnvironmentValueKind
  displayName: string
  description?: string
  defaultValue?: string
  currentValue?: string
  vaultSecretPath?: string
}

export interface EnvironmentVariableRequest {
  varKey: string
  valueKind: EnvironmentValueKind
  displayName: string
  description?: string
  defaultValue?: string
  currentValue?: string
  vaultSecretPath?: string
}

export const environmentVariableApi = {
  list: () =>
    get<ApiEnvelope<EnvironmentVariable[]>>('/environment-variables').then((body) => {
      if (!Array.isArray(body.data)) {
        throw new Error('Environment variable list payload is missing')
      }
      return body.data
    }),
  create: (data: EnvironmentVariableRequest) =>
    post<ApiEnvelope<EnvironmentVariable>>('/environment-variables', data).then((body) => body.data),
  update: (id: string, data: EnvironmentVariableRequest) =>
    put<ApiEnvelope<EnvironmentVariable>>(`/environment-variables/${id}`, data).then((body) => body.data),
  remove: (id: string) =>
    del<ApiEnvelope<void>>(`/environment-variables/${id}`),
}
