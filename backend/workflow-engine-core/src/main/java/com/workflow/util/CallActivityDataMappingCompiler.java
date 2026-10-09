package com.workflow.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.exception.WorkflowBusinessException;
import lombok.extern.slf4j.Slf4j;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayInputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Turns a Function Unit call step's designer settings into the Flowable XML that moves data.
 *
 * <p>The designer stores three platform properties on a {@code callActivity}:
 * <ul>
 *   <li>{@code callRowsTable} — for a call that runs once per row: the sub-table whose rows it
 *       runs for. Compiled to the loop's {@code flowable:collection}, an expression that reads
 *       those rows from {@code __subTables__} when the call starts (so rows added at any point
 *       before then are included), with each row available as {@value #ROW_VARIABLE}.</li>
 *   <li>{@code callInputMapping} — {@code [{"from": field, "to": calledField}]}: values handed to
 *       the called Function Unit. {@code from} is a field of this request, or {@code row.<field>}
 *       for a field of the row a per-row call is running for. Compiled to {@code flowable:in}.</li>
 *   <li>{@code callOutputMapping} — {@code [{"from": calledField, "to": field}]}: values copied
 *       back when the called unit finishes. For a call that runs once, {@code to} is a field of
 *       this request, compiled to {@code flowable:out}. For a per-row call, {@code to} is
 *       {@code row.<field>} — each call's result goes into the row it ran for. A list element is
 *       not something {@code flowable:out} can address, so those are left to the portal, which
 *       applies them when each call finishes; here they are only validated.</li>
 * </ul>
 *
 * <p>A per-row call also hands its child the row it runs for, as {@value #CALL_ROW_VARIABLE}, so
 * that result can find its way back to that row.
 *
 * <p>Done at deploy time, in the engine, because these are Flowable constructs: the designer
 * edits a platform-level description and never has to know Flowable's variable-passing XML.
 * A malformed setting fails the deployment with a reason the designer can act on — a call that
 * silently dropped its data would be worse than one that does not deploy.
 */
@Slf4j
public final class CallActivityDataMappingCompiler {

    static final String ROW_VARIABLE = "callRow";
    /** The row a per-row call runs for, as seen by the called instance. */
    static final String CALL_ROW_VARIABLE = "__callRow";
    static final String ROWS_TABLE = "callRowsTable";
    static final String INPUT_MAPPING = "callInputMapping";
    static final String OUTPUT_MAPPING = "callOutputMapping";

    private static final String FLOWABLE_NS = "http://flowable.org/bpmn";
    private static final String BPMN_NS = "http://www.omg.org/spec/BPMN/20100524/MODEL";
    private static final String ROW_PREFIX = "row.";
    /** Field and table names as the platform creates them; also keeps them safe inside an expression. */
    private static final Pattern NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
    private static final ObjectMapper JSON = new ObjectMapper();

    private CallActivityDataMappingCompiler() {
    }

    /**
     * The BPMN with every call step's data settings compiled; unchanged (same string) when no
     * call step has any.
     *
     * @throws WorkflowBusinessException when a setting is malformed
     */
    public static String compile(String bpmnXml) {
        if (bpmnXml == null
                || !(bpmnXml.contains(ROWS_TABLE) || bpmnXml.contains(INPUT_MAPPING) || bpmnXml.contains(OUTPUT_MAPPING))) {
            return bpmnXml;
        }
        Document doc = parse(bpmnXml);
        Element root = doc.getDocumentElement();
        if (!root.hasAttribute("xmlns:flowable")) {
            root.setAttribute("xmlns:flowable", FLOWABLE_NS);
        }

        int compiled = 0;
        NodeList calls = doc.getElementsByTagNameNS(BPMN_NS, "callActivity");
        for (int i = 0; i < calls.getLength(); i++) {
            if (compileCall(doc, (Element) calls.item(i))) {
                compiled++;
            }
        }
        if (compiled == 0) {
            return bpmnXml;
        }
        log.info("Compiled data mappings of {} call activit{}", compiled, compiled == 1 ? "y" : "ies");
        return serialize(doc);
    }

    private static boolean compileCall(Document doc, Element call) {
        String id = call.getAttribute("id");
        Map<String, String> props = platformProperties(call);
        String rowsTable = blankToNull(props.get(ROWS_TABLE));
        List<Map<String, String>> inputs = mapping(props.get(INPUT_MAPPING), INPUT_MAPPING, id);
        List<Map<String, String>> outputs = mapping(props.get(OUTPUT_MAPPING), OUTPUT_MAPPING, id);
        if (rowsTable == null && inputs.isEmpty() && outputs.isEmpty()) {
            return false;
        }

        Element loop = firstChild(call, "multiInstanceLoopCharacteristics");
        if (rowsTable != null) {
            requireName(rowsTable, "the rows table", id);
            if (loop == null) {
                throw fail(id, "names rows table '" + rowsTable + "' but does not run once per row");
            }
            loop.setAttributeNS(FLOWABLE_NS, "flowable:collection",
                    "${fuCallRows.of(execution, '" + rowsTable + "')}");
            loop.setAttributeNS(FLOWABLE_NS, "flowable:elementVariable", ROW_VARIABLE);
        }
        for (Map<String, String> entry : outputs) {
            boolean toRow = entry.get("to").startsWith(ROW_PREFIX);
            if (loop != null && !toRow) {
                throw fail(id, "runs once per row, so it can only copy values back into the row each call ran for, "
                        + "not into '" + entry.get("to") + "'");
            }
            if (loop == null && toRow) {
                throw fail(id, "copies a value into row field '" + entry.get("to") + "' but does not run once per row");
            }
        }

        Element extensions = firstChild(call, "extensionElements");
        if (extensions == null) {
            extensions = doc.createElementNS(BPMN_NS, qualified(call, "extensionElements"));
            call.insertBefore(extensions, call.getFirstChild());
        }
        for (Map<String, String> entry : inputs) {
            String from = entry.get("from");
            String to = entry.get("to");
            requireName(to, "a called field", id);
            Element in = doc.createElementNS(FLOWABLE_NS, "flowable:in");
            if (from.startsWith(ROW_PREFIX)) {
                String rowField = from.substring(ROW_PREFIX.length());
                requireName(rowField, "a row field", id);
                if (rowsTable == null) {
                    throw fail(id, "maps row field '" + rowField + "' but does not run once per row");
                }
                in.setAttribute("sourceExpression", "${" + ROW_VARIABLE + "['" + rowField + "']}");
            } else {
                requireName(from, "a field", id);
                in.setAttribute("source", from);
            }
            in.setAttribute("target", to);
            extensions.appendChild(in);
        }
        if (rowsTable != null) {
            Element row = doc.createElementNS(FLOWABLE_NS, "flowable:in");
            row.setAttribute("source", ROW_VARIABLE);
            row.setAttribute("target", CALL_ROW_VARIABLE);
            extensions.appendChild(row);
        }
        for (Map<String, String> entry : outputs) {
            requireName(entry.get("from"), "a called field", id);
            String to = entry.get("to");
            if (to.startsWith(ROW_PREFIX)) {
                requireName(to.substring(ROW_PREFIX.length()), "a row field", id);
                continue; // written into the row by the portal when this call finishes
            }
            requireName(to, "a field", id);
            Element out = doc.createElementNS(FLOWABLE_NS, "flowable:out");
            out.setAttribute("source", entry.get("from"));
            out.setAttribute("target", entry.get("to"));
            extensions.appendChild(out);
        }
        return true;
    }

    /** Platform extension properties of one element, any prefix (the designer uses more than one). */
    private static Map<String, String> platformProperties(Element element) {
        Map<String, String> out = new LinkedHashMap<>();
        Element extensions = firstChild(element, "extensionElements");
        if (extensions == null) {
            return out;
        }
        NodeList all = extensions.getElementsByTagNameNS("*", "property");
        for (int i = 0; i < all.getLength(); i++) {
            Element property = (Element) all.item(i);
            if (property.hasAttribute("name")) {
                out.putIfAbsent(property.getAttribute("name"), property.getAttribute("value"));
            }
        }
        return out;
    }

    private static List<Map<String, String>> mapping(String json, String property, String callId) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        List<Map<String, Object>> raw;
        try {
            raw = JSON.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            throw fail(callId, "has an unreadable " + property);
        }
        List<Map<String, String>> out = new ArrayList<>();
        for (Map<String, Object> entry : raw) {
            String from = entry.get("from") == null ? null : String.valueOf(entry.get("from")).trim();
            String to = entry.get("to") == null ? null : String.valueOf(entry.get("to")).trim();
            if ((from == null || from.isEmpty()) && (to == null || to.isEmpty())) {
                continue; // a row the designer added and left empty
            }
            if (from == null || from.isEmpty() || to == null || to.isEmpty()) {
                throw fail(callId, "has a " + property + " entry with only one side filled in");
            }
            out.add(Map.of("from", from, "to", to));
        }
        return out;
    }

    private static void requireName(String value, String what, String callId) {
        if (value == null || !NAME.matcher(value).matches()) {
            throw fail(callId, "names " + what + " '" + value + "' that is not a valid field name");
        }
    }

    private static WorkflowBusinessException fail(String callId, String reason) {
        return new WorkflowBusinessException("CALL_ACTIVITY_MAPPING_INVALID",
                "Call activity '" + callId + "' " + reason + ".");
    }

    private static Element firstChild(Element parent, String localName) {
        for (Node n = parent.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element e && localName.equals(e.getLocalName())) {
                return e;
            }
        }
        return null;
    }

    /** Same prefix as the element it goes into, so the document keeps one prefix per namespace. */
    private static String qualified(Element sibling, String localName) {
        String prefix = sibling.getPrefix();
        return prefix == null || prefix.isEmpty() ? localName : prefix + ":" + localName;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static Document parse(String xml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            // XXE hardening, as in BpmnDeployEnhancer.
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            factory.setAttribute(javax.xml.XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(javax.xml.XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            return factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new WorkflowBusinessException("BPMN_INVALID", "BPMN could not be read: " + e.getMessage());
        }
    }

    private static String serialize(Document doc) {
        try {
            Transformer transformer = TransformerFactory.newInstance().newTransformer();
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
            StringWriter writer = new StringWriter();
            transformer.transform(new DOMSource(doc), new StreamResult(writer));
            return writer.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Could not write compiled BPMN", e);
        }
    }
}
