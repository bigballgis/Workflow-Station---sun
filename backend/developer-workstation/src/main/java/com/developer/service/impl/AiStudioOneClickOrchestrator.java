package com.developer.service.impl;

import com.developer.dto.AiStudioProposalJobResponse.Progress;
import com.developer.enums.AiStudioPhase;
import com.developer.exception.AiGenerationException;
import com.developer.service.AiStudioChatService.AgentOneClickDraft;
import com.developer.service.AiStudioChatService.StudioChatResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.UnaryOperator;

/**
 * AI Studio 一键生成经 Content Organizer 的逐阶段编排。
 *
 * <p>CO 的主管每次只把请求交给一个专家（{@code transfer_to_agent}，拿不回控制权），所以整套设计由这里按
 * 流程 → 表 → 表单 → 决策 → 动作 逐个发 PROPOSE。每一步只调 CO、解析，<b>不写库</b>：产出并入本轮设计，
 * 下一步的 CURRENT DESIGN 就带上它；五步都成功后由调用方走一次 scope {@code ALL} 的预览与 Apply。
 * 这样流程与动作在同一次写入里，{@code AiWriteServiceImpl#writeProcessDefinition} 才能把动作 id 注入 BPMN；
 * 中途失败或 Stop 时库里什么都没变。</p>
 *
 * <p>无状态、不碰 JPA：输入在请求线程上备好（{@link AgentOneClickDraft}），本类跑在提案作业线程里。</p>
 */
@Slf4j
final class AiStudioOneClickOrchestrator {

    /** 编排顺序：每一步要引用的对象都在它前面（表单要流程与表，动作要表单与决策）。 */
    static final List<AiStudioPhase> STEPS = List.of(AiStudioPhase.PROCESS_DESIGN, AiStudioPhase.TABLE_DESIGN,
            AiStudioPhase.FORM_DESIGN, AiStudioPhase.DECISION_DESIGN, AiStudioPhase.ACTION_DESIGN);

    static final String STEP_NO_DATA = "AI_STUDIO_ONE_CLICK_STEP_NO_DATA";

    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();

    /**
     * 向 CO 发一条请求并解析回答，解析结果交给 {@code accept}；解析或 {@code accept} 抛出可修违规时带着原话重问一次。
     * 返回 {@code accept} 的结果。
     */
    @FunctionalInterface
    interface Agent {
        Map<String, Object> ask(String message, UnaryOperator<Map<String, Object>> accept);
    }

    private final Agent agent;
    /** 跨切片校验（{@link AiResponseParser#checkCrossSlices}）：就地修改并返回修复记录，修不了的抛出 */
    private final Function<Map<String, Object>, List<String>> crossSliceCheck;
    private final Duration budget;
    private final Duration callTimeout;
    private final Clock clock;

    /**
     * @param budget      整次编排的时限（与提案作业的最长运行时间一致）
     * @param callTimeout 单次 CO 调用的上限：剩余时间不够一次调用就不再发下一步
     */
    AiStudioOneClickOrchestrator(Agent agent, Function<Map<String, Object>, List<String>> crossSliceCheck,
                                 Duration budget, Duration callTimeout, Clock clock) {
        this.agent = agent;
        this.crossSliceCheck = crossSliceCheck;
        this.budget = budget;
        this.callTimeout = callTimeout;
        this.clock = clock;
    }

