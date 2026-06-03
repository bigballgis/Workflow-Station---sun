import request from '@/api/request'

// ==================== Frontend Module Registry ====================

// Management APIs (Admin Center)
export function listModules(params: any) {
  return request.get('/frontend-modules', { params })
}

export function createModule(data: any) {
  return request.post('/frontend-modules', data)
}

export function updateModule(id: number, data: any) {
  return request.put(`/frontend-modules/${id}`, data)
}

export function enableModule(id: number) {
  return request.post(`/frontend-modules/${id}/enable`)
}

export function disableModule(id: number) {
  return request.post(`/frontend-modules/${id}/disable`)
}

export function switchVersion(id: number, data: { version: string; remoteEntryUrl?: string }) {
  return request.post(`/frontend-modules/${id}/switch-version`, data)
}

export function rollbackVersion(id: number, data: { targetVersion: string }) {
  return request.post(`/frontend-modules/${id}/rollback-version`, data)
}

// Runtime API (host consumption)
export function getRuntimeConfig(params: { hostApp: string; env: string }) {
  return request.get('/frontend-modules/runtime', { params })
}

// Version history (Phase 2)
export function getVersions(id: number) {
  return request.get(`/frontend-modules/${id}/versions`)
}

// Health check (Phase 2)
export function healthCheck(id: number) {
  return request.post(`/frontend-modules/${id}/health-check`)
}

// ==================== Package Export/Import ====================

/** Export MFE module package as zip blob */
export async function exportPackage(id: number): Promise<Blob> {
  const response = await request.get(`/frontend-modules/${id}/export`, {
    responseType: 'blob'
  })
  return response as unknown as Blob
}

/** Import MFE module package zip */
export function importPackage(targetEnv: string, file: File) {
  const formData = new FormData()
  formData.append('file', file)
  return request.post('/frontend-modules/import-package', formData, {
    params: { targetEnv },
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}
