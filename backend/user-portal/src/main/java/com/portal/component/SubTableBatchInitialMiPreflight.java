package com.portal.component;

import com.platform.common.util.JsonUtils;
import com.portal.exception.PortalException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.w3c.dom.Element;
import org.w3c.dom.Document;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

/** Prepare frozen assignments reached directly from the process start, before Flowable creates any task. */
final class SubTableBatchInitialMiPreflight {
    private SubTableBatchInitialMiPreflight() { }

    static List<SubTableBatchInitialMiOutcome.Expected> prepare(String xml, String processKey, Map<String, Object> variables,
            Map<String, Object> baseline, JdbcTemplate jdbc) {
        if (!baseline.containsKey(SubTableWriteIsolation.BATCH_RUNTIME_FIELD)) return List.of();
        try {
            var document = BpmnMiXmlSupport.parseBpmnSecurely(xml);
            Element process = BpmnMiXmlSupport.findElementByLocalNameAndId(document, "process", processKey);
            if (process == null) throw new PortalException("409", "Frozen start process is missing");
            Initial initial = initialTargets(process, document);
            if (initial == null) return List.of();
            Map<String, Object> controls = sourceControls(initial.starts(), baseline);
            if (controls.isEmpty()) return List.of(); // Only this source's explicit capability can change the legacy start contract.
            SubTableBatchMiCollection.validateTargets(document, controls);
            Map<String, Object> prepared = new HashMap<>(variables);
            List<SubTableBatchInitialMiOutcome.Expected> expected = new ArrayList<>();
            Map<String, Integer> uses = new HashMap<>();
            for (Element target : initial.targets()) uses.merge(collection(target), 1, Integer::sum);
            for (Element target : initial.targets()) {
                if (SubTableBatchMiCollection.inject(target, prepared, controls, baseline, (people, roles) -> {
                    SubTableBatchUserEligibility.requireActive(people, jdbc);
                    SubTableBatchRoleEligibility.requireEligible(roles, baseline, jdbc);
                })) {
                    if (uses.get(collection(target)) != 1) throw new PortalException("409", "Initial assignment collections overlap");
                    expected.addAll(SubTableBatchInitialMiOutcome.plan(target, controls, (List<?>) prepared.get(collection(target))));
                }
            }
            variables.putAll(prepared); // No collection is published if any direct initial target fails.
            return List.copyOf(expected);
        } catch (PortalException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new PortalException("409", "Initial assignment source cannot be verified", exception);
        }
    }

    private record Initial(List<Element> starts, List<Element> targets) { }
    private static String collection(Element target) {
        return BpmnMiXmlSupport.extractFlowableCollection(BpmnMiXmlSupport.findMultiInstanceLoopInSubProcess(target));
    }
    private static Initial initialTargets(Element process, Document document) {
        List<Element> starts = new ArrayList<>();
        List<Element> targets = new ArrayList<>();
        for (var node = process.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (!(node instanceof Element start) || !"startEvent".equals(start.getLocalName())) continue;
            starts.add(start);
            for (String flowId : BpmnMiXmlSupport.listSequenceFlowIdsWithSourceRef(document, start.getAttribute("id"))) {
                Element flow = BpmnMiXmlSupport.findElementByLocalNameAndId(document, "sequenceFlow", flowId);
                Element target = BpmnMiXmlSupport.findElementByLocalNameAndId(document, "subProcess", flow.getAttribute("targetRef"));
                if (target != null && BpmnMiXmlSupport.findMultiInstanceLoopInSubProcess(target) != null) targets.add(target);
            }
        }
        if (targets.isEmpty()) return null; // This helper does not traverse past human/gateway/service nodes.
        return new Initial(starts, targets);
    }
    private static Map<String, Object> sourceControls(List<Element> starts, Map<String, Object> baseline) {
        for (Element start : starts) {
            String sourceId = BpmnMiXmlSupport.findFirstPropertyValue(start, "formId");
            if (sourceId == null) continue; // A source without a form does not enable this new capability.
            var controls = SubTableBatchRuntimeSnapshot.projectForForm(baseline, sourceId, JsonUtils.getObjectMapper());
            for (Object raw : controls.values()) {
                if (!(raw instanceof Map<?, ?> control) || !(control.get("capabilities") instanceof Map<?, ?> capabilities)) {
                    throw new PortalException("409", "Initial assignment capabilities are invalid");
                }
                if (Boolean.TRUE.equals(capabilities.get("allowBatchAssign"))) {
                    if (starts.size() != 1) throw new PortalException("409", "Initial assignment start event is ambiguous");
                    return controls;
                }
            }
        }
        return Map.of();
    }
}
