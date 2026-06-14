package com.developer.property;

import com.developer.dto.DecisionDefinitionRequest;
import com.developer.dto.FunctionUnitResponse;
import com.developer.dto.ValidationResult;
import com.developer.entity.DecisionDefinition;
import com.developer.entity.FunctionUnit;
import com.developer.exception.DeveloperBusinessException;
import com.developer.exception.ResourceNotFoundException;
import net.jqwik.api.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * DecisionDesignComponentImpl 属性测试 —— CRUD 主题
 * Feature: dmn-decision-table-integration
 *
 * Property 1: 决策定义 CRUD 往返
 * Property 3: 决策键功能单元内唯一约束
 * Property 4: 决策定义列表完整性与计数一致性
 * Property 5: 删除使决策定义不可检索
 *
 * Validates: Requirements 2.2, 2.5, 3.2, 3.3, 3.4, 3.5, 3.6, 3.8, 7.2
 */
public class DecisionDesignCrudPropertyTest extends DecisionDesignPropertyTestBase {

    // ========== Property 1: 决策定义 CRUD 往返 ==========

    /**
     * Property 1: For any valid DecisionDefinitionRequest, creating via component.create()
     * then retrieving via component.getById() should return an entity with the same
     * decisionKey, decisionName, dmnXml, hitPolicy, and description values.
     *
     * **Validates: Requirements 2.2, 3.2, 3.4, 3.5**
     */
    @Property(tries = 100)
    void crudRoundTripProperty(
            @ForAll("validDecisionRequests") DecisionDefinitionRequest request) {

        Long functionUnitId = 1L;
        FunctionUnit functionUnit = FunctionUnit.builder().id(functionUnitId).name("TestFU").build();

        when(functionUnitComponent.getById(functionUnitId)).thenReturn(functionUnit);
        when(decisionDefinitionService.existsByFunctionUnitIdAndDecisionKey(functionUnitId, request.getDecisionKey()))
                .thenReturn(false);

        ValidationResult validResult = new ValidationResult();
        when(dmnXmlValidator.validate(request.getDmnXml())).thenReturn(validResult);
        when(dmnXmlParser.extractHitPolicy(request.getDmnXml())).thenReturn("FIRST");

        // Capture saved entity and assign an ID
        Long generatedId = idGenerator.getAndIncrement();
        when(decisionDefinitionService.save(any(DecisionDefinition.class))).thenAnswer(invocation -> {
            DecisionDefinition saved = invocation.getArgument(0);
            saved.setId(generatedId);
            saved.setCreatedAt(Instant.now());
            saved.setUpdatedAt(Instant.now());
            return saved;
        });

        // Create
        DecisionDefinition created = component.create(functionUnitId, request);

        // Mock getById to return the created entity
        when(decisionDefinitionService.findById(generatedId)).thenReturn(Optional.of(created));

        // Retrieve
        DecisionDefinition retrieved = component.getById(functionUnitId, generatedId);

        // Verify round-trip: all request fields match
        assertThat(retrieved.getDecisionKey())
                .as("decisionKey should survive create-then-get round-trip")
                .isEqualTo(request.getDecisionKey());
        assertThat(retrieved.getDecisionName())
                .as("decisionName should survive create-then-get round-trip")
                .isEqualTo(request.getDecisionName());
        assertThat(retrieved.getDmnXml())
                .as("dmnXml should survive create-then-get round-trip")
                .isEqualTo(request.getDmnXml());
        assertThat(retrieved.getDescription())
                .as("description should survive create-then-get round-trip")
                .isEqualTo(request.getDescription());

        // hitPolicy: if request provided one, it should match; otherwise extracted from XML
        String expectedHitPolicy = (request.getHitPolicy() != null && !request.getHitPolicy().isBlank())
                ? request.getHitPolicy() : "FIRST";
        assertThat(retrieved.getHitPolicy())
                .as("hitPolicy should survive create-then-get round-trip")
                .isEqualTo(expectedHitPolicy);

        assertThat(retrieved.getFunctionUnit()).isEqualTo(functionUnit);
        assertThat(retrieved.getId()).isNotNull();
    }

    // ========== Property 3: 决策键功能单元内唯一约束 ==========

