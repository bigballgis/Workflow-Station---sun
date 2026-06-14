package com.developer.property;

import com.developer.dto.DecisionDefinitionRequest;
import com.developer.entity.DecisionDefinition;
import com.developer.entity.FunctionUnit;
import com.developer.repository.FunctionUnitRepository;
import com.developer.repository.IconRepository;
import net.jqwik.api.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * DecisionDesignComponentImpl 属性测试 —— AI 生成 主题
 * Feature: dmn-decision-table-integration
 *
 * Property 16: AI 生成数据应用
 * Property 16 (conflict): MODIFY 模式覆盖已有决策
 *
 * Validates: Requirements 17.2, 17.3
 */
public class DecisionDesignAiGeneratedPropertyTest extends DecisionDesignPropertyTestBase {

    // ========== Property 16: AI 生成数据应用 ==========

    /**
     * Property 16: For any AiGeneratedData containing M decisionDefinitions,
     * applying that data to a FunctionUnit via AiWriteServiceImpl.applyGeneratedData()
     * should result in the FunctionUnit containing exactly M DecisionDefinition entities,
     * each with the correct decisionKey, decisionName, dmnXml, hitPolicy, and description.
     *
     * **Validates: Requirements 17.2, 17.3**
     */
    @Property(tries = 100)
    void aiGeneratedDataAppliesDecisionDefinitions(
            @ForAll("decisionCounts") int m,
            @ForAll("functionUnitIds") Long functionUnitId,
            @ForAll("validDecisionRequestLists") List<DecisionDefinitionRequest> requestPool) {

        List<DecisionDefinitionRequest> requests = requestPool.stream().limit(m).toList();
        int actualM = requests.size();

        // Build AI generated decision definitions as List<Map<String, Object>>
        List<Map<String, Object>> aiDecisionDefs = new ArrayList<>();
        for (int i = 0; i < actualM; i++) {
            DecisionDefinitionRequest req = requests.get(i);
            Map<String, Object> decisionMap = new LinkedHashMap<>();
            decisionMap.put("decisionKey", req.getDecisionKey() + "_" + i);
            decisionMap.put("decisionName", req.getDecisionName());
            decisionMap.put("dmnXml", req.getDmnXml());
            decisionMap.put("hitPolicy", req.getHitPolicy());
            decisionMap.put("description", req.getDescription());
            aiDecisionDefs.add(decisionMap);
        }

        com.developer.dto.AiGeneratedData generatedData = com.developer.dto.AiGeneratedData.builder()
                .decisionDefinitions(aiDecisionDefs)
                .build();

        // Build a fresh FunctionUnit (NEW mode — no existing data)
        FunctionUnit functionUnit = FunctionUnit.builder()
                .id(functionUnitId)
                .code("fu-ai-" + functionUnitId)
                .name("AI_FU_" + functionUnitId)
                .displayName("Test FU for AI generation")
                .status(com.developer.enums.FunctionUnitStatus.DRAFT)
                .decisionDefinitions(new ArrayList<>())
                .tableDefinitions(new ArrayList<>())
                .formDefinitions(new ArrayList<>())
                .actionDefinitions(new ArrayList<>())
                .build();

        // Mock repositories for AiWriteServiceImpl
        FunctionUnitRepository aiFuRepo = mock(FunctionUnitRepository.class);
        IconRepository aiIconRepo = mock(IconRepository.class);
        jakarta.persistence.EntityManager aiEntityManager = mock(jakarta.persistence.EntityManager.class);

        when(aiFuRepo.findById(functionUnitId)).thenReturn(Optional.of(functionUnit));
        when(aiFuRepo.save(any(FunctionUnit.class))).thenAnswer(inv -> inv.getArgument(0));

        com.developer.service.impl.AiWriteServiceImpl aiWriteService =
                new com.developer.service.impl.AiWriteServiceImpl(aiFuRepo, aiIconRepo, aiEntityManager);

        // Apply AI generated data
        aiWriteService.applyGeneratedData(functionUnitId, generatedData, null);

        // Verify: FunctionUnit should contain exactly M decision definitions
        assertThat(functionUnit.getDecisionDefinitions())
                .as("FunctionUnit should contain exactly %d decision definitions after AI apply", actualM)
                .hasSize(actualM);

        // Verify each decision definition's fields match the AI-generated data
        for (int i = 0; i < actualM; i++) {
            Map<String, Object> expectedMap = aiDecisionDefs.get(i);
            DecisionDefinition actual = functionUnit.getDecisionDefinitions().get(i);

            assertThat(actual.getDecisionKey())
                    .as("decisionKey[%d] should match AI-generated data", i)
                    .isEqualTo(expectedMap.get("decisionKey"));
            assertThat(actual.getDecisionName())
                    .as("decisionName[%d] should match AI-generated data", i)
                    .isEqualTo(expectedMap.get("decisionName"));
            assertThat(actual.getDmnXml())
                    .as("dmnXml[%d] should match AI-generated data", i)
                    .isEqualTo(expectedMap.get("dmnXml"));
            assertThat(actual.getHitPolicy())
                    .as("hitPolicy[%d] should match AI-generated data", i)
                    .isEqualTo(expectedMap.get("hitPolicy"));
            assertThat(actual.getDescription())
                    .as("description[%d] should match AI-generated data", i)
                    .isEqualTo(expectedMap.get("description"));
            assertThat(actual.getFunctionUnit())
                    .as("decision[%d] should be associated with the target FunctionUnit", i)
                    .isSameAs(functionUnit);
        }
    }

