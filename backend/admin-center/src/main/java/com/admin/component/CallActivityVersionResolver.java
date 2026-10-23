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
 * Rewrites every Function Unit call so it targets one exact deployed process.
 *
 * <h2>Why rewriting is needed</h2>
 * A {@code callActivity} names the callee by process key, and Flowable would then run
 * whatever version of that key is newest at the moment the token arrives — even one
 * published, disabled or rolled back after the caller was deployed. The callee's owner
 * would change what an already-running request does, without the caller knowing.
 *
 * <p>So each call is fixed at deploy time to one release of the callee: the version the
 * designer chose ({@code calledVersion} extension property), or else the callee's release
 * that is active when the caller is deployed — the one Portal would start a new request
 * of it on. That release's Flowable process definition id is written into
 * {@code calledElement}, with {@code flowable:calledElementType="id"} so the engine treats
 * it as an id rather than a key, and the release itself is handed to the called instance
 * ({@code flowable:in} constants), so the called instance resolves its forms, emails and
 * tables in that release from its very first step.
 *
 * <p>Resolution happens here rather than in the engine because the mapping from a
 * published version to a Flowable definition lives in this service's catalog
 * ({@code sys_function_unit_contents}); the engine has no notion of semantic versions.
 *
 * <p>A call that cannot be fixed to a release fails the deployment. Falling back to
 * "latest" would silently give the caller a different process than the one it was
 * designed and tested against.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CallActivityVersionResolver {

    /** A callActivity start tag: its prefix, attributes and whether it closes itself. */
    private static final Pattern CALL_ACTIVITY_PATTERN = Pattern.compile(
            "<((?:\\w+:)?)callActivity\\b([^>]*?)(/?)>", Pattern.DOTALL);
    /** The call's own extension block: by the BPMN schema its first child, after any documentation. */
    private static final Pattern EXTENSION_ELEMENTS_START = Pattern.compile(
            "<((?:\\w+:)?)extensionElements\\b[^>]*?(/?)>", Pattern.DOTALL);
    private static final Pattern DOCUMENTATION = Pattern.compile(
            "<((?:\\w+:)?)documentation\\b[^>]*?(?:/>|>.*?</\\1documentation\\s*>)", Pattern.DOTALL);
    /** Release identities are written into an expression as plain constants, so only these characters. */
    private static final Pattern PLAIN_CONSTANT = Pattern.compile("[A-Za-z0-9._-]+");

    private static final Pattern CALLED_ELEMENT_ATTR = Pattern.compile("\\bcalledElement=\"([^\"]*)\"");
    private static final Pattern ID_ATTR = Pattern.compile("\\bid=\"([^\"]*)\"");

    private static final String FLOWABLE_NS = "http://flowable.org/bpmn";
    private static final Pattern DEFINITIONS_START_TAG = Pattern.compile("<(?:\\w+:)?definitions\\b");

    /** Designer property naming the published version this call is pinned to. */
    private static final Pattern PINNED_VERSION_PROPERTY = Pattern.compile(
            "<(?:\\w+:)?property\\b[^>]*\\bname=\"calledVersion\"[^>]*\\bvalue=\"([^\"]*)\"");

    private final FunctionUnitRepository functionUnitRepository;
    private final FunctionUnitContentRepository contentRepository;

    /**
     * Decides the callee's active release, by the same rule Portal starts new requests with.
     * Lazy field: the lifecycle reaches this resolver through validation and deployment.
     */
    @org.springframework.context.annotation.Lazy
    @org.springframework.beans.factory.annotation.Autowired
    private FunctionUnitLifecycleComponent lifecycle;

    /**
     * Returns the BPMN with every call rewritten to one release's exact process definition,
     * and that release handed to the called instance.
     *
     * <p>Diagrams with no calls come back byte-for-byte unchanged, so this is a no-op for
     * the overwhelming majority of processes.
     *
     * @throws CallActivityPinUnresolvableException when a call cannot be fixed to a deployed release
     */
    public String resolvePinnedCalls(String bpmnXml) {
        if (bpmnXml == null || !bpmnXml.contains("callActivity")) {
            return bpmnXml;
        }

        StringBuilder out = new StringBuilder(bpmnXml.length());
        Matcher matcher = CALL_ACTIVITY_PATTERN.matcher(bpmnXml);
        int cursor = 0;
        boolean rewroteAnything = false;

        while (matcher.find()) {
            String prefix = matcher.group(1);
            String attrs = matcher.group(2);
            boolean selfClosing = "/".equals(matcher.group(3));
            String body = selfClosing ? "" : bodyOf(bpmnXml, matcher.end());

            String pinnedVersion = firstGroup(PINNED_VERSION_PROPERTY, body);
            String calledCode = firstGroup(CALLED_ELEMENT_ATTR, attrs);
            String elementId = firstGroup(ID_ATTR, attrs);

            if (calledCode == null || calledCode.isBlank()) {
                continue;
            }

            FunctionUnit callee = pinnedVersion == null || pinnedVersion.isBlank()
                    ? activeRelease(calledCode, elementId)
                    : publishedVersion(calledCode, pinnedVersion, elementId);
            String definitionId = definitionIdOf(callee, elementId);
            String releaseParameters = releaseParameters(callee, elementId);

            // Replace the key with the concrete definition id and mark it as an id, so
            // Flowable stops resolving "latest of this key".
            String rewrittenAttrs = CALLED_ELEMENT_ATTR
                    .matcher(attrs)
                    .replaceFirst(Matcher.quoteReplacement(
                            "calledElement=\"" + definitionId + "\" flowable:calledElementType=\"id\""));

            out.append(bpmnXml, cursor, matcher.start(2));
            out.append(rewrittenAttrs);
            cursor = appendReleaseParameters(out, bpmnXml, matcher, prefix, selfClosing, releaseParameters);
            rewroteAnything = true;

            log.info("Pinned call activity {} -> Function Unit '{}' version {} ({})",
                    elementId, calledCode, callee.getVersion(), definitionId);
        }

        if (!rewroteAnything) {
            return bpmnXml;
        }
        out.append(bpmnXml.substring(cursor));
        return ensureFlowableNamespace(out.toString());
    }

    /**
     * Declares the {@code flowable} prefix on the root element when the designer did not.
     * The rewrite above writes {@code flowable:calledElementType}; with the prefix unbound
     * the XML is not well-formed and Flowable refuses to read it.
     */
    private String ensureFlowableNamespace(String xml) {
        if (xml.contains("xmlns:flowable=")) {
            return xml;
        }
        Matcher root = DEFINITIONS_START_TAG.matcher(xml);
        if (!root.find()) {
            return xml;
        }
        return xml.substring(0, root.end())
                + " xmlns:flowable=\"" + FLOWABLE_NS + "\""
                + xml.substring(root.end());
    }

    /**
     * Writes the rest of the call's start tag plus the release parameters, merged into the
     * call's own extension block (the engine reads the designer's call settings there).
     *
     * @return where copying the original XML resumes
     */
    private static int appendReleaseParameters(StringBuilder out, String xml, Matcher call, String prefix,
                                               boolean selfClosing, String parameters) {
        if (selfClosing) {
            out.append('>').append(extensionBlock(prefix, parameters))
                    .append("</").append(prefix).append("callActivity>");
            return call.end();
        }
        out.append(xml, call.end(2), call.end());
        int at = ownExtensionPosition(xml, call.end());
        out.append(xml, call.end(), at);
        Matcher extensions = EXTENSION_ELEMENTS_START.matcher(xml).region(at, xml.length());
        if (!extensions.lookingAt()) {
            out.append(extensionBlock(prefix, parameters));
            return at;
        }
        if ("/".equals(extensions.group(2))) {
            out.append(extensionBlock(extensions.group(1), parameters));
        } else {
            out.append(extensions.group()).append(parameters);
        }
        return extensions.end();
    }

    /** Where the call's own extension block starts, or belongs: past whitespace and documentation. */
    private static int ownExtensionPosition(String xml, int from) {
        int at = skipWhitespace(xml, from);
        Matcher documentation = DOCUMENTATION.matcher(xml);
        while (documentation.region(at, xml.length()).lookingAt()) {
            at = skipWhitespace(xml, documentation.end());
        }
        return at;
    }

    private static int skipWhitespace(String xml, int from) {
        int at = from;
        while (at < xml.length() && Character.isWhitespace(xml.charAt(at))) {
            at++;
        }
        return at;
    }

    private static String extensionBlock(String prefix, String content) {
        return "<" + prefix + "extensionElements>" + content + "</" + prefix + "extensionElements>";
    }

    /** The callee release as constants the called instance starts with, under the Portal's pin keys. */
    private static String releaseParameters(FunctionUnit callee, String elementId) {
        StringBuilder in = new StringBuilder();
        for (String[] pin : new String[][] {
                {"functionUnitId", callee.getId()},
                {"functionUnitCode", callee.getCode()},
                {"functionUnitVersionLabel", callee.getVersion()}}) {
            if (pin[1] == null || !PLAIN_CONSTANT.matcher(pin[1]).matches()) {
                throw new CallActivityPinUnresolvableException(
                        "Call activity '" + elementId + "' calls a release whose " + pin[0] + " '" + pin[1]
                                + "' cannot be handed to the called process as a constant.");
            }
            in.append("<flowable:in sourceExpression=\"${'").append(pin[1])
                    .append("'}\" target=\"").append(pin[0]).append("\"/>");
        }
        return in.toString();
    }

    /**
     * The callee's release a new request would start on (deployed, enabled, highest version).
     *
     * @throws CallActivityPinUnresolvableException when the callee has none in this environment
     */
    private FunctionUnit activeRelease(String calledCode, String elementId) {
        return lifecycle.getActiveCatalogForPortalStart(calledCode)
                .orElseThrow(() -> new CallActivityPinUnresolvableException(
                        "Call activity '" + elementId + "' calls Function Unit '" + calledCode
                                + "', which has no enabled deployed version in this environment. "
                                + "Deploy and enable it before deploying the calling Function Unit."));
    }

    /**
     * The published version a call is pinned to.
     *
     * @throws CallActivityPinUnresolvableException when that version is not deployed in this environment
     */
    private FunctionUnit publishedVersion(String calledCode, String pinnedVersion, String elementId) {
        Optional<FunctionUnit> targetOpt =
                functionUnitRepository.findByCodeAndVersion(calledCode, pinnedVersion);
        if (targetOpt.isEmpty()) {
            throw new CallActivityPinUnresolvableException(
                    "Call activity '" + elementId + "' is pinned to version " + pinnedVersion
                            + " of Function Unit '" + calledCode + "', which is not deployed in this "
                            + "environment. Deploy that version, or re-point the call at a version that exists.");
        }
        return targetOpt.get();
    }

    /**
     * The Flowable process definition id deployed for one release.
     *
     * @throws CallActivityPinUnresolvableException when that release has no deployed process
     */
    private String definitionIdOf(FunctionUnit callee, String elementId) {
        List<FunctionUnitContent> processContents = contentRepository
                .findByFunctionUnitIdAndContentType(callee.getId(), ContentType.PROCESS);

        String definitionId = processContents.stream()
                .map(FunctionUnitContent::getFlowableProcessDefinitionId)
                .filter(id -> id != null && !id.isBlank())
                .findFirst()
                .orElse(null);

        if (definitionId == null) {
            throw new CallActivityPinUnresolvableException(
                    "Call activity '" + elementId + "' calls version " + callee.getVersion()
                            + " of Function Unit '" + callee.getCode() + "', but that version has no deployed "
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
