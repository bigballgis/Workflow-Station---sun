package com.admin.service;

import com.admin.dto.response.AutomationFlowSummary;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;
import java.util.Optional;

/**
 * 自动化 flow 迁移管理（uat → prod 发布通道）+ 引擎部署期 flowId 解析。
 *
 * <p>与 piece 管理同模式：读走共库 SQL（AP 表），写一律经 AP API（按当前操作人换取的会话），
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
     * 部署期解析（引擎调用）：ref 是 BPMN 里的 {@code ap:flowKey} 业务键或 legacy
     * {@code ap:flowId}。本环境存在同 id 的 flow 时原样返回；否则按迁移键
     * （{@code metadata.hermesFlowKey}）查找映射。找不到返回 empty。
     *
     * <p>返回值带 {@code published} 标记：FR-C05 要求引用了<b>未发布</b> flow 的部署显式
     * 失败，由 {@code /resolve} 端点将 {@code published=false} 映射为 404（错误信息说明
     * flow 未发布）。内部调用（导出、还原去重）不关心发布态，取 {@code flowId()} 即可。</p>
     */
    Optional<FlowResolution> resolveFlowRef(String ref);

    /** {@link #resolveFlowRef} 的结果：本环境 flowId + 是否已有发布版本 */
    record FlowResolution(String flowId, boolean published) {}

    /**
     * 按 BPMN 引用导出（FU 导出包随带 flow 时由 DW 调用）：ref 是 {@code ap:flowId}，
     * 可能是源环境 id，故先按 {@link #resolveFlowRef} 落到本环境 flow 再导出。
     * 引用在本环境解析不到（flow 已删）时返回 empty。
     */
    Optional<FlowExportFile> exportFlowByRef(String ref);

    /**
     * FU 导入包随带 flow 的还原：只补齐本环境<b>缺失</b>的 flow。迁移键已能解析到本环境
     * flow 的一律跳过（{@link FlowRestoreStatus#ALREADY_PRESENT}）——同环境重导/加版本时
     * 不能用包里的旧快照盖掉正在维护的草稿。
     *
     * <p>草稿写入失败即抛（FU 的自动化会因此不可用，不做静默跳过）；发布失败是可预期的
     * 环境差异（本环境缺 connection 凭据），以 {@link FlowRestoreStatus#PUBLISH_FAILED}
     * 连同原因回传，由调用方展示、运维在 Automation 管理页补齐后手工发布。</p>
     */
    List<FlowRestoreResult> restoreFlows(List<JsonNode> flowExports);

    enum FlowRestoreStatus {
        /** 本环境原本没有，已新建并发布 */
        CREATED,
        /** 本环境已有同 id/同迁移键的 flow，未改动 */
        ALREADY_PRESENT,
        /** 草稿已落地，发布未过（多为缺 connection 凭据） */
        PUBLISH_FAILED
    }

    /**
     * connection 清单比对（导入前预检）：按 externalId 查目标 project 里是否已有
     * 同名 connection。connection 凭据不随导出包走（设计使然），缺失项须在本环境
     * 手工重建后 flow 才能运行——导入本身不被阻塞。
     */
    List<ConnectionCheckItem> checkConnections(List<String> externalIds);

    /**
     * 启停：prod 日常运维的主控制。可逆、保留执行历史、保留 flowId，
     * 已部署 BPMN 的引用不会因此失效（只是不再被触发）。
     * 启用要求已有发布版本（webhook 只触发已发布版本），否则 AP 侧报错。
     */
    void setFlowEnabled(String flowId, boolean enabled);

    /**
     * 删除（不可逆）：AP 侧 {@code flow_run} 对 flow 是 ON DELETE CASCADE，
     * 执行历史会一并消失。{@code force=false} 时先做引用检查，被 FU 的 BPMN
     * 引用则抛 {@link FlowInUseException}。
     */
    void deleteFlow(String flowId, boolean force);

    /** 引用检查未通过：列出占用该 flow 的 Function Unit 名称 */
    class FlowInUseException extends RuntimeException {
        private final transient List<String> functionUnitNames;

        public FlowInUseException(String flowId, List<String> functionUnitNames) {
            super("flow " + flowId + " is referenced by " + functionUnitNames.size() + " function unit(s)");
            this.functionUnitNames = functionUnitNames;
        }

        public List<String> getFunctionUnitNames() {
            return functionUnitNames;
        }
    }

    record FlowExportFile(String filename, byte[] content) {}

    record ConnectionCheckItem(String externalId, boolean exists,
                               String displayName, String pieceName, String status) {}

    record FlowImportResult(String flowId, String flowKey, String displayName,
                            boolean created, boolean published) {}

    record FlowRestoreResult(String flowKey, String displayName, String flowId,
                             FlowRestoreStatus status, String detail) {}
}
