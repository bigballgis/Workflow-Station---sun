package com.developer.component.impl.compare;

import com.developer.dto.VersionCompareResponse;
import com.developer.entity.Version;
import com.developer.exception.DeveloperBusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/** Produces a bounded, typed diff from two already-authorized saved versions. */
@Component
public class VersionCompareEngine {

    private static final int MAX_CHANGES_PER_MODULE = 300;
    private static final int MAX_PREVIEW_LENGTH = 240;
    private static final List<String> MODULE_ORDER = List.of(
            VersionSnapshotNormalizer.BASIC, VersionSnapshotNormalizer.PROCESS,
            VersionSnapshotNormalizer.TABLES, VersionSnapshotNormalizer.FORMS,
            VersionSnapshotNormalizer.VIEWS, VersionSnapshotNormalizer.ACTIONS,
            VersionSnapshotNormalizer.AUTOMATION, VersionSnapshotNormalizer.CONNECTIONS,
            VersionSnapshotNormalizer.EMAIL_TEMPLATES, VersionSnapshotNormalizer.EMAIL_MONITORS,
            VersionSnapshotNormalizer.DECISIONS, VersionSnapshotNormalizer.DOCUMENTS);

    private final VersionSnapshotNormalizer normalizer;
    private final VersionSemanticCompareEngine semanticEngine;

    public VersionCompareEngine(VersionSnapshotNormalizer normalizer) {
        this.normalizer = normalizer;
        this.semanticEngine = new VersionSemanticCompareEngine();
    }

    public VersionCompareResponse compare(Version base, Version target) {
        if (base.getId().equals(target.getId())) {
            throw new DeveloperBusinessException("BIZ_VERSION_COMPARE_SAME_VERSION",
                    "Choose two different versions to compare");
        }
        boolean sameFu = VersionViewMatcher.sameFunctionUnit(base, target);
        var pair = VersionActionMatcher.align(base, target,
                normalizer.normalize(base.getSnapshotData(), sameFu ? target.getSnapshotData() : null),
                normalizer.normalize(target.getSnapshotData(), sameFu ? base.getSnapshotData() : null));
        var decisions = VersionDecisionMatcher.align(base, target, pair.before(), pair.after());
        var views = VersionViewMatcher.align(base, target, decisions.before(), decisions.after());
        var forms = VersionFormMatcher.align(views.before(), views.after(), sameFu);
        var emails = VersionEmailMatcher.align(forms.before(), forms.after(), sameFu);
        var basic = VersionBasicMatcher.align(emails.before(), emails.after());
        var before = basic.before();
        var after = basic.after();
        List<VersionCompareResponse.ModuleDiff> modules = new ArrayList<>();
        VersionCompareResponse.Counts totals = new VersionCompareResponse.Counts(0, 0, 0);
        VersionCompareResponse.Counts displayTotals = new VersionCompareResponse.Counts(0, 0, 0);
        for (String key : MODULE_ORDER) {
            VersionCompareResponse.ModuleDiff module = compareModule(key, before, after);
            modules.add(module);
            totals = totals.plus(module.counts());
            displayTotals = displayTotals.plus("COMPARED".equals(module.semantic().status())
                    ? module.semantic().counts() : module.counts());
        }
        return new VersionCompareResponse(
                new VersionCompareResponse.VersionRef(base.getId(), base.getVersionNumber(), base.getPublishedAt()),
                new VersionCompareResponse.VersionRef(target.getId(), target.getVersionNumber(), target.getPublishedAt()),
                totals, modules, displayTotals);
    }

    private VersionCompareResponse.ModuleDiff compareModule(String key,
            VersionSnapshotNormalizer.Normalized before,
            VersionSnapshotNormalizer.Normalized after) {
        if (VersionSnapshotNormalizer.AUTOMATION.equals(key)) {
            VersionCompareResponse.SemanticDiff semantic = semanticEngine.compare(key, before, after);
            return new VersionCompareResponse.ModuleDiff(key, "NOT_SNAPSHOTTED",
                    new VersionCompareResponse.Counts(0, 0, 0), false, List.of(), semantic);
        }
        if (before.notCaptured().contains(key) || after.notCaptured().contains(key)) {
            return unavailable(key, "NOT_CAPTURED");
        }
        Counter counter = new Counter();
        diff(key, before.modules().get(key), after.modules().get(key), counter);
        VersionCompareResponse.SemanticDiff semantic = semanticEngine.compare(key, before, after);
        if (VersionSnapshotNormalizer.DECISIONS.equals(key) && "COMPARED".equals(semantic.status())
                && semantic.items().isEmpty() && !counter.changes.isEmpty()) {
            // #1684: an unsupported structural XML change is not "No changes".
            semantic = new VersionCompareResponse.SemanticDiff("UNPARSEABLE", semantic.scope(),
                    new VersionCompareResponse.Counts(0, 0, 0), false, List.of());
        }
        return new VersionCompareResponse.ModuleDiff(key, "COMPARED", counter.counts(),
                counter.truncated, List.copyOf(counter.changes), semantic);
    }

