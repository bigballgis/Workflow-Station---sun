package com.admin.component;

import com.admin.entity.FunctionUnit;
import com.admin.enums.FunctionUnitStatus;
import com.admin.repository.FunctionUnitAccessRepository;
import com.admin.repository.FunctionUnitContentRepository;
import com.admin.repository.FunctionUnitDependencyRepository;
import com.admin.repository.FunctionUnitRepository;
import com.platform.common.version.SemanticVersion;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;

import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Property-based tests for function unit deduplication logic.
 * 
 * Feature: function-unit-latest-version-display
 */
class FunctionUnitDeduplicationPropertyTest {

    private FunctionUnitRepository functionUnitRepository = mock(FunctionUnitRepository.class);
    private FunctionUnitDependencyRepository dependencyRepository = mock(FunctionUnitDependencyRepository.class);
    private FunctionUnitContentRepository contentRepository = mock(FunctionUnitContentRepository.class);
    private FunctionUnitAccessRepository accessRepository = mock(FunctionUnitAccessRepository.class);

    private FunctionUnitManagerComponent createComponent(List<FunctionUnit> deployedUnits) {
        FunctionUnitRepository repo = mock(FunctionUnitRepository.class);
        when(repo.findByStatusAndEnabled(FunctionUnitStatus.DEPLOYED, true))
                .thenReturn(deployedUnits);
        
        return new FunctionUnitManagerComponent(
                repo,
                dependencyRepository,
                contentRepository,
                accessRepository
        );
    }

    private FunctionUnit buildFunctionUnit(String code, String version) {
        return FunctionUnit.builder()
                .id(UUID.randomUUID().toString())
                .code(code)
                .name("Test " + code)
                .version(version)
                .status(FunctionUnitStatus.DEPLOYED)
                .enabled(true)
                .deployedAt(Instant.now())
                .build();
    }

    @Provide
    Arbitrary<String> codes() {
        return Arbitraries.strings().alpha().ofMinLength(3).ofMaxLength(10)
                .map(s -> "CODE_" + s.toUpperCase());
    }

    @Provide
    Arbitrary<String> semanticVersions() {
        return Combinators.combine(
                Arbitraries.integers().between(0, 20),
                Arbitraries.integers().between(0, 20),
                Arbitraries.integers().between(0, 20)
        ).as((major, minor, patch) -> major + "." + minor + "." + patch);
    }

    @Provide
    Arbitrary<List<FunctionUnit>> functionUnitLists() {
        Arbitrary<String> codeArb = Arbitraries.of("CODE_A", "CODE_B", "CODE_C", "CODE_D");
        Arbitrary<String> versionArb = semanticVersions();

        return Combinators.combine(codeArb, versionArb)
                .as((code, version) -> buildFunctionUnit(code, version))
                .list().ofMinSize(1).ofMaxSize(20);
    }

    /**
     * Property 1: 去重结果唯一性与版本正确性
     * 
     * For any list of function units with multiple versions per code,
     * after deduplication each code appears exactly once and the version
     * is the semantic maximum of all versions for that code.
     * 
     * Feature: function-unit-latest-version-display, Property 1: 去重结果唯一性与版本正确性
     * Validates: Requirements 1.1, 1.2, 2.1, 2.3
     */
    @Property(tries = 200)
    void deduplicationKeepsUniqueCodesWithHighestVersion(
            @ForAll("functionUnitLists") List<FunctionUnit> input) {
        
        FunctionUnitManagerComponent component = createComponent(input);
        List<FunctionUnit> result = component.listLatestDeployedFunctionUnits();

        // Each code appears exactly once
        Map<String, Long> codeCounts = result.stream()
                .collect(Collectors.groupingBy(FunctionUnit::getCode, Collectors.counting()));
        for (Map.Entry<String, Long> entry : codeCounts.entrySet()) {
            assertThat(entry.getValue())
                    .as("Code %s should appear exactly once", entry.getKey())
                    .isEqualTo(1L);
        }

        // The version kept is the semantic maximum for that code
        Map<String, List<FunctionUnit>> inputByCode = input.stream()
                .collect(Collectors.groupingBy(FunctionUnit::getCode));

        for (FunctionUnit resultUnit : result) {
            String code = resultUnit.getCode();
            List<FunctionUnit> allForCode = inputByCode.get(code);
            assertThat(allForCode).isNotNull();

            // Find the expected max version
            SemanticVersion maxVersion = allForCode.stream()
                    .map(u -> SemanticVersion.parse(u.getVersion()))
                    .max(SemanticVersion::compareTo)
                    .orElseThrow();

            SemanticVersion resultVersion = SemanticVersion.parse(resultUnit.getVersion());
            assertThat(resultVersion)
                    .as("Version for code %s should be the maximum", code)
                    .isEqualByComparingTo(maxVersion);
        }
    }

    /**
     * Property 2: 去重保留所有功能单元代码
     * 
     * For any list of function units, the set of codes in the deduplicated result
     * is identical to the set of codes in the input (no codes lost, no codes added).
     * 
     * Feature: function-unit-latest-version-display, Property 2: 去重保留所有功能单元代码
     * Validates: Requirements 1.1, 1.3
     */
    @Property(tries = 200)
    void deduplicationPreservesAllCodes(
            @ForAll("functionUnitLists") List<FunctionUnit> input) {
        
        FunctionUnitManagerComponent component = createComponent(input);
        List<FunctionUnit> result = component.listLatestDeployedFunctionUnits();

        Set<String> inputCodes = input.stream()
                .map(FunctionUnit::getCode)
                .collect(Collectors.toSet());

        Set<String> resultCodes = result.stream()
                .map(FunctionUnit::getCode)
                .collect(Collectors.toSet());

        assertThat(resultCodes)
                .as("Result codes should match input codes exactly")
                .isEqualTo(inputCodes);
    }
}
