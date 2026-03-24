package com.developer.validation;

import com.developer.dto.DecisionTableModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

/**
 * DMN XML 解析器
 * 负责 DMN XML 与结构化模型之间的双向转换
 */
@Component
@Slf4j
public class DmnXmlParser {

    /**
     * 将 DMN XML 解析为结构化模型
     */
    public DecisionTableModel parseToModel(String dmnXml) {
        try {
            Document document = parseSecurely(dmnXml);
            return extractModel(document);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse DMN XML: " + e.getMessage(), e);
        }
    }

    /**
     * 将结构化模型转换回 DMN XML
     */
    public String toXml(DecisionTableModel model) {
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<definitions xmlns=\"https://www.omg.org/spec/DMN/20191111/MODEL/\"");
        xml.append(" id=\"definitions\" name=\"definitions\"");
        xml.append(" namespace=\"http://camunda.org/schema/1.0/dmn\">\n");

        String key = escapeXml(model.getDecisionKey() != null ? model.getDecisionKey() : "decision");
        String name = escapeXml(model.getDecisionName() != null ? model.getDecisionName() : "");

        xml.append("  <decision id=\"").append(key).append("\" name=\"").append(name).append("\">\n");

        String hitPolicy = model.getHitPolicy() != null ? model.getHitPolicy() : "FIRST";
        xml.append("    <decisionTable id=\"decisionTable_1\" hitPolicy=\"").append(escapeXml(hitPolicy)).append("\">\n");

        // Inputs
        if (model.getInputs() != null) {
            for (int i = 0; i < model.getInputs().size(); i++) {
                DecisionTableModel.InputColumn input = model.getInputs().get(i);
                xml.append("      <input id=\"input_").append(i + 1).append("\"");
                if (input.getLabel() != null) {
                    xml.append(" label=\"").append(escapeXml(input.getLabel())).append("\"");
                }
                xml.append(">\n");
                xml.append("        <inputExpression id=\"inputExpression_").append(i + 1).append("\"");
                if (input.getTypeRef() != null) {
                    xml.append(" typeRef=\"").append(escapeXml(input.getTypeRef())).append("\"");
                }
                xml.append(">\n");
                xml.append("          <text>");
                xml.append(escapeXml(input.getInputExpression() != null ? input.getInputExpression() : ""));
                xml.append("</text>\n");
                xml.append("        </inputExpression>\n");
                xml.append("      </input>\n");
            }
        }

        // Outputs
        if (model.getOutputs() != null) {
            for (int i = 0; i < model.getOutputs().size(); i++) {
                DecisionTableModel.OutputColumn output = model.getOutputs().get(i);
                xml.append("      <output id=\"output_").append(i + 1).append("\"");
                if (output.getLabel() != null) {
                    xml.append(" label=\"").append(escapeXml(output.getLabel())).append("\"");
                }
                if (output.getName() != null) {
                    xml.append(" name=\"").append(escapeXml(output.getName())).append("\"");
                }
                if (output.getTypeRef() != null) {
                    xml.append(" typeRef=\"").append(escapeXml(output.getTypeRef())).append("\"");
                }
                xml.append(" />\n");
            }
        }

        // Rules
        if (model.getRules() != null) {
            for (int i = 0; i < model.getRules().size(); i++) {
                DecisionTableModel.Rule rule = model.getRules().get(i);
                xml.append("      <rule id=\"rule_").append(i + 1).append("\">\n");

                if (rule.getInputEntries() != null) {
                    for (int j = 0; j < rule.getInputEntries().size(); j++) {
                        xml.append("        <inputEntry id=\"inputEntry_").append(i + 1).append("_").append(j + 1).append("\">\n");
                        xml.append("          <text>").append(escapeXml(rule.getInputEntries().get(j))).append("</text>\n");
                        xml.append("        </inputEntry>\n");
                    }
                }

                if (rule.getOutputEntries() != null) {
                    for (int j = 0; j < rule.getOutputEntries().size(); j++) {
                        xml.append("        <outputEntry id=\"outputEntry_").append(i + 1).append("_").append(j + 1).append("\">\n");
                        xml.append("          <text>").append(escapeXml(rule.getOutputEntries().get(j))).append("</text>\n");
                        xml.append("        </outputEntry>\n");
                    }
                }

                xml.append("      </rule>\n");
            }
        }

        xml.append("    </decisionTable>\n");
        xml.append("  </decision>\n");
        xml.append("</definitions>\n");

