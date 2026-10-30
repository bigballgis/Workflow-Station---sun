package com.workflow.assignment;

import com.platform.common.assignment.AssignmentExclusionPolicy;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Pure responsibility veto. Inputs are effective records for one case, including all retained rounds. */
public final class AssignmentExclusionEvaluator {
    private AssignmentExclusionEvaluator() { }

    public static Set<String> excluded(AssignmentExclusionPolicy policy, String originalInitiator,
            String functionUnitCode, Collection<Responsibility> history) {
        var excluded = new HashSet<String>();
        if (policy.excludeInitiator()) excluded.add(requireIdentity(originalInitiator));
        for (Responsibility record : history) {
            boolean selected = policy.excludeSelectedNodeHandlers() && functionUnitCode.equals(record.functionUnitCode())
                    && policy.selectedNodeKeys().contains(record.nodeKey());
            if (policy.excludePreviousHandlers() || selected) excluded.addAll(record.responsibleUserIds());
        }
        return Set.copyOf(excluded);
    }

    /** Backup is eligible only when a successful, nonempty primary resolution was entirely vetoed. */
    public static Pool filter(AssignmentExclusionPolicy policy, List<String> primary,
            List<String> backup, Set<String> excluded) {
        List<String> allowed = without(primary, excluded);
        if (!allowed.isEmpty()) return new Pool(Source.PRIMARY, allowed);
        if (!primary.isEmpty() && policy.backup().enabled()) {
            return new Pool(Source.BACKUP, without(backup, excluded));
        }
        return new Pool(Source.PRIMARY, allowed);
    }

    private static List<String> without(List<String> users, Set<String> excluded) {
        var allowed = new LinkedHashSet<String>();
        for (String user : users) if (!excluded.contains(requireIdentity(user))) allowed.add(user);
        return List.copyOf(allowed);
    }

    private static String requireIdentity(String id) {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("Missing canonical responsibility identity");
        return id;
    }

    public record Responsibility(String functionUnitCode, String nodeKey, Set<String> responsibleUserIds) {
        public Responsibility {
            if (functionUnitCode == null || nodeKey == null || responsibleUserIds == null || responsibleUserIds.isEmpty()) {
                throw new IllegalArgumentException("Incomplete responsibility record");
            }
            responsibleUserIds.forEach(AssignmentExclusionEvaluator::requireIdentity);
            responsibleUserIds = Set.copyOf(responsibleUserIds);
        }
    }

    public enum Source { PRIMARY, BACKUP }
    public record Pool(Source source, List<String> users) { }
}
