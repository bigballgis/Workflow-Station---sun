package com.admin.service;

import com.admin.dto.response.AutomationPieceSummary;

import java.util.List;

/**
 * 自动化组件(piece)目录只读服务(P1)。
 *
 * <p>读路径直接查同库的 {@code piece_metadata} 表(只读,不写);
 * 写路径(安装/删除,P2)必须经 AP API 走,以触发其 Redis pubsub 缓存失效。</p>
 */
public interface AutomationPieceService {

    /** 列出全部 piece(含同名多版本),按 name、version 倒序。 */
    List<AutomationPieceSummary> listPieces();

    /**
     * 导出一个 piece 的离线物料。
     *
     * <p>无 archive(烘焙件)→ 元数据 JSON,与 {@code deploy/pieces/metadata/piece-*.json}
     * 逐字段同构,可直接用于 generate-metadata-seed.js;有 archive(ARCHIVE 件)→
     * zip(元数据 JSON + 运行时 tgz)。</p>
     */
    PieceExportFile exportPiece(String name, String version);

    /** 导出产物:文件名 + MIME + 字节流。 */
    record PieceExportFile(String filename, String contentType, byte[] content) {
    }
}
