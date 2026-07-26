package com.admin.service;

import com.admin.dto.response.AutomationPieceSummary;

import java.util.List;

/**
 * 自动化组件(piece)目录服务。
 *
 * <p>读路径(P1)直接查同库的 {@code piece_metadata} / {@code platform} 表(只读,不写);
 * 写路径(P2:导入/删除/启停)一律经 AP API 走——由 AP 落库并发 Redis pubsub
 * 让全部节点刷新进程内 piece 缓存,绝不直写表(直写不触发缓存失效)。</p>
 */
public interface AutomationPieceService {

    /** 列出全部 piece(含同名多版本),按 name、version 倒序。 */
    List<AutomationPieceSummary> listPieces();

    /**
     * 导入(在线安装)一个自研 piece:上传 build-piece 产出的 tgz,
     * 服务端解析包内 package.json 取 name/version,代理 AP
     * {@code POST /v1/pieces}(packageType=ARCHIVE)。装完立即在设计器可见,无需重启。
     */
    PieceImportResult importPiece(byte[] tarball, String filename);

    /**
     * 删除一个 piece 版本(代理 AP {@code DELETE /v1/pieces},HERMES-PATCH 端点)。
     *
     * @throws PieceInUseException 有 flow 引用该组件且未 force 时
     */
    void deletePiece(String name, String version, boolean force);

    /** 启用/停用:写 AP platform.filteredPieceNames(BLOCKED 语义,只影响设计器目录,存量 flow 照常运行)。 */
    void setPieceDisabled(String name, boolean disabled);

    /** 导入结果。 */
    record PieceImportResult(String name, String version, String displayName) {
    }

    /** 删除被 flow 引用拦截。 */
    class PieceInUseException extends RuntimeException {
        private final int flowCount;

        public PieceInUseException(String name, int flowCount) {
            super("piece " + name + " is referenced by " + flowCount + " flow(s)");
            this.flowCount = flowCount;
        }

        public int getFlowCount() {
            return flowCount;
        }
    }

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
