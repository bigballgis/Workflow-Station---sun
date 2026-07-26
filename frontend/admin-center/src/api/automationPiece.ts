import { get } from './request'

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
}

export const automationPieceApi = {
  /** 目录列表(含同名多版本) */
  list: () => get<ApiEnvelope<AutomationPieceSummary[]>>('/automation/pieces'),

  /** 导出:烘焙件 → 元数据 JSON;ARCHIVE 件 → zip(元数据 + 运行时 tgz) */
  exportPiece: (name: string, version: string) =>
    get<Blob>('/automation/pieces/export', {
      params: { name, version },
      responseType: 'blob'
    })
}

/** 导出文件名(与后端命名规则一致):@activepieces/piece-x → piece-x[.json|-bundle.zip] */
export function exportFilename(piece: AutomationPieceSummary): string {
  const short = piece.name.includes('/') ? piece.name.split('/')[1] : piece.name
  return piece.hasArchive ? `${short}-${piece.version}-bundle.zip` : `${short}.json`
}