    StudioChatResult run(AgentOneClickDraft draft, Consumer<Progress> progress) {
        Instant deadline = clock.instant().plus(budget);
        Map<String, Object> generated = new LinkedHashMap<>();
        StringBuilder reply = new StringBuilder();
        for (int i = 0; i < STEPS.size(); i++) {
            AiStudioPhase phase = STEPS.get(i);
            String label = "Step " + (i + 1) + "/" + STEPS.size() + " (" + phase.label() + ")";
            if (Thread.currentThread().isInterrupted()) {
                // 作业已被取消/替换：作业服务会丢掉这里的任何结果，只是别再多烧一次 CO
                throw new AiGenerationException("AI_STUDIO_PROPOSAL_CANCELLED", label + ": cancelled");
            }
            if (clock.instant().plus(callTimeout).isAfter(deadline)) {
                throw new AiGenerationException("AI_STUDIO_PROPOSAL_TIMED_OUT", label + ": not started, less than "
                        + callTimeout.toSeconds() + "s left of the " + budget.toMinutes() + "-minute limit");
            }
            progress.accept(new Progress(i + 1, STEPS.size(), phase.name()));
            Instant started = clock.instant();
            int step = i;
            Map<String, Object> upstream = generated;
            Map<String, Object> outcome;
            try {
                outcome = agent.ask(message(draft, upstream, step), parsed -> accept(step, parsed, upstream));
            } catch (AiGenerationException e) {
                throw new AiGenerationException(e.getErrorCode(), label + ": " + e.getMessage());
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> merged = (Map<String, Object>) outcome.get("design");
            generated = merged;
            String explanation = outcome.get("reply") instanceof String r ? r.strip() : "";
            if (!explanation.isEmpty()) {
                if (!reply.isEmpty()) reply.append("\n\n");
                reply.append("### ").append(i + 1).append('/').append(STEPS.size()).append(' ')
                        .append(phase.label()).append('\n').append(explanation);
            }
            log.info("AI Studio one-click step done: functionUnitId={}, step={}/{}, phase={}, tookSeconds={}",
                    draft.functionUnitId(), i + 1, STEPS.size(), phase, Duration.between(started, clock.instant())
                            .toSeconds());
        }
        Map<String, Object> proposal = new LinkedHashMap<>(generated);
        proposal.keySet().retainAll(AiStudioChatServiceImpl.ONE_CLICK_SLICES);
        return new StudioChatResult(reply.isEmpty() ? null : reply.toString(), proposal,
                AiStudioChatServiceImpl.SCOPE_ALL);
    }

    /**
     * 一步的回答 → 并入后的本轮设计。抛出的可修违规会让 {@link Agent} 带原话重问这一步。
     *
     * <p>表单步、动作步把累计结果交给跨切片校验（表单 / 动作的 stage 绑定要对得上本轮流程的 userTask），
     * 并采用它的自动修复——到动作步时与网关链路一次解析整套设计的效果一致。</p>
     */
    private Map<String, Object> accept(int step, Map<String, Object> parsed, Map<String, Object> generated) {
        AiStudioPhase phase = STEPS.get(step);
        Set<String> slices = slices(phase);
        Map<String, Object> data = parsed.get("generatedData") instanceof Map<?, ?> m ? copy(m) : new LinkedHashMap<>();
        data.keySet().retainAll(slices);
        if (data.isEmpty()) {
            if (phase != AiStudioPhase.DECISION_DESIGN) {
                Object text = parsed.get("reply");
                throw new AiGenerationException(STEP_NO_DATA, text instanceof String t && !t.isBlank()
                        ? "Content Organizer returned no data: " + t.strip()
                        : "Content Organizer returned neither data nor an explanation");
            }
            // 需求里没有决策表是常态：专家可以不出数据块
            data.put("decisionDefinitions", List.of());
        }
        Map<String, Object> merged = copy(generated);
        merged.putAll(data);
        return Map.of("design", checked(phase, merged), "reply", parsed.get("reply") instanceof String r ? r : "");
    }

    private Map<String, Object> checked(AiStudioPhase phase, Map<String, Object> merged) {
        if (phase == AiStudioPhase.FORM_DESIGN || phase == AiStudioPhase.ACTION_DESIGN) {
            for (String repair : crossSliceCheck.apply(merged)) {
                log.warn("AI Studio one-click: platform process rule repaired before saving: {}", repair);
            }
        }
        return merged;
    }

    /**
     * 第 {@code step} 步的请求。CURRENT DESIGN = 当前设计，其中核心切片按「上游用本轮产出、本步保留旧值
     * （重新生成时名字稳定）、下游清空」替换——下游旧值会被这次整体替换删掉，留着只会让专家去对齐马上消失的对象。
     */
    private String message(AgentOneClickDraft draft, Map<String, Object> generated, int step) {
        Map<String, Object> design = new LinkedHashMap<>(draft.design());
        for (int j = 0; j < STEPS.size(); j++) {
            for (String slice : slices(STEPS.get(j))) {
                if (j < step) {
                    design.put(slice, generated.get(slice));
                } else if (j > step) {
                    design.put(slice, "processDefinition".equals(slice) ? null : List.of());
                }
            }
        }
        AiStudioPhase phase = STEPS.get(step);
        String scope = phase.proposalScope().orElseThrow();
        return AiStudioAgentMessage.build("PROPOSE", phase.name(), scope, toJson(design), draft.schemaMetadata(),
                draft.formatSpec(), draft.documents(), draft.conversation(), userRequest(draft.userRequest(), step));
    }

    static String userRequest(String request, int step) {
        AiStudioPhase phase = STEPS.get(step);
        String order = String.join(", ", STEPS.stream().map(AiStudioPhase::label).toList());
        return (request == null ? "" : request.strip()) + "\n\n"
                + "========== One-click generation (system-provided) ==========\n"
                + "This is step " + (step + 1) + " of " + STEPS.size() + " of a one-click generation that builds the "
                + "whole function unit design one phase per request, in this order: " + order + ".\n"
                + "Produce the COMPLETE " + phase.proposalScope().orElseThrow() + " slice ("
                + String.join(", ", new java.util.TreeSet<>(slices(phase)))
                + ") for the requirements above; it replaces the existing one.\n"
                + "Slices of earlier steps in CURRENT DESIGN were generated in this run and are authoritative: "
                + "use their exact names and ids.\n"
                + "Slices of later steps are intentionally empty because the platform generates them next: do not "
                + "report them as missing dependencies and do not output them.\n"
                + (phase == AiStudioPhase.DECISION_DESIGN
                        ? "If the requirements call for no decision table, output an empty decisionDefinitions array.\n"
                        : "")
                + "========== End of one-click generation ==========";
    }

    private static Set<String> slices(AiStudioPhase phase) {
        return AiStudioChatServiceImpl.allowedSlices(phase.proposalScope().orElseThrow());
    }

    /** 深拷贝：校验器会就地改 BPMN 与动作，被拒的一次尝试不能改到上一步的结果。 */
    private static Map<String, Object> copy(Map<?, ?> value) {
        try {
            return JSON.readValue(JSON.writeValueAsBytes(value), new TypeReference<LinkedHashMap<String, Object>>() {
            });
        } catch (IOException e) {
            throw new AiGenerationException("AI_STUDIO_CONTEXT_SERIALIZATION_FAILED",
                    "Could not copy the generated design: " + e.getMessage());
        }
    }

    private static String toJson(Object value) {
        try {
            return JSON.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new AiGenerationException("AI_STUDIO_CONTEXT_SERIALIZATION_FAILED",
                    "Could not serialize the design for Content Organizer: " + e.getOriginalMessage());
        }
    }
}
