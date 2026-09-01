package com.admin.service;

import com.admin.dto.response.AutomationFlowRunSummary;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;
import java.util.Optional;

/**
 * 自动化 flow 的执行记录（运维视角，跨 project）。
 *
 * <p>这页原本在 Developer Workstation 的 Automation → Run History；DW 只在 dev 存在
 * （不进 K8S 部署集），而"某次自动化为什么失败"是生产运维问题，故与 piece 目录、flow
 * 迁移一样收在 Admin Center。</p>
 *
 * <p>与 {@link AutomationFlowService} 同模式：列表读走共库 SQL（AP 的 {@code flow_run} 表）；
 * 逐步骤输出不在表里（存 AP 的 logs 文件），故详情按当前操作人换取会话后经 AP API 取。</p>
 */
public interface AutomationFlowRunService {

    /** Page-hydrate：按 id 取整行，顺序由调用方维持 */
    List<AutomationFlowRunSummary> findRunsByIds(List<String> ids);

    /**
     * 一次运行的完整 JSON（含 {@code steps} 逐步骤输出），经 AP API 取。
     *
     * <p>AP 的 {@code GET /v1/flow-runs/:id} 按 token 所属 project 取数：运行落在当前
     * 操作人会话之外的 project（如某人的 Personal Project）时 AP 返回 404/403，
     * 这里同样返回 empty，由调用方按"看不到这条运行"处理，而不是伪造一个空详情。</p>
     */
    Optional<JsonNode> getRunDetail(String runId);
}