    /**
     * Property 3: For any FunctionUnit and any existing decisionKey, attempting to create
     * another DecisionDefinition with the same decisionKey in the same FunctionUnit
     * should throw DeveloperBusinessException with errorCode "CONFLICT_DECISION_KEY_EXISTS".
     *
     * **Validates: Requirements 2.5, 3.8**
     */
    @Property(tries = 100)
    void duplicateDecisionKeyThrowsConflict(
            @ForAll("validDecisionRequests") DecisionDefinitionRequest request,
            @ForAll("functionUnitIds") Long functionUnitId) {

        FunctionUnit functionUnit = FunctionUnit.builder().id(functionUnitId).name("FU_" + functionUnitId).build();

        when(functionUnitComponent.getById(functionUnitId)).thenReturn(functionUnit);
        // Simulate that the key already exists
        when(decisionDefinitionService.existsByFunctionUnitIdAndDecisionKey(functionUnitId, request.getDecisionKey()))
                .thenReturn(true);

        assertThatThrownBy(() -> component.create(functionUnitId, request))
                .isInstanceOf(DeveloperBusinessException.class)
                .satisfies(ex -> {
                    DeveloperBusinessException bex = (DeveloperBusinessException) ex;
                    assertThat(bex.getErrorCode()).isEqualTo("CONFLICT_DECISION_KEY_EXISTS");
                });

        // Verify save was never called
        verify(decisionDefinitionService, never()).save(any());
    }

    /**
     * Property 3 (complement): The same decisionKey in different FunctionUnits should
     * be allowed — no conflict thrown.
     *
     * **Validates: Requirements 2.5, 3.8**
     */
    @Property(tries = 100)
    void sameKeyDifferentFunctionUnitAllowed(
            @ForAll("validDecisionRequests") DecisionDefinitionRequest request,
            @ForAll("functionUnitIdPairs") Long[] fuIds) {

        Long fuId1 = fuIds[0];
        Long fuId2 = fuIds[1];

        FunctionUnit fu1 = FunctionUnit.builder().id(fuId1).name("FU_" + fuId1).build();
        FunctionUnit fu2 = FunctionUnit.builder().id(fuId2).name("FU_" + fuId2).build();

        ValidationResult validResult = new ValidationResult();
        when(dmnXmlValidator.validate(request.getDmnXml())).thenReturn(validResult);
        when(dmnXmlParser.extractHitPolicy(request.getDmnXml())).thenReturn("FIRST");

        // FU1: key does not exist
        when(functionUnitComponent.getById(fuId1)).thenReturn(fu1);
        when(decisionDefinitionService.existsByFunctionUnitIdAndDecisionKey(fuId1, request.getDecisionKey()))
                .thenReturn(false);
        when(decisionDefinitionService.save(any(DecisionDefinition.class))).thenAnswer(invocation -> {
            DecisionDefinition saved = invocation.getArgument(0);
            saved.setId(idGenerator.getAndIncrement());
            saved.setCreatedAt(Instant.now());
            saved.setUpdatedAt(Instant.now());
            return saved;
        });

        // Create in FU1 — should succeed
        DecisionDefinition created1 = component.create(fuId1, request);
        assertThat(created1).isNotNull();

        // FU2: same key does not exist in FU2
        when(functionUnitComponent.getById(fuId2)).thenReturn(fu2);
        when(decisionDefinitionService.existsByFunctionUnitIdAndDecisionKey(fuId2, request.getDecisionKey()))
                .thenReturn(false);

        // Create in FU2 with same key — should also succeed
        DecisionDefinition created2 = component.create(fuId2, request);
        assertThat(created2).isNotNull();
    }

    // ========== Property 4: 决策定义列表完整性与计数一致性 ==========

