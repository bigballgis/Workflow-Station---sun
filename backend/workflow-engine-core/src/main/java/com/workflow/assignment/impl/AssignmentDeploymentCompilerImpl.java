package com.workflow.assignment.impl;

import com.workflow.assignment.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.common.assignment.AssignmentExclusionBpmnValidator;
import com.platform.common.assignment.AssignmentIdentitySnapshot;
import com.workflow.util.BpmnExtensionUtils;
import org.flowable.bpmn.converter.BpmnXMLConverter;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.CallActivity;
import org.flowable.bpmn.model.ExtensionAttribute;
import org.flowable.bpmn.model.ExtensionElement;
import org.flowable.engine.RepositoryService;
import org.springframework.stereotype.Component;

import javax.xml.stream.XMLInputFactory;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/** Server-only compiled contract, including immutable account binding and frozen called definitions. */
@Component
public class AssignmentDeploymentCompilerImpl implements AssignmentDeploymentCompiler {
    private final RepositoryService definitions;
    private final AssignmentIdentityClient identities;
    private final ObjectMapper json;
    private final com.workflow.client.AdminCenterClient admin;

    public AssignmentDeploymentCompilerImpl(RepositoryService definitions, AssignmentIdentityClient identities, ObjectMapper json,
            com.workflow.client.AdminCenterClient admin) {
        this.definitions = definitions;
        this.identities = identities;
        this.json = json;
        this.admin = admin;
    }

    public Compilation compileDeployment(String portableXml) {
        var policies = AssignmentExclusionBpmnValidator.policies(portableXml);
        BpmnModel model = read(portableXml);
        var process = model.getMainProcess();
        if (process == null) throw new IllegalArgumentException("Assignment policy has no process");
        for (var portableProcess : model.getProcesses()) {
            for (String reserved : java.util.List.of(CONTRACT, TRACKING, USERS, DEPENDENCIES)) {
                if (BpmnExtensionUtils.getExtensionProperty(portableProcess, reserved) != null) {
                    throw new IllegalArgumentException("Portable BPMN cannot provide compiled assignment metadata");
                }
            }
        }
        CallGraph graph = calls(process);
        boolean tracking = graph.tracked() || policies.values().stream().anyMatch(policy -> policy.enabled());
        if (!tracking) return new Compilation(portableXml,false); // An explicitly unprotected graph retains all original deployment semantics.
        if (model.getProcesses().size()!=1) throw new IllegalArgumentException("Protected assignment deployment requires one process per BPMN resource");
        if (graph.unfrozen()) throw new IllegalArgumentException("Protected assignment call graphs require frozen definition IDs");
        graph.activities().forEach(AssignmentMiPublication::validate);
        graph.tasks().forEach(AssignmentNativeSource::dynamic);
        var references = new java.util.LinkedHashSet<AssignmentIdentitySnapshot.Reference>();
        if (tracking) {
            graph.calls().forEach(AssignmentCallOutputMapping::validate);
            for (var policy : policies.values()) {
                var backup = policy.backup();
                if (backup.enabled() && backup.mode() == com.platform.common.assignment.AssignmentExclusionPolicy.Mode.BU_ROLE
                        && backup.buSource() == com.platform.common.assignment.AssignmentExclusionPolicy.BuSource.FIXED_BU) {
                    for (String role : backup.roleCodes()) if (!admin.isEligibleRole(backup.businessUnitCode(), role)) {
                        throw new IllegalArgumentException("Backup role is not admitted to its fixed business unit");
                    }
                }
            }
            references.addAll(graph.identities());
            policies.values().stream().filter(policy -> policy.backup().enabled())
                    .flatMap(policy -> policy.backup().userRefs().stream()).distinct()
                    .map(value -> new AssignmentIdentitySnapshot.Reference(value, AssignmentIdentitySnapshot.Kind.USERNAME))
                    .forEach(references::add);
            for (var task : process.findFlowElementsOfType(org.flowable.bpmn.model.UserTask.class, true)) {
                AssignmentNativeSource.dynamic(task);
                literals(task).stream().map(value -> new AssignmentIdentitySnapshot.Reference(value, AssignmentIdentitySnapshot.Kind.ID_OR_USERNAME))
                        .forEach(references::add);
                var policy = policies.get(task.getId());
                if (policy != null && policy.enabled() && !task.getCandidateGroups().isEmpty()
                        && BpmnExtensionUtils.getExtensionProperty(task, "assigneeType") == null) {
                    throw new IllegalArgumentException("Protected native group tasks require an explicit platform assignment source");
                }
            }
        }
        Map<String,String> boundUsers = bind(references.stream().toList());
        try {
            property(process, CONTRACT, "1");
            property(process, TRACKING, Boolean.toString(tracking));
            property(process, USERS, json.writeValueAsString(boundUsers));
            property(process, DEPENDENCIES, json.writeValueAsString(graph.definitions()));
            return new Compilation(new String(new BpmnXMLConverter().convertToXML(model), StandardCharsets.UTF_8),tracking);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalArgumentException("Cannot bind assignment accounts", e);
        }
    }

