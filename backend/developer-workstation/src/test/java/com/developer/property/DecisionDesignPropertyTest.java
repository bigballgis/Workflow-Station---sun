package com.developer.property;

import com.developer.component.FunctionUnitComponent;
import com.developer.component.impl.DecisionDesignComponentImpl;
import com.developer.component.impl.FunctionUnitComponentImpl;
import com.developer.component.impl.VersionComponentImpl;
import com.developer.dto.DecisionDefinitionRequest;
import com.developer.dto.FunctionUnitResponse;
import com.developer.dto.ValidationResult;
import com.developer.entity.DecisionDefinition;
import com.developer.entity.FunctionUnit;
import com.developer.exception.DeveloperBusinessException;
import com.developer.exception.ResourceNotFoundException;
import com.developer.repository.*;
import com.developer.service.DecisionDefinitionService;
import com.developer.validation.DmnXmlParser;
import com.developer.validation.DmnXmlValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.jqwik.api.*;
import net.jqwik.api.lifecycle.BeforeProperty;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;

import com.developer.security.FunctionUnitWorkspaceAccessService;
import com.developer.service.UserDisplayNameService;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * DecisionDesignComponentImpl 属性测试
 * Feature: dmn-decision-table-integration
 *
 * Property 1: 决策定义 CRUD 往返
 * Property 3: 决策键功能单元内唯一约束
 * Property 4: 决策定义列表完整性与计数一致性
 * Property 5: 删除使决策定义不可检索
 * Property 8: 版本快照往返（含决策定义）
 *
 * Validates: Requirements 2.2, 2.5, 3.2, 3.3, 3.4, 3.5, 3.6, 3.8, 6.1, 6.3, 7.2
 */
public class DecisionDesignPropertyTest {

    private DecisionDefinitionService decisionDefinitionService;
    private DmnXmlValidator dmnXmlValidator;
    private DmnXmlParser dmnXmlParser;
    private FunctionUnitComponent functionUnitComponent;
    private DecisionDesignComponentImpl component;

    // For Property 4: FunctionUnitComponentImpl to test decisionCount in toResponse
    private FunctionUnitRepository functionUnitRepository;
    private FunctionUnitComponentImpl functionUnitComponentImpl;
    private FunctionUnitDevGroupAssignmentRepository functionUnitDevGroupAssignmentRepository;

    private AtomicLong idGenerator;

    @BeforeProperty
    void setUp() {
        decisionDefinitionService = mock(DecisionDefinitionService.class);
        dmnXmlValidator = mock(DmnXmlValidator.class);
        dmnXmlParser = mock(DmnXmlParser.class);
        functionUnitComponent = mock(FunctionUnitComponent.class);

        component = new DecisionDesignComponentImpl(
                decisionDefinitionService,
                dmnXmlValidator,
                dmnXmlParser,
                functionUnitComponent
        );

        // Set up FunctionUnitComponentImpl with mocked dependencies for Property 4
        functionUnitRepository = mock(FunctionUnitRepository.class);
        functionUnitDevGroupAssignmentRepository = mock(FunctionUnitDevGroupAssignmentRepository.class);
        when(functionUnitDevGroupAssignmentRepository.findByFunctionUnitId(anyLong())).thenReturn(List.of());
        functionUnitComponentImpl = new FunctionUnitComponentImpl(
                functionUnitRepository,
                mock(ProcessDefinitionRepository.class),
                mock(TableDefinitionRepository.class),
                mock(FormDefinitionRepository.class),
                mock(ActionDefinitionRepository.class),
                mock(DecisionDefinitionRepository.class),
                mock(FormTableBindingRepository.class),
                mock(FormStageBindingRepository.class),
                mock(TableRelationRepository.class),
                mock(SubTableViewConfigRepository.class),
                mock(VersionRepository.class),
                mock(IconRepository.class),
                new ObjectMapper(),
                mock(UserDisplayNameService.class),
                mock(FunctionUnitWorkspaceAccessService.class),
                functionUnitDevGroupAssignmentRepository,
                mock(com.developer.component.VersionComponent.class),
                mock(com.developer.util.DeveloperWorkstationSequenceSynchronizer.class)
        );

        idGenerator = new AtomicLong(1L);
    }

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

    // ========== Property 8: 版本快照往返（含决策定义） ==========

