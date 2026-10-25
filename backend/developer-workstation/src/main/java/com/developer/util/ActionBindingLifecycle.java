package com.developer.util;

import com.platform.common.bpmn.BpmnActionBindings;
import com.platform.common.bpmn.BpmnActionBindings.Property;
import org.w3c.dom.Element;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Preserve valid Action identity; recover genuinely missing legacy IDs by their paired names. */
public final class ActionBindingLifecycle {
    private ActionBindingLifecycle() {}

    public static boolean references(String xml, String id) {
        for (Element scope : BpmnActionBindings.scopes(BpmnActionBindings.parse(xml))) {
            var properties = BpmnActionBindings.properties(scope);
            Property property = properties.get(BpmnActionBindings.idsKey(scope));
            if (property != null && BpmnActionBindings.ids(property.value()).contains(id)) return true;
        }
        return false;
    }

    public static String reconcile(String stored, Map<String, String> namesById, boolean recoverMissingIds) {
        if (stored == null || stored.isBlank() || namesById.isEmpty()) return stored;
        var document = BpmnActionBindings.parse(stored);
        Map<String, String> idsByName = new LinkedHashMap<>();
        namesById.forEach((id, name) -> idsByName.put(name, id));
        boolean changed = false;
        for (Element scope : BpmnActionBindings.scopes(document)) {
            var properties = BpmnActionBindings.properties(scope);
            changed |= reconcilePair(properties, BpmnActionBindings.idsKey(scope), namesById, idsByName, recoverMissingIds);
        }
        if (!changed) return stored;
        String xml = BpmnActionBindings.serialize(document);
        return stored.trim().startsWith("<") ? xml : XmlEncodingUtil.encode(xml);
    }

    private static boolean reconcilePair(Map<String, Property> properties, String key,
                                          Map<String, String> namesById, Map<String, String> idsByName,
                                          boolean recoverMissingIds) {
        Property idsProperty = properties.get(key);
        if (idsProperty == null) return false;
        String nameKey = key.equals("actionIds") ? "actionNames" : "globalActionNames";
        Property namesProperty = properties.get(nameKey);
        List<String> ids = BpmnActionBindings.ids(idsProperty.value());
        List<String> oldNames = namesProperty == null ? List.of() : BpmnActionBindings.names(namesProperty.value());
        List<String> newIds = new ArrayList<>(ids);
        List<String> newNames = new ArrayList<>();
        for (int i = 0; i < ids.size(); i++) {
            String id = ids.get(i);
            if (!namesById.containsKey(id) && recoverMissingIds && oldNames.size() == ids.size()) {
                String recovered = idsByName.get(oldNames.get(i));
                if (recovered != null) newIds.set(i, recovered);
            }
            String name = namesById.get(newIds.get(i));
            if (name == null && oldNames.size() != ids.size()) return false;
            newNames.add(name != null ? name : oldNames.get(i));
        }
        boolean changed = !newIds.equals(ids);
        if (changed) idsProperty.setValue(BpmnActionBindings.json(newIds.stream().map(ActionBindingLifecycle::storedId).toList()));
        if (!newNames.equals(oldNames)) {
            if (namesProperty == null) namesProperty = createNamesProperty(idsProperty, nameKey);
            namesProperty.setValue(BpmnActionBindings.json(newNames));
            changed = true;
        }
        return changed;
    }

    private static Object storedId(String id) {
        try { return Long.valueOf(id); }
        catch (NumberFormatException ex) { return id; } // Legacy nonnumeric IDs keep their identity.
    }

    private static Property createNamesProperty(Property idsProperty, String key) {
        Element ids = idsProperty.element();
        Element element = ids.getOwnerDocument().createElementNS(ids.getNamespaceURI(),
                (ids.getPrefix() == null ? "" : ids.getPrefix() + ":") + ids.getLocalName());
        element.setAttribute("name", key);
        element.setAttribute("value", "[]");
        ids.getParentNode().appendChild(element);
        return new Property(element, idsProperty.priority());
    }
}