        return xml.toString();
    }

    /**
     * 从 DMN XML 中提取 hit policy
     */
    public String extractHitPolicy(String dmnXml) {
        try {
            Document document = parseSecurely(dmnXml);
            NodeList tables = document.getElementsByTagNameNS("*", "decisionTable");
            if (tables.getLength() > 0) {
                Element table = (Element) tables.item(0);
                String hp = table.getAttribute("hitPolicy");
                return (hp != null && !hp.isEmpty()) ? hp : "UNIQUE";
            }
        } catch (Exception e) {
            log.warn("Failed to extract hit policy from DMN XML: {}", e.getMessage());
        }
        return "UNIQUE";
    }

    /**
     * 从 DMN XML 中提取 decision key
     */
    public String extractDecisionKey(String dmnXml) {
        try {
            Document document = parseSecurely(dmnXml);
            NodeList decisions = document.getElementsByTagNameNS("*", "decision");
            if (decisions.getLength() > 0) {
                Element decision = (Element) decisions.item(0);
                return decision.getAttribute("id");
            }
        } catch (Exception e) {
            log.warn("Failed to extract decision key from DMN XML: {}", e.getMessage());
        }
        return null;
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

    private DecisionTableModel extractModel(Document document) {
        DecisionTableModel.DecisionTableModelBuilder modelBuilder = DecisionTableModel.builder();

        // Extract decision key and name
        NodeList decisions = document.getElementsByTagNameNS("*", "decision");
        if (decisions.getLength() > 0) {
            Element decision = (Element) decisions.item(0);
            modelBuilder.decisionKey(decision.getAttribute("id"));
            modelBuilder.decisionName(decision.getAttribute("name"));
        }

        // Extract decision table
        NodeList tables = document.getElementsByTagNameNS("*", "decisionTable");
        if (tables.getLength() > 0) {
            Element table = (Element) tables.item(0);
            String hp = table.getAttribute("hitPolicy");
            modelBuilder.hitPolicy((hp != null && !hp.isEmpty()) ? hp : "UNIQUE");

            modelBuilder.inputs(extractInputColumns(table));
            modelBuilder.outputs(extractOutputColumns(table));
            modelBuilder.rules(extractRules(table));
        }

        return modelBuilder.build();
    }

    private List<DecisionTableModel.InputColumn> extractInputColumns(Element table) {
        List<DecisionTableModel.InputColumn> inputs = new ArrayList<>();
        NodeList inputNodes = table.getElementsByTagNameNS("*", "input");
        for (int i = 0; i < inputNodes.getLength(); i++) {
            Element input = (Element) inputNodes.item(i);
            DecisionTableModel.InputColumn.InputColumnBuilder col = DecisionTableModel.InputColumn.builder();
            col.label(input.getAttribute("label"));

            NodeList exprNodes = input.getElementsByTagNameNS("*", "inputExpression");
            if (exprNodes.getLength() > 0) {
                Element expr = (Element) exprNodes.item(0);
                col.typeRef(expr.getAttribute("typeRef"));
                NodeList textNodes = expr.getElementsByTagNameNS("*", "text");
                if (textNodes.getLength() > 0) {
                    col.inputExpression(textNodes.item(0).getTextContent());
                }
            }
            inputs.add(col.build());
        }
        return inputs;
    }

    private List<DecisionTableModel.OutputColumn> extractOutputColumns(Element table) {
        List<DecisionTableModel.OutputColumn> outputs = new ArrayList<>();
        NodeList outputNodes = table.getElementsByTagNameNS("*", "output");
        for (int i = 0; i < outputNodes.getLength(); i++) {
            Element output = (Element) outputNodes.item(i);
            outputs.add(DecisionTableModel.OutputColumn.builder()
                    .label(output.getAttribute("label"))
                    .name(output.getAttribute("name"))
                    .typeRef(output.getAttribute("typeRef"))
                    .build());
        }
        return outputs;
    }

    private List<DecisionTableModel.Rule> extractRules(Element table) {
        List<DecisionTableModel.Rule> rules = new ArrayList<>();
        NodeList ruleNodes = table.getElementsByTagNameNS("*", "rule");
        for (int i = 0; i < ruleNodes.getLength(); i++) {
            Element rule = (Element) ruleNodes.item(i);

            List<String> inputEntries = new ArrayList<>();
            NodeList inputEntryNodes = rule.getElementsByTagNameNS("*", "inputEntry");
            for (int j = 0; j < inputEntryNodes.getLength(); j++) {
                Element entry = (Element) inputEntryNodes.item(j);
                NodeList textNodes = entry.getElementsByTagNameNS("*", "text");
                inputEntries.add(textNodes.getLength() > 0 ? textNodes.item(0).getTextContent() : "");
            }

            List<String> outputEntries = new ArrayList<>();
            NodeList outputEntryNodes = rule.getElementsByTagNameNS("*", "outputEntry");
            for (int j = 0; j < outputEntryNodes.getLength(); j++) {
                Element entry = (Element) outputEntryNodes.item(j);
                NodeList textNodes = entry.getElementsByTagNameNS("*", "text");
                outputEntries.add(textNodes.getLength() > 0 ? textNodes.item(0).getTextContent() : "");
            }

            rules.add(DecisionTableModel.Rule.builder()
                    .inputEntries(inputEntries)
                    .outputEntries(outputEntries)
                    .build());
        }
        return rules;
    }

    private String escapeXml(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