    /**
     * Property 8: For any FunctionUnit with N DecisionDefinitions, creating a snapshot
     * via FunctionUnitComponentImpl.createSnapshot() and restoring from that snapshot
     * via VersionComponentImpl.restoreFromSnapshot() should preserve all decision
     * definition fields (decisionKey, decisionName, dmnXml, hitPolicy, description)
     * and the count should remain N.
     *
     * **Validates: Requirements 6.1, 6.3**
     */
    @Property(tries = 100)
    void versionSnapshotRoundTripPreservesDecisionDefinitions(
            @ForAll("decisionCounts") int n,
            @ForAll("functionUnitIds") Long functionUnitId,
            @ForAll("validDecisionRequestLists") List<DecisionDefinitionRequest> requestPool) {

        // Take up to n items from the pool
        List<DecisionDefinitionRequest> requests = requestPool.stream().limit(n).toList();
        int actualN = requests.size();

        // Build a FunctionUnit with N DecisionDefinitions
        FunctionUnit functionUnit = FunctionUnit.builder()
                .id(functionUnitId)
                .code("fu-snap-" + functionUnitId)
                .name("FU_Snap_" + functionUnitId)
                .description("Test FU for snapshot")
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
                            .decisionKey(req.getDecisionKey() + "_" + i) // ensure unique keys
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
            ObjectMapper objectMapper = new ObjectMapper();

            // Step 1: Create snapshot via FunctionUnitComponentImpl.createSnapshot() (private)
            java.lang.reflect.Method createSnapshotMethod = FunctionUnitComponentImpl.class
                    .getDeclaredMethod("createSnapshot", FunctionUnit.class);
            createSnapshotMethod.setAccessible(true);
            byte[] snapshotBytes = (byte[]) createSnapshotMethod.invoke(functionUnitComponentImpl, functionUnit);

            assertThat(snapshotBytes).isNotNull();

            // Verify snapshot JSON contains decisionDefinitions array with correct count
            @SuppressWarnings("unchecked")
            Map<String, Object> snapshotMap = objectMapper.readValue(snapshotBytes, Map.class);
            assertThat(snapshotMap).containsKey("decisionDefinitions");

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> decisionSnapshots =
                    (List<Map<String, Object>>) snapshotMap.get("decisionDefinitions");
            assertThat(decisionSnapshots)
                    .as("Snapshot should contain exactly %d decision definitions", actualN)
                    .hasSize(actualN);

            // Step 2: Restore from snapshot via VersionComponentImpl.restoreFromSnapshot() (private)
            VersionComponentImpl versionComponent = new VersionComponentImpl(
                    mock(VersionRepository.class),
                    mock(FunctionUnitRepository.class),
                    objectMapper,
                    mock(com.developer.util.DeveloperWorkstationSequenceSynchronizer.class)
            );

            // Build a fresh FunctionUnit to restore into
            FunctionUnit restored = FunctionUnit.builder()
                    .id(functionUnitId)
                    .code("fu-snap-" + functionUnitId)
                    .name("FU_Snap_" + functionUnitId)
                    .description("Old description")
                    .status(com.developer.enums.FunctionUnitStatus.DRAFT)
                    .decisionDefinitions(new ArrayList<>())
                    .tableDefinitions(new ArrayList<>())
                    .formDefinitions(new ArrayList<>())
                    .actionDefinitions(new ArrayList<>())
                    .build();

            // Add some pre-existing decisions that should be cleared on restore
            restored.getDecisionDefinitions().add(
                    DecisionDefinition.builder()
                            .id(idGenerator.getAndIncrement())
                            .functionUnit(restored)
                            .decisionKey("pre_existing_key")
                            .decisionName("Pre-existing")
                            .build()
            );

            java.lang.reflect.Method restoreMethod = VersionComponentImpl.class
                    .getDeclaredMethod("restoreFromSnapshot", FunctionUnit.class, Map.class);
            restoreMethod.setAccessible(true);
            restoreMethod.invoke(versionComponent, restored, snapshotMap);

            // Step 3: Verify round-trip — restored FunctionUnit should have exactly N decisions
            assertThat(restored.getDecisionDefinitions())
                    .as("Restored FunctionUnit should have exactly %d decision definitions", actualN)
                    .hasSize(actualN);

            // Verify each decision definition's fields match the originals
            for (int i = 0; i < actualN; i++) {
                DecisionDefinition original = functionUnit.getDecisionDefinitions().get(i);
                DecisionDefinition restoredDef = restored.getDecisionDefinitions().get(i);

                assertThat(restoredDef.getDecisionKey())
                        .as("decisionKey[%d] should survive snapshot round-trip", i)
                        .isEqualTo(original.getDecisionKey());
                assertThat(restoredDef.getDecisionName())
                        .as("decisionName[%d] should survive snapshot round-trip", i)
                        .isEqualTo(original.getDecisionName());
                assertThat(restoredDef.getDmnXml())
                        .as("dmnXml[%d] should survive snapshot round-trip", i)
                        .isEqualTo(original.getDmnXml());
                assertThat(restoredDef.getHitPolicy())
                        .as("hitPolicy[%d] should survive snapshot round-trip", i)
                        .isEqualTo(original.getHitPolicy());
                assertThat(restoredDef.getDescription())
                        .as("description[%d] should survive snapshot round-trip", i)
                        .isEqualTo(original.getDescription());

                // Restored decisions should be associated with the restored FunctionUnit
                assertThat(restoredDef.getFunctionUnit())
                        .as("restored decision[%d] should be associated with the restored FunctionUnit", i)
                        .isSameAs(restored);
            }

        } catch (Exception e) {
            fail("Snapshot round-trip should not throw exception: " + e.getMessage(), e);
        }
    }

    // ========== Generators ==========

    @Provide
    Arbitrary<DecisionDefinitionRequest> validDecisionRequests() {
        return Combinators.combine(
                validDecisionKeys(),
                validDecisionNames(),
                validDmnXmlStrings(),
                validHitPolicies(),
                validDescriptions()
        ).as((key, name, xml, hitPolicy, desc) -> {
            DecisionDefinitionRequest req = new DecisionDefinitionRequest();
            req.setDecisionKey(key);
            req.setDecisionName(name);
            req.setDmnXml(xml);
            req.setHitPolicy(hitPolicy);
            req.setDescription(desc);
            return req;
        });
    }

    @Provide
    Arbitrary<Long> functionUnitIds() {
        return Arbitraries.longs().between(1L, 10000L);
    }

    @Provide
    Arbitrary<Integer> decisionCounts() {
        return Arbitraries.integers().between(0, 20);
    }

    @Provide
    Arbitrary<List<DecisionDefinitionRequest>> validDecisionRequestLists() {
        return validDecisionRequests().list().ofMinSize(0).ofMaxSize(20);
    }

    @Provide
    Arbitrary<Long[]> functionUnitIdPairs() {
        return Combinators.combine(
                Arbitraries.longs().between(1L, 5000L),
                Arbitraries.longs().between(5001L, 10000L)
        ).as((id1, id2) -> new Long[]{id1, id2});
    }

    // ========== Helper Arbitraries ==========

    private Arbitrary<String> validDecisionKeys() {
        return Arbitraries.strings()
                .withCharRange('a', 'z')
                .ofMinLength(1)
                .ofMaxLength(20)
                .map(s -> "dk" + s);
    }

    private Arbitrary<String> validDecisionNames() {
        return Arbitraries.strings()
                .withCharRange('a', 'z')
                .ofMinLength(1)
                .ofMaxLength(30)
                .map(s -> "Decision " + s);
    }

    private Arbitrary<String> validDmnXmlStrings() {
        return Combinators.combine(
                validDecisionKeys(),
                Arbitraries.of("FIRST", "UNIQUE", "ANY", "PRIORITY",
                        "COLLECT", "RULE_ORDER", "OUTPUT_ORDER")
        ).as((key, hp) ->
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<definitions xmlns=\"https://www.omg.org/spec/DMN/20191111/MODEL/\"" +
                " id=\"definitions\" name=\"definitions\"" +
                " namespace=\"http://camunda.org/schema/1.0/dmn\">\n" +
                "  <decision id=\"" + key + "\" name=\"Test\">\n" +
                "    <decisionTable id=\"dt_1\" hitPolicy=\"" + hp + "\">\n" +
                "      <input id=\"input_1\" label=\"Amount\">\n" +
                "        <inputExpression id=\"ie_1\" typeRef=\"double\">\n" +
                "          <text>amount</text>\n" +
                "        </inputExpression>\n" +
                "      </input>\n" +
                "      <output id=\"output_1\" label=\"Result\" name=\"result\" typeRef=\"string\" />\n" +
                "    </decisionTable>\n" +
                "  </decision>\n" +
                "</definitions>"
        );
    }

    private Arbitrary<String> validHitPolicies() {
        return Arbitraries.of("FIRST", "UNIQUE", "ANY", "PRIORITY",
                "COLLECT", "RULE_ORDER", "OUTPUT_ORDER");
    }

    private Arbitrary<String> validDescriptions() {
        return Arbitraries.oneOf(
                Arbitraries.just(null),
                Arbitraries.strings()
                        .withCharRange('a', 'z')
                        .ofMinLength(0)
                        .ofMaxLength(50)
                        .map(s -> "Desc: " + s)
        );
    }

    // ========== Property 9: 克隆复制所有决策定义 ==========

    /**
     * Property 9: For any FunctionUnit with N DecisionDefinitions, cloning the
     * FunctionUnit should produce a new FunctionUnit with exactly N DecisionDefinitions,
     * each having the same decisionKey, decisionName, dmnXml, hitPolicy, description
     * as the source, but different id and associated with the new FunctionUnit.
     *
     * **Validates: Requirements 6.4**
     */
    @Property(tries = 100)
    void cloneCopiesAllDecisionDefinitions(
            @ForAll("decisionCounts") int n,
            @ForAll("functionUnitIds") Long sourceFuId,
            @ForAll("validDecisionRequestLists") List<DecisionDefinitionRequest> requestPool) {

        // Take up to n items from the pool
        List<DecisionDefinitionRequest> requests = requestPool.stream().limit(n).toList();
        int actualN = requests.size();

        // Build source FunctionUnit with N DecisionDefinitions (no tables/forms/actions/process for simplicity)
        FunctionUnit source = FunctionUnit.builder()
                .id(sourceFuId)
                .code("fu-src-" + sourceFuId)
                .name("SourceFU_" + sourceFuId)
                .description("Source description")
                .status(com.developer.enums.FunctionUnitStatus.PUBLISHED)
                .decisionDefinitions(new ArrayList<>())
                .tableDefinitions(new ArrayList<>())
                .formDefinitions(new ArrayList<>())
                .actionDefinitions(new ArrayList<>())
                .build();

        for (int i = 0; i < actualN; i++) {
            DecisionDefinitionRequest req = requests.get(i);
            source.getDecisionDefinitions().add(
                    DecisionDefinition.builder()
                            .id(idGenerator.getAndIncrement())
                            .functionUnit(source)
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

        String clonedName = "ClonedFU_" + sourceFuId;

        // Mock: name does not exist
        when(functionUnitRepository.existsByName(clonedName)).thenReturn(false);
        // Mock: source FU found
        when(functionUnitRepository.findById(sourceFuId)).thenReturn(Optional.of(source));
        // Mock: code uniqueness check always passes
        when(functionUnitRepository.existsByCode(anyString())).thenReturn(false);

        // Capture all saved DecisionDefinitions
        List<DecisionDefinition> savedDecisions = new ArrayList<>();
        DecisionDefinitionRepository decisionDefRepo = mock(DecisionDefinitionRepository.class);
        when(decisionDefRepo.save(any(DecisionDefinition.class))).thenAnswer(invocation -> {
            DecisionDefinition saved = invocation.getArgument(0);
            saved.setId(idGenerator.getAndIncrement());
            saved.setCreatedAt(Instant.now());
            saved.setUpdatedAt(Instant.now());
            savedDecisions.add(saved);
            return saved;
        });

        // Build a FunctionUnitComponentImpl with the mocked decisionDefinitionRepository
        FunctionUnitDevGroupAssignmentRepository cloneDevGroupRepo = mock(FunctionUnitDevGroupAssignmentRepository.class);
        when(cloneDevGroupRepo.findByFunctionUnitId(anyLong())).thenReturn(List.of());
        FunctionUnitComponentImpl cloneComponent = new FunctionUnitComponentImpl(
                functionUnitRepository,
                mock(ProcessDefinitionRepository.class),
                mock(TableDefinitionRepository.class),
                mock(FormDefinitionRepository.class),
                mock(ActionDefinitionRepository.class),
                decisionDefRepo,
                mock(FormTableBindingRepository.class),
                mock(FormStageBindingRepository.class),
                mock(TableRelationRepository.class),
                mock(SubTableViewConfigRepository.class),
                mock(VersionRepository.class),
                mock(IconRepository.class),
                new ObjectMapper(),
                mock(UserDisplayNameService.class),
                mock(FunctionUnitWorkspaceAccessService.class),
                cloneDevGroupRepo,
                mock(com.developer.component.VersionComponent.class),
                mock(com.developer.util.DeveloperWorkstationSequenceSynchronizer.class)
        );

        // Mock: functionUnitRepository.save returns the entity with an ID
        Long clonedFuId = idGenerator.getAndIncrement();
        when(functionUnitRepository.save(any(FunctionUnit.class))).thenAnswer(invocation -> {
            FunctionUnit saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(clonedFuId);
            }
            return saved;
        });

        // Execute clone
        FunctionUnit cloned = cloneComponent.clone(sourceFuId, clonedName);

        // Verify: exactly N decision definitions were saved
        assertThat(savedDecisions)
                .as("Clone should save exactly %d decision definitions", actualN)
                .hasSize(actualN);

        // Verify: each cloned decision has same field values but different id and functionUnit
        for (int i = 0; i < actualN; i++) {
            DecisionDefinition original = source.getDecisionDefinitions().get(i);
            DecisionDefinition clonedDef = savedDecisions.get(i);

            assertThat(clonedDef.getDecisionKey())
                    .as("cloned decisionKey[%d] should match source", i)
                    .isEqualTo(original.getDecisionKey());
            assertThat(clonedDef.getDecisionName())
                    .as("cloned decisionName[%d] should match source", i)
                    .isEqualTo(original.getDecisionName());
            assertThat(clonedDef.getDmnXml())
                    .as("cloned dmnXml[%d] should match source", i)
                    .isEqualTo(original.getDmnXml());
            assertThat(clonedDef.getHitPolicy())
                    .as("cloned hitPolicy[%d] should match source", i)
                    .isEqualTo(original.getHitPolicy());
            assertThat(clonedDef.getDescription())
                    .as("cloned description[%d] should match source", i)
                    .isEqualTo(original.getDescription());

            // ID should be different from source
            assertThat(clonedDef.getId())
                    .as("cloned decision[%d] should have a different id", i)
                    .isNotEqualTo(original.getId());

            // Should be associated with the cloned FunctionUnit, not the source
            assertThat(clonedDef.getFunctionUnit())
                    .as("cloned decision[%d] should be associated with the cloned FunctionUnit", i)
                    .isSameAs(cloned);
            assertThat(clonedDef.getFunctionUnit().getId())
                    .as("cloned decision[%d] functionUnit id should differ from source", i)
                    .isNotEqualTo(sourceFuId);
        }

        // Verify: cloned FunctionUnit has a different ID
        assertThat(cloned.getId())
                .as("Cloned FunctionUnit should have a different ID from source")
                .isNotEqualTo(sourceFuId);
    }

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
                .description("Test FU for export/import round-trip")
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
                    new com.developer.component.impl.ExportImportComponentImpl(
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
                    new com.developer.component.impl.ExportImportComponentImpl(
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
                    new com.developer.component.impl.ExportImportComponentImpl(
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

    // ========== Helper: Build a test ZIP for import ==========

    private byte[] buildTestZip(String fuName, String dmnXml) {
        try (java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
             java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(baos)) {

            // Add manifest.json
            ObjectMapper mapper = new ObjectMapper();
            mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
            mapper.disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

            Map<String, Object> manifest = new LinkedHashMap<>();
            manifest.put("name", fuName);
            manifest.put("code", "fu-import-test");
            manifest.put("version", "1.0.0");
            manifest.put("description", "Test import");

            Map<String, Object> components = new LinkedHashMap<>();
            components.put("process", null);
            components.put("tables", List.of());
            components.put("forms", List.of());
            components.put("actions", List.of());
            components.put("decisions", List.of("decisions/decision_0.dmn"));
            manifest.put("components", components);

            byte[] manifestBytes = mapper.writeValueAsBytes(manifest);
            zos.putNextEntry(new java.util.zip.ZipEntry("manifest.json"));
            zos.write(manifestBytes);
            zos.closeEntry();

            // Add decision DMN file
            byte[] dmnBytes = dmnXml.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            zos.putNextEntry(new java.util.zip.ZipEntry("decisions/decision_0.dmn"));
            zos.write(dmnBytes);
            zos.closeEntry();

            zos.finish();
            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Failed to build test ZIP: " + e.getMessage(), e);
        }
    }

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
                .description("Test FU for AI generation")
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
                .description("Test FU for AI overwrite")
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