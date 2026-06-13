package com.portal.component;

import com.platform.common.jdbc.SubTableRowKeySupport;
import com.portal.dto.ProcessInstanceInfo;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Static helpers for the multi-instance (MI) sub-table overlay machinery.
 * Extracted from {@link ProcessComponent}; pure functions over MI task payloads and
 * {@code __subTables__} variable rows (no Spring/IO dependencies).
 */
final class MiOverlaySupport {

    private MiOverlaySupport() {
    }

    record MiRowProgress(String statusColumn, String nodeColumn, String status, String currentNode) {}

    static Long extractNumericSubTableRowId(Map<String, Object> row) {
        if (row == null || row.isEmpty()) {
            return null;
        }
        for (String key : List.of("id", "id_idw", "rowId")) {
            Long n = coerceWholeNumber(SubTableRowKeySupport.getRowValueIgnoreCase(row, key));
            if (n != null) {
                return n;
            }
        }
        Object rawRk = row.get("rowKey");
        if (rawRk instanceof Map<?, ?> m) {
            Map<String, Object> rk = SubTableRowKeySupport.normalizeStringKeyMap(m);
            for (String key : List.of("id", "id_idw", "rowId")) {
                Long n = coerceWholeNumber(SubTableRowKeySupport.getRowValueIgnoreCase(rk, key));
                if (n != null) {
                    return n;
                }
            }
        }
        return null;
    }

    static void normalizeVariableRowPkEnvelope(Map<String, Object> row) {
        if (row == null || row.isEmpty()) {
            return;
        }
        Long idNum = coerceWholeNumber(SubTableRowKeySupport.getRowValueIgnoreCase(row, "id"));
        Long idIdwNum = coerceWholeNumber(SubTableRowKeySupport.getRowValueIgnoreCase(row, "id_idw"));
        if (idNum == null && idIdwNum != null) {
            row.put("id", idIdwNum);
        } else if (idNum != null && idIdwNum == null) {
            row.put("id_idw", idNum);
        }
    }

    static Set<String> miDashboardColumnsToProtect(Map<String, MiRowProgress> miProgress) {
        Set<String> cols = new LinkedHashSet<>();
        cols.add("task_status");
        cols.add("task_current_node");
        if (miProgress == null) {
            return cols;
        }
        for (MiRowProgress p : miProgress.values()) {
            if (p == null) {
                continue;
            }
            if (p.statusColumn != null && !p.statusColumn.isBlank()) {
                cols.add(p.statusColumn.trim());
            }
            if (p.nodeColumn != null && !p.nodeColumn.isBlank()) {
                cols.add(p.nodeColumn.trim());
            }
        }
        return cols;
    }

    static boolean isProtectedMiDashboardColumn(Set<String> protectedCols, String columnName) {
        if (columnName == null || protectedCols == null || protectedCols.isEmpty()) {
            return false;
        }
        for (String p : protectedCols) {
            if (p != null && p.equalsIgnoreCase(columnName)) {
                return true;
            }
        }
        return false;
    }

    static MiRowProgress preferMiRowProgressOverlay(MiRowProgress a, MiRowProgress b) {
        if (a == null) {
            return b;
        }
        if (b == null) {
            return a;
        }
        boolean aTerminal = isTerminalMiOverlayProgress(a);
        boolean bTerminal = isTerminalMiOverlayProgress(b);
        if (aTerminal && !bTerminal) {
            return a;
        }
        if (bTerminal && !aTerminal) {
            return b;
        }
        int oa = miSubFormOrdinalHint(a.currentNode());
        int ob = miSubFormOrdinalHint(b.currentNode());
        if (oa != Integer.MIN_VALUE && ob != Integer.MIN_VALUE && oa != ob) {
            return ob > oa ? b : a;
        }
        return b;
    }

    static boolean isTerminalMiOverlayProgress(MiRowProgress p) {
        if (p == null) {
            return false;
        }
        if (p.status() != null && "COMPLETED".equalsIgnoreCase(p.status().trim())) {
            return true;
        }
        String node = p.currentNode();
        return node != null && "end".equalsIgnoreCase(node.trim());
    }

