package com.workflow.component;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Label;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.repository.ProcessDefinitionQuery;
import org.flowable.task.api.Task;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Invariants for node actionIds + process globalActionIds merge.
 */
class BpmnActionParserMergePropertyTest {

    @Property(tries = 100)
    @Label("mergeActionIds keeps node order then new Global ids, first-seen wins")
    void mergeIsNodeFirstUnique(
            @ForAll("actionIdLists") List<String> nodeIds,
            @ForAll("actionIdLists") List<String> globalIds) {
        List<String> merged = newParser(null).mergeActionIds(nodeIds, globalIds);
        List<String> expected = uniqueNonBlank(nodeIds, globalIds);
        if (expected.isEmpty()) {
            assertThat(merged).isNull();
        } else {
            assertThat(merged).containsExactlyElementsOf(expected);
        }
    }

    @Property(tries = 80)
    @Label("XML extractActionIds equals merge of node JSON and global JSON")
    void xmlExtractMergesNodeThenGlobal(
            @ForAll("actionIdLists") List<String> nodeIds,
            @ForAll("actionIdLists") List<String> globalIds) {
        String pdId = "pd:" + UUID.randomUUID();
        RepositoryService repositoryService = mockXml(pdId, bpmnXml(jsonArray(nodeIds), jsonArray(globalIds)));
        BpmnActionParser parser = newParser(repositoryService);

        Task task = mock(Task.class);
        when(task.getProcessDefinitionId()).thenReturn(pdId);
        when(task.getTaskDefinitionKey()).thenReturn("ut1");
        when(repositoryService.getBpmnModel(pdId)).thenReturn(null);

        List<String> extracted = parser.extractActionIds(task);
        List<String> expected = uniqueNonBlank(nodeIds, globalIds);
        if (expected.isEmpty()) {
            assertThat(extracted).isNull();
        } else {
            assertThat(extracted).containsExactlyElementsOf(expected);
        }
    }

    @Property(tries = 80)
    @Label("parseActionIds round-trips a JSON array of numeric ids")
    void parseActionIdsRoundTripsNumericJson(@ForAll("actionIdLists") List<String> ids) {
        List<String> parsed = newParser(null).parseActionIds(jsonArray(ids));
        if (ids.isEmpty()) {
            assertThat(parsed).isNull();
        } else {
            assertThat(parsed).containsExactlyElementsOf(ids);
        }
    }

    @Provide
    Arbitrary<List<String>> actionIdLists() {
        return Arbitraries.integers().between(1, 200).map(String::valueOf)
                .list().ofMinSize(0).ofMaxSize(6);
    }

    private static BpmnActionParser newParser(RepositoryService repositoryService) {
        BpmnActionParser parser = new BpmnActionParser();
        if (repositoryService != null) {
            ReflectionTestUtils.setField(parser, "repositoryService", repositoryService);
        }
        return parser;
    }

    private static List<String> uniqueNonBlank(List<String> nodeIds, List<String> globalIds) {
        LinkedHashSet<String> merged = new LinkedHashSet<>();
        appendNonBlank(merged, nodeIds);
        appendNonBlank(merged, globalIds);
        return new ArrayList<>(merged);
    }

    private static void appendNonBlank(LinkedHashSet<String> target, List<String> ids) {
        if (ids == null) {
            return;
        }
        for (String id : ids) {
            if (id != null && !id.isBlank()) {
                target.add(id);
            }
        }
    }

    private static String jsonArray(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return null;
        }
        return ids.stream().collect(Collectors.joining(",", "[", "]"));
    }

    private static RepositoryService mockXml(String processDefinitionId, String xml) {
        RepositoryService repositoryService = mock(RepositoryService.class);
        ProcessDefinition pd = mock(ProcessDefinition.class);
        when(pd.getId()).thenReturn(processDefinitionId);
        when(pd.getResourceName()).thenReturn("p.bpmn20.xml");
        when(pd.getDeploymentId()).thenReturn("d-" + processDefinitionId);
        ProcessDefinitionQuery query = mock(ProcessDefinitionQuery.class);
        when(repositoryService.createProcessDefinitionQuery()).thenReturn(query);
        when(query.processDefinitionId(processDefinitionId)).thenReturn(query);
        when(query.singleResult()).thenReturn(pd);
        when(repositoryService.getResourceAsStream("d-" + processDefinitionId, "p.bpmn20.xml"))
                .thenReturn(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        return repositoryService;
    }

    private static String bpmnXml(String nodeActionIds, String globalActionIds) {
        String globalBlock = globalActionIds == null ? "" : """
                          <extensionElements>
                            <custom:properties>
                              <custom:property name="globalActionIds" value="%s"/>
                            </custom:properties>
                          </extensionElements>
                """.formatted(globalActionIds);
        String nodeBlock = nodeActionIds == null ? "" : """
                          <extensionElements>
                            <custom:properties>
                              <custom:property name="actionIds" value="%s"/>
                            </custom:properties>
                          </extensionElements>
                """.formatted(nodeActionIds);
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                             xmlns:custom="http://workflow.platform/schema/custom"
                             targetNamespace="http://example.org">
                  <process id="p1" isExecutable="true">
                %s                    <userTask id="ut1" name="A">
                %s                    </userTask>
                  </process>
                </definitions>
                """.formatted(globalBlock, nodeBlock);
    }
}
