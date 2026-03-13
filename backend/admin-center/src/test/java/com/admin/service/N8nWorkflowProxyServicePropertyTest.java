package com.admin.service;

import com.admin.dto.response.N8nWorkflowDTO;
import com.admin.service.impl.N8nWorkflowProxyServiceImpl;
import net.jqwik.api.*;

import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * N8nWorkflowProxyService 属性测试
 * Feature: n8n-workflow-integration, Property 3: N8N 工作流列表仅返回活跃工作流
 * 验证需求: 2.3
 *
 * Validates: Requirements 2.3
 */
class N8nWorkflowProxyServicePropertyTest {

    @Provide
    Arbitrary<List<N8nWorkflowDTO>> workflowLists() {
        Arbitrary<N8nWorkflowDTO> workflow = Combinators.combine(
                Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(10),
                Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(30),
                Arbitraries.of(true, false),
                Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(10).list().ofMaxSize(3),
                Arbitraries.strings().ofMinLength(10).ofMaxLength(30)
        ).as((id, name, active, tags, createdAt) ->
                N8nWorkflowDTO.builder()
                        .id(id)
                        .name(name)
                        .active(active)
                        .tags(tags)
                        .createdAt(createdAt)
                        .build()
        );

        return workflow.list().ofMinSize(0).ofMaxSize(50);
    }

    /**
     * Feature: n8n-workflow-integration, Property 3: N8N 工作流列表仅返回活跃工作流
     *
     * 对于任意从 N8N API 返回的工作流列表（包含 active=true 和 active=false 的混合列表），
     * 经过过滤后，返回的所有工作流的 active 字段应均为 true。
     *
     * Validates: Requirements 2.3
     */
    @Property(tries = 100)
    void filteredWorkflowsShouldOnlyContainActiveOnes(
            @ForAll("workflowLists") List<N8nWorkflowDTO> allWorkflows) {

        // Apply the filtering logic
        List<N8nWorkflowDTO> filtered = N8nWorkflowProxyServiceImpl.filterActiveWorkflows(allWorkflows);

        // All returned workflows must have active=true
        assertThat(filtered).allMatch(wf -> Boolean.TRUE.equals(wf.getActive()),
                "All filtered workflows should be active");

        // The count of filtered workflows should equal the count of active workflows in the original list
        long expectedActiveCount = allWorkflows.stream()
                .filter(wf -> Boolean.TRUE.equals(wf.getActive()))
                .count();
        assertThat(filtered).hasSize((int) expectedActiveCount);

        // All active workflows from the original list should be present in the filtered result
        List<String> expectedIds = allWorkflows.stream()
                .filter(wf -> Boolean.TRUE.equals(wf.getActive()))
                .map(N8nWorkflowDTO::getId)
                .collect(Collectors.toList());
        List<String> actualIds = filtered.stream()
                .map(N8nWorkflowDTO::getId)
                .collect(Collectors.toList());
        assertThat(actualIds).containsExactlyElementsOf(expectedIds);
    }
}
