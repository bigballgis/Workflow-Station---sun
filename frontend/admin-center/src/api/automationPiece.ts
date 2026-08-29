import { del, get, post } from './request'
import type { AdminListPage } from '@/types/common'
import type { ListColumnFilter } from '@platform-shared/list/columnMeta'

/** 后端 ApiResponse 包装(与 LdapSync 等接口同构) */
interface ApiEnvelope<T> {
  success: boolean
  data: T
}

export interface AutomationPieceSummary {
  id: string
  name: string
  displayName: string
  description: string | null
  logoUrl: string
  version: string
  pieceType: 'OFFICIAL' | 'CUSTOM'
  packageType: 'REGISTRY' | 'ARCHIVE'
  hasArchive: boolean
  /** 已停用(AP platform.filteredPieceNames 黑名单,设计器目录不可见) */
  disabled: boolean
  platformId: string | null
  actionCount: number
  triggerCount: number
  actionNames: string[]
  triggerNames: string[]
  categories: string[]
  authors: string[]
  minimumSupportedRelease: string
  maximumSupportedRelease: string
  projectUsage: number
  created: string
  updated: string
  versions?: AutomationPieceSummary[]
}

export interface AutomationPieceListQuery {
  page: number
  size: number
  keyword?: string
  filters?: Array<ListColumnFilter & { field: string }>
  sortField?: string
  sortDirection?: 'ASC' | 'DESC'
}

export const automationPieceApi = {
  /** 目录列表(含同名多版本) */
  list: () => get<ApiEnvelope<AutomationPieceSummary[]>>('/automation/pieces'),

  query: (body: AutomationPieceListQuery) =>
    post<ApiEnvelope<AdminListPage<AutomationPieceSummary>>>('/automation/pieces/query', body),

  /** 导出:烘焙件 → 元数据 JSON;ARCHIVE 件 → zip(元数据 + 运行时 tgz) */
  exportPiece: (name: string, version: string) =>
    get<Blob>('/automation/pieces/export', {
      params: { name, version },
      responseType: 'blob'
    }),

  /** 导入:上传 build-piece 产出的 tgz,服务端代理 AP 在线安装(ARCHIVE),免烘焙免重启 */
  importPiece: (file: File) => {
    const form = new FormData()
    form.append('file', file)
    return post<ApiEnvelope<{ name: string; version: string; displayName: string }>>(
      '/automation/pieces/import', form,
      { headers: { 'Content-Type': 'multipart/form-data' }, timeout: 120000 }
    )
  },

  /** 删除一个版本;有 flow 引用时后端 409(code=PIECE_IN_USE),force=true 强制 */
  deletePiece: (name: string, version: string, force = false) =>
    del<ApiEnvelope<unknown>>('/automation/pieces', { params: { name, version, force } }),

  /** 启停:只影响设计器目录可见性,存量 flow 照常运行 */
  togglePiece: (name: string, disabled: boolean) =>
    post<ApiEnvelope<unknown>>('/automation/pieces/toggle', null, { params: { name, disabled } })
}

/** 导出文件名(与后端命名规则一致):@activepieces/piece-x → piece-x[.json|-bundle.zip] */
export function exportFilename(piece: AutomationPieceSummary): string {
  const short = piece.name.includes('/') ? piece.name.split('/')[1] : piece.name
  return piece.hasArchive ? `${short}-${piece.version}-bundle.zip` : `${short}.json`
}