    /** Largest N from {@code sub form N} tokens; {@link Integer#MIN_VALUE} if none. */
    static int miSubFormOrdinalHint(String name) {
        if (name == null || name.isBlank()) {
            return Integer.MIN_VALUE;
        }
        Matcher m = Pattern.compile("(?i)\\bsub\\s*form\\s*(\\d+)\\b").matcher(name.trim());
        int max = Integer.MIN_VALUE;
        while (m.find()) {
            try {
                max = Math.max(max, Integer.parseInt(m.group(1)));
            } catch (NumberFormatException ignored) {
                /* ignore */
            }
        }
        return max;
    }

    /**
     * When canonical PK strings differ between variables and wf_extended_task_info (e.g. copied forms /
     * id vs id_idw), still resolve MI overlay if there is exactly one logical PK value match.
     */
    static MiRowProgress lookupMiRowProgressForVariableRow(
            Map<String, MiRowProgress> miProgress,
            List<String> pkCols,
            Map<String, Object> rowKey) {
        if (miProgress == null || miProgress.isEmpty() || rowKey == null) {
            return null;
        }
        String canon = SubTableRowKeySupport.canonicalRowKeyString(pkCols, rowKey);
        MiRowProgress hit = miProgress.get(canon);
        if (hit != null) {
            return hit;
        }
        if (pkCols.size() != 1) {
            return null;
        }
        Long want = normalizeRowKeyLong(pkCols.get(0), rowKey);
        if (want == null) {
            return null;
        }
        MiRowProgress onlyMatch = null;
        int matches = 0;
        for (Map.Entry<String, MiRowProgress> e : miProgress.entrySet()) {
            Long parsed = parseCanonicalSinglePkSuffixLong(e.getKey());
            if (parsed != null && parsed.equals(want)) {
                matches++;
                onlyMatch = e.getValue();
            }
        }
        if (matches == 1 && onlyMatch != null) {
            return onlyMatch;
        }
        return null;
    }

    private static Long normalizeRowKeyLong(String pkCol, Map<String, Object> rowKey) {
        Object v = SubTableRowKeySupport.getRowValueIgnoreCase(rowKey, pkCol);
        return coerceWholeNumber(v);
    }

    static Long parseCanonicalSinglePkSuffixLong(String canonKey) {
        if (canonKey == null || canonKey.isEmpty()) {
            return null;
        }
        int eq = canonKey.lastIndexOf('=');
        if (eq < 0 || eq >= canonKey.length() - 1) {
            return null;
        }
        return coerceWholeNumber(canonKey.substring(eq + 1));
    }