    /**
     * Property 4a: For any FunctionUnit with N decision definitions,
     * DecisionDesignComponentImpl.list() should return exactly N items.
     *
     * **Validates: Requirements 3.3**
     */
    @Property(tries = 100)
    void listReturnsExactlyNDecisions(
            @ForAll("decisionCounts") int n,
            @ForAll("functionUnitIds") Long functionUnitId) {

        FunctionUnit functionUnit = FunctionUnit.builder().id(functionUnitId).name("FU_" + functionUnitId).build();
        when(functionUnitComponent.getById(functionUnitId)).thenReturn(functionUnit);

        // Build N decision definitions
        List<DecisionDefinition> decisions = IntStream.range(0, n)
                .mapToObj(i -> DecisionDefinition.builder()
                        .id(idGenerator.getAndIncrement())
                        .functionUnit(functionUnit)
                        .decisionKey("dk_" + i)
                        .decisionName("Decision " + i)
                        .hitPolicy("FIRST")
                        .createdAt(Instant.now())
                        .updatedAt(Instant.now())
                        .build())
                .toList();

        when(decisionDefinitionService.findByFunctionUnitId(functionUnitId)).thenReturn(decisions);

        List<DecisionDefinition> result = component.list(functionUnitId);

        assertThat(result)
                .as("list() should return exactly %d items", n)
                .hasSize(n);
    }

    /**
     * Property 4b: For any FunctionUnit with N decision definitions,
     * FunctionUnitResponse.decisionCount should equal N.
     *
     * **Validates: Requirements 7.2**
     */
    @Property(tries = 100)
    void decisionCountMatchesNumberOfDecisions(
            @ForAll("decisionCounts") int n,
            @ForAll("functionUnitIds") Long functionUnitId) {

        // Build a FunctionUnit entity with N DecisionDefinitions in its collection
        FunctionUnit functionUnit = FunctionUnit.builder()
                .id(functionUnitId)
                .code("fu-test-" + functionUnitId)
                .name("FU_" + functionUnitId)
                .decisionDefinitions(new ArrayList<>())
                .tableDefinitions(new ArrayList<>())
                .formDefinitions(new ArrayList<>())
                .actionDefinitions(new ArrayList<>())
                .build();

        for (int i = 0; i < n; i++) {
            functionUnit.getDecisionDefinitions().add(
                    DecisionDefinition.builder()
                            .id(idGenerator.getAndIncrement())
                            .functionUnit(functionUnit)
                            .decisionKey("dk_" + i)
                            .decisionName("Decision " + i)
                            .hitPolicy("FIRST")
                            .createdAt(Instant.now())
                            .updatedAt(Instant.now())
                            .build()
            );
        }

        when(functionUnitRepository.findById(functionUnitId)).thenReturn(Optional.of(functionUnit));

        FunctionUnitResponse response = functionUnitComponentImpl.getByIdAsResponse(functionUnitId);

        assertThat(response.getDecisionCount())
                .as("decisionCount should equal the number of decision definitions (%d)", n)
                .isEqualTo(n);
    }

    // ========== Property 5: 删除使决策定义不可检索 ==========

    /**
     * Property 5: For any existing DecisionDefinition, after component.delete(),
     * component.getById() should throw ResourceNotFoundException.
     *
     * **Validates: Requirements 3.6**
     */
    @Property(tries = 100)
    void deleteRendersDecisionUnretrievable(
            @ForAll("validDecisionRequests") DecisionDefinitionRequest request,
            @ForAll("functionUnitIds") Long functionUnitId) {

        Long decisionId = idGenerator.getAndIncrement();
        FunctionUnit functionUnit = FunctionUnit.builder().id(functionUnitId).name("FU_" + functionUnitId).build();

        DecisionDefinition existing = DecisionDefinition.builder()
                .id(decisionId)
                .functionUnit(functionUnit)
                .decisionKey(request.getDecisionKey())
                .decisionName(request.getDecisionName())
                .dmnXml(request.getDmnXml())
                .hitPolicy(request.getHitPolicy())
                .description(request.getDescription())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        when(functionUnitComponent.getById(functionUnitId)).thenReturn(functionUnit);

        // Before delete: entity exists
        when(decisionDefinitionService.findById(decisionId)).thenReturn(Optional.of(existing));
        doNothing().when(decisionDefinitionService).deleteById(decisionId);

        // Delete
        component.delete(functionUnitId, decisionId);

        verify(decisionDefinitionService).deleteById(decisionId);

        // After delete: entity no longer found
        when(decisionDefinitionService.findById(decisionId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> component.getById(functionUnitId, decisionId))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
