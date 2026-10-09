package com.admin.component;

import com.admin.entity.FunctionUnit;
import com.admin.entity.FunctionUnitContent;
import com.admin.enums.ContentType;
import com.admin.repository.FunctionUnitContentRepository;
import com.admin.repository.FunctionUnitRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Rewrites pinned Function Unit calls so they target one exact deployed process.
 *
 * <h2>Why rewriting is needed</h2>
 * A {@code callActivity} normally names the callee by process key, and Flowable then
 * runs whatever version of that key is newest at the moment the token arrives. That is
 * the right default, but it means the callee's owner changes what the caller does every
 * time they publish — without the caller knowing.
 *
 * <p>When a designer pins a call to a published version, the BPMN carries that version
 * in a {@code calledVersion} extension property. Here, at deploy time, it is resolved to
 * the concrete Flowable process definition id for that version and written into
 * {@code calledElement}, with {@code flowable:calledElementType="id"} so the engine
 * treats it as an id rather than a key.
 *
 * <p>Resolution happens here rather than in the engine because the mapping from a
 * published version to a Flowable definition lives in this service's catalog
 * ({@code sys_function_unit_contents}); the engine has no notion of semantic versions.
 *
 * <p>An unresolvable pin fails the deployment. Falling back to "latest" would silently
 * give the caller a different process than the one it was designed and tested against.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CallActivityVersionResolver {

    /** A whole callActivity element, so its attributes and body can both be read. */
    private static final Pattern CALL_ACTIVITY_PATTERN = Pattern.compile(
            "<(?:\\w+:)?callActivity\\b([^>]*?)(/?)>", Pattern.DOTALL);

    private static final Pattern CALLED_ELEMENT_ATTR = Pattern.compile("\\bcalledElement=\"([^\"]*)\"");
    private static final Pattern ID_ATTR = Pattern.compile("\\bid=\"([^\"]*)\"");

    /** Designer property naming the published version this call is pinned to. */
    private static final Pattern PINNED_VERSION_PROPERTY = Pattern.compile(
            "<(?:\\w+:)?property\\b[^>]*\\bname=\"calledVersion\"[^>]*\\bvalue=\"([^\"]*)\"");

    private final FunctionUnitRepository functionUnitRepository;
    private final FunctionUnitContentRepository contentRepository;

    /**
     * Returns the BPMN with every pinned call rewritten to its exact process definition.
     *
     * <p>Diagrams with no pinned calls come back byte-for-byte unchanged, so this is a
     * no-op for the overwhelming majority of processes.
     *
     * @throws IllegalStateException when a pin names a version that is not deployed
     */
    public String resolvePinnedCalls(String bpmnXml) {
        if (bpmnXml == null || !bpmnXml.contains("calledVersion")) {
            return bpmnXml;
        }

        StringBuilder out = new StringBuilder(bpmnXml.length());
        Matcher matcher = CALL_ACTIVITY_PATTERN.matcher(bpmnXml);
        int cursor = 0;
        boolean rewroteAnything = false;

        while (matcher.find()) {
            String attrs = matcher.group(1);
            boolean selfClosing = "/".equals(matcher.group(2));
            String body = selfClosing ? "" : bodyOf(bpmnXml, matcher.end());

            String pinnedVersion = firstGroup(PINNED_VERSION_PROPERTY, body);
            String calledCode = firstGroup(CALLED_ELEMENT_ATTR, attrs);
            String elementId = firstGroup(ID_ATTR, attrs);

            if (pinnedVersion == null || pinnedVersion.isBlank()
                    || calledCode == null || calledCode.isBlank()) {
                continue;
            }

            String definitionId = resolveDefinitionId(calledCode, pinnedVersion, elementId);

            // Replace the key with the concrete definition id and mark it as an id, so
            // Flowable stops resolving "latest of this key".
            String rewrittenAttrs = CALLED_ELEMENT_ATTR
                    .matcher(attrs)
                    .replaceFirst(Matcher.quoteReplacement(
                            "calledElement=\"" + definitionId + "\" flowable:calledElementType=\"id\""));

            out.append(bpmnXml, cursor, matcher.start(1));
            out.append(rewrittenAttrs);
            cursor = matcher.end(1);
            rewroteAnything = true;

            log.info("Pinned call activity {} -> Function Unit '{}' version {} ({})",
                    elementId, calledCode, pinnedVersion, definitionId);
        }

        if (!rewroteAnything) {
            return bpmnXml;
        }
        out.append(bpmnXml.substring(cursor));
        return out.toString();
    }

    /**
     * The Flowable process definition id deployed for one published version.
     *
     * @throws IllegalStateException when that version is not deployed in this environment
     */
    private String resolveDefinitionId(String calledCode, String pinnedVersion, String elementId) {
        Optional<FunctionUnit> targetOpt =
                functionUnitRepository.findByCodeAndVersion(calledCode, pinnedVersion);
        if (targetOpt.isEmpty()) {
            throw new CallActivityPinUnresolvableException(
                    "Call activity '" + elementId + "' is pinned to version " + pinnedVersion
                            + " of Function Unit '" + calledCode + "', which is not deployed in this "
                            + "environment. Deploy that version, or re-point the call at a version that exists.");
        }

        List<FunctionUnitContent> processContents = contentRepository
                .findByFunctionUnitIdAndContentType(targetOpt.get().getId(), ContentType.PROCESS);

        String definitionId = processContents.stream()
                .map(FunctionUnitContent::getFlowableProcessDefinitionId)
                .filter(id -> id != null && !id.isBlank())
                .findFirst()
                .orElse(null);

        if (definitionId == null) {
            throw new CallActivityPinUnresolvableException(
                    "Call activity '" + elementId + "' is pinned to version " + pinnedVersion
                            + " of Function Unit '" + calledCode + "', but that version has no deployed "
                            + "process. Deploy it before deploying the calling Function Unit.");
        }
        return definitionId;
    }

    /** XML between a call activity's opening tag and its own closing tag. */
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
}
