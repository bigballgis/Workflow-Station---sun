package com.developer.component.impl.compare;

import com.developer.dto.VersionCompareResponse;

import java.util.Set;

/** Module-aware, read-only presentation layer over the Phase 1 compare projection. */
final class VersionSemanticCompareEngine {

    private static final Set<String> XML_MODULES = Set.of(
            VersionSnapshotNormalizer.PROCESS,
            VersionSnapshotNormalizer.AUTOMATION,
            VersionSnapshotNormalizer.DECISIONS);

    private final VersionSemanticJsonProjector jsonProjector = new VersionSemanticJsonProjector();
    private final VersionSemanticXmlProjector xmlProjector = new VersionSemanticXmlProjector();
    private final VersionSemanticConnectionProjector connectionProjector =
            new VersionSemanticConnectionProjector();

    VersionCompareResponse.SemanticDiff compare(String module,
            VersionSnapshotNormalizer.Normalized before,
            VersionSnapshotNormalizer.Normalized after) {
        if (before.notCaptured().contains(module) || after.notCaptured().contains(module)) {
            return VersionSemanticDiffSupport.unavailable("NOT_CAPTURED", "NONE");
        }
        if (VersionSnapshotNormalizer.CONNECTIONS.equals(module)) {
            var pair = connectionProjector.project(before, after);
            return VersionSemanticDiffSupport.compare("COMPARED", "FULL", pair.before(), pair.after());
        }
        if (XML_MODULES.contains(module)) {
            var old = xmlProjector.project(module, before);
            var next = xmlProjector.project(module, after);
            if (!"COMPARED".equals(old.status()) || !"COMPARED".equals(next.status())) {
                return VersionSemanticDiffSupport.unavailable("UNPARSEABLE", old.scope());
            }
            return VersionSemanticDiffSupport.compare("COMPARED", old.scope(),
                    old.objects(), next.objects());
        }
        boolean partialViews = VersionSnapshotNormalizer.VIEWS.equals(module)
                && (before.sanitizedSource().path("_viewComparePartial").asBoolean()
                || after.sanitizedSource().path("_viewComparePartial").asBoolean());
        boolean partialForms = VersionSnapshotNormalizer.FORMS.equals(module)
                && (before.sanitizedSource().path("_formComparePartial").asBoolean()
                || after.sanitizedSource().path("_formComparePartial").asBoolean());
        String emailFlag = switch (module) {
            case VersionSnapshotNormalizer.EMAIL_TEMPLATES -> "_emailTemplatesComparePartial";
            case VersionSnapshotNormalizer.EMAIL_MONITORS -> "_emailMonitorsComparePartial";
            default -> "";
        };
        boolean partialEmails = !emailFlag.isEmpty() && (before.sanitizedSource().path(emailFlag).asBoolean()
                || after.sanitizedSource().path(emailFlag).asBoolean());
        boolean partialBasic = VersionSnapshotNormalizer.BASIC.equals(module)
                && (VersionBasicMatcher.partial(before) || VersionBasicMatcher.partial(after));
        return VersionSemanticDiffSupport.compare("COMPARED", partialViews || partialForms || partialEmails || partialBasic ? "PARTIAL" : "FULL",
                jsonProjector.project(module, before), jsonProjector.project(module, after));
    }
}
