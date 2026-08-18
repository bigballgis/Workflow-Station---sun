package com.admin.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 契约的另一半。
 *
 * <p>{@code deploy/scripts/check-ap-schema-contract.ts} 保证「契约 ⊆ AP 实际 schema」，
 * 但它管不了「admin-center 的 SQL ⊆ 契约」——有人新写一条查 AP 表的 SQL 却忘了登记契约，
 * 那条列就完全不受门禁保护，下次 AP 删它照样线上崩。本测试补的就是这一半。
 *
 * <p>2026-08 UAT 事故（AP 0.88 删除 {@code platform.filteredPieceNames}，Automation Pieces
 * 整页 500）暴露的根因是 admin-center 用裸 SQL 直查 AP 私有表（AP 与平台共库）。在把这些查询
 * 改成走 AP REST 之前，这两个方向的校验是唯一的防线。
 */
class ApSchemaContractTest {

    /** AP 拥有的表。平台自有表（sys_/up_/dw_/ac_/we_ 前缀）不属于本契约。 */
    private static final Set<String> AP_TABLES = Set.of(
            "piece_metadata", "file", "flow", "flow_version", "project", "user_identity", "platform");

    private static final List<Path> SOURCES = List.of(
            Path.of("src/main/java/com/admin/service/impl/AutomationPieceServiceImpl.java"),
            Path.of("src/main/java/com/admin/service/impl/AutomationFlowServiceImpl.java"));

    private static final Path CONTRACT = Path.of("../../deploy/contracts/ap-schema-contract.json");

    /** 双引号包裹的标识符：AP 的列名是 camelCase，必须加引号，故这是最可靠的抓取点。 */
    private static final Pattern QUOTED_IDENT = Pattern.compile("\\\\\"([A-Za-z][A-Za-z0-9_]*)\\\\\"");

    @Test
    @DisplayName("admin-center SQL 里引用的 AP 列，必须都已登记进 schema 契约")
    void everyQuotedApColumnIsDeclaredInContract() throws IOException {
        Set<String> declared = declaredColumns();
        assertThat(declared).as("契约文件应能读到列").isNotEmpty();

        List<String> undeclared = new ArrayList<>();
        for (Path src : SOURCES) {
            if (!Files.exists(src)) {
                continue;
            }
            String body = Files.readString(src);
            if (!mentionsApTable(body)) {
                continue;
            }
            Matcher m = QUOTED_IDENT.matcher(body);
            while (m.find()) {
                String ident = m.group(1);
                if (isLikelyApColumn(ident) && !declared.contains(ident)) {
                    undeclared.add(src.getFileName() + " -> \"" + ident + "\"");
                }
            }
        }

        assertThat(new LinkedHashSet<>(undeclared))
                .as("""
                        这些列出现在直查 AP 表的 SQL 里，但没有登记进 deploy/contracts/ap-schema-contract.json。
                        未登记 = 不受发布门禁保护 = AP 下次删掉它时线上才发现。
                        请把它们加进契约（连同所属表与使用它的 SQL 常量名）。""")
                .isEmpty();
    }

    private static boolean mentionsApTable(String body) {
        String lower = body.toLowerCase(Locale.ROOT);
        return AP_TABLES.stream().anyMatch(t -> lower.contains(" " + t) || lower.contains("\n" + t));
    }

    /**
     * 只挑「像 AP 列」的标识符：camelCase 且首字母小写。
     * 排除 SQL 别名与平台自有表的 snake_case 列，避免把噪声算成漏登记。
     */
    private static boolean isLikelyApColumn(String ident) {
        return !ident.contains("_")
                && Character.isLowerCase(ident.charAt(0))
                && !ident.equals("flowKey")        // 由 metadata->>'hermesFlowKey' 产生的别名
                && !ident.equals("published")      // 表达式别名
                && !ident.equals("fromPublished")  // 表达式别名
                && !ident.equals("projectName")    // p."displayName" 的别名
                && !ident.equals("ownerFirstName") // ui."firstName" 的别名
                && !ident.equals("ownerLastName")  // ui."lastName" 的别名
                && !ident.equals("unitName")       // 平台表别名
                && !ident.equals("bpmn");          // 平台表别名
    }

    private static Set<String> declaredColumns() throws IOException {
        JsonNode root = new ObjectMapper().readTree(Files.readString(CONTRACT));
        Set<String> cols = new HashSet<>();
        root.path("tables").fields().forEachRemaining(e ->
                e.getValue().path("columns").forEach(c -> cols.add(c.asText())));
        return cols;
    }
}