    private VersionCompareResponse.ModuleDiff unavailable(String key, String status) {
        return new VersionCompareResponse.ModuleDiff(key, status,
                new VersionCompareResponse.Counts(0, 0, 0), false, List.of(),
                new VersionCompareResponse.SemanticDiff(status, "NONE",
                        new VersionCompareResponse.Counts(0, 0, 0), false, List.of()));
    }

    private void diff(String path, JsonNode before, JsonNode after, Counter counter) {
        if (before == null && after == null) return;
        if (path.startsWith("DOCUMENTS/") && (before == null || before.isTextual())
                && (after == null || after.isTextual())) {
            if (before != null && before.equals(after)) return;
            var field = VersionDocumentPreview.field(before, after);
            counter.add(new VersionCompareResponse.Change(path, before == null ? "ADDED" : after == null ? "REMOVED" : "MODIFIED",
                    field.oldValue(), field.newValue()));
            counter.truncated |= field.textContext() != null && field.textContext().omittedChanges();
            return;
        }
        if (before == null || before.isMissingNode()) {
            counter.add(new VersionCompareResponse.Change(path, "ADDED", null, preview(after)));
            return;
        }
        if (after == null || after.isMissingNode()) {
            counter.add(new VersionCompareResponse.Change(path, "REMOVED", preview(before), null));
            return;
        }
        if (before.equals(after)) return;
        if (before.isObject() && after.isObject()) {
            Set<String> keys = new TreeSet<>();
            before.fieldNames().forEachRemaining(keys::add);
            after.fieldNames().forEachRemaining(keys::add);
            for (String key : keys) {
                diff(path + "/" + key, before.get(key), after.get(key), counter);
            }
            return;
        }
        if (before.isArray() && after.isArray()) {
            int length = Math.max(before.size(), after.size());
            for (int index = 0; index < length; index++) {
                diff(path + "/" + index,
                        index < before.size() ? before.get(index) : null,
                        index < after.size() ? after.get(index) : null, counter);
            }
            return;
        }
        counter.add(new VersionCompareResponse.Change(path, "MODIFIED", preview(before), preview(after)));
    }

    private String preview(JsonNode value) {
        if (value == null || value.isNull()) return null;
        if (value.isObject()) return "{…}";
        if (value.isArray()) return "[" + value.size() + " items]";
        String rendered = value.isTextual() ? value.asText() : value.toString();
        if (rendered.startsWith(VersionActionSnapshotProjector.OPAQUE_PREFIX)) return "[hidden configuration]";
        if (rendered.startsWith("<{")) return "XML (" + rendered.length() + " characters)";
        if (rendered.startsWith("!UNPARSEABLE_XML:")) {
            return "Unparseable XML (" + (rendered.length() - "!UNPARSEABLE_XML:".length()) + " characters)";
        }
        return rendered.length() > MAX_PREVIEW_LENGTH
                ? rendered.substring(0, MAX_PREVIEW_LENGTH) + "…" : rendered;
    }

    private static final class Counter {
        private int added;
        private int modified;
        private int removed;
        private boolean truncated;
        private final List<VersionCompareResponse.Change> changes = new ArrayList<>();

        private void add(VersionCompareResponse.Change change) {
            switch (change.type()) {
                case "ADDED" -> added++;
                case "REMOVED" -> removed++;
                default -> modified++;
            }
            if (changes.size() < MAX_CHANGES_PER_MODULE) changes.add(change);
            else truncated = true;
        }

        private VersionCompareResponse.Counts counts() {
            return new VersionCompareResponse.Counts(added, modified, removed);
        }
    }
}
