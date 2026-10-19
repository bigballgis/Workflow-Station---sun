package com.platform.common.recordnote;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Validate portable note references before publishing/deploying any FU package. */
public final class RecordNoteBpmnValidator {
    private RecordNoteBpmnValidator() {}

    public static void validate(Collection<Map<String,Object>> configs,String bpmn) {
        boolean enabled=configs.stream().flatMap(c -> RecordNoteDesign.notes(c).stream())
                .anyMatch(n -> n.get("props") instanceof Map<?,?> p
                        && p.get("attachmentNotification") instanceof Map<?,?> a && Boolean.TRUE.equals(a.get("enabled")));
        if(!enabled) return;
        if(bpmn==null || bpmn.isBlank()) throw new IllegalArgumentException("Record Note targets require a process");
        try {
            Set<String> nodes=targetTables(bpmn).keySet();
            Set<String> controls=new HashSet<>();
            for(var config:configs) for(var note:RecordNoteDesign.notes(config)) {
                @SuppressWarnings("unchecked") var props=(Map<String,Object>)note.get("props");
                RecordNoteDesign.validate(props,nodes);
                if(props.get("attachmentNotification") instanceof Map<?,?> policy && Boolean.TRUE.equals(policy.get("enabled"))) {
                    Object form=props.get("formKey"), control=props.get("controlId");
                    if(!(form instanceof String f) || f.isBlank() || !(control instanceof String c) || c.isBlank()
                            || !controls.add(f+":"+c)) throw new IllegalArgumentException("Missing or duplicate Record Note identity");
                }
            }
        } catch(IllegalArgumentException e) { throw e; }
        catch(Exception e) { throw new IllegalArgumentException("Cannot validate Record Note task references",e); }
    }
    /** Configured table per eligible MI task, taken only from the frozen process. */
    public static Map<String,String> targetTables(String bpmn) {
        if(bpmn==null || bpmn.isBlank()) throw new IllegalArgumentException("Record Note targets require a process");
        Map<String,String> tables=new LinkedHashMap<>();
        targetConfigurations(bpmn).forEach((node,config) -> tables.put(node,config.get("subTableName")));
        return tables;
    }

    /** Explicit assignment properties with nearest-scope precedence for each eligible task. */
    public static Map<String,Map<String,String>> targetConfigurations(String bpmn) {
        try {
            var document=parse(bpmn);
            Map<String,Map<String,String>> result=new LinkedHashMap<>();
            var tasks=document.getElementsByTagNameNS("*","userTask");
            for(int i=0;i<tasks.getLength();i++) {
                var task=(org.w3c.dom.Element)tasks.item(i);
                Map<String,String> values=configuration(task);
                if(values.containsKey("__multiInstance") && values.containsKey("subTableName")
                        && (values.containsKey("assigneeField") || values.containsKey("roleField"))) {
                    result.put(task.getAttribute("id"),Map.copyOf(values));
                }
            }
            return result;
        } catch(IllegalArgumentException e) { throw e; }
        catch(Exception e) { throw new IllegalArgumentException("Cannot read Record Note task references",e); }
    }

    private static org.w3c.dom.Document parse(String bpmn) throws Exception {
        String xml=bpmn.stripLeading().startsWith("<")?bpmn:
                new String(Base64.getDecoder().decode(bpmn),StandardCharsets.UTF_8);
        DocumentBuilderFactory factory=DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities",false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities",false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD,"");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA,"");
        factory.setXIncludeAware(false); factory.setExpandEntityReferences(false);
        return factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }

    private static Map<String,String> configuration(org.w3c.dom.Element task) {
        Map<String,String> values=new HashMap<>();
        org.w3c.dom.Node node=task;
        while(node instanceof org.w3c.dom.Element element) {
            var children=element.getChildNodes();
            for(int i=0;i<children.getLength();i++) if(children.item(i) instanceof org.w3c.dom.Element child) {
                if("multiInstanceLoopCharacteristics".equals(child.getLocalName())) values.put("__multiInstance","true");
                if("extensionElements".equals(child.getLocalName())) {
                    var properties=child.getElementsByTagName("*");
                    for(int j=0;j<properties.getLength();j++) {
                        var property=(org.w3c.dom.Element)properties.item(j);
                        if(property.hasAttribute("name") && property.hasAttribute("value")
                                && !property.getAttribute("value").isBlank()) {
                            values.putIfAbsent(property.getAttribute("name"),property.getAttribute("value"));
                        }
                    }
                }
            }
            node=node.getParentNode();
        }
        return values;
    }
}
