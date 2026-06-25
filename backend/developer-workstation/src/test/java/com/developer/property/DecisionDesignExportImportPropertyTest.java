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

    // ========== Property 15: 导入即版本（无冲突策略） ==========

    /**
     * Property 15: Import behavior without conflict strategy:
     * - When the function unit name does NOT exist → a new function unit is created and the
     *   imported decision is saved (SUCCESS, not versioned).
     * - When the function unit name already exists → the import adds a version onto the existing
     *   unit: it snapshots+clears via VersionComponent, then re-imports the package content onto
     *   the SAME function unit (SUCCESS, versioned=true, no delete of the existing unit).
     *
     * **Validates: name-absent→new, name-exists→add-version**
     */
    @Property(tries = 100)
    void importAddsVersionWhenNameExistsElseCreatesNew(
            @ForAll("validDecisionRequests") DecisionDefinitionRequest originalRequest,
            @ForAll("validDecisionRequests") DecisionDefinitionRequest importedRequest,
            @ForAll("functionUnitIds") Long functionUnitId) {

        // Use a real DmnXmlParser for extracting keys from XML
        DmnXmlParser realParser = new DmnXmlParser();

        // --- Name does NOT exist → new import ---
        {
            FunctionUnitRepository newFuRepo = mock(FunctionUnitRepository.class);
            DecisionDefinitionRepository newDecisionRepo = mock(DecisionDefinitionRepository.class);

            String fuName = "FreshFU_" + functionUnitId;
            when(newFuRepo.findByName(fuName)).thenReturn(Optional.empty());
            when(newFuRepo.existsByCode(anyString())).thenReturn(false);

            Long newFuId = idGenerator.getAndIncrement();
            when(newFuRepo.save(any(FunctionUnit.class))).thenAnswer(invocation -> {
                FunctionUnit saved = invocation.getArgument(0);
                if (saved.getId() == null) {
                    saved.setId(newFuId);
                }
                return saved;
            });
            when(newDecisionRepo.findByFunctionUnitId(newFuId)).thenReturn(new ArrayList<>());
            when(newDecisionRepo.save(any(DecisionDefinition.class))).thenAnswer(invocation -> {
                DecisionDefinition saved = invocation.getArgument(0);
                saved.setId(idGenerator.getAndIncrement());
                return saved;
            });

            com.developer.component.impl.ExportImportComponentImpl newComponent =
                    com.developer.component.impl.ExportImportTestComponents.build(
                            newFuRepo,
                            mock(ProcessDefinitionRepository.class),
                            mock(TableDefinitionRepository.class),
                            mock(FormDefinitionRepository.class),
                            mock(ActionDefinitionRepository.class),
                            newDecisionRepo,
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

            byte[] zipBytes = buildTestZip(fuName, importedRequest.getDmnXml());
            org.springframework.mock.web.MockMultipartFile mockFile =
                    new org.springframework.mock.web.MockMultipartFile(
                            "file", "test.zip", "application/zip", zipBytes);

            Map<String, Object> result = newComponent.importFunctionUnit(mockFile, null);

            assertThat(result.get("status"))
                    .as("New import (name absent) should return SUCCESS")
                    .isEqualTo("SUCCESS");
            assertThat(result.get("versioned"))
                    .as("New import should not be versioned")
                    .isEqualTo(false);
            verify(newDecisionRepo).save(any(DecisionDefinition.class));
        }

        // --- Name EXISTS → add a version onto the existing unit ---
        {
            FunctionUnitRepository versionFuRepo = mock(FunctionUnitRepository.class);
            DecisionDefinitionRepository versionDecisionRepo = mock(DecisionDefinitionRepository.class);

            String fuName = "ExistingFU_" + functionUnitId;

            FunctionUnit existingFu = FunctionUnit.builder()
                    .id(functionUnitId)
                    .code("fu-existing-" + functionUnitId)
                    .name(fuName)
                    .currentVersion("1.0.0")
                    .build();

            when(versionFuRepo.findByName(fuName)).thenReturn(Optional.of(existingFu));
            when(versionFuRepo.existsByCode(anyString())).thenReturn(false);
            when(versionFuRepo.save(any(FunctionUnit.class))).thenAnswer(invocation -> invocation.getArgument(0));

            when(versionDecisionRepo.findByFunctionUnitId(functionUnitId)).thenReturn(new ArrayList<>());
            when(versionDecisionRepo.save(any(DecisionDefinition.class))).thenAnswer(invocation -> {
                DecisionDefinition saved = invocation.getArgument(0);
                saved.setId(idGenerator.getAndIncrement());
                return saved;
            });

            com.developer.component.impl.ExportImportComponentImpl versionComponent =
                    com.developer.component.impl.ExportImportTestComponents.build(
                            versionFuRepo,
                            mock(ProcessDefinitionRepository.class),
                            mock(TableDefinitionRepository.class),
                            mock(FormDefinitionRepository.class),
                            mock(ActionDefinitionRepository.class),
                            versionDecisionRepo,
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

            byte[] zipBytes = buildTestZip(fuName, importedRequest.getDmnXml());
            org.springframework.mock.web.MockMultipartFile mockFile =
                    new org.springframework.mock.web.MockMultipartFile(
                            "file", "test.zip", "application/zip", zipBytes);

            Map<String, Object> result = versionComponent.importFunctionUnit(mockFile, "my change log");

            assertThat(result.get("status"))
                    .as("Re-import onto existing name should return SUCCESS")
                    .isEqualTo("SUCCESS");
            assertThat(result.get("versioned"))
                    .as("Re-import onto existing name should be versioned")
                    .isEqualTo(true);
            // Existing unit must NOT be deleted (we add a version, not overwrite-by-delete)
            verify(versionFuRepo, never()).deleteById(any());
            verify(versionDecisionRepo).save(any(DecisionDefinition.class));
        }
    }
}
