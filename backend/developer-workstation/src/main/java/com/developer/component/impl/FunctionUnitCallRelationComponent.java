package com.developer.component.impl;

import com.developer.dto.FunctionUnitCallRelations;
import com.developer.entity.FunctionUnit;
import com.developer.entity.ProcessDefinition;
import com.developer.repository.FunctionUnitRepository;
import com.developer.repository.ProcessDefinitionRepository;
import com.developer.repository.VersionRepository;
import com.developer.util.XmlEncodingUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Derives the call relations between Function Units from their design-time BPMN.
 *
 * <p>Both directions are answered, because "who calls me" is the half a designer
 * cannot see from their own diagram, and it is exactly what makes a change to a
 * callable unit risky.
 *
 * <p>Nothing is stored: a call is declared by a {@code callActivity}'s
 * {@code calledElement}, so the BPMN is already the record. A cached table would
 * only be something to drift out of step with the diagrams.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FunctionUnitCallRelationComponent {

    /** One callActivity element, captured whole so its attributes can be read. */
    private static final Pattern CALL_ACTIVITY_PATTERN = Pattern.compile(
            "<(?:\\w+:)?callActivity\\b([^>]*)(/?)>", Pattern.DOTALL);

    private static final Pattern ID_ATTR = Pattern.compile("\\bid=\"([^\"]*)\"");
    private static final Pattern NAME_ATTR = Pattern.compile("\\bname=\"([^\"]*)\"");
    private static final Pattern CALLED_ELEMENT_ATTR = Pattern.compile("\\bcalledElement=\"([^\"]*)\"");

    /** Designer property pinning the call to one published version of the callee. */
    private static final Pattern PINNED_VERSION_PROPERTY = Pattern.compile(
            "<(?:\\w+:)?property\\b[^>]*\\bname=\"calledVersion\"[^>]*\\bvalue=\"([^\"]*)\"");

    private final FunctionUnitRepository functionUnitRepository;
    private final ProcessDefinitionRepository processDefinitionRepository;
    /** Published version snapshots, used to tell whether a pinned version still exists. */
    private final VersionRepository versionRepository;

    /**
     * Outgoing and incoming calls for one Function Unit.
     *
     * <p>Every unit's BPMN is scanned to answer the incoming half. That is one pass
     * over the design catalog; it runs when a designer opens the process tab, not on
     * a runtime hot path.
     */
    public FunctionUnitCallRelations resolve(Long functionUnitId) {
        FunctionUnit self = functionUnitRepository.findById(functionUnitId).orElse(null);
        if (self == null) {
            return FunctionUnitCallRelations.builder()
                    .functionUnitId(functionUnitId)
                    .calls(List.of())
                    .calledBy(List.of())
                    .build();
        }

        List<FunctionUnit> allUnits = functionUnitRepository.findAll();
        Map<String, FunctionUnit> unitByCode = new HashMap<>();
        for (FunctionUnit unit : allUnits) {
            if (unit.getCode() != null) {
                unitByCode.put(unit.getCode(), unit);
            }
        }

        return FunctionUnitCallRelations.builder()
                .functionUnitId(self.getId())
                .functionUnitCode(self.getCode())
                .functionUnitName(self.getName())
                .calls(resolveOutgoing(self, unitByCode))
                .calledBy(resolveIncoming(self, allUnits))
                .build();
    }

    /** The units this one's own diagram calls. */
    private List<FunctionUnitCallRelations.CalledUnit> resolveOutgoing(
            FunctionUnit self, Map<String, FunctionUnit> unitByCode) {

        List<FunctionUnitCallRelations.CalledUnit> out = new ArrayList<>();
        for (CallActivityRef ref : parseCallActivities(bpmnOf(self.getId()))) {
            if (ref.calledElement() == null || ref.calledElement().isBlank()) {
                // A call step with no target yet: worth showing as unconfigured
                // rather than hiding, since it will fail deployment.
                out.add(FunctionUnitCallRelations.CalledUnit.builder()
                        .callActivityId(ref.elementId())
                        .callActivityName(ref.name())
                        .multiInstance(ref.multiInstance())
                        .build());
                continue;
            }

            FunctionUnit target = unitByCode.get(ref.calledElement());
            out.add(FunctionUnitCallRelations.CalledUnit.builder()
                    .callActivityId(ref.elementId())
                    .callActivityName(ref.name())
                    .code(ref.calledElement())
                    .name(target != null ? target.getName() : null)
                    .id(target != null ? target.getId() : null)
                    .callable(target != null
                            && target.getStartupMode() != null
                            && target.getStartupMode().allowsBeingCalled())
                    .multiInstance(ref.multiInstance())
                    .pinnedVersion(ref.pinnedVersion())
                    .pinnedVersionAvailable(isPinnedVersionAvailable(target, ref.pinnedVersion()))
                    .currentVersion(target != null ? target.getCurrentVersion() : null)
                    .newerVersionAvailable(hasNewerVersion(target, ref.pinnedVersion()))
                    .build());
        }
        return out;
    }

    /**
     * Whether a pinned version still exists on the target.
     *
     * <p>A pin that no longer resolves is the failure worth surfacing early: the
     * callee's owner deleted or never published that version, and the call will
     * fail at deploy time with nothing on screen explaining why.
     *
     * <p>An unpinned call is trivially fine — it follows whatever is deployed.
     */
    private boolean isPinnedVersionAvailable(FunctionUnit target, String pinnedVersion) {
        if (target == null) {
            return false;
        }
        if (pinnedVersion == null || pinnedVersion.isBlank()) {
            return true;
        }
        if (pinnedVersion.equals(target.getCurrentVersion())) {
            return true;
        }
        return versionRepository
                .findByFunctionUnitIdAndVersionNumber(target.getId(), pinnedVersion)
                .isPresent();
    }

    /**
     * Whether the target has moved past the version this call is pinned to.
     *
     * <p>Unpinned calls are never "behind" — they follow whatever is deployed. A pin
     * that names a version newer than current (e.g. pinned ahead of a rollback) is
     * not behind either; only strictly older counts.
     */
    private boolean hasNewerVersion(FunctionUnit target, String pinnedVersion) {
        if (target == null || pinnedVersion == null || pinnedVersion.isBlank()) {
            return false;
        }
        String current = target.getCurrentVersion();
        if (current == null || current.isBlank()) {
            return false;
        }
        return compareVersions(current, pinnedVersion) > 0;
    }

    /**
     * Compares dotted versions part by part as numbers.
     *
     * <p>A plain string comparison would rank {@code 1.10.0} below {@code 1.9.0} and
     * report the wrong one as newer. A non-numeric part (e.g. {@code -SNAPSHOT}) is
     * compared by its leading digits, which is enough for the MAJOR.MINOR.PATCH
     * versions this platform publishes.
     */
    static int compareVersions(String a, String b) {
        String[] left = a.trim().split("\\.");
        String[] right = b.trim().split("\\.");
        int length = Math.max(left.length, right.length);
        for (int i = 0; i < length; i++) {
            int l = i < left.length ? leadingNumber(left[i]) : 0;
            int r = i < right.length ? leadingNumber(right[i]) : 0;
            if (l != r) {
                return Integer.compare(l, r);
            }
        }
        return 0;
    }

    private static int leadingNumber(String part) {
        int end = 0;
        while (end < part.length() && Character.isDigit(part.charAt(end))) {
            end++;
        }
        if (end == 0) {
            return 0;
        }
        try {
            return Integer.parseInt(part.substring(0, end));
        } catch (NumberFormatException e) {
            return Integer.MAX_VALUE;
        }
    }

    /** The units whose diagrams call this one. */
    private List<FunctionUnitCallRelations.CallerUnit> resolveIncoming(
            FunctionUnit self, List<FunctionUnit> allUnits) {

        if (self.getCode() == null || self.getCode().isBlank()) {
            return List.of();
        }

        List<FunctionUnitCallRelations.CallerUnit> out = new ArrayList<>();
        for (FunctionUnit candidate : allUnits) {
            if (candidate.getId().equals(self.getId())) {
                continue;
            }
            String bpmn = bpmnOf(candidate.getId());
            // Cheap reject before parsing: most units call nothing at all.
            if (bpmn == null || !bpmn.contains(self.getCode())) {
                continue;
            }
            for (CallActivityRef ref : parseCallActivities(bpmn)) {
                if (self.getCode().equals(ref.calledElement())) {
                    out.add(FunctionUnitCallRelations.CallerUnit.builder()
                            .id(candidate.getId())
                            .code(candidate.getCode())
                            .name(candidate.getName())
                            .callActivityId(ref.elementId())
                            .callActivityName(ref.name())
                            .pinnedVersion(ref.pinnedVersion())
                            .build());
                }
            }
        }
        return out;
    }

    /** Plain BPMN for a unit, decoding the stored form; null when it has no process. */
    private String bpmnOf(Long functionUnitId) {
        Optional<ProcessDefinition> processOpt =
                processDefinitionRepository.findByFunctionUnitId(functionUnitId);
        if (processOpt.isEmpty() || processOpt.get().getBpmnXml() == null) {
            return null;
        }
        try {
            return XmlEncodingUtil.smartDecode(processOpt.get().getBpmnXml());
        } catch (Exception e) {
            log.warn("Could not decode BPMN of function unit {}: {}", functionUnitId, e.getMessage());
            return null;
        }
    }

    private List<CallActivityRef> parseCallActivities(String bpmnXml) {
        if (bpmnXml == null || bpmnXml.isBlank()) {
            return List.of();
        }
        List<CallActivityRef> out = new ArrayList<>();
        Matcher matcher = CALL_ACTIVITY_PATTERN.matcher(bpmnXml);
        while (matcher.find()) {
            String attrs = matcher.group(1);
            boolean selfClosing = "/".equals(matcher.group(2));

            // Loop characteristics and the pinned version both live in the element
            // body, so a self-closing call activity has neither.
            String body = selfClosing ? "" : bodyOf(bpmnXml, matcher.end());

            out.add(new CallActivityRef(
                    firstGroup(ID_ATTR, attrs),
                    firstGroup(NAME_ATTR, attrs),
                    firstGroup(CALLED_ELEMENT_ATTR, attrs),
                    body.contains("multiInstanceLoopCharacteristics"),
                    firstGroup(PINNED_VERSION_PROPERTY, body)));
        }
        return out;
    }

    /**
     * The XML between a call activity's opening tag and its own closing tag.
     *
     * <p>Scans for the matching {@code </…callActivity>} rather than the first
     * {@code </} encountered: a call activity's body contains nested elements
     * (extension properties, loop characteristics), and stopping at the first
     * close tag would cut the body short and hide them.
     */
    private String bodyOf(String xml, int fromIndex) {
        int cursor = xml.indexOf("</", fromIndex);
        while (cursor >= 0) {
            int tagEnd = xml.indexOf('>', cursor);
            if (tagEnd < 0) {
                break;
            }
            if (xml.substring(cursor, tagEnd).contains("callActivity")) {
                return xml.substring(fromIndex, cursor);
            }
            cursor = xml.indexOf("</", tagEnd);
        }
        return "";
    }

    private String firstGroup(Pattern pattern, String input) {
        if (input == null) {
            return null;
        }
        Matcher m = pattern.matcher(input);
        return m.find() ? m.group(1) : null;
    }

    private record CallActivityRef(String elementId, String name, String calledElement,
                                   boolean multiInstance, String pinnedVersion) {
    }
}
