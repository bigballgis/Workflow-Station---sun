package com.developer.validation;

import com.developer.dto.ValidationResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.util.Set;

/**
 * DMN XML 验证器
 * 负责 DMN XML 的安全检查和语义验证
 */
@Component
@Slf4j
public class DmnXmlValidator {

    private static final long MAX_XML_SIZE = 1_048_576; // 1MB

    private static final Set<String> VALID_HIT_POLICIES = Set.of(
            "FIRST", "UNIQUE", "ANY", "PRIORITY",
            "COLLECT", "RULE_ORDER", "OUTPUT_ORDER"
    );

    /**
     * 验证 DMN XML 的安全性和语义正确性
     */
    public ValidationResult validate(String dmnXml) {
        ValidationResult result = new ValidationResult();

        // Size limit check
        if (dmnXml == null || dmnXml.isBlank()) {
            result.addError("EMPTY_XML", "DMN XML content is empty", null);
            return result;
        }

        if (dmnXml.getBytes().length > MAX_XML_SIZE) {
            result.addError("XML_TOO_LARGE", "DMN XML exceeds maximum size of 1MB", null);
            return result;
        }

        // Parse XML with XXE protection
        Document document;
        try {
            document = parseSecurely(dmnXml);
        } catch (Exception e) {
            result.addError("INVALID_XML_FORMAT", "DMN XML is not well-formed: " + e.getMessage(), null);
            return result;
        }

        // Semantic validation: at least one decision table
        validateDecisionTableStructure(document, result);

        // Hit policy validation
        validateHitPolicy(document, result);

        // JUEL expression basic syntax check
        validateJuelExpressions(document, result);

        return result;
    }

    private Document parseSecurely(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);

        // XXE prevention
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);

        DocumentBuilder builder = factory.newDocumentBuilder();
        return builder.parse(new InputSource(new StringReader(xml)));
    }

    private void validateDecisionTableStructure(Document document, ValidationResult result) {
        NodeList decisionTables = document.getElementsByTagNameNS("*", "decisionTable");
        if (decisionTables.getLength() == 0) {
            result.addError("NO_DECISION_TABLE", "DMN XML must contain at least one decision table", null);
            return;
        }

        for (int i = 0; i < decisionTables.getLength(); i++) {
            Element table = (Element) decisionTables.item(i);

            NodeList inputs = table.getElementsByTagNameNS("*", "input");
            if (inputs.getLength() == 0) {
                result.addError("NO_INPUT_COLUMN",
                        "Decision table must contain at least one input column", null);
            }

            NodeList outputs = table.getElementsByTagNameNS("*", "output");
            if (outputs.getLength() == 0) {
                result.addError("NO_OUTPUT_COLUMN",
                        "Decision table must contain at least one output column", null);
            }
        }
    }

    private void validateHitPolicy(Document document, ValidationResult result) {
        NodeList decisionTables = document.getElementsByTagNameNS("*", "decisionTable");
        for (int i = 0; i < decisionTables.getLength(); i++) {
            Element table = (Element) decisionTables.item(i);
            String hitPolicy = table.getAttribute("hitPolicy");
            if (hitPolicy != null && !hitPolicy.isEmpty() && !VALID_HIT_POLICIES.contains(hitPolicy)) {
                result.addError("INVALID_HIT_POLICY",
                        "Invalid hit policy: " + hitPolicy + ". Must be one of: " + VALID_HIT_POLICIES, null);
            }
        }
    }

    private void validateJuelExpressions(Document document, ValidationResult result) {
        // Validate input entries
        validateEntryExpressions(document, "inputEntry", result);
        // Validate output entries
        validateEntryExpressions(document, "outputEntry", result);
    }

    private void validateEntryExpressions(Document document, String tagName, ValidationResult result) {
        NodeList entries = document.getElementsByTagNameNS("*", tagName);
        for (int i = 0; i < entries.getLength(); i++) {
            Element entry = (Element) entries.item(i);
            NodeList textNodes = entry.getElementsByTagNameNS("*", "text");
            if (textNodes.getLength() > 0) {
                String expression = textNodes.item(0).getTextContent();
                if (expression != null && !expression.isBlank()) {
                    validateJuelSyntax(expression, tagName, entry.getAttribute("id"), result);
                }
            }
        }
    }

    private void validateJuelSyntax(String expression, String entryType, String entryId, ValidationResult result) {
        String trimmed = expression.trim();
        // Empty or dash means "any" — valid
        if (trimmed.isEmpty() || "-".equals(trimmed)) {
            return;
        }

        // Check for unbalanced ${} or #{} expressions
        int dollarCount = countOccurrences(trimmed, "${");
        int hashCount = countOccurrences(trimmed, "#{");
        int closingCount = countOccurrences(trimmed, "}");

        if ((dollarCount + hashCount) > 0 && (dollarCount + hashCount) != closingCount) {
            result.addError("INVALID_JUEL_EXPRESSION",
                    "Unbalanced JUEL expression in " + entryType + " '" + entryId + "': " + trimmed, entryId);
        }

        // Check for unbalanced parentheses
        int openParens = 0;
        for (char c : trimmed.toCharArray()) {
            if (c == '(') openParens++;
            if (c == ')') openParens--;
            if (openParens < 0) {
                result.addError("INVALID_JUEL_EXPRESSION",
                        "Unbalanced parentheses in " + entryType + " '" + entryId + "': " + trimmed, entryId);
                return;
            }
        }
        if (openParens != 0) {
            result.addError("INVALID_JUEL_EXPRESSION",
                    "Unbalanced parentheses in " + entryType + " '" + entryId + "': " + trimmed, entryId);
        }
    }

    private int countOccurrences(String str, String sub) {
        int count = 0;
        int idx = 0;
        while ((idx = str.indexOf(sub, idx)) != -1) {
            count++;
            idx += sub.length();
        }
        return count;
    }
}
