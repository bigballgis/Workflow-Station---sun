package com.portal.properties;

import net.jqwik.api.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property test for dependency missing error handling.
 *
 * Feature: travel-expense-reimbursement, Property 4: 依赖缺失时的错误处理
 *
 * For any dependent init script (01, 02, 03), when prerequisite data is
 * missing, the script should contain RAISE EXCEPTION patterns to fail
 * explicitly rather than silently inserting incomplete data.
 *
 * **Validates: Requirements 8.4**
 */
public class TravelExpenseDependencyErrorPropertyTest {

    private static final Path SCRIPTS_DIR = resolveScriptsDir();

    private static Path resolveScriptsDir() {
        Path candidate = Paths.get("deploy/init-scripts/14-travel-expense-reimbursement");
        if (candidate.toFile().exists()) return candidate;
        candidate = Paths.get("../../deploy/init-scripts/14-travel-expense-reimbursement");
        if (candidate.toFile().exists()) return candidate;
        Path current = Paths.get("").toAbsolutePath();
        while (current != null) {
            Path scripts = current.resolve("deploy/init-scripts/14-travel-expense-reimbursement");
            if (scripts.toFile().exists()) return scripts;
            current = current.getParent();
        }
        return Paths.get("deploy/init-scripts/14-travel-expense-reimbursement");
    }

    /** Scripts that have dependencies on prior scripts. */
    private static final List<String> DEPENDENT_SCRIPTS = List.of(
            "01-create-tables.sql",
            "02-create-bpmn-process.sql",
            "03-form-table-bindings.sql"
    );

    /**
     * Feature: travel-expense-reimbursement, Property 4
     *
     * For any dependent script, the SQL content must contain
     * RAISE EXCEPTION patterns for dependency checks.
     *
     * **Validates: Requirements 8.4**
     */
    @Property(tries = 100)
    @Label("Feature: travel-expense-reimbursement, Property 4: Dependency missing error handling")
    void dependentScriptContainsRaiseException(
            @ForAll("dependentScriptIndices") int scriptIndex)
            throws IOException {

        String scriptFile = DEPENDENT_SCRIPTS.get(scriptIndex);
        Path scriptPath = SCRIPTS_DIR.resolve(scriptFile);
        String content = Files.readString(scriptPath);
        String contentUpper = content.toUpperCase();

        // Every dependent script must contain RAISE EXCEPTION
        assertThat(contentUpper)
                .as("Script '%s' must contain RAISE EXCEPTION for dependency checks",
                        scriptFile)
                .contains("RAISE EXCEPTION");

        // Verify the RAISE EXCEPTION message references the dependency
        // (should mention which script to run first)
        boolean mentionsRunFirst = content.contains("Run 00-create-function-unit.sql first")
                || content.contains("Run 01-create-tables.sql first");
        assertThat(mentionsRunFirst)
                .as("Script '%s' RAISE EXCEPTION must mention which script to run first",
                        scriptFile)
                .isTrue();

        // Verify there is a NULL check pattern before RAISE EXCEPTION
        // (SELECT INTO ... IF ... IS NULL THEN RAISE EXCEPTION)
        assertThat(contentUpper)
                .as("Script '%s' must check for NULL before raising exception",
                        scriptFile)
                .contains("IS NULL");

        // Verify the script queries the function unit first
        assertThat(contentUpper)
                .as("Script '%s' must query function unit as dependency check",
                        scriptFile)
                .contains("FU-20260403-A1B2C3");
    }

    // ==================== Providers ====================

    @Provide
    Arbitrary<Integer> dependentScriptIndices() {
        return Arbitraries.integers().between(0, DEPENDENT_SCRIPTS.size() - 1);
    }
}