    private CallGraph calls(org.flowable.bpmn.model.Process root) {
        var queue = new java.util.ArrayDeque<org.flowable.bpmn.model.Process>();
        var visited = new java.util.HashSet<String>();
        var references = new java.util.LinkedHashSet<AssignmentIdentitySnapshot.Reference>();
        var calls = new java.util.ArrayList<CallActivity>();
        var tasks = new java.util.ArrayList<org.flowable.bpmn.model.UserTask>();
        var activities=new java.util.ArrayList<>(root.findFlowElementsOfType(org.flowable.bpmn.model.Activity.class,true));
        boolean tracked = false;
        boolean unfrozen = false;
        queue.add(root);
        while (!queue.isEmpty()) {
            var process = queue.remove();
            for (CallActivity call : process.findFlowElementsOfType(CallActivity.class, true)) {
                calls.add(call);
                var target=target(call); unfrozen|=!target.frozen();
                for (String definition:target.definitions()) {
                    if (!visited.add(definition)) continue;
                    var called = definitions.getBpmnModel(definition);
                    var metadata = AssignmentCompiledMetadata.read(called);
                    activities.addAll(called.getMainProcess().findFlowElementsOfType(org.flowable.bpmn.model.Activity.class,true));
                    tracked |= metadata.tracked();
                    for (var task : called.getMainProcess().findFlowElementsOfType(org.flowable.bpmn.model.UserTask.class, true)) {
                        tasks.add(task);
                        for (String literal : literals(task)) {
                            String frozen = metadata.users().get(literal);
                            references.add(new AssignmentIdentitySnapshot.Reference(frozen == null ? literal : frozen, AssignmentIdentitySnapshot.Kind.ID));
                        }
                    }
                    queue.add(called.getMainProcess());
                }
            }
        }
        return new CallGraph(tracked, references.stream().toList(),java.util.List.copyOf(calls),unfrozen,java.util.List.copyOf(tasks),java.util.List.copyOf(visited),java.util.List.copyOf(activities));
    }

    private CallTarget target(CallActivity call) {
        String value=call.getCalledElement();
        if (value==null || value.isBlank() || value.contains("${") || value.contains("#{")) {
            return new CallTarget(false,java.util.List.of()); // Legacy dynamic targets remain dynamic; protected callers reject this shape.
        }
        if ("id".equals(call.getCalledElementType())) return new CallTarget(true,java.util.List.of(value));
        var current=definitions.createProcessDefinitionQuery().processDefinitionKey(value).latestVersion().list();
        if (current.isEmpty()) throw new IllegalArgumentException("Assignment call target is unavailable");
        return new CallTarget(false,current.stream().map(org.flowable.engine.repository.ProcessDefinition::getId).toList());
    }

    private Map<String,String> bind(java.util.List<AssignmentIdentitySnapshot.Reference> references) {
        Map<String,String> users = new LinkedHashMap<>();
        for (int offset=0; offset<references.size(); offset+=1000) {
            var snapshot = identities.resolve(references.subList(offset, Math.min(offset+1000,references.size())));
            if (!snapshot.missingReferences().isEmpty()) throw new IllegalArgumentException("Static called users require frozen canonical IDs or deployment bindings");
            snapshot.users().forEach((reference,user) -> {
                if (!user.active()) throw new IllegalArgumentException("Configured assignment account is inactive");
                String previous=users.putIfAbsent(reference,user.id());
                if (previous!=null && !previous.equals(user.id())) throw new IllegalArgumentException("Conflicting assignment user bindings");
            });
        }
        return Map.copyOf(users);
    }

    private static java.util.List<String> literals(org.flowable.bpmn.model.UserTask task) {
        var users = new java.util.ArrayList<>(task.getCandidateUsers());
        if (task.getAssignee()!=null && !task.getAssignee().isBlank()) users.add(task.getAssignee());
        return users.stream().filter(value -> !value.contains("${") && !value.contains("#{")).toList();
    }

    private record CallGraph(boolean tracked, java.util.List<AssignmentIdentitySnapshot.Reference> identities,java.util.List<CallActivity> calls,
            boolean unfrozen,java.util.List<org.flowable.bpmn.model.UserTask> tasks,java.util.List<String> definitions,
            java.util.List<org.flowable.bpmn.model.Activity> activities) { }
    private record CallTarget(boolean frozen,java.util.List<String> definitions) { }

    private static BpmnModel read(String xml) {
        try {
            XMLInputFactory factory = XMLInputFactory.newFactory();
            factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
            factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
            var reader = factory.createXMLStreamReader(new StringReader(xml));
            try {
                return new BpmnXMLConverter().convertToBpmnModel(reader);
            } finally {
                reader.close();
            }
        } catch (javax.xml.stream.XMLStreamException e) {
            throw new IllegalArgumentException("Cannot compile assignment BPMN", e);
        }
    }

    private static void property(org.flowable.bpmn.model.Process process, String name, String value) {
        ExtensionElement properties = new ExtensionElement();
        properties.setName("properties");
        properties.setNamespace("http://workflow.platform/schema/assignment-runtime");
        properties.setNamespacePrefix("assignmentRuntime");
        ExtensionElement property = new ExtensionElement();
        property.setName("property");
        property.setNamespace("http://workflow.platform/schema/assignment-runtime");
        property.setNamespacePrefix("assignmentRuntime");
        for (var entry : Map.of("name", name, "value", value).entrySet()) {
            ExtensionAttribute attribute = new ExtensionAttribute();
            attribute.setName(entry.getKey()); attribute.setValue(entry.getValue());
            property.addAttribute(attribute);
        }
        properties.addChildElement(property);
        process.addExtensionElement(properties);
    }
}
