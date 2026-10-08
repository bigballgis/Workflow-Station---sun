package com.developer.component.impl.compare;

import com.developer.exception.DeveloperBusinessException;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.XMLConstants;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Canonicalizes BPMN/DMN XML without resolving external resources. */
public final class VersionXmlNormalizer {

    private static final String UNPARSEABLE_PREFIX = "!UNPARSEABLE_XML:";

    public String normalize(String xml) {
        if (xml == null || xml.isBlank()) {
            return "";
        }
        Document document = parse(xml);
        if (document == null) return UNPARSEABLE_PREFIX + xml.replace("\r\n", "\n");
        StringBuilder canonical = new StringBuilder();
        append(document.getDocumentElement(), canonical);
        return canonical.toString();
    }

    public String decisionKey(String xml, int index) {
        String key = stableDecisionKey(xml);
        return key.isBlank() ? "decision-" + index : key;
    }

    String stableDecisionKey(String xml) {
        if (xml == null || xml.isBlank()) {
            return "";
        }
        Document document = parse(xml);
        if (document == null) return "";
        var decisions = document.getElementsByTagNameNS("*", "decision");
        if (decisions.getLength() > 0) {
            Node id = decisions.item(0).getAttributes().getNamedItem("id");
            if (id != null && !id.getNodeValue().isBlank()) {
                return id.getNodeValue();
            }
        }
        return "";
    }

    Document parseForSemantic(String xml) {
        return parse(xml);
    }

    private Document parse(String xml) {
        String upper = xml.toUpperCase(Locale.ROOT);
        if (upper.contains("<!DOCTYPE") || upper.contains("<!ENTITY")) {
            throw new DeveloperBusinessException("BIZ_VERSION_COMPARE_XML_INVALID",
                    "A saved version contains unsafe BPMN/DMN XML declarations");
        }
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            var builder = factory.newDocumentBuilder();
            builder.setErrorHandler(new DefaultHandler());
            return builder.parse(new InputSource(new StringReader(xml)));
        } catch (ParserConfigurationException e) {
            throw new IllegalStateException("Secure XML parser configuration is unavailable", e);
        } catch (SAXException | IOException e) {
            // Some historical snapshots contain malformed DMN. Compare the saved text
            // without interpreting it rather than suppressing every other module.
            return null;
        }
    }

    private void append(Node node, StringBuilder out) {
        if (node.getNodeType() == Node.ELEMENT_NODE) {
            String name = nodeName(node);
            out.append('<').append(name);
            NamedNodeMap attributes = node.getAttributes();
            List<Node> sorted = new ArrayList<>();
            for (int index = 0; index < attributes.getLength(); index++) {
                Node attribute = attributes.item(index);
                if (!XMLConstants.XMLNS_ATTRIBUTE_NS_URI.equals(attribute.getNamespaceURI())) {
                    sorted.add(attribute);
                }
            }
            sorted.sort(Comparator.comparing(this::nodeName));
            for (Node attribute : sorted) {
                out.append(' ').append(nodeName(attribute)).append('=').append(attribute.getNodeValue().length())
                        .append(':').append(attribute.getNodeValue());
            }
            out.append('>');
            for (Node child = node.getFirstChild(); child != null; child = child.getNextSibling()) {
                append(child, out);
            }
            out.append("</").append(name).append('>');
        } else if (node.getNodeType() == Node.TEXT_NODE || node.getNodeType() == Node.CDATA_SECTION_NODE) {
            String content = node.getNodeValue().replace("\r\n", "\n");
            if (!content.isBlank()) {
                out.append(content.length()).append(':').append(content);
            }
        }
    }

    private String nodeName(Node node) {
        return "{" + (node.getNamespaceURI() == null ? "" : node.getNamespaceURI()) + "}"
                + (node.getLocalName() == null ? node.getNodeName() : node.getLocalName());
    }
}
