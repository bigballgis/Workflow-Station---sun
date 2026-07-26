package com.admin.service;

import com.admin.dto.response.AutomationFlowSummary;

import java.util.List;
import java.util.Optional;

/**
 * 自动化 flow 迁移管理（uat → prod 发布通道）+ 引擎部署期 flowId 解析。
 *
 * <p>与 piece 管理同模式：读走共库 SQL（AP 表），写一律经 AP API（共享账号会话），
 * 不直写 AP 表。见 DECISIONS Q5/Q7 与 {@code AutomationPieceService}。</p>
 */
public interface AutomationFlowService {

    /** 全部 flow 概要（管理面视角，跨 project） */
    List<AutomationFlowSummary> listFlows();

    /** 导出可携带 JSON（优先已发布版本，无发布则最新草稿） */
    FlowExportFile exportFlow(String flowId);

    /**
     * 导入（upsert）：按迁移键匹配本环境 flow（{@code id == flowKey} 或
     * {@code metadata.hermesFlowKey == flowKey}），命中则更新草稿，否则新建；
     * {@code publish} 为 true 时随后发布并启用。
     */
    FlowImportResult importFlow(byte[] json, boolean publish);

    /**
     * 部署期解析（引擎调用）：ref 是 BPMN 里的 {@code ap:flowId}。本环境存在同 id
     * 的 flow 时原样返回；否则按迁移键查找映射。找不到返回 empty。
     */
    Optional<String> resolveFlowRef(String ref);

    record FlowExportFile(String filename, byte[] content) {}

    record FlowImportResult(String flowId, String flowKey, String displayName,
                            boolean created, boolean published) {}
}
