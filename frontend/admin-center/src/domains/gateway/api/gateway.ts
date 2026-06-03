import request from '@/api/request'

// ==================== API Definitions ====================
export function createApi(data: any) { return request.post('/gateway/apis', data) }
export function listApis(params: any) { return request.get('/gateway/apis', { params }) }
export function getApi(apiId: number) { return request.get(`/gateway/apis/${apiId}`) }
export function updateApi(apiId: number, data: any) { return request.put(`/gateway/apis/${apiId}`, data) }
export function createApiVersion(apiId: number, data: any) { return request.post(`/gateway/apis/${apiId}/versions`, data) }
export function listApiVersions(apiId: number, params?: any) { return request.get(`/gateway/apis/${apiId}/versions`, { params }) }
export function importOpenApi(data: any) { return request.post('/gateway/apis/import-openapi', data) }

// ==================== Applications ====================
export function createApp(data: any) { return request.post('/gateway/applications', data) }
export function listApps(params: any) { return request.get('/gateway/applications', { params }) }
export function getApp(appId: number) { return request.get(`/gateway/applications/${appId}`) }
export function updateApp(appId: number, data: any) { return request.put(`/gateway/applications/${appId}`, data) }
export function createCredential(appId: number, data: any) { return request.post(`/gateway/applications/${appId}/credentials`, data) }
export function listCredentials(appId: number, params?: any) { return request.get(`/gateway/applications/${appId}/credentials`, { params }) }

// ==================== Releases ====================
export function createRelease(data: any) { return request.post('/gateway/releases', data) }
export function listReleases(params: any) { return request.get('/gateway/releases', { params }) }
export function getRelease(releaseId: number) { return request.get(`/gateway/releases/${releaseId}`) }
export function submitTesting(releaseId: number) { return request.post(`/gateway/releases/${releaseId}/submit-testing`) }
export function publishRelease(releaseId: number) { return request.post(`/gateway/releases/${releaseId}/publish`) }
export function rollbackRelease(releaseId: number, data: any) { return request.post(`/gateway/releases/${releaseId}/rollback`, data) }
export function getReleaseHistory(releaseId: number, params?: any) { return request.get(`/gateway/releases/${releaseId}/history`, { params }) }

// ==================== Audit ====================
export function listAuditLogs(params: any) { return request.get('/gateway/audit', { params }) }
export function listAuditReleases(params: any) { return request.get('/gateway/audit/releases', { params }) }

// ==================== Phase 2: Promotion & Approval ====================
export function promoteRelease(releaseId: number, data: any) { return request.post(`/gateway/releases/${releaseId}/promote`, data) }
export function requestApproval(releaseId: number, data: any) { return request.post(`/gateway/releases/${releaseId}/request-approval`, data) }
export function approveRelease(releaseId: number, data: any) { return request.post(`/gateway/releases/${releaseId}/approve`, data) }

// ==================== Phase 2: Drift ====================
export function listDriftReports(params: any) { return request.get('/gateway/drift/reports', { params }) }
export function getDriftReport(reportId: number) { return request.get(`/gateway/drift/reports/${reportId}`) }
export function triggerDriftSync(data: any) { return request.post('/gateway/drift/sync', data) }

// ==================== Phase 2: Monitoring ====================
export function getMonitoringOverview(params: any) { return request.get('/gateway/monitoring/overview', { params }) }
export function getApiMetrics(apiId: number, params: any) { return request.get(`/gateway/monitoring/apis/${apiId}`, { params }) }
