package com.workflow.util;

import org.flowable.bpmn.model.ExtensionElement;
import org.flowable.bpmn.model.FlowElement;

import java.util.List;
import java.util.Map;

/**
 * Reads custom BPMN extension properties from Flowable model elements.
 */
public final class BpmnExtensionUtils {

    private BpmnExtensionUtils() {
    }

    public static String getExtensionProperty(FlowElement flowElement, String propertyName) {
        if (flowElement.getExtensionElements() == null) {
            return null;
        }

        List<ExtensionElement> propertiesElements = flowElement.getExtensionElements().get("properties");
        if (propertiesElements == null || propertiesElements.isEmpty()) {
            return null;
        }

        for (ExtensionElement propertiesElement : propertiesElements) {
            List<ExtensionElement> propertyElements = propertiesElement.getChildElements().get("property");
            if (propertyElements != null) {
                for (ExtensionElement propertyElement : propertyElements) {
                    String name = propertyElement.getAttributeValue(null, "name");
                    if (propertyName.equals(name)) {
                        return propertyElement.getAttributeValue(null, "value");
                    }
                }
            }

            List<ExtensionElement> valuesElements = propertiesElement.getChildElements().get("values");
            if (valuesElements != null) {
                for (ExtensionElement valueElement : valuesElements) {
                    String name = valueElement.getAttributeValue(null, "name");
                    if (propertyName.equals(name)) {
                        return valueElement.getAttributeValue(null, "value");
                    }
                }
            }
        }
        return null;
    }

    public static String resolveExpression(String template, Map<String, Object> variables) {
        if (template == null) {
            return null;
        }
        String result = template;
        if (variables != null) {
            for (Map.Entry<String, Object> entry : variables.entrySet()) {
                String placeholder = "${" + entry.getKey() + "}";
                if (result.contains(placeholder) && entry.getValue() != null) {
                    result = result.replace(placeholder, entry.getValue().toString());
                }
            }
        }
        if (result.startsWith("${") && result.endsWith("}") && variables != null) {
            String key = result.substring(2, result.length() - 1).trim();
            Object value = variables.get(key);
            return value != null ? value.toString() : result;
        }
        return result;
    }
}
