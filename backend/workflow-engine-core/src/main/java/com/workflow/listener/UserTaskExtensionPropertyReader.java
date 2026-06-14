package com.workflow.listener;

import org.flowable.bpmn.model.ExtensionElement;
import org.flowable.bpmn.model.UserTask;

import java.util.List;

/**
 * Reads custom designer extension properties (e.g. {@code assigneeType}, {@code subTableName}) from a
 * Flowable {@link UserTask}'s in-memory BPMN model. Handles both the {@code properties}/{@code property}
 * container form and arbitrarily nested {@code property} elements, with namespace-prefixed names.
 * Extracted verbatim from {@link TaskAssignmentListener}; behavior is unchanged.
 */
final class UserTaskExtensionPropertyReader {

    private UserTaskExtensionPropertyReader() {
    }

    static String getExtensionProperty(UserTask userTask, String propertyName) {
        if (userTask.getExtensionElements() == null || userTask.getExtensionElements().isEmpty()) {
            return null;
        }
        for (List<ExtensionElement> group : userTask.getExtensionElements().values()) {
            if (group == null) {
                continue;
            }
            for (ExtensionElement container : group) {
                if (container == null || container.getName() == null) {
                    continue;
                }
                // Flowable parses designer-exported custom:properties with getName() returning "properties"
                // or namespaced like "custom:properties"; equals "properties" alone would miss assigneeType
                if (!isExtensionPropertiesContainer(container.getName())) {
                    continue;
                }
                String v = findPropertyInPropertiesContainer(container, propertyName);
                if (v != null) {
                    return v;
                }
            }
        }
        // Fallback: match property element at any nesting level (compatible with non-standard nesting)
        for (List<ExtensionElement> group : userTask.getExtensionElements().values()) {
            if (group == null) {
                continue;
            }
            for (ExtensionElement root : group) {
                String v = findExtensionPropertyRecursive(root, propertyName);
                if (v != null) {
                    return v;
                }
            }
        }
        return null;
    }

    private static boolean isExtensionPropertiesContainer(String elementName) {
        if (elementName == null || elementName.isBlank()) {
            return false;
        }
        String n = elementName.trim();
        if ("properties".equalsIgnoreCase(n)) {
            return true;
        }
        int colon = n.lastIndexOf(':');
        if (colon >= 0 && colon < n.length() - 1) {
            return "properties".equalsIgnoreCase(n.substring(colon + 1));
        }
        return false;
    }

    private static boolean isExtensionPropertyElementName(String elementName) {
        if (elementName == null || elementName.isBlank()) {
            return false;
        }
        String n = elementName.trim();
        if ("property".equalsIgnoreCase(n)) {
            return true;
        }
        int colon = n.lastIndexOf(':');
        if (colon >= 0 && colon < n.length() - 1) {
            return "property".equalsIgnoreCase(n.substring(colon + 1));
        }
        return false;
    }

    private static String findExtensionPropertyRecursive(ExtensionElement el, String propertyName) {
        if (el == null) {
            return null;
        }
        if (el.getName() != null && isExtensionPropertyElementName(el.getName())) {
            String name = el.getAttributeValue(null, "name");
            if (propertyName.equals(name)) {
                return el.getAttributeValue(null, "value");
            }
        }
        if (el.getChildElements() == null) {
            return null;
        }
        for (List<ExtensionElement> children : el.getChildElements().values()) {
            if (children == null) {
                continue;
            }
            for (ExtensionElement child : children) {
                String v = findExtensionPropertyRecursive(child, propertyName);
                if (v != null) {
                    return v;
                }
            }
        }
        return null;
    }

    private static String findPropertyInPropertiesContainer(ExtensionElement propertiesElement, String propertyName) {
        if (propertiesElement.getChildElements() == null) {
            return null;
        }
        for (List<ExtensionElement> propertyElements : propertiesElement.getChildElements().values()) {
            if (propertyElements == null) {
                continue;
            }
            for (ExtensionElement propertyElement : propertyElements) {
                if (propertyElement.getName() == null
                        || !isExtensionPropertyElementName(propertyElement.getName())) {
                    continue;
                }
                String name = propertyElement.getAttributeValue(null, "name");
                if (propertyName.equals(name)) {
                    return propertyElement.getAttributeValue(null, "value");
                }
            }
        }
        return null;
    }
}