    static Long coerceWholeNumber(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof Number n) {
            return n.longValue();
        }
        try {
            String s = String.valueOf(raw).trim();
            if (s.isEmpty()) {
                return null;
            }
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static boolean isPortalProcessCompleted(ProcessInstanceInfo info) {
        return info != null && info.getStatus() != null
                && "COMPLETED".equalsIgnoreCase(info.getStatus().trim());
    }

    /**
     * Mirrors portal {@code task_status}/{@code task_current_node} when writing MI extension columns,
     * SubTableField / My Request flow chart depends on these columns.
     */
    static void applyMiOverlayToVariableRow(Map<String, Object> row, MiRowProgress p) {
        if (row == null || p == null) {
            return;
        }
        if (p.statusColumn != null && !p.statusColumn.isBlank()) {
            row.put(p.statusColumn, p.status);
        }
        if (p.nodeColumn != null && !p.nodeColumn.isBlank()) {
            row.put(p.nodeColumn, p.currentNode);
        }
        row.put("task_status", mapWorkflowMiStatusToPortalTaskStatus(p.status));
        row.put("task_current_node",
                p.currentNode != null && !p.currentNode.isBlank() ? p.currentNode : "-");
    }

    private static String mapWorkflowMiStatusToPortalTaskStatus(String workflowStatus) {
        if (workflowStatus == null || workflowStatus.isBlank()) {
            return "PENDING";
        }
        String u = workflowStatus.trim().toUpperCase(Locale.ROOT);
        if ("COMPLETED".equals(u)) {
            return "COMPLETED";
        }
        if ("CANCELLED".equals(u)) {
            return "CANCELLED";
        }
        if ("IN_PROGRESS".equals(u) || "ASSIGNED".equals(u) || "CREATED".equals(u)) {
            return "IN_PROGRESS";
        }
        return workflowStatus;
    }

    /**
     * Fallback when process is archived but variable snapshot/MI API still shows in-progress placeholders (often after soft-deleted extended tasks).
     */
    static void normalizeStuckMiParticipantRowForCompletedProcess(Map<String, Object> row) {
        if (row == null || row.isEmpty()) {
            return;
        }
        Object ts = row.get("task_status");
        String s = ts != null ? String.valueOf(ts).trim() : "";
        if ("COMPLETED".equalsIgnoreCase(s) || "CANCELLED".equalsIgnoreCase(s)) {
            return;
        }
        boolean miLike = row.containsKey("assignee_user_id")
                || row.containsKey("assignee_display_name")
                || row.containsKey("task_current_node");
        if (!miLike) {
            return;
        }
        row.put("task_status", "COMPLETED");
        row.put("task_current_node", "end");
    }

    /**
     * Same sub-table row may retain multiple extended tasks per BPMN step (completed + orphan CREATED/ASSIGNED).
     * Without dedupe, portal may show an earlier step as current (e.g. still sub form1).
     */
    static List<Map<String, Object>> dedupeMiTasksPreferCompletedPerStepKey(List<Map<String, Object>> tasks) {
        if (tasks == null || tasks.size() <= 1) {
            return tasks;
        }
        Map<String, List<Map<String, Object>>> byStep = new LinkedHashMap<>();
        for (Map<String, Object> t : tasks) {
            String key = miAggregateStepKey(t);
            byStep.computeIfAbsent(key, k -> new ArrayList<>()).add(t);
        }
        List<Map<String, Object>> out = new ArrayList<>(tasks.size());
        for (List<Map<String, Object>> group : byStep.values()) {
            boolean hasCompleted = group.stream()
                    .anyMatch(x -> "COMPLETED".equalsIgnoreCase(stringVal(x.get("status"))));
            if (hasCompleted) {
                group.stream()
                        .filter(x -> "COMPLETED".equalsIgnoreCase(stringVal(x.get("status"))))
                        .max(Comparator.comparing(x -> {
                            LocalDateTime done = parseMiStatusLocalDateTime(x.get("completedTime"));
                            if (done != null) {
                                return done;
                            }
                            LocalDateTime created = parseMiStatusLocalDateTime(x.get("createdTime"));
                            return created != null ? created : LocalDateTime.MIN;
                        }))
                        .ifPresent(out::add);
            } else {
                // Same BPMN step, no terminal COMPLETED snapshot — overlapping extended rows explode candidate count;
                // a late orphaned CREATED must not outweigh a real ASSIGNED row on a later BPMN step in pickLatestActiveTask.
                pickRepresentativeOverlappingMiExtendedRow(group).ifPresent(out::add);
            }
        }
        return out.isEmpty() ? tasks : out;
    }

    private static String miAggregateStepKey(Map<String, Object> t) {
        String defKey = stringVal(t.get("taskDefinitionKey"));
        if (defKey != null && !defKey.isBlank()) {
            return defKey.trim();
        }
        String name = stringVal(t.get("taskName"));
        if (name != null && !name.isBlank()) {
            return name.trim().replaceAll("\\s+", " ");
        }
        String taskId = stringVal(t.get("taskId"));
        return taskId != null && !taskId.isBlank() ? taskId.trim() : ("anon:" + System.identityHashCode(t));
    }

    /**
     * When several {@code wf_extended_task_info} rows collide on the same step key and none are completed,
     * prefer the row that mirrors what Flowable still keeps as a real workload (assignee/status), not stray CREATED.
     */
    private static Optional<Map<String, Object>> pickRepresentativeOverlappingMiExtendedRow(List<Map<String, Object>> group) {
        if (group == null || group.isEmpty()) {
            return Optional.empty();
        }
        if (group.size() == 1) {
            return Optional.of(group.get(0));
        }
        Map<String, Object> best = group.get(0);
        for (int i = 1; i < group.size(); i++) {
            Map<String, Object> cand = group.get(i);
            if (compareOverlappingMiExtendedRows(cand, best) > 0) {
                best = cand;
            }
        }
        return Optional.of(best);
    }

    /**
     * Higher score ⇒ more authoritative for overlapping extended MI rows sharing a step key or across sequential steps.
     */
    private static int miOverlappingExtendedTaskAuthority(Map<String, Object> t) {
        if (t == null) {
            return -10_000;
        }
        String st = stringVal(t.get("status"));
        if ("COMPLETED".equalsIgnoreCase(st) || "CANCELLED".equalsIgnoreCase(st)) {
            return -10_000;
        }
        String assignee = stringVal(t.get("assignee"));
        boolean hasAssignee = assignee != null && !assignee.isBlank();
        if ("ASSIGNED".equalsIgnoreCase(st) && hasAssignee) {
            return 500;
        }
        if ("IN_PROGRESS".equalsIgnoreCase(st)) {
            return 450;
        }
        if ("CREATED".equalsIgnoreCase(st) && hasAssignee) {
            return 300;
        }
        if ("CREATED".equalsIgnoreCase(st)) {
            return 100;
        }
        return 200;
    }

    /**
     * &gt;0 if {@code a} should win over {@code b} when both denote overlapping MI extension noise.
     */
    private static int compareOverlappingMiExtendedRows(Map<String, Object> a, Map<String, Object> b) {
        int ca = miOverlappingExtendedTaskAuthority(a);
        int cb = miOverlappingExtendedTaskAuthority(b);
        if (ca != cb) {
            return Integer.compare(ca, cb);
        }
        LocalDateTime ta = parseMiStatusLocalDateTime(a.get("createdTime"));
        LocalDateTime tb = parseMiStatusLocalDateTime(b.get("createdTime"));
        if (ta != null && tb != null && !ta.equals(tb)) {
            return ta.compareTo(tb);
        }
        if (ta != null && tb == null) {
            return 1;
        }
        if (ta == null && tb != null) {
            return -1;
        }
        String ida = Objects.toString(stringVal(a.get("taskId")), "");
        String idb = Objects.toString(stringVal(b.get("taskId")), "");
        return ida.compareTo(idb);
    }

    /** LocalDateTime in API Map may be an ISO string */
    private static LocalDateTime parseMiStatusLocalDateTime(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof LocalDateTime ldt) {
            return ldt;
        }
        if (raw instanceof String s && !s.isBlank()) {
            try {
                return LocalDateTime.parse(s.trim());
            } catch (Exception ignored) {
                return null;
            }
        }
        return null;
    }

    static Map<String, Object> pickLatestActiveTask(List<Map<String, Object>> tasks) {
        Map<String, Object> best = null;
        for (Map<String, Object> t : tasks) {
            String st = stringVal(t.get("status"));
            if ("COMPLETED".equalsIgnoreCase(st) || "CANCELLED".equalsIgnoreCase(st)) {
                continue;
            }
            if (best == null) {
                best = t;
                continue;
            }
            int cmp = compareOverlappingMiExtendedRows(t, best);
            if (cmp > 0) {
                best = t;
                continue;
            }
            if (cmp < 0) {
                continue;
            }
            int ordT = miSubFormOrdinalHint(stringVal(t.get("taskName")));
            int ordB = miSubFormOrdinalHint(stringVal(best.get("taskName")));
            if (ordT != Integer.MIN_VALUE && ordB != Integer.MIN_VALUE && ordT > ordB) {
                best = t;
            }
        }
        return best;
    }

    static String stringVal(Object o) {
        return o != null ? String.valueOf(o) : null;
    }

    static String normalizeMiTableKey(String s) {
        return s == null ? "" : s.trim().toLowerCase(Locale.ROOT);
    }

    static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
