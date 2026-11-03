package com.developer.service.impl;

import java.util.List;
import java.util.Map;

/**
 * AI Studio 发给 Content Organizer agent setting 的整段请求。分节与顺序是 CO 上各 Agent 的 Instruction 读取的契约
 * （docs/design/developer-workstation-ai-studio-co-agent-instructions.md「调用约定」），改分节名要两边一起改。
 *
 * <p>CO 的主管按 PHASE 把整条消息转交给一个专家，专家读的也是这条消息，所以这里不区分"给谁看"。</p>
 */
final class AiStudioAgentMessage {

    /** 与网关链路的文档上限一致（AiGenerationServiceImpl#MAX_DOCUMENT_CONTENT_LENGTH）。 */
    static final int MAX_DOCUMENT_CHARS = 50_000;

    /**
     * 格式说明是 AI Generate 的 GENERATION 提示词全文：字段规范以它为准，但它自带的输出顺序与语言要求是网关链路的，
     * 与 CO 专家的 OUTPUT 规则（说明在前、标记块在后、说明跟随用户语言）不同——在分节开头说清楚谁说了算。
     */
    private static final String FORMAT_NOTE = "(Field names, enum values and constraints of the generatedData JSON. "
            + "Ignore any output-order, marker-placement or output-language instructions inside it: "
            + "your own OUTPUT rules decide those.)";

    private AiStudioAgentMessage() {
    }

    /**
     * @param mode             {@code PROPOSE} 或 {@code ADVISORY}
     * @param scope            写入范围；没有的阶段（VALIDATION）传 {@code NONE}
     * @param currentDesign    FunctionUnitContextDTO 的 JSON
     * @param schemaMetadata   枚举与结构元数据的 JSON
     * @param formatSpec       GENERATION 提示词全文；顾问轮不需要，传 null 省掉约 20KB
     * @param documents        {@code [{documentType, content}]}，可空
     * @param conversation     历史转写，可空
     * @param userRequest      本轮用户消息
     */
    static String build(String mode, String phase, String scope, String currentDesign, String schemaMetadata,
                        String formatSpec, List<Map<String, String>> documents, String conversation,
                        String userRequest) {
        StringBuilder sb = new StringBuilder();
        sb.append("## MODE: ").append(mode).append('\n');
        sb.append("## PHASE: ").append(phase).append('\n');
        sb.append("## SCOPE: ").append(scope).append('\n');
        sb.append("## CURRENT DESIGN\n").append(currentDesign).append('\n');
        sb.append("## SCHEMA METADATA\n").append(schemaMetadata).append('\n');
        if (formatSpec != null && !formatSpec.isBlank()) {
            sb.append("## FORMAT SPECIFICATION\n").append(FORMAT_NOTE).append('\n').append(formatSpec.strip()).append('\n');
        }
        if (documents != null && !documents.isEmpty()) {
            sb.append("## DOCUMENTS\n");
            for (Map<String, String> doc : documents) {
                String content = doc.getOrDefault("content", "");
                if (content.length() > MAX_DOCUMENT_CHARS) {
                    content = content.substring(0, MAX_DOCUMENT_CHARS) + "\n…(document truncated)";
                }
                sb.append("### ").append(doc.getOrDefault("documentType", "DOCUMENT")).append('\n')
                        .append(content.strip()).append('\n');
            }
        }
        if (conversation != null && !conversation.isBlank()) {
            sb.append("## CONVERSATION\n").append(conversation.strip()).append('\n');
        }
        sb.append("## USER REQUEST\n").append(userRequest == null ? "" : userRequest.strip());
        return sb.toString();
    }
}