    /**
     * Property 16 (conflict): For any FunctionUnit with pre-existing DecisionDefinitions,
     * applying AiGeneratedData in MODIFY mode should clear old decisions and write only
     * the new AI-generated ones (full replacement strategy).
     *
     * **Validates: Requirements 17.3**
     */
    @Property(tries = 100)
    void aiGeneratedDataOverwritesExistingDecisions(
            @ForAll("functionUnitIds") Long functionUnitId,
            @ForAll("validDecisionRequestLists") List<DecisionDefinitionRequest> existingPool,
            @ForAll("validDecisionRequestLists") List<DecisionDefinitionRequest> newPool) {

        // Limit sizes for test performance
        List<DecisionDefinitionRequest> existingRequests = existingPool.stream().limit(5).toList();
        List<DecisionDefinitionRequest> newRequests = newPool.stream().limit(5).toList();
        int existingCount = existingRequests.size();
        int newCount = newRequests.size();

        // Build a FunctionUnit with pre-existing decision definitions (triggers MODIFY mode)
        FunctionUnit functionUnit = FunctionUnit.builder()
                .id(functionUnitId)
                .code("fu-ai-mod-" + functionUnitId)
                .name("AI_Modify_FU_" + functionUnitId)
                .displayName("Test FU for AI overwrite")
                .status(com.developer.enums.FunctionUnitStatus.DRAFT)
                .decisionDefinitions(new ArrayList<>())
                .tableDefinitions(new ArrayList<>())
                .formDefinitions(new ArrayList<>())
                .actionDefinitions(new ArrayList<>())
                .build();

        // Add pre-existing decisions
        for (int i = 0; i < existingCount; i++) {
            DecisionDefinitionRequest req = existingRequests.get(i);
            functionUnit.getDecisionDefinitions().add(
                    DecisionDefinition.builder()
                            .id(idGenerator.getAndIncrement())
                            .functionUnit(functionUnit)
                            .decisionKey("existing_" + req.getDecisionKey() + "_" + i)
                            .decisionName(req.getDecisionName())
                            .dmnXml(req.getDmnXml())
                            .hitPolicy(req.getHitPolicy())
                            .description(req.getDescription())
                            .build()
            );
        }

        // Build AI generated data with new decisions
        List<Map<String, Object>> aiDecisionDefs = new ArrayList<>();
        for (int i = 0; i < newCount; i++) {
            DecisionDefinitionRequest req = newRequests.get(i);
            Map<String, Object> decisionMap = new LinkedHashMap<>();
            decisionMap.put("decisionKey", "new_" + req.getDecisionKey() + "_" + i);
            decisionMap.put("decisionName", req.getDecisionName());
            decisionMap.put("dmnXml", req.getDmnXml());
            decisionMap.put("hitPolicy", req.getHitPolicy());
            decisionMap.put("description", req.getDescription());
            aiDecisionDefs.add(decisionMap);
        }

        com.developer.dto.AiGeneratedData generatedData = com.developer.dto.AiGeneratedData.builder()
                .decisionDefinitions(aiDecisionDefs)
                .build();

        // Mock repositories
        FunctionUnitRepository modFuRepo = mock(FunctionUnitRepository.class);
        IconRepository modIconRepo = mock(IconRepository.class);
        jakarta.persistence.EntityManager modEntityManager = mock(jakarta.persistence.EntityManager.class);

        when(modFuRepo.findById(functionUnitId)).thenReturn(Optional.of(functionUnit));
        when(modFuRepo.save(any(FunctionUnit.class))).thenAnswer(inv -> inv.getArgument(0));

        com.developer.service.impl.AiWriteServiceImpl aiWriteService =
                new com.developer.service.impl.AiWriteServiceImpl(modFuRepo, modIconRepo, modEntityManager);

        // Apply — MODIFY mode should clear existing and write new
        aiWriteService.applyGeneratedData(functionUnitId, generatedData, null);

        // Verify: only the new AI-generated decisions remain
        assertThat(functionUnit.getDecisionDefinitions())
                .as("After MODIFY mode apply, FunctionUnit should contain only %d new decisions", newCount)
                .hasSize(newCount);

        // Verify none of the old "existing_" prefixed keys remain
        for (DecisionDefinition dd : functionUnit.getDecisionDefinitions()) {
            assertThat(dd.getDecisionKey())
                    .as("No pre-existing decision keys should remain after MODIFY mode apply")
                    .startsWith("new_");
        }

        // Verify each new decision matches the AI-generated data
        for (int i = 0; i < newCount; i++) {
            Map<String, Object> expectedMap = aiDecisionDefs.get(i);
            DecisionDefinition actual = functionUnit.getDecisionDefinitions().get(i);

            assertThat(actual.getDecisionKey()).isEqualTo(expectedMap.get("decisionKey"));
            assertThat(actual.getDecisionName()).isEqualTo(expectedMap.get("decisionName"));
            assertThat(actual.getDmnXml()).isEqualTo(expectedMap.get("dmnXml"));
            assertThat(actual.getHitPolicy()).isEqualTo(expectedMap.get("hitPolicy"));
            assertThat(actual.getDescription()).isEqualTo(expectedMap.get("description"));
        }
    }
}
