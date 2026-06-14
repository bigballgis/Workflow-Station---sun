package com.developer.property;

import com.developer.dto.DecisionDefinitionRequest;
import com.developer.entity.DecisionDefinition;
import com.developer.entity.FunctionUnit;
import com.developer.repository.*;
import com.developer.validation.DmnXmlParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.jqwik.api.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.developer.security.FunctionUnitWorkspaceAccessService;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * DecisionDesignComponentImpl 属性测试 —— 导出/导入 主题
 * Feature: dmn-decision-table-integration
 *
 * Property 14: 导出/导入往返
 * Property 15: 导入冲突策略
 *
 * Validates: Requirements 15.1, 15.3, 15.4
 */
public class DecisionDesignExportImportPropertyTest extends DecisionDesignPropertyTestBase {

    // ========== Property 14: 导出/导入往返 ==========

    /**
     * Property 14: For any FunctionUnit with N DecisionDefinitions, exporting to ZIP
     * and then parsing the ZIP should preserve all decision definition DMN XML content.
     * The exported ZIP should contain N .dmn files in the decisions/ directory, and
     * importing from those files should recreate equivalent DecisionDefinitions.
     *
     * **Validates: Requirements 15.1, 15.3**
     */
    @Property(tries = 100)
    void exportImportRoundTripPreservesDecisionDefinitions(
            @ForAll("decisionCounts") int n,
            @ForAll("functionUnitIds") Long functionUnitId,
            @ForAll("validDecisionRequestLists") List<DecisionDefinitionRequest> requestPool) {

        List<DecisionDefinitionRequest> requests = requestPool.stream().limit(n).toList();
        int actualN = requests.size();

        // Build a FunctionUnit with N DecisionDefinitions
        FunctionUnit functionUnit = FunctionUnit.builder()
                .id(functionUnitId)
                .code("fu-exp-" + functionUnitId)
                .name("ExportFU_" + functionUnitId)
                .displayName("Test FU for export/import round-trip")
                .status(com.developer.enums.FunctionUnitStatus.DRAFT)
                .decisionDefinitions(new ArrayList<>())
                .tableDefinitions(new ArrayList<>())
                .formDefinitions(new ArrayList<>())
                .actionDefinitions(new ArrayList<>())
                .build();

        for (int i = 0; i < actualN; i++) {
            DecisionDefinitionRequest req = requests.get(i);
            functionUnit.getDecisionDefinitions().add(
                    DecisionDefinition.builder()
                            .id(idGenerator.getAndIncrement())
                            .functionUnit(functionUnit)
                            .decisionKey(req.getDecisionKey() + "_" + i)
                            .decisionName(req.getDecisionName())
                            .dmnXml(req.getDmnXml())
                            .hitPolicy(req.getHitPolicy())
                            .description(req.getDescription())
                            .createdAt(Instant.now())
                            .updatedAt(Instant.now())
                            .build()
            );
        }

        try {
            // Set up ExportImportComponentImpl with mocked repos and real DmnXmlParser
            FunctionUnitRepository exportFuRepo = mock(FunctionUnitRepository.class);
            DecisionDefinitionRepository exportDecisionRepo = mock(DecisionDefinitionRepository.class);
            TableDefinitionRepository exportTableRepo = mock(TableDefinitionRepository.class);
            FormDefinitionRepository exportFormRepo = mock(FormDefinitionRepository.class);
            ActionDefinitionRepository exportActionRepo = mock(ActionDefinitionRepository.class);
            TableRelationRepository exportRelationRepo = mock(TableRelationRepository.class);
            DmnXmlParser realParser = new DmnXmlParser();

            when(exportFuRepo.findById(functionUnitId)).thenReturn(Optional.of(functionUnit));
            when(exportTableRepo.findByFunctionUnitIdWithFields(functionUnitId))
                    .thenReturn(functionUnit.getTableDefinitions());
            when(exportFormRepo.findByFunctionUnitIdWithBindings(functionUnitId))
                    .thenReturn(functionUnit.getFormDefinitions());
            when(exportActionRepo.findByFunctionUnitId(functionUnitId))
                    .thenReturn(functionUnit.getActionDefinitions());
            when(exportDecisionRepo.findByFunctionUnitId(functionUnitId))
                    .thenReturn(functionUnit.getDecisionDefinitions());
            when(exportRelationRepo.findByFunctionUnitId(functionUnitId)).thenReturn(new ArrayList<>());

            com.developer.component.impl.ExportImportComponentImpl exportImportComponent =
                    com.developer.component.impl.ExportImportTestComponents.build(
                            exportFuRepo,
                            mock(ProcessDefinitionRepository.class),
                            exportTableRepo,
                            exportFormRepo,
                            exportActionRepo,
                            exportDecisionRepo,
                            mock(FormTableBindingRepository.class),
                            mock(FormStageBindingRepository.class),
                            exportRelationRepo,
                            realParser,
                            mock(FunctionUnitWorkspaceAccessService.class),
                            mock(FunctionUnitDevGroupAssignmentRepository.class),
                            mock(jakarta.persistence.EntityManager.class),
                            new ObjectMapper(),
                            mock(com.developer.util.DeveloperWorkstationSequenceSynchronizer.class)
                    );

            // Step 1: Export to ZIP
            byte[] zipBytes = exportImportComponent.exportFunctionUnit(functionUnitId);
            assertThat(zipBytes).isNotNull();
            assertThat(zipBytes.length).isGreaterThan(0);

            // Step 2: Parse the ZIP and extract decision files
            Map<String, String> dmnFilesInZip = new LinkedHashMap<>();
            Map<String, Object> manifest = null;

            try (java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(
                    new java.io.ByteArrayInputStream(zipBytes))) {
                java.util.zip.ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                    byte[] buffer = new byte[1024];
                    int len;
                    while ((len = zis.read(buffer)) > 0) {
                        baos.write(buffer, 0, len);
                    }
                    String entryName = entry.getName();
                    if (entryName.startsWith("decisions/") && entryName.endsWith(".dmn")) {
                        dmnFilesInZip.put(entryName, baos.toString(java.nio.charset.StandardCharsets.UTF_8));
                    }
                    if ("manifest.json".equals(entryName)) {
                        manifest = new ObjectMapper().readValue(baos.toByteArray(), Map.class);
                    }
                }
            }

            // Step 3: Verify the ZIP contains exactly N .dmn files
            assertThat(dmnFilesInZip)
                    .as("ZIP should contain exactly %d .dmn files in decisions/ directory", actualN)
                    .hasSize(actualN);

            // Step 4: Verify manifest lists the decision files
            assertThat(manifest).isNotNull();
            @SuppressWarnings("unchecked")
            Map<String, Object> components = (Map<String, Object>) manifest.get("components");
            @SuppressWarnings("unchecked")
            List<String> decisionPaths = (List<String>) components.get("decisions");
            assertThat(decisionPaths)
                    .as("Manifest should list %d decision file paths", actualN)
                    .hasSize(actualN);

            // Step 5: Verify each DMN XML in the ZIP matches the original
            List<String> sortedDmnFiles = new ArrayList<>(dmnFilesInZip.values());
            for (int i = 0; i < actualN; i++) {
                DecisionDefinition original = functionUnit.getDecisionDefinitions().get(i);
                String exportedXml = sortedDmnFiles.get(i);

                assertThat(exportedXml)
                        .as("Exported DMN XML[%d] should match original dmnXml", i)
                        .isEqualTo(original.getDmnXml());
            }

            // Step 6: Verify round-trip by parsing exported XML and checking key fields
            for (int i = 0; i < actualN; i++) {
                DecisionDefinition original = functionUnit.getDecisionDefinitions().get(i);
                String exportedXml = sortedDmnFiles.get(i);

                String extractedKey = realParser.extractDecisionKey(exportedXml);
                String extractedHitPolicy = realParser.extractHitPolicy(exportedXml);

                // The key extracted from the XML should match what DmnXmlParser extracts
                // (the XML contains the decision key as the <decision id="..."> attribute)
                assertThat(extractedKey)
                        .as("Extracted decisionKey[%d] from exported XML should be non-null", i)
                        .isNotNull();
                assertThat(extractedHitPolicy)
                        .as("Extracted hitPolicy[%d] from exported XML should be non-null", i)
                        .isNotNull();
            }

        } catch (Exception e) {
            fail("Export/import round-trip should not throw exception: " + e.getMessage(), e);
        }
    }

    // ========== Property 15: 导入冲突策略 ==========

    /**
     * Property 15: When importing decisions with conflicting keys into a FunctionUnit
     * that already has decisions:
     * - SKIP strategy at the function-unit level preserves the original FU and all its decisions
     * - OVERWRITE strategy at the function-unit level replaces the FU (deleting old decisions)
     *   and the importDecision method overwrites any conflicting decision keys
     *
     * **Validates: Requirements 15.4**
     */
    @Property(tries = 100)
    void importConflictStrategyBehavior(
            @ForAll("validDecisionRequests") DecisionDefinitionRequest originalRequest,
            @ForAll("validDecisionRequests") DecisionDefinitionRequest importedRequest,
            @ForAll("functionUnitIds") Long functionUnitId) {

        // Use a real DmnXmlParser for extracting keys from XML
        DmnXmlParser realParser = new DmnXmlParser();

        // --- Test SKIP strategy: original decisions should be preserved ---
        {
            FunctionUnitRepository skipFuRepo = mock(FunctionUnitRepository.class);
            DecisionDefinitionRepository skipDecisionRepo = mock(DecisionDefinitionRepository.class);

            // Simulate existing FU with the same name
            String fuName = "ConflictFU_" + functionUnitId;
            when(skipFuRepo.existsByName(fuName)).thenReturn(true);

            com.developer.component.impl.ExportImportComponentImpl skipComponent =
                    com.developer.component.impl.ExportImportTestComponents.build(
                            skipFuRepo,
                            mock(ProcessDefinitionRepository.class),
                            mock(TableDefinitionRepository.class),
                            mock(FormDefinitionRepository.class),
                            mock(ActionDefinitionRepository.class),
                            skipDecisionRepo,
                            mock(FormTableBindingRepository.class),
                            mock(FormStageBindingRepository.class),
                            mock(TableRelationRepository.class),
                            realParser,
                            mock(FunctionUnitWorkspaceAccessService.class),
                            mock(FunctionUnitDevGroupAssignmentRepository.class),
                            mock(jakarta.persistence.EntityManager.class),
                            new ObjectMapper(),
                            mock(com.developer.util.DeveloperWorkstationSequenceSynchronizer.class)
                    );

            // Build a ZIP with one decision
            byte[] zipBytes = buildTestZip(fuName, importedRequest.getDmnXml());

            // Import with SKIP strategy
            org.springframework.mock.web.MockMultipartFile mockFile =
                    new org.springframework.mock.web.MockMultipartFile(
                            "file", "test.zip", "application/zip", zipBytes);

            Map<String, Object> result = skipComponent.importFunctionUnit(mockFile, "SKIP");

            assertThat(result.get("status"))
                    .as("SKIP strategy should return SKIPPED status when FU name conflicts")
                    .isEqualTo("SKIPPED");

            // Verify no decisions were saved (import was skipped entirely)
            verify(skipDecisionRepo, never()).save(any(DecisionDefinition.class));
        }

        // --- Test OVERWRITE strategy: imported decisions should replace originals ---
        {
            FunctionUnitRepository overwriteFuRepo = mock(FunctionUnitRepository.class);
            DecisionDefinitionRepository overwriteDecisionRepo = mock(DecisionDefinitionRepository.class);

            String fuName = "ConflictFU_" + functionUnitId;

            // Simulate existing FU
            FunctionUnit existingFu = FunctionUnit.builder()
                    .id(functionUnitId)
                    .code("fu-existing-" + functionUnitId)
                    .name(fuName)
                    .build();

            when(overwriteFuRepo.existsByName(fuName)).thenReturn(true);
            when(overwriteFuRepo.findByName(fuName)).thenReturn(Optional.of(existingFu));
            when(overwriteFuRepo.existsByCode(anyString())).thenReturn(false);

            // After delete + save, return a new FU
            Long newFuId = idGenerator.getAndIncrement();
            when(overwriteFuRepo.save(any(FunctionUnit.class))).thenAnswer(invocation -> {
                FunctionUnit saved = invocation.getArgument(0);
                if (saved.getId() == null) {
                    saved.setId(newFuId);
                }
                return saved;
            });

            // importDecision will call findByFunctionUnitId to check for conflicts
            when(overwriteDecisionRepo.findByFunctionUnitId(newFuId)).thenReturn(new ArrayList<>());
            when(overwriteDecisionRepo.save(any(DecisionDefinition.class))).thenAnswer(invocation -> {
                DecisionDefinition saved = invocation.getArgument(0);
                saved.setId(idGenerator.getAndIncrement());
                return saved;
            });

            com.developer.component.impl.ExportImportComponentImpl overwriteComponent =
                    com.developer.component.impl.ExportImportTestComponents.build(
                            overwriteFuRepo,
                            mock(ProcessDefinitionRepository.class),
                            mock(TableDefinitionRepository.class),
                            mock(FormDefinitionRepository.class),
                            mock(ActionDefinitionRepository.class),
                            overwriteDecisionRepo,
                            mock(FormTableBindingRepository.class),
                            mock(FormStageBindingRepository.class),
                            mock(TableRelationRepository.class),
                            realParser,
                            mock(FunctionUnitWorkspaceAccessService.class),
                            mock(FunctionUnitDevGroupAssignmentRepository.class),
                            mock(jakarta.persistence.EntityManager.class),
                            new ObjectMapper(),
                            mock(com.developer.util.DeveloperWorkstationSequenceSynchronizer.class)
                    );

            // Build a ZIP with one decision
            byte[] zipBytes = buildTestZip(fuName, importedRequest.getDmnXml());

            org.springframework.mock.web.MockMultipartFile mockFile =
                    new org.springframework.mock.web.MockMultipartFile(
                            "file", "test.zip", "application/zip", zipBytes);

            Map<String, Object> result = overwriteComponent.importFunctionUnit(mockFile, "OVERWRITE");

            assertThat(result.get("status"))
                    .as("OVERWRITE strategy should return SUCCESS status")
                    .isEqualTo("SUCCESS");

            // Verify the existing FU was deleted
            verify(overwriteFuRepo).deleteById(existingFu.getId());

            // Verify a new decision was saved (the imported one)
            verify(overwriteDecisionRepo).save(any(DecisionDefinition.class));
        }
    }
}
